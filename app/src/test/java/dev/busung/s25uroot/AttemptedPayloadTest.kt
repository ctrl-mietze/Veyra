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

private fun tempFileOf(size: Int, salt: Int = 0): File =
    Files.createTempFile("attempt", ".bin").toFile().apply {
        writeBytes(ByteArray(size) { (it + salt).toByte() })
        deleteOnExit()
    }

private fun withHelper(
    payload: CachedPayload,
    helper: File,
): CachedPayload = payload.copy(
    helperSha256 = sha256Of(helper),
    helperSize = helper.length(),
)

/**
 * Whether a retry may run the payload that failed.
 *
 * The retry contract now includes all three files the attempted run actually verified: exploit,
 * KernelSU payload and root helper. Tests therefore provide the exact helper file too instead of
 * checking only its remembered digest.
 */
class AttemptedPayloadTest {

    @Test
    fun `an attempt whose files are still there can be retried`() {
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val helper = tempFileOf(2048)
        val descriptor = withHelper(
            attempt().copy(
                exploit = attempt().exploit.copy(sha256 = sha256Of(exploit)),
                kernelSu = attempt().kernelSu.copy(sha256 = sha256Of(kernelSu)),
            ),
            helper,
        )

        assertNull(
            attemptedPayloadRejection(
                attempted = descriptor,
                helperSha256 = sha256Of(helper),
                helperSize = helper.length(),
                snapshot = ATTEMPT_DEVICE,
                exploitFile = exploit,
                kernelSuFile = kernelSu,
                rootHelperFile = helper,
            ),
        )
    }

    @Test
    fun `an attempt whose exploit is gone refuses instead of falling back`() {
        val kernelSu = tempFileOf(48)
        val helper = tempFileOf(2048)
        val descriptor = withHelper(attempt(), helper)

        val reason = attemptedPayloadRejection(
            attempted = descriptor,
            helperSha256 = sha256Of(helper),
            helperSize = helper.length(),
            snapshot = ATTEMPT_DEVICE,
            exploitFile = null,
            kernelSuFile = kernelSu,
            rootHelperFile = helper,
        )
        assertNotNull(reason)
        assert(reason!!.contains("no longer on the device"))
    }

    @Test
    fun `a file that is not the bytes the attempt verified refuses`() {
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val helper = tempFileOf(2048)
        val descriptor = withHelper(attempt(), helper)

        val reason = attemptedPayloadRejection(
            attempted = descriptor,
            helperSha256 = sha256Of(helper),
            helperSize = helper.length(),
            snapshot = ATTEMPT_DEVICE,
            exploitFile = exploit,
            kernelSuFile = kernelSu,
            rootHelperFile = helper,
        )
        assertNotNull(reason)
        assert(reason!!.contains("exploit this retry was for"))
    }

    @Test
    fun `an attempt for another kernel version is refused`() {
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val helper = tempFileOf(2048)
        val descriptor = withHelper(
            attempt(kernelVersions = listOf("6.6.99")),
            helper,
        )

        val reason = attemptedPayloadRejection(
            attempted = descriptor,
            helperSha256 = sha256Of(helper),
            helperSize = helper.length(),
            snapshot = ATTEMPT_DEVICE,
            exploitFile = exploit,
            kernelSuFile = kernelSu,
            rootHelperFile = helper,
        )
        assertNotNull(reason)
        assert(reason!!.contains("kernel version"))
    }

    @Test
    fun `an attempt verified against another root helper is refused`() {
        val exploit = tempFileOf(64)
        val kernelSu = tempFileOf(48)
        val expectedHelper = tempFileOf(2048, salt = 0)
        val actualHelper = tempFileOf(2048, salt = 1)
        val descriptor = withHelper(attempt(), expectedHelper)

        val reason = attemptedPayloadRejection(
            attempted = descriptor,
            helperSha256 = sha256Of(actualHelper),
            helperSize = actualHelper.length(),
            snapshot = ATTEMPT_DEVICE,
            exploitFile = exploit,
            kernelSuFile = kernelSu,
            rootHelperFile = actualHelper,
        )
        assertNotNull(reason)
        assert(reason!!.contains("different root helper"))
    }
}
