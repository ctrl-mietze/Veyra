package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetGapTest {

    private val device = DeviceSnapshot(
        manufacturer = "samsung",
        model = "SM-S918B",
        device = "dm3q",
        kernelRelease = "6.1.99-android14-11-abcdef",
        kernelVersionInfo = "#1 SMP PREEMPT",
        machine = "aarch64",
        buildId = "BP4A.251205.006",
        fingerprint = "samsung/dm3qxeea/dm3q:16/BP4A.251205.006/S918BXXSAFZF5:user/release-keys",
        androidRelease = "16",
        sdk = 36,
        abi = "arm64-v8a",
        pageSize = 4096,
    )

    private fun profile(
        id: String,
        name: String,
        models: List<String>,
        kernels: List<String>,
    ) = TargetProfile(
        profileId = id,
        displayName = name,
        models = models.toSet(),
        kernelVersions = kernels.toSet(),
        exploit = RemoteArtifact(url = "https://example.invalid/$id.so", size = 1024),
        kernelSu = RemoteArtifact(url = "https://example.invalid/$id-ksud", size = 2048),
    )

    @Test
    fun `the identity is the model and the kernel release`() {
        val described = TargetGap.describe(device)
        assertTrue(described.contains("SM-S918B"))
        assertTrue(described.contains(device.kernelRelease))
    }

    @Test
    fun `the same model on another kernel is named before another model on this kernel`() {
        val catalog = listOf(
            profile("other-model", "Galaxy S24 series", listOf("SM-S921B"), listOf(device.kernelVersion)),
            profile("same-model", "S23 series", listOf("SM-S918B"), listOf("6.1.98")),
        )
        val closest = TargetGap.closest(device, catalog)
        assertEquals(listOf("S23 series", "Galaxy S24 series"), closest.map { it.displayName })
        assertEquals(TargetGap.Reason.SameModel, closest.first().reason)
        assertEquals(TargetGap.Reason.SameKernel, closest.last().reason)
    }

    @Test
    fun `an entry that covers the device is a match, not a near miss`() {
        // The three-part rule: the same model and the same three-part version is a profile the run
        // will use, whatever the rest of the release string says. So it can never be named as close.
        val catalog = listOf(
            profile("sibling", "S23 series", listOf("SM-S918B"), listOf(device.kernelVersion)),
        )
        assertTrue(device.kernelVersion.isNotEmpty())
        assertTrue(TargetGap.closest(device, catalog).isEmpty())
    }

    @Test
    fun `an entry is only named for something it lists`() {
        // An entry for another model on another kernel is not close to anything, and naming it would
        // send the user to a profile that cannot help them.
        val catalog = listOf(
            profile("unrelated", "Galaxy A series", listOf("SM-A155N"), listOf("5.10.1")),
        )
        assertTrue(TargetGap.closest(device, catalog).isEmpty())
    }

    @Test
    fun `an entry that matches the full kernel release is never described as a gap`() {
        // Same rule, stated for the exact case: the refusal must not name the profile it should have
        // used, or the message would contradict itself.
        val catalog = listOf(
            profile("exact", "S23 series", listOf("SM-S918B"), listOf(device.kernelRelease)),
        )
        assertTrue(TargetGap.closest(device, catalog).isEmpty())
    }

    @Test
    fun `the kernels a named entry covers travel with it`() {
        val catalog = listOf(profile("same-model", "S23 series", listOf("SM-S918B"), listOf("6.1.97", "6.1.98")))
        val named = TargetGap.closest(device, catalog).first()
        assertTrue(named.kernels.contains("6.1.97"))
    }

    @Test
    fun `the model comparison ignores case, as the rest of the app does`() {
        val catalog = listOf(profile("same-model", "S23 series", listOf("sm-s918b"), listOf("6.1.98")))
        assertEquals(1, TargetGap.closest(device, catalog).size)
    }

    @Test
    fun `a long catalog is cut to the shortest useful answer`() {
        val catalog = (1..6).map { index ->
            profile("same-model-$index", "S23 series $index", listOf("SM-S918B"), listOf("6.1.9$index"))
        }
        assertEquals(3, TargetGap.closest(device, catalog).size)
    }
}
