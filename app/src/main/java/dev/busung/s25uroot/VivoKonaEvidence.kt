package ctrl.mietze.veyraroot

import android.content.Context
import com.kernelpack.profile.BaselineRegistry
import com.kernelpack.profile.BaselineScheme

internal enum class VivoEvidenceState(val mark: String) {
    Verified("✓"),
    Missing("✗"),
    Unknown("?"),
    Conflict("!"),
}

internal data class VivoEvidenceCheck(
    val name: String,
    val state: VivoEvidenceState,
    val detail: String,
) {
    fun line(): String = "${state.mark} $name · $detail"
}

internal data class VivoKonaEvidenceReport(
    val applicable: Boolean,
    val checks: List<VivoEvidenceCheck>,
    val backend: CVeyraBackend,
    val liveSymbols: Map<String, ULong>,
    val kernelCodeRange: String?,
    val vrKoSeen: Boolean?,
    val buildableBaselineId: String?,
) {
    val verifiedCount: Int get() = checks.count { it.state == VivoEvidenceState.Verified }
    val conflictCount: Int get() = checks.count { it.state == VivoEvidenceState.Conflict }

    /**
     * "Porting ready" is deliberately not "runnable".
     * It means the device has supplied the live evidence needed to finish/verify a future exact baseline.
     */
    val portingEvidenceComplete: Boolean
        get() = applicable &&
            conflictCount == 0 &&
            liveSymbols["_text"] != null &&
            liveSymbols["init_task"] != null &&
            kernelCodeRange != null

    val runnableRegistered: Boolean get() = buildableBaselineId != null

    fun lines(): List<String> = buildList {
        add("Vivo / Kona Evidence Ladder")
        add("Backend: ${backend.storedValue}")
        checks.forEach { add(it.line()) }
        if (liveSymbols.isNotEmpty()) {
            add(
                "Live symbols: " +
                    liveSymbols.entries.joinToString(" · ") { (name, value) ->
                        "$name=0x${value.toString(16)}"
                    },
            )
        }
        kernelCodeRange?.let { add("Physical kernel code: $it") }
        add(
            when {
                runnableRegistered ->
                    "Verdict: exact buildable baseline registered ($buildableBaselineId)."
                portingEvidenceComplete ->
                    "Verdict: live porting evidence is strong, but no buildable 4.19 baseline/base-library is registered yet."
                else ->
                    "Verdict: evidence is incomplete; keep analysis-only and collect the missing live values."
            },
        )
        add("Evidence completion never substitutes a missing executable baseline.")
    }
}

internal object VivoKonaEvidence {
    private val requiredSymbols = listOf(
        "_text",
        "_stext",
        "init_task",
        "security_hook_heads",
        "selinux_state",
        "kallsyms_lookup_name",
    )

    fun inspect(
        context: Context,
        snapshot: DeviceSnapshot = DeviceSnapshot.current(),
    ): VivoKonaEvidenceReport {
        val profile = VivoLegacyProfiles.detect(snapshot)
        val identity = listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.fingerprint,
        ).joinToString(" ").lowercase()
        val vivoFamilyCandidate = "vivo" in identity || "iqoo" in identity
        val kernel419152Candidate =
            snapshot.kernelRelease.startsWith("4.19.152", ignoreCase = true)

        if (profile == null && !(vivoFamilyCandidate && kernel419152Candidate)) {
            return VivoKonaEvidenceReport(
                applicable = false,
                checks = listOf(
                    VivoEvidenceCheck(
                        "Device identity",
                        VivoEvidenceState.Missing,
                        "No exact Vivo X60/Kona profile or 4.19.152 vivo candidate match",
                    ),
                ),
                backend = CVeyraBackend.None,
                liveSymbols = emptyMap(),
                kernelCodeRange = null,
                vrKoSeen = null,
                buildableBaselineId = null,
            )
        }

        val checks = ArrayList<VivoEvidenceCheck>()
        checks += VivoEvidenceCheck(
            "Device identity",
            if (profile != null) VivoEvidenceState.Verified else VivoEvidenceState.Unknown,
            profile?.let { "${it.name} · aliases matched" }
                ?: "unlisted vivo/iQOO 4.19.152 candidate · live Kona platform check required",
        )

        val exactFamily = profile?.exactReleasePrefixes?.any {
            snapshot.kernelRelease.startsWith(it, ignoreCase = true)
        } ?: kernel419152Candidate
        checks += VivoEvidenceCheck(
            "Kernel source family",
            if (exactFamily) VivoEvidenceState.Verified else VivoEvidenceState.Conflict,
            "live=${snapshot.kernelRelease} · expected=" +
                (profile?.exactReleasePrefixes?.joinToString("/")
                    ?: "4.19.152-perf* / Kona family"),
        )

        val arm64 = snapshot.abi.contains("arm64", ignoreCase = true) ||
            snapshot.machine.contains("aarch64", ignoreCase = true)
        checks += VivoEvidenceCheck(
            "Architecture",
            if (arm64) VivoEvidenceState.Verified else VivoEvidenceState.Conflict,
            "ABI=${snapshot.abi} · machine=${snapshot.machine}",
        )

        val backend = if (CVeyraPreferences.enabled(context)) {
            CVeyraController.probeBackend(context)
        } else {
            CVeyraBackend.None
        }

        val live = when (backend) {
            CVeyraBackend.Root -> CVeyraController.rootShell(context, liveCommand())
            CVeyraBackend.WirelessAdb -> CVeyraController.shell(context, liveCommand())
            CVeyraBackend.None -> null
        }

        val output = live?.takeIf { it.exitCode == 0 }?.output.orEmpty()
        val props = parseProperties(output)
        val liveKona = props.values.any { value ->
            value.contains("kona", ignoreCase = true) ||
                value.contains("sm8250", ignoreCase = true)
        }
        val symbols = parseSymbols(output)
        val range = parseKernelCodeRange(output)
        val vrKo = parseVrKo(output)

        checks += VivoEvidenceCheck(
            "Kona platform",
            when {
                profile != null && liveKona -> VivoEvidenceState.Verified
                profile != null && backend == CVeyraBackend.None -> VivoEvidenceState.Unknown
                profile != null && !liveKona -> VivoEvidenceState.Unknown
                liveKona -> VivoEvidenceState.Verified
                backend == CVeyraBackend.None -> VivoEvidenceState.Unknown
                else -> VivoEvidenceState.Conflict
            },
            when {
                liveKona -> "live board/hardware reports Kona / SM8250"
                profile != null -> "known alias profile; live board property not readable/confirmed"
                backend == CVeyraBackend.None -> "unlisted candidate needs CVeyra live evidence"
                else -> "unlisted vivo candidate did not report Kona / SM8250"
            },
        )

        checks += VivoEvidenceCheck(
            "Privileged live backend",
            when (backend) {
                CVeyraBackend.Root -> VivoEvidenceState.Verified
                CVeyraBackend.WirelessAdb -> VivoEvidenceState.Unknown
                CVeyraBackend.None -> VivoEvidenceState.Missing
            },
            when (backend) {
                CVeyraBackend.Root -> "root-backed live evidence available"
                CVeyraBackend.WirelessAdb -> "shell evidence available; protected addresses may be redacted"
                CVeyraBackend.None -> "CVeyra has no live backend"
            },
        )

        val presentRequired = requiredSymbols.count { symbols[it] != null }
        checks += VivoEvidenceCheck(
            "Live kernel symbols",
            when {
                presentRequired >= 4 && symbols["_text"] != null && symbols["init_task"] != null ->
                    VivoEvidenceState.Verified
                symbols.isNotEmpty() -> VivoEvidenceState.Unknown
                else -> VivoEvidenceState.Missing
            },
            "$presentRequired/${requiredSymbols.size} required symbols resolved",
        )

        checks += VivoEvidenceCheck(
            "Physical kernel code range",
            if (range != null) VivoEvidenceState.Verified else VivoEvidenceState.Missing,
            range ?: "/proc/iomem did not expose a non-zero Kernel code range",
        )

        checks += VivoEvidenceCheck(
            "vivo anti-root module state",
            when (vrKo) {
                true -> VivoEvidenceState.Verified
                false -> VivoEvidenceState.Verified
                null -> VivoEvidenceState.Unknown
            },
            when (vrKo) {
                true -> "vr.ko / vr module is loaded"
                false -> "module list readable; vr.ko not present"
                null -> "module list unavailable/redacted"
            },
        )

        val series = "4.19"
        val buildable = BaselineRegistry.profileIdFor(
            BaselineScheme.VIVO,
            series,
            snapshot.kernelRelease,
        ) ?: BaselineRegistry.profileIdFor(
            BaselineScheme.UNIVERSAL,
            series,
            snapshot.kernelRelease,
        )
        checks += VivoEvidenceCheck(
            "Buildable Veyra baseline",
            if (buildable != null) VivoEvidenceState.Verified else VivoEvidenceState.Missing,
            buildable ?: "no executable 4.19 base-library/baseline is registered",
        )

        val config = parseConfig(output)
        checks += VivoEvidenceCheck(
            "Kernel config evidence",
            if (config.isNotEmpty()) VivoEvidenceState.Verified else VivoEvidenceState.Unknown,
            if (config.isEmpty()) {
                "config.gz unavailable"
            } else {
                config.joinToString(" · ")
            },
        )

        return VivoKonaEvidenceReport(
            applicable = true,
            checks = checks,
            backend = backend,
            liveSymbols = symbols,
            kernelCodeRange = range,
            vrKoSeen = vrKo,
            buildableBaselineId = buildable,
        )
    }

    fun export(context: Context): String {
        val snapshot = DeviceSnapshot.current()
        val report = inspect(context, snapshot)
        val safeModel = snapshot.model.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeBuild = snapshot.buildId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val body = buildString {
            appendLine("Veyra Vivo/Kona Evidence Ladder")
            appendLine("Model: ${snapshot.model}")
            appendLine("Device: ${snapshot.device}")
            appendLine("Build: ${snapshot.buildId}")
            appendLine("Kernel: ${snapshot.kernelRelease}")
            appendLine()
            report.lines().forEach(::appendLine)
        }
        return DownloadStore.save(
            context,
            "Veyra-Vivo-Kona-Evidence-$safeModel-$safeBuild.txt",
            body.toByteArray(),
        )
    }

    private fun liveCommand(): String = """
echo '=== VEYRA_VIVO_IDENTITY ==='
echo "PROP|model|§(getprop ro.product.model 2>/dev/null)"
echo "PROP|device|§(getprop ro.product.device 2>/dev/null)"
echo "PROP|board|§(getprop ro.product.board 2>/dev/null)"
echo "PROP|platform|§(getprop ro.board.platform 2>/dev/null)"
echo "PROP|hardware|§(getprop ro.hardware 2>/dev/null)"
echo "PROP|boot_hardware|§(getprop ro.boot.hardware 2>/dev/null)"
echo '=== VEYRA_VIVO_SYMBOLS ==='
for S in _text _stext init_task security_hook_heads selinux_state kallsyms_lookup_name; do
  L=§(grep -m1 " §S§" /proc/kallsyms 2>/dev/null || true)
  if [ -n "§L" ]; then
    A=§(printf '%s\n' "§L" | awk '{print §1}')
    echo "SYM|§S|§A"
  else
    echo "SYM|§S|missing"
  fi
done
echo '=== VEYRA_VIVO_IOMEM ==='
grep -i 'Kernel code' /proc/iomem 2>/dev/null | head -n 4 | sed 's/^/IOMEM|/'
echo '=== VEYRA_VIVO_MODULES ==='
if [ -r /proc/modules ]; then
  echo 'MODULES|readable'
  grep -Ei '(^|[[:space:]])(vr|vivo|defex)[^[:space:]]*' /proc/modules 2>/dev/null | sed 's/^/MODULE|/'
else
  echo 'MODULES|unreadable'
fi
echo '=== VEYRA_VIVO_CONFIG ==='
if [ -r /proc/config.gz ]; then
  toybox gzip -dc /proc/config.gz 2>/dev/null | grep -E '^(CONFIG_KALLSYMS|CONFIG_KALLSYMS_ALL|CONFIG_ARCH_KONA|CONFIG_BUILD_ARM64_UNCOMPRESSED_KERNEL)=' | sed 's/^/CONFIG|/'
fi
""".trimIndent().replace('§', '$')

    private fun parseProperties(output: String): Map<String, String> =
        output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split('|', limit = 3)
                if (parts.size != 3 || parts[0] != "PROP") return@mapNotNull null
                val value = parts[2].trim()
                if (value.isBlank()) null else parts[1] to value
            }
            .toMap()

    private fun parseSymbols(output: String): Map<String, ULong> =
        output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split('|')
                if (parts.size != 3 || parts[0] != "SYM") return@mapNotNull null
                val raw = parts[2]
                if (raw == "missing" || raw.all { it == '0' }) return@mapNotNull null
                val value = raw.toULongOrNull(16) ?: return@mapNotNull null
                if (value == 0UL) null else parts[1] to value
            }
            .toMap()

    private fun parseKernelCodeRange(output: String): String? =
        output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("IOMEM|") }
            ?.removePrefix("IOMEM|")
            ?.trim()
            ?.takeIf { line ->
                val range = line.substringBefore(':').trim()
                val start = range.substringBefore('-').toULongOrNull(16) ?: return@takeIf false
                val end = range.substringAfter('-', "").toULongOrNull(16) ?: return@takeIf false
                start != 0UL && end > start
            }

    private fun parseVrKo(output: String): Boolean? {
        if ("MODULES|readable" !in output) return null
        return output.lineSequence().any { line ->
            line.startsWith("MODULE|") &&
                Regex("(^|[| ])vr(?:[._-]|\\s)", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }
    }

    private fun parseConfig(output: String): List<String> =
        output.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("CONFIG|") }
            .map { it.removePrefix("CONFIG|") }
            .filter(String::isNotBlank)
            .distinct()
            .toList()
}
