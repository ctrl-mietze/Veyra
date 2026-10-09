package ctrl.mietze.veyraroot

import android.content.Context
import android.os.Build
import java.util.Locale

internal enum class DfOemProfile(val label: String) {
    SamsungDefex("Samsung / DEFEX"),
    OnePlusOppo("OnePlus / Oppo"),
    Generic("Generic GKI"),
}

internal enum class DfRouteMode(val label: String) {
    Compatible("DF Compatible"),
    Enhanced("Veyra DF+"),
    Unsupported("Unavailable"),
}

internal data class DfRoutePlan(
    val mode: DfRouteMode,
    val available: Boolean,
    val oem: DfOemProfile,
    val kmi: String?,
    val kernelSeries: String,
    val androidMajor: Int,
    val flavor: KernelSuFlavor,
    val engineAsset: String?,
    val koAsset: String?,
    val ksudAsset: String?,
    val exactKmiAsset: Boolean,
    val enginePresent: Boolean,
    val ksudPresent: Boolean,
    val notes: List<String>,
    val blockers: List<String>,
) {
    val routeLabel: String
        get() = buildString {
            append(mode.label)
            kmi?.let { append(" · ").append(it) }
            append(" · ").append(oem.label)
            append(" · ").append(flavor.label)
        }

    fun asLines(): List<String> = buildList {
        add(routeLabel)
        add("Kernel KMI: android$androidMajor-$kernelSeries")
        add(
            "engine=${if (enginePresent) "present" else "missing"} · " +
                "ksud=${if (ksudPresent) "present" else "missing"} · " +
                "exact KMI module=${if (exactKmiAsset) "present" else "missing"}",
        )
        notes.forEach { add("✓ $it") }
        blockers.forEach { add("✗ $it") }
    }
}

internal object DfPlusPlanner {
    fun plan(
        context: Context,
        snapshot: DeviceSnapshot = DeviceSnapshot.current(),
        flavor: KernelSuFlavor = AppPreferences.kernelsuFlavor(context),
    ): DfRoutePlan {
        val systemAndroidMajor = snapshot.androidRelease.substringBefore('.').toIntOrNull()
            ?: androidMajorFromSdk(snapshot.sdk)
        val support = FastRootSupport.forSnapshot(snapshot)
        val androidMajor = support.androidMajor
        val kernelSeries = support.kernelSeries
        val kmi = support.kmi

        val oem = detectOem(snapshot)
        val blockers = ArrayList<String>()
        val notes = ArrayList<String>()

        val arm64 = snapshot.abi.contains("arm64", ignoreCase = true) ||
            snapshot.machine.contains("aarch64", ignoreCase = true)
        if (!arm64) blockers += "DirtyFrag route is arm64-only in this build"
        if (snapshot.sdk < Build.VERSION_CODES.P) blockers += "Android API 28+ is required"
        if (kmi == null) {
            blockers += support.reason
        }
        if (systemAndroidMajor != androidMajor) {
            notes += "Userspace Android $systemAndroidMajor retains kernel KMI android$androidMajor"
        }

        val routeFlavor = when (flavor) {
            KernelSuFlavor.KernelSu -> "classic"
            KernelSuFlavor.KernelSuNext -> "next"
            KernelSuFlavor.ReSukiSU -> null
        }
        if (routeFlavor == null) {
            blockers += "Current DF engine has no ReSukiSU kernel-module flavour"
        }

        val engineAsset = when (routeFlavor) {
            "classic" -> "local-sources/dfroot/libdfexp.so"
            "next" -> "local-sources/dfroot/libdfexpnext.so"
            else -> null
        }
        val ksudAsset = when (routeFlavor) {
            "classic" -> "local-sources/dfroot/ksud-new-classic"
            "next" -> "local-sources/dfroot/ksud-new-next"
            else -> null
        }
        val koAsset = if (kmi != null && routeFlavor != null) {
            "local-sources/dfroot/ko/$routeFlavor/${kmi}_kernelsu.ko"
        } else {
            null
        }

        val enginePresent = engineAsset?.let { assetExists(context, it) } == true
        val ksudPresent = ksudAsset?.let { assetExists(context, it) } == true
        val exactKmiAsset = koAsset?.let { assetExists(context, it) } == true

        if (!enginePresent) blockers += "DF native engine asset is missing"
        if (!ksudPresent) blockers += "KernelSU daemon asset is missing"
        if (kmi != null && routeFlavor != null && !exactKmiAsset) {
            blockers += "Exact local KMI module is missing: $kmi"
        }

        when (oem) {
            DfOemProfile.SamsungDefex -> {
                notes += "Samsung DEFEX-aware route selected"
                notes += "Samsung-specific KernelSU manager compatibility is expected"
            }
            DfOemProfile.OnePlusOppo -> {
                notes += "OPlus execution-block compatibility route selected"
            }
            DfOemProfile.Generic -> {
                notes += "Generic GKI route selected"
            }
        }

        if (exactKmiAsset) notes += "Exact KMI module exists locally"
        if (enginePresent && ksudPresent) notes += "DF engine and daemon are fully offline-capable"

        val available = blockers.isEmpty()
        val mode = when {
            !available -> DfRouteMode.Unsupported
            exactKmiAsset -> DfRouteMode.Enhanced
            else -> DfRouteMode.Compatible
        }

        return DfRoutePlan(
            mode = mode,
            available = available,
            oem = oem,
            kmi = kmi,
            kernelSeries = kernelSeries,
            androidMajor = androidMajor,
            flavor = flavor,
            engineAsset = engineAsset,
            koAsset = koAsset,
            ksudAsset = ksudAsset,
            exactKmiAsset = exactKmiAsset,
            enginePresent = enginePresent,
            ksudPresent = ksudPresent,
            notes = notes,
            blockers = blockers,
        )
    }

    fun compatiblePlan(
        context: Context,
        snapshot: DeviceSnapshot = DeviceSnapshot.current(),
        flavor: KernelSuFlavor = AppPreferences.kernelsuFlavor(context),
    ): DfRoutePlan {
        val enhanced = plan(context, snapshot, flavor)
        val blockers = enhanced.blockers.filterNot {
            it.startsWith("Exact local KMI module is missing:")
        }
        return enhanced.copy(
            mode = if (blockers.isEmpty()) DfRouteMode.Compatible else DfRouteMode.Unsupported,
            available = blockers.isEmpty(),
            blockers = blockers,
            notes = enhanced.notes +
                "Compatibility mode uses the native DF engine's embedded KMI route; external .ko evidence is optional",
        )
    }

    fun detectOem(snapshot: DeviceSnapshot): DfOemProfile {
        val identity = listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.fingerprint,
        ).joinToString(" ").lowercase(Locale.ROOT)

        return when {
            "samsung" in identity || snapshot.model.startsWith("SM-", ignoreCase = true) ->
                DfOemProfile.SamsungDefex
            "oneplus" in identity || "oppo" in identity || "realme" in identity ->
                DfOemProfile.OnePlusOppo
            else -> DfOemProfile.Generic
        }
    }

    private fun assetExists(context: Context, path: String): Boolean = runCatching {
        context.assets.open(path).use { input -> input.read() >= 0 }
    }.getOrDefault(false)

    private fun androidMajorFromSdk(sdk: Int): Int = when (sdk) {
        31, 32 -> 12
        33 -> 13
        34 -> 14
        35 -> 15
        36 -> 16
        37 -> 17
        else -> sdk
    }
}
