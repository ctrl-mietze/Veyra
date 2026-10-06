package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {

    private fun entry(id: String, result: InstallRunResult) = InstallHistoryEntry(
        id = id,
        startedAtMillis = 1_000,
        completedAtMillis = 2_000,
        result = result,
        log = "",
    )

    private val history = listOf(
        entry("a", InstallRunResult.Succeeded),
        entry("b", InstallRunResult.Failed),
        entry("c", InstallRunResult.Failed),
        entry("d", InstallRunResult.RootOnly),
    )

    // --- chips ---------------------------------------------------------------------------------

    @Test
    fun `a result the history never recorded gets no chip`() {
        val chips = historyResultFilters(history, selected = HistoryFilter.All)

        assertEquals(
            listOf(HistoryFilter.All, HistoryFilter.Succeeded, HistoryFilter.RootOnly, HistoryFilter.Failed),
            chips,
        )
    }

    @Test
    fun `the selected result keeps its chip after its last run is gone`() {
        val empty = emptyList<InstallHistoryEntry>()

        val chips = historyResultFilters(empty, selected = HistoryFilter.Failed)

        // Otherwise deleting the last failure would take the chip away and leave an empty list that
        // cannot be explained or cleared from the chips themselves.
        assertEquals(listOf(HistoryFilter.All, HistoryFilter.Failed), chips)
    }

    @Test
    fun `chips follow the enum order, not the order runs happen to be in`() {
        val reversed = history.reversed()

        assertEquals(
            historyResultFilters(history, HistoryFilter.All),
            historyResultFilters(reversed, HistoryFilter.All),
        )
    }

    // --- filtering -----------------------------------------------------------------------------

    @Test
    fun `all shows every run, a chip shows only its kind`() {
        assertEquals(4, filterHistory(history, HistoryFilter.All).size)
        assertEquals(listOf("b", "c"), filterHistory(history, HistoryFilter.Failed).map { it.id })
        assertEquals(listOf("a"), filterHistory(history, HistoryFilter.Succeeded).map { it.id })
        assertEquals(listOf("d"), filterHistory(history, HistoryFilter.RootOnly).map { it.id })
        assertEquals(emptyList<String>(), filterHistory(history, HistoryFilter.Stopped).map { it.id })
    }

    @Test
    fun `a running run is its own kind, not a failure`() {
        val live = listOf(entry("a", InstallRunResult.Running))

        assertEquals(emptyList<String>(), filterHistory(live, HistoryFilter.Failed).map { it.id })
        assertEquals(listOf("a"), filterHistory(live, HistoryFilter.Running).map { it.id })
    }

    @Test
    fun `filtering keeps the history's own order`() {
        val newestFirst = listOf(
            entry("new", InstallRunResult.Failed),
            entry("old", InstallRunResult.Failed),
        )

        assertEquals(listOf("new", "old"), filterHistory(newestFirst, HistoryFilter.Failed).map { it.id })
    }
}
