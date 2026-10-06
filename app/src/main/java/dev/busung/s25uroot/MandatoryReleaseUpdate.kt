package ctrl.mietze.veyraroot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class RequiredReleaseUpdate(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val digest: String?,
)

internal object MandatoryReleaseUpdate {
    private const val LATEST_API =
        "https://api.github.com/repos/ctrl-mietze/VeyraRoot/releases/latest"

    fun check(): RequiredReleaseUpdate? {
        if (!BuildConfig.RELEASE_HARDENED) return null
        val raw = httpText(LATEST_API) ?: return null
        val json = JSONObject(raw)
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null

        val latest = normalizeVersion(json.optString("tag_name"))
        val current = normalizeVersion(BuildConfig.VERSION_BASE)
        if (latest.isBlank() || compareVersions(latest, current) <= 0) return null

        var apkUrl: String? = null
        var apkName: String? = null
        var digest: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                apkUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                apkName = name
                digest = asset.optString("digest")
                    .removePrefix("sha256:")
                    .trim()
                    .takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                break
            }
        }

        return RequiredReleaseUpdate(
            version = latest,
            releaseUrl = json.optString("html_url"),
            apkUrl = apkUrl,
            apkName = apkName,
            digest = digest,
        )
    }

    fun install(context: Context, update: RequiredReleaseUpdate): Result<Unit> = runCatching {
        val app = context.applicationContext
        val url = update.apkUrl
        if (url.isNullOrBlank()) {
            val page = Uri.parse(update.releaseUrl)
            context.startActivity(
                Intent(Intent.ACTION_VIEW, page)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return@runCatching
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return@runCatching
        }

        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, update.apkName ?: "VeyraRoot-update.apk")
        val temporary = File(dir, target.name + ".part")
        download(url, temporary)

        update.digest?.let { expected ->
            val actual = sha256(temporary)
            require(MessageDigest.isEqual(
                actual.lowercase().toByteArray(),
                expected.lowercase().toByteArray(),
            )) { "Downloaded update SHA-256 does not match the GitHub release asset." }
        }

        if (target.exists()) target.delete()
        require(temporary.renameTo(target)) { "Could not prepare downloaded update." }

        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            target,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }

    private fun httpText(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE}")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE}")
        }
        try {
            require(connection.responseCode in 200..299) {
                "Update download failed: HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeVersion(value: String): String =
        value.trim().removePrefix("v").substringBefore('+').substringBefore('-')

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}

@Composable
internal fun MandatoryReleaseGate(
    content: @Composable () -> Unit,
) {
    if (!BuildConfig.RELEASE_HARDENED) {
        content()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    var required by remember { mutableStateOf<RequiredReleaseUpdate?>(null) }
    var installing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val update = withContext(Dispatchers.IO) { MandatoryReleaseUpdate.check() }
        required = update
        checked = true
    }

    LaunchedEffect(Unit) { refresh() }

    if (!checked) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(62.dp),
                )
                CircularProgressIndicator(modifier = Modifier.padding(top = 20.dp))
            }
        }
        return
    }

    val update = required
    if (update == null) {
        content()
        return
    }

    BackHandler(enabled = true) { }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_veyra_mark),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Update required",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Veyra Root ${BuildConfig.VERSION_BASE} must be updated to v${update.version} before you can continue.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                enabled = !installing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                onClick = {
                    installing = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            MandatoryReleaseUpdate.install(context, update)
                        }
                        result.onFailure {
                            error = it.message ?: it.javaClass.simpleName
                        }
                        installing = false
                    }
                },
            ) {
                if (installing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Text("Install required update", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(
                "There is no skip option in the public release channel.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
