package ctrl.mietze.veyraroot

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class VMarkedPlugin(
    val id: String,
    val name: String,
    val version: String,
    val minVeyra: String,
    val apkUrl: String?,
    val sha256: String?,
)

internal data class VMarkedManifest(
    val schemaVersion: Int,
    val channel: String,
    val plugins: List<VMarkedPlugin>,
)

internal object VMarkedRepository {
    private const val REMOTE_MANIFEST =
        "https://raw.githubusercontent.com/ctrl-mietze/VeyraRoot/main/vmarked/manifest.json"
    private const val CACHE_DIR = "vmarked"
    private const val CACHE_FILE = "manifest.json"

    fun refresh(context: Context): VMarkedManifest {
        val app = context.applicationContext
        val remote = runCatching { fetchRemote() }.getOrNull()
        if (remote != null) {
            runCatching {
                val dir = File(app.filesDir, CACHE_DIR)
                if (!dir.exists()) dir.mkdirs()
                File(dir, CACHE_FILE).writeText(remote)
            }
            return parse(remote)
        }

        val cached = runCatching {
            File(File(app.filesDir, CACHE_DIR), CACHE_FILE)
                .takeIf(File::isFile)
                ?.readText()
        }.getOrNull()
        if (!cached.isNullOrBlank()) return parse(cached)

        val bundled = app.assets.open("vmarked/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        return parse(bundled)
    }

    fun current(context: Context): VMarkedManifest = runCatching {
        val app = context.applicationContext
        val cached = File(File(app.filesDir, CACHE_DIR), CACHE_FILE)
        if (cached.isFile) parse(cached.readText())
        else app.assets.open("vmarked/manifest.json")
            .bufferedReader()
            .use { parse(it.readText()) }
    }.getOrElse {
        VMarkedManifest(schemaVersion = 1, channel = "stable", plugins = emptyList())
    }

    private fun fetchRemote(): String {
        val connection = (URL(REMOTE_MANIFEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_500
            readTimeout = 5_500
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE}")
        }
        return connection.useConnection { conn ->
            require(conn.responseCode in 200..299) {
                "vmarked manifest HTTP ${conn.responseCode}"
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun parse(raw: String): VMarkedManifest {
        val root = JSONObject(raw)
        val array = root.optJSONArray("plugins")
        val plugins = buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    val version = item.optString("version").trim()
                    if (id.isBlank() || name.isBlank() || version.isBlank()) continue
                    add(
                        VMarkedPlugin(
                            id = id,
                            name = name,
                            version = version,
                            minVeyra = item.optString("minVeyra", "1.0.0"),
                            apkUrl = item.optString("apkUrl").trim().takeIf(String::isNotBlank),
                            sha256 = item.optString("sha256").trim().takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }
        return VMarkedManifest(
            schemaVersion = root.optInt("schemaVersion", 1),
            channel = root.optString("channel", "stable"),
            plugins = plugins,
        )
    }

    private inline fun <T> HttpURLConnection.useConnection(
        block: (HttpURLConnection) -> T,
    ): T = try {
        block(this)
    } finally {
        disconnect()
    }
}
