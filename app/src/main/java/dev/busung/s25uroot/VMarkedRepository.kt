package ctrl.mietze.veyraroot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class VeyraMarketKind {
    DataPack,
    CompanionApk;

    companion object {
        fun parse(raw: String): VeyraMarketKind = when (raw.trim().lowercase(Locale.ROOT)) {
            "companion-apk", "apk", "companion" -> CompanionApk
            else -> DataPack
        }
    }
}

internal data class VeyraMarketSettingsEntry(
    val id: String,
    val title: String,
    val summary: String,
    val action: String,
    val value: String,
)

internal data class VeyraMarketEntry(
    val id: String,
    val name: String,
    val summary: String,
    val version: String,
    val versionCode: Long,
    val minVeyraCode: Long,
    val kind: VeyraMarketKind,
    val artifactUrl: String,
    val sha256: String?,
    val size: Long?,
    val packageName: String?,
    val signerSha256: String?,
    val capabilities: List<String>,
    val settingsEntries: List<VeyraMarketSettingsEntry>,
)

internal data class VeyraMarketManifest(
    val schemaVersion: Int,
    val channel: String,
    val entries: List<VeyraMarketEntry>,
)

internal data class VeyraMarketInstalled(
    val id: String,
    val version: String,
    val versionCode: Long,
    val kind: VeyraMarketKind,
    val packageName: String?,
    val settingsEntries: List<VeyraMarketSettingsEntry>,
)

internal sealed interface VeyraMarketInstallResult {
    data class Installed(val entry: VeyraMarketInstalled) : VeyraMarketInstallResult
    data class InstallerOpened(val entry: VeyraMarketEntry) : VeyraMarketInstallResult
}

internal object VeyraMarketRepository {
    private const val REMOTE_MANIFEST =
        "https://raw.githubusercontent.com/ctrl-mietze/Veyra/main/market/manifest.json"
    private const val BUNDLED_MANIFEST = "market/manifest.json"
    private const val CACHE_DIR = "market"
    private const val CACHE_MANIFEST = "manifest.json"
    private const val RECEIPTS_DIR = "receipts"
    private const val PACKS_DIR = "packs"
    private const val MAX_MANIFEST_BYTES = 512 * 1024
    private const val MAX_DATA_PACK_BYTES = 16L * 1024L * 1024L
    private const val MAX_APK_BYTES = 256L * 1024L * 1024L

    fun refresh(context: Context): VeyraMarketManifest {
        val remote = runCatching { fetchText(REMOTE_MANIFEST, MAX_MANIFEST_BYTES) }.getOrNull()
        if (remote != null) {
            val parsed = parse(remote)
            val cache = File(File(context.filesDir, CACHE_DIR), CACHE_MANIFEST)
            cache.parentFile?.mkdirs()
            runCatching { cache.writeText(remote) }
            return parsed
        }
        return current(context)
    }

    fun current(context: Context): VeyraMarketManifest = runCatching {
        val cache = File(File(context.filesDir, CACHE_DIR), CACHE_MANIFEST)
        if (cache.isFile) {
            parse(cache.readText())
        } else {
            context.assets.open(BUNDLED_MANIFEST).bufferedReader().use { parse(it.readText()) }
        }
    }.getOrElse {
        VeyraMarketManifest(1, "stable", emptyList())
    }

    fun installed(context: Context, entry: VeyraMarketEntry): VeyraMarketInstalled? {
        reconcilePending(context)
        val file = receiptFile(context, entry.id)
        if (!file.isFile) return null
        val installed = runCatching { parseReceipt(file.readText()) }.getOrNull() ?: return null
        if (installed.kind == VeyraMarketKind.CompanionApk) {
            val packageName = installed.packageName ?: return null
            if (!packageInstalled(context, packageName)) return null
        }
        return installed
    }

    fun installedSettings(context: Context): List<VeyraMarketSettingsEntry> {
        reconcilePending(context)
        val dir = File(File(context.filesDir, CACHE_DIR), RECEIPTS_DIR)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { runCatching { parseReceipt(it.readText()) }.getOrNull() }
            ?.filter {
                it.kind != VeyraMarketKind.CompanionApk ||
                    it.packageName?.let { pkg -> packageInstalled(context, pkg) } == true
            }
            ?.flatMap { it.settingsEntries }
            ?.distinctBy { it.id }
            .orEmpty()
    }

    fun install(
        context: Context,
        entry: VeyraMarketEntry,
        onProgress: (Float) -> Unit = {},
    ): VeyraMarketInstallResult {
        require(entry.minVeyraCode <= BuildConfig.VERSION_CODE.toLong()) {
            "This extension needs a newer Veyra Root build"
        }
        return when (entry.kind) {
            VeyraMarketKind.DataPack -> installDataPack(context, entry, onProgress)
            VeyraMarketKind.CompanionApk -> installCompanion(context, entry, onProgress)
        }
    }

    fun remove(context: Context, entry: VeyraMarketEntry): Boolean {
        return when (entry.kind) {
            VeyraMarketKind.DataPack -> {
                File(File(File(context.filesDir, CACHE_DIR), PACKS_DIR), entry.id).deleteRecursively()
                receiptFile(context, entry.id).delete()
                true
            }
            VeyraMarketKind.CompanionApk -> {
                val packageName = entry.packageName ?: return false
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                false
            }
        }
    }

    fun performSettingsAction(
        context: Context,
        entry: VeyraMarketSettingsEntry,
    ): String? = when (entry.action.lowercase(Locale.ROOT)) {
        "open-package" -> {
            val intent = context.packageManager.getLaunchIntentForPackage(entry.value)
                ?: return "Package is not installed"
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }
        "open-uri" -> {
            val uri = Uri.parse(entry.value)
            require(uri.scheme == "https" || uri.scheme == "http") {
                "Only web links are allowed"
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        }
        "info" -> entry.value.ifBlank { entry.summary }
        else -> "This extension action is not supported by this Veyra build"
    }

    private fun installDataPack(
        context: Context,
        entry: VeyraMarketEntry,
        onProgress: (Float) -> Unit,
    ): VeyraMarketInstallResult {
        val bytes = readArtifact(context, entry, MAX_DATA_PACK_BYTES, onProgress)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.optInt("schemaVersion") == 1) { "Unsupported data-pack schema" }
        require(json.optString("kind") in setOf("settings-data", "magic-ota-catalog")) {
            "Unsupported data-pack capability"
        }

        val target = File(
            File(File(File(context.filesDir, CACHE_DIR), PACKS_DIR), entry.id),
            entry.version,
        ).apply { mkdirs() }
        File(target, "payload.json").writeBytes(bytes)

        val installed = installedFrom(entry)
        writeReceipt(context, installed)
        return VeyraMarketInstallResult.Installed(installed)
    }

    private fun installCompanion(
        context: Context,
        entry: VeyraMarketEntry,
        onProgress: (Float) -> Unit,
    ): VeyraMarketInstallResult {
        val packageName = entry.packageName ?: error("Companion APK has no package name")
        require(packageName != context.packageName) {
            "Market companion packages may not replace Veyra Root"
        }

        val bytes = readArtifact(context, entry, MAX_APK_BYTES, onProgress)
        val dir = File(context.cacheDir, "market").apply { mkdirs() }
        val apk = File(dir, "${entry.id}-${entry.version}.apk")
        apk.writeBytes(bytes)
        verifyCompanion(context, apk, entry)

        val pending = installedFrom(entry)
        val pendingFile = pendingReceiptFile(context, entry.id)
        pendingFile.parentFile?.mkdirs()
        pendingFile.writeText(receiptJson(pending).toString())

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        return VeyraMarketInstallResult.InstallerOpened(entry)
    }

    fun reconcilePending(context: Context) {
        val dir = File(File(context.filesDir, CACHE_DIR), "pending")
        dir.listFiles()?.forEach { file ->
            val pending = runCatching { parseReceipt(file.readText()) }.getOrNull()
            if (pending != null &&
                pending.packageName?.let { packageInstalled(context, it) } == true
            ) {
                writeReceipt(context, pending)
                file.delete()
            }
        }
    }

    private fun installedFrom(entry: VeyraMarketEntry) = VeyraMarketInstalled(
        id = entry.id,
        version = entry.version,
        versionCode = entry.versionCode,
        kind = entry.kind,
        packageName = entry.packageName,
        settingsEntries = entry.settingsEntries,
    )

    private fun readArtifact(
        context: Context,
        entry: VeyraMarketEntry,
        maxBytes: Long,
        onProgress: (Float) -> Unit,
    ): ByteArray {
        val bytes = if (entry.artifactUrl.startsWith("asset://")) {
            context.assets.open(entry.artifactUrl.removePrefix("asset://")).use { it.readBytes() }
        } else {
            download(entry.artifactUrl, maxBytes, onProgress)
        }
        require(bytes.isNotEmpty()) { "Market artifact is empty" }
        require(bytes.size.toLong() <= maxBytes) { "Market artifact exceeds size limit" }
        entry.size?.let { require(bytes.size.toLong() == it) { "Market artifact size mismatch" } }
        entry.sha256?.let { expected ->
            require(expected.equals(sha256(bytes), ignoreCase = true)) {
                "Market artifact SHA-256 mismatch"
            }
        }
        onProgress(1f)
        return bytes
    }

    private fun verifyCompanion(
        context: Context,
        apk: File,
        entry: VeyraMarketEntry,
    ) {
        val expectedPackage = entry.packageName ?: error("Missing companion package")
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } else {
            manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: error("Market artifact is not an APK")

        require(archive.packageName == expectedPackage) { "Companion package name mismatch" }
        entry.signerSha256?.let { expected ->
            require(signerDigests(archive).any { it.equals(expected, ignoreCase = true) }) {
                "Companion APK signer mismatch"
            }
        }
    }

    private fun parse(raw: String): VeyraMarketManifest {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 1) { "Unsupported market schema" }
        val array = root.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val version = item.optString("version").trim()
                val artifactUrl = item.optString("artifactUrl").trim()
                if (id.isEmpty() || name.isEmpty() || version.isEmpty() || artifactUrl.isEmpty()) continue

                require(
                    artifactUrl.startsWith("https://", ignoreCase = true) ||
                        artifactUrl.startsWith("asset://")
                ) { "Market artifacts must use HTTPS or bundled assets" }

                add(
                    VeyraMarketEntry(
                        id = id,
                        name = name,
                        summary = item.optString("summary"),
                        version = version,
                        versionCode = item.optLong("versionCode", 1L),
                        minVeyraCode = item.optLong("minVeyraCode", 0L),
                        kind = VeyraMarketKind.parse(item.optString("kind")),
                        artifactUrl = artifactUrl,
                        sha256 = item.optString("sha256").trim().lowercase(Locale.ROOT)
                            .takeIf(String::isNotEmpty)
                            ?.also { require(isSha256(it)) { "Invalid market SHA-256" } },
                        size = item.optLong("size", 0L).takeIf { it > 0L },
                        packageName = item.optString("packageName").trim().takeIf(String::isNotEmpty),
                        signerSha256 = item.optString("signerSha256").trim().lowercase(Locale.ROOT)
                            .takeIf(String::isNotEmpty)
                            ?.also { require(isSha256(it)) { "Invalid companion signer" } },
                        capabilities = item.optJSONArray("capabilities").strings(),
                        settingsEntries = parseSettingsEntries(item.optJSONArray("settingsEntries")),
                    ),
                )
            }
        }
        return VeyraMarketManifest(1, root.optString("channel", "stable"), entries)
    }

    private fun parseSettingsEntries(array: JSONArray?): List<VeyraMarketSettingsEntry> =
        if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) continue
                add(
                    VeyraMarketSettingsEntry(
                        id = id,
                        title = title,
                        summary = item.optString("summary"),
                        action = item.optString("action", "info"),
                        value = item.optString("value"),
                    ),
                )
            }
        }

    private fun writeReceipt(context: Context, installed: VeyraMarketInstalled) {
        val file = receiptFile(context, installed.id)
        file.parentFile?.mkdirs()
        file.writeText(receiptJson(installed).toString(2))
    }

    private fun receiptJson(installed: VeyraMarketInstalled): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("id", installed.id)
            .put("version", installed.version)
            .put("versionCode", installed.versionCode)
            .put("kind", installed.kind.name)
            .put("packageName", installed.packageName ?: JSONObject.NULL)
            .put(
                "settingsEntries",
                JSONArray().apply {
                    installed.settingsEntries.forEach { entry ->
                        put(
                            JSONObject()
                                .put("id", entry.id)
                                .put("title", entry.title)
                                .put("summary", entry.summary)
                                .put("action", entry.action)
                                .put("value", entry.value),
                        )
                    }
                },
            )

    private fun parseReceipt(raw: String): VeyraMarketInstalled {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 1) { "Unsupported receipt schema" }
        return VeyraMarketInstalled(
            id = root.getString("id"),
            version = root.getString("version"),
            versionCode = root.optLong("versionCode", 1L),
            kind = VeyraMarketKind.valueOf(root.getString("kind")),
            packageName = root.optString("packageName").trim().takeIf(String::isNotEmpty),
            settingsEntries = parseSettingsEntries(root.optJSONArray("settingsEntries")),
        )
    }

    private fun receiptFile(context: Context, id: String): File =
        File(File(File(context.filesDir, CACHE_DIR), RECEIPTS_DIR), "$id.json")

    private fun pendingReceiptFile(context: Context, id: String): File =
        File(File(File(context.filesDir, CACHE_DIR), "pending"), "$id.json")

    private fun packageInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    @Suppress("DEPRECATION")
    private fun signerDigests(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners
            else signing.signingCertificateHistory
        } else {
            info.signatures ?: emptyArray()
        }
        return signatures.mapTo(linkedSetOf()) { sha256(it.toByteArray()) }
    }

    private fun download(
        url: String,
        maxBytes: Long,
        onProgress: (Float) -> Unit,
    ): ByteArray {
        require(url.startsWith("https://", ignoreCase = true)) { "HTTPS required" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE} Market")
        }
        try {
            require(connection.responseCode in 200..299) {
                "Market download HTTP ${connection.responseCode}"
            }
            val expected = connection.contentLengthLong
            require(expected <= 0L || expected <= maxBytes) { "Market artifact is too large" }
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Market artifact exceeds size limit" }
                    output.write(buffer, 0, read)
                    if (expected > 0) {
                        onProgress((total.toDouble() / expected).toFloat().coerceIn(0f, 1f))
                    }
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchText(url: String, maxBytes: Int): String =
        download(url, maxBytes.toLong()) {}.toString(Charsets.UTF_8)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
}
