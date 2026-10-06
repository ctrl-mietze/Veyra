package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which manager version the app offers, from the three facts that can decide one.
 *
 * The order is the whole rule and it is what these walk: a version the user named cannot be taken back
 * by anything the app resolves, the payload's KernelSU is offered when nothing is named (it is the
 * daemon the next run stages, so its own manager is the only one certainly built against it), and the
 * flavour's own release is what is left when neither says.
 *
 * [ManagerOffer.assetNameKnown] is asserted beside each one because it decides whether a tap needs a
 * request at all: only the flavour's release has a file name the app knows.
 */
class ManagerOfferTest {

    @Test
    fun `a version the user named wins over the payload`() {
        val offer = managerOffer(named = "3.1.2", payload = "3.4.0", builtIn = "3.4.0")

        assertEquals("3.1.2", offer.version)
        assertEquals(ManagerOfferOrigin.Named, offer.origin)
        assertFalse(offer.assetNameKnown)
    }

    @Test
    fun `the payload's own KernelSU is offered when nothing is named`() {
        val offer = managerOffer(named = null, payload = "3.4.0", builtIn = "3.3.0")

        assertEquals("3.4.0", offer.version)
        assertEquals(ManagerOfferOrigin.Payload, offer.origin)
        // The APK inside a release carries a build number its version does not, so this one is found by
        // asking for the release rather than assembled from the version.
        assertFalse(offer.assetNameKnown)
    }

    @Test
    fun `a payload on the release the flavour already names still needs no lookup`() {
        // The usual case on this project's own feed, and the reason the check is on the value rather
        // than on which fact produced it.
        val offer = managerOffer(named = null, payload = "3.4.0", builtIn = "3.4.0")

        assertEquals(ManagerOfferOrigin.Payload, offer.origin)
        assertTrue(offer.assetNameKnown)
    }

    @Test
    fun `nothing declared falls back to the flavour's own release`() {
        val offer = managerOffer(named = null, payload = null, builtIn = "3.3.0")

        assertEquals("3.3.0", offer.version)
        assertEquals(ManagerOfferOrigin.BuiltIn, offer.origin)
        assertTrue(offer.assetNameKnown)
    }

    @Test
    fun `blank is nothing rather than a version`() {
        // Both stored facts are strings a preference file or a feed can hand back empty, and an empty
        // version would reach the screen as a download that cannot be named at all.
        val blankName = managerOffer(named = "   ", payload = "3.4.0", builtIn = "3.3.0")
        assertEquals(ManagerOfferOrigin.Payload, blankName.origin)

        val blankPayload = managerOffer(named = null, payload = "", builtIn = "3.3.0")
        assertEquals("3.3.0", blankPayload.version)
        assertEquals(ManagerOfferOrigin.BuiltIn, blankPayload.origin)

        // And what is there is trimmed, because a value typed into a field arrives with the spaces
        // somebody left around it.
        assertEquals("3.4.0", managerOffer(named = " 3.4.0 ", payload = null, builtIn = "3.3.0").version)
    }

    @Test
    fun `naming the flavour's own version keeps the download a single tap`() {
        val offer = managerOffer(named = "3.3.0", payload = null, builtIn = "3.3.0")

        assertEquals(ManagerOfferOrigin.Named, offer.origin)
        assertTrue(offer.assetNameKnown)
    }

    /**
     * The offer reads a record, so a decision point that does not write one leaves the manager row
     * offering the previous payload's release - silently, and with a version that looks plausible.
     *
     * These are the three moments the app decides what this device will run: a run resolving its
     * payload, a payload picked in the target sheet, and the offline cache being loaded. A fourth is a
     * change this test is meant to ask about rather than to discover on a phone.
     */
    @Test
    fun `every moment a payload is decided records the KernelSU it stages`() {
        val sites = mapOf(
            "src/main/java/dev/busung/s25uroot/PayloadRepository.kt" to
                "rememberResolvedPayload(context, resolved)",
            "src/main/java/dev/busung/s25uroot/OfflinePayloadStore.kt" to
                "rememberResolvedPayload(context, cached.profile())",
            "src/main/java/dev/busung/s25uroot/MainActivity.kt" to
                "rememberResolvedPayload(context, profile)",
        )

        for ((path, call) in sites) {
            assertTrue(
                "$path decides a payload without recording the KernelSU it stages",
                source(path).contains(call),
            )
        }
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
