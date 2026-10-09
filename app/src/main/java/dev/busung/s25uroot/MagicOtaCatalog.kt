package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal enum class MagicOtaArtifactType {
    FullOta,
    BootImage;

    companion object {
        fun parse(raw: String): MagicOtaArtifactType = when (raw.trim().lowercase(Locale.ROOT)) {
            "boot", "boot-image", "boot_image" -> BootImage
            else -> FullOta
        }
    }
}

internal data class MagicOtaCandidate(
    val id: String,
    val provider: String,
    val type: MagicOtaArtifactType,
    val url: String,
    val label: String,
    val build: String = "",
    val kernelRelease: String = "",
    val sha256: String? = null,
    val priority: Int = 0,
) {
    fun score(snapshot: DeviceSnapshot): Int {
        var score = priority
        val wantedBuild = snapshot.buildId.trim()
        val candidateBuild = build.trim()
        if (candidateBuild.isNotEmpty()) {
            score += 10
            when {
                wantedBuild.equals(candidateBuild, ignoreCase = true) -> score += 260
                wantedBuild.isNotEmpty() &&
                    candidateBuild.contains(wantedBuild, ignoreCase = true) -> score += 220
                wantedBuild.isNotEmpty() &&
                    wantedBuild.contains(candidateBuild, ignoreCase = true) -> score += 200
                snapshot.fingerprint.contains(candidateBuild, ignoreCase = true) -> score += 180
            }
        }
        if (kernelRelease.isNotBlank()) {
            score += if (kernelRelease.equals(snapshot.kernelRelease, ignoreCase = true)) 320 else -180
        }
        if (type == MagicOtaArtifactType.BootImage) score += 25
        if (provider == "Veyra known-good") score += 400
        return score
    }

    val exactBuildHint: Boolean
        get() = build.isNotBlank()
}

internal data class MagicOtaProfile(
    val id: String,
    val manufacturers: Set<String>,
    val models: Set<String>,
    val modelPrefixes: Set<String>,
    val devices: Set<String>,
    val providers: Set<String>,
    val maxCandidates: Int,
) {
    fun matches(snapshot: DeviceSnapshot): Boolean {
        if (manufacturers.isNotEmpty() &&
            manufacturers.none { snapshot.manufacturer.contains(it, ignoreCase = true) }
        ) return false
        if (devices.isNotEmpty() &&
            devices.none { it.equals(snapshot.device, ignoreCase = true) }
        ) return false
        if (models.isNotEmpty() &&
            models.none {
                it.equals(snapshot.model, ignoreCase = true) ||
                    snapshot.fingerprint.contains(it, ignoreCase = true)
            }
        ) return false
        if (modelPrefixes.isNotEmpty() &&
            modelPrefixes.none {
                snapshot.model.startsWith(it, ignoreCase = true) ||
                    snapshot.fingerprint.contains(it, ignoreCase = true)
            }
        ) return false
        return manufacturers.isNotEmpty() || models.isNotEmpty() ||
            modelPrefixes.isNotEmpty() || devices.isNotEmpty()
    }

    fun specificity(snapshot: DeviceSnapshot): Int {
        var score = 0
        if (devices.any { it.equals(snapshot.device, ignoreCase = true) }) score += 100
        if (models.any { it.equals(snapshot.model, ignoreCase = true) }) score += 80
        if (modelPrefixes.any { snapshot.model.startsWith(it, ignoreCase = true) }) score += 60
        if (manufacturers.any { snapshot.manufacturer.contains(it, ignoreCase = true) }) score += 20
        return score
    }
}

internal data class MagicOtaCatalogState(
    val profiles: List<MagicOtaProfile>,
    val entriesByProfile: Map<String, List<MagicOtaCandidate>>,
    val source: String,
)

internal object MagicOtaCatalog {
    private const val ASSET_PATH = "magic-builder/ota-catalog-v1.json"
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/ctrl-mietze/Veyra/main/magic/ota-catalog-v1.json"
    private const val GOOGLE_PIXEL_INDEX =
        "https://raw.githubusercontent.com/CNMan/Nexus5X/master/FullOTAImages.txt"
    private const val NOTHING_ARCHIVE =
        "https://raw.githubusercontent.com/quintenvandamme/nothing_archive/main/nothing.json"

    private const val CACHE_DIR = "magic-builder/catalog"
    private const val REMOTE_CACHE = "veyra-ota-catalog-v1.json"
    private const val GOOGLE_CACHE = "google-full-ota-index.txt"
    private const val NOTHING_CACHE = "nothing-archive.json"
    private const val HISTORY_FILE = "ota-history-v1.json"

    private const val MAX_CATALOG_BYTES = 3 * 1024 * 1024
    private const val MAX_PROVIDER_BYTES = 8 * 1024 * 1024
    private const val CACHE_TTL_MS = 12L * 60L * 60L * 1000L
    private const val DEFAULT_MAX_CANDIDATES = 10

    fun candidates(
        context: Context,
        snapshot: DeviceSnapshot,
        onLog: (String) -> Unit = {},
    ): List<MagicOtaCandidate> {
        val catalog = load(context, onLog)
        val matches = catalog.profiles
            .filter { it.matches(snapshot) }
            .sortedByDescending { it.specificity(snapshot) }

        val specific = matches.filter { it.specificity(snapshot) > 20 }
        val activeProfiles = if (specific.isNotEmpty()) specific else matches
        val profileIds = activeProfiles.mapTo(linkedSetOf()) { it.id }
        val providers = activeProfiles.flatMapTo(linkedSetOf()) { it.providers }
        val maxCandidates = activeProfiles
            .map { it.maxCandidates }
            .maxOrNull()
            ?.coerceIn(1, 20)
            ?: DEFAULT_MAX_CANDIDATES

        val result = ArrayList<MagicOtaCandidate>()
        result += historyCandidates(context, snapshot)
        for (profile in activeProfiles) {
            result += catalog.entriesByProfile[profile.id].orEmpty()
        }

        if (PROVIDER_GOOGLE in providers) {
            result += runCatching { googlePixelCandidates(context, snapshot, onLog) }
                .onFailure { onLog("Pixel OTA index unavailable: ${it.message}") }
                .getOrDefault(emptyList())
        }
        if (PROVIDER_NOTHING in providers) {
            result += runCatching { nothingCandidates(context, snapshot, onLog) }
                .onFailure { onLog("Nothing OTA index unavailable: ${it.message}") }
                .getOrDefault(emptyList())
        }

        // Market data packs are deliberately data-only. They can extend this catalog without
        // loading untrusted code into Veyra's process.
        result += marketCandidates(context, profileIds, snapshot, onLog)

        val deduped = result
            .filter { it.url.startsWith("https://", ignoreCase = true) }
            .filter { it.kernelRelease.isBlank() ||
                it.kernelRelease.equals(snapshot.kernelRelease, ignoreCase = true) }
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<MagicOtaCandidate> { it.score(snapshot) }
                    .thenByDescending { it.build }
                    .thenBy { it.provider },
            )
            .take(maxCandidates)

        onLog(
            "OTA intelligence: ${activeProfiles.size} device profile(s), " +
                "${providers.size} provider(s), ${deduped.size} candidate(s)",
        )
        deduped.forEachIndexed { index, candidate ->
            onLog(
                "#${index + 1} ${candidate.provider}: ${candidate.label}" +
                    candidate.build.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
            )
        }
        return deduped
    }

    fun profileSummary(context: Context, snapshot: DeviceSnapshot): String {
        val catalog = load(context) {}
        val matches = catalog.profiles.filter { it.matches(snapshot) }
        if (matches.isEmpty()) return "No OTA profile"
        val best = matches.maxByOrNull { it.specificity(snapshot) } ?: return "No OTA profile"
        return "${best.id} · ${best.providers.joinToString()}"
    }

    fun rememberSuccessful(
        context: Context,
        snapshot: DeviceSnapshot,
        candidate: MagicOtaCandidate,
    ) {
        val file = historyFile(context)
        val root = runCatching {
            if (file.isFile) JSONObject(file.readText()) else JSONObject()
        }.getOrElse { JSONObject() }
        val entries = root.optJSONArray("entries") ?: JSONArray()
        val next = JSONArray()
        next.put(
            JSONObject()
                .put("device", snapshot.device)
                .put("model", snapshot.model)
                .put("buildId", snapshot.buildId)
                .put("kernelRelease", snapshot.kernelRelease)
                .put("id", candidate.id)
                .put("provider", candidate.provider)
                .put("type", candidate.type.name)
                .put("url", candidate.url)
                .put("label", candidate.label)
                .put("build", candidate.build)
                .put("sha256", candidate.sha256 ?: JSONObject.NULL),
        )
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            if (item.optString("url") == candidate.url &&
                item.optString("kernelRelease") == snapshot.kernelRelease
            ) continue
            if (next.length() >= 39) break
            next.put(item)
        }
        root.put("schemaVersion", 1)
        root.put("entries", next)
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    private fun load(context: Context, onLog: (String) -> Unit): MagicOtaCatalogState {
        val bundledRaw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val bundled = parseCatalog(bundledRaw, "bundled")
        val remoteRaw = runCatching {
            fetchCachedText(
                context = context,
                url = REMOTE_URL,
                cacheName = REMOTE_CACHE,
                maxBytes = MAX_CATALOG_BYTES,
                forceRefresh = true,
            )
        }.onFailure {
            onLog("Veyra OTA catalog: bundled fallback (${it.javaClass.simpleName})")
        }.getOrNull()

        val remote = remoteRaw?.let { raw ->
            runCatching { parseCatalog(raw, "Veyra GitHub") }
                .onFailure { onLog("Veyra OTA catalog rejected: ${it.message}") }
                .getOrNull()
        }
        if (remote == null) return bundled

        val profiles = linkedMapOf<String, MagicOtaProfile>()
        bundled.profiles.forEach { profiles[it.id] = it }
        remote.profiles.forEach { profiles[it.id] = it }

        val entries = linkedMapOf<String, MutableList<MagicOtaCandidate>>()
        bundled.entriesByProfile.forEach { (key, value) ->
            entries.getOrPut(key) { mutableListOf() }.addAll(value)
        }
        remote.entriesByProfile.forEach { (key, value) ->
            entries.getOrPut(key) { mutableListOf() }.addAll(value)
        }
        return MagicOtaCatalogState(
            profiles = profiles.values.toList(),
            entriesByProfile = entries.mapValues { (_, value) -> value.distinctBy { it.url } },
            source = "bundled + Veyra GitHub",
        )
    }

    private fun parseCatalog(raw: String, source: String): MagicOtaCatalogState {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion") == 1) { "Unsupported OTA catalog schema" }
        val defaultMax = root.optInt("maxCandidatesPerDevice", DEFAULT_MAX_CANDIDATES)
            .coerceIn(1, 20)
        val profiles = buildList {
            val array = root.optJSONArray("profiles") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                add(
                    MagicOtaProfile(
                        id = id,
                        manufacturers = item.stringSet("manufacturer"),
                        models = item.stringSet("models"),
                        modelPrefixes = item.stringSet("modelPrefixes"),
                        devices = item.stringSet("devices"),
                        providers = item.stringSet("providers"),
                        maxCandidates = item.optInt("maxCandidates", defaultMax).coerceIn(1, 20),
                    ),
                )
            }
        }
        val entries = linkedMapOf<String, MutableList<MagicOtaCandidate>>()
        val array = root.optJSONArray("entries") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val profileId = item.optString("profileId").trim()
            val url = item.optString("url").trim()
            if (profileId.isEmpty() || !url.startsWith("https://", ignoreCase = true)) continue
            val sha = item.optString("sha256").trim().lowercase(Locale.ROOT)
                .takeIf(::isSha256)
            entries.getOrPut(profileId) { mutableListOf() }.add(
                MagicOtaCandidate(
                    id = item.optString("id", "veyra-$index"),
                    provider = item.optString("provider", "Veyra").ifBlank { "Veyra" },
                    type = MagicOtaArtifactType.parse(item.optString("type", "ota")),
                    url = url,
                    label = item.optString("label", profileId),
                    build = item.optString("build"),
                    kernelRelease = item.optString("kernelRelease"),
                    sha256 = sha,
                    priority = item.optInt("priority", 0),
                ),
            )
        }
        return MagicOtaCatalogState(profiles, entries, source)
    }

    private fun googlePixelCandidates(
        context: Context,
        snapshot: DeviceSnapshot,
        onLog: (String) -> Unit,
    ): List<MagicOtaCandidate> {
        val codename = snapshot.device.trim().lowercase(Locale.ROOT)
        require(codename.matches(Regex("[a-z0-9_-]{2,32}"))) { "Invalid Pixel codename" }
        val text = fetchCachedText(
            context,
            GOOGLE_PIXEL_INDEX,
            GOOGLE_CACHE,
            MAX_PROVIDER_BYTES,
        )
        val prefix = "https://dl.google.com/dl/android/aosp/$codename-ota-"
        val candidates = text.lineSequence()
            .map(String::trim)
            .filter { it.startsWith(prefix, ignoreCase = true) && it.endsWith(".zip") }
            .mapIndexed { index, url ->
                val file = url.substringAfterLast('/')
                val build = file
                    .removePrefix("$codename-ota-")
                    .removeSuffix(".zip")
                    .replace(Regex("-[0-9a-fA-F]{8,64}$"), "")
                MagicOtaCandidate(
                    id = "google-$codename-$index",
                    provider = "Google full OTA index",
                    type = MagicOtaArtifactType.FullOta,
                    url = url,
                    label = "$codename full OTA",
                    build = build,
                    priority = 50,
                )
            }
            .toList()
        onLog("Pixel provider: ${candidates.size} OTA build(s) indexed for $codename")
        return candidates
    }

    private fun nothingCandidates(
        context: Context,
        snapshot: DeviceSnapshot,
        onLog: (String) -> Unit,
    ): List<MagicOtaCandidate> {
        val key = nothingArchiveKey(snapshot.device) ?: return emptyList()
        val raw = fetchCachedText(
            context,
            NOTHING_ARCHIVE,
            NOTHING_CACHE,
            MAX_PROVIDER_BYTES,
        )
        val parsed = JSONTokener(raw).nextValue()
        val root = when (parsed) {
            is JSONObject -> parsed
            is JSONArray -> parsed.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        val releases = root.optJSONObject(key) ?: return emptyList()
        val candidates = ArrayList<MagicOtaCandidate>()

        val keys = releases.keys()
        while (keys.hasNext()) {
            val releaseKey = keys.next()
            val release = releases.optJSONObject(releaseKey) ?: continue
            val build = release.optString("build_number")
            val display = release.optString("name", releaseKey)

            val boots = release.optJSONArray("boot")
            if (boots != null) {
                for (index in 0 until boots.length()) {
                    val boot = boots.optJSONObject(index) ?: continue
                    if (!boot.optString("type").equals("stock", ignoreCase = true)) continue
                    val url = boot.optString("url")
                    if (!url.startsWith("https://", ignoreCase = true)) continue
                    candidates += MagicOtaCandidate(
                        id = "nothing-$key-$releaseKey-boot",
                        provider = "Nothing archive",
                        type = MagicOtaArtifactType.BootImage,
                        url = url,
                        label = "$display stock boot",
                        build = build,
                        priority = 65,
                    )
                    break
                }
            }

            val ota = release.optJSONArray("ota")
            if (ota != null) {
                for (index in 0 until ota.length()) {
                    val item = ota.optJSONObject(index) ?: continue
                    if (!item.optString("type").equals("full", ignoreCase = true)) continue
                    val url = item.optString("url")
                    if (!url.startsWith("https://", ignoreCase = true)) continue
                    candidates += MagicOtaCandidate(
                        id = "nothing-$key-$releaseKey-full-$index",
                        provider = "Nothing archive",
                        type = MagicOtaArtifactType.FullOta,
                        url = url,
                        label = "$display full OTA",
                        build = build,
                        priority = 45,
                    )
                }
            }
        }
        onLog("Nothing provider: ${candidates.size} boot/full-OTA artifact(s) indexed for $key")
        return candidates
    }

    private fun nothingArchiveKey(device: String): String? = when (device.lowercase(Locale.ROOT)) {
        "spacewar" -> "nothing_phone_1"
        "pong" -> "nothing_phone_2"
        "pacman" -> "nothing_phone_2a"
        "pacmanpro" -> "nothing_phone_2a_plus"
        "tetris" -> "cmf_phone_1"
        else -> null
    }

    private fun historyCandidates(
        context: Context,
        snapshot: DeviceSnapshot,
    ): List<MagicOtaCandidate> {
        val file = historyFile(context)
        if (!file.isFile) return emptyList()
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return emptyList()
        val entries = root.optJSONArray("entries") ?: return emptyList()
        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                if (!item.optString("device").equals(snapshot.device, ignoreCase = true)) continue
                if (!item.optString("kernelRelease").equals(snapshot.kernelRelease, ignoreCase = true)) continue
                val url = item.optString("url")
                if (!url.startsWith("https://", ignoreCase = true)) continue
                add(
                    MagicOtaCandidate(
                        id = item.optString("id", "history-$index"),
                        provider = PROVIDER_HISTORY,
                        type = MagicOtaArtifactType.parse(item.optString("type")),
                        url = url,
                        label = item.optString("label", "Known-good OTA"),
                        build = item.optString("build"),
                        kernelRelease = item.optString("kernelRelease"),
                        sha256 = item.optString("sha256").takeIf(::isSha256),
                        priority = 100,
                    ),
                )
            }
        }
    }

    private fun marketCandidates(
        context: Context,
        profileIds: Set<String>,
        snapshot: DeviceSnapshot,
        onLog: (String) -> Unit,
    ): List<MagicOtaCandidate> {
        val root = File(context.filesDir, "market/packs")
        val packFiles = root.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && it.name == "payload.json" }
            .toList()
        if (packFiles.isEmpty()) return emptyList()

        val result = ArrayList<MagicOtaCandidate>()
        for (file in packFiles) {
            runCatching {
                val json = JSONObject(file.readText())
                if (json.optString("kind") != "magic-ota-catalog") return@runCatching
                val catalogObject = json.optJSONObject("catalog") ?: json
                val parsed = parseCatalog(catalogObject.toString(), "market:${file.parentFile?.name}")
                profileIds.forEach { id -> result += parsed.entriesByProfile[id].orEmpty() }
            }.onFailure {
                onLog("Market OTA pack ${file.parentFile?.name}: ${it.message}")
            }
        }
        return result
            .filter { it.kernelRelease.isBlank() ||
                it.kernelRelease.equals(snapshot.kernelRelease, ignoreCase = true) }
    }

    private fun fetchCachedText(
        context: Context,
        url: String,
        cacheName: String,
        maxBytes: Int,
        forceRefresh: Boolean = false,
    ): String {
        val directory = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        val cache = File(directory, cacheName)
        val fresh = cache.isFile &&
            System.currentTimeMillis() - cache.lastModified() in 0 until CACHE_TTL_MS
        if (fresh && !forceRefresh) return cache.readText()

        return runCatching {
            fetchText(url, maxBytes).also { body ->
                cache.writeText(body)
            }
        }.getOrElse { error ->
            if (cache.isFile) cache.readText() else throw error
        }
    }

    private fun fetchText(url: String, maxBytes: Int): String {
        require(url.startsWith("https://", ignoreCase = true)) { "HTTPS required" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("User-Agent", "VeyraRoot/${BuildConfig.VERSION_BASE} MagicOTA")
        }
        try {
            require(connection.responseCode in 200..299) {
                "HTTP ${connection.responseCode}"
            }
            val declared = connection.contentLengthLong
            require(declared <= 0 || declared <= maxBytes) {
                "Catalog is too large ($declared bytes)"
            }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(
                    if (declared in 1..maxBytes.toLong()) declared.toInt() else 64 * 1024,
                )
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "Catalog exceeds $maxBytes bytes" }
                    output.write(buffer, 0, read)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun historyFile(context: Context): File =
        File(File(context.filesDir, "magic-builder"), HISTORY_FILE)

    private fun JSONObject.stringSet(name: String): Set<String> {
        val value = opt(name)
        if (value is JSONArray) {
            return buildSet {
                for (index in 0 until value.length()) {
                    value.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }
        }
        if (value is String) {
            return setOf(value.trim()).filterTo(linkedSetOf(), String::isNotEmpty)
        }
        return emptySet()
    }

    private const val PROVIDER_GOOGLE = "google-pixel-index"
    private const val PROVIDER_NOTHING = "nothing-archive"
    private const val PROVIDER_HISTORY = "Veyra known-good"
}
