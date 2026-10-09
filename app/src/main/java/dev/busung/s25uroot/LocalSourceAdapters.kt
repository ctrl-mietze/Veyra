package ctrl.mietze.veyraroot

import android.content.Context
import java.util.Locale
import org.json.JSONObject

enum class LocalAdapterKind {
    KernelBuilder,
    OffsetResearch,
    Compatibility,
}

internal data class LocalSourceAdapter(
    val key: String,
    val kind: LocalAdapterKind,
    val repository: String,
    val title: String,
    val manufacturers: Set<String> = emptySet(),
    val modelPrefixes: Set<String> = emptySet(),
    val devices: Set<String> = emptySet(),
    val kernelSeries: Set<String> = emptySet(),
    val capabilities: List<String>,
) {
    fun matchScore(snapshot: DeviceSnapshot): Int {
        val manufacturer = snapshot.manufacturer.lowercase(Locale.ROOT)
        val model = snapshot.model.lowercase(Locale.ROOT)
        val device = snapshot.device.lowercase(Locale.ROOT)
        val identity = listOf(
            manufacturer,
            model,
            device,
            snapshot.buildId,
            snapshot.fingerprint,
        ).joinToString(" ").lowercase(Locale.ROOT)

        var score = 0
        if (manufacturers.any { it.lowercase(Locale.ROOT) in identity }) score += 35
        if (devices.any { it.equals(device, ignoreCase = true) }) score += 50
        if (modelPrefixes.any { prefix ->
                model.startsWith(prefix.lowercase(Locale.ROOT)) ||
                    identity.contains(prefix.lowercase(Locale.ROOT))
            }
        ) score += 45
        if (kernelSeries.any { snapshot.kernelRelease.startsWith("$it.") || snapshot.kernelRelease.startsWith(it) }) {
            score += 30
        }
        if (manufacturers.isEmpty() && modelPrefixes.isEmpty() && devices.isEmpty()) score += 5
        return score
    }

    fun matches(snapshot: DeviceSnapshot): Boolean {
        if (kind == LocalAdapterKind.Compatibility) return true
        val identityMatch =
            manufacturers.any { snapshot.manufacturer.contains(it, ignoreCase = true) } ||
                modelPrefixes.any { snapshot.model.startsWith(it, ignoreCase = true) } ||
                devices.any { snapshot.device.equals(it, ignoreCase = true) } ||
                modelPrefixes.any { snapshot.fingerprint.contains(it, ignoreCase = true) }
        val kernelMatch = kernelSeries.isEmpty() ||
            kernelSeries.any { series ->
                snapshot.kernelRelease.startsWith("$series.") ||
                    snapshot.kernelRelease.startsWith(series)
            }
        return identityMatch && kernelMatch
    }
}

internal data class LocalSourceMetadata(
    val kind: String,
    val repository: String,
    val coverage: String,
    val evidence: String,
    val note: String,
)

internal object LocalSourceAdapters {
    val all: List<LocalSourceAdapter> = listOf(
        LocalSourceAdapter(
            key = "research-oneplus-p2p3p",
            kind = LocalAdapterKind.OffsetResearch,
            repository = "p2p3p/GhostLock-for-OnePlus",
            title = "GhostLock OnePlus adapter",
            manufacturers = setOf("oneplus"),
            kernelSeries = setOf("6.6", "6.12"),
            capabilities = listOf(
                "per-uname offset-table matching",
                "boot.img → kallsyms source adaptation",
                "unknown-kernel rejection",
            ),
        ),
        LocalSourceAdapter(
            key = "research-rootmys24",
            kind = LocalAdapterKind.KernelBuilder,
            repository = "NanoTurtle1145/root-my-s24",
            title = "Root My S24 builder adapter",
            manufacturers = setOf("samsung"),
            modelPrefixes = setOf("SM-S921", "SM-S926", "SM-S928"),
            kernelSeries = setOf("6.1"),
            capabilities = listOf(
                "S24-family exact firmware matching",
                "boot image target regeneration",
                "firmware-specific offset validation",
            ),
        ),
        LocalSourceAdapter(
            key = "research-honor80gt",
            kind = LocalAdapterKind.KernelBuilder,
            repository = "yakidango-official/GhostLock-H80GT",
            title = "Honor 80 GT builder adapter",
            manufacturers = setOf("honor"),
            modelPrefixes = setOf("AGT-AN00"),
            kernelSeries = setOf("5.10"),
            capabilities = listOf(
                "AGT-AN00 exact build matching",
                "boot/kernel evidence extraction",
                "5.10 target generation",
            ),
        ),
        LocalSourceAdapter(
            key = "research-oneplus-joinchang",
            kind = LocalAdapterKind.KernelBuilder,
            repository = "JoinChang/ghostlock-oneplus",
            title = "GhostLock OnePlus multi-device adapter",
            manufacturers = setOf("oneplus"),
            kernelSeries = setOf("6.12"),
            capabilities = listOf(
                "multi-device runtime table matching",
                "boot.img → kallsyms adaptation",
                "board-specific physical evidence gate",
            ),
        ),
        LocalSourceAdapter(
            key = "research-ionstack-s22u",
            kind = LocalAdapterKind.KernelBuilder,
            repository = "sarabpal-dev/IonStack-S22U",
            title = "IonStack Samsung 5.10 adapter",
            manufacturers = setOf("samsung"),
            modelPrefixes = setOf("SM-S901", "SM-S906", "SM-S908"),
            kernelSeries = setOf("5.10"),
            capabilities = listOf(
                "S22-family model mapping",
                "kallsyms/config/Image target generation",
                "Samsung 5.10 source cross-check",
            ),
        ),
        LocalSourceAdapter(
            key = "research-rootmyvivo",
            kind = LocalAdapterKind.KernelBuilder,
            repository = "zenyxx-xd/RootMyVivo-Payloads",
            title = "RootMyVivo catalog adapter",
            manufacturers = setOf("vivo", "iqoo"),
            kernelSeries = setOf("5.15", "6.1", "6.6"),
            capabilities = listOf(
                "full-kernel-string matching",
                "ready/experimental state separation",
                "vivo/iQOO build catalog adaptation",
            ),
        ),
        LocalSourceAdapter(
            key = "research-shizuku-next",
            kind = LocalAdapterKind.Compatibility,
            repository = "rushiranpise/Shizuku-Next",
            title = "Shizuku Next compatibility adapter",
            capabilities = listOf(
                "CVeyra start-method reference",
                "wireless ADB compatibility evidence",
                "permission/networking behavior reference",
            ),
        ),
    )

    fun forSource(source: PayloadSource): LocalSourceAdapter? =
        source.localKey?.let(::forKey)

    fun forKey(key: String): LocalSourceAdapter? =
        all.firstOrNull { it.key == key }

    fun matching(snapshot: DeviceSnapshot): List<LocalSourceAdapter> =
        all
            .filter { it.matches(snapshot) }
            .sortedByDescending { it.matchScore(snapshot) }

    fun readMetadata(context: Context, key: String): LocalSourceMetadata? = runCatching {
        val raw = context.assets.open("local-sources/$key/source.json")
            .bufferedReader()
            .use { it.readText() }
        val json = JSONObject(raw)
        LocalSourceMetadata(
            kind = json.optString("kind"),
            repository = json.optString("repository"),
            coverage = json.optString("coverage"),
            evidence = json.optString("evidence"),
            note = json.optString("note"),
        )
    }.getOrNull()
}
