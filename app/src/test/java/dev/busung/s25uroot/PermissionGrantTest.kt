package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grant's one pure piece: the command that is handed to a shell.
 *
 * It is worth its own test because everything else about the grant is a device reading, and this is the
 * part where a mistake is not a failure but a *second command*: the string is a shell command rather
 * than an argument list, so a package name carrying a quote would otherwise end the quoting and run
 * whatever came after it.
 */
class PermissionGrantTest {

    @Test
    fun `the grant is the command the shell user is allowed to run`() {
        // The app's own id, read from the build rather than typed: this is the command a person is
        // handed when no transport can grant the permission for them, and the package in it has to be
        // the one that is installed.
        val applicationId = BuildConfig.APPLICATION_ID
        assertEquals(
            "pm grant '$applicationId' android.permission.WRITE_SECURE_SETTINGS",
            PermissionGrant.grantCommand(applicationId),
        )
    }

    @Test
    fun `a package name with a quote in it cannot end the quoting`() {
        val hostile = "app'; id; echo '"
        val command = PermissionGrant.grantCommand(hostile)

        // The word the shell will hand to `pm` is the package name and nothing more: every quote in it
        // is escaped, so none of it can end the quoting and start a second command.
        val word = command.removePrefix("pm grant ").substringBefore(" android.permission")
        assertEquals(hostile, unquote(word))
    }

    /** Undoes a shell single-quoting the way `/system/bin/sh` would, so the round trip is asserted. */
    private fun unquote(quoted: String): String {
        assertTrue(quoted.startsWith("'") && quoted.endsWith("'"))
        return quoted.substring(1, quoted.length - 1).replace("'\\''", "'")
    }

    @Test
    fun `a transport that was not asked grants nothing`() {
        assertFalse(GrantOutcome.NoTransport.granted)
        assertFalse(GrantOutcome.Refused(GrantTransport.CVeyraShell, "denied").granted)
        assertTrue(GrantOutcome.AlreadyGranted.granted)
        assertTrue(GrantOutcome.Granted(GrantTransport.RootShell).granted)
    }
}
