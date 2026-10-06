package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadSourcesTest {
    private val official = PayloadSource.DEFAULT
    private val community = PayloadSource("example-org/payloads", "testing", enabled = false)

    @Test
    fun defaultSourceIsUsable() {
        assertTrue(official.enabled)
        assertEquals("rushiranpise/Root-My-Galaxy-Payloads@main", official.id)
    }

    @Test
    fun whatIsTypedIsTakenAsWritten() {
        // Deliberately no format rule on the field. A pattern cannot tell a repository that exists from
        // one that does not, and a rule that refuses a half-typed owner is the app arguing with a form
        // nobody has finished - the read that adding performs is what can tell, and it says what it got.
        assertEquals(
            PayloadSource("example-org/payloads", "main"),
            PayloadSource.create("  example-org/payloads  ", " main "),
        )
        assertEquals("rushiranpise", PayloadSource.create("rushiranpise", "main")?.repository)
        assertEquals("owner/name/extra", PayloadSource.create("owner/name/extra", "main")?.repository)
        assertEquals("with space", PayloadSource.create("example-org/payloads", "with space")?.branch)
        assertNull(PayloadSource.create("", "main"))
        assertNull(PayloadSource.create("   ", "main"))
        assertNull(PayloadSource.create("example-org/payloads", ""))
    }

    @Test
    fun selectionIdKeepsSourcesApartWhenTheyOfferTheSamePayload() {
        val payload = "galaxy-s25-series-kernel-6.6.98"

        val officialSelection = selectionIdFor(official.id, payload)
        val communitySelection = selectionIdFor(community.id, payload)
        assertEquals(officialSelection, selectionIdFor(official.id, payload))
        assertFalse(officialSelection == communitySelection)
        assertEquals(official.id, sourceFromSelectionId(officialSelection))
        assertEquals(community.id, sourceFromSelectionId(communitySelection))
        assertEquals(payload, profileFromSelectionId(officialSelection))
        assertEquals(payload, profileFromSelectionId(communitySelection))
    }

    @Test
    fun unqualifiedSelectionStaysAPlainProfileId() {
        assertEquals("plain", selectionIdFor("", "plain"))
        assertNull(sourceFromSelectionId("plain"))
        assertEquals("plain", profileFromSelectionId("plain"))
    }

    @Test
    fun catalogEditsAddRemoveAndToggleSources() {
        var sources = listOf(official)

        sources = sources.withSourceAdded(community)
        assertEquals(listOf(official, community), sources)

        sources = sources.withSourceAdded(PayloadSource(community.repository, community.branch))
        assertEquals(2, sources.size)

        sources = sources.withSourceEnabled(community.id, true)
        assertEquals(listOf(official, community.copy(enabled = true)), sources)
        assertEquals(2, sources.enabledSources().size)

        sources = sources.withSourceEnabled(official.id, false)
        assertEquals(listOf(community.id), sources.enabledSources().map { it.id })

        sources = sources.withSourceRemoved(official.id)
        assertEquals(listOf(community.id), sources.map { it.id })
        assertTrue(sources.single().enabled)
    }

    @Test
    fun pastingAFullCommitPinsTheSourceInsteadOfFollowingIt() {
        val sha = "4f9a2c1d5b8e7a6c3f2e1d0c9b8a7f6e5d4c3b2a"

        val pinned = PayloadSource.create("example-org/payloads", sha)

        assertEquals(sha, pinned?.pinnedCommit)
        assertTrue(pinned?.isPinned == true)
        // The ref is kept as the provenance of the pin, and the id names the revision, not the ref.
        assertEquals(sha, pinned?.branch)
        assertEquals("example-org/payloads@$sha", pinned?.id)
    }

    @Test
    fun aTagOrBranchIsFollowedRatherThanPinned() {
        val tag = PayloadSource.create("example-org/payloads", "v1.2.3")
        val branch = PayloadSource.create("example-org/payloads", "feature/multi-source")

        assertFalse(tag?.isPinned == true)
        assertEquals("example-org/payloads@v1.2.3", tag?.id)
        assertEquals("example-org/payloads@feature/multi-source", branch?.id)
    }

    @Test
    fun pinningFreezesARevisionAndUnpinningReturnsToTheBranch() {
        val sha = "4f9a2c1d5b8e7a6c3f2e1d0c9b8a7f6e5d4c3b2a"
        val sources = listOf(PayloadSource("example-org/payloads", "testing"))

        val pinned = sources.withSourcePinned(sources.single().id, sha).single()
        assertEquals(sha, pinned.pinnedCommit)
        assertEquals("testing at 4f9a2c1", pinned.refLabel)
        assertEquals("example-org/payloads @ testing @ 4f9a2c1", pinned.label)

        val unpinned = listOf(pinned).withSourceUnpinned(pinned.id).single()
        assertEquals("", unpinned.pinnedCommit)
        assertEquals("testing", unpinned.refLabel)

        // A missing id changes nothing rather than dropping or duplicating a source.
        assertEquals(listOf(pinned), listOf(pinned).withSourcePinned("other/repo@main", sha))
    }

    @Test
    fun aPinnedRevisionAndItsBranchCanBeConfiguredTogether() {
        val sha = "4f9a2c1d5b8e7a6c3f2e1d0c9b8a7f6e5d4c3b2a"
        val branch = PayloadSource("example-org/payloads", "testing")
        val pinned = branch.copy(pinnedCommit = sha)

        assertFalse(branch.id == pinned.id)
        assertEquals(listOf(branch, pinned), listOf(branch).withSourceAdded(pinned))
        // Adding the same pin twice is still refused.
        assertEquals(2, listOf(branch, pinned).withSourceAdded(pinned.copy(enabled = false)).size)
    }

    @Test
    fun onlyAFullCommitCountsAsAPin() {
        assertTrue(PayloadSource.isCommitValid("4f9a2c1d5b8e7a6c3f2e1d0c9b8a7f6e5d4c3b2a"))
        assertFalse(PayloadSource.isCommitValid("4f9a2c1"))
        assertFalse(PayloadSource.isCommitValid("4F9A2C1D5B8E7A6C3F2E1D0C9B8A7F6E5D4C3B2A"))
        assertFalse(PayloadSource.isCommitValid("main"))
    }

    @Test
    fun aTargetWithoutASourceStillResolvesToItsOwnId() {
        val profile = TargetProfile(
            profileId = "galaxy-s25-series-kernel-6.6.98",
            displayName = "Galaxy S25 series",
            models = setOf("SM-S931B"),
            kernelVersions = setOf("6.6.98"),
            exploit = RemoteArtifact("https://example.invalid/exploit", 1),
            kernelSu = RemoteArtifact("https://example.invalid/ksud", 1),
        )

        assertEquals(profile.profileId, profile.selectionId)

        val sourced = profile.copy(sourceId = community.id, sourceLabel = community.label)
        assertEquals(selectionIdFor(community.id, profile.profileId), sourced.selectionId)
        assertEquals(community.id, sourceFromSelectionId(sourced.selectionId))
        assertEquals(profile.profileId, profileFromSelectionId(sourced.selectionId))
    }
}
