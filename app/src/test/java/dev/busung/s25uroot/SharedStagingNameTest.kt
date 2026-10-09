package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names in `/data/local/tmp` that both installs can write, and the rule that keeps them apart.
 *
 * The directory is one directory for the whole device - it is not per-app, and it is the only place a
 * shell can reach a file this app stages - so two installs of this app share it exactly as two apps share
 * a home screen. What they must not share is a file: the sweep deletes by name, and a name that means
 * "this app's payload" to one install means the same to the other, so one app's cleanup can take the file
 * the other app's run is about to execute.
 *
 * ## What is written down here, and how it was read
 *
 * [WRITTEN_BY_THE_OTHER_INSTALL] is not a guess. It is the list of paths the other app's own staging
 * code builds - five names, read off its `InstallViewModel` - and it is a fact about *that* app, which
 * is why it cannot be derived from this one. If the other app ever stages a sixth name this list is
 * what has to change, and the fixture is the only place that would know. Two of the five are not really
 * either app's: the payload's own loader reads the daemon at `ksud-s25u-kdp` and the stage copy at
 * `.ksud-stage`, so both installs must use them and neither may rename them.
 *
 * ## What this file covers, and what is next door
 *
 * The staging half: nothing this app writes may be a name the other install also writes. The sweep half
 * - that the names left alone are exactly the ones written down here - is [StagingSweepTest]'s, next to
 * the code that decides it, and the identity half is [InstallIdentityTest]'s.
 */
class SharedStagingNameTest {

    @Test
    fun `nothing stages a name the other install writes, apart from the payload's own`() {
        // Three of the five are the other app's alone - its helper, its payload and its log - and this
        // app stopped writing them when it moved its own names to the fork's prefix. One reappearing
        // here would be a run staging into a file the other install is using, which no test of this
        // app's own behaviour could notice: both apps work perfectly until they run at the same time.
        val offenders = stagedInSources()
            .filterNot {
                it.path in READ_ONLY_PATHS ||
                    it.name in PAYLOAD_OWNED_NAMES ||
                    isThisForksOwn(it.name)
            }

        assertEquals(
            "these stage into a name the app this fork came from also writes, so the two installs would " +
                "be fighting over one file - Veyra-owned names must stay outside upstream's staging names",
            emptyList<String>(),
            offenders.map(Staged::toString),
        )
    }

    @Test
    fun `the sweep leaves exactly the names the other install's own runs write`() {
        val alone = StagingSweep.removable(otherInstallPresent = false).map { it.name }.toSet()
        val withTheOtherInstall = StagingSweep.removable(otherInstallPresent = true).map { it.name }.toSet()

        // The whole difference the other install makes, and nothing else about the sweep may depend on
        // it: it is the five names that app stages, plus the socket the payload's daemon leaves behind,
        // which is nobody's to add to a list about *staging* - it is not staged at all, it is created.
        assertEquals(
            "the sweep is leaving something other than the paths the other install writes",
            WRITTEN_BY_THE_OTHER_INSTALL + PAYLOAD_SOCKET,
            alone - withTheOtherInstall,
        )
    }

    @Test
    fun `the names this app stopped writing are still read`() {
        // Not staging them is only half of it. A device that ran one of this fork's earlier builds has
        // them in the directory right now, and a catalogue that dropped them would report that device
        // clean while a detector reads its favourite names off it.
        val catalogued = StagedResidue.catalog.mapTo(HashSet()) { it.name }

        (WRITTEN_BY_THE_OTHER_INSTALL - PAYLOAD_OWNED_NAMES).forEach { name ->
            assertTrue(
                "$name is no longer staged by anything here and is no longer in the catalogue, so a " +
                    "device that still has one would be reported as clean",
                catalogued.contains(name),
            )
        }
    }

    /** One staged path as a shipped source writes it, and where it was written. */
    private class Staged(val file: File, val line: Int, val path: String) {
        val name: String get() = path.substringAfterLast('/')
        override fun toString(): String = "${file.path}:$line: $path"
    }

    /** Veyra-owned generations of names, with or without a leading dot marker. */
    private fun isThisForksOwn(name: String): Boolean {
        val plain = name.removePrefix(".")
        return plain in VEYRA_OWNED_EXACT || VEYRA_PREFIXES.any(plain::startsWith)
    }

    /**
     * Every path the shipped sources name under the directory, read the way [StagedResidueTest] reads
     * them.
     *
     * Blind to how a path is put together and blind to whether it is a literal in a `val` or text inside
     * a script, because the point is to catch the name wherever it is written - and the catalogue itself
     * is skipped, since naming the other install's paths is that file's job.
     */
    private fun stagedInSources(): List<Staged> {
        val staged = shippedSources()
            .filter { it.name != "StagedResidue.kt" }
            .flatMap { source ->
                val text = source.readText()
                STAGING_PATH.findAll(text).map { match ->
                    Staged(source, text.take(match.range.first).count { it == '\n' } + 1, match.value)
                }.toList()
            }

        // A scan that reads nothing satisfies the assertion above, and so does a rule with no prefix to
        // check - so both are failures of the fixture rather than passes of the test.
        assertTrue(
            "no staged paths were found; the scan is looking at the wrong directory",
            staged.isNotEmpty(),
        )
        assertTrue(
            "no Veyra-owned staged path was found, so the rule above is checking nothing",
            staged.any { isThisForksOwn(it.name) },
        )
        return staged
    }

    private fun shippedSources(): List<File> = listOf(File("src/main/java"), File("app/src/main/java"))
        .filter(File::isDirectory)
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .distinctBy { it.absolutePath }

    private companion object {

        /** Current and retained Veyra-owned staging namespaces. */
        val VEYRA_PREFIXES = setOf("rmgnext-", "veyra-", "cveyra-")
        val VEYRA_OWNED_EXACT = setOf("ksud-pixel")

        /** Paths mentioned as read-only inputs, not written by Veyra. */
        val READ_ONLY_PATHS = setOf("/data/local/tmp/boot.img")

        /**
         * What the app this fork came from stages, read off its own staging code.
         *
         * Five names, and no derivation is possible: they are that app's choices, and the only way to
         * know them is to read it. Recorded rather than fetched, because a test that reached GitHub
         * would fail on the day it could not, and this is a fact that changes when that app changes.
         */
        val WRITTEN_BY_THE_OTHER_INSTALL = setOf(
            "ksu-helper",
            "ksu-payload",
            "ksu-exploit.log",
            "ksud-s25u-kdp",
            ".ksud-stage",
        )

        /**
         * The names the payload owns, so both installs must use them and neither may move them.
         *
         * The socket is in here for a different reason from the other two: nothing stages it. The
         * payload's su daemon creates it, on whichever install started that daemon, which is what puts it
         * on the shared list - and it is not a name any staging code should be writing.
         */
        val PAYLOAD_OWNED_NAMES = setOf("ksud-s25u-kdp", ".ksud-stage", PAYLOAD_SOCKET)

        /** The one path no staging code creates: the daemon's own socket. */
        const val PAYLOAD_SOCKET = "temp_su.sock"

        /** The directory, then one name of unquoted characters - the same shape the catalogue uses. */
        val STAGING_PATH = Regex("""/data/local/tmp/[A-Za-z0-9._-]+""")
    }
}
