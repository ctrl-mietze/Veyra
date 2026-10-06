package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File

/**
 * Read-only privileged storage bridge inspired specifically by Shizuku+ StorageProxy.
 *
 * This is deliberately not a root emulator. In CVeyra ADB mode it uses the authenticated local-adb
 * identity (uid 2000), validates every path against a narrow whitelist, and streams files with the
 * ADB sync protocol. App-private /data/data access is attempted only through Android run-as, so
 * non-debuggable apps remain inaccessible without real root.
 */
internal object CVeyraPrivilegedStorageProxy {
    private const val MIN_BOOT_BYTES = 4L * 1024L * 1024L

    private val allowedPrefixes = listOf(
        "/data/data/",
        "/data/user/",
        "/data/app/",
        "/storage/emulated/",
        "/sdcard/",
        "/data/local/tmp/",
    )

    fun available(context: Context): Boolean =
        CVeyraPreferences.enabled(context) &&
            CVeyraPreferences.backend(context) == CVeyraBackend.WirelessAdb &&
            AdbCredentialStore.hasStoredKey(context) &&
            AppPreferences.adbPaired(context)

    fun findBootCandidates(context: Context): List<String> {
        require(available(context)) { "CVeyra ADB storage proxy is unavailable" }

        val command = """
            for P in \
              /storage/emulated/0/Download/boot.img \
              /sdcard/Download/boot.img \
              /storage/emulated/0/Android/data/${context.packageName}/files/boot.img \
              /data/local/tmp/boot.img; do
              [ -r "${'$'}P" ] && echo "${'$'}P"
            done
            for D in \
              /storage/emulated/0/Download \
              /storage/emulated/0/Android/data/${context.packageName}/files \
              /data/local/tmp; do
              [ -d "${'$'}D" ] || continue
              find "${'$'}D" -maxdepth 2 -type f -iname '*boot*.img' -size +4096k -print 2>/dev/null
            done
        """.trimIndent()

        val snapshot = DeviceSnapshot.current()
        val nothing = NothingMagicProfiles.detect(snapshot)
        val result = CVeyraController.shell(context, command)
        return result
            ?.takeIf { it.exitCode == 0 }
            ?.output
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter { it.isNotBlank() && isSafePath(it) && isKernelBootCandidate(it) }
            ?.distinct()
            ?.sortedWith(
                compareByDescending<String> { candidateScore(it, snapshot, nothing) }
                    .thenBy { it.lowercase() },
            )
            ?.toList()
            .orEmpty()
    }

    private fun isKernelBootCandidate(path: String): Boolean {
        val name = File(path).name.lowercase()
        if (!name.endsWith(".img") || "boot" !in name) return false
        return listOf(
            "vendor_boot",
            "init_boot",
            "vendor_kernel_boot",
            "vbmeta",
        ).none { marker -> marker in name }
    }

    private fun candidateScore(
        path: String,
        snapshot: DeviceSnapshot,
        nothing: NothingMagicProfile?,
    ): Int {
        val lower = path.lowercase()
        var score = 0
        if (nothing != null) {
            if (nothing.codename in lower) score += 1200
            if (nothing.modelNumbers.any { it in lower }) score += 900
        }
        val device = snapshot.device.lowercase()
        if (device.isNotBlank() && device in lower) score += 700
        val model = snapshot.model.lowercase()
        if (model.isNotBlank() && model in lower) score += 500
        val build = snapshot.buildId.lowercase()
        if (build.isNotBlank() && build in lower) score += 600
        if (lower.endsWith("/boot.img")) score += 250
        if ("/download/" in lower) score += 80
        return score
    }

    fun captureBootImage(context: Context, destination: File): String {
        val candidates = findBootCandidates(context)
        require(candidates.isNotEmpty()) {
            context.getString(R.string.magic_builder_proxy_no_boot)
        }

        val errors = ArrayList<String>()
        for (candidate in candidates) {
            destination.delete()
            val attempt = runCatching {
                pull(context, candidate, destination)
                require(destination.length() >= MIN_BOOT_BYTES) {
                    "file is only ${destination.length()} bytes"
                }
                candidate
            }
            val source = attempt.getOrNull()
            if (source != null) {
                AppLog.info(
                    AppLogTags.BUILDER,
                    "Privileged Storage Proxy selected $source (${destination.length()} bytes)",
                )
                return source
            }
            errors += "$candidate: ${attempt.exceptionOrNull()?.message ?: "unknown error"}"
        }

        destination.delete()
        error(
            context.getString(R.string.magic_builder_proxy_pull_failed) +
                " " + errors.take(3).joinToString(" | "),
        )
    }

    fun pull(context: Context, remotePath: String, destination: File) {
        val source = validatedPath(remotePath)
        require(available(context)) { "CVeyra ADB storage proxy is unavailable" }

        TemporaryWirelessAdb.useBlocking(
            context = context,
            settleMillis = 350,
            onLog = { line -> AppLog.debug(AppLogTags.WIRELESS_ADB, "StorageProxy $line") },
        ) {
            WirelessAdbSession.open(context, portDiscoveryTimeoutMs = 20_000).use { session ->
                val direct = runCatching { session.pull(source, destination) }
                if (direct.isSuccess) return@use

                val stage = "/data/local/tmp/cveyra-storage-proxy-" +
                    System.nanoTime().toString(16) + ".bin"
                try {
                    val stageCommand = when {
                        source.startsWith("/data/data/") || source.startsWith("/data/user/") -> {
                            val pkg = packageFromPrivatePath(source)
                                ?: error("Private-data path has no valid package name")
                            "run-as ${shellQuote(pkg)} cat ${shellQuote(source)} > ${shellQuote(stage)}"
                        }
                        else -> "cat ${shellQuote(source)} > ${shellQuote(stage)}"
                    }
                    val staged = session.shell(stageCommand)
                    check(staged.exitCode == 0) {
                        "storage proxy could not stage $source: ${staged.output}"
                    }
                    val stat = session.shell("stat -c %s ${shellQuote(stage)} 2>/dev/null")
                    check(stat.exitCode == 0 && (stat.output.trim().toLongOrNull() ?: 0L) > 0L) {
                        "storage proxy produced an empty staging file"
                    }
                    session.pull(stage, destination)
                } finally {
                    session.remove(stage)
                }
            }
        }

        require(destination.isFile && destination.length() > 0L) {
            "Privileged Storage Proxy produced no readable output for $source"
        }
    }

    private fun validatedPath(path: String): String {
        require(path.isNotBlank() && path.startsWith("/")) {
            "Storage proxy path is invalid"
        }
        require('\n' !in path && '\r' !in path) {
            "Storage proxy path contains control characters"
        }
        val normalized = File(path).toPath().normalize().toString()
        require(isSafePath(normalized)) { "Storage proxy path is outside the allowed roots" }
        return normalized
    }

    private fun isSafePath(path: String): Boolean {
        if (!path.startsWith("/")) return false
        val normalized = runCatching { File(path).toPath().normalize().toString() }.getOrNull()
            ?: return false
        return allowedPrefixes.any { prefix ->
            normalized.startsWith(prefix) || normalized == prefix.removeSuffix("/")
        }
    }

    private fun packageFromPrivatePath(path: String): String? {
        val packageName = when {
            path.startsWith("/data/data/") ->
                path.removePrefix("/data/data/").substringBefore("/")
            path.startsWith("/data/user/") -> {
                val rest = path.removePrefix("/data/user/")
                rest.substringAfter("/", "").substringBefore("/")
            }
            else -> return null
        }
        return packageName.takeIf {
            it.length in 3..255 &&
                PACKAGE_NAME.matches(it) &&
                '.' in it
        }
    }

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
}
