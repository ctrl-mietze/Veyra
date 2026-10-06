package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSuReadinessTest {

    private fun report(
        boot: String = "boot-1",
        uid: String = "0",
        ns: String = "same",
        want: Int = 2,
        got: Int = 2,
    ) = "some shell noise\n${KernelSuReadiness.REPORT_PREFIX} boot=$boot uid=$uid ns=$ns want=$want got=$got\n"

    @Test
    fun `the probe command shares one definition of what should be mounted`() {
        // Both the app-side probe and the script that restarts Zygote use this fragment, so a change
        // to "what counts as a module that should be mounted" cannot reach one and miss the other.
        val variables = KernelSuReadiness.variables()
        assertTrue(KernelSuReadiness.command().contains(variables))
        assertTrue(
            RootRecovery.restartZygoteScript("boot-1", "/tmp/accepted", "/tmp/report")
                .contains(variables),
        )
    }

    @Test
    fun `a report is read back into what it measured`() {
        val state = KernelSuReadiness.parse(report(ns = "entered", want = 3, got = 3))!!
        assertEquals("boot-1", state.bootToken)
        assertTrue(state.uidZero)
        assertEquals(MountNamespace.Entered, state.namespace)
        assertEquals(3, state.expected)
        assertEquals(3, state.mounted)
        assertTrue(state.mountsComplete)
    }

    @Test
    fun `a probe that never printed is not a device missing its modules`() {
        assertNull(KernelSuReadiness.parse("no such command\n"))
        assertNull(KernelSuReadiness.parse("${KernelSuReadiness.REPORT_PREFIX} boot=b uid=0 ns=sideways want=1 got=1"))
        assertNull(KernelSuReadiness.parse("${KernelSuReadiness.REPORT_PREFIX} boot=b uid=0 ns=same want=many got=1"))
    }

    @Test
    fun `a device with nothing to mount is complete`() {
        val state = KernelSuReadiness.parse(report(want = 0, got = 0))!!
        assertTrue(state.mountsComplete)
        assertFalse(state.definitelyMissingMounts)
    }

    @Test
    fun `a module that is enabled but unmounted is found missing`() {
        val state = KernelSuReadiness.parse(report(want = 3, got = 1))!!
        assertFalse(state.mountsComplete)
        assertTrue(state.definitelyMissingMounts)
        assertEquals(
            "only 1 of 3 enabled modules are mounted, so restarting Zygote now would not load them",
            KernelSuReadiness.refusal(state, "boot-1"),
        )
    }

    @Test
    fun `an unreadable namespace is reported, not treated as missing mounts`() {
        // Refusing when a check could not be made would make the action unusable on every device
        // without nsenter, which is a worse outcome than a restart that turns out to be unnecessary.
        val state = KernelSuReadiness.parse(report(ns = "unavailable", want = 2, got = -1))!!
        assertFalse(state.mountsComplete)
        assertFalse(state.definitelyMissingMounts)
        assertNull(KernelSuReadiness.refusal(state, "boot-1"))
    }

    @Test
    fun `the boot changing under the probe refuses the restart`() {
        val state = KernelSuReadiness.parse(report(boot = "boot-2"))!!
        assertEquals(
            "the device rebooted while the module mounts were being read",
            KernelSuReadiness.refusal(state, "boot-1"),
        )
    }

    @Test
    fun `mounts read without root refuse the restart`() {
        val state = KernelSuReadiness.parse(report(uid = "2000", want = 0, got = 0))!!
        assertEquals(
            "the module mounts could not be read as root",
            KernelSuReadiness.refusal(state, "boot-1"),
        )
    }

    @Test
    fun `a missing probe does not refuse anything`() {
        assertNull(KernelSuReadiness.refusal(null, "boot-1"))
    }
}
