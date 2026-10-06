package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reboot menu's two rules, both of which fail silently.
 *
 * A row offered on a tier that cannot ask for it is a button that does nothing where it should have been
 * greyed out, and an argument that is not a single word is a command that turns into two - so the target
 * list is checked as text rather than trusted.
 */
class RebootTargetsTest {

    @Test
    fun `a root shell can ask for every one of them`() {
        RebootTarget.entries.forEach { target ->
            assertTrue("root cannot ask for ${target.name}", ShellTier.Root.canAskFor(target))
            assertNull(
                "root is refused ${target.name}",
                rebootRefusalFor(ShellTier.Root, target),
            )
        }
    }

    @Test
    fun `a shizuku shell can reboot the phone, in every mode, but cannot restart the userspace`() {
        // The shell user holds the reboot permission - it is how `adb reboot recovery` works - so the
        // target travels as the command's own argument and none of these needs root.
        RebootTarget.entries
            .filterNot { it.viaDaemon }
            .forEach { target ->
                assertTrue(
                    "a Shizuku shell cannot ask for ${target.name}",
                    ShellTier.Unprivileged.canAskFor(target),
                )
            }

        // The soft restart is the daemon emulating a reboot, which is not a reboot request at all.
        assertFalse(ShellTier.Unprivileged.canAskFor(RebootTarget.SoftRestart))
        assertEquals(
            RebootRefusal.NeedsRoot,
            rebootRefusalFor(ShellTier.Unprivileged, RebootTarget.SoftRestart),
        )
    }

    @Test
    fun `no shell at all offers nothing, and says so`() {
        RebootTarget.entries.forEach { target ->
            assertFalse("nothing can ask for ${target.name}", ShellTier.None.canAskFor(target))
            assertEquals(
                RebootRefusal.NothingToAskWith,
                rebootRefusalFor(ShellTier.None, target),
            )
        }
    }

    @Test
    fun `the command asks for the target by name`() {
        assertEquals(
            listOf("/system/bin/reboot download", "/system/bin/svc power reboot download"),
            rebootCommands(RebootTarget.Download),
        )
        assertEquals(
            listOf("/system/bin/reboot recovery", "/system/bin/svc power reboot recovery"),
            rebootCommands(RebootTarget.Recovery),
        )
    }

    @Test
    fun `a plain reboot carries no argument`() {
        assertEquals(
            listOf("/system/bin/reboot", "/system/bin/svc power reboot"),
            rebootCommands(RebootTarget.Reboot),
        )
    }

    @Test
    fun `the daemon's own restart is not a reboot command`() {
        // It goes through the recovery actions, which own the checks that make it verifiable.
        assertTrue(rebootCommands(RebootTarget.SoftRestart).isEmpty())
    }

    @Test
    fun `every argument is one word`() {
        // The argument is pasted into a shell command, so a second word in it would be a second command:
        // the list is checked as text rather than trusted to stay well formed.
        val argument = Regex("[a-z]+")
        RebootTarget.entries.forEach { target ->
            val value = target.argument ?: return@forEach
            assertTrue("${target.name} carries '$value', which is not one lowercase word", argument.matches(value))
        }
    }

    @Test
    fun `the ones that leave Android are the ones that are confirmed first`() {
        assertEquals(
            listOf(
                RebootTarget.Recovery,
                RebootTarget.Bootloader,
                RebootTarget.Download,
                RebootTarget.Edl,
            ),
            RebootTarget.entries.filter { it.leavesAndroid },
        )
        // A plain reboot and a soft restart both come back to Android, so neither is a one-way door.
        assertFalse(RebootTarget.Reboot.leavesAndroid)
        assertFalse(RebootTarget.SoftRestart.leavesAndroid)
    }

    @Test
    fun `the six rows draw one card rather than six`() {
        val count = RebootTarget.entries.size
        val positions = RebootTarget.entries.indices.map { rebootRowPosition(it, count) }

        // The ends carry the curve and everything between them is flat, which is the whole of what makes a
        // run of rows read as one card with seams in it rather than as a stack of separate things.
        assertEquals(SettingsCardPosition.Top, positions.first())
        assertEquals(SettingsCardPosition.Bottom, positions.last())
        assertEquals(
            List(count - 2) { SettingsCardPosition.Middle },
            positions.subList(1, positions.lastIndex),
        )
        assertEquals("more than one top", 1, positions.count { it == SettingsCardPosition.Top })
        assertEquals("more than one bottom", 1, positions.count { it == SettingsCardPosition.Bottom })
        // Rounding an end that is inside the group is the failure the eye has to catch, so it is the one the
        // rule is written to make impossible: nothing in the middle can be an end.
        assertEquals(0, positions.count { it == SettingsCardPosition.GroupedSingle })
    }

    @Test
    fun `a target on its own rounds at both ends`() {
        // A single row in a group is the one case where Top-then-Bottom would draw a cap over a seam.
        assertEquals(SettingsCardPosition.GroupedSingle, rebootRowPosition(0, 1))
    }

    @Test
    fun `everything that leaves Android carries an argument, and the daemon's restart does not`() {
        RebootTarget.entries.forEach { target ->
            if (target.leavesAndroid || target == RebootTarget.SoftRestart) {
                assertTrue("${target.name} asks for nothing in particular", target.argument != null || target.viaDaemon)
            }
        }
        assertNull(RebootTarget.SoftRestart.argument)
    }
}
