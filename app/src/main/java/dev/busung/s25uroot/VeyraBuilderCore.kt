package ctrl.mietze.veyraroot

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class VeyraBuilderStep(val label: String) {
    Welcome("Start"),
    Ota("OTA"),
    Discovery("Discovery"),
    LocalImages("Local images"),
    Termux("Termux"),
    ReportImport("Report"),
    Fallbacks("Fallbacks"),
    RiskMode("Mode"),
    Review("Review"),
    Export("Export");

    fun next(): VeyraBuilderStep = entries.getOrElse(ordinal + 1) { this }
    fun previous(): VeyraBuilderStep = entries.getOrElse(ordinal - 1) { this }
}

internal enum class VeyraBuilderRiskMode(val label: String) {
    Standard("Standard"),
    Research("Unsafe / Research"),
    Experimental("Hard Crash / Experimental"),
}

internal data class VeyraBuilderSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val step: VeyraBuilderStep = VeyraBuilderStep.Welcome,
    val otaUrl: String = "",
    val otaBootName: String = "",
    val otaBootPath: String = "",
    val otaBootSha256: String = "",
    val otaKernelRelease: String = "",
    val otaXblName: String = "",
    val otaXblSha256: String = "",
    val localBootUri: String = "",
    val localBootName: String = "",
    val localBootSha256: String = "",
    val localBootMatchesOta: Boolean? = null,
    val localXblUri: String = "",
    val localXblName: String = "",
    val localXblSha256: String = "",
    val reportName: String = "",
    val reportCreatedUtc: String = "",
    val reportKernel: String = "",
    val evidenceSuccessful: Int = 0,
    val evidenceIndependentGroups: Int = 0,
    val evidenceConflicts: Int = 0,
    val riskMode: VeyraBuilderRiskMode = VeyraBuilderRiskMode.Standard,
    val lastMessage: String = "",
    val exportedPaths: List<String> = emptyList(),
)

internal object VeyraBuilderSessionStore {
    private const val PREFS = "veyra_builder_session_v1"
    private const val KEY = "session"

    fun load(context: Context): VeyraBuilderSession {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return VeyraBuilderSession()
        return runCatching { decode(JSONObject(raw)) }
            .getOrElse { VeyraBuilderSession() }
    }

    fun save(context: Context, session: VeyraBuilderSession): VeyraBuilderSession {
        val next = session.copy(updatedAt = System.currentTimeMillis())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(next).toString())
            .apply()
        return next
    }

    fun newSession(context: Context): VeyraBuilderSession {
        val next = VeyraBuilderSession()
        save(context, next)
        return next
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    private fun encode(s: VeyraBuilderSession) = JSONObject()
        .put("schemaVersion", 1)
        .put("id", s.id)
        .put("createdAt", s.createdAt)
        .put("updatedAt", s.updatedAt)
        .put("step", s.step.name)
        .put("otaUrl", s.otaUrl)
        .put("otaBootName", s.otaBootName)
        .put("otaBootPath", s.otaBootPath)
        .put("otaBootSha256", s.otaBootSha256)
        .put("otaKernelRelease", s.otaKernelRelease)
        .put("otaXblName", s.otaXblName)
        .put("otaXblSha256", s.otaXblSha256)
        .put("localBootUri", s.localBootUri)
        .put("localBootName", s.localBootName)
        .put("localBootSha256", s.localBootSha256)
        .put("localBootMatchesOta", s.localBootMatchesOta ?: JSONObject.NULL)
        .put("localXblUri", s.localXblUri)
        .put("localXblName", s.localXblName)
        .put("localXblSha256", s.localXblSha256)
        .put("reportName", s.reportName)
        .put("reportCreatedUtc", s.reportCreatedUtc)
        .put("reportKernel", s.reportKernel)
        .put("evidenceSuccessful", s.evidenceSuccessful)
        .put("evidenceIndependentGroups", s.evidenceIndependentGroups)
        .put("evidenceConflicts", s.evidenceConflicts)
        .put("riskMode", s.riskMode.name)
        .put("lastMessage", s.lastMessage)
        .put("exportedPaths", JSONArray(s.exportedPaths))

    private fun decode(j: JSONObject): VeyraBuilderSession {
        require(j.optInt("schemaVersion", 1) == 1)
        return VeyraBuilderSession(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
            step = runCatching { VeyraBuilderStep.valueOf(j.optString("step")) }
                .getOrDefault(VeyraBuilderStep.Welcome),
            otaUrl = j.optString("otaUrl"),
            otaBootName = j.optString("otaBootName"),
            otaBootPath = j.optString("otaBootPath"),
            otaBootSha256 = j.optString("otaBootSha256"),
            otaKernelRelease = j.optString("otaKernelRelease"),
            otaXblName = j.optString("otaXblName"),
            otaXblSha256 = j.optString("otaXblSha256"),
            localBootUri = j.optString("localBootUri"),
            localBootName = j.optString("localBootName"),
            localBootSha256 = j.optString("localBootSha256"),
            localBootMatchesOta = if (j.isNull("localBootMatchesOta")) null else j.optBoolean("localBootMatchesOta"),
            localXblUri = j.optString("localXblUri"),
            localXblName = j.optString("localXblName"),
            localXblSha256 = j.optString("localXblSha256"),
            reportName = j.optString("reportName"),
            reportCreatedUtc = j.optString("reportCreatedUtc"),
            reportKernel = j.optString("reportKernel"),
            evidenceSuccessful = j.optInt("evidenceSuccessful"),
            evidenceIndependentGroups = j.optInt("evidenceIndependentGroups"),
            evidenceConflicts = j.optInt("evidenceConflicts"),
            riskMode = runCatching { VeyraBuilderRiskMode.valueOf(j.optString("riskMode")) }
                .getOrDefault(VeyraBuilderRiskMode.Standard),
            lastMessage = j.optString("lastMessage"),
            exportedPaths = buildList {
                val array = j.optJSONArray("exportedPaths") ?: JSONArray()
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            },
        )
    }
}

internal object VeyraBuilderPreferences {
    private const val PREFS = "veyra_builder_preferences_v1"
    private const val INCLUDE_XBL = "include_xbl"
    private const val AUTO_TERMUX_SETUP = "auto_termux_setup"
    private const val MAX_SOURCES = "max_sources"
    private const val VERIFY_TARGET = "verify_target"

    fun includeXbl(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(INCLUDE_XBL, false)
    fun setIncludeXbl(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(INCLUDE_XBL, enabled).apply()
    }
    fun autoTermuxSetup(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_TERMUX_SETUP, true)
    fun setAutoTermuxSetup(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTO_TERMUX_SETUP, enabled).apply()
    }
    fun maxSources(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MAX_SOURCES, 10).coerceIn(4, 10)
    fun setMaxSources(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(MAX_SOURCES, value.coerceIn(4, 10)).apply()
    }
    fun verificationTarget(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(VERIFY_TARGET, 4).coerceIn(2, 6)
    fun setVerificationTarget(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(VERIFY_TARGET, value.coerceIn(2, 6)).apply()
    }
}

internal data class VeyraBuilderImportedReport(
    val name: String,
    val createdUtc: String,
    val kernel: String,
    val successes: Int,
    val independentGroups: Int,
    val conflicts: Int,
    val observations: Int,
)

internal object VeyraBuilderReportParser {
    fun parse(name: String, raw: String): VeyraBuilderImportedReport {
        val root = JSONObject(raw)
        require(root.optString("format") == "veyra.termux.report/v1") { "Unsupported Veyra Termux report format" }
        val observations = root.optJSONArray("observations") ?: JSONArray()
        val groups = linkedSetOf<String>()
        val valuesByField = linkedMapOf<String, MutableSet<String>>()
        var success = 0
        var kernel = ""
        for (index in 0 until observations.length()) {
            val item = observations.optJSONObject(index) ?: continue
            if (item.optString("outcome") == "SUCCESS") {
                success++
                item.optString("independent_group").takeIf(String::isNotBlank)?.let(groups::add)
                val field = item.optString("field")
                val value = item.optString("value")
                if (field.isNotBlank() && value.isNotBlank()) valuesByField.getOrPut(field) { linkedSetOf() }.add(value)
                if (field == "device.uname_r" && kernel.isBlank()) kernel = value
            }
        }
        val conflicts = valuesByField.values.count { it.size > 1 }
        return VeyraBuilderImportedReport(
            name = name,
            createdUtc = root.optString("created_utc"),
            kernel = kernel,
            successes = success,
            independentGroups = groups.size,
            conflicts = conflicts,
            observations = observations.length(),
        )
    }
}

internal object VeyraBuilderFiles {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    fun sha256(file: File): String = sha256(file.readBytes())

    fun readUri(context: Context, uri: Uri, maxBytes: Long = 512L * 1024L * 1024L): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(128 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "Selected file is too large" }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
        error("Could not read selected file")
    }
}

internal object VeyraBuilderExporter {
    data class Result(val paths: List<String>, val summary: String)

    fun export(context: Context, session: VeyraBuilderSession): Result {
        val snapshot = DeviceSnapshot.current()
        val strategy = BuilderStrategyPlanner.evaluate(context, snapshot)
        val stem = "VeyraBuilder-${session.id.take(8)}"
        fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

        val candidate = buildString {
            appendLine("# Veyra Builder candidate profile")
            appendLine("# Evidence/provenance output — not a root guarantee.")
            appendLine("schema_version = 1")
            appendLine("status = \"candidate\"")
            appendLine("session_id = \"${esc(session.id)}\"")
            appendLine("device_model = \"${esc(snapshot.model)}\"")
            appendLine("device_codename = \"${esc(snapshot.device)}\"")
            appendLine("release = \"${esc(session.otaKernelRelease.ifBlank { snapshot.kernelRelease })}\"")
            appendLine("ota_boot_sha256 = \"${session.otaBootSha256}\"")
            appendLine("local_boot_sha256 = \"${session.localBootSha256}\"")
            appendLine("evidence_successful = ${session.evidenceSuccessful}")
            appendLine("evidence_independent_groups = ${session.evidenceIndependentGroups}")
            appendLine("risk_mode = \"${session.riskMode.name}\"")
            appendLine("# Kernel offsets are intentionally not guessed or copied from nearby builds.")
        }

        val sessionJson = JSONObject()
            .put("format", "veyra.builder.session/v1")
            .put("session", JSONObject()
                .put("id", session.id)
                .put("createdAt", session.createdAt)
                .put("updatedAt", session.updatedAt)
                .put("step", session.step.name)
                .put("otaUrl", session.otaUrl)
                .put("otaBootSha256", session.otaBootSha256)
                .put("otaKernelRelease", session.otaKernelRelease)
                .put("localBootSha256", session.localBootSha256)
                .put("localBootMatchesOta", session.localBootMatchesOta ?: JSONObject.NULL)
                .put("reportName", session.reportName)
                .put("evidenceSuccessful", session.evidenceSuccessful)
                .put("evidenceIndependentGroups", session.evidenceIndependentGroups)
                .put("evidenceConflicts", session.evidenceConflicts)
                .put("riskMode", session.riskMode.name))
            .put("device", JSONObject()
                .put("manufacturer", snapshot.manufacturer)
                .put("model", snapshot.model)
                .put("device", snapshot.device)
                .put("kernelRelease", snapshot.kernelRelease)
                .put("buildId", snapshot.buildId)
                .put("fingerprint", snapshot.fingerprint))
            .put("routes", JSONArray(strategy.lines()))
            .toString(2)

        val summary = buildString {
            appendLine("VEYRA BUILDER RESULT")
            appendLine("Session: ${session.id}")
            appendLine("Device: ${snapshot.manufacturer} ${snapshot.model} (${snapshot.device})")
            appendLine("Live kernel: ${snapshot.kernelRelease}")
            appendLine("OTA kernel: ${session.otaKernelRelease.ifBlank { "not resolved" }}")
            appendLine("OTA boot SHA-256: ${session.otaBootSha256.ifBlank { "not available" }}")
            appendLine("Local boot SHA-256: ${session.localBootSha256.ifBlank { "not provided" }}")
            appendLine("Boot match: ${session.localBootMatchesOta?.toString() ?: "not compared"}")
            appendLine("Evidence: ${session.evidenceSuccessful} successful / ${session.evidenceIndependentGroups} independent groups")
            appendLine("Conflicts: ${session.evidenceConflicts}")
            appendLine("Risk mode: ${session.riskMode.label}")
            appendLine()
            strategy.lines().forEach(::appendLine)
            appendLine()
            appendLine("PROFILE_EXPORTED=true")
            appendLine("NATIVE_ARTIFACT_VERIFIED=false")
            appendLine("DEVICE_TESTED=false")
        }

        val paths = listOf(
            DownloadStore.save(context, "$stem.conf", candidate.toByteArray()),
            DownloadStore.save(context, "$stem.json", sessionJson.toByteArray()),
            DownloadStore.save(context, "$stem-report.txt", summary.toByteArray()),
        )
        return Result(paths, summary)
    }
}
