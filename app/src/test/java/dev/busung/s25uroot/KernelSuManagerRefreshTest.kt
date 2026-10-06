package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSuManagerRefreshTest {

    @Test
    fun `the manager is only stopped if it is running`() {
        // The reading and the action are one command on purpose: a separate check is a second answer
        // to a question that can change in between, and a manager stopped when it was never running
        // would make the log claim a refresh that did not happen.
        val command = KernelSuManagerRefresh.stopCommand("me.weishu.kernelsu")
        assertTrue(command.startsWith("if pidof "))
        assertTrue(command.contains("am force-stop "))
        assertTrue(command.indexOf("pidof") < command.indexOf("am force-stop"))
    }

    @Test
    fun `the package name is quoted everywhere it is used`() {
        // The name comes off the device, not from this app: KernelSU-Next's spoofed manager build
        // rewrites its own package to three random words, so a name with a shell character in it is a
        // name this app has to survive rather than one it can rule out. Both uses are quoted - the
        // `pidof` and the `force-stop` - and the check is that nothing of the name survives outside
        // those quotes, which is what would actually run as a command.
        val hostile = "com.example; rm -rf /data"
        val command = KernelSuManagerRefresh.stopCommand(hostile)
        assertEquals(2, Regex(Regex.escape("'$hostile'")).findAll(command).count())
        val unquoted = command.replace(Regex("'[^']*'"), "")
        assertFalse(unquoted.contains("rm -rf"))
        assertFalse(unquoted.contains("com.example"))
    }

    @Test
    fun `a quote inside the name cannot end the quoting early`() {
        // A shell quote is the one character that would otherwise close the quoted span and let the
        // rest run as a command, so the escape for it is checked the same way: strip every quoted span
        // and the escape itself, then nothing of the name may be left in what remains.
        val command = KernelSuManagerRefresh.stopCommand("com.o'brien; killall zygote")
        val unquoted = command
            .replace("'\\''", "")
            .replace(Regex("'[^']*'"), "")
        assertFalse(unquoted.contains("brien"))
        assertFalse(unquoted.contains("killall"))
    }

    @Test
    fun `a marker from a failing command is not a refresh`() {
        assertFalse(
            KernelSuManagerRefresh.stopped(
                ShizukuController.ShellResult(exitCode = 1, output = "RMG_MANAGER_STOPPED"),
            ),
        )
    }

    @Test
    fun `a manager that was not running is not reported as refreshed`() {
        // The shell prints nothing when the manager is not running, which is the common case and must
        // not produce a log line about a manager that was stopped.
        assertFalse(KernelSuManagerRefresh.stopped(ShizukuController.ShellResult(0, "")))
        assertFalse(KernelSuManagerRefresh.stopped(null))
    }

    @Test
    fun `a stopped manager is reported`() {
        assertTrue(KernelSuManagerRefresh.stopped(ShizukuController.ShellResult(0, "RMG_MANAGER_STOPPED\n")))
    }
}
