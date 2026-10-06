package ctrl.mietze.veyraroot

import android.content.Context

internal enum class VsprPrivilege(val storedValue: String) {
    SuperSu("supersu"),
    Adb("adb"),
    Shizuku("shizuku"),
    AdVeyra("adveyra"),
    CVeyra("cveyra"),
    System("system"),
}

internal enum class VsprPermissionMode(val storedValue: String) {
    Offline("offline"),
    Auto("auto"),
    Self("self");

    companion object {
        fun fromStored(value: String?): VsprPermissionMode =
            entries.firstOrNull { it.storedValue == value } ?: Offline
    }
}

internal object VsprStore {
    private const val PREFS = "vspr"
    private const val PREFIX = "grants_"
    private const val PROVIDER_PREFIX = "provider_"
    private const val SHOW_SYSTEM_APPS = "show_system_apps"
    private const val PERMISSION_MODE = "permission_mode"
    private const val SELF_APPS = "self_apps"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun grants(context: Context, packageName: String): Set<VsprPrivilege> {
        val raw = prefs(context).getStringSet(PREFIX + packageName, emptySet()).orEmpty()
        return VsprPrivilege.entries.filter { it.storedValue in raw }.toSet()
    }

    fun setGrant(
        context: Context,
        packageName: String,
        privilege: VsprPrivilege,
        enabled: Boolean,
    ) = setGrantRaw(context, packageName, privilege, enabled)

    internal fun setGrantRaw(
        context: Context,
        packageName: String,
        privilege: VsprPrivilege,
        enabled: Boolean,
    ) {
        val next = grants(context, packageName).toMutableSet().apply {
            if (enabled) add(privilege) else remove(privilege)
        }
        prefs(context).edit()
            .putStringSet(PREFIX + packageName, next.map { it.storedValue }.toSet())
            .apply()
    }

    fun clearPrivilege(context: Context, packageName: String, privilege: VsprPrivilege) {
        setGrant(context, packageName, privilege, false)
    }

    fun providerEnabled(context: Context, privilege: VsprPrivilege): Boolean =
        prefs(context).getBoolean(PROVIDER_PREFIX + privilege.storedValue, false)

    fun setProviderEnabled(context: Context, privilege: VsprPrivilege, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(PROVIDER_PREFIX + privilege.storedValue, enabled)
            .apply()
    }

    fun showSystemApps(context: Context): Boolean =
        prefs(context).getBoolean(SHOW_SYSTEM_APPS, false)

    fun setShowSystemApps(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SHOW_SYSTEM_APPS, enabled).apply()
    }

    fun permissionMode(context: Context): VsprPermissionMode =
        VsprPermissionMode.fromStored(prefs(context).getString(PERMISSION_MODE, null))

    fun setPermissionMode(context: Context, mode: VsprPermissionMode) {
        prefs(context).edit().putString(PERMISSION_MODE, mode.storedValue).apply()
    }

    fun selfApps(context: Context): Set<String> =
        prefs(context).getStringSet(SELF_APPS, emptySet()).orEmpty().toSet()

    fun addSelfApp(context: Context, packageName: String) {
        val next = selfApps(context).toMutableSet().apply { add(packageName) }
        prefs(context).edit().putStringSet(SELF_APPS, next).apply()
    }

    fun removeSelfApp(context: Context, packageName: String) {
        val next = selfApps(context).toMutableSet().apply { remove(packageName) }
        prefs(context).edit().putStringSet(SELF_APPS, next).apply()
    }

}

/**
 * Read-only integration with KernelSU-family app profiles.
 *
 * Newer KernelSU-Next builds expose a profile-get command as JSON. V-SPR uses it only
 * to avoid presenting a second root grant for an app already managed directly by KernelSU.
 * Unsupported managers simply return an empty set; V-SPR never edits KernelSU profiles here.
 */
internal object KernelSuDirectGrantProbe {
    private val safePackage = Regex("[A-Za-z0-9_.]+")

    fun grantedPackages(packageNames: List<String>): Set<String> {
        val packages = packageNames.filter { it.matches(safePackage) }.distinct()
        if (packages.isEmpty()) return emptySet()

        val script = buildString {
            append("K=${'$'}(command -v ksud 2>/dev/null || true); ")
            append("[ -n \"${'$'}K\" ] || exit 0; ")
            append("for P in ")
            packages.forEach { append("'").append(it).append("' ") }
            append("; do ")
            append("J=${'$'}(${'$'}K profile get \"${'$'}P\" 2>/dev/null || true); ")
            append("case \"${'$'}J\" in ")
            append("*'\\\"allow_su\\\":true'*|*'\\\"allow_su\\\": true'*) echo \"${'$'}P\";; ")
            append("esac; done")
        }

        val result = KernelSuRuntime.rootShell(script) ?: return emptySet()
        if (result.exitCode != 0) return emptySet()
        return result.output.lineSequence()
            .map(String::trim)
            .filter { it.matches(safePackage) }
            .toSet()
    }
}
