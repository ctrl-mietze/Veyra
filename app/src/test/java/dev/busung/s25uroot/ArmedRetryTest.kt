package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an armed retry means for the boot the phone is in.
 *
 * The rule is worth testing because getting it wrong is silent in both directions: call a retry that is
 * waiting on a reboot "ready to start" and the card offers the very attempt the user turned down when
 * they chose the restart; call one that is waiting on a tap "waiting on a restart" and the install is
 * never offered at all, on the device that has no cached payload for the boot gate to run.
 */
class ArmedRetryTest {

    private val earlierBoot = "370f9b19-0000-4000-8000-000000000001"
    private val currentBoot = "efd03bf5-0000-4000-8000-000000000002"

    @Test
    fun `a retry armed before this boot is waiting to be started`() {
        val retry = ArmedRetry.of(armedForBoot = earlierBoot, bootToken = currentBoot)

        assertEquals(earlierBoot, retry?.armedForBoot)
        assertTrue(retry!!.afterReboot)
    }

    @Test
    fun `a retry armed in this boot is still waiting on the restart`() {
        // Armed and then not rebooted for: starting it now would be the prompt retry the user declined.
        val retry = ArmedRetry.of(armedForBoot = currentBoot, bootToken = currentBoot)

        assertFalse(retry!!.afterReboot)
    }

    @Test
    fun `an unreadable boot id still offers the retry`() {
        // Nothing will start on its own with no boot id to run in, so the honest offer is to start it.
        val retry = ArmedRetry.of(armedForBoot = earlierBoot, bootToken = null)

        assertTrue(retry!!.afterReboot)
    }

    @Test
    fun `no armed retry is no card`() {
        assertNull(ArmedRetry.of(armedForBoot = null, bootToken = currentBoot))
    }

    @Test
    fun `a blank stored value is no retry either`() {
        // The preference is removed rather than blanked when a retry is consumed, and a blank read has
        // to mean the same thing: a stored empty string must not arm anything.
        assertNull(ArmedRetry.of(armedForBoot = "", bootToken = currentBoot))
    }
}
