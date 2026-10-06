package ctrl.mietze.veyraroot

import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Logs tab's filter: the level floor, the text, and nothing else. */
class LogFilterTest {

    private fun entry(
        level: AppLogLevel,
        tag: String,
        message: String = "line",
        atMillis: Long = 1_700_000_000_000,
    ) = AppLogEntry(atMillis = atMillis, level = level, tag = tag, message = message)

    private val log = listOf(
        entry(AppLogLevel.Info, AppLogTags.SHIZUKU, "binder came up"),
        entry(AppLogLevel.Debug, AppLogTags.SHIZUKU, "start requested"),
        entry(AppLogLevel.Warn, AppLogTags.KERNEL_SU, "daemon did not answer"),
        entry(AppLogLevel.Error, AppLogTags.KERNEL_SU, "load refused"),
        entry(AppLogLevel.Error, AppLogTags.RUN, "exploit failed"),
        entry(AppLogLevel.Info, AppLogTags.CATALOG, "fetched revision"),
    )

    @Test
    fun `an untouched filter shows every line`() {
        val filter = LogFilter()
        assertEquals(log.size, log.count(filter::matches))
    }

    @Test
    fun `the Errors chip is the warnings floor under the name of what people look for`() {
        val filter = LogFilter().withErrorsOnly(true)
        assertEquals(AppLogLevel.Warn, filter.minLevel)
        assertTrue(filter.errorsOnly)
        assertEquals(
            listOf("daemon did not answer", "load refused", "exploit failed"),
            log.filter(filter::matches).map { it.message },
        )
    }

    @Test
    fun `turning Errors off only lowers the floor that chip raised`() {
        assertEquals(AppLogLevel.Debug, LogFilter().withErrorsOnly(true).withErrorsOnly(false).minLevel)

        // A higher floor is a level someone picked, and this chip did not pick it.
        val higher = LogFilter(minLevel = AppLogLevel.Error)
        assertEquals(AppLogLevel.Error, higher.withErrorsOnly(false).minLevel)
        assertTrue(higher.errorsOnly)
    }

    @Test
    fun `the filter is a floor and a text, with no tag dimension left`() {
        // The tag row and the count behind each chip were removed rather than hidden, because the row was
        // the tallest thing above the log - and the one question it answered is now the text field's job.
        // Read off the declaration rather than searched for by name, so a `tags` field added back fails
        // here even if it is called something else.
        // Instance fields only: the class also carries the Compose compiler's static `$stable`, which is not
        // one of the questions the filter is asked.
        val fields = LogFilter::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertTrue("the level floor is gone from the filter", fields.contains("minLevel"))
        assertTrue("the text is gone from the filter", fields.contains("query"))
        assertFalse(
            "a tag set is part of the filter again; it was removed, not hidden",
            fields.any { it.contains("tag", ignoreCase = true) },
        )
    }

    @Test
    fun `one chip covers everything that went wrong`() {
        // Four chips were drawn where three floors were meant: a Problems chip at the warnings floor and an
        // Errors chip one level above it, showing a subset of the lines the first already showed. Read off
        // the page rather than the filter, because where that redundancy lived was the row.
        val page = source("src/main/java/dev/busung/s25uroot/MainActivity.kt")
            .substringAfter("private fun LogsPage")
            .substringBefore("\nprivate fun ")

        assertEquals(
            "the level chips are no longer All, Info and Errors, one chip per floor",
            3,
            Regex("LogFilterChip\\(").findAll(page).count(),
        )
        assertFalse(
            "a second chip for the same failures is back",
            source("src/main/java/dev/busung/s25uroot/MainActivity.kt").contains("logs_filter_problems"),
        )
        assertFalse(
            "the Problems label is back in the strings",
            source("src/main/res/values/strings.xml").contains("logs_filter_problems"),
        )
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
