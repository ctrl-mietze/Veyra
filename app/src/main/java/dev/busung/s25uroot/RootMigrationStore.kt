package ctrl.mietze.veyraroot

import android.content.Context

internal enum class RootMigrationStage(val storedValue: String) {
    Idle("idle"),
    Prepared("prepared"),
    RebootRequested("reboot_requested"),
    Completing("completing"),
    Completed("completed"),
    Failed("failed");

    companion object {
        fun fromStoredValue(value: String?): RootMigrationStage =
            entries.firstOrNull { it.storedValue == value } ?: Idle
    }
}

internal data class RootMigrationPlan(
    val stage: RootMigrationStage,
    val providerPackage: String?,
    val providerLabel: String?,
    val preparedBootId: String?,
    val rootVerifiedBootId: String?,
    val backupPath: String?,
) {
    val prepared: Boolean
        get() = stage == RootMigrationStage.Prepared ||
            stage == RootMigrationStage.RebootRequested ||
            stage == RootMigrationStage.Completing
}

internal object RootMigrationStore {
    private const val PREFS = "root_migration"
    private const val STAGE = "stage"
    private const val PROVIDER_PACKAGE = "provider_package"
    private const val PROVIDER_LABEL = "provider_label"
    private const val PREPARED_BOOT = "prepared_boot"
    private const val ROOT_VERIFIED_BOOT = "root_verified_boot"
    private const val BACKUP_PATH = "backup_path"
    private const val CURRENT_PROVIDER = "current_provider"
    private const val PREV_BOOT_ROOT = "prev_boot_root"
    private const val PREV_LOAD_KSU = "prev_load_ksu"
    private const val PREV_RESTART_AFTER_ROOT = "prev_restart_after_root"
    private const val PREV_SHIZUKU_MODE = "prev_shizuku_mode"
    private const val PREV_CVEYRA_ENABLED = "prev_cveyra_enabled"
    private const val PREV_CVEYRA_BOOT = "prev_cveyra_boot"
    private const val PREV_CVEYRA_ROOT = "prev_cveyra_root"
    private const val PREV_CVEYRA_PROMOTE = "prev_cveyra_promote"
    private const val SNAPSHOT_STORED = "snapshot_stored"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun plan(context: Context): RootMigrationPlan {
        val p = prefs(context)
        return RootMigrationPlan(
            stage = RootMigrationStage.fromStoredValue(p.getString(STAGE, null)),
            providerPackage = p.getString(PROVIDER_PACKAGE, null)?.takeIf(String::isNotBlank),
            providerLabel = p.getString(PROVIDER_LABEL, null)?.takeIf(String::isNotBlank),
            preparedBootId = p.getString(PREPARED_BOOT, null)?.takeIf(String::isNotBlank),
            rootVerifiedBootId = p.getString(ROOT_VERIFIED_BOOT, null)?.takeIf(String::isNotBlank),
            backupPath = p.getString(BACKUP_PATH, null)?.takeIf(String::isNotBlank),
        )
    }

    fun selectedProviderPackage(context: Context): String? =
        prefs(context).getString(PROVIDER_PACKAGE, null)?.takeIf(String::isNotBlank)

    fun rememberProvider(context: Context, candidate: RmgCandidate) {
        prefs(context).edit()
            .putString(PROVIDER_PACKAGE, candidate.packageName)
            .putString(PROVIDER_LABEL, candidate.label)
            .apply()
    }
    fun currentProviderPackage(context: Context): String? =
        prefs(context).getString(CURRENT_PROVIDER, null)
            ?.takeIf(String::isNotBlank)

    fun markCurrentProvider(context: Context, packageName: String) {
        prefs(context).edit().putString(CURRENT_PROVIDER, packageName).apply()
    }

    fun rootVerifiedForCurrentBoot(context: Context): Boolean {
        val boot = AutoRootSupport.currentBootToken() ?: return false
        return prefs(context).getString(ROOT_VERIFIED_BOOT, null) == boot
    }

    fun verifyRootNow(context: Context): Boolean {
        val boot = AutoRootSupport.currentBootToken() ?: return false
        val verified = SuShell.isRoot()
        val editor = prefs(context).edit()
        if (verified) editor.putString(ROOT_VERIFIED_BOOT, boot)
        else editor.remove(ROOT_VERIFIED_BOOT)
        editor.apply()
        return verified
    }

    fun migrationAvailable(context: Context): Boolean {
        val veyraAlreadyOwnsRoot =
            currentProviderPackage(context) == context.packageName ||
                AppPreferences.loadedFlavor(context) != null
        return RootStatusProbe.isActiveQuick() &&
            !veyraAlreadyOwnsRoot &&
            plan(context).stage != RootMigrationStage.RebootRequested &&
            plan(context).stage != RootMigrationStage.Completing
    }

    private fun storeSnapshot(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(SNAPSHOT_STORED, false)) return
        p.edit()
            .putBoolean(SNAPSHOT_STORED, true)
            .putBoolean(PREV_BOOT_ROOT, AppPreferences.bootRootMode(context))
            .putBoolean(PREV_LOAD_KSU, AppPreferences.loadKernelSu(context))
            .putBoolean(PREV_RESTART_AFTER_ROOT, AppPreferences.restartAfterRoot(context))
            .putBoolean(PREV_SHIZUKU_MODE, AppPreferences.shizukuMode(context))
            .putBoolean(PREV_CVEYRA_ENABLED, CVeyraPreferences.enabled(context))
            .putBoolean(PREV_CVEYRA_BOOT, CVeyraPreferences.startOnBoot(context))
            .putBoolean(PREV_CVEYRA_ROOT, CVeyraPreferences.startAsRoot(context))
            .putBoolean(PREV_CVEYRA_PROMOTE, CVeyraPreferences.promoteAfterBoot(context))
            .apply()
    }
    fun prepareFullRestart(
        context: Context,
        candidate: RmgCandidate,
    ): Result<RootMigrationPlan> = runCatching {
        require(verifyRootNow(context)) {
            context.getString(R.string.migration_root_required)
        }
        require(KernelSuRuntime.loadedInThisBoot(context)) {
            context.getString(R.string.migration_ksu_not_active)
        }

        rememberProvider(context, candidate)

        // A full reboot removes the current temporary root. If the cached payload needs a shell helper,
        // CVeyra must therefore have its own Wireless-ADB identity ready before we reboot; the current
        // root broker cannot exist on the other side of the reboot.
        val payloadNeedsShell = AutoRootSupport.bootPayloadNeedsShell(
            context = context,
            preferAttempted = false,
        )
        val cveyraWirelessReady =
            AdbCredentialStore.hasStoredKey(context) && AppPreferences.adbPaired(context)
        require(!payloadNeedsShell || cveyraWirelessReady) {
            context.getString(R.string.migration_cveyra_pair_required)
        }

        val backup = RmgBackupStore.backup(context, candidate)
        storeSnapshot(context)

        AppPreferences.setLoadKernelSu(context, true)
        AppPreferences.setBootRootMode(context, true)
        AppPreferences.setRestartAfterRoot(context, false)
        // Veyra's unattended transport should use the same local ADB identity CVeyra owns instead of
        // requiring a separate Shizuku binder after reboot.
        AppPreferences.setShizukuMode(context, false)

        CVeyraPreferences.setEnabled(context, true)
        CVeyraPreferences.setStartOnBoot(context, true)
        CVeyraPreferences.setStartAsRoot(context, true)
        CVeyraPreferences.setPromoteAfterBoot(context, true)
        CVeyraController.start(context)

        val boot = AutoRootSupport.currentBootToken()
        prefs(context).edit()
            .putString(STAGE, RootMigrationStage.Prepared.storedValue)
            .putString(PREPARED_BOOT, boot)
            .putString(BACKUP_PATH, backup.exportedPath ?: backup.apkFile.absolutePath)
            .apply()
        plan(context)
    }

    fun markRebootRequested(context: Context) {
        prefs(context).edit()
            .putString(STAGE, RootMigrationStage.RebootRequested.storedValue)
            .apply()
    }

    fun markCompleting(context: Context) {
        prefs(context).edit()
            .putString(STAGE, RootMigrationStage.Completing.storedValue)
            .apply()
    }
    fun complete(context: Context) {
        markCurrentProvider(context, context.packageName)
        restoreTemporarySettings(context)
        prefs(context).edit()
            .putString(STAGE, RootMigrationStage.Completed.storedValue)
            .remove(ROOT_VERIFIED_BOOT)
            .remove(PREPARED_BOOT)
            .apply()
    }

    fun fail(context: Context) {
        // The migration-specific boot/CVeyra settings are temporary even when the cleanup step fails.
        // Veyra may already own the running KernelSU session at this point, so keeping those overrides
        // armed would unexpectedly change the user's next boot.
        restoreTemporarySettings(context)
        prefs(context).edit()
            .putString(STAGE, RootMigrationStage.Failed.storedValue)
            .remove(ROOT_VERIFIED_BOOT)
            .remove(PREPARED_BOOT)
            .apply()
    }

    fun cancel(context: Context) {
        restoreTemporarySettings(context)
        prefs(context).edit()
            .putString(STAGE, RootMigrationStage.Idle.storedValue)
            .remove(ROOT_VERIFIED_BOOT)
            .remove(PREPARED_BOOT)
            .remove(BACKUP_PATH)
            .apply()
    }

    private fun restoreTemporarySettings(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(SNAPSHOT_STORED, false)) return

        AppPreferences.setBootRootMode(context, p.getBoolean(PREV_BOOT_ROOT, false))
        AppPreferences.setLoadKernelSu(context, p.getBoolean(PREV_LOAD_KSU, true))
        AppPreferences.setRestartAfterRoot(
            context,
            p.getBoolean(PREV_RESTART_AFTER_ROOT, false),
        )
        AppPreferences.setShizukuMode(
            context,
            p.getBoolean(PREV_SHIZUKU_MODE, false),
        )

        CVeyraPreferences.setEnabled(context, p.getBoolean(PREV_CVEYRA_ENABLED, false))
        CVeyraPreferences.setStartOnBoot(context, p.getBoolean(PREV_CVEYRA_BOOT, false))
        CVeyraPreferences.setStartAsRoot(context, p.getBoolean(PREV_CVEYRA_ROOT, false))
        CVeyraPreferences.setPromoteAfterBoot(context, p.getBoolean(PREV_CVEYRA_PROMOTE, true))
        if (CVeyraPreferences.enabled(context)) CVeyraController.start(context)
        else CVeyraController.stop(context)

        p.edit()
            .remove(SNAPSHOT_STORED)
            .remove(PREV_BOOT_ROOT)
            .remove(PREV_LOAD_KSU)
            .remove(PREV_RESTART_AFTER_ROOT)
            .remove(PREV_SHIZUKU_MODE)
            .remove(PREV_CVEYRA_ENABLED)
            .remove(PREV_CVEYRA_BOOT)
            .remove(PREV_CVEYRA_ROOT)
            .remove(PREV_CVEYRA_PROMOTE)
            .apply()
    }
}

internal object VeyraKsuPreferences {
    private const val PREFS = "veyra_ksu"
    private const val AUTO_INSTALL_AFTER_ROOT = "auto_install_after_root"

    fun autoInstallAfterRoot(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(AUTO_INSTALL_AFTER_ROOT, true)

    fun setAutoInstallAfterRoot(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTO_INSTALL_AFTER_ROOT, enabled)
            .apply()
    }
}
