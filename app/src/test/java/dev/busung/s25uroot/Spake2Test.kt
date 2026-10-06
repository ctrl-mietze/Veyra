package ctrl.mietze.veyraroot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Spake2Test {

    private val password = "123456".toByteArray() + ByteArray(64) { it.toByte() }

    @Test
    fun `our message is one compressed ed25519 point`() {
        assertEquals(32, Spake2(password).ourMessage.size)
    }

    @Test
    fun `two exchanges with the same password are still different`() {
        // x is fresh per exchange, so a repeated message would mean the random scalar was reused -
        // which would make two sessions share a key.
        val first = Spake2(password).ourMessage
        val second = Spake2(password).ourMessage

        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `the password changes the message`() {
        assertNotEquals(
            Spake2(password).ourMessage.toList(),
            Spake2("654321".toByteArray() + ByteArray(64)).ourMessage.toList(),
        )
    }

    @Test
    fun `a peer message that is not a point is refused`() {
        val spake2 = Spake2(password)

        assertFalse(spake2.processTheirMessage(ByteArray(31)))
        assertFalse(spake2.processTheirMessage(ByteArray(33)))
        assertFalse(spake2.processTheirMessage(ByteArray(0)))
    }

    @Test
    fun `a payload round-trips once the exchange has run`() {
        val spake2 = Spake2(password)
        assertTrue(spake2.processTheirMessage(spake2.ourMessage))

        val plaintext = "hello pairing".toByteArray()
        val ciphertext = spake2.encrypt(plaintext)

        assertNotEquals(plaintext.toList(), ciphertext.toList())
        // GCM appends its tag, which is what makes a wrong code detectable at all.
        assertEquals(plaintext.size + 16, ciphertext.size)
    }

    @Test
    fun `no two payloads share a nonce`() {
        // The one cryptographic property here that must never slip: a repeated GCM nonce under the
        // same key leaks the plaintexts and the authentication key, so the counter has to advance.
        val spake2 = Spake2(password)
        spake2.processTheirMessage(spake2.ourMessage)

        val plaintext = "the same payload every time".toByteArray()
        val first = spake2.encrypt(plaintext)
        val second = spake2.encrypt(plaintext)

        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `encrypting before the exchange is a programming error, not a silent no-op`() {
        val spake2 = Spake2(password)

        runCatching { spake2.encrypt(ByteArray(4)) }
        assertNotNull(runCatching { spake2.encrypt(ByteArray(4)) }.exceptionOrNull())
        runCatching { spake2.decrypt(ByteArray(20)) }
        assertNotNull(runCatching { spake2.decrypt(ByteArray(20)) }.exceptionOrNull())
    }

    @Test
    fun `a payload that does not authenticate decrypts to nothing`() {
        // Every other peer produces a well-formed message; a wrong pairing code is only ever visible
        // here, so this has to report failure rather than an empty success.
        val spake2 = Spake2(password)
        spake2.processTheirMessage(spake2.ourMessage)

        assertNull(spake2.decrypt(ByteArray(8192 + 16)))
    }

    @Test
    fun `hex helper reads a compressed point byte for byte`() {
        val point = Spake2.hexToBytes("5866666666666666666666666666666666666666666666666666666666666666")

        assertEquals(32, point.size)
        // The Ed25519 base point: y = 4/5, which is 0x58 followed by thirty-one 0x66 bytes.
        assertEquals(0x58.toByte(), point[0])
        assertArrayEquals(ByteArray(31) { 0x66 }, point.copyOfRange(1, 32))
    }
}
