package ctrl.mietze.veyraroot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class MagicUnlockRound(
    val number: Int,
    val timestamp: Long,
    val kind: String,
    val schemes: List<String>,
    val phase: String,
    val analysisOnly: Boolean,
    val blockedKind: String?,
    val outputName: String,
    val outputSha256: String,
    val outputSize: Long,
    val supportBundlePath: String,
    val missingArtifacts: List<String>,
    val agreements: List<String>,
    val conflicts: List<String>,
)

internal data class MagicUnlockCandidate(
    val targetKey: String,
    val route: String,
    val model: String,
    val device: String,
    val buildId: String,
    val fingerprint: String,
    val kernelRelease: String,
    val abi: String,
    val pageSize: Long,
    val nothingProfile: String?,
    val inputSource: String,
    val inputSha256: String,
    val inputSize: Long,
    val rounds: List<MagicUnlockRound>,
) {
    val latest: MagicUnlockRound? get() = rounds.lastOrNull()
    val ready: Boolean get() = rounds.any { it.outputSize > 0L && it.outputSha256.isNotBlank() }
    val agreements: List<String>
        get() = rounds.flatMap { it.agreements }.distinct()
    val conflicts: List<String>
        get() = rounds.flatMap { it.conflicts }
            .filterNot { conflict ->
                ready && (conflict == "analysis-only" || conflict.startsWith("build-blocked:"))
            }
            .distinct()

    val evidenceScore: Int
        get() {
            var score = 20
            if (route != "Generic Android") score += 10
            if (inputSha256.isNotBlank()) score += 15
            if (agreements.any { it == "exact-live-kernel" }) score += 15
            if (agreements.any { it == "verified-device-reference" }) score += 10
            if (agreements.any { it == "support-bundle-exported" }) score += 10
            if (ready) score += 30
            score -= (conflicts.size * 4).coerceAtMost(28)
            return score.coerceIn(0, 100)
        }

    val status: String
        get() = when {
            ready -> "PAYLOAD_READY"
            conflicts.isEmpty() && rounds.isNotEmpty() -> "EVIDENCE_CONVERGED"
            rounds.isEmpty() -> "CAPTURED"
            else -> "ANALYZING"
        }
}

internal object MagicUnlockCandidateStore {
    private const val DIRECTORY = "magic-unlock"
    private const val FILE_NAME = "candidate.json"
    private const val MAX_ROUNDS = 24

    fun load(context: Context): MagicUnlockCandidate? {
        val file = candidateFile(context)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { decode(JSONObject(file.readText())) }.getOrNull()
    }

    fun loadFor(context: Context, snapshot: DeviceSnapshot): MagicUnlockCandidate? =
        load(context)?.takeIf { it.targetKey == targetKey(snapshot) }

    fun clear(context: Context) {
        candidateFile(context).delete()
    }

    fun recordCapture(
        context: Context,
        snapshot: DeviceSnapshot,
        route: MagicBuilderOemRoute,
        nothingProfile: NothingMagicProfile?,
        capture: MagicBuilderCapture,
    ): MagicUnlockCandidate {
        val sha = sha256(capture.file)
        val existing = loadFor(context, snapshot)
        val base = existing ?: newCandidate(
            snapshot = snapshot,
            route = route,
            nothingProfile = nothingProfile,
            inputSource = capture.blockDevice,
            inputSha256 = sha,
            inputSize = capture.file.length(),
        )
        val agreements = buildList {
            add("device-identity")
            add("exact-live-kernel")
            if (route != MagicBuilderOemRoute.Generic) add("oem-route")
            if (nothingProfile != null) add("nothing-profile")
            if (nothingProfile?.verifiedKernelReleases?.any {
                    it.equals(snapshot.kernelRelease, ignoreCase = true)
                } == true
            ) {
                add("verified-device-reference")
            }
        }
        val conflicts = buildList {
            if (existing != null && existing.inputSha256.isNotBlank() &&
                !existing.inputSha256.equals(sha, ignoreCase = true)
            ) {
                add("captured-image-changed")
            }
        }
        val round = MagicUnlockRound(
            number = nextRound(base),
            timestamp = System.currentTimeMillis(),
            kind = "capture",
            schemes = emptyList(),
            phase = "CAPTURED",
            analysisOnly = false,
            blockedKind = null,
            outputName = "",
            outputSha256 = "",
            outputSize = 0L,
            supportBundlePath = "",
            missingArtifacts = emptyList(),
            agreements = agreements,
            conflicts = conflicts,
        )
        return save(context, base.copy(
            inputSource = capture.blockDevice,
            inputSha256 = sha,
            inputSize = capture.file.length(),
            rounds = appendRound(base.rounds, round),
        ))
    }

    fun recordBuild(
        context: Context,
        snapshot: DeviceSnapshot,
        route: MagicBuilderOemRoute,
        nothingProfile: NothingMagicProfile?,
        attemptedSchemes: List<PayloadScheme>,
        state: PayloadBuildState,
        supportBundlePath: String?,
        supportMissing: List<String>,
    ): MagicUnlockCandidate {
        val base = loadFor(context, snapshot) ?: newCandidate(
            snapshot = snapshot,
            route = route,
            nothingProfile = nothingProfile,
        )
        val agreements = buildList {
            add("device-identity")
            add("exact-live-kernel")
            if (route != MagicBuilderOemRoute.Generic) add("oem-route")
            if (nothingProfile != null) add("nothing-profile")
            if (nothingProfile?.verifiedKernelReleases?.any {
                    it.equals(snapshot.kernelRelease, ignoreCase = true)
                } == true
            ) {
                add("verified-device-reference")
            }
            if (state.summary.isNotBlank()) add("kernel-analysis-completed")
            if (state.outputSize > 0L && state.outputSha256.isNotBlank()) {
                add("builder-output-verified")
            }
            if (!supportBundlePath.isNullOrBlank()) add("support-bundle-exported")
        }
        val conflicts = buildList {
            if (state.analysisOnly) add("analysis-only")
            state.blockedKind?.let { add("build-blocked:${it.name}") }
            state.error?.takeIf(String::isNotBlank)?.let { add("build-error") }
            supportMissing.forEach { add("missing:$it") }
        }
        val round = MagicUnlockRound(
            number = nextRound(base),
            timestamp = System.currentTimeMillis(),
            kind = "build",
            schemes = attemptedSchemes.map { it.name },
            phase = state.phase.name,
            analysisOnly = state.analysisOnly,
            blockedKind = state.blockedKind?.name,
            outputName = state.outputName,
            outputSha256 = state.outputSha256,
            outputSize = state.outputSize,
            supportBundlePath = supportBundlePath.orEmpty(),
            missingArtifacts = supportMissing,
            agreements = agreements,
            conflicts = conflicts,
        )
        return save(context, base.copy(rounds = appendRound(base.rounds, round)))
    }

    fun recordSupportBundle(
        context: Context,
        snapshot: DeviceSnapshot,
        route: MagicBuilderOemRoute,
        nothingProfile: NothingMagicProfile?,
        attemptedSchemes: List<PayloadScheme>,
        state: PayloadBuildState,
        bundle: MagicBuilderSupportBundle,
    ): MagicUnlockCandidate {
        val base = loadFor(context, snapshot) ?: newCandidate(
            snapshot = snapshot,
            route = route,
            nothingProfile = nothingProfile,
        )
        val round = MagicUnlockRound(
            number = nextRound(base),
            timestamp = System.currentTimeMillis(),
            kind = "support",
            schemes = attemptedSchemes.map { it.name },
            phase = state.phase.name,
            analysisOnly = state.analysisOnly,
            blockedKind = state.blockedKind?.name,
            outputName = state.outputName,
            outputSha256 = state.outputSha256,
            outputSize = state.outputSize,
            supportBundlePath = bundle.savedPath,
            missingArtifacts = bundle.missing,
            agreements = buildList {
                add("device-identity")
                add("exact-live-kernel")
                add("support-bundle-exported")
                if (bundle.captured.isNotEmpty()) add("support-artifacts:${bundle.captured.size}")
            },
            conflicts = bundle.missing.map { "missing:$it" },
        )
        return save(context, base.copy(rounds = appendRound(base.rounds, round)))
    }

    fun export(context: Context, candidate: MagicUnlockCandidate): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "VeyraRoot-UnlockCandidate-$stamp.json"
        val bytes = encode(candidate).toString(2).toByteArray(Charsets.UTF_8)
        val path = DownloadStore.save(context, name, bytes)
        AppPreferences.setMagicUnlockReport(context, path)
        return path
    }

    private fun newCandidate(
        snapshot: DeviceSnapshot,
        route: MagicBuilderOemRoute,
        nothingProfile: NothingMagicProfile?,
        inputSource: String = "",
        inputSha256: String = "",
        inputSize: Long = 0L,
    ) = MagicUnlockCandidate(
        targetKey = targetKey(snapshot),
        route = MagicBuilderController.oemRouteName(route),
        model = snapshot.model,
        device = snapshot.device,
        buildId = snapshot.buildId,
        fingerprint = snapshot.fingerprint,
        kernelRelease = snapshot.kernelRelease,
        abi = snapshot.abi,
        pageSize = snapshot.pageSize,
        nothingProfile = nothingProfile?.name,
        inputSource = inputSource,
        inputSha256 = inputSha256,
        inputSize = inputSize,
        rounds = emptyList(),
    )

    private fun appendRound(
        rounds: List<MagicUnlockRound>,
        round: MagicUnlockRound,
    ): List<MagicUnlockRound> = (rounds + round).takeLast(MAX_ROUNDS)

    private fun nextRound(candidate: MagicUnlockCandidate): Int =
        (candidate.rounds.maxOfOrNull { it.number } ?: 0) + 1

    private fun targetKey(snapshot: DeviceSnapshot): String =
        listOf(snapshot.fingerprint, snapshot.kernelRelease, snapshot.abi, snapshot.pageSize)
            .joinToString("|")

    private fun candidateFile(context: Context): File =
        File(File(context.filesDir, DIRECTORY).apply { mkdirs() }, FILE_NAME)

    private fun save(context: Context, candidate: MagicUnlockCandidate): MagicUnlockCandidate {
        val destination = candidateFile(context)
        val temporary = File(destination.parentFile, "$FILE_NAME.part")
        temporary.writeText(encode(candidate).toString(2))
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) { "Could not finalize Magic Unlock candidate" }
        return candidate
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun encode(candidate: MagicUnlockCandidate): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("targetKey", candidate.targetKey)
        put("route", candidate.route)
        put("model", candidate.model)
        put("device", candidate.device)
        put("buildId", candidate.buildId)
        put("fingerprint", candidate.fingerprint)
        put("kernelRelease", candidate.kernelRelease)
        put("abi", candidate.abi)
        put("pageSize", candidate.pageSize)
        put("nothingProfile", candidate.nothingProfile ?: JSONObject.NULL)
        put("inputSource", candidate.inputSource)
        put("inputSha256", candidate.inputSha256)
        put("inputSize", candidate.inputSize)
        put("evidenceScore", candidate.evidenceScore)
        put("status", candidate.status)
        put("rounds", JSONArray().apply {
            candidate.rounds.forEach { round -> put(encodeRound(round)) }
        })
    }

    private fun encodeRound(round: MagicUnlockRound): JSONObject = JSONObject().apply {
        put("number", round.number)
        put("timestamp", round.timestamp)
        put("kind", round.kind)
        put("schemes", JSONArray(round.schemes))
        put("phase", round.phase)
        put("analysisOnly", round.analysisOnly)
        put("blockedKind", round.blockedKind ?: JSONObject.NULL)
        put("outputName", round.outputName)
        put("outputSha256", round.outputSha256)
        put("outputSize", round.outputSize)
        put("supportBundlePath", round.supportBundlePath)
        put("missingArtifacts", JSONArray(round.missingArtifacts))
        put("agreements", JSONArray(round.agreements))
        put("conflicts", JSONArray(round.conflicts))
    }

    private fun decode(json: JSONObject): MagicUnlockCandidate = MagicUnlockCandidate(
        targetKey = json.optString("targetKey"),
        route = json.optString("route"),
        model = json.optString("model"),
        device = json.optString("device"),
        buildId = json.optString("buildId"),
        fingerprint = json.optString("fingerprint"),
        kernelRelease = json.optString("kernelRelease"),
        abi = json.optString("abi"),
        pageSize = json.optLong("pageSize"),
        nothingProfile = json.optString("nothingProfile").takeIf {
            it.isNotBlank() && it != "null"
        },
        inputSource = json.optString("inputSource"),
        inputSha256 = json.optString("inputSha256"),
        inputSize = json.optLong("inputSize"),
        rounds = json.optJSONArray("rounds").toRounds(),
    )

    private fun JSONArray?.toRounds(): List<MagicUnlockRound> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val json = optJSONObject(index) ?: continue
                add(
                    MagicUnlockRound(
                        number = json.optInt("number"),
                        timestamp = json.optLong("timestamp"),
                        kind = json.optString("kind"),
                        schemes = json.optJSONArray("schemes").toStrings(),
                        phase = json.optString("phase"),
                        analysisOnly = json.optBoolean("analysisOnly"),
                        blockedKind = json.optString("blockedKind").takeIf {
                            it.isNotBlank() && it != "null"
                        },
                        outputName = json.optString("outputName"),
                        outputSha256 = json.optString("outputSha256"),
                        outputSize = json.optLong("outputSize"),
                        supportBundlePath = json.optString("supportBundlePath"),
                        missingArtifacts = json.optJSONArray("missingArtifacts").toStrings(),
                        agreements = json.optJSONArray("agreements").toStrings(),
                        conflicts = json.optJSONArray("conflicts").toStrings(),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
}
