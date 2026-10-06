package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a manifest is allowed to say about where its artifacts live.
 *
 * The rule is a list of repositories, and it is tested because getting it wrong does not look like a
 * rule failing: a catalog that names somewhere the list does not cover is refused *whole*, and the first
 * screen the app shows says "Support check failed" about a source that is perfectly reachable. That is
 * what this fork's own default source did - its feed is the upstream catalog with a different owner, so
 * every artifact URL in it names the upstream repository - and nothing but a test on the rule keeps it
 * from happening again the next time a feed is copied.
 */
class ArtifactPinTest {
    private val source = PayloadSource(PayloadSource.DEFAULT_REPOSITORY, PayloadSource.DEFAULT_BRANCH)
    private val commit = "a".repeat(40)

    @Test
    fun aSourceIsReadFromItself() {
        assertEquals(
            "https://raw.githubusercontent.com/${source.repository}/$commit/artifacts/pa3q-x/cve.so",
            pinnedArtifactUrl(
                source,
                "https://raw.githubusercontent.com/${source.repository}/main/artifacts/pa3q-x/cve.so",
                commit,
            ),
        )
    }

    @Test
    fun aFeedCopiedFromTheUpstreamCatalogStillReads() {
        // The path survives and the owner does not: the artifact is fetched from the repository the
        // manifest was read from, at the commit it was read at, so a fork that carries the files needs
        // no edit to the feed it was copied with - and nothing is ever taken from upstream at run time.
        assertEquals(
            "https://raw.githubusercontent.com/${source.repository}/$commit/artifacts/pa3q-x/cve.so",
            pinnedArtifactUrl(
                source,
                "https://raw.githubusercontent.com/${PayloadSource.LEGACY_REPOSITORY}/main/" +
                    "artifacts/pa3q-x/cve.so",
                commit,
            ),
        )
    }

    @Test
    fun aPinnedSourceKeepsTheCommitAndNotTheBranch() {
        val pinned = PayloadSource(source.repository, commit, pinnedCommit = commit)
        assertEquals(
            "https://raw.githubusercontent.com/${source.repository}/$commit/kernelsu/ksud-s25u-kdp",
            pinnedArtifactUrl(
                pinned,
                "https://raw.githubusercontent.com/${source.repository}/main/kernelsu/ksud-s25u-kdp",
                commit,
            ),
        )
    }

    @Test
    fun anywhereElseIsRefused() {
        val path = "artifacts/pa3q-x/cve.so"
        assertNull(pinnedArtifactUrl(source, "https://evil.example/$path", commit))
        assertNull(pinnedArtifactUrl(source, "https://raw.githubusercontent.com/someone-else/payloads/main/$path", commit))
        // Same repository, but named over a scheme the prefix does not carry, and over a branch that is
        // not the one the source was configured with. Neither is the URL this app wrote down.
        assertNull(pinnedArtifactUrl(source, "http://raw.githubusercontent.com/${source.repository}/main/$path", commit))
        assertNull(pinnedArtifactUrl(source, "https://raw.githubusercontent.com/${source.repository}/dev/$path", commit))
    }

    @Test
    fun theRuleNamesThreeRepositoriesAndNothingElse() {
        // The width of the rule, written down: three repositories, each one a catalog this app ships or
        // was copied from. A fourth would be a new place payloads can be fetched from, which is a decision
        // and not a detail.
        val prefixes = allowedMutableRawPrefixes(source)
        assertEquals(3, prefixes.size)
        prefixes.forEach { assertTrue(it, it.startsWith("https://raw.githubusercontent.com/")) }
        assertTrue(
            prefixes.any { it.contains(PayloadSource.LEGACY_REPOSITORY) },
        )
    }
}
