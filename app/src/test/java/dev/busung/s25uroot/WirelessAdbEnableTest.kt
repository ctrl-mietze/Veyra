package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a wireless-debugging write actually did.
 *
 * The rule exists because `Settings.Global.putInt` returns without complaint whether or not the device
 * honours the value. Reading it back is the only way to know, and the case that matters is the one where
 * the write was made and the setting did not move: that is a device refusing the change, and the user
 * needs to be told to use the switch instead of watching the app wait for a port that will never exist.
 */
class WirelessAdbEnableTest {

    @Test
    fun `a device that already had it on is not written to`() {
        assertEquals(
            WirelessAdbEnableResult.AlreadyOn,
            wirelessAdbEnableResult(
                stateBefore = true,
                route = WirelessAdbEnableRoute.Setting,
                stateAfter = true,
            ),
        )
    }

    @Test
    fun `a write that reads back as on is a success`() {
        assertEquals(
            WirelessAdbEnableResult.Enabled,
            wirelessAdbEnableResult(
                stateBefore = false,
                route = WirelessAdbEnableRoute.Setting,
                stateAfter = true,
            ),
        )
    }

    /** The shape that used to pass for success: the write went through and changed nothing. */
    @Test
    fun `a write that reads back as off is a refusal, not a success`() {
        assertEquals(
            WirelessAdbEnableResult.Refused,
            wirelessAdbEnableResult(
                stateBefore = false,
                route = WirelessAdbEnableRoute.Setting,
                stateAfter = false,
            ),
        )
        assertEquals(
            WirelessAdbEnableResult.Refused,
            wirelessAdbEnableResult(
                stateBefore = false,
                route = WirelessAdbEnableRoute.Root,
                stateAfter = false,
            ),
        )
    }

    @Test
    fun `a device with neither the permission nor root can only be changed by hand`() {
        assertEquals(
            WirelessAdbEnableResult.Unavailable,
            wirelessAdbEnableResult(
                stateBefore = false,
                route = WirelessAdbEnableRoute.Unavailable,
                stateAfter = false,
            ),
        )
    }

    /** An unreadable setting is never reported as an off one. */
    @Test
    fun `a setting that cannot be read is unknown rather than off`() {
        assertEquals(
            WirelessAdbEnableResult.Unknown,
            wirelessAdbEnableResult(
                stateBefore = false,
                route = WirelessAdbEnableRoute.Setting,
                stateAfter = null,
            ),
        )
        assertEquals(
            WirelessAdbEnableResult.Unknown,
            wirelessAdbEnableResult(
                stateBefore = null,
                route = WirelessAdbEnableRoute.Setting,
                stateAfter = null,
            ),
        )
    }

    /**
     * No route outranks an unreadable setting: nothing was written, so the answer is about the device's
     * permissions rather than about what it will not let us read.
     */
    @Test
    fun `no route is unavailable even when the setting cannot be read`() {
        assertEquals(
            WirelessAdbEnableResult.Unavailable,
            wirelessAdbEnableResult(
                stateBefore = null,
                route = WirelessAdbEnableRoute.Unavailable,
                stateAfter = null,
            ),
        )
    }

    /** A reading that arrives is what counts, whatever was read before the write. */
    @Test
    fun `reading it on afterwards is enough even if it was unreadable before`() {
        assertEquals(
            WirelessAdbEnableResult.Enabled,
            wirelessAdbEnableResult(
                stateBefore = null,
                route = WirelessAdbEnableRoute.Root,
                stateAfter = true,
            ),
        )
    }
}
