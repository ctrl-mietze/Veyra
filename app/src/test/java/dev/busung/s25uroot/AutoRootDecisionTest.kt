package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val THIS_BOOT = "0f2a4c6e-1b2d-4f6a-8c0e-2d4f6a8c0e2d"
private const val LAST_BOOT = "9e8d7c6b-5a49-4837-2615-0403f2e1d0c9"

class AutoRootDecisionTest {

    private fun decide(
        enabled: Boolean = true,
        retryArmed: Boolean = false,
        kernelSuLoadEnabled: Boolean = true,
        kernelSuActive: Boolean = false,
        runInFlight: Boolean = false,
        hasVerifiedInstall: Boolean = true,
        verifiedBootToken: String? = LAST_BOOT,
        attemptedBootToken: String? = null,
        bootToken: String = THIS_BOOT,
    ) = autoRootDecision(
        enabled = enabled,
        kernelSuLoadEnabled = kernelSuLoadEnabled,
        kernelSuActive = kernelSuActive,
        runInFlight = runInFlight,
        hasVerifiedInstall = hasVerifiedInstall,
        verifiedBootToken = verifiedBootToken,
        attemptedBootToken = attemptedBootToken,
        bootToken = bootToken,
        retryArmed = retryArmed,
    )

    @Test
    fun `a fresh boot with a verified install runs`() {
        assertEquals(AutoRootDecision.Run, decide())
    }

    @Test
    fun `the setting is what makes it automatic`() {
        assertEquals(AutoRootDecision.SkipDisabled, decide(enabled = false))
    }

    @Test
    fun `a boot that already has root is left alone`() {
        assertEquals(AutoRootDecision.SkipAlreadyRooted, decide(kernelSuActive = true))
    }

    @Test
    fun `an install verified in this boot is not repeated`() {
        // This is the userspace-restart case: BOOT_COMPLETED arrives again, the kernel boot id does
        // not change, and the install that already succeeded in this boot is still the answer.
        assertEquals(
            AutoRootDecision.SkipAlreadyVerified,
            decide(verifiedBootToken = THIS_BOOT),
        )
    }

    @Test
    fun `a boot whose attempt is spent does not get a second one`() {
        assertEquals(AutoRootDecision.SkipAttempted, decide(attemptedBootToken = THIS_BOOT))
    }

    @Test
    fun `a boot that finds a run in flight stands down`() {
        // The user is installing by hand while the gate wakes up: the run is the attempt this boot is
        // getting, and starting a second exploit beside it is two payloads racing one kernel.
        assertEquals(AutoRootDecision.SkipRunInFlight, decide(runInFlight = true))
    }

    @Test
    fun `a run in flight is not the question about the cache`() {
        // Before this, a device with no verified install was told to run one online installation first -
        // while the user was watching one, which reads as the app not looking at the phone.
        assertEquals(
            AutoRootDecision.SkipRunInFlight,
            decide(runInFlight = true, hasVerifiedInstall = false),
        )
    }

    @Test
    fun `a run in flight does not outrank a boot that is already rooted`() {
        // KernelSU answering is the better answer, and it is also what marks this boot verified: a run
        // that has just loaded the module must not leave the gate waiting to be told about it.
        assertEquals(
            AutoRootDecision.SkipAlreadyRooted,
            decide(runInFlight = true, kernelSuActive = true),
        )
        assertEquals(
            AutoRootDecision.SkipAlreadyVerified,
            decide(runInFlight = true, verifiedBootToken = THIS_BOOT),
        )
    }

    @Test
    fun `without a verified install the user is asked for one`() {
        assertEquals(
            AutoRootDecision.NeedsPriorInstall,
            decide(hasVerifiedInstall = false),
        )
    }

    @Test
    fun `a boot with no load to make is refused for that reason`() {
        // Root on boot is on and there is a verified install, so every other rule would say run. What
        // stops it is that runs are told not to load KernelSU, which is a different answer from the
        // setting being off - and has to stay different, because one of them the user chose.
        assertEquals(
            AutoRootDecision.SkipKernelSuLoadingOff,
            decide(kernelSuLoadEnabled = false),
        )
    }

    @Test
    fun `the load decision outranks what the device looks like`() {
        // Nothing is loaded on a boot that is not allowed to load anything, so there is nothing for a
        // reading about KernelSU to change - including a reading that says it is already active.
        assertEquals(
            AutoRootDecision.SkipKernelSuLoadingOff,
            decide(kernelSuLoadEnabled = false, kernelSuActive = true),
        )
    }

    @Test
    fun `turning root on boot off still wins over the load decision`() {
        // Both are configuration, and the automation being off is the outer answer: someone reading
        // the log should not be told about KernelSU loading when the feature itself is switched off.
        assertEquals(
            AutoRootDecision.SkipDisabled,
            decide(enabled = false, kernelSuLoadEnabled = false),
        )
    }

    @Test
    fun `an armed retry gets this boot's install with root on boot off`() {
        // The retry is a request the user made by hand from a failed run's dialog, and the phone was
        // restarted to honour it. Root on boot being off is not a refusal of it - it is a setting about
        // every other boot.
        assertEquals(
            AutoRootDecision.Run,
            decide(enabled = false, retryArmed = true),
        )
    }

    @Test
    fun `with root on boot on, a retry is still a retry`() {
        // Both asked for this boot, and one of them owns the attempt's payload: the retry runs the
        // attempt it was armed for rather than the cached payload, so the boot is a retry boot and is
        // reported as one. It used to be reported as a root-on-boot boot, because the flag that decided
        // the wording was guarded by `!bootRootMode`.
        assertEquals(
            AutoRootDecision.Run,
            decide(enabled = true, retryArmed = true),
        )
        val service = source("src/main/java/dev/busung/s25uroot/AutoRootService.kt")
        assertFalse(
            "the notification's wording is decided by a third flag again, so a boot with both turned on " +
                "is called root on boot while it runs the payload that failed",
            service.contains("retryTriggeredThisBoot"),
        )
        assertTrue(
            "the run no longer prefers the attempted payload when the boot is a retry",
            service.contains("preferAttemptedPayload = retryArmedThisBoot"),
        )
        assertTrue(
            "the notification no longer names a retry boot as one",
            service.contains("if (retryArmedThisBoot) R.string.autoroot_retry_title"),
        )
    }

    @Test
    fun `the notification's way out skips the run and not the setting`() {
        val service = source("src/main/java/dev/busung/s25uroot/AutoRootService.kt")
        val receiver = source("src/main/java/dev/busung/s25uroot/AutoRootBootReceiver.kt")

        assertTrue(
            "one action behind both labels, or the two boots can drift apart again",
            service.contains("setAction(AutoRootActionReceiver.ACTION_SKIP_INSTALL)"),
        )
        assertFalse(
            "the way out of one boot's run turns off the setting that governs every boot after it",
            receiver.contains("AppPreferences.setBootRootMode"),
        )
        assertTrue(
            "the skip no longer takes back the request this boot is honouring, so a skipped retry comes " +
                "back at the next restart",
            receiver.contains("skipTakesBackRetry(AppPreferences.retryArmedInBoot(context), bootToken)") &&
                receiver.contains("AppPreferences.setRetryAfterReboot(context, null)"),
        )
    }

    @Test
    fun `a skip takes back the retry this boot ran, never one armed while it ran`() {
        // The pending case: armed in a boot that is over, so this boot is honouring it and skipping means
        // giving it up - otherwise it survives and the next restart runs it again.
        assertTrue(
            "a pending retry survives the skip",
            skipTakesBackRetry(armedForBoot = LAST_BOOT, bootToken = THIS_BOOT),
        )
        // The armed-in-this-boot case: the user asked for a retry of the boot *after* this one, which is
        // how the flag waits. Clearing that would take back something just requested, under a button that
        // says it is skipping the run in front of them.
        assertFalse(
            "a retry armed for the next boot is taken back by a skip of this one",
            skipTakesBackRetry(armedForBoot = THIS_BOOT, bootToken = THIS_BOOT),
        )
        assertFalse("nothing armed is nothing to take back", skipTakesBackRetry(null, THIS_BOOT))
        assertFalse(
            "an unreadable boot id cannot tell whose request it is, so it takes nothing back",
            skipTakesBackRetry(LAST_BOOT, null),
        )
    }

    @Test
    fun `root already active outranks the setting and the receipt`() {
        // The state of the device is what decides, not the bookkeeping: if KernelSU is answering,
        // there is nothing to do whatever the settings or the stored tokens say.
        assertEquals(
            AutoRootDecision.SkipAlreadyRooted,
            decide(kernelSuActive = true, hasVerifiedInstall = false, verifiedBootToken = null),
        )
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
