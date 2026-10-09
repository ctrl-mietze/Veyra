package ctrl.mietze.veyraroot

import android.content.Context
import com.kernelpack.policy.BuildGate
import com.kernelpack.profile.BaselineRegistry
import com.kernelpack.profile.BaselineScheme

enum class BuilderStrategyState(val label: String) {
    RunnableCandidate("runnable candidate"),
    AnalysisOnly("analysis only"),
    Blocked("blocked"),
}

data class BuilderStrategyCandidate(
    val id: String,
    val label: String,
    val state: BuilderStrategyState,
    val reason: String,
    val priority: Int,
)

data class BuilderStrategyReport(
    val kernelRelease: String,
    val series: String,
    val oemRouteName: String,
    val candidates: List<BuilderStrategyCandidate>,
) {
    val recommended: BuilderStrategyCandidate?
        get() = candidates
            .filter { it.state == BuilderStrategyState.RunnableCandidate }
            .minByOrNull { it.priority }
            ?: candidates
                .filter { it.state == BuilderStrategyState.AnalysisOnly }
                .minByOrNull { it.priority }

    fun lines(): List<String> = buildList {
        add(
            "Kernel: $kernelRelease · series=${series.ifBlank { "unknown" }} · " +
                "OEM=$oemRouteName",
        )
        candidates.sortedBy { it.priority }.forEach { candidate ->
            add("[${candidate.state.label}] ${candidate.label} · ${candidate.reason}")
        }
        recommended?.let {
            add("Recommended next route: ${it.label} · ${it.state.label}")
        }
        add("A runnable candidate is still subject to the route's runtime/evidence gates.")
    }
}

internal object BuilderStrategyPlanner {
    fun evaluate(
        context: Context,
        snapshot: DeviceSnapshot = DeviceSnapshot.current(),
    ): BuilderStrategyReport {
        val series = BuildGate.seriesOf(snapshot.kernelRelease).orEmpty()
        val route = MagicBuilderController.oemRoute(snapshot)
        val flavor = AppPreferences.kernelsuFlavor(context)
        val candidates = ArrayList<BuilderStrategyCandidate>()

        fun baseline(
            id: String,
            label: String,
            scheme: BaselineScheme,
            priority: Int,
        ) {
            if (series.isBlank()) {
                candidates += BuilderStrategyCandidate(
                    id,
                    label,
                    BuilderStrategyState.Blocked,
                    "kernel series could not be parsed",
                    priority,
                )
                return
            }
            val profileId = BaselineRegistry.profileIdFor(
                scheme,
                series,
                snapshot.kernelRelease,
            )
            val entry = profileId?.let(BaselineRegistry::byId)
            candidates += when {
                entry == null -> BuilderStrategyCandidate(
                    id,
                    label,
                    BuilderStrategyState.AnalysisOnly,
                    "no registered executable baseline for $series",
                    priority,
                )
                entry.buildable -> BuilderStrategyCandidate(
                    id,
                    label,
                    BuilderStrategyState.RunnableCandidate,
                    "registered buildable baseline ${entry.profile.id} · ${entry.quad()}",
                    priority,
                )
                else -> BuilderStrategyCandidate(
                    id,
                    label,
                    BuilderStrategyState.AnalysisOnly,
                    "baseline ${entry.profile.id} exists but its offset set is not buildable",
                    priority,
                )
            }
        }

        val vivoRoute = route == MagicBuilderOemRoute.VivoIqoo
        if (vivoRoute) {
            baseline(
                id = "vivo",
                label = "Vivo / iQOO builder",
                scheme = BaselineScheme.VIVO,
                priority = 5,
            )
        }

        baseline(
            id = "universal",
            label = "Standard Universal builder",
            scheme = BaselineScheme.UNIVERSAL,
            priority = if (vivoRoute) 15 else 5,
        )

        val dfPlus = DfPlusPlanner.plan(context, snapshot, flavor)
        candidates += BuilderStrategyCandidate(
            id = "df-plus",
            label = "Veyra DF+",
            state = if (dfPlus.available) {
                BuilderStrategyState.RunnableCandidate
            } else {
                BuilderStrategyState.Blocked
            },
            reason = if (dfPlus.available) {
                dfPlus.routeLabel
            } else {
                dfPlus.blockers.joinToString("; ").ifBlank { "no coherent DF+ route" }
            },
            priority = 20,
        )

        val dfCompatible = DfPlusPlanner.compatiblePlan(context, snapshot, flavor)
        candidates += BuilderStrategyCandidate(
            id = "df-compatible",
            label = "DF Compatible",
            state = if (dfCompatible.available) {
                BuilderStrategyState.RunnableCandidate
            } else {
                BuilderStrategyState.Blocked
            },
            reason = if (dfCompatible.available) {
                dfCompatible.routeLabel
            } else {
                dfCompatible.blockers.joinToString("; ").ifBlank { "no coherent DF route" }
            },
            priority = 30,
        )

        val vivoLegacy = VivoLegacyProfiles.detect(snapshot)
        if (vivoLegacy != null) {
            val exactSource = vivoLegacy.exactReleasePrefixes.any { prefix ->
                snapshot.kernelRelease.startsWith(prefix, ignoreCase = true)
            }
            candidates += BuilderStrategyCandidate(
                id = "vivo-kona-source",
                label = "Vivo/Kona source-assisted Magic",
                state = if (exactSource) {
                    BuilderStrategyState.AnalysisOnly
                } else {
                    BuilderStrategyState.Blocked
                },
                reason = if (exactSource) {
                    "exact public source family matched; live symbol/physical evidence is collected, but no executable 4.19 baseline is registered"
                } else {
                    "device profile matched but full kernel release is outside the exact source family"
                },
                priority = 80,
            )
        } else if (
            vivoRoute &&
            series == "4.19" &&
            snapshot.kernelRelease.startsWith("4.19.152", ignoreCase = true)
        ) {
            candidates += BuilderStrategyCandidate(
                id = "vivo-kona-candidate",
                label = "Vivo/Kona live-confirmation route",
                state = BuilderStrategyState.AnalysisOnly,
                reason = "unlisted vivo/iQOO 4.19.152 variant; Evidence Ladder can confirm Kona/SM8250 live without guessing a device alias",
                priority = 82,
            )
        }

        return BuilderStrategyReport(
            kernelRelease = snapshot.kernelRelease,
            series = series,
            oemRouteName = MagicBuilderController.oemRouteName(route),
            candidates = candidates,
        )
    }
}
