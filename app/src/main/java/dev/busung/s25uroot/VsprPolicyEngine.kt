package ctrl.mietze.veyraroot

import android.content.Context
import android.os.Process

internal data class VsprApplyResult(
    val success: Boolean,
    val detail: String,
)

internal object VsprPolicyEngine {
    private val safePackage = Regex("[A-Za-z0-9_.]+")

    fun apply(
        context: Context,
        packageName: String,
        privilege: VsprPrivilege,
        enabled: Boolean,
    ): VsprApplyResult {
        val app = context.applicationContext
        if (!CVeyraAccessStore.isActive(app)) {
            return VsprApplyResult(false, "CVeyra Access 2.0.0 is not active.")
        }
        if (!packageName.matches(safePackage)) {
            return VsprApplyResult(false, "Invalid package name.")
        }

        val appInfo = runCatching {
            app.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            return VsprApplyResult(false, "App is not installed.")
        }

        return when (privilege) {
            VsprPrivilege.CVeyra -> applyCVeyra(app, packageName, enabled)
            VsprPrivilege.Adb -> applyRuntimeGrant(
                app,
                packageName,
                "android.permission.WRITE_SECURE_SETTINGS",
                enabled,
                "ADB / WRITE_SECURE_SETTINGS",
            )
            VsprPrivilege.Shizuku -> applyRuntimeGrant(
                app,
                packageName,
                "moe.shizuku.manager.permission.API_V23",
                enabled,
                "Shizuku API",
            )
            VsprPrivilege.SuperSu -> {
                val directlyGranted =
                    packageName in KernelSuDirectGrantProbe.grantedPackages(listOf(packageName))
                when {
                    enabled && directlyGranted ->
                        VsprApplyResult(
                            true,
                            "KernelSU already grants Superuser to $packageName. V-SPR tracks the existing grant.",
                        )
                    enabled ->
                        VsprApplyResult(
                            false,
                            "KernelSU must grant Superuser to this app first. Veyra will not forge an incompatible KernelSU profile.",
                        )
                    !enabled && directlyGranted ->
                        VsprApplyResult(
                            false,
                            "This is a direct KernelSU grant. Revoke it in the KernelSU manager first, then refresh CVeyra.",
                        )
                    else ->
                        VsprApplyResult(true, "No direct KernelSU Superuser grant is active.")
                }
            }
            VsprPrivilege.System -> {
                val alreadySystemUid = appInfo.uid == Process.SYSTEM_UID
                when {
                    enabled && alreadySystemUid ->
                        VsprApplyResult(true, "App already runs as Android system UID 1000.")
                    enabled ->
                        VsprApplyResult(
                            false,
                            "Android UID 1000 cannot be assigned at runtime. The app must already be system-signed/installed with that UID.",
                        )
                    else -> VsprApplyResult(true, "No V-SPR System UID grant is active.")
                }
            }
            VsprPrivilege.AdVeyra -> applyDeviceOwnerState(app, packageName, enabled)
        }
    }

    fun clearAll(context: Context, packageName: String): List<VsprApplyResult> =
        VsprStore.grants(context, packageName).map { privilege ->
            val result = apply(context, packageName, privilege, false)
            if (result.success) VsprStore.setGrantRaw(context, packageName, privilege, false)
            result
        }

    private fun applyCVeyra(
        context: Context,
        packageName: String,
        enabled: Boolean,
    ): VsprApplyResult {
        val stateDir = "/data/adb/veyra_ksu"
        val allowFile = "$stateDir/cveyra.allow"
        val quoted = shellQuote(packageName)
        val command = if (enabled) {
            "mkdir -p $stateDir && chmod 700 $stateDir && " +
                "touch $allowFile && chmod 600 $allowFile && " +
                "grep -vxF $quoted $allowFile > $allowFile.tmp 2>/dev/null || true; " +
                "mv -f $allowFile.tmp $allowFile 2>/dev/null || true; " +
                "printf '%s\\n' $quoted >> $allowFile; " +
                "sort -u $allowFile -o $allowFile && chmod 600 $allowFile"
        } else {
            "mkdir -p $stateDir && touch $allowFile && " +
                "grep -vxF $quoted $allowFile > $allowFile.tmp 2>/dev/null || true; " +
                "mv -f $allowFile.tmp $allowFile && chmod 600 $allowFile"
        }
        val result = CVeyraController.rootShell(context, command)
            ?: return VsprApplyResult(false, "CVeyra Root broker is unavailable.")
        if (result.exitCode != 0) {
            return VsprApplyResult(
                false,
                result.output.ifBlank { "CVeyra allowlist update failed." },
            )
        }
        if (enabled) VsprStore.addSelfApp(context, packageName)
        return VsprApplyResult(
            true,
            if (enabled) {
                "CVeyra broker access is active for $packageName."
            } else {
                "CVeyra broker access was removed for $packageName."
            },
        )
    }

    private fun applyRuntimeGrant(
        context: Context,
        packageName: String,
        permission: String,
        enabled: Boolean,
        label: String,
    ): VsprApplyResult {
        val requested = runCatching {
            val info = context.packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            )
            permission in info.requestedPermissions.orEmpty()
        }.getOrDefault(false)
        if (!requested && enabled) {
            return VsprApplyResult(
                false,
                "$packageName does not request $permission, so $label cannot be granted safely.",
            )
        }

        val verb = if (enabled) "grant" else "revoke"
        val command = "pm $verb ${shellQuote(packageName)} ${shellQuote(permission)}"
        val result = CVeyraController.rootShell(context, command)
            ?: return VsprApplyResult(false, "CVeyra Root broker is unavailable.")
        return if (result.exitCode == 0) {
            VsprApplyResult(
                true,
                if (enabled) "$label granted to $packageName."
                else "$label revoked from $packageName.",
            )
        } else {
            VsprApplyResult(
                false,
                result.output.ifBlank { "$label $verb failed." },
            )
        }
    }

    private fun applyDeviceOwnerState(
        context: Context,
        packageName: String,
        enabled: Boolean,
    ): VsprApplyResult {
        val result = CVeyraController.rootShell(
            context,
            "dpm list owners 2>/dev/null || dpm get-device-owner 2>/dev/null || true",
        ) ?: return VsprApplyResult(false, "CVeyra Root broker is unavailable.")
        val ownsDevice = result.output.contains(packageName)
        return when {
            enabled && ownsDevice ->
                VsprApplyResult(true, "$packageName is already an Android Device Owner.")
            enabled ->
                VsprApplyResult(
                    false,
                    "Device Owner is not active for this app. Android only allows promotion under strict provisioning conditions; Veyra will not fake the state.",
                )
            !enabled && ownsDevice ->
                VsprApplyResult(
                    false,
                    "Device Owner removal must use Android's Device Policy flow; Veyra will not silently remove ownership.",
                )
            else -> VsprApplyResult(true, "No Device Owner grant is active.")
        }
    }
}
