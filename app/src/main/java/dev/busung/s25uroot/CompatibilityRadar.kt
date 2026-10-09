package ctrl.mietze.veyraroot

import android.content.Context
import com.kernelpack.policy.BuildGate
import com.kernelpack.profile.BaselineRegistry
import com.kernelpack.profile.BaselineScheme
import java.security.MessageDigest

internal data class CompatibilityRadarReport(
    val deviceSignature: String,
    val lines: List<String>,
) {
    val summary: String
        get() = lines.firstOrNull().orEmpty()

    fun asText(): String = buildString {
        appendLine("Veyra Compatibility Radar")
        appendLine("Device signature: $deviceSignature")
        appendLine()
        lines.forEach(::appendLine)
    }
}

internal object VeyraCompatibilityRadar {
    fun scan(context: Context): CompatibilityRadarReport {
        val snapshot = DeviceSnapshot.current()
        val series = BuildGate.seriesOf(snapshot.kernelRelease).orEmpty()
        val major = series.substringBefore('.').toIntOrNull()
        val family = when {
            series in BaselineRegistry.UNSTABLE_4X_SERIES -> "4.x (unstable)"
            series in BaselineRegistry.TEST_SERIES -> "5.x Legacy"
            series in BaselineRegistry.MAINLINE_SERIES -> "6.x mainline"
            major == 7 -> "7.x Magic analysis"
            else -> "unregistered family"
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
        val vivoLocal = VivoLegacyProfiles.detect(snapshot)
        val fast = FastRootSupport.forSnapshot(snapshot)
        val flavor = AppPreferences.kernelsuFlavor(context)
        val dfPlan = DfPlusPlanner.plan(context, snapshot, flavor)
        val inventory = DfKmiInventory.read(context)
        val expectedKo = dfPlan.kmi?.let { it + "_kernelsu.ko" }
        val classicPresent = expectedKo != null && expectedKo in inventory.classic
        val nextPresent = expectedKo != null && expectedKo in inventory.next

        fun assetExists(path: String): Boolean = runCatching {
            context.assets.open(path).use { it.read() >= -1 }
        }.getOrDefault(false)

        val pixelManifest = assetExists("local-sources/pixel/targets-v3.json")
        val dfManifest = assetExists("local-sources/dfroot/targets-v3.json")
        val dfClassic = assetExists("local-sources/dfroot/libdfexp.so")
        val dfNext = assetExists("local-sources/dfroot/libdfexpnext.so")

        val signatureMaterial = listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.buildId,
            snapshot.kernelRelease,
            snapshot.fingerprint,
        ).joinToString("|")
        val signature = MessageDigest.getInstance("SHA-256")
            .digest(signatureMaterial.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

        val lines = buildList {
            add("${snapshot.manufacturer} ${snapshot.model} · ${snapshot.device}")
            add("Android ${snapshot.androidRelease} (API ${snapshot.sdk})")
            add("Build: ${snapshot.buildId}")
            add("Kernel: ${snapshot.kernelRelease}")
            add("Family: $family · series=${series.ifBlank { "unknown" }}")
            add("OEM route: ${MagicBuilderController.oemRouteName(MagicBuilderController.oemRoute(snapshot))}")
            add("Universal exact baseline: ${universal ?: "none → analysis only"}")
            add("Vivo exact baseline: ${vivo ?: "none → analysis only"}")
            add(
                if (vivoLocal != null) {
                    "Local vivo profile: ${vivoLocal.name} · expected ${vivoLocal.expectedSeries.joinToString()}"
                } else {
                    "Local vivo profile: no exact device match"
                },
            )
            add("Root method: ${AppPreferences.rootMethod(context).label}")
            add("KernelSU flavor: ${flavor.label}")
            add("DF route: ${if (dfPlan.available) dfPlan.routeLabel else "blocked"}")
            if (!dfPlan.available) {
                add("DF blockers: ${dfPlan.blockers.joinToString("; ")}")
            }
            add("DF KMI assets: ${inventory.total} total · classic=${inventory.classic.size} · next=${inventory.next.size}")
            if (dfPlan.kmi != null) {
                add("Required KMI: ${dfPlan.kmi} · classic=${if (classicPresent) "OK" else "missing"} · next=${if (nextPresent) "OK" else "missing"}")
                add("OEM DF profile: ${dfPlan.oem.label}")
            }
            add("Local Pixel catalog: ${if (pixelManifest) "OK" else "missing"}")
            add("Local DFRoot catalog: ${if (dfManifest) "OK" else "missing"}")
            add("DF engines: classic=${if (dfClassic) "OK" else "missing"} · next=${if (dfNext) "OK" else "missing"}")
        }

        return CompatibilityRadarReport(
            deviceSignature = signature,
            lines = lines,
        )
    }

    fun export(context: Context): String {
        val report = scan(context)
        val snapshot = DeviceSnapshot.current()
        val safeModel = snapshot.model.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeBuild = snapshot.buildId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return DownloadStore.save(
            context,
            "Veyra-Compatibility-$safeModel-$safeBuild.txt",
            report.asText().toByteArray(),
        )
    }
}
