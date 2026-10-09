package ctrl.mietze.veyraroot

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class VivoCatalogTrust {
    ExactDeviceKernel,
    KernelOnlyResearch,
    None,
}

internal data class VivoCatalogCandidate(
    val buildId: String,
    val status: String,
    val exploit: String,
    val kernelMatches: List<String>,
    val artifactUrl: String?,
    val sha256: String?,
    val size: Long?,
    val trust: VivoCatalogTrust,
    val deviceIds: List<String>,
) {
    val ready: Boolean get() = status.equals("ready", ignoreCase = true)
}

internal data class VivoCatalogReport(
    val updated: String,
    val source: String,
    val deviceMatches: List<String>,
    val candidates: List<VivoCatalogCandidate>,
    val error: String? = null,
) {
    fun lines(snapshot: DeviceSnapshot): List<String> = buildList {
        add("RootMyVivo live catalog · updated=${updated.ifBlank { "unknown" }} · source=$source")
        add("Target: ${snapshot.model} · ${snapshot.device} · ${snapshot.kernelRelease}")
        if (deviceMatches.isEmpty()) {
            add("Device identity: no exact catalog device entry")
        } else {
            add("Device identity: ${deviceMatches.joinToString()}")
        }

        val exact = candidates.filter { it.trust == VivoCatalogTrust.ExactDeviceKernel }
        val research = candidates.filter { it.trust == VivoCatalogTrust.KernelOnlyResearch }

        if (exact.isEmpty()) {
            add("Exact device+kernel payload: none")
        } else {
            exact.forEach { candidate ->
                add(
                    "[exact/${candidate.status}] ${candidate.buildId} · " +
                        candidate.kernelMatches.joinToString(" / "),
                )
                if (candidate.ready) {
                    add(
                        "  catalog status=ready · integrity metadata=" +
                            if (candidate.sha256 != null && candidate.size != null) {
                                "complete"
                            } else {
                                "incomplete"
                            },
                    )
                } else {
                    add("  catalog status=${candidate.status}; Veyra keeps it research-only")
                }
            }
        }

        research.take(5).forEach { candidate ->
            add(
                "[kernel-only research/${candidate.status}] ${candidate.buildId} · " +
                    "same kernel signature but no exact device identity",
            )
        }

        error?.let { add("Refresh note: $it") }
        add("Veyra never substitutes a kernel-only or experimental catalog hit for an exact runnable target.")
    }
}

internal object VivoPayloadCatalog {
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/zenyxx-xd/RootMyVivo-Payloads/main/catalog/devices.json"
    private const val CACHE_DIR = "research-catalogs"
    private const val CACHE_FILE = "rootmyvivo-devices-v5.json"
    private const val MAX_BYTES = 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    fun refresh(
        context: Context,
        snapshot: DeviceSnapshot = DeviceSnapshot.current(),
    ): VivoCatalogReport {
        val cache = cacheFile(context)
        val remote = runCatching { fetch() }
        val raw = remote.getOrNull()

        if (raw != null) {
            runCatching {
                cache.parentFile?.mkdirs()
                cache.writeText(raw)
            }
            return parse(raw, snapshot, source = "live")
        }

        if (cache.isFile) {
            return runCatching {
                parse(
                    cache.readText(),
                    snapshot,
                    source = "cache",
                    refreshError = remote.exceptionOrNull()?.message,
                )
            }.getOrElse { cachedError ->
                VivoCatalogReport(
                    updated = "",
                    source = "unavailable",
                    deviceMatches = emptyList(),
                    candidates = emptyList(),
                    error = cachedError.message ?: "catalog cache is unreadable",
                )
            }
        }

        return VivoCatalogReport(
            updated = "",
            source = "unavailable",
            deviceMatches = emptyList(),
            candidates = emptyList(),
            error = remote.exceptionOrNull()?.message ?: "catalog unavailable",
        )
    }

    internal fun parse(
        raw: String,
        snapshot: DeviceSnapshot,
        source: String = "test",
        refreshError: String? = null,
    ): VivoCatalogReport {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 5) {
            "Unsupported RootMyVivo catalog schema"
        }

        val builds = root.optJSONObject("builds") ?: JSONObject()
        val devices = root.optJSONArray("devices") ?: JSONArray()
        val identity = identity(snapshot)
        val matchedDeviceIds = ArrayList<String>()
        val deviceBuilds = linkedSetOf<String>()

        for (index in 0 until devices.length()) {
            val device = devices.optJSONObject(index) ?: continue
            if (!deviceMatches(device, identity)) continue

            val id = device.optString("id").trim()
            if (id.isNotEmpty()) matchedDeviceIds += id

            val kernels = device.optJSONArray("kernels") ?: JSONArray()
            for (kernelIndex in 0 until kernels.length()) {
                val build = kernels.optJSONObject(kernelIndex)
                    ?.optString("build")
                    ?.trim()
                    .orEmpty()
                if (build.isNotEmpty()) deviceBuilds += build
            }
        }

        val candidates = ArrayList<VivoCatalogCandidate>()
        val keys = builds.keys()
        while (keys.hasNext()) {
            val buildId = keys.next()
            val build = builds.optJSONObject(buildId) ?: continue
            val matches = build.optJSONArray("match").strings()
            if (!matches.any { kernelMatches(snapshot.kernelRelease, it) }) continue

            val referencedByExactDevice = buildId in deviceBuilds
            val file = build.optJSONObject("file")
            val sha = file?.optString("sha256")
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            val size = file?.optLong("size", 0L)?.takeIf { it > 0L }
            val artifactUrl = file?.optString("url")
                ?.trim()
                ?.takeIf { it.startsWith("https://", ignoreCase = true) }

            candidates += VivoCatalogCandidate(
                buildId = buildId,
                status = build.optString("status", "unknown"),
                exploit = build.optString("exploit"),
                kernelMatches = matches,
                artifactUrl = artifactUrl,
                sha256 = sha,
                size = size,
                trust = if (referencedByExactDevice) {
                    VivoCatalogTrust.ExactDeviceKernel
                } else {
                    VivoCatalogTrust.KernelOnlyResearch
                },
                deviceIds = if (referencedByExactDevice) matchedDeviceIds.toList() else emptyList(),
            )
        }

        return VivoCatalogReport(
            updated = root.optString("updated"),
            source = source,
            deviceMatches = matchedDeviceIds.distinct(),
            candidates = candidates.sortedWith(
                compareByDescending<VivoCatalogCandidate> {
                    it.trust == VivoCatalogTrust.ExactDeviceKernel
                }.thenByDescending { it.ready }
                    .thenBy { it.buildId },
            ),
            error = refreshError,
        )
    }

    private fun identity(snapshot: DeviceSnapshot): String =
        listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.buildId,
            snapshot.fingerprint,
        ).joinToString(" ").lowercase(Locale.ROOT)

    private fun deviceMatches(device: JSONObject, identity: String): Boolean {
        val terms = buildList {
            device.optString("code").trim().takeIf(String::isNotEmpty)?.let(::add)
            device.optString("marketName").trim().takeIf(String::isNotEmpty)?.let(::add)
            addAll(device.optJSONArray("models").strings())
            addAll(device.optJSONArray("names").strings())
        }
        return terms.any { term ->
            val normalized = term.lowercase(Locale.ROOT).trim()
            normalized.length >= 4 && identity.contains(normalized)
        }
    }

    private fun kernelMatches(live: String, catalog: String): Boolean {
        val target = catalog.trim()
        if (target.isEmpty()) return false
        return live.equals(target, ignoreCase = true) ||
            live.startsWith("$target-", ignoreCase = true)
    }

    private fun fetch(): String {
        val connection = (java.net.URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE} VivoCatalog")
        }
        try {
            require(connection.responseCode in 200..299) {
                "RootMyVivo catalog HTTP ${connection.responseCode}"
            }
            connection.contentLengthLong.takeIf { it > 0L }?.let { size ->
                require(size <= MAX_BYTES) { "RootMyVivo catalog exceeds size limit" }
            }

            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES) { "RootMyVivo catalog exceeds size limit" }
                    out.write(buffer, 0, read)
                }
                return out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheFile(context: Context): java.io.File =
        java.io.File(java.io.File(context.filesDir, CACHE_DIR), CACHE_FILE)

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
}
