package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the manager chooser can offer, read from a releases listing.
 *
 * The shape matters more than the parsing: what comes back has to be the tag this app would ask for when
 * it looks up a download (`releases/tags/v<version>`), or the list is a menu of things that fail when
 * picked.
 */
class ManagerVersionsTest {

    @Test
    fun `a tag loses its v and keeps the order the API gave`() {
        val listing = """
            [
              {"tag_name": "v3.3.0", "draft": false},
              {"tag_name": "v3.2.5", "draft": false},
              {"tag_name": "v3.2.4", "draft": false}
            ]
        """.trimIndent()

        assertEquals(listOf("3.3.0", "3.2.5", "3.2.4"), managerVersionsInReleases(listing))
    }

    @Test
    fun `a draft is skipped and a prerelease is not`() {
        // A draft's tag is not published, so offering it would be offering a lookup that cannot work. A
        // prerelease is a release like any other: its own tag says what it is.
        val listing = """
            [
              {"tag_name": "v4.0.0", "draft": true},
              {"tag_name": "v3.4.0-rc1", "draft": false},
              {"tag_name": "v3.3.0", "draft": false}
            ]
        """.trimIndent()

        assertEquals(listOf("3.4.0-rc1", "3.3.0"), managerVersionsInReleases(listing))
    }

    @Test
    fun `the same version is offered once`() {
        // Two releases can carry one version - a re-run of a failed release job leaves two - and a list
        // with it twice would look like two different choices.
        val listing = """
            [
              {"tag_name": "v3.2.5", "draft": false},
              {"tag_name": "v3.2.5", "draft": false},
              {"tag_name": "3.2.5", "draft": false}
            ]
        """.trimIndent()

        assertEquals(listOf("3.2.5"), managerVersionsInReleases(listing))
    }

    @Test
    fun `a release with no tag is not a version`() {
        val listing = """
            [
              {"tag_name": "", "draft": false},
              {"draft": false},
              {"tag_name": "v3.2.5", "draft": false}
            ]
        """.trimIndent()

        assertEquals(listOf("3.2.5"), managerVersionsInReleases(listing))
    }

    @Test
    fun `an answer that is not a listing fails rather than reading as empty`() {
        // What a rate limit looks like, and the difference is the whole reason the field is still there:
        // "could not read" and "this project has published nothing" need different answers from the user.
        val rateLimited = """{"message": "API rate limit exceeded for 1.2.3.4."}"""

        val failed = runCatching { managerVersionsInReleases(rateLimited) }
        assertTrue(failed.isFailure)
    }
}
