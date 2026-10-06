package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chain that opens Developer options.
 *
 * Worth pinning because its failure mode is silent: an entry that resolves to nothing on a build it
 * was not written for looks exactly like a tap that did nothing, and the user is left where the
 * notification used to leave them - with an instruction to go somewhere the app said it would take
 * them.
 */
class DeveloperOptionsTest {

    /**
     * The screen the platform documents comes first, since on most builds it is the whole answer.
     */
    @Test
    fun `the documented action is tried first`() {
        val first = DeveloperOptions.targets().first()

        assertEquals("android.settings.APPLICATION_DEVELOPMENT_SETTINGS", first.action)
    }

    /**
     * Settings home last, and present. This is the entry that makes "could not open" impossible on any
     * device with a Settings app: the worse outcome is one screen away, never nowhere.
     */
    @Test
    fun `settings home is the last resort and every chain has one`() {
        val targets = DeveloperOptions.targets()

        assertEquals("android.settings.SETTINGS", targets.last().action)
        assertEquals(1, targets.count { it.action == "android.settings.SETTINGS" })
    }

    /**
     * The two dashboard names are both in the chain, because the devices this app runs on are Samsung
     * ones whose developer dashboard is not the AOSP class.
     */
    @Test
    fun `both dashboard class names are offered`() {
        val components = DeveloperOptions.targets().mapNotNull { it.component }

        assertTrue(components.any { it.endsWith("Settings\$DevelopmentSettingsActivity") })
        assertTrue(components.any { it.endsWith("Settings\$DevelopmentSettingsDashboardActivity") })
        assertTrue(components.all { it.startsWith("com.android.settings.") })
    }

    @Test
    fun `targets offer exactly one of an action or a component`() {
        DeveloperOptions.targets().forEach { target ->
            assertNotEquals(
                "${target.label} should name one way to open it, not both or neither",
                target.action == null,
                target.component == null,
            )
        }
    }

    /** A chain entry naming nothing is a bug the constructor refuses rather than launches. */
    @Test(expected = IllegalArgumentException::class)
    fun `a target with both an action and a component is refused`() {
        DeveloperOptions.Target("android.settings.SETTINGS", "com.android.settings.Settings")
    }

    /** A component outside the settings package is never what was meant. */
    @Test(expected = IllegalArgumentException::class)
    fun `a component target must be in the settings package`() {
        DeveloperOptions.Target(null, "com.example.Other")
    }

    /**
     * The selection itself: first entry that opens wins, so a build with only the last one still gets
     * a screen rather than an error.
     */
    @Test
    fun `the first openable target wins`() {
        val opened = mutableListOf<String>()
        val chosen = DeveloperOptions.firstOpenable(DeveloperOptions.targets()) { target ->
            opened += target.label
            target.action == "android.settings.SETTINGS"
        }

        assertEquals("android.settings.SETTINGS", chosen?.label)
        // Everything before it was tried, and nothing after it was: the order is the chain.
        assertEquals(DeveloperOptions.targets().size, opened.size)
    }

    @Test
    fun `nothing openable is reported as nothing rather than as a target`() {
        assertNull(DeveloperOptions.firstOpenable(DeveloperOptions.targets()) { false })
    }
}
