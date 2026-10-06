package ctrl.mietze.veyraroot

import android.content.Context

internal data class SystemUpdateSnapshot(
    val controlled: Boolean = false,
    val managedPackages: Set<String> = emptySet(),
    val backend: CVeyraBackend = CVeyraBackend.None,
)

internal data class SystemUpdateActionResult(
    val success: Boolean,
    val snapshot: SystemUpdateSnapshot,
    val detail: String,
)

internal object SystemUpdateControl {
    private const val PREFS = "system_update_control"
    private const val MANAGED_PACKAGES = "managed_packages"
    private const val SAVED_AUTO_UPDATE = "saved_auto_update"
    private const val AUTO_UPDATE_SAVED = "auto_update_saved"

    private val genericPackages = listOf(
        "com.android.updater",
        "com.google.android.systemupdater",
        "org.lineageos.updater",
        "org.evolutionx.updater",
        "com.cyanogenmod.updater",
    )

    private val samsung = listOf(
        "com.wssyncmldm",
        "com.sec.android.soagent",
        "com.samsung.sdm",
    )

    private val huaweiHonor = listOf(
        "com.huawei.android.hwouc",
        "com.huawei.android.hwoucassistant",
        "com.hihonor.ouc",
        "com.hihonor.android.hwouc",
    )

    private val xiaomi = listOf(
        "com.android.updater",
    )

    private val oplus = listOf(
        "com.oppo.ota",
        "com.oppo.otaui",
        "com.oppo.sau",
        "com.oplus.ota",
        "com.coloros.ota",
        "com.coloros.otaui",
        "com.coloros.sau",
        "com.oplus.sau",
    )

    private val vivo = listOf(
        "com.bbk.updater",
        "com.vivo.updater",
        "com.vivo.systemupdate",
    )

    private val motoLenovo = listOf(
        "com.motorola.ccc.ota",
        "com.motorola.android.fota",
        "com.lenovo.ota",
    )

    private val sony = listOf(
        "com.sonyericsson.updatecenter",
        "com.sonymobile.updatecenter",
    )

    private val asus = listOf(
        "com.asus.dm",
        "com.asus.systemupdate",
    )

    private val zteNubia = listOf(
        "com.zte.zdm",
        "com.zte.updater",
        "cn.nubia.systemupdate",
    )

    private val transsion = listOf(
        "com.transsion.systemupdate",
        "com.transsion.ota",
        "com.itel.updater",
        "com.infinix.updater",
        "com.tecno.updater",
    )

    private val meizu = listOf(
        "com.meizu.flyme.update",
        "com.meizu.update",
    )

    private val nothing = listOf(
        "com.nothing.systemupdate",
        "com.nothing.ota",
    )

    private val lgHtc = listOf(
        "com.lge.lgdmsclient",
        "com.lge.updatecenter",
        "com.htc.updater",
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(context: Context): SystemUpdateSnapshot {
        val managed = prefs(context).getStringSet(MANAGED_PACKAGES, emptySet()).orEmpty().toSet()
        return SystemUpdateSnapshot(
            controlled = managed.isNotEmpty() || prefs(context).getBoolean(AUTO_UPDATE_SAVED, false),
            managedPackages = managed,
            backend = CVeyraPreferences.backend(context),
        )
    }

    fun disable(context: Context, device: DeviceSnapshot): SystemUpdateActionResult {
        val backend = CVeyraController.probeBackend(context)
        if (backend == CVeyraBackend.None) {
            return SystemUpdateActionResult(
                success = false,
                snapshot = snapshot(context),
                detail = "CVeyra is not active. Start CVeyra as Root or Wireless ADB first.",
            )
        }

        val currentAuto = CVeyraController.shell(
            context,
            "settings get global ota_disable_automatic_update 2>/dev/null",
        )?.output?.trim().orEmpty()
        val p = prefs(context)
        val hadSavedAutoState = p.getBoolean(AUTO_UPDATE_SAVED, false)

        val candidates = packagesFor(device).distinct()
        val alreadyDisabled = disabledPackages(context)
        val changed = p.getStringSet(MANAGED_PACKAGES, emptySet()).orEmpty().toMutableSet()

        candidates.forEach { packageName ->
            if (!installed(context, packageName) || packageName in alreadyDisabled) return@forEach
            val result = CVeyraController.shell(
                context,
                "pm disable-user --user 0 ${shellQuote(packageName)}",
            )
            if (result?.exitCode == 0) changed += packageName
        }

        val globalResult = CVeyraController.shell(
            context,
            "settings put global ota_disable_automatic_update 1",
        )
        val success = globalResult?.exitCode == 0 || changed.isNotEmpty()
        if (success) {
            val editor = p.edit().putStringSet(MANAGED_PACKAGES, changed)
            if (!hadSavedAutoState) {
                editor
                    .putBoolean(AUTO_UPDATE_SAVED, true)
                    .putString(SAVED_AUTO_UPDATE, currentAuto)
            }
            editor.apply()
        }
        val state = snapshot(context).copy(backend = backend)
        return SystemUpdateActionResult(
            success = success,
            snapshot = state,
            detail = if (success) {
                "Success — saved you from using System Update."
            } else {
                "System Update could not be disabled through the current CVeyra backend."
            },
        )
    }

    fun enable(context: Context): SystemUpdateActionResult {
        val backend = CVeyraController.probeBackend(context)
        if (backend == CVeyraBackend.None) {
            return SystemUpdateActionResult(
                success = false,
                snapshot = snapshot(context),
                detail = "CVeyra is not active. Start CVeyra as Root or Wireless ADB first.",
            )
        }

        val p = prefs(context)
        val managed = p.getStringSet(MANAGED_PACKAGES, emptySet()).orEmpty()
        var allPackagesOk = true
        managed.forEach { packageName ->
            val result = CVeyraController.shell(
                context,
                "pm enable ${shellQuote(packageName)}",
            )
            if (result?.exitCode != 0) allPackagesOk = false
        }

        val saved = p.getString(SAVED_AUTO_UPDATE, null)
        val restoreCommand = saved
            ?.takeIf { it.matches(Regex("-?\\d+")) }
            ?.let { "settings put global ota_disable_automatic_update $it" }
            ?: "settings delete global ota_disable_automatic_update"
        val global = CVeyraController.shell(context, restoreCommand)

        if (allPackagesOk && global?.exitCode == 0) {
            p.edit()
                .remove(MANAGED_PACKAGES)
                .remove(SAVED_AUTO_UPDATE)
                .remove(AUTO_UPDATE_SAVED)
                .apply()
        }

        val success = allPackagesOk && global?.exitCode == 0
        return SystemUpdateActionResult(
            success = success,
            snapshot = snapshot(context).copy(backend = backend),
            detail = if (success) {
                "System Update is enabled again."
            } else {
                "Some update components could not be restored."
            },
        )
    }

    private fun packagesFor(device: DeviceSnapshot): List<String> {
        val id = "${device.manufacturer} ${device.model} ${device.device}".lowercase()
        return buildList {
            addAll(genericPackages)
            when {
                "samsung" in id -> addAll(samsung)
                "huawei" in id || "honor" in id -> addAll(huaweiHonor)
                "xiaomi" in id || "redmi" in id || "poco" in id -> addAll(xiaomi)
                "oppo" in id || "oneplus" in id || "realme" in id || "oplus" in id -> addAll(oplus)
                "vivo" in id || "iqoo" in id -> addAll(vivo)
                "motorola" in id || "lenovo" in id -> addAll(motoLenovo)
                "sony" in id || "xperia" in id -> addAll(sony)
                "asus" in id || "rog" in id -> addAll(asus)
                "zte" in id || "nubia" in id || "redmagic" in id -> addAll(zteNubia)
                "tecno" in id || "infinix" in id || "itel" in id -> addAll(transsion)
                "meizu" in id -> addAll(meizu)
                "nothing" in id || "cmf" in id -> addAll(nothing)
                "lg" in id || "htc" in id -> addAll(lgHtc)
            }
        }
    }

    private fun installed(context: Context, packageName: String): Boolean {
        val result = CVeyraController.shell(
            context,
            "pm path ${shellQuote(packageName)} >/dev/null 2>&1",
        )
        return result?.exitCode == 0
    }

    private fun disabledPackages(context: Context): Set<String> =
        CVeyraController.shell(context, "pm list packages -d")?.output
            ?.lineSequence()
            ?.mapNotNull { line -> line.removePrefix("package:").trim().takeIf(String::isNotBlank) }
            ?.toSet()
            .orEmpty()
}
