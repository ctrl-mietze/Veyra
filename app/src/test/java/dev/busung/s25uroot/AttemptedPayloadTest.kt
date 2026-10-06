package ctrl.mietze.veyraroot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val ATTEMPT_EXPLOIT_SHA =
    "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
private const val ATTEMPT_KSUD_SHA =
    "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"
private const val ATTEMPT_HELPER_SHA =
    "1122334455667788990011223344556677889900aabbccddeeff001122334455"

private val ATTEMPT_DEVICE = DeviceSnapshot(
    manufacturer = "samsung",
    model = "SM-S938U1",
    device = "pa3q",
    kernelRelease = "6.6.98-android15-8-pd6ff1cd-abogkiS938USQSCCZF9-4k",
    kernelVersionInfo = "#1 SMP PREEMPT",
    machine = "aarch64",
    buildId = "BP4A.251205.006",
    fingerprint = "samsung/pa3q/pa3q:16/BUILD",
    androidRelease = "16",
    sdk = 36,
    abi = "arm64-v8a",
    pageSize = 4096L,
)

private fun attempt(
    kernelVersions: List<String> = listOf("6.6.98"),
) = CachedPayload(
    id = knownGoodId(ATTEMPT_EXPLOIT_SHA, ATTEMPT_KSUD_SHA, ATTEMPT_HELPER_SHA),
    profileId = "pa3q-kernelsu-next-6.6.98",
    displayName = "Galaxy S25 kernel 6.6.98 (KernelSU-Next)",
    models = listOf("SM-S938U1"),
    kernelVersions = kernelVersions,
    requiresFreshP0Session = false,
    routePolicy = ExploitRoutePolicy.LEGACY,
    exploit = RemoteArtifact(
        url = "https://example.invalid/exploit.so",
        size = 64,
        verifySize = true,
        sha256 = ATTEMPT_EXPLOIT_SHA,
    ),
    kernelSu = RemoteArtifact(
        url = "https://example.invalid/ksud",
        size = 48,
        verifySize = true,
        sha256 = ATTEMPT_KSUD_SHA,
    ),
    helperSha256 = ATTEMPT_HELPER_SHA,
    helperSize = 2048L,
)

private fun tempFileOf(size: Int): File = Files.createTempFile("attempt", ".bin").toFile().apply {
    writeBytes(ByteArray(size) { it.toByte() })
    deleteOnExit()
}

/**
 * Whether a retry may run the payload that failed.
 *
 * The case that matters is the one a person meets while testing payloads: the files were downloaded by
 * the attempt itself, into a directory this app shares with every other run of that profile, so they can
 * be gone by the time a retry runs after a reboot. The alternative to a refusal here is a run that
 * installs something the user did not ask for, which is why each reason is asserted rather than just
 * the yes/no.
 */
class AttemptedPayloadTest {

    @Test
    fun `an attempt whose files are still there can be retried`() {
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        // No digests on the artifacts either way would be a weaker check, so both files carry real ones.
        val descriptor = attempt().copy(
            exploit = attempt().exploit.copy(sha256 = sha256Of(exploit)),
            kernelSu = attempt().kernelSu.copy(sha256 = sha256Of(kernelSu)),
        )
        assertNull(
            attemptedPayloadRejection(
                attempted = descriptor,
                helperSha256 = ATTEMPT_HELPER_SHA,
                helperSize = 2048L,
                snapshot = ATTEMPT_DEVICE,
                exploitFile = exploit,
                kernelSuFile = kernelSu,
            ),
        )
    }

    @Test
    fun `an attempt whose exploit is gone refuses instead of falling back`() {
        val kernelSu = tempFileOf(48)
        val reason = attemptedPayloadRejection(
            attempted = attempt(),
            helperSha256 = ATTEMPT_HELPER_SHA,
            helperSize = 2048L,
            snapshot = ATTEMPT_DEVICE,
            exploitFile = null,
            kernelSuFile = kernelSu,
        )
        assertNotNull(reason)
        assert(reason!!.contains("no longer on the device"))
    }

    @Test
    fun `a file that is not the bytes the attempt verified refuses`() {
        // The same path, different content: a later run of the same profile rewrites this directory, so
        // a file that is present is not the same claim as a file that is the one that was verified.
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val reason = attemptedPayloadRejection(
            attempted = attempt(),
            helperSha256 = ATTEMPT_HELPER_SHA,
            helperSize = 2048L,
            snapshot = ATTEMPT_DEVICE,
            exploitFile = exploit,
            kernelSuFile = kernelSu,
        )
        assertNotNull(reason)
        assert(reason!!.contains("exploit this retry was for"))
    }

    @Test
    fun `an attempt for another kernel version is refused`() {
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val reason = attemptedPayloadRejection(
            attempted = attempt(kernelVersions = listOf("6.6.99")),
            helperSha256 = ATTEMPT_HELPER_SHA,
            helperSize = 2048L,
            snapshot = ATTEMPT_DEVICE,
            exploitFile = exploit,
            kernelSuFile = kernelSu,
        )
        assertNotNull(reason)
        assert(reason!!.contains("kernel version"))
    }

    @Test
    fun `an attempt verified against another root helper is refused`() {
        // The helper is part of the pairing, not context: a build that ships a different one must not
        // be able to run a payload that was verified against this one.
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val reason = attemptedPayloadRejection(
            attempted = attempt(),
            helperSha256 = ATTEMPT_KSUD_SHA,
            helperSize = 2048L,
            snapshot = ATTEMPT_DEVICE,
            exploitFile = exploit,
            kernelSuFile = kernelSu,
        )
        assertNotNull(reason)
        assert(reason!!.contains("different root helper"))
    }
}
