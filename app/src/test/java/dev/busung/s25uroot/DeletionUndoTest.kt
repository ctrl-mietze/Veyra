package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which deletions ask first, and which offer a way back instead.
 *
 * The rule is not "deletions are undoable" - it is that a confirmation and an undo are for different things.
 * A run's log is a file this app wrote and the only copy of it, so removing one is a change to the phone and
 * the entries have to be held before they go. A file this app staged in `/data/local/tmp` is copyable from
 * the payload catalogue, so removing one costs nothing and asking about it charged every cleanup a second tap
 * to protect against a mistake with no consequence.
 *
 * The one case that still asks is the one where the app cannot know what it is removing: a name in the shared
 * temp directory that it did not write. That is what the confirmation is left for, and this pins it that way
 * so a later change cannot widen the one-tap path past the files the app owns.
 */
class DeletionUndoTest {

    @Test
    fun `a deleted run is held before it goes, so it can be put back`() {
        val source = mainActivity()

        // Captured from the list before the delete, because the store is the only copy: an undo that
        // re-read the directory afterwards would have nothing to put back.
        assertTrue(
            "the history delete does not capture the entries it is about to remove",
            source.contains("val doomed = history.filter { it.id in ids }"),
        )
        assertTrue(
            "nothing offers to put a deleted run back",
            source.contains("onRestoreEntries(doomed)"),
        )
        assertTrue(
            "the undo is not offered as the action of the snackbar that reports the delete",
            source.contains("actionLabel = context.getString(R.string.action_undo)"),
        )
        assertTrue(
            "the message does not say what went",
            source.contains("R.plurals.history_deleted"),
        )
    }

    @Test
    fun `deleting a run no longer asks first`() {
        val source = mainActivity()

        // The dialog's own strings are the marker: the flow that asked is gone rather than merely skipped.
        assertFalse(
            "the history delete still asks before it acts, which is what the undo replaced",
            source.contains("history_delete_selected_title"),
        )
        assertTrue(
            "the delete is not reachable from the selection bar",
            source.contains("onDeleteSelected = { deleteWithUndo(selectionIds) }"),
        )
    }

    @Test
    fun `the app's own staging is removed on one tap`() {
        val stagedRow = mainActivity().substringAfter("private fun StagedResidueDialog")

        assertTrue(
            "the row for a file this app staged does not delete on the tap itself",
            stagedRow.contains("deleteNow(\n                                            PendingDelete("),
        )
    }

    @Test
    fun `a name the app did not write is still asked about`() {
        val source = mainActivity()

        assertTrue(
            "a leftover this app did not stage is no longer confirmed",
            source.contains("pendingDelete = PendingDelete(entry.name, entry.path)"),
        )
        assertTrue(
            "the confirmation does not say the entry is not this app's",
            source.contains("R.string.residue_delete_other"),
        )
        // Two kinds of row cannot reach the same confirmation: that is the distinction being tested, and a
        // single `mine` flag deciding it inside the dialog is how it was expressed before this.
        assertFalse(
            "the confirmation still decides what to say per row",
            source.contains("if (pending.mine)"),
        )
    }

    @Test
    fun `the undo is one surface for the whole app`() {
        val source = mainActivity()

        assertTrue(
            "the app has no snackbar host for a deletion to report through",
            source.contains("snackbarHost = { SnackbarHost(snackbarHostState) }"),
        )
        assertTrue(
            "history does not report its deletion through the app's own host",
            source.contains("snackbarHostState = snackbarHostState,"),
        )
    }

    private fun mainActivity(): String = listOf(
        File("src/main/java/dev/busung/s25uroot/MainActivity.kt"),
        File("app/src/main/java/dev/busung/s25uroot/MainActivity.kt"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("MainActivity was not found from ${File(".").absolutePath}")
}
