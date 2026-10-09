package ctrl.mietze.veyraroot

import android.content.Context
import com.kernelpack.ota.OtaPayloadExtractor
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Finds the exact boot image Magic Builder needs without making "CVeyra root" a prerequisite.
 *
 * Order:
 *  1. live active-slot capture through CVeyra root / storage proxy;
 *  2. known-good OTA history for this exact kernel;
 *  3. Veyra OTA catalog;
 *  4. OEM/community provider catalogs.
 *
 * A downloaded candidate is never accepted because its phone name looks right. Its extracted kernel
 * release has to equal the running device's full uname release before it can become builder input.
 */
internal object MagicBootResolver {
    private const val MIN_BOOT_BYTES = 4L * 1024L * 1024L
    private const val MAX_DIRECT_BOOT_BYTES = 256L * 1024L * 1024L

    /** Ten are ranked/checked; only the strongest few are allowed to consume network payload data. */
    private const val MAX_METADATA_CANDIDATES = 10
    private const val MAX_NETWORK_ATTEMPTS = 4

    suspend fun resolve(
        context: Context,
        onLog: (String) -> Unit = {},
    ): MagicBuilderCapture {
        val snapshot = DeviceSnapshot.current()
        val failures = ArrayList<String>()

        if (liveCapturePossible(context)) {
            onLog("Magic input 1/4: checking live active boot")
            val live = runCatching { MagicBuilderController.captureLiveBoot(context) }
            live.getOrNull()?.let {
                onLog("Live boot capture matched ${snapshot.kernelRelease}")
                return it
            }
            val reason = live.exceptionOrNull()?.message ?: "unknown live-capture failure"
            failures += "live: $reason"
            onLog("Live capture unavailable: $reason")
        } else {
            onLog("Live boot capture is not available; OTA intelligence takes over")
        }

        onLog("Magic input 2/4: resolving OTA / stock-boot candidates")
        val ranked = MagicOtaCatalog.candidates(context, snapshot, onLog)
            .take(MAX_METADATA_CANDIDATES)
        if (ranked.isEmpty()) {
            error(
                "No exact live boot and no OTA candidate is known for " +
                    "${snapshot.model} / ${snapshot.buildId}. " +
                    failures.joinToString(" | "),
            )
        }

        val exactHints = ranked.filter { it.looksExactFor(snapshot) }
        val downloadPlan = (
            exactHints +
                ranked.filterNot { it in exactHints }
            )
            .distinctBy { it.url }
            .take(MAX_NETWORK_ATTEMPTS)

        onLog(
            "Magic input 3/4: ${ranked.size} metadata candidates checked; " +
                "${downloadPlan.size} strongest artifact(s) may be read",
        )

        downloadPlan.forEachIndexed { index, candidate ->
            onLog(
                "Candidate ${index + 1}/${downloadPlan.size}: " +
                    "${candidate.provider} · ${candidate.label}",
            )
            val attempt = runCatching {
                val file = when (candidate.type) {
                    MagicOtaArtifactType.BootImage ->
                        downloadBootImage(context, candidate, onLog)
                    MagicOtaArtifactType.FullOta ->
                        extractBootFromOta(context, candidate, onLog)
                }
                validateExactBoot(file, snapshot, candidate)
                MagicOtaCatalog.rememberSuccessful(context, snapshot, candidate)
                MagicBuilderCapture(
                    file = file,
                    blockDevice = "OTA · ${candidate.provider} · " +
                        candidate.build.ifBlank { candidate.label },
                )
            }

            attempt.getOrNull()?.let { capture ->
                onLog("Exact OTA boot match accepted: ${capture.blockDevice}")
                return capture
            }

            val reason = attempt.exceptionOrNull()?.message ?: "unknown candidate failure"
            failures += "${candidate.provider}/${candidate.build.ifBlank { candidate.id }}: $reason"
            onLog("Candidate rejected: $reason")
        }

        onLog("Magic input 4/4: no candidate produced the running kernel")
        error(
            "Magic Builder could not obtain an exact boot image for " +
                "${snapshot.kernelRelease}. " +
                failures.takeLast(6).joinToString(" | "),
        )
    }

    private fun liveCapturePossible(context: Context): Boolean {
        if (!CVeyraPreferences.enabled(context)) return false
        return when (CVeyraPreferences.backend(context)) {
            CVeyraBackend.Root -> true
            CVeyraBackend.WirelessAdb -> CVeyraPrivilegedStorageProxy.available(context)
            CVeyraBackend.None -> false
        }
    }

    private fun MagicOtaCandidate.looksExactFor(snapshot: DeviceSnapshot): Boolean {
        if (kernelRelease.isNotBlank() &&
            kernelRelease.equals(snapshot.kernelRelease, ignoreCase = true)
        ) return true
        if (build.isBlank()) return false
        return snapshot.buildId.equals(build, ignoreCase = true) ||
            build.contains(snapshot.buildId, ignoreCase = true) ||
            snapshot.buildId.contains(build, ignoreCase = true) ||
            snapshot.fingerprint.contains(build, ignoreCase = true)
    }

    private fun validateExactBoot(
        file: File,
        snapshot: DeviceSnapshot,
        candidate: MagicOtaCandidate,
    ) {
        require(file.isFile && file.length() >= MIN_BOOT_BYTES) {
            "boot image is only ${file.length()} bytes"
        }

        val release = MagicBuilderController.kernelReleaseOfBootImage(file)
            ?: error("kernel release could not be read from extracted boot image")
        require(release.equals(snapshot.kernelRelease, ignoreCase = true)) {
            "kernel $release does not equal running ${snapshot.kernelRelease}"
        }
        if (candidate.kernelRelease.isNotBlank()) {
            require(release.equals(candidate.kernelRelease, ignoreCase = true)) {
                "catalog kernel ${candidate.kernelRelease} does not equal extracted $release"
            }
        }
    }

    private suspend fun extractBootFromOta(
        context: Context,
        candidate: MagicOtaCandidate,
        onLog: (String) -> Unit,
    ): File {
        val directory = File(context.cacheDir, "magic-ota-resolver").apply {
            require(mkdirs() || isDirectory) { "Cannot create OTA resolver cache" }
        }
        purge(directory)
        val result = OtaPayloadExtractor.extractPartitions(candidate.url, directory) { raw ->
            val line = raw.trim()
            if (line.isNotEmpty()) onLog("OTA: $line")
        }
        val source = result.bootFile
        require(source.isFile && source.length() >= MIN_BOOT_BYTES) {
            "OTA extractor did not return a usable boot image"
        }

        val destination = finalBootFile(context)
        source.copyTo(destination, overwrite = true)
        destination.setReadable(true, true)
        return destination
    }

    private fun downloadBootImage(
        context: Context,
        candidate: MagicOtaCandidate,
        onLog: (String) -> Unit,
    ): File {
        val destination = finalBootFile(context)
        destination.delete()
        val connection = (URL(candidate.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE} MagicOTA")
            setRequestProperty("Accept", "application/octet-stream,*/*")
        }

        try {
            require(connection.responseCode in 200..299) {
                "boot download HTTP ${connection.responseCode}"
            }
            val declared = connection.contentLengthLong
            require(declared <= 0 || declared in MIN_BOOT_BYTES..MAX_DIRECT_BOOT_BYTES) {
                "unexpected boot image size $declared"
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination, false).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_DIRECT_BOOT_BYTES) {
                            "boot image exceeds $MAX_DIRECT_BOOT_BYTES bytes"
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            require(total >= MIN_BOOT_BYTES) { "downloaded boot image is too small ($total)" }

            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            candidate.sha256?.let { expected ->
                require(expected.equals(actualSha, ignoreCase = true)) {
                    "boot SHA-256 mismatch"
                }
            }
            onLog(
                "Stock boot downloaded: $total bytes · sha256=${actualSha.take(12)}…",
            )
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun finalBootFile(context: Context): File {
        val directory = File(context.filesDir, "magic-builder").apply {
            require(mkdirs() || isDirectory) { "Cannot create Magic Builder work directory" }
        }
        return File(directory, "auto-resolved-boot.img")
    }

    private fun purge(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile &&
                (file.name.startsWith("ksuroot_ota_") || file.name.startsWith("veyra_ota_"))
            ) {
                file.delete()
            }
        }
    }
}
