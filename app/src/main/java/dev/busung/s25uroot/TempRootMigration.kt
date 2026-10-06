package ctrl.mietze.veyraroot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

internal data class RmgCandidate(
    val packageName: String,
    val label: String,
    val versionName: String,
    val sourceApk: String,
    val markers: List<String>,
) {
    val display: String get() = "$label ($packageName)"
}

internal object RmgCandidateScanner {
    fun scan(context: Context): List<RmgCandidate> {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        return apps.asSequence()
            .filter { it.packageName != context.packageName }
            .filter { info ->
                info.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                    info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            }
            .mapNotNull { info -> inspect(pm, info) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun inspect(pm: PackageManager, info: ApplicationInfo): RmgCandidate? {
        val packageName = info.packageName
        val label = runCatching { pm.getApplicationLabel(info).toString() }
            .getOrDefault(packageName)
        val lowerIdentity = "$packageName $label".lowercase(Locale.ROOT)
        val markers = ArrayList<String>()
        if ("root my galaxy" in lowerIdentity || "rootmygalaxy" in lowerIdentity) {
            markers += "Root My Galaxy identity"
        }
        if ("s25uroot" in lowerIdentity || "rmg" in lowerIdentity) {
            markers += "RMG package marker"
        }

        runCatching {
            ZipFile(info.sourceDir).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                if (names.any { it.endsWith("/libcve43499root.so") }) {
                    markers += "CVE-2026-43499 payload library"
                }
                if (names.any { it.endsWith("/libs25u_native.so") }) {
                    markers += "Root My Galaxy native probe"
                }
                if (names.any { it == "META-INF/version-control-info.textproto" }) {
                    markers += "Android VCS metadata"
                }
            }
        }

        val rmgEvidence = markers.count { it != "Android VCS metadata" }
        if (rmgEvidence == 0) return null

        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrNull()

        return RmgCandidate(
            packageName = packageName,
            label = label,
            versionName = packageInfo?.versionName.orEmpty(),
            sourceApk = info.sourceDir,
            markers = markers.distinct(),
        )
    }
}

internal data class RmgBackup(
    val packageName: String,
    val label: String,
    val versionName: String,
    val apkFile: File,
    val exportedPath: String?,
    val sha256: String,
)

internal object RmgBackupStore {
    fun backup(context: Context, candidate: RmgCandidate): RmgBackup {
        val source = File(candidate.sourceApk)
        require(source.isFile) { "Source APK is unavailable" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safePackage = candidate.packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(context.filesDir, "rmg-backups/$safePackage").apply { mkdirs() }
        val dest = File(dir, "$stamp-base.apk")
        source.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        val bytes = dest.readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val exported = runCatching {
            DownloadStore.save(
                context,
                "Veyra-backup-$safePackage-$stamp.apk",
                bytes,
            )
        }.getOrNull()
        return RmgBackup(
            packageName = candidate.packageName,
            label = candidate.label,
            versionName = candidate.versionName,
            apkFile = dest,
            exportedPath = exported,
            sha256 = digest,
        )
    }

    fun latest(context: Context, packageName: String): File? {
        val safePackage = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.filesDir, "rmg-backups/$safePackage")
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", true) }
            ?.maxByOrNull { it.lastModified() }
    }
}

internal sealed interface TempRootAdoptionResult {
    data class Success(
        val backup: RmgBackup,
        val sourceDisabled: Boolean,
        val detail: String,
    ) : TempRootAdoptionResult

    data class Refused(val detail: String) : TempRootAdoptionResult
}

internal object TempRootAdopter {
    fun adopt(
        context: Context,
        candidate: RmgCandidate,
        disableSource: Boolean,
    ): TempRootAdoptionResult {
        if (!KernelSuRuntime.loadedInThisBoot(context)) {
            return TempRootAdoptionResult.Refused(
                "KernelSU is not active in this boot. There is no temporary session to adopt.",
            )
        }
        if (!SuShell.isRoot()) {
            return TempRootAdoptionResult.Refused(
                "Veyra Root does not currently have KernelSU superuser access. Grant Veyra first.",
            )
        }

        VeyraKsuBridge.markDirectRootVerified()

        val backup = runCatching { RmgBackupStore.backup(context, candidate) }
            .getOrElse {
                return TempRootAdoptionResult.Refused(
                    "The source APK could not be backed up: ${it.message ?: it.javaClass.simpleName}",
                )
            }

        val disabled = if (disableSource) {
            val command = "pm disable-user --user 0 ${shellQuote(candidate.packageName)}"
            val result = SuShell.run(command)
            result != null && result.exitCode == 0
        } else {
            false
        }

        return TempRootAdoptionResult.Success(
            backup = backup,
            sourceDisabled = disabled,
            detail = if (disableSource) {
                if (disabled) {
                    "Veyra verified its own root path and disabled the source app. The current KernelSU session remains loaded."
                } else {
                    "Veyra verified its own root path, but Android did not disable the source app."
                }
            } else {
                "Veyra verified its own root path. The source app was left enabled."
            },
        )
    }

    fun clearSourceData(packageName: String): Boolean {
        val result = SuShell.run(
            "pm clear --user 0 ${shellQuote(packageName)}",
        )
        return result != null && result.exitCode == 0
    }

    fun disableAndClearSource(packageName: String): Pair<Boolean, Boolean> {
        val disabled = SuShell.run(
            "pm disable-user --user 0 ${shellQuote(packageName)}",
        )?.let { it.exitCode == 0 } ?: false
        val cleared = if (disabled) clearSourceData(packageName) else false
        return disabled to cleared
    }

    fun reenableSource(packageName: String): Boolean {
        val result = SuShell.run("pm enable ${shellQuote(packageName)}")
        return result != null && result.exitCode == 0
    }
}

internal object InstanceOverrideGuard {
    fun explain(candidate: RmgCandidate): String =
        "A safe in-place override for ${candidate.packageName} needs a Veyra clone built for that exact package and accepted signing lineage. " +
            "This build can back up the original, but it will not rewrite Android package/signature databases."
}
