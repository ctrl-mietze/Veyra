package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetProfileTest {
    private val profile = TargetProfile(
        profileId = "galaxy-s25-series-kernel-6.6.98",
        displayName = "Galaxy S25 series",
        models = setOf("SM-S931B", "SM-S938N"),
        kernelVersions = setOf("6.6.98"),
        exploit = RemoteArtifact("https://example.invalid/exploit", 1),
        kernelSu = RemoteArtifact("https://example.invalid/ksud", 1),
    )

    @Test
    fun matchesRegionalS25OnSameKernelVersion() {
        assertTrue(profile.matches(snapshot("SM-S931B", "6.6.98-android15-8-build-a")))
        assertTrue(profile.matches(snapshot("SM-S938N", "6.6.98-android15-8-build-b")))
    }

    @Test
    fun rejectsUnlistedModelOrKernelVersion() {
        assertFalse(profile.matches(snapshot("SM-S928B", "6.6.98-android15-8-build")))
        assertFalse(profile.matches(snapshot("SM-S938N", "6.6.102-android15-8-build")))
    }

    @Test
    fun prefersProfileWithExactKernelRelease() {
        val zcs = profile.copy(
            profileId = "pa2q-S9360ZCSCCZG1",
            models = setOf("SM-S9360"),
            kernelVersions = setOf("6.6.98"),
        )
        val zhs = profile.copy(
            profileId = "pa2q-S9360ZHSCCZG1",
            models = setOf("SM-S9360"),
            kernelVersions = setOf(
                "6.6.98",
                "6.6.98-android15-8-pd6ff1cd-abogkiS9360ZHSCCZG1-4k",
            ),
        )

        val selected = listOf(zcs, zhs)
            .resolveFor(snapshot("SM-S9360", "6.6.98-android15-8-pd6ff1cd-abogkiS9360ZHSCCZG1-4k"))

        assertEquals("pa2q-S9360ZHSCCZG1", selected?.profileId)
    }

    @Test
    fun fallsBackToThreePartMatch() {
        val selected = listOf(profile)
            .resolveFor(snapshot("SM-S931B", "6.6.98-android15-8-build-a"))

        assertEquals("galaxy-s25-series-kernel-6.6.98", selected?.profileId)
    }

    @Test
    fun returnsNullWhenNothingMatches() {
        assertNull(listOf(profile).resolveFor(snapshot("SM-S928B", "6.6.98-android15-8-build-a")))
    }

    @Test
    fun aProfileThatDeclaresOnlyTheFullReleaseStillMatches() {
        // Sources are not limited to the feed's three-part form, and the rest of the app already
        // reads a listed full release as a match. When only this rule disagreed, an entry a source
        // offered could never be selected, and the device was told nothing covered it.
        val exactOnly = profile.copy(
            profileId = "pa2q-S9360ZHSCCZG1",
            models = setOf("SM-S9360"),
            kernelVersions = setOf("6.6.98-android15-8-pd6ff1cd-abogkiS9360ZHSCCZG1-4k"),
        )
        val device = snapshot("SM-S9360", "6.6.98-android15-8-pd6ff1cd-abogkiS9360ZHSCCZG1-4k")

        assertEquals("pa2q-S9360ZHSCCZG1", listOf(exactOnly).resolveFor(device)?.profileId)
    }

    @Test
    fun kernelMatchSeparatesAnExactReleaseFromAThreePartSibling() {
        val zhs = profile.copy(
            profileId = "pa2q-S9360ZHSCCZG1",
            models = setOf("SM-S9360"),
            kernelVersions = setOf(
                "6.6.98",
                "6.6.98-android15-8-pd6ff1cd-abogkiS9360ZHSCCZG1-4k",
            ),
        )
        val zcs = profile.copy(
            profileId = "pa2q-S9360ZCSCCZG1",
            models = setOf("SM-S9360"),
            kernelVersions = setOf("6.6.98"),
        )
        val snapshot = snapshot("SM-S9360", "6.6.98-android15-8-pd6ff1cd-abogkiS9360ZHSCCZG1-4k")

        assertEquals(KernelMatch.Exact, zhs.kernelMatch(snapshot))
        assertEquals(KernelMatch.Version, zcs.kernelMatch(snapshot))
    }

    @Test
    fun kernelMatchIsNoneForAnUnlistedKernel() {
        assertEquals(
            KernelMatch.None,
            profile.kernelMatch(snapshot("SM-S931B", "6.6.102-android15-8-build")),
        )
    }

    @Test
    fun freshP0SessionOutlastsTheLongestAttemptEnvelopeProposedForIt() {
        // One community proposal allowed a single 840-second attempt, another 1200 s of page scan
        // plus 2200 s of attempt. The app must not be the thing that cuts such a run off, so the
        // ceiling for a marked profile covers the longest of those, and only marked profiles move.
        assertTrue(RunLimits.defaultCeilings(freshSession = true).totalMillis >= 3_400_000L)
        assertTrue(
            RunLimits.defaultCeilings(freshSession = true).totalMillis >
                RunLimits.defaultCeilings(freshSession = false).totalMillis,
        )
        assertEquals(900_000L, RunLimits.defaultCeilings(freshSession = false).totalMillis)
    }

    @Test
    fun freshP0SessionRunsOnceWithoutCacheOrShortTimeoutOverrides() {
        val freshProfile = profile.copy(requiresFreshP0Session = true)

        // The payload's own window travels in the environment as well, so a fresh session's map is the
        // attempt plus what this app decided the payload should wait - no offset, no timeout override.
        assertEquals(
            mapOf("EXPLOIT_ATTEMPTS" to "1", "P0_MIN_BOOT_UPTIME_SEC" to "120"),
            InstallViewModel.exploitEnvironment(
                freshProfile.requiresFreshP0Session,
                "0x1a0000",
                payloadQuietWindowSec = BootSettle.PAYLOAD_QUIET_WINDOW_MAX_SECONDS,
            ),
        )
    }

    private fun snapshot(
        model: String,
        kernelRelease: String,
    ) = DeviceSnapshot(
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
