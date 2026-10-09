package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check's two contracts: how a stat's errno is read, and that the catalog is the whole list.
 *
 * The errno rule is the one that decides whether a denied read can ever be shown as a clean device,
 * and the catalog rule is the one that decides whether a file this app writes can be missing from the
 * list - either of which would make the check report good news about residue that is still there. Both
 * are testable without a device, which is why they are the two halves that were built that way: the
 * `Context`-free part of the reading is [StagedResidue.readingFor], and the catalog is checked against
 * the sources the same way [ManifestPermissionTest] checks permissions against the manifest.
 */
class StagedResidueTest {

    @Test
    fun `a file that is not there is the only thing read as an absence`() {
        assertEquals(ResidueReading.Gone, StagedResidue.readingFor(ENOENT))
        // A path that is not a directory is the same answer arrived at differently: the name is simply
        // not there to be found.
        assertEquals(ResidueReading.Gone, StagedResidue.readingFor(ENOTDIR))
    }

    @Test
    fun `a denied read is never reported as an absence`() {
        // The failure this prevents: an app that cannot see /data/local/tmp reporting a clean device,
        // which is the same statement as a device with nothing on it and the opposite of the truth.
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(EACCES))
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(EPERM))
    }

    @Test
    fun `an errno this app has never seen is no answer either`() {
        // The default is the point, and it has to survive an errno nobody anticipated: anything that is
        // not "no such file" must come back as unknown rather than as good news.
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(ELOOP))
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(Int.MIN_VALUE))
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(0))
    }

    @Test
    fun `a readable reading that found nothing is not a blind one`() {
        // The two have to look different, because one is a device with nothing left on it and the other
        // is a check that cannot see. Reporting the second as the first is the whole risk of this code.
        val clean = ResidueReport(
            findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Gone) },
            directoryVisible = true,
        )
        assertFalse(clean.blind)
        assertTrue(clean.present.isEmpty())

        val blind = ResidueReport(
            findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Unreadable) },
            directoryVisible = false,
        )
        assertTrue(blind.blind)
    }

    @Test
    fun `one readable path is enough to make a reading that is not blind`() {
        // A reading can be mixed: the files this app owns are world-readable while a root-owned script
        // is not stat'able, and the answer for the list as a whole is still a real one.
        val mixed = ResidueReport(
            findings = StagedResidue.catalog.mapIndexed { index, staged ->
                ResidueFinding(
                    staged,
                    if (index == 0) {
                        ResidueReading.Present(sizeBytes = 10L, modifiedAtMillis = 0L)
                    } else {
                        ResidueReading.Unreadable
                    },
                )
            },
            directoryVisible = true,
        )
        assertFalse(mixed.blind)
        assertEquals(1, mixed.present.size)
        assertEquals(10L, mixed.totalBytes)
    }

    @Test
    fun `the totals are what the list shows`() {
        val staged = StagedResidue.catalog.first()
        val other = StagedResidue.catalog.last()
        val report = ResidueReport(
            findings = listOf(
                ResidueFinding(staged, ResidueReading.Present(2_000L, 1_000L)),
                ResidueFinding(other, ResidueReading.Present(3_000L, 500L)),
                ResidueFinding(StagedResidue.catalog[1], ResidueReading.Gone),
            ),
            directoryVisible = true,
        )
        assertEquals(5_000L, report.totalBytes)
        // The oldest, not the newest: it is the one that says how long this has been accumulating.
        assertEquals(500L, report.oldestMillis)
    }

    @Test
    fun `an unreadable path is not counted in the size`() {
        val report = ResidueReport(
            findings = listOf(
                ResidueFinding(StagedResidue.catalog[0], ResidueReading.Unreadable),
                ResidueFinding(StagedResidue.catalog[1], ResidueReading.Present(1_500L, 0L)),
            ),
            directoryVisible = true,
        )
        // A path whose size nobody could read contributes nothing rather than a zero, so the total is
        // a floor on what is there and never a claim that something empty is.
        assertEquals(1_500L, report.totalBytes)
        assertEquals(1, report.present.size)
    }

    @Test
    fun `an entry this app did not stage is never a clean reading`() {
        // The reading this whole half exists for: the directory holds something, none of it is this
        // app's, and the card has to say so rather than "Nothing left".
        val others = listed(
            extras = listOf(TempEntry("uipref.xml", ResidueReading.Present(1_200L, 0L))),
        )
        assertEquals(ResidueVerdict.Others, others.verdict)
        assertTrue(others.present.isEmpty())
        assertEquals(1, others.extras.size)

        // Both halves at once, which is the ordinary state after a run that has not been swept.
        val both = ResidueReport(
            findings = listOf(
                ResidueFinding(StagedResidue.catalog.first(), ResidueReading.Present(21_464L, 0L)),
            ),
            directoryVisible = true,
            extras = listOf(TempEntry("uipref.xml", ResidueReading.Present(1_200L, 0L))),
            directoryListed = true,
        )
        assertEquals(ResidueVerdict.StagedAndOthers, both.verdict)
    }

    @Test
    fun `a directory that could not be listed is a weaker clean than an empty one`() {
        // "Nothing left" is a claim about the directory; a check taken by name may only claim the
        // names it asked about. One device answers the first and the other the second.
        val weaker = byName(emptyList())
        assertEquals(ResidueVerdict.CleanByName, weaker.verdict)
        assertFalse(weaker.directoryListed)

        assertEquals(ResidueVerdict.Clean, listed(extras = emptyList()).verdict)
    }

    @Test
    fun `an extra is enough to keep a reading that could not read the catalog from being blind`() {
        // Blind means "this app can answer nothing". A listing that saw names did answer - and one of
        // those names being visible is worth more than every catalogued path being denied.
        val deniedButListed = ResidueReport(
            findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Unreadable) },
            directoryVisible = true,
            extras = listOf(TempEntry("ksu_late_load.log", ResidueReading.Unreadable)),
            directoryListed = true,
        )
        assertFalse(deniedButListed.blind)
        assertEquals(ResidueVerdict.Others, deniedButListed.verdict)

        // With nothing listed and nothing readable it is still blind, which is the case that must not
        // be mistaken for a device with nothing on it.
        assertTrue(blindReading().blind)
    }

    @Test
    fun `an entry's path is its name under the directory the check reads`() {
        // The listing gives a name and a row's delete needs a path, so the two have to be put together
        // the one way this app puts paths together - the same way the catalog's are.
        val entry = TempEntry("uipref.xml", ResidueReading.Present(337L, 0L))

        assertEquals("${StagedResidue.DIRECTORY}/uipref.xml", entry.path)
    }

    @Test
    fun `the listing is read from the shell's own marked names`() {
        // The marker is what separates an empty directory from a shell that never got as far as listing
        // it: without it, a refusal would read as a directory with nothing in it.
        assertEquals(
            listOf("ksu-helper", ".ksud-stage"),
            StagedDirectory.namesIn("has ksu-helper\nhas .ksud-stage\nlisted\n"),
        )
        assertEquals(
            emptyList<String>(),
            StagedDirectory.namesIn("listed\n"),
        )
        // A shell's own complaint travels in the same stream and is not a name.
        assertNull(StagedDirectory.namesIn("ls: /data/local/tmp: Permission denied\n"))
    }

    @Test
    fun `the listing command names the directory once and prints under a prefix`() {
        val command = StagedDirectory.command()
        assertTrue(command.contains("${StagedResidue.DIRECTORY}/*"))
        // Dot-names are staging markers here, so they have to be in the glob.
        assertTrue(command.contains("${StagedResidue.DIRECTORY}/.[!.]*"))
        assertTrue(command.contains(StagedDirectory.NAME_PREFIX))
        assertTrue(command.contains(StagedDirectory.LISTED_MARK))
    }

    /** A reading of a device nothing could be found on by name, because the shell never answered. */
    private fun byName(extras: List<TempEntry>) = ResidueReport(
        findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Gone) },
        directoryVisible = true,
        extras = extras,
        directoryListed = false,
    )

    /** The same device with every catalogued path denied as well, which is the blind reading. */
    private fun blindReading() = ResidueReport(
        findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Unreadable) },
        directoryVisible = true,
    )

    /** A reading of a device where the directory was listed, with nothing of this app's in it. */
    private fun listed(extras: List<TempEntry>) = ResidueReport(
        findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Gone) },
        directoryVisible = true,
        extras = extras,
        directoryListed = true,
    )

    @Test
    fun `a size reads the way a file manager would print it`() {
        assertEquals("512 B", StagedResidue.sizeLabel(512L))
        assertEquals("21 KB", StagedResidue.sizeLabel(21_464L))
        assertEquals("5.1 MB", StagedResidue.sizeLabel(5_095_160L))
        assertEquals("0 B", StagedResidue.sizeLabel(0L))
    }

    @Test
    fun `an age is counted in whole days, and the oldest thing says so plainly`() {
        val now = 1_800_000_000_000L
        val day = 86_400_000L
        assertEquals("today", StagedResidue.ageLabelOf(now, now))
        assertEquals("yesterday", StagedResidue.ageLabelOf(now - day, now))
        assertEquals("3 days", StagedResidue.ageLabelOf(now - 3 * day, now))
        assertEquals("a week", StagedResidue.ageLabelOf(now - 8 * day, now))
        assertEquals("6 weeks", StagedResidue.ageLabelOf(now - 42 * day, now))
        assertEquals("3 months", StagedResidue.ageLabelOf(now - 100 * day, now))
        // A timestamp in the future is a clock that moved, not a file from tomorrow.
        assertEquals("today", StagedResidue.ageLabelOf(now + 5 * day, now))
        assertEquals("unknown", StagedResidue.ageLabelOf(null, now))
    }

    @Test
    fun `every path in the catalog is under the directory the check reads`() {
        assertTrue(StagedResidue.catalog.isNotEmpty())
        StagedResidue.catalog.forEach { staged ->
            assertTrue(
                "${staged.path} is not under ${StagedResidue.DIRECTORY}",
                staged.path.startsWith("${StagedResidue.DIRECTORY}/"),
            )
            assertTrue("${staged.path} is not a name", staged.name.isNotBlank())
        }
    }

    @Test
    fun `the catalog lists nothing twice`() {
        // A duplicate would be a row counted twice in the summary, and would hide the path it was
        // meant to be alongside.
        val paths = StagedResidue.catalog.map { it.path }
        assertEquals(paths.size, paths.distinct().size)
    }

    @Test
    fun `every path the app stages is in the catalog`() {
        val sources = sourceFiles()
        assertTrue("no sources were found; the scan is looking at the wrong directory", sources.isNotEmpty())
        val staged = sources
            .filter { it.name != "StagedResidue.kt" }
            .flatMap { source ->
                STAGING_PATH.findAll(source.readText()).map { match -> match.value }.toList()
            }
            .distinct()
            // These are intentionally not residue owned by the cleanup catalog:
            // boot.img is only a user/shell-provided read candidate, while .cveyra- is the
            // persistent authenticated broker directory and must survive normal residue cleanup.
            .filterNot { it in NON_RESIDUE_PATHS }
        assertTrue("the scan found no staged paths at all", staged.isNotEmpty())

        val catalogued = StagedResidue.catalog.map { it.path }.toSet()
        val missing = staged.filterNot { catalogued.contains(it) }.sorted()
        assertEquals(
            "these paths are staged by the app but are not in StagedResidue.catalog, so the check " +
                "would report a device with nothing on it while they are sitting there",
            emptyList<String>(),
            missing,
        )
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)

    private companion object {
        /**
         * A staged path as the sources write it: the directory, then one name of unquoted characters.
         *
         * Deliberately blind to how the path is put together - a literal, or a constant the staging
         * code refers to - because the point is to catch the name wherever it is written.
         */
        val STAGING_PATH = Regex("""/data/local/tmp/[A-Za-z0-9._-]+""")
        val NON_RESIDUE_PATHS = setOf(
            "/data/local/tmp/boot.img",
            "/data/local/tmp/.cveyra-",
        )

        /*
         * The errno numbers written out, rather than read from `OsConstants`.
         *
         * A local unit test is compiled against the mockable `android.jar` AGP generates, in which
         * every platform constant is zeroed - so `OsConstants.EACCES` is 0 here and would make these
         * assertions test nothing but each other. The numbers are the ones Linux and the device use,
         * and writing them down is what makes this test able to disagree with the app's own use of the
         * platform constants: ENOENT 2, EACCES 13, EPERM 1, ELOOP 40, ENOTDIR 20.
         */
        const val ENOENT = 2
        const val ENOTDIR = 20
        const val EACCES = 13
        const val EPERM = 1
        const val ELOOP = 40
    }
}
