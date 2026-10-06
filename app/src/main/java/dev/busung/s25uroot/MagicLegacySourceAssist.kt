package ctrl.mietze.veyraroot

import com.kernelpack.model.TargetProfile
import org.json.JSONArray
import org.json.JSONObject

internal data class MagicSourceAnalysis(
    val summary: String,
    val header: String,
    val offsetsJson: String,
    val notices: List<String>,
)

internal data class MagicSourceComparison(
    val agreements: List<String>,
    val conflicts: List<String>,
    val unresolved: List<String>,
) {
    val checked: Int get() = agreements.size + conflicts.size + unresolved.size
    val clean: Boolean get() = conflicts.isEmpty()
}

/**
 * Evidence-only fallback for the public 4.19.152-perf+ Kona source/adapter pair.
 *
 * Nothing here creates a runnable payload or guesses a physical load address. It exists so an exact
 * V2045/Kona capture remains useful when a vendor kallsyms layout cannot be decoded by the generic
 * parser: Magic Builder can still export what is known and explicitly name what is missing.
 */
internal object VivoKonaSourceAssist {
    private const val ADAPTER_REPO = "XiaoBaiLovesStirring/ghostlock-k419-adapter"
    private const val KERNEL_REPO = "ZProtons/android_kernel_vivo_kona"
    private const val EXACT_RELEASE = "4.19.152-perf+"

    private const val K419_IMAGE_BASE = 0xffffff8008000000UL
    private const val K419_TEXT = 0xffffff8008080000UL

    /**
     * Reference values that agree between the public k419 target.h/offset table and have a direct
     * kernelpack key. Adapter offsets are relative to KIMAGE_TEXT_BASE, while kernelpack intentionally
     * uses _text as its analysis base. Comparison therefore happens on absolute addresses below.
     * SELinux enforcing is intentionally excluded because the two public adapter files currently
     * disagree on that value; a conflicted source must not become an automatic gate.
     */
    private val k419ReferenceOffsets: Map<String, Long> = linkedMapOf(
        "INIT_TASK" to 0x2E00000L,
        "ROOT_TASK_GROUP" to 0x30F21C0L,
        "SECURITY_HOOK_HEADS" to 0x254FD90L,
        "KMALLOC_CACHES" to 0x254F880L,
        "ANON_PIPE_BUF_OPS" to 0x1D79200L,
        "ASHMEM_FOPS" to 0x1EF0B10L,
        "ASHMEM_IOCTL" to 0xE406F0L,
        "ASHMEM_MMAP" to 0xE40E70L,
        "ASHMEM_OPEN" to 0xE40FE0L,
        "ASHMEM_RELEASE" to 0xE41070L,
        "ASHMEM_SHOW_FDINFO" to 0xE410F8L,
        "COPY_SPLICE_READ" to 0x346C28L,
        "NOOP_LLSEEK" to 0x3020D8L,
        "SLIDE_NFULNL_LOGGER" to 0x2E165A8L,
    )

    fun compare(profile: TargetProfile): MagicSourceComparison {
        val agreements = ArrayList<String>()
        val conflicts = ArrayList<String>()
        val unresolved = ArrayList<String>()

        val actualText = profile.imageBase.toULong()
        if (actualText == K419_TEXT) {
            agreements += "_text=0x" + actualText.toString(16)
        } else {
            conflicts += "_text boot=0x" + actualText.toString(16) +
                " ref=0x" + K419_TEXT.toString(16)
        }

        k419ReferenceOffsets.forEach { (key, expectedOffset) ->
            val actualOffset = profile.offset(key)
            if (actualOffset == null) {
                unresolved += key
                return@forEach
            }
            val actualAddress = actualText + actualOffset.toULong()
            val expectedAddress = K419_IMAGE_BASE + expectedOffset.toULong()
            if (actualAddress == expectedAddress) {
                agreements += key
            } else {
                conflicts += key + " boot=0x" + actualAddress.toString(16) +
                    " ref=0x" + expectedAddress.toString(16)
            }
        }

        return MagicSourceComparison(agreements, conflicts, unresolved)
    }
    fun supports(snapshot: DeviceSnapshot): Boolean {
        val profile = VivoLegacyProfiles.detect(snapshot) ?: return false
        return profile.exactReleasePrefixes.any { prefix ->
            snapshot.kernelRelease.startsWith(prefix, ignoreCase = true)
        }
    }

    fun analyze(snapshot: DeviceSnapshot, reason: Throwable): MagicSourceAnalysis {
        require(supports(snapshot))
        val profile = requireNotNull(VivoLegacyProfiles.detect(snapshot))

        val missing = listOf(
            "Live kernel_phys_load / Kernel code physical address",
            "A complete live kallsyms decode or equivalent symbol verification",
            "End-to-end validation of the adapted payload on this exact Vivo build",
            "Runtime confirmation of vivo anti-root modules such as vr.ko when present",
        )

        val summary = buildString {
            appendLine("Source-assisted legacy analysis")
            appendLine("${profile.name} · ${snapshot.kernelRelease}")
            appendLine("${profile.platform} · ${profile.architecture}")
            appendLine("Kernel source: $KERNEL_REPO")
            appendLine("4.19 adapter: $ADAPTER_REPO")
            appendLine("Exact release evidence: $EXACT_RELEASE")
            appendLine("Generic kallsyms parser: ${reason.javaClass.simpleName}")
            appendLine("Runnable payload: NO — live physical-load/symbol validation still required")
            append("Missing: ").append(missing.joinToString("; "))
        }

        val header = buildString {
            appendLine("/*")
            appendLine(" * Veyra Magic Builder source-assisted analysis")
            appendLine(" * NOT A RUNNABLE target.h — do not feed directly into a payload build.")
            appendLine(" * Device: ${snapshot.model} (${snapshot.device})")
            appendLine(" * Live kernel: ${snapshot.kernelRelease}")
            appendLine(" * Platform evidence: ${profile.platform}, ${profile.architecture}")
            appendLine(" * Kernel source: $KERNEL_REPO")
            appendLine(" * Adapter evidence: $ADAPTER_REPO")
            appendLine(" * Source adapter exact release: $EXACT_RELEASE")
            appendLine(" *")
            appendLine(" * The public adapter carries a 4.19-specific structure/offset table but leaves")
            appendLine(" * kernel_phys_load unresolved. Veyra therefore treats it as source evidence only")
            appendLine(" * until the live target supplies the remaining physical/symbol evidence.")
            appendLine(" */")
            appendLine("#define VEYRA_SOURCE_ASSISTED_ONLY 1")
            appendLine("#define VEYRA_EXPECTED_ARM64 1")
            appendLine("#define VEYRA_EXPECTED_KONA_SM8250 1")
            appendLine("#define VEYRA_EXPECTED_KALLSYMS_ALL 1")
        }

        val json = JSONObject()
            .put("format", "veyra-magic-source-analysis-v1")
            .put("runnable", false)
            .put("device", snapshot.device)
            .put("model", snapshot.model)
            .put("manufacturer", snapshot.manufacturer)
            .put("kernelRelease", snapshot.kernelRelease)
            .put("androidRelease", snapshot.androidRelease)
            .put("sdk", snapshot.sdk)
            .put("abi", snapshot.abi)
            .put("platform", profile.platform)
            .put("architecture", profile.architecture)
            .put("kernelSource", KERNEL_REPO)
            .put("adapterSource", ADAPTER_REPO)
            .put("adapterRelease", EXACT_RELEASE)
            .put("sourceFacts", JSONArray(profile.sourceFacts))
            .put("parserFailure", reason.javaClass.simpleName + ": " + reason.message.orEmpty())
            .put("missingLiveEvidence", JSONArray(missing))
            .toString(2)

        return MagicSourceAnalysis(
            summary = summary,
            header = header,
            offsetsJson = json,
            notices = listOf(
                "Exact 4.19.152-perf+ source evidence matched the live Vivo/Kona profile.",
                "No physical address or missing kernel symbol was guessed.",
                "Exported artifacts are analysis-only until the remaining live evidence is verified.",
            ),
        )
    }
}
