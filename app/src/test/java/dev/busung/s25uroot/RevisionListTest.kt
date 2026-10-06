package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HEAD = "6e3223e689688540060ddd97a0927e927bfed207"
private const val PREVIOUS = "6dc16c2f11f0a6f1a3c1c6a2f2b3f0c9a1b2c3d4"

/**
 * The shape `github.com/{owner}/{repo}/commits/{ref}.atom` actually serves, trimmed to what the
 * parser reads. This is the route the app prefers, because unlike the REST API it is not limited to
 * 60 requests an hour per address.
 */
private val ATOM_FEED = """
    <?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US">
      <id>tag:github.com,2008:/o/r/commits/main</id>
      <title>Recent Commits to r:main</title>
      <updated>2026-09-03T03:23:27Z</updated>
      <entry>
        <id>tag:github.com,2008:Grit::Commit/$HEAD</id>
        <link type="text/html" rel="alternate" href="https://github.com/o/r/commit/$HEAD"/>
        <title>
            Merge pull request #285 from E-R-Butch/feat/q4q-f9360zcsaizf1
        </title>
        <updated>2026-09-03T03:23:27Z</updated>
        <author><name>BuSung-dev</name></author>
      </entry>
      <entry>
        <id>tag:github.com,2008:Grit::Commit/$PREVIOUS</id>
        <title>add x710 profile</title>
        <updated>2026-09-02T11:00:00Z</updated>
        <author><name>someone</name></author>
      </entry>
    </feed>
""".trimIndent()

class RevisionListTest {

    @Test
    fun `a revision summary reads the revision, not the branch`() {
        val url = revisionManifestUrl("BuSung-dev/Root-My-Galaxy-Payloads", HEAD)

        // The one property a summary of a *revision* depends on: it describes the revision the user
        // is looking at, and not wherever the branch happens to be.
        assertTrue(url.contains("/$HEAD/"))
        assertTrue(url.endsWith(MANIFEST_PATH))
        assertFalse(url.contains("/main/"))
    }

    @Test
    fun `the atom feed lists commits with their sha, first line and date`() {
        val revisions = parseCommitAtom(ATOM_FEED)

        assertEquals(2, revisions.size)
        assertEquals(HEAD, revisions[0].commit)
        assertEquals("Merge pull request #285 from E-R-Butch/feat/q4q-f9360zcsaizf1", revisions[0].label)
        assertEquals("2026-09-03", revisions[0].date)
        assertEquals(PREVIOUS, revisions[1].commit)
        assertEquals("add x710 profile", revisions[1].label)
    }

    @Test
    fun `the first atom entry is the ref head`() {
        // This is what makes resolution possible without the API: the newest entry of a ref is the
        // commit that ref points at.
        assertEquals(HEAD, parseCommitAtom(ATOM_FEED, limit = 1).firstOrNull()?.commit)
    }

    @Test
    fun `an atom title is one line`() {
        val label = parseCommitAtom(ATOM_FEED)[0].label

        assertTrue(label.none { it == '\n' || it == '\r' })
        assertTrue(!label.contains("  "))
    }

    @Test
    fun `an atom feed that is not a feed is an empty list`() {
        assertTrue(parseCommitAtom("").isEmpty())
        assertTrue(parseCommitAtom("<html><body>Not Found</body></html>").isEmpty())
        assertTrue(parseCommitAtom("<feed><entry><title>x</title></entry></feed>").isEmpty())
    }

    @Test
    fun `an atom entry without a full commit is dropped`() {
        val feed = "<feed><entry><id>Grit::Commit/6e3223e</id><title>abbrev</title></entry></feed>"

        assertTrue(parseCommitAtom(feed).isEmpty())
    }

    @Test
    fun `a commit is listed with its first line, its date and its sha`() {
        val json = """
            [
              {
                "sha": "$HEAD",
                "commit": {
                  "message": "Merge pull request #285\n\nbody that must not be a label",
                  "committer": {"date": "2026-09-03T03:23:27Z"}
                }
              }
            ]
        """.trimIndent()

        val revisions = parseCommits(json)

        assertEquals(1, revisions.size)
        assertEquals(HEAD, revisions[0].commit)
        assertEquals("Merge pull request #285", revisions[0].label)
        assertEquals("2026-09-03", revisions[0].date)
        assertEquals(null, revisions[0].tag)
    }

    @Test
    fun `order is kept, because the first commit is the ref head`() {
        val json = listOf(HEAD, PREVIOUS).joinToString(",", "[", "]") { sha ->
            """{"sha":"$sha","commit":{"message":"m","committer":{"date":"2026-09-03T00:00:00Z"}}}"""
        }

        assertEquals(listOf(HEAD, PREVIOUS), parseCommits(json).map { it.commit })
    }

    @Test
    fun `a revision that is not a full commit is dropped`() {
        // Listing one would offer a pin that cannot be resolved again.
        val json = """
            [
              {"sha":"6e3223e","commit":{"message":"abbrev","committer":{"date":"2026-09-03T00:00:00Z"}}},
              {"sha":"$HEAD","commit":{"message":"real","committer":{"date":"2026-09-03T00:00:00Z"}}}
            ]
        """.trimIndent()

        assertEquals(listOf(HEAD), parseCommits(json).map { it.commit })
    }

    @Test
    fun `an error page is an empty list rather than a crash`() {
        assertTrue(parseCommits("Not Found").isEmpty())
        assertTrue(parseCommits("""{"message":"API rate limit exceeded"}""").isEmpty())
        assertTrue(parseCommits("").isEmpty())
    }

    @Test
    fun `the list is capped at what was asked for`() {
        val json = (1..30).joinToString(",", "[", "]") { index ->
            val sha = index.toString(16).padStart(40, '0')
            """{"sha":"$sha","commit":{"message":"m$index","committer":{"date":"2026-09-03T00:00:00Z"}}}"""
        }

        assertEquals(15, parseCommits(json, limit = 15).size)
    }

    @Test
    fun `a tag carries the commit it points at, which is what gets pinned`() {
        val json = """[{"name":"v1.2.3","commit":{"sha":"$HEAD"}}]"""

        val tags = parseTags(json)

        assertEquals(1, tags.size)
        assertEquals("v1.2.3", tags[0].tag)
        assertEquals("v1.2.3", tags[0].label)
        assertEquals(HEAD, tags[0].commit)
    }

    @Test
    fun `an empty tag list is normal, not a failure`() {
        assertTrue(parseTags("[]").isEmpty())
        assertTrue(parseTags("Not Found").isEmpty())
    }

    @Test
    fun `a label stops being a label before it becomes a paragraph`() {
        val message = "x".repeat(400)
        val json = """[{"sha":"$HEAD","commit":{"message":"$message","committer":{"date":"2026-09-03T00:00:00Z"}}}]"""

        assertEquals(96, parseCommits(json)[0].label.length)
    }
}
