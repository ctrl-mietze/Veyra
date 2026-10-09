package ctrl.mietze.veyraroot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String?,
    val releaseUrl: String,
    val versionCode: Long = 0L,
    val minimumVersionCode: Long = 0L,
    val required: Boolean = false,
    val sha256: String? = null,
    val packageName: String = BuildConfig.APPLICATION_ID,
    val signerSha256: String? = null,
)

internal sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object Current : UpdateCheck
    data object Disabled : UpdateCheck
}

internal sealed interface UpdateInstallResult {
    data object InstalledByCVeyra : UpdateInstallResult
    data object InstallerOpened : UpdateInstallResult
}

internal object AppUpdater {
    /**
     * Compatibility helpers kept pure so update ordering stays independently testable.
     * The live updater uses versionCode as the authority; these helpers are still useful for
     * displaying/comparing human release names and for older call sites/tests.
     */
    internal fun versionBase(raw: String): String? {
        val stripped = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('+')
            .substringBefore('-')
            .trim()
        if (!Regex("^\\d+(?:\\.\\d+)+$").matches(stripped)) return null
        return stripped
    }

    internal fun compareVersions(left: String, right: String): Int {
        val a = versionBase(left) ?: return left.compareTo(right)
        val b = versionBase(right) ?: return left.compareTo(right)
        val ap = a.split('.').map { it.toLongOrNull() ?: 0L }
        val bp = b.split('.').map { it.toLongOrNull() ?: 0L }
        val count = maxOf(ap.size, bp.size)
        for (index in 0 until count) {
            val av = ap.getOrElse(index) { 0L }
            val bv = bp.getOrElse(index) { 0L }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    internal fun isUpdateAvailable(latestVersion: String, currentVersion: String): Boolean {
        if (latestVersion.isBlank()) return false
        val latestBase = versionBase(latestVersion)
        val currentBase = versionBase(currentVersion)
        return if (latestBase != null && currentBase != null) {
            compareVersions(latestBase, currentBase) > 0
        } else {
            latestVersion.trim() != currentVersion.trim()
        }
    }

    private const val MAX_METADATA_BYTES = 128 * 1024
    private const val MAX_APK_BYTES = 256L * 1024L * 1024L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 20_000

    fun check(context: Context): UpdateCheck {
        val root = JSONObject(
            fetchText(PublicReleaseInfo.updateChannelUrl(), MAX_METADATA_BYTES),
        )
        require(root.optInt("schemaVersion") == 1) { "Unsupported update metadata schema" }

        val packageName = root.optString("packageName", context.packageName)
        require(packageName == context.packageName) { "Update channel is for another package" }

        val versionCode = root.optLong("versionCode", 0L)
        val minimumVersionCode = root.optLong("minimumVersionCode", 0L)
        require(versionCode > 0L) { "Invalid remote versionCode" }
        require(minimumVersionCode >= 0L) { "Invalid minimumVersionCode" }

        if (versionCode <= BuildConfig.VERSION_CODE.toLong()) return UpdateCheck.Current

        val sha = root.optString("sha256").trim().lowercase(Locale.ROOT)
            .takeIf(String::isNotEmpty)
        if (sha != null) require(isSha256(sha)) { "Invalid APK SHA-256 in update channel" }
        val signer = root.optString("signerSha256").trim().lowercase(Locale.ROOT)
            .takeIf(String::isNotEmpty)
        if (signer != null) require(isSha256(signer)) { "Invalid signer SHA-256 in update channel" }

        val apkUrl = root.optString("apkUrl").trim().takeIf(String::isNotEmpty)
        if (apkUrl != null) require(apkUrl.startsWith("https://", ignoreCase = true)) {
            "Update APK must use HTTPS"
        }
        val releaseUrl = root.optString("releaseUrl").trim()
        if (releaseUrl.isNotEmpty()) require(releaseUrl.startsWith("https://", ignoreCase = true)) {
            "Release URL must use HTTPS"
        }

        val required = true

        return UpdateCheck.Available(
            UpdateInfo(
                versionName = root.optString("versionName", versionCode.toString()),
                apkUrl = apkUrl,
                releaseUrl = releaseUrl,
                versionCode = versionCode,
                minimumVersionCode = minimumVersionCode,
                required = required,
                sha256 = sha,
                packageName = packageName,
                signerSha256 = signer,
            ),
        )
    }

    fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit = {},
    ): File {
        val url = info.apkUrl ?: error("This update does not publish an APK yet")
        require(url.startsWith("https://", ignoreCase = true)) { "Update APK must use HTTPS" }

        val directory = File(context.cacheDir, "updates").apply {
            require(mkdirs() || isDirectory) { "Could not create update cache" }
        }
        directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        val destination = File(directory, "VeyraRoot-${info.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        val connection = open(url)
        try {
            require(connection.responseCode in 200..299) {
                "Update download HTTP ${connection.responseCode}"
            }
            val expectedSize = connection.contentLengthLong
            require(expectedSize <= 0 || expectedSize <= MAX_APK_BYTES) {
                "Update APK is unexpectedly large"
            }

            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination, false).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_APK_BYTES) { "Update APK exceeds size limit" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        if (expectedSize > 0) {
                            onProgress((total.toDouble() / expectedSize.toDouble()).toFloat().coerceIn(0f, 1f))
                        }
                    }
                    output.fd.sync()
                }
            }
            require(total > 0L) { "Update download was empty" }

            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            info.sha256?.let { expected ->
                require(expected.equals(actualSha, ignoreCase = true)) {
                    "Update SHA-256 mismatch"
                }
            }

            verifyPackage(context, destination, info)
            onProgress(1f)
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun install(context: Context, apk: File): UpdateInstallResult {
        require(apk.isFile && apk.length() > 0L) { "Update APK is missing" }

        if (CVeyraPreferences.backend(context) == CVeyraBackend.Root ||
            CVeyraAccessStore.isActive(context)
        ) {
            val staged = "/data/local/tmp/veyra-update.apk"
            val command = buildString {
                append("cp ")
                append(shellQuote(apk.absolutePath))
                append(' ')
                append(shellQuote(staged))
                append(" && chmod 0644 ")
                append(shellQuote(staged))
                append(" && pm install -r ")
                append(shellQuote(staged))
                append("; RC=\$?; rm -f ")
                append(shellQuote(staged))
                append("; exit \$RC")
            }
            val result = CVeyraController.rootShell(context, command)
            if (result?.exitCode == 0 &&
                result.output.contains("Success", ignoreCase = true)
            ) {
                return UpdateInstallResult.InstalledByCVeyra
            }
            AppLog.warn(
                AppLogTags.CATALOG,
                "CVeyra update install fell back to Android installer: " +
                    (result?.output?.take(240) ?: "root broker unavailable"),
            )
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return UpdateInstallResult.InstallerOpened
    }

    private fun verifyPackage(context: Context, apk: File, info: UpdateInfo) {
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } else {
            manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: error("Downloaded file is not a readable APK")

        require(archive.packageName == info.packageName && archive.packageName == context.packageName) {
            "Downloaded APK package does not match Veyra Root"
        }

        val downloadedSigners = signerDigests(archive)
        require(downloadedSigners.isNotEmpty()) { "Downloaded APK has no signer" }

        @Suppress("DEPRECATION")
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val installedSigners = signerDigests(installed)
        require(installedSigners.isNotEmpty() && downloadedSigners.any { it in installedSigners }) {
            "Downloaded APK signer does not match the installed Veyra Root"
        }

        info.signerSha256?.let { expected ->
            require(downloadedSigners.any { it.equals(expected, ignoreCase = true) }) {
                "Downloaded APK does not match the published Veyra signing certificate"
            }
        }

        if (info.versionCode > 0L) {
            val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archive.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archive.versionCode.toLong()
            }
            require(archiveCode == info.versionCode) {
                "Downloaded APK versionCode ${archiveCode} does not match metadata ${info.versionCode}"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
        } else {
            info.signatures ?: emptyArray()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun fetchText(url: String, maxBytes: Int): String {
        val connection = open(url)
        try {
            require(connection.responseCode in 200..299) {
                "Update check HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Update metadata is too large" }
                    output.write(buffer, 0, read)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://", ignoreCase = true)) { "HTTPS required" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE} Updater")
            setRequestProperty("Accept", "application/json,application/vnd.android.package-archive,*/*")
        }
    }
}
