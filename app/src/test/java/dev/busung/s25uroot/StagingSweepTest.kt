package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a sweep deletes, what it refuses to, and what it is able to say about it.
 *
 * The two rules worth pinning are the ones whose failure is silent. A sweep set that includes the
 * daemon breaks this boot's own repair actions, and a sweep that cannot see a run in flight deletes the
 * payload that run is executing - neither shows up as a failed assertion unless it is written as one.
 */
class StagingSweepTest {

    @Test
    fun `every catalogued path is swept when this app is the only install`() {
        // Nothing staged in /data/local/tmp outlives the run that staged it, and the daemon is the
        // exemption worth pinning as absent: it is the largest artefact this app leaves behind and the
        // first name a detector prints.
        assertEquals(
            StagedResidue.catalog.map { it.path }.toSet(),
            StagingSweep.removable(otherInstallPresent = false).map { it.path }.toSet(),
        )
        val names = StagingSweep.removable(false).map { it.name }
        assertTrue(names.contains("ksud-s25u-kdp"))
        assertTrue(names.contains(".ksud-stage"))
    }

    @Test
    fun `the names both installs write are left alone while the other install is here`() {
        // Those names are the payload's rather than either app's, so both apps write them and neither
        // can tell whose file it is looking at. Deleting one while the other app is mid-run is deleting
        // the payload that run is about to load, which is the one way this sweep can break something
        // outside itself.
        val swept = StagingSweep.removable(otherInstallPresent = true).map { it.name }.toSet()

        StagedResidue.sharedWithTheOtherInstall.forEach { shared ->
            assertFalse("$shared is swept even though the other install writes it", swept.contains(shared))
        }
        // And everything else still goes: the exemption must not become a sweep that stops sweeping.
        val ours = StagedResidue.catalog
            .map { it.name }
            .filterNot { it in StagedResidue.sharedWithTheOtherInstall }
        assertTrue("the exemption covers the whole catalogue", ours.isNotEmpty())
        assertEquals(ours.toSet(), swept)
    }

    @Test
    fun `no name this app chooses is ever exempt from the sweep`() {
        // The list is "what we may not rename", not "what we decided not to clean": a name this fork
        // picks that ended up on it would quietly shrink the sweep on every device that also has the
        // other app installed, which is the failure nobody would report.
        val catalogued = StagedResidue.catalog.map { it.name }.toSet()

        StagedResidue.sharedWithTheOtherInstall.forEach { shared ->
            assertTrue(
                "$shared is exempted but is not in the catalogue, so nothing reads it",
                catalogued.contains(shared),
            )
            assertFalse(
                "$shared carries the fork's own prefix, so it is ours to name and should be swept",
                shared.startsWith("rmgnext-"),
            )
        }
        // The socket is nobody's to rename: the payload's daemon creates it, under that name, on both
        // installs.
        assertTrue(StagedResidue.sharedWithTheOtherInstall.contains("temp_su.sock"))
    }

    @Test
    fun `the sibling check looks for the id this fork moved off, not for this app`() {
        // A check for our own id would answer true on every device that has this app on it, so the
        // sweep would be narrowed everywhere and the daemon copy would stay in /data/local/tmp after
        // every run - a bug that reads like a deliberate policy.
        assertNotEquals(BuildConfig.APPLICATION_ID, SiblingInstall.PACKAGE)
        assertEquals("ctrl.mietze.veyraroot", SiblingInstall.PACKAGE)
    }

    @Test
    fun `the reason that is safe still holds in the one action that reads a daemon`() {
        val source = sourceFiles().firstOrNull { it.name == "RootRecovery.kt" }
        requireNotNull(source) { "RootRecovery.kt was not found; the scan is looking at the wrong directory" }
        val text = source.readText()

        // Every action in that file reaches the daemon at its installed path...
        assertTrue(text.contains("private const val KSUD_PATH = \"/data/adb/ksud\""))
        // ...and the reload, the only action that wants a stage file, writes it from that copy itself,
        // which is what makes the staged daemon this sweep removes unread by anything.
        assertTrue(text.contains("KSUD=\$KSUD_PATH"))
        assertTrue(text.contains("'ksud-stage-copy-failed'"))
        assertFalse(
            "a recovery action now reads the staged daemon, which this sweep deletes after every run",
            text.contains("/data/local/tmp/ksud-s25u-kdp"),
        )
    }

    @Test
    fun `the helper's late-load, the reader the staged daemon does have, is asked for inside a run`() {
        // The payload's helper bind-mounts /data/local/tmp/ksud-s25u-kdp when it late-loads, and the
        // helper is otherwise a socket server with no retry of its own - so the sweep's safety rests on
        // every such request being a step of a run, made before that run's own sweep. A request from
        // anywhere else would be a request made after the file was deleted.
        val askers = sourceFiles().filter { it.readText().contains("\"--late-load\"") }.map { it.name }

        assertEquals(listOf("InstallViewModel.kt"), askers)
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)


    @Test
    fun `everything a detector names as temp-root residue is swept, the daemon aside`() {
        val swept = StagingSweep.removable(otherInstallPresent = false).map { it.name }

        assertTrue(swept.contains("rmgnext-helper"))
        assertTrue(swept.contains("rmgnext-shizuku-payload"))
        assertTrue(swept.contains("temp_su.sock"))
        assertTrue(swept.contains("ksud-s25u-kdp"))
        assertTrue(swept.contains("rmgnext-ksud-helper"))
        assertTrue(swept.contains("rmgnext-payload"))
        assertTrue(swept.contains("rmgnext-shizuku-exploit.log"))
        assertTrue(swept.contains("rmgnext-soft-reboot-keeper.sh"))
        assertTrue(swept.contains(".rmgnext-soft-reboot-accepted"))
        // The names this fork no longer writes are swept too, and that is deliberate: a device that ran
        // one of its earlier builds still has them, and the older app's names are the ones detectors
        // were reading when they reported this app.
        assertTrue(swept.contains("ksu-helper"))
        assertTrue(swept.contains("rmg-ksud-helper"))
        assertTrue(swept.contains("rmg-reload-modules-ksud.log"))
    }

    @Test
    fun `the command asks what is there before it deletes and after`() {
        // The whole text, because the order is the report: the "had" lines are what the cleanup has to
        // be measured against, and the "left" lines are what it could not do. A shape assertion would
        // pass on a command that asked twice in the wrong order, and this is a shell script where only
        // the text is the truth.
        val path = "/data/local/tmp/a"
        val expected = """
            [ -e '$path' ] && printf 'had %s\n' '$path'
            rm_out=${'$'}(rm -f -- '$path' 2>&1)
            [ -n "${'$'}rm_out" ] && printf 'said %s\n' "${'$'}rm_out"
            [ -e '$path' ] && printf 'left %s\n' '$path'
            exit 0
        """.trimIndent()
        assertEquals(expected, StagingSweep.command(listOf(path)))
    }

    @Test
    fun `the shell is told to end clean, so a deleted path cannot be read as a refusal`() {
        // A script's exit status is its last command's, and that was a `[ -e ]` test on a path this
        // sweep had just deleted - so a perfect sweep exited 1 and every run was reported as a refusal.
        val command = StagingSweep.command(listOf("/data/local/tmp/a", "/data/local/tmp/gone"))

        assertTrue("the shell's status is still a test on a path", command.trimEnd().endsWith("exit 0"))
        assertTrue(StagingSweep.command(listOf("/data/local/tmp/a")).contains("2>&1"))
    }

    @Test
    fun `a refusal is read from what the delete said`() {
        val output = """
            had /data/local/tmp/ksu-helper
            said rm: /data/local/tmp/temp_su.sock: Permission denied
            left /data/local/tmp/temp_su.sock
        """.trimIndent()

        // The socket is denied to the shell domain, so its own presence check cannot see it and what
        // `rm` said is the only report there will ever be.
        assertEquals(
            listOf("rm: /data/local/tmp/temp_su.sock: Permission denied"),
            StagingSweep.pathsIn(output, StagingSweep.SAID_PREFIX),
        )
        assertEquals(
            SweepVerdict.Refused,
            SweepOutcome.Done(
                found = StagingSweep.pathsIn(output, StagingSweep.FOUND_PREFIX),
                left = StagingSweep.pathsIn(output, StagingSweep.LEFT_PREFIX),
                complaint = StagingSweep.pathsIn(output, StagingSweep.SAID_PREFIX).joinToString(", "),
            ).verdict,
        )
    }

    @Test
    fun `every path in the sweep is asked about, not only the first`() {
        val command = StagingSweep.command(listOf("/data/local/tmp/a", "/data/local/tmp/b"))

        assertEquals(2, command.split("'had ").size - 1)
        assertEquals(2, command.split("'left ").size - 1)
        assertTrue(command.contains("rm -f -- '/data/local/tmp/a' '/data/local/tmp/b'"))
    }

    @Test
    fun `a delete's complaints come back in the output rather than being lost`() {
        // A transport that returns only stdout would report a denied unlink as a clean sweep.
        assertTrue(StagingSweep.command(listOf("/data/local/tmp/a")).contains("2>&1"))
    }

    @Test
    fun `every path is quoted, so a name cannot become a second command`() {
        val hostile = "/data/local/tmp/a'; rm -rf /data #"
        val command = StagingSweep.command(listOf(hostile))

        // The name appears, and it appears quoted, so the quote inside it is inert. Checked as a
        // property rather than as one expected string, because that is what makes it hold for names
        // nobody thought of when this was written.
        assertTrue(command.contains(shellQuote(hostile)))
        assertFalse("the path was not quoted", command.contains("a'; rm -rf"))
    }

    @Test
    fun `a successful run sweeps before it asks for the restart that would end it`() {
        // The failure this prevents is silent, and it was real: the sweep lived only in the runner's own
        // `finally`, while a successful run asks for the userspace restart from inside the `try` - so the
        // process was gone before the shell could answer, and a loaded root left its staged helper and
        // payload in /data/local/tmp for a detector to find, on exactly the runs that worked. The order
        // is the fix, so the order is what this asserts, against the source that holds it.
        val source = sourceFiles().firstOrNull { it.name == "InstallViewModel.kt" }
        requireNotNull(source) { "InstallViewModel.kt was not found; the scan is looking at the wrong directory" }
        val text = source.readText()

        // The first mention of each: the success path's sweep, and the restart it has to precede.
        val sweep = text.indexOf("sweepStaging(app)")
        val restart = text.indexOf("RecoveryTool.SoftReboot")
        assertTrue("the run never sweeps its staging", sweep >= 0)
        assertTrue("the run never asks for the restart", restart >= 0)
        assertTrue(
            "the restart is asked for before the sweep, and the restart ends the process that sweeps",
            sweep < restart,
        )
    }

    @Test
    fun `a row's delete removes exactly the path that row names`() {
        // The row's own button goes through the same command as a sweep, so the quoting and the `rm -f`
        // are all that stand between a name in a list and the filesystem - and it must not be the
        // recursive form, which would take a directory this app never staged.
        val path = "/data/local/tmp/ksu_late_load.log"
        val command = StagingSweep.command(listOf(path))

        assertTrue(command.contains("rm -f -- '$path'"))
        assertFalse("a row's delete used the recursive form", command.contains("rm -rf"))
    }

    @Test
    fun `a delete of nothing is not a failure`() {
        // The screen can call this with a path list that came back empty, and "nothing to do" has to
        // stay distinct from "the shell refused": one is a no-op, the other is news for the log.
        val outcome = StagingSweep.remove(emptyList()) as SweepOutcome.Done

        assertEquals(SweepVerdict.NothingToDo, outcome.verdict)
        assertEquals(0, outcome.removed)
    }

    @Test
    fun `clearing asks what is there before and after, and deletes by glob rather than by name`() {
        // The difference from a sweep is the whole point of this command: a sweep names this app's own
        // paths, and a clear takes whatever is in the directory, so what it deletes cannot be written as
        // a list. Checked as the exact text, because the order is the report and only the text is true.
        val expected = """
            for e in /data/local/tmp/* /data/local/tmp/.[!.]*; do [ -e "${'$'}e" ] || continue; printf 'had %s\n' "${'$'}e"; done
            rm_out=${'$'}(rm -rf -- /data/local/tmp/* /data/local/tmp/.[!.]* 2>&1)
            [ -n "${'$'}rm_out" ] && printf 'said %s\n' "${'$'}rm_out"
            for e in /data/local/tmp/* /data/local/tmp/.[!.]*; do [ -e "${'$'}e" ] || continue; printf 'left %s\n' "${'$'}e"; done
            exit 0
        """.trimIndent()
        assertEquals(expected, StagingSweep.clearCommand())
    }

    @Test
    fun `clearing empties the directory without removing the directory`() {
        val command = StagingSweep.clearCommand()
        val delete = command.lineSequence().first { it.contains("rm -rf") }

        // Every target is a glob inside the directory...
        assertTrue(delete.contains("/data/local/tmp/*"))
        assertTrue(delete.contains("/data/local/tmp/.[!.]*"))
        // ...and the directory itself is not one of them: it belongs to the shell uid, with a mode an
        // app has no business rewriting, and a run stages into it again afterwards.
        assertFalse("the directory itself is a delete target", delete.contains("/data/local/tmp "))
        // Dot-names are staging markers here, so a clear that skipped them would leave the markers.
        assertTrue(command.trimEnd().endsWith("exit 0"))
    }

    @Test
    fun `what a sweep printed is read back as paths`() {
        val output = """
            rm: /data/local/tmp/temp_su.sock: Permission denied
            left /data/local/tmp/temp_su.sock
            had /data/local/tmp/ksu-helper
            had /data/local/tmp/ksu-payload
        """.trimIndent()

        // `rm`'s own complaint travels in the same stream and is not a path.
        assertEquals(
            listOf("/data/local/tmp/temp_su.sock"),
            StagingSweep.pathsIn(output, StagingSweep.LEFT_PREFIX),
        )
        assertEquals(
            listOf("/data/local/tmp/ksu-helper", "/data/local/tmp/ksu-payload"),
            StagingSweep.pathsIn(output, StagingSweep.FOUND_PREFIX),
        )
    }

    @Test
    fun `a path that survives the delete is not counted as removed`() {
        val outcome = SweepOutcome.Done(
            found = listOf("/a", "/b", "/c"),
            left = listOf("/c"),
            complaint = "",
        )
        assertEquals(SweepVerdict.LeftBehind, outcome.verdict)
        assertEquals(2, outcome.removed)
    }

    @Test
    fun `a refusal outranks a leftover, because only one of them is a fact about the device`() {
        val outcome = SweepOutcome.Done(
            found = listOf("/a", "/b"),
            left = emptyList(),
            // The socket is denied to the shell domain outright, so its own presence check cannot see
            // it and `rm`'s complaint is the only report there will ever be.
            complaint = "rm: /b: Permission denied",
        )
        assertEquals(SweepVerdict.Refused, outcome.verdict)
    }

    @Test
    fun `a sweep with nothing to sweep is not news`() {
        // The state the sweep exists to produce is not worth a line: one about an empty directory on
        // every launch is the noise that hides the lines that matter.
        val nothing = SweepOutcome.Done(
            found = emptyList(),
            left = emptyList(),
            complaint = "",
        )
        assertEquals(SweepVerdict.NothingToDo, nothing.verdict)
    }

    @Test
    fun `a sweep that deleted without complaint is the clean case`() {
        val outcome = SweepOutcome.Done(
            found = listOf("/a", "/b"),
            left = emptyList(),
            complaint = "",
        )
        assertEquals(SweepVerdict.Removed, outcome.verdict)
        assertEquals(2, outcome.removed)
    }

    @Test
    fun `a run's record describes a run only while its process is alive and its boot is this one`() {
        val holder = RunHolder(bootToken = "boot-a", pid = 4242)

        assertTrue(holder.holds(bootToken = "boot-a", alive = true))
        // The process died: a record outliving its run must not hold anything back.
        assertFalse(holder.holds(bootToken = "boot-a", alive = false))
        // Another boot's pid means nothing, whatever is at that number now.
        assertFalse(holder.holds(bootToken = "boot-b", alive = true))
        // A boot that could not be read matches nothing, which is the safe way round: no record is
        // treated as a run in flight, so nothing is refused a sweep for a phone that cannot be read.
        assertFalse(holder.holds(bootToken = null, alive = true))
    }

    @Test
    fun `a stored record that is not one is refused rather than guessed at`() {
        assertEquals(null, RunHolder.of(bootToken = null, pid = "12"))
        assertEquals(null, RunHolder.of(bootToken = "  ", pid = "12"))
        assertEquals(null, RunHolder.of(bootToken = "boot-a", pid = null))
        assertEquals(null, RunHolder.of(bootToken = "boot-a", pid = "not-a-pid"))
        assertEquals(null, RunHolder.of(bootToken = "boot-a", pid = "0"))
        assertEquals(null, RunHolder.of(bootToken = "boot-a", pid = "-3"))
        assertEquals(RunHolder("boot-a", 12), RunHolder.of(bootToken = " boot-a ", pid = " 12 "))
    }
}
