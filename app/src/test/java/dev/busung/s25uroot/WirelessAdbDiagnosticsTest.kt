package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessAdbDiagnosticsTest {

    @Test
    fun `a refused certificate is reported as unpaired, not as a connection problem`() {
        // The device saying it does not know this app needs the opposite response from a network
        // fault: one is "pair again", the other is "turn wireless debugging on".
        assertEquals(
            WirelessAdbAuthState.PairingRejected,
            wirelessAdbFailureState(pairingRejected = true, portUnavailable = true),
        )
    }

    @Test
    fun `a missing port is its own state`() {
        assertEquals(
            WirelessAdbAuthState.PortUnavailable,
            wirelessAdbFailureState(pairingRejected = false, portUnavailable = true),
        )
    }

    @Test
    fun `anything else is a connection failure`() {
        assertEquals(
            WirelessAdbAuthState.ConnectionFailed,
            wirelessAdbFailureState(pairingRejected = false, portUnavailable = false),
        )
    }

    @Test
    fun `wireless debugging already on is usable with no permission and no root`() {
        // The case a user is in the moment they open the pairing dialog: the setting is on, the
        // permission is missing, and there is no root yet. The old rule refused to test here, which
        // reported a working transport as unusable.
        assertTrue(
            wirelessAdbUsable(wirelessDebuggingEnabled = true, permissionGranted = false, rootAvailable = false),
        )
    }

    @Test
    fun `with the setting off, either route to changing it is enough`() {
        assertTrue(
            wirelessAdbUsable(wirelessDebuggingEnabled = false, permissionGranted = true, rootAvailable = false),
        )
        assertTrue(
            wirelessAdbUsable(wirelessDebuggingEnabled = false, permissionGranted = false, rootAvailable = true),
        )
    }

    @Test
    fun `off, unpermitted and unrooted is the one case that cannot proceed`() {
        assertFalse(
            wirelessAdbUsable(wirelessDebuggingEnabled = false, permissionGranted = false, rootAvailable = false),
        )
    }

    @Test
    fun `the setting is preferred where the permission exists, and root is the fallback`() {
        assertEquals(WirelessAdbEnableRoute.Setting, wirelessAdbEnableRoute(true, rootAvailable = true))
        assertEquals(WirelessAdbEnableRoute.Root, wirelessAdbEnableRoute(false, rootAvailable = true))
        assertEquals(
            WirelessAdbEnableRoute.Unavailable,
            wirelessAdbEnableRoute(false, rootAvailable = false),
        )
    }

    @Test
    fun `the grant command names this app's own package`() {
        // It is shown to the user to paste, so it has to be the package they are looking at.
        assertTrue(AdbPairing.GRANT_COMMAND.contains(BuildConfig.APPLICATION_ID))
        assertTrue(AdbPairing.GRANT_COMMAND.contains("WRITE_SECURE_SETTINGS"))
    }

    @Test
    fun `every state has a distinct meaning for the card`() {
        // The card's whole job is to say which of these it is, so a state that nothing can produce -
        // or one that means the same as another - would make it say the wrong thing.
        val states = WirelessAdbAuthState.entries
        assertEquals(states.size, states.toSet().size)
        assertEquals(7, states.size)
    }
}
