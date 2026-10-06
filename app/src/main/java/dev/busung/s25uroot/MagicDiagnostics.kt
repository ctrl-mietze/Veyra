package ctrl.mietze.veyraroot

import android.content.Context
import com.kernelpack.boot.BootImageParser
import com.kernelpack.boot.KernelDecompressor
import com.kernelpack.kallsyms.KallsymsFinder
import com.kernelpack.policy.BuildGate
import com.kernelpack.profile.BaselineRegistry
import com.kernelpack.profile.BaselineScheme
import java.security.MessageDigest
import java.util.Locale

private fun magicShellScript(text: String): String = text.trimIndent().replace('§', '$')

internal data class MagicDiagnosticReport(
    val title: String,
    val lines: List<String>,
    val savedPath: String? = null,
)

internal object MagicDiagnostics {
    fun sourceMatchMatrix(context: Context): MagicDiagnosticReport {
        val snapshot = DeviceSnapshot.current()
        val route = MagicBuilderController.oemRoute(snapshot)
        val series = BuildGate.seriesOf(snapshot.kernelRelease).orEmpty()

        data class Match(val source: MagicResearchSource, val score: Int, val why: String)
        val matches = MagicResearchSources.all.mapNotNull { source ->
            val repo = source.repository.lowercase(Locale.ROOT)
            val coverage = source.coverage.lowercase(Locale.ROOT)
            var score = 0
            val why = ArrayList<String>()

            fun hit(points: Int, text: String) {
                score += points
                why += text
            }

            when (route) {
                MagicBuilderOemRoute.Samsung -> {
                    if ("s24" in repo || "s22" in repo || "samsung" in coverage) {
                        hit(6, "Samsung source")
                    }
                }
                MagicBuilderOemRoute.Honor -> {
                    if ("h80gt" in repo || "honor" in coverage) hit(7, "Honor source")
                }
                MagicBuilderOemRoute.OnePlusOppoRealme -> {
                    if ("oneplus" in repo || "oneplus" in coverage) hit(7, "OnePlus/OPlus source")
                }
                MagicBuilderOemRoute.VivoIqoo -> {
                    if ("vivo" in repo || "iqoo" in coverage || "vivo" in coverage) {
                        hit(7, "vivo/iQOO source")
                    }
                }
                else -> Unit
            }

            when (series) {
                "4.19" -> if ("4.19" in source.exactEvidence || "4.x" in coverage) hit(4, "4.19 evidence")
                "5.10" -> if ("5.10" in source.exactEvidence || "5.10" in coverage) hit(4, "5.10 evidence")
                "5.15" -> if ("5.15" in source.exactEvidence || "5.15" in coverage) hit(4, "5.15 evidence")
                "6.1" -> if ("6.1" in source.exactEvidence || "6.1" in coverage) hit(4, "6.1 evidence")
                "6.6" -> if ("6.6" in source.exactEvidence || "6.6" in coverage) hit(4, "6.6 evidence")
                "6.12" -> if ("6.12" in source.exactEvidence || "6.12" in coverage) hit(4, "6.12 evidence")
            }

            val vivo = VivoLegacyProfiles.detect(snapshot)
            if (vivo?.sourceRepository == source.repository) hit(12, "exact local profile source")

            if (score > 0) Match(source, score, why.distinct().joinToString(", ")) else null
        }.sortedByDescending(Match::score)

        val lines = buildList {
            add("${snapshot.manufacturer} ${snapshot.model} · ${snapshot.kernelRelease}")
            add("OEM route: ${MagicBuilderController.oemRouteName(route)} · series=$series")
            if (matches.isEmpty()) {
                add("No device-specific research source matched. Generic Magic analysis remains available.")
            } else {
                matches.forEach { match ->
                    val tier = when {
                        match.score >= 14 -> "strong"
                        match.score >= 8 -> "family"
                        else -> "research"
                    }
                    add("[$tier] ${match.source.label} · ${match.why}")
                    add("  ${match.source.repository}")
                }
            }
            add("Research match never substitutes an exact runnable baseline.")
        }
        return MagicDiagnosticReport("Source Match Matrix", lines)
    }

    fun kernelFamilyGate(context: Context): MagicDiagnosticReport {
        val snapshot = DeviceSnapshot.current()
        val series = BuildGate.seriesOf(snapshot.kernelRelease).orEmpty()
        val major = series.substringBefore('.').toIntOrNull()
        val family = when {
            series in BaselineRegistry.UNSTABLE_4X_SERIES -> "4.x unstable"
            series in BaselineRegistry.TEST_SERIES -> "5.x legacy"
            series in BaselineRegistry.MAINLINE_SERIES -> "6.x mainline"
            major == 7 -> "7.x analysis-only"
            else -> "unregistered"
        }
        val universal = BaselineRegistry.profileIdFor(
            BaselineScheme.UNIVERSAL,
            series,
            snapshot.kernelRelease,
        )
        val vivo = BaselineRegistry.profileIdFor(
            BaselineScheme.VIVO,
            series,
            snapshot.kernelRelease,
        )
        return MagicDiagnosticReport(
            "Kernel Family Gate",
            listOf(
                "Kernel: ${snapshot.kernelRelease}",
                "Series: ${series.ifBlank { "unknown" }} · family=$family",
                "Universal exact baseline: ${universal ?: "none"}",
                "Vivo exact baseline: ${vivo ?: "none"}",
                "4.x enabled: ${AppPreferences.magicBuilderAllowUnstable4x(context)}",
                "Strict exact kernel: ${AppPreferences.magicBuilderStrictKernel(context)}",
                "Nearest-family override: ${AppPreferences.magicBuilderNearestFamily(context)}",
                when {
                    major == 7 -> "Verdict: Magic analysis supported; runnable output requires a registered exact baseline."
                    universal != null || vivo != null -> "Verdict: exact registered baseline exists."
                    else -> "Verdict: analysis first; no exact runnable baseline is registered."
                },
            ),
        )
    }

    fun kmiMatrix(context: Context): MagicDiagnosticReport {
        val snapshot = DeviceSnapshot.current()
        val fast = FastRootSupport.forSnapshot(snapshot)
        val inventory = DfKmiInventory.read(context)
        val expected = fast.kmi?.plus("_kernelsu.ko")
        val classic = expected != null && expected in inventory.classic
        val next = expected != null && expected in inventory.next
        return MagicDiagnosticReport(
            "KMI Matrix",
            buildList {
                add("Android ${fast.androidMajor} · kernel ${fast.kernelSeries}")
                add(fast.reason)
                add("Classic KMI modules: ${inventory.classic.size}")
                add("KernelSU-Next KMI modules: ${inventory.next.size}")
                if (expected != null) {
                    add("Required: $expected")
                    add("Classic: ${if (classic) "present" else "missing"}")
                    add("Next: ${if (next) "present" else "missing"}")
                }
                add(
                    if (fast.available && (classic || next)) {
                        "Verdict: New (fast) has a matching local KMI module."
                    } else {
                        "Verdict: no local exact KMI route; keep Standard/Magic analysis."
                    },
                )
            },
        )
    }

    fun liveSymbolProbe(context: Context): MagicDiagnosticReport {
        val backend = if (CVeyraPreferences.enabled(context)) {
            CVeyraController.probeBackend(context)
        } else {
            CVeyraBackend.None
        }
        if (backend == CVeyraBackend.None) {
            return MagicDiagnosticReport(
                "Live Symbol Probe",
                listOf("CVeyra has no privileged backend. Start CVeyra first."),
            )
        }

        val command = magicShellScript(
            """
for S in _text _stext init_task security_hook_heads selinux_state kallsyms_lookup_name; do
  LINE=§(grep -m1 " §S§" /proc/kallsyms 2>/dev/null || true)
  if [ -n "§LINE" ]; then
    echo "§S|§LINE"
  else
    echo "§S|missing"
  fi
done
            """,
        )
        val result = if (backend == CVeyraBackend.Root) {
            CVeyraController.rootShell(context, command)
        } else {
            CVeyraController.shell(context, command)
        }
        val lines = buildList {
            add("Backend: ${backend.storedValue}")
            if (result == null || result.exitCode != 0) {
                add("Live symbol probe failed.")
            } else {
                result.output.lineSequence().filter(String::isNotBlank).forEach { line ->
                    val symbol = line.substringBefore('|')
                    val value = line.substringAfter('|', "missing")
                    val address = value.substringBefore(' ').trim()
                    val visible = address.isNotBlank() &&
                        address != "0000000000000000" &&
                        address != "missing"
                    add("$symbol: ${if (visible) value else "not readable / hidden"}")
                }
            }
            add("Hidden symbols remain analysis evidence; Veyra does not guess their addresses.")
        }
        return MagicDiagnosticReport("Live Symbol Probe", lines)
    }

    fun bootEvidenceReport(context: Context): MagicDiagnosticReport {
        val capture = MagicBuilderController.captureLiveBoot(context)
        val bytes = capture.file.readBytes()
        val parsed = BootImageParser.parse(bytes, KernelDecompressor.default)
        val version = KallsymsFinder.linuxVersionFromImage(parsed.image)
        val arch = KallsymsFinder.guessArchitectureFromImage(parsed.image)
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val snapshot = DeviceSnapshot.current()
        val parsedRelease = version?.let { kernelReleaseFromBanner(it.first, it.second) }
        val exactMatch = parsedRelease?.equals(snapshot.kernelRelease, ignoreCase = true) == true

        val lines = listOf(
            "Device: ${snapshot.model} (${snapshot.device})",
            "Source: ${capture.blockDevice}",
            "Boot bytes: ${bytes.size}",
            "SHA-256: $sha",
            "Container: ${parsed.info.container}",
            "Decompressed kernel: ${parsed.info.decompressed}",
            "ARM64 image header: ${parsed.info.arm64Image}",
            "Architecture: ${arch?.display ?: "unresolved"}",
            "Kernel banner: ${version?.first ?: "not found"}",
            "Live kernel: ${snapshot.kernelRelease}",
            "Exact release match: $exactMatch",
        )

        val safeDevice = snapshot.device.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = DownloadStore.save(
            context,
            "Veyra-Magic-BootEvidence-$safeDevice.txt",
            buildString {
                appendLine("Veyra Magic Boot Evidence")
                lines.forEach(::appendLine)
            }.toByteArray(),
        )
        return MagicDiagnosticReport("Boot Evidence Report", lines, path)
    }
}
