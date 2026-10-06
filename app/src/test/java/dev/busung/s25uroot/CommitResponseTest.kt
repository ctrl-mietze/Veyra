package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val COMMIT = "6e3223e689688540060ddd97a0927e927bfed207"

class CommitResponseTest {

    @Test
    fun `the bare sha the media type asks for is the commit`() {
        assertEquals(COMMIT, parseCommitResponse(COMMIT))
    }

    @Test
    fun `a trailing newline does not spoil it`() {
        assertEquals(COMMIT, parseCommitResponse("$COMMIT\n"))
        assertEquals(COMMIT, parseCommitResponse("  $COMMIT  \r\n"))
    }

    @Test
    fun `a server that ignored the media type is still understood`() {
        val json = """{"sha":"$COMMIT","commit":{"message":"update payloads"}}"""

        assertEquals(COMMIT, parseCommitResponse(json))
    }

    @Test
    fun `an error page is not a commit`() {
        assertNull(parseCommitResponse("Not Found"))
        assertNull(parseCommitResponse("""{"message":"API rate limit exceeded"}"""))
        assertNull(parseCommitResponse(""))
        assertNull(parseCommitResponse("   "))
    }

    @Test
    fun `a shortened sha is refused rather than trusted`() {
        // A seven-character abbreviation is what GitHub *displays*; a source pinned to one would be
        // a source that cannot be resolved again.
        assertNull(parseCommitResponse("6e3223e"))
        assertNull(parseCommitResponse("""{"sha":"6e3223e"}"""))
    }

    @Test
    fun `a truncated body is refused`() {
        assertNull(parseCommitResponse(COMMIT.take(39)))
        assertNull(parseCommitResponse("""{"sha":"$COMMIT""""))
    }
}
