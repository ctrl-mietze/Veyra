package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStagingTest {

    private val quotedTemp = shellQuote("/data/local/tmp/payload.so.shizuku-abc.tmp")
    private val quotedPath = shellQuote("/data/local/tmp/payload.so")

    @Test
    fun theUploadNeverNamesTheDestination() {
        // The bug this replaced: `cat > <destination>` truncated the file the app was about to
        // execute, so an upload that died halfway left a partial payload where a good one had been
        // and the cache ran it. Nothing before publishing may mention the destination.
        val upload = uploadCommand(quotedTemp)

        assertFalse(upload.contains(quotedPath))
        assertTrue(upload.contains("cat > $quotedTemp"))
        assertTrue(upload.contains("rm -f $quotedTemp"))
    }

    @Test
    fun publishingRefusesAShortFileBeforeMovingIt() {
        val script = publishCommand(quotedPath, quotedTemp, "755", expectedBytes = 4096)

        // The size is checked against what the local side actually sent, so a truncated transfer is
        // caught even when the manifest declared size was wrong.
        assertTrue(script.contains("actual=$(/system/bin/wc -c < $quotedTemp)"))
        assertTrue(script.contains("-ne 4096"))
        assertTrue(script.contains("staged size mismatch"))
        // And it fails before the move, so the destination keeps whatever it had.
        assertTrue(script.indexOf("exit 1") < script.indexOf("mv -f"))
        assertTrue(script.contains("chmod 755 $quotedTemp"))
        assertTrue(script.contains("mv -f $quotedTemp $quotedPath"))
    }

    @Test
    fun publishingCleansUpAfterItselfOnEveryExit() {
        val script = publishCommand(quotedPath, quotedTemp, "644", expectedBytes = 1)

        // A temp file left behind in /data/local/tmp is one more file the next run has to reason
        // about, and the trap is what covers the failure paths that exit before the move.
        assertTrue(script.contains("trap 'rm -f $quotedTemp' EXIT HUP INT TERM"))
        assertTrue(script.startsWith("set -e"))
    }

    @Test
    fun aPathIsQuotedSoTheRemoteShellCannotInterpretIt() {
        assertEquals("'/data/local/tmp/payload.so'", shellQuote("/data/local/tmp/payload.so"))
        assertEquals("'/data/local/tmp/a b.so'", shellQuote("/data/local/tmp/a b.so"))
        // A quote inside the value closes the quoted string, escapes, and reopens it.
        assertEquals("'a'\\''b'", shellQuote("a'b"))
        // Interpolation characters reach the shell as data, not as syntax.
        assertEquals("'\$(reboot)'", shellQuote("\$(reboot)"))
    }

    @Test
    fun onlyOctalPermissionsAreAcceptedAsAMode() {
        assertTrue(isFileMode("755"))
        assertTrue(isFileMode("644"))
        assertTrue(isFileMode("0755"))
        assertFalse(isFileMode("75"))
        assertFalse(isFileMode("999"))
        assertFalse(isFileMode(""))
        assertFalse(isFileMode("755; rm -rf /"))
        assertFalse(isFileMode("-rwxr-xr-x"))
    }
}
