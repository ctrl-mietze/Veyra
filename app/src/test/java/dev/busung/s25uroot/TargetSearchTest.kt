package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The target sheet's two controls: what fits this phone, and what was typed. */
class TargetSearchTest {

    private val mine = TargetProfile(
        profileId = "pa2q-S9360ZHSCCZG1",
        displayName = "Galaxy S25 series",
        models = setOf("SM-S9360"),
        kernelVersions = setOf("6.6.98"),
        exploit = RemoteArtifact("https://example.invalid/exploit", 1),
        kernelSu = RemoteArtifact("https://example.invalid/ksud", 1),
        sourceLabel = "Root-My-Galaxy-Payloads",
    )
    private val otherDevice = mine.copy(
        profileId = "pa3q-S9210ZSACCZG1",
        displayName = "Galaxy S24 series",
        models = setOf("SM-S9210"),
    )
    private val otherKernel = mine.copy(
        profileId = "pa2q-next-S9360ZHSCCZG1",
        kernelVersions = setOf("6.6.102"),
        flavor = KernelSuFlavor.KernelSuNext,
        sourceLabel = "payloads-next",
        kernelSuVersion = "3.4.0",
    )
    private val catalog = listOf(mine, otherDevice, otherKernel)
    private val device = snapshot(model = "SM-S9360", kernelRelease = "6.6.98-android15-8-build")

    /** What a query finds with the toggle off, as ids, so the expectation reads as the answer. */
    private fun found(query: String): List<String> =
        visibleTargets(catalog, device, fitsDeviceOnly = false, query = query).map { it.profileId }

    @Test
    fun `the toggle keeps only what this phone could run`() {
        assertEquals(
            listOf(mine.profileId),
            visibleTargets(catalog, device, fitsDeviceOnly = true, query = "").map { it.profileId },
        )
    }

    @Test
    fun `with the toggle off the whole catalog is reachable`() {
        assertEquals(3, visibleTargets(catalog, device, fitsDeviceOnly = false, query = "").size)
    }

    @Test
    fun `search finds a target by its model, its kernel release, its source or its flavour`() {
        // The four things someone arrives holding, none of which is the profile's own name.
        assertEquals(listOf(otherDevice.profileId), found("S9210"))
        assertEquals(listOf(otherKernel.profileId), found("6.6.102"))
        assertEquals(listOf(otherKernel.profileId), found("payloads-next"))
        assertEquals(listOf(otherKernel.profileId), found("next"))
    }

    @Test
    fun `search finds a target by the KernelSU it stages`() {
        // "Which of these stages the release I have a manager for" is a question someone can arrive
        // with, and the answer must not depend on whether the entry's name happens to spell it out -
        // some do ("Galaxy S25 Ultra | KernelSU-Next 3.4.0") and the ones this field was added for do not.
        assertEquals(listOf(otherKernel.profileId), found("3.4.0"))
    }

    @Test
    fun `an entry that declares no KernelSU version is not found by one`() {
        // The fallback the app applies for these is the flavour's own release, which is a decision this
        // app makes and not a fact about the entry - so searching for it must not return them.
        assertTrue(mine.kernelSuVersion == null)
        assertTrue(found("3.3.0").isEmpty())
    }

    @Test
    fun `search is part of a value, in either case`() {
        // Nobody types the whole of a kernel release, and nobody types a model's case deliberately.
        assertEquals(
            listOf(otherKernel.profileId, mine.profileId).sorted(),
            found("sm-s9360").sorted(),
        )
        assertEquals(listOf(otherKernel.profileId), found("6.6.10"))
    }

    @Test
    fun `a blank query is every target rather than none`() {
        // The sheet opens with an empty field, which must not read as a filter matching nothing.
        listOf("", "   ").forEach { query ->
            assertEquals(3, visibleTargets(catalog, device, fitsDeviceOnly = false, query = query).size)
            assertTrue(mine.matchesQuery(query))
        }
    }

    @Test
    fun `the toggle and the search narrow together rather than one replacing the other`() {
        // Searching another device's model while the toggle is on is a question about this phone, so
        // the answer is nothing - which is the state the sheet names, with the way out of it.
        assertTrue(visibleTargets(catalog, device, fitsDeviceOnly = true, query = "S9210").isEmpty())
        assertEquals(listOf(otherDevice.profileId), found("S9210"))
    }

    @Test
    fun `the search itself knows nothing about the device`() {
        // One of them filters on the device and the other on the text. A search that quietly inherited
        // the toggle could not find the entry that made the empty list worth explaining.
        assertTrue(otherDevice.matchesQuery("S9210"))
        // The same target the device rule rejects, which is the whole point: the search must not
        // inherit the answer to a question it was not asked.
        assertTrue(!otherDevice.matches(device))
    }

    private fun snapshot(model: String, kernelRelease: String) = DeviceSnapshot(
        manufacturer = "samsung",
        model = model,
        device = "unused",
        kernelRelease = kernelRelease,
        kernelVersionInfo = "#1 SMP PREEMPT",
        machine = "aarch64",
        buildId = "BP4A.251205.006.S938BCZG1",
        fingerprint = "samsung/example",
        androidRelease = "16",
        sdk = 36,
        abi = "arm64-v8a",
        pageSize = 4096,
    )
}
