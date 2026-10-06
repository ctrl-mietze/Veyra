package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate's rule for Shizuku, which is the one place a boot can be held back by a setting.
 *
 * The cases worth writing down are the ones that cannot be tried by hand: a phone whose Shizuku is
 * switched off in settings but present on the device, one that asked for Shizuku with nothing able to
 * start it - the case the wait would spend two minutes discovering - and the one where the payload does
 * not need the transport being waited for at all.
 */
class ShizukuWaitTest {

    @Test
    fun `a run that did not ask for Shizuku waits for nothing`() {
        // Even with Shizuku up and usable: asking is what makes it the run's transport.
        assertEquals(
            ShizukuWait.NotRequested,
            shizukuWait(requested = false, usable = true, startable = true),
        )
    }

    @Test
    fun `a usable Shizuku is taken without waiting`() {
        assertEquals(
            ShizukuWait.Ready,
            shizukuWait(requested = true, usable = true, startable = true),
        )
    }

    @Test
    fun `asking for Shizuku on a device that can start it means hold`() {
        assertEquals(
            ShizukuWait.Await,
            shizukuWait(requested = true, usable = false, startable = true),
        )
    }

    @Test
    fun `asking for Shizuku with no way to start it is refused, not waited for`() {
        // The wait would end by saying this same thing, two minutes later, in front of the one attempt
        // this boot gets.
        assertEquals(
            ShizukuWait.Unstartable,
            shizukuWait(requested = true, usable = false, startable = false),
        )
    }

    @Test
    fun `a usable Shizuku outranks the question of starting one`() {
        // The two readings are not ordered by what the device can do, and a device that cannot start
        // Shizuku is still a device that can use the one already running.
        assertEquals(
            ShizukuWait.Ready,
            shizukuWait(requested = true, usable = true, startable = false),
        )
    }

    // --- what a boot does with that answer, given the payload it is about to run ---------------------

    @Test
    fun `a boot runs a payload that does not need a shell instead of waiting for one`() {
        // The case this rule exists for: Use Shizuku is on, Shizuku is not up, something here could
        // start it - and the payload can carry itself, so the wait is two minutes spent buying a
        // transport the run would not have used anyway.
        assertEquals(
            BootShizukuPlan.WithoutShell,
            bootShizukuPlan(
                requested = true,
                usable = false,
                startable = true,
                shellRequired = false,
            ),
        )
    }

    @Test
    fun `a boot runs a payload that does not need a shell with nothing here able to start one`() {
        // A boot with no Wi-Fi and no root: nothing can bring Shizuku up, and refusing over it would be
        // refusing to install a payload that never needed Shizuku in the first place.
        assertEquals(
            BootShizukuPlan.WithoutShell,
            bootShizukuPlan(
                requested = true,
                usable = false,
                startable = false,
                shellRequired = false,
            ),
        )
    }

    @Test
    fun `waiting and refusing are both answers about getting a shell`() {
        // So neither can be reached by a payload that does not need one, whatever the device looks like.
        listOf(true, false).forEach { startable ->
            listOf(true, false).forEach { usable ->
                val plan = bootShizukuPlan(
                    requested = true,
                    usable = usable,
                    startable = startable,
                    shellRequired = false,
                )
                assertTrue(
                    "usable=$usable startable=$startable gave $plan",
                    plan == BootShizukuPlan.AsAsked || plan == BootShizukuPlan.WithoutShell,
                )
            }
        }
    }

    @Test
    fun `a payload that needs a shell keeps the wait and the refusal`() {
        // The other half of the rule, and the half that must not change: for a payload the app's own
        // process cannot carry, waiting is the only way to get it a transport, and saying so is the only
        // honest answer when there is none.
        assertEquals(
            BootShizukuPlan.Wait,
            bootShizukuPlan(true, usable = false, startable = true, shellRequired = true),
        )
        assertEquals(
            BootShizukuPlan.Unstartable,
            bootShizukuPlan(true, usable = false, startable = false, shellRequired = true),
        )
    }

    @Test
    fun `a usable Shizuku is used whether or not the payload needs one`() {
        // The setting is what the user asked for, and the shell reading only decides what to do when it
        // cannot be honoured - not whether to honour it.
        listOf(true, false).forEach { shellRequired ->
            assertEquals(
                BootShizukuPlan.AsAsked,
                bootShizukuPlan(true, usable = true, startable = false, shellRequired = shellRequired),
            )
        }
    }

    @Test
    fun `a boot that did not ask for Shizuku never goes a different way than asked`() {
        listOf(true, false).forEach { shellRequired ->
            listOf(true, false).forEach { usable ->
                assertEquals(
                    BootShizukuPlan.AsAsked,
                    bootShizukuPlan(
                        requested = false,
                        usable = usable,
                        startable = true,
                        shellRequired = shellRequired,
                    ),
                )
            }
        }
    }

    // --- which of the device's two payloads answers that question ------------------------------------

    @Test
    fun `the payload the run will resolve is the one whose policy counts`() {
        // A retry runs the attempt, a root-on-boot run runs the cache, and the two can disagree: reading
        // the wrong one would hold a boot back over a payload that is not the one it was about to run.
        val shellOnly = payload(shellRequired = true)
        val selfCarrying = payload(shellRequired = false)

        assertTrue(bootPayloadNeedsShell(true, attempted = shellOnly, cached = selfCarrying))
        assertFalse(bootPayloadNeedsShell(true, attempted = selfCarrying, cached = shellOnly))
        assertTrue(bootPayloadNeedsShell(false, attempted = selfCarrying, cached = shellOnly))
        assertFalse(bootPayloadNeedsShell(false, attempted = shellOnly, cached = selfCarrying))
    }

    @Test
    fun `an attempt record is not read for a boot that is not repeating one`() {
        // Only one of the two can be the payload being run, so a shell-only attempt sitting on the device
        // must not make a root-on-boot run wait for Shizuku it does not need - and the other way round.
        assertFalse(
            bootPayloadNeedsShell(
                preferAttempted = false,
                attempted = payload(shellRequired = true),
                cached = payload(shellRequired = false),
            ),
        )
        assertFalse(
            bootPayloadNeedsShell(
                preferAttempted = true,
                attempted = payload(shellRequired = false),
                cached = payload(shellRequired = true),
            ),
        )
    }

    @Test
    fun `a device that cannot say what its payload is keeps the wait`() {
        // The conservative default, and what keeps this change from reaching a boot nobody can describe:
        // no cache and no attempt means the old behaviour, which is to wait and then say what happened.
        assertTrue(bootPayloadNeedsShell(preferAttempted = true, attempted = null, cached = null))
        assertTrue(bootPayloadNeedsShell(preferAttempted = false, attempted = null, cached = null))
    }

    @Test
    fun `a payload that leaves the transport to the settings does not need a shell`() {
        // Which is every target that does not declare it, so this is the common case and not an edge one:
        // a policy that says nothing means "whatever transport the settings produce", and the run's own
        // rule already reads it that way - a boot that waited, and then refused, was the one place that
        // did not.
        assertFalse(ExploitRoutePolicy.LEGACY.prefersShellTransport)
        assertFalse(
            bootPayloadNeedsShell(
                preferAttempted = false,
                attempted = null,
                cached = payload(shellRequired = ExploitRoutePolicy.LEGACY.prefersShellTransport),
            ),
        )
    }
}

/** A cached descriptor that differs from its siblings only in the policy this file asks about. */
private fun payload(shellRequired: Boolean) = CachedPayload(
    id = knownGoodId("a".repeat(64), "b".repeat(64), "c".repeat(64)),
    profileId = "pa3q-kernelsu-next-6.6.98",
    displayName = "Galaxy S25 kernel 6.6.98 (KernelSU-Next)",
    models = listOf("SM-S938U1"),
    kernelVersions = listOf("6.6.98"),
    requiresFreshP0Session = false,
    routePolicy = ExploitRoutePolicy(prefersShellTransport = shellRequired),
    exploit = RemoteArtifact(url = "https://example.invalid/exploit.so", size = 64, verifySize = true),
    kernelSu = RemoteArtifact(url = "https://example.invalid/ksud", size = 48, verifySize = true),
    helperSha256 = "d".repeat(64),
    helperSize = 2048L,
)
