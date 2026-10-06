package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLogExportTest {

    private fun entry(
        id: String,
        startedAtMillis: Long,
        result: InstallRunResult,
        log: String = "line",
    ) = InstallHistoryEntry(
        id = id,
        startedAtMillis = startedAtMillis,
        completedAtMillis = startedAtMillis + 1_000,
        result = result,
        log = log,
    )

    @Test
    fun anArchiveCountsOnlyTheRunsThatCanGoIntoIt() {
        val entries = listOf(
            entry("aaaaaaaa-0000", 1_000, InstallRunResult.Succeeded),
            entry("bbbbbbbb-0000", 2_000, InstallRunResult.Failed),
            // Still being written: its log has no end yet, so it cannot be part of the archive.
            entry("cccccccc-0000", 3_000, InstallRunResult.Running),
        )

        val name = HistoryLogExporter.archiveFileName(entries, now = 1_767_225_600_000)

        assertTrue(name.endsWith("-2.zip"))
        assertTrue(name.startsWith("RootMyGalaxy-logs-"))
    }

    @Test
    fun archiveNamesAreStableForTheSameMoment() {
        val entries = listOf(entry("aaaaaaaa-0000", 1_000, InstallRunResult.Succeeded))

        assertEquals(
            HistoryLogExporter.archiveFileName(entries, now = 1_767_225_600_000),
            HistoryLogExporter.archiveFileName(entries, now = 1_767_225_600_000),
        )
    }

    @Test
    fun anEntryNameCarriesItsIdBecauseTwoRunsCanShareASecond() {
        val first = entry("11111111-2222-3333", 1_767_225_600_000, InstallRunResult.Succeeded)
        val second = entry("99999999-8888-7777", 1_767_225_600_000, InstallRunResult.Succeeded)

        val firstName = HistoryLogExporter.entryFileName(first)

        // Date and time are formatted in the device's zone, so the shape is what is asserted here
        // rather than a fixed hour.
        assertTrue(
            firstName.matches(Regex("RootMyGalaxy-\\d{8}-\\d{6}-succeeded-11111111\\.log")),
        )
        // Same second, same result: only the id keeps the two archive entries apart.
        assertNotEquals(firstName, HistoryLogExporter.entryFileName(second))
    }

    @Test
    fun anEntryNameReportsTheResultItActuallyHad() {
        val failed = entry("11111111-2222-3333", 1_767_225_600_000, InstallRunResult.Failed)

        assertTrue(HistoryLogExporter.entryFileName(failed).contains("-failed-"))
    }
}
