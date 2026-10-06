package ctrl.mietze.veyraroot

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPLOIT_SHA = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
private const val KSUD_SHA = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"
private const val HELPER_SHA = "1122334455667788990011223344556677889900aabbccddeeff001122334455"
private const val OTHER_SHA = "ffeeddccbbaa9988776655443322110000112233445566778899aabbccddeeff"

private val TEST_DEVICE = DeviceSnapshot(
    manufacturer = "samsung",
    model = "SM-S938B",
    device = "pa3q",
    kernelRelease = "6.6.98-android15-8-pd6ff1cd-abogkiS938BXXSBCZF1-4k",
    kernelVersionInfo = "#1 SMP PREEMPT",
    machine = "aarch64",
    buildId = "BP4A.251205.006",
    fingerprint = "samsung/pa3q/pa3q:16/BUILD",
    androidRelease = "16",
    sdk = 36,
    abi = "arm64-v8a",
    pageSize = 4096L,
)

private fun cached(
    helperSha256: String = HELPER_SHA,
    profileId: String = "galaxy-s25-series-2026-06-07",
    models: List<String> = listOf("SM-S938B", "SM-S931B"),
    kernels: List<String> = listOf("6.6.98"),
) = CachedPayload(
    id = knownGoodId(EXPLOIT_SHA, KSUD_SHA, helperSha256),
    profileId = profileId,
    displayName = "Galaxy S25 series",
    models = models,
    kernelVersions = kernels,
    requiresFreshP0Session = false,
    routePolicy = ExploitRoutePolicy.LEGACY,
    exploit = RemoteArtifact(url = "https://example/exploit.so", size = 10, sha256 = EXPLOIT_SHA),
    kernelSu = RemoteArtifact(url = "https://example/ksud", size = 10, sha256 = KSUD_SHA),
    helperSha256 = helperSha256,
    helperSize = 4096L,
)

class OfflinePayloadStoreTest {

    // --- identity -----------------------------------------------------------------------------------

    @Test
    fun `the cache id is built from what was cached`() {
        val id = knownGoodId(EXPLOIT_SHA, KSUD_SHA, HELPER_SHA)

        assertEquals("v1-${EXPLOIT_SHA.take(16)}-${KSUD_SHA.take(16)}-${HELPER_SHA.take(16)}", id)
        // The same payload caches to the same id, so re-publishing replaces instead of accumulating.
        assertEquals(id, knownGoodId(EXPLOIT_SHA, KSUD_SHA, HELPER_SHA))
        assertFalse(id == knownGoodId(EXPLOIT_SHA, KSUD_SHA, OTHER_SHA))
    }

    // --- the descriptor -----------------------------------------------------------------------------

    @Test
    fun `a descriptor survives being written and read back`() {
        val original = cached()

        val parsed = CachedPayload.parse(original.toJson())

        assertEquals(original, parsed)
        assertEquals(original.routePolicy, parsed.routePolicy)
        assertEquals(original.exploit.sha256, parsed.exploit.sha256)
    }

    @Test
    fun `the flavour and the source are carried through the cache, not re-derived`() {
        // An offline run has no catalog to ask, so a cache that dropped either of these would leave the
        // run to read the app's own setting instead - and the setting can have been changed since,
        // which is how a payload built for one KernelSU ends up installing the other one's daemon.
        val original = cached().copy(
            flavor = KernelSuFlavor.KernelSuNext,
            sourceId = "rushiranpise-Root-My-Galaxy-Payloads",
            sourceLabel = "Root-My-Galaxy-Payloads",
            sourceCommit = "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c",
        )

        val parsed = CachedPayload.parse(original.toJson())
        val profile = parsed.profile()

        assertEquals(KernelSuFlavor.KernelSuNext, parsed.flavor)
        assertEquals(KernelSuFlavor.KernelSuNext, profile.flavor)
        // The plan shows this pair, which is the whole reason it is cached: a cached run can still say
        // where its payload came from and at which revision.
        assertEquals("Root-My-Galaxy-Payloads", profile.sourceLabel)
        assertEquals("9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c", profile.sourceCommit)
        assertEquals("rushiranpise-Root-My-Galaxy-Payloads", profile.sourceId)
    }

    @Test
    fun `the KernelSU version the cached daemon is built from survives the cache`() {
        // An offline run loads this exact daemon, so the manager that matches it is a fact about the
        // cache rather than about whatever the feed says today - and a dropped version would send the
        // offer back to the flavour's own release, which may be another line entirely.
        val parsed = CachedPayload.parse(
            cached().copy(flavor = KernelSuFlavor.KernelSuNext, kernelSuVersion = "3.4.0").toJson(),
        )

        assertEquals("3.4.0", parsed.kernelSuVersion)
        assertEquals("3.4.0", parsed.profile().kernelSuVersion)
    }

    @Test
    fun `a cache written before flavours and sources existed still reads as KernelSU with no source`() {
        // This is the migration case, and it has to be readable rather than refused: the payload is
        // still a payload, and the entry it rebuilds is the one an older build would have run.
        val legacy = cached().toJson().let { json ->
            JSONObject(json).apply {
                remove("flavor")
                remove("kernelSuVersion")
                remove("sourceId")
                remove("sourceLabel")
                remove("sourceCommit")
            }.toString()
        }

        val parsed = CachedPayload.parse(legacy)

        assertEquals(KernelSuFlavor.Default, parsed.flavor)
        assertNull(parsed.kernelSuVersion)
        assertEquals("", parsed.sourceLabel)
        assertEquals("", parsed.sourceCommit)
    }

    @Test
    fun `the profile a run would use is rebuilt from the descriptor`() {
        val profile = cached().profile()

        assertEquals("galaxy-s25-series-2026-06-07", profile.profileId)
        assertEquals(setOf("SM-S938B", "SM-S931B"), profile.models)
        assertEquals(setOf("6.6.98"), profile.kernelVersions)
        assertTrue(profile.matches(TEST_DEVICE))
    }

    // --- what the cache refuses ---------------------------------------------------------------------

    @Test
    fun `a cache from a build with a different helper is refused`() {
        val reason = cacheRejectionReason(
            cached = cached(helperSha256 = OTHER_SHA),
            helperSha256 = HELPER_SHA,
            helperSize = 4096L,
            snapshot = TEST_DEVICE,
            requestedProfileId = null,
        )

        assertNotNull(reason)
        assertTrue(reason!!.contains("root helper"))
    }

    @Test
    fun `a cache for another device or kernel version is refused`() {
        val otherModel = cacheRejectionReason(
            cached = cached(models = listOf("SM-S931B")),
            helperSha256 = HELPER_SHA,
            helperSize = 4096L,
            snapshot = TEST_DEVICE,
            requestedProfileId = null,
        )
        val otherKernel = cacheRejectionReason(
            cached = cached(kernels = listOf("6.6.97")),
            helperSha256 = HELPER_SHA,
            helperSize = 4096L,
            snapshot = TEST_DEVICE,
            requestedProfileId = null,
        )

        assertNotNull(otherModel)
        assertNotNull(otherKernel)
    }

    @Test
    fun `a cache that is not the selected target is refused`() {
        val reason = cacheRejectionReason(
            cached = cached(),
            helperSha256 = HELPER_SHA,
            helperSize = 4096L,
            snapshot = TEST_DEVICE,
            requestedProfileId = "something-else",
        )

        assertNotNull(reason)
    }

    @Test
    fun `a usable cache is not refused`() {
        assertNull(
            cacheRejectionReason(
                cached = cached(),
                helperSha256 = HELPER_SHA,
                helperSize = 4096L,
                snapshot = TEST_DEVICE,
                requestedProfileId = "galaxy-s25-series-2026-06-07",
            ),
        )
    }

    // --- the artifact check -------------------------------------------------------------------------

    @Test
    fun `a cached file is held to the digest it was verified with`() {
        val file = Files.createTempFile("exploit", ".so").toFile()
        try {
            file.writeBytes(ByteArray(10) { 7 })
            val sha = sha256Of(file)
            val sized = RemoteArtifact(url = "u", size = 10)

            assertTrue(fileMatchesArtifact(file, sized.copy(sha256 = sha)))
            assertFalse(fileMatchesArtifact(file, sized.copy(sha256 = OTHER_SHA)))
            // A file of the wrong length is refused even when no digest is declared.
            assertFalse(fileMatchesArtifact(file, RemoteArtifact(url = "u", size = 11)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a digest proves the length, so a wrong declared size is not a second failure`() {
        val file = Files.createTempFile("ksud", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(24) { 3 })
            val artifact = RemoteArtifact(
                url = "u",
                size = 999,
                verifySize = true,
                sha256 = sha256Of(file),
            )

            // This is the rule the download path applies too: matching content is what matters, and a
            // feed that mis-states a length should not fail a file whose bytes are right.
            assertTrue(fileMatchesArtifact(file, artifact))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a missing file is never a match`() {
        val missing = File(File(System.getProperty("java.io.tmpdir"), "does-not-exist-${System.nanoTime()}"), "x")

        assertFalse(fileMatchesArtifact(missing, RemoteArtifact(url = "u", size = 1, verifySize = false)))
    }

    @Test
    fun `a digest is the lowercase hex of the content`() {
        val file = Files.createTempFile("digest", ".txt").toFile()
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                sha256Of(file),
            )
        } finally {
            file.delete()
        }
    }
}
