package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactIntegrityTest {

    private val hashOfAbc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun computedHashIsTheLowercaseHexAManifestWouldDeclare() {
        assertEquals(hashOfAbc, sha256Hex("abc".toByteArray()))
        assertTrue(isSha256(sha256Hex("anything".toByteArray())))
    }

    @Test
    fun onlyAFullLowercaseHashCountsAsDeclared() {
        assertTrue(isSha256(hashOfAbc))
        assertFalse(isSha256(hashOfAbc.uppercase()))
        assertFalse(isSha256(hashOfAbc.dropLast(1)))
        assertFalse(isSha256(hashOfAbc + "0"))
        assertFalse(isSha256(""))
        assertFalse(isSha256("sha256:$hashOfAbc"))
        assertFalse(isSha256("z".repeat(64)))
    }

    @Test
    fun aDeclaredHashTakesOverFromTheSizeCheck() {
        // A hash proves the content and therefore the length as well, so a feed that states one no
        // longer has to state a size it can stand behind, and no longer needs to turn checking off.
        val verified = RemoteArtifact("https://example.invalid/exploit", 4096, sha256 = hashOfAbc)
        val sizeOnly = RemoteArtifact("https://example.invalid/exploit", 4096)
        val unchecked = RemoteArtifact("https://example.invalid/exploit", 0, verifySize = false)
        val uncheckedWithHash =
            RemoteArtifact("https://example.invalid/exploit", 0, verifySize = false, sha256 = hashOfAbc)

        assertFalse(verified.checksSize)
        assertTrue(sizeOnly.checksSize)
        assertFalse(unchecked.checksSize)
        assertFalse(uncheckedWithHash.checksSize)
        assertNull(sizeOnly.sha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEnforcedSizeHasToBePositive() {
        RemoteArtifact("https://example.invalid/exploit", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aHashThatIsNotALowercaseSha256IsRefused() {
        RemoteArtifact("https://example.invalid/exploit", 4096, sha256 = "not-a-hash")
    }

    @Test
    fun theManifestReadsBothArtifactsTheSameWay() {
        // The escape hatch and the hash are per artifact, so one payload's ksud can be pinned by
        // hash while its exploit is not, and a manifest that omits the fields keeps the strict
        // defaults rather than inheriting whatever the other artifact declared.
        val parsed = SupportManifest.parse(
            """
            {
              "schemaVersion": 3,
              "payloads": [
                {
                  "payloadId": "e2s-S926BXXUEDZDR",
                  "displayName": "Galaxy S25",
                  "models": ["SM-S931B"],
                  "kernelVersions": ["6.6.98"],
                  "exploit": {
                    "url": "https://raw.githubusercontent.com/example/feed/main/exploit.so",
                    "size": 4096,
                    "sha256": "$hashOfAbc"
                  },
                  "kernelsu": {
                    "url": "https://raw.githubusercontent.com/example/feed/main/ksud",
                    "size": 8192
                  }
                }
              ]
            }
            """.trimIndent().toByteArray(),
        )

        val profile = parsed.targets.single()
        assertEquals(hashOfAbc, profile.exploit.sha256)
        assertFalse(profile.exploit.checksSize)
        assertNull(profile.kernelSu.sha256)
        assertTrue(profile.kernelSu.checksSize)
    }
}
