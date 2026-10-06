package ctrl.mietze.veyraroot

import android.content.Context
import java.util.Locale

internal data class MagicResearchSource(
    val label: String,
    val repository: String,
    val coverage: String,
    val exactEvidence: String,
)

internal object MagicResearchSources {
    val all = listOf(
        MagicResearchSource(
            label = "GhostLock K4.19 adapter",
            repository = "XiaoBaiLovesStirring/ghostlock-k419-adapter",
            coverage = "4.19 unstable / exact-adapter evidence",
            exactEvidence = "4.19.152-perf+",
        ),
        MagicResearchSource(
            label = "vivo 4.x kernel autopatch",
            repository = "4accccc/vivo-4.x-kernel-autopatch",
            coverage = "vivo 4.9 / 4.14 / 4.19 compatibility evidence",
            exactEvidence = "4.9.77+, 4.14.141+, 4.14.186, 4.19.127, 4.19.191 / 4.19.191+",
        ),
        MagicResearchSource(
            label = "vivo X60 Kona kernel source",
            repository = "ZProtons/android_kernel_vivo_kona",
            coverage = "vivo Kona / SM8250 source tree, ARM64 legacy-kernel evidence",
            exactEvidence = "Makefile 4.19.152; kona-perf_defconfig LOCALVERSION=-perf, KALLSYMS_ALL, ARCH_KONA, uncompressed ARM64 kernel; PD2046 appears in vivo driver code",
        ),
        MagicResearchSource(
            label = "Veyra Local / vivo X60",
            repository = "local://vivo-x60-legacy",
            coverage = "local-first vivo/iQOO legacy builder route",
            exactEvidence = "aliases V2045 / V2046A / 2045 / 2045T / PD2046 / PD2046F; exact source family 4.19.152-perf",
        ),
        MagicResearchSource(
            label = "GhostLock for OnePlus",
            repository = "p2p3p/GhostLock-for-OnePlus",
            coverage = "OnePlus GhostLock device tables and boot.img offset extraction, modern 6.x research",
            exactEvidence = "per-uname runtime offset table; known device directories include Ace 6T and OnePlus 15; unknown kernels are rejected rather than cross-used",
        ),
        MagicResearchSource(
            label = "GhostLock OnePlus multi-device",
            repository = "JoinChang/ghostlock-oneplus",
            coverage = "OnePlus 6.12-family adaptation workflow and multi-device runtime offset tables",
            exactEvidence = "device tables include OnePlus 13/15 and pudding-family targets; boot.img -> kallsyms workflow; physical kernel load is treated as board-specific live evidence",
        ),
        MagicResearchSource(
            label = "Root My S24 research",
            repository = "NanoTurtle1145/root-my-s24",
            coverage = "Samsung S24 family 6.1 GKI firmware-specific targets",
            exactEvidence = "documented S921/S928 target generation across 6.1.128/6.1.145 builds; repository explicitly warns that offsets must be regenerated per exact firmware",
        ),
        MagicResearchSource(
            label = "IonStack Samsung 5.10",
            repository = "sarabpal-dev/IonStack-S22U",
            coverage = "Samsung Android 12 / kernel 5.10 target-generation evidence",
            exactEvidence = "S22-family S901/S906/S908 plus related Samsung 5.10 GKI targets; target_generator combines kallsyms, config and Image and preserves target-specific fixes",
        ),
        MagicResearchSource(
            label = "GhostLock Honor 80 GT",
            repository = "yakidango-official/GhostLock-H80GT",
            coverage = "Honor 80 GT / AGT-AN00 MagicOS kernel 5.10 targets",
            exactEvidence = "documented AGT-AN00 targets on 5.10.198 and 5.10.209 with boot.img-derived kallsyms/image/disassembly provenance and matching KernelSU module workflow",
        ),
        MagicResearchSource(
            label = "RootMyVivo application",
            repository = "zenyxx-xd/RootMyVivo",
            coverage = "vivo/iQOO full-kernel-string catalog matching and temp-root/KernelSU workflow",
            exactEvidence = "catalog v5 matches payloads by full kernel string and keeps ready vs experimental state separate",
        ),
        MagicResearchSource(
            label = "RootMyVivo payload catalog",
            repository = "zenyxx-xd/RootMyVivo-Payloads",
            coverage = "vivo/iQOO 5.15 / 6.1 / 6.6 device-build evidence",
            exactEvidence = "schema v5 catalog contains exact build IDs for iQOO 13/Neo/Z-series and vivo X200-family; payload entries carry status, sha256, size and match-condition notes",
        ),
    )
}

internal data class VivoLegacyProfile(
    val name: String,
    val aliases: Set<String>,
    val expectedSeries: Set<String>,
    val exactReleasePrefixes: Set<String> = emptySet(),
    val platform: String = "",
    val architecture: String = "",
    val sourceRepository: String? = null,
    val sourceFacts: List<String> = emptyList(),
) {
    fun matches(snapshot: DeviceSnapshot): Boolean {
        val identity = listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.buildId,
            snapshot.fingerprint,
        ).joinToString(" ").lowercase(Locale.ROOT)
        if ("vivo" !in identity) return false
        return aliases.any { alias -> identity.contains(alias.lowercase(Locale.ROOT)) }
    }
}

internal object VivoLegacyProfiles {
    val X60 = VivoLegacyProfile(
        name = "vivo X60 / Kona",
        aliases = setOf("V2045", "V2046A", "2045", "2045T", "PD2046", "PD2046F", "PD2046F_EX"),
        expectedSeries = setOf("4.19"),
        exactReleasePrefixes = setOf("4.19.152-perf"),
        platform = "Qualcomm Kona / SM8250",
        architecture = "arm64",
        sourceRepository = "ZProtons/android_kernel_vivo_kona",
        sourceFacts = listOf(
            "source VERSION/PATCHLEVEL/SUBLEVEL = 4.19.152",
            "kona-perf_defconfig LOCALVERSION=-perf",
            "CONFIG_KALLSYMS_ALL=y",
            "CONFIG_ARCH_KONA=y",
            "CONFIG_BUILD_ARM64_UNCOMPRESSED_KERNEL=y",
            "PD2046 is present in vivo driver project checks",
        ),
    )

    val all = listOf(X60)

    fun detect(snapshot: DeviceSnapshot): VivoLegacyProfile? =
        all.firstOrNull { it.matches(snapshot) }
}

internal data class DfKmiInventory(
    val classic: List<String>,
    val next: List<String>,
) {
    val total: Int get() = classic.size + next.size

    companion object {
        fun read(context: Context): DfKmiInventory {
            fun list(path: String): List<String> = runCatching {
                context.assets.list(path)
                    ?.filter { it.endsWith("_kernelsu.ko") }
                    ?.sorted()
                    .orEmpty()
            }.getOrDefault(emptyList())

            return DfKmiInventory(
                classic = list("local-sources/dfroot/ko/classic"),
                next = list("local-sources/dfroot/ko/next"),
            )
        }
    }
}
