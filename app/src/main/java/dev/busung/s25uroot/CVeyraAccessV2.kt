package ctrl.mietze.veyraroot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun cveyraShellScript(text: String): String = text.trimIndent().replace('§', '$')

internal enum class CVeyraAccessPhase {
    InfoRequired,
    VeyraKsuRequired,
    RebootRequired,
    ReadyToAccept,
    Active,
}

internal data class CVeyraAccessStatus(
    val phase: CVeyraAccessPhase,
    val veyraKsu: VeyraKsuState,
    val legacyKernelSuGrants: Int = 0,
    val backupPath: String = "",
) {
    val active: Boolean get() = phase == CVeyraAccessPhase.Active
    val waitingForReboot: Boolean get() = phase == CVeyraAccessPhase.RebootRequired
}

internal data class KernelSuGrantBackup(
    val path: String,
    val count: Int,
    val packages: Set<String>,
)

internal object CVeyraAccessStore {
    private const val PREFS = "cveyra_access_v2"
    private const val INFO_READ = "info_read"
    private const val ACTIVE = "active"
    private const val MODULE_PENDING = "module_pending"
    private const val INSTALL_BOOT_ID = "install_boot_id"
    private const val RESUME_SETUP = "resume_setup"
    private const val BACKUP_PATH = "backup_path"
    private const val LEGACY_ROOT_PACKAGES = "legacy_root_packages"
    private const val ACCEPTED_VERSION = "accepted_version"
    const val ACCESS_VERSION = "2.0.0"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun infoRead(context: Context): Boolean = prefs(context).getBoolean(INFO_READ, false)

    fun markInfoRead(context: Context) {
        prefs(context).edit().putBoolean(INFO_READ, true).apply()
    }

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(ACTIVE, false)

    fun requestResume(context: Context) {
        prefs(context).edit().putBoolean(RESUME_SETUP, true).apply()
    }

    fun shouldResumeSetup(context: Context): Boolean =
        prefs(context).getBoolean(RESUME_SETUP, false)

    fun clearResumeRequest(context: Context) {
        prefs(context).edit().putBoolean(RESUME_SETUP, false).apply()
    }

    fun legacyRootPackages(context: Context): Set<String> =
        prefs(context).getStringSet(LEGACY_ROOT_PACKAGES, emptySet()).orEmpty().toSet()

    fun backupPath(context: Context): String = prefs(context).getString(BACKUP_PATH, "").orEmpty()

    fun status(context: Context): CVeyraAccessStatus {
        val module = VeyraKsuBridge.readState()
        val p = prefs(context)
        val phase = when {
            p.getBoolean(ACTIVE, false) -> CVeyraAccessPhase.Active
            !p.getBoolean(INFO_READ, false) -> CVeyraAccessPhase.InfoRequired
            !module.installed || module.moduleVersionCode < 20000 -> CVeyraAccessPhase.VeyraKsuRequired
            p.getBoolean(MODULE_PENDING, false) && !moduleLoadedAfterInstall(context, module) ->
                CVeyraAccessPhase.RebootRequired
            module.enabled && module.permissionBridge in setOf("ready", "active") ->
                CVeyraAccessPhase.ReadyToAccept
            else -> CVeyraAccessPhase.RebootRequired
        }
        return CVeyraAccessStatus(
            phase = phase,
            veyraKsu = module,
            legacyKernelSuGrants = legacyRootPackages(context).size,
            backupPath = backupPath(context),
        )
    }

    fun markModuleInstalled(context: Context) {
        prefs(context).edit()
            .putBoolean(MODULE_PENDING, true)
            .putString(INSTALL_BOOT_ID, currentBootId())
            .putBoolean(RESUME_SETUP, true)
            .apply()
    }

    private fun moduleLoadedAfterInstall(context: Context, module: VeyraKsuState): Boolean {
        // KernelSU's native soft-reboot may not change the kernel boot_id. The reliable proof that
        // the newly installed module actually ran is its own v2 lifecycle state, not a different
        // kernel boot UUID.
        if (!module.installed || !module.enabled || module.moduleVersionCode < 20000) return false
        return module.stage in setOf("service", "late-load") &&
            module.permissionBridge in setOf("ready", "active")
    }

    fun backupLegacyKernelSu(context: Context): KernelSuGrantBackup {
        val app = context.applicationContext
        val packageNames = installedPackages(app)
        val safe = packageNames.filter { it.matches(Regex("[A-Za-z0-9_.]+")) }
        val packageArgs = safe.joinToString(" ") { shellQuote(it) }
        val script = cveyraShellScript(
            """
K=§(command -v ksud 2>/dev/null || true)
[ -n "§K" ] || K=/data/adb/ksud
[ -x "§K" ] || exit 127
for P in $packageArgs; do
  J=§(§K profile get "§P" 2>/dev/null || true)
  case "§J" in
    *'"allow_su":true'*|*'"allow_su": true'*) printf '%s\t%s\n' "§P" "§J" ;;
  esac
done
            """,
        )
        val result = KernelSuRuntime.rootShell(script, timeoutSeconds = 45)
        val lines = result?.takeIf { it.exitCode == 0 }?.output
            ?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty()
        val packages = lines.mapNotNull { line ->
            line.substringBefore('\t').trim().takeIf { it.matches(Regex("[A-Za-z0-9_.]+")) }
        }.toSet()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val body = buildString {
            appendLine("Veyra KernelSU grant backup")
            appendLine("created=$stamp")
            appendLine("accessVersion=$ACCESS_VERSION")
            appendLine("packages=${packages.size}")
            appendLine()
            lines.forEach(::appendLine)
        }
        val path = DownloadStore.save(
            app,
            "Veyra-KernelSU-grants-$stamp.txt",
            body.toByteArray(),
        )
        val rootCopy = "/data/adb/veyra_ksu/backups/ksu-grants-$stamp.txt"
        val stage = File(app.cacheDir, "veyra-ksu-grants-$stamp.txt").apply { writeText(body) }
        runCatching {
            KernelSuRuntime.rootShell(
                "mkdir -p /data/adb/veyra_ksu/backups && chmod 700 /data/adb/veyra_ksu/backups && " +
                    "cp ${shellQuote(stage.absolutePath)} ${shellQuote(rootCopy)} && " +
                    "chmod 600 ${shellQuote(rootCopy)}",
                timeoutSeconds = 15,
            )
        }
        stage.delete()
        prefs(app).edit()
            .putString(BACKUP_PATH, path)
            .putStringSet(LEGACY_ROOT_PACKAGES, packages)
            .apply()
        return KernelSuGrantBackup(path, packages.size, packages)
    }

    fun accept(context: Context): Result<CVeyraAccessStatus> = runCatching {
        val app = context.applicationContext
        val current = status(app)
        require(current.phase == CVeyraAccessPhase.ReadyToAccept) {
            "VeyraKSU 2.0.0 must be active after the required reboot first."
        }
        require(SuShell.isRoot()) {
            "Veyra itself needs a KernelSU Superuser grant before CVeyra Access can be enforced."
        }
        val backup = backupLegacyKernelSu(app)
        check(VeyraKsuBridge.activatePermissionBridge()) {
            "VeyraKSU did not accept the permission bridge activation."
        }
        VsprStore.setProviderEnabled(app, VsprPrivilege.CVeyra, true)
        VsprStore.setProviderEnabled(app, VsprPrivilege.SuperSu, true)
        VsprStore.setPermissionMode(app, VsprPermissionMode.Self)
        CVeyraPreferences.setEnabled(app, true)
        CVeyraPreferences.setStartAsRoot(app, true)
        CVeyraPreferences.setPromoteAfterBoot(app, true)
        CVeyraPreferences.setStartOnBoot(app, true)
        prefs(app).edit()
            .putBoolean(ACTIVE, true)
            .putBoolean(MODULE_PENDING, false)
            .putBoolean(RESUME_SETUP, true)
            .putString(ACCEPTED_VERSION, ACCESS_VERSION)
            .putString(BACKUP_PATH, backup.path)
            .putStringSet(LEGACY_ROOT_PACKAGES, backup.packages)
            .apply()
        CVeyraController.start(app)
        status(app)
    }

    fun enforce(context: Context) {
        if (!isActive(context)) return
        CVeyraPreferences.setEnabled(context, true)
        CVeyraPreferences.setStartAsRoot(context, true)
        CVeyraPreferences.setPromoteAfterBoot(context, true)
        CVeyraPreferences.setStartOnBoot(context, true)
    }

    private fun currentBootId(): String = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrDefault("")

    @Suppress("DEPRECATION")
    private fun installedPackages(context: Context): List<String> {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }
        return apps.asSequence()
            .map(ApplicationInfo::packageName)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }
}

internal enum class CVeyraPrivacyModule(
    val id: String,
    val title: String,
    val summary: String,
) {
    Accessibility(
        "hide_accessibility",
        "Hide accessibility",
        "Temporarily turns the real accessibility settings off and restores the exact previous values when unloaded.",
    ),
    DeveloperOptions(
        "hide_developer_options",
        "Hide developer options",
        "Temporarily turns the real Developer options flag off and restores it when unloaded.",
    ),
    UsbDebugging(
        "hide_usb_debugging",
        "Hide USB debugging",
        "Temporarily turns the real USB debugging flag off. CVeyra Access keeps its root broker independent from ADB.",
    ),
    PrivateDns(
        "hide_private_dns",
        "Hide Private DNS",
        "Temporarily turns Private DNS off and restores both its mode and hostname when unloaded.",
    ),
}

internal object CVeyraPrivacyModules {
    private const val PREFS = "cveyra_privacy_modules_v2"
    private const val ENABLED = "enabled_"
    private const val ORIGINAL = "original_"
    private const val NULL_SENTINEL = "<VEYRA_NULL>"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context, module: CVeyraPrivacyModule): Boolean =
        prefs(context).getBoolean(ENABLED + module.id, false)

    fun setEnabled(context: Context, module: CVeyraPrivacyModule, enabled: Boolean): Result<Unit> =
        runCatching {
            require(CVeyraAccessStore.isActive(context)) { "CVeyra Access is not active." }
            if (enabled) hide(context, module) else restore(context, module)
            prefs(context).edit().putBoolean(ENABLED + module.id, enabled).apply()
        }

    private fun keys(module: CVeyraPrivacyModule): List<Triple<String, String, String>> = when (module) {
        CVeyraPrivacyModule.DeveloperOptions -> listOf(
            Triple("global", "development_settings_enabled", "0"),
        )
        CVeyraPrivacyModule.UsbDebugging -> listOf(
            Triple("global", "adb_enabled", "0"),
        )
        CVeyraPrivacyModule.Accessibility -> listOf(
            Triple("secure", "accessibility_enabled", "0"),
            Triple("secure", "enabled_accessibility_services", ""),
        )
        CVeyraPrivacyModule.PrivateDns -> listOf(
            Triple("global", "private_dns_mode", "off"),
            Triple("global", "private_dns_specifier", ""),
        )
    }

    private fun hide(context: Context, module: CVeyraPrivacyModule) {
        keys(module).forEach { (namespace, key, hidden) ->
            val prefKey = ORIGINAL + module.id + "_" + namespace + "_" + key
            if (!prefs(context).contains(prefKey)) {
                val current = readSetting(context, namespace, key)
                prefs(context).edit().putString(prefKey, current ?: NULL_SENTINEL).apply()
            }
            writeSetting(context, namespace, key, hidden)
        }
    }

    private fun restore(context: Context, module: CVeyraPrivacyModule) {
        keys(module).forEach { (namespace, key, _) ->
            val prefKey = ORIGINAL + module.id + "_" + namespace + "_" + key
            if (!prefs(context).contains(prefKey)) return@forEach
            val original = prefs(context).getString(prefKey, NULL_SENTINEL)
            if (original == NULL_SENTINEL) deleteSetting(context, namespace, key)
            else writeSetting(context, namespace, key, original.orEmpty())
            prefs(context).edit().remove(prefKey).apply()
        }
    }

    private fun readSetting(context: Context, namespace: String, key: String): String? {
        val result = CVeyraController.rootShell(
            context,
            "settings get $namespace ${shellQuote(key)}",
        ) ?: error("Root broker unavailable")
        if (result.exitCode != 0) error(result.output.ifBlank { "settings get failed" })
        return result.output.trim().takeUnless { it == "null" }
    }

    private fun writeSetting(context: Context, namespace: String, key: String, value: String) {
        val result = CVeyraController.rootShell(
            context,
            "settings put $namespace ${shellQuote(key)} ${shellQuote(value)}",
        ) ?: error("Root broker unavailable")
        if (result.exitCode != 0) error(result.output.ifBlank { "settings put failed" })
    }

    private fun deleteSetting(context: Context, namespace: String, key: String) {
        val result = CVeyraController.rootShell(
            context,
            "settings delete $namespace ${shellQuote(key)}",
        ) ?: error("Root broker unavailable")
        if (result.exitCode != 0) error(result.output.ifBlank { "settings delete failed" })
    }
}

internal object CVeyraFirewall {
    private const val PREFS = "cveyra_firewall_v2"
    private const val BLOCKED = "blocked_packages"
    private const val CHAIN = "VEYRA_OUT"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun blockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(BLOCKED, emptySet()).orEmpty().toSet()

    fun setBlocked(context: Context, packageName: String, blocked: Boolean): Result<Boolean> =
        runCatching {
            require(CVeyraAccessStore.isActive(context)) { "CVeyra Access is not active." }
            require(packageName.matches(Regex("[A-Za-z0-9_.]+"))) { "Invalid package name." }
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val uid = appInfo.uid
            val next = blockedPackages(context).toMutableSet().apply {
                if (blocked) add(packageName) else remove(packageName)
            }
            val uids = next.mapNotNull { pkg ->
                runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()
            }.distinct().sorted()
            val uidBody = uids.joinToString("\n", postfix = if (uids.isEmpty()) "" else "\n")
            val stage = File(context.cacheDir, "veyra-firewall.uids").apply { writeText(uidBody) }
            val platformValue = if (blocked) "false" else "true"
            val command = cveyraShellScript(
                """
STATE=/data/adb/veyra_ksu
mkdir -p "§STATE"
chmod 700 "§STATE"
cp ${shellQuote(stage.absolutePath)} "§STATE/firewall.uids" || exit 31
chmod 600 "§STATE/firewall.uids"
SCRIPT=/data/adb/modules/veyra_ksu/firewall.sh
if [ -x "§SCRIPT" ]; then
  "§SCRIPT" apply
else
  iptables -w 2 -N $CHAIN 2>/dev/null || true
  iptables -w 2 -C OUTPUT -j $CHAIN 2>/dev/null || iptables -w 2 -I OUTPUT 1 -j $CHAIN
  iptables -w 2 -F $CHAIN
  for U in §(cat "§STATE/firewall.uids" 2>/dev/null); do
    iptables -w 2 -A $CHAIN -m owner --uid-owner "§U" -j REJECT
  done
  if command -v ip6tables >/dev/null 2>&1; then
    ip6tables -w 2 -N $CHAIN 2>/dev/null || true
    ip6tables -w 2 -C OUTPUT -j $CHAIN 2>/dev/null || ip6tables -w 2 -I OUTPUT 1 -j $CHAIN
    ip6tables -w 2 -F $CHAIN
    for U in §(cat "§STATE/firewall.uids" 2>/dev/null); do
      ip6tables -w 2 -A $CHAIN -m owner --uid-owner "§U" -j REJECT
    done
  fi
fi
cmd connectivity set-chain3-enabled true >/dev/null 2>&1 || true
cmd connectivity set-package-networking-enabled $platformValue ${shellQuote(packageName)} >/dev/null 2>&1 || true
echo APPLIED
                """,
            )
            val result = CVeyraController.rootShell(context, command)
                ?: error("CVeyra Root broker unavailable")
            stage.delete()
            if (result.exitCode != 0) error(result.output.ifBlank { "Firewall apply failed" })
            prefs(context).edit().putStringSet(BLOCKED, next).apply()
            // UID is deliberately resolved even for an unblock so a stale package cannot be stored.
            uid >= 0
        }

    fun clearAll(context: Context): Result<Unit> = runCatching {
        val current = blockedPackages(context)
        current.forEach { pkg -> setBlocked(context, pkg, false).getOrThrow() }
    }
}
