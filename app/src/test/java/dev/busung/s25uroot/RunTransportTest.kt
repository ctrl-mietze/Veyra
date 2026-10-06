package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTransportTest {

    @Test
    fun `a shell-only payload with no Shizuku uses a stored pairing`() {
        assertEquals(
            RunTransport.LocalAdb,
            chooseRunTransport(
                shellRequired = true,
                shizukuRequested = false,
                shizukuUsable = false,
                localAdbPaired = true,
            ),
        )
    }

    @Test
    fun `a usable Shizuku wins over a pairing`() {
        // The pairing path turns wireless debugging on and off around itself, which is worth avoiding
        // when a session that already exists can carry the payload instead.
        assertEquals(
            RunTransport.Shizuku,
            chooseRunTransport(true, shizukuRequested = true, shizukuUsable = true, localAdbPaired = true),
        )
    }

    @Test
    fun `Shizuku that was asked for but is silent falls back to a pairing`() {
        // Asked for is not the same as available, and the point of asking was to get a shell.
        assertEquals(
            RunTransport.LocalAdb,
            chooseRunTransport(true, shizukuRequested = true, shizukuUsable = false, localAdbPaired = true),
        )
    }

    @Test
    fun `a pairing that was never made does not block a usable Shizuku`() {
        assertEquals(
            RunTransport.Shizuku,
            chooseRunTransport(true, shizukuRequested = true, shizukuUsable = true, localAdbPaired = false),
        )
    }

    @Test
    fun `a shell-only payload never falls back to the app's own domain`() {
        // Running it as the app would fail for a reason that looks like the payload's fault, so the
        // refusal is the honest answer - and the caller names both transports it needed.
        assertNull(
            chooseRunTransport(true, shizukuRequested = false, shizukuUsable = false, localAdbPaired = false),
        )
        assertNull(
            chooseRunTransport(true, shizukuRequested = true, shizukuUsable = false, localAdbPaired = false),
        )
    }

    @Test
    fun `a payload that does not need a shell keeps the transport the settings ask for`() {
        assertEquals(
            RunTransport.Shizuku,
            chooseRunTransport(false, shizukuRequested = true, shizukuUsable = true, localAdbPaired = true),
        )
        assertEquals(
            RunTransport.App,
            chooseRunTransport(false, shizukuRequested = false, shizukuUsable = false, localAdbPaired = true),
        )
        // A pairing is not used by choice: it is what carries a payload that would otherwise have
        // nothing, and a payload that does not need a shell is not that case.
        assertEquals(
            RunTransport.App,
            chooseRunTransport(false, shizukuRequested = true, shizukuUsable = false, localAdbPaired = true),
        )
    }

    @Test
    fun `a stability preference uses paired local ADB without making shell mandatory`() {
        assertEquals(
            RunTransport.LocalAdb,
            chooseRunTransport(
                shellRequired = false,
                shizukuRequested = false,
                shizukuUsable = false,
                localAdbPaired = true,
                shellPreferred = true,
            ),
        )
        // Preference is deliberately not a requirement: without a paired shell the app path remains
        // available instead of refusing the run.
        assertEquals(
            RunTransport.App,
            chooseRunTransport(
                shellRequired = false,
                shizukuRequested = false,
                shizukuUsable = false,
                localAdbPaired = false,
                shellPreferred = true,
            ),
        )
    }

    @Test
    fun `the refusal names Shizuku's state, because the two cases have different answers`() {
        // "Switched on but silent" sends the user to start it; "switched off" sends them to a setting.
        assertNotEquals(
            shellTransportRefusalStringId(shizukuRequested = true),
            shellTransportRefusalStringId(shizukuRequested = false),
        )
    }

    @Test
    fun `a manual run asks about Shizuku instead of failing when it is not running`() {
        assertTrue(
            shouldHoldForShizuku(
                unattended = false,
                requested = true,
                running = false,
                ignoringShizuku = false,
            ),
        )
        // Nothing to ask: it is up, nobody asked for it, or the person already answered.
        assertFalse(shouldHoldForShizuku(false, requested = true, running = true, ignoringShizuku = false))
        assertFalse(shouldHoldForShizuku(false, requested = false, running = false, ignoringShizuku = false))
        assertFalse(shouldHoldForShizuku(false, requested = true, running = false, ignoringShizuku = true))
    }

    @Test
    fun `a boot never stops to ask, because there is nobody there to answer`() {
        // Unattended runs either wait for Shizuku before starting or refuse - the gate's business, not
        // this question's. Holding one would leave a boot install parked behind a dialog nobody sees.
        assertFalse(shouldHoldForShizuku(true, requested = true, running = false, ignoringShizuku = false))
        assertFalse(shouldHoldForShizuku(true, requested = true, running = false, ignoringShizuku = true))
    }

    @Test
    fun `an exit code is read from the end of the payload's own output`() {
        assertEquals(0, localAdbExploitExitCode("exploit completed done=1 root=1\n$ADB_EXIT_MARKER" + "0\n"))
        assertEquals(3, localAdbExploitExitCode("no root\n$ADB_EXIT_MARKER" + "3\n"))
    }

    @Test
    fun `a run that reported no code is unknown, never success`() {
        // The failure this prevents: a stream that ended early reads as exit 0 and the run claims root.
        assertEquals(LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE, localAdbExploitExitCode(""))
        assertEquals(LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE, localAdbExploitExitCode("$ADB_EXIT_MARKER\n"))
        assertNotEquals(0, localAdbExploitExitCode("the shell died mid-run"))
    }

    @Test
    fun `the payload cannot decide its own exit code by printing the marker`() {
        assertEquals(1, localAdbExploitExitCode("$ADB_EXIT_MARKER" + "0\nreal output\n$ADB_EXIT_MARKER" + "1\n"))
    }
}
