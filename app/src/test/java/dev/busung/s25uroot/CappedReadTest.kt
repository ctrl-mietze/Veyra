package ctrl.mietze.veyraroot

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much of an answer this app will read.
 *
 * The bug these exist for was not a parser and not a URL: a version listing was read under a single
 * release's ceiling, so both flavours answered "could not read the published versions" while the app
 * was holding a perfectly good answer it had refused itself. A ceiling is therefore worth a test of its
 * own - both that it reads a page of releases whole, and that it says why when it does not.
 */
class CappedReadTest {

    private fun body(bytes: Int): ByteArrayInputStream =
        ByteArrayInputStream(ByteArray(bytes) { 'x'.code.toByte() })

    @Test
    fun `a body under the ceiling is read whole`() {
        assertEquals(120_000, readCappedText(body(120_000), MAX_RELEASE_BYTES).length)
    }

    @Test
    fun `a body over the ceiling is refused, naming the ceiling`() {
        // The message is shown, so it has to say what the limit was: a refusal with no reason is what
        // made this look like a broken app rather than a limit.
        val failure = runCatching { readCappedText(body(MAX_RELEASE_BYTES + 1), MAX_RELEASE_BYTES) }

        assertTrue(failure.isFailure)
        assertTrue(
            failure.exceptionOrNull()?.message.orEmpty().contains("1 MB"),
        )
    }

    @Test
    fun `a listing is not held to one release's ceiling`() {
        // Ten releases of KernelSU-Next measured 2.1 MB - every entry carries its changelog - and thirty
        // of KernelSU measured 5.2 MB. The listing ceiling has to leave room for the page it asks for,
        // which is why the two ceilings are separate and why this ratio is asserted rather than assumed.
        assertTrue(MAX_LISTING_BYTES >= MAX_RELEASE_BYTES * 5)

        val listing = readCappedText(body(3 * 1024 * 1024), MAX_LISTING_BYTES)
        assertEquals(3 * 1024 * 1024, listing.length)
    }
}
