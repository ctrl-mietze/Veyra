package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DfPlusTest {
    private fun snapshot(
        manufacturer: String,
        model: String,
        device: String = "device",
        release: String = "6.12.35-android16-8-gki",
        android: String = "16",
        sdk: Int = 36,
    ) = DeviceSnapshot(
        manufacturer = manufacturer,
        model = model,
        device = device,
        kernelRelease = release,
        kernelVersionInfo = "#1 SMP PREEMPT",
        machine = "aarch64",
        buildId = "TEST.001",
        fingerprint = "$manufacturer/$device/$device:16/TEST",
        androidRelease = android,
        sdk = sdk,
        abi = "arm64-v8a",
        pageSize = 4096L,
    )

    @Test
    fun samsungGetsDefexProfile() {
        assertEquals(
            DfOemProfile.SamsungDefex,
            DfPlusPlanner.detectOem(snapshot("samsung", "SM-S938B", "pa3q")),
        )
    }

    @Test
    fun oplusFamilyGetsOplusProfile() {
        assertEquals(
            DfOemProfile.OnePlusOppo,
            DfPlusPlanner.detectOem(snapshot("OnePlus", "CPH2581", "waffle")),
        )
        assertEquals(
            DfOemProfile.OnePlusOppo,
            DfPlusPlanner.detectOem(snapshot("realme", "RMX0000")),
        )
    }

    @Test
    fun unrelatedGkiDeviceStaysGeneric() {
        assertEquals(
            DfOemProfile.Generic,
            DfPlusPlanner.detectOem(snapshot("Google", "Pixel 9 Pro XL", "komodo")),
        )
    }

    @Test
    fun rootMethodsKeepCompatibleAndEnhancedDistinct() {
        assertEquals(RootMethod.DfCompatible, RootMethod.fromStoredValue("df-compatible"))
        assertEquals(RootMethod.Fast, RootMethod.fromStoredValue("fast"))
        assertEquals(RootMethod.Standard, RootMethod.fromStoredValue("unknown"))
        assertTrue(RootMethod.DfCompatible.usesDf)
        assertTrue(RootMethod.Fast.usesDf)
        assertFalse(RootMethod.Standard.usesDf)
    }

    @Test
    fun kmiMatrixIncludesCurrentAndroidFamilies() {
        val a14 = FastRootSupport.forSnapshot(
            snapshot("Google", "Pixel", release = "6.1.99-android14", android = "14", sdk = 34),
        )
        assertTrue(a14.available)
        assertEquals("android14-6.1", a14.kmi)

        val a16 = FastRootSupport.forSnapshot(
            snapshot("samsung", "SM-S938B", release = "6.12.35-android16", android = "16", sdk = 36),
        )
        assertTrue(a16.available)
        assertEquals("android16-6.12", a16.kmi)

        val upgradedSamsung = FastRootSupport.forSnapshot(
            snapshot(
                "samsung",
                "SM-S938U1",
                "pa3q",
                release = "6.6.98-android15-8-pd6ff1cd-abogkiS938USQSCCZF9-4k",
                android = "16",
                sdk = 36,
            ),
        )
        assertTrue(upgradedSamsung.available)
        assertEquals("android15-6.6", upgradedSamsung.kmi)

        val sdkFallback = FastRootSupport.forSnapshot(
            snapshot(
                "samsung",
                "SM-S938B",
                release = "6.12.35-gki",
                android = "",
                sdk = 36,
            ),
        )
        assertTrue(sdkFallback.available)
        assertEquals("android16-6.12", sdkFallback.kmi)

        val unsupported = FastRootSupport.forSnapshot(
            snapshot("vivo", "V2046A", release = "4.19.152-perf+", android = "12", sdk = 31),
        )
        assertFalse(unsupported.available)
    }

    @Test
    fun availabilityPreservesExplicitCompatibleChoiceWhenBothRoutesExist() {
        val support = FastRootSupport(
            available = true,
            androidMajor = 16,
            kernelSeries = "6.12",
            kmi = "android16-6.12",
            reason = "test",
        )
        val availability = RootMethodAvailability(
            standardAvailable = true,
            fast = support,
        )
        assertEquals(
            RootMethod.DfCompatible,
            availability.effective(RootMethod.DfCompatible),
        )
    }
}
