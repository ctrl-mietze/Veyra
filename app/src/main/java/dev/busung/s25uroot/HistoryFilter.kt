package ctrl.mietze.veyraroot

/**
 * Which runs the history list shows, as the result chips name them.
 *
 * One filter at a time rather than a set, because the interesting question in this screen is almost
 * always "which of these went wrong" - a run has exactly one result, so a selection of several would
 * only ever mean the union, which is a longer way of saying one of them.
 */
enum class HistoryFilter {
    All,
    Succeeded,
    RootOnly,
    Failed,
    Stopped,
    Running;

    fun matches(result: InstallRunResult): Boolean = when (this) {
        All -> true
        Succeeded -> result == InstallRunResult.Succeeded
        RootOnly -> result == InstallRunResult.RootOnly
        Failed -> result == InstallRunResult.Failed
        Stopped -> result == InstallRunResult.Stopped
        Running -> result == InstallRunResult.Running
    }
}

/**
 * The chips worth drawing: all of them, then one per result this history actually holds.
 *
 * A chip that can only ever produce an empty list is noise, so a result the history has never recorded
 * gets no chip - with one exception, the result currently selected. Without it, deleting the last run of
 * a filtered kind would take the chip away and leave the list empty with nothing to explain why or to
 * press to get back, which is the state this screen exists to avoid.
 */
fun historyResultFilters(
    entries: List<InstallHistoryEntry>,
    selected: HistoryFilter,
): List<HistoryFilter> = buildList {
    add(HistoryFilter.All)
    HistoryFilter.entries.forEach { filter ->
        if (filter == HistoryFilter.All) return@forEach
        val recorded = entries.any { filter.matches(it.result) }
        if (recorded || filter == selected) add(filter)
    }
}

/**
 * The runs to draw: those whose result the chosen chip names.
 *
 * The order is the history's own - newest first - because filtering is a question about which runs to
 * look at, not about how to sort them.
 */
fun filterHistory(
    entries: List<InstallHistoryEntry>,
    filter: HistoryFilter,
): List<InstallHistoryEntry> = entries.filter { filter.matches(it.result) }
