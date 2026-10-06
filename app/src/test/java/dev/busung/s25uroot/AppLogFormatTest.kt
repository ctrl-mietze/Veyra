package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app log's file format, which is the part of that feature a device is not needed to check.
 *
 * Everything asserted here is a rule the screen depends on: a line that round-trips is a line the tab
 * can show after a restart, a message that cannot contain a newline is why a stack trace is one entry
 * rather than eleven, and a trim that lands on a boundary is why the newest lines survive being
 * trimmed at all.
 */
class AppLogFormatTest {
    private fun entry(
        level: AppLogLevel = AppLogLevel.Info,
        tag: String = "InstallViewModel",
        message: String = "Starting a run",
        atMillis: Long = System.currentTimeMillis(),
    ) = AppLogEntry(atMillis = atMillis, level = level, tag = tag, message = message)

    @Test
    fun `a line survives being written and read back`() {
        val original = entry(level = AppLogLevel.Warn, tag = "AutoRootService", message = "Standing down")

        val parsed = AppLogFormat.parse(AppLogFormat.line(original))

        assertNotNull(parsed)
        assertEquals(AppLogLevel.Warn, parsed!!.level)
        assertEquals("AutoRootService", parsed.tag)
        assertEquals("Standing down", parsed.message)
        // The file carries no year, so the stamp is what has to match - the millis are reconstructed
        // from the current one, and comparing those would fail on the last day of any year.
        assertEquals(AppLogFormat.stamp(original.atMillis), AppLogFormat.stamp(parsed.atMillis))
    }

    @Test
    fun `every level round-trips`() {
        AppLogLevel.entries.forEach { level ->
            val parsed = AppLogFormat.parse(AppLogFormat.line(entry(level = level)))
            assertEquals(level, parsed?.level)
        }
    }

    /**
     * The reason a message is flattened at all: one entry is one line, and a line is what the file is
     * parsed by, trimmed by, and read by.
     */
    @Test
    fun `a message with newlines in it stays one line`() {
        val original = entry(message = "dd: failed\n  at line 2\n  at line 3")

        val line = AppLogFormat.line(original)

        assertEquals(1, line.lines().size)
        assertEquals("dd: failed \\n   at line 2 \\n   at line 3", AppLogFormat.parse(line)?.message)
    }

    @Test
    fun `a tag cannot split the line's fields`() {
        assertEquals("Wireless-ADB", AppLogFormat.normalizeTag("  Wireless ADB "))
        assertEquals("App", AppLogFormat.normalizeTag("   "))

        val parsed = AppLogFormat.parse(AppLogFormat.line(entry(tag = "Wireless ADB")))
        assertEquals("Wireless-ADB", parsed?.tag)
    }

    @Test
    fun `an over-long message is cut and marked as cut`() {
        val cut = AppLogFormat.oneline("x".repeat(50), maxChars = 10)

        assertEquals("xxxxxxxxxx …", cut)
        assertTrue(cut.endsWith(" …"))
    }

    @Test
    fun `a line that is not ours parses as nothing`() {
        assertNull(AppLogFormat.parse(""))
        assertNull(AppLogFormat.parse("something the payload printed"))
        // A missing stamp, which is what a half-written line looks like.
        assertNull(AppLogFormat.parse("I InstallViewModel Starting a run"))
        // A stamp with no level in it.
        assertNull(AppLogFormat.parse("09-17 21:04:33.123 InstallViewModel Starting a run"))
    }

    @Test
    fun `a trim keeps the newest lines and starts at a line boundary`() {
        val text = (1..20).joinToString("\n") { "line-$it" } + "\n"

        val kept = AppLogFormat.keepTailLines(text, maxChars = 40)

        assertTrue("kept ${kept.length} chars", kept.length <= 40)
        assertTrue(kept.endsWith("line-20\n"))
        // Nothing that merely looks like a line: the top of the tail is a whole line, not a fragment
        // of the one that was cut.
        assertFalse(kept.startsWith("e-"))
        kept.lineSequence().filter(String::isNotEmpty).forEach { line ->
            assertTrue("fragment kept: $line", line.startsWith("line-"))
        }
    }

    @Test
    fun `a text that fits is not trimmed`() {
        val text = "line-1\nline-2\n"
        assertEquals(text, AppLogFormat.keepTailLines(text, maxChars = 4096))
    }

    @Test
    fun `a level filter is a floor, not a match`() {
        val debug = entry(level = AppLogLevel.Debug)
        val info = entry(level = AppLogLevel.Info)
        val warn = entry(level = AppLogLevel.Warn)
        val error = entry(level = AppLogLevel.Error)

        assertFalse(AppLogFormat.matches(debug, AppLogLevel.Warn, ""))
        assertFalse(AppLogFormat.matches(info, AppLogLevel.Warn, ""))
        assertTrue(AppLogFormat.matches(warn, AppLogLevel.Warn, ""))
        assertTrue(AppLogFormat.matches(error, AppLogLevel.Warn, ""))
        assertTrue(AppLogFormat.matches(debug, AppLogLevel.Debug, ""))
    }

    @Test
    fun `a search looks at the tag as well as the message`() {
        val shizukuLine = entry(tag = "ShizukuController", message = "Permission not granted")
        val otherLine = entry(tag = "KernelSuManager", message = "Downloading the manager")

        assertTrue(AppLogFormat.matches(shizukuLine, AppLogLevel.Debug, "permission"))
        assertTrue(AppLogFormat.matches(shizukuLine, AppLogLevel.Debug, "shizukucontroller"))
        assertFalse(AppLogFormat.matches(otherLine, AppLogLevel.Debug, "shizuku"))
        // Whitespace around a search is not part of it: this is typed by hand.
        assertTrue(AppLogFormat.matches(otherLine, AppLogLevel.Debug, "  manager  "))
        assertTrue(AppLogFormat.matches(otherLine, AppLogLevel.Debug, ""))
    }

    @Test
    fun `the stamp is the shape the file holds and the tab shows`() {
        val stamp = AppLogFormat.stamp(System.currentTimeMillis())

        assertTrue("stamp was '$stamp'", Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$""").matches(stamp))
    }
}
