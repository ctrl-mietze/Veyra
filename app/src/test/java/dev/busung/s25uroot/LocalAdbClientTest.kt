package ctrl.mietze.veyraroot

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transport's two contracts that can be checked without a device: what a shell reply means, and
 * whether the public key this app sends adbd is the shape adbd parses.
 */
class LocalAdbClientTest {

    private val marker = LocalAdbClient.SHELL_EXIT_MARKER

    @Test
    fun `the exit marker is what makes a shell result readable`() {
        val result = adbShellResult("hello\n${marker}0\n")

        assertEquals(0, result.exitCode)
        assertEquals("hello", result.output)
    }

    @Test
    fun `a failing command keeps its exit code and its output`() {
        val result = adbShellResult("cannot open\n${marker}1\n")

        assertEquals(1, result.exitCode)
        assertEquals("cannot open", result.output)
    }

    @Test
    fun `a reply without a marker is unknown, never success`() {
        // The failure this prevents: reading "no answer" as "worked" and letting a run continue on a
        // step that never happened.
        val result = adbShellResult("something went wrong\n")

        assertEquals(LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE, result.exitCode)
        assertNotEquals(0, result.exitCode)
    }

    @Test
    fun `a malformed marker is unknown too`() {
        val result = adbShellResult("output\n${marker}not-a-number\n")

        assertEquals(LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE, result.exitCode)
    }

    @Test
    fun `the last marker wins, because a command may print one of its own`() {
        // A command that echoes the marker's text must not be able to decide its own exit code.
        val result = adbShellResult("${marker}99\nreal output\n${marker}7\n")

        assertEquals(7, result.exitCode)
        assertEquals("${marker}99\nreal output", result.output)
    }

    @Test
    fun `a token frame with no token is refused, not dereferenced`() {
        // adbd always sends the twenty bytes, so this is the case where something else answered on the
        // port - and the failure has to be a sentence rather than a blank NPE in the log.
        val refusal = assertThrows(IllegalStateException::class.java) { adbAuthTokenToSign(null) }
        assertTrue(refusal.message!!.contains("no token"))
    }

    @Test
    fun `a token frame with a token is signed as it came`() {
        val token = ByteArray(20) { it.toByte() }
        assertEquals(token, adbAuthTokenToSign(token))
    }

    @Test
    fun `a shell in the shell domain is usable`() {
        val shell = "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input)" +
            " context=u:r:shell:s0"

        assertEquals(null, localAdbShellIdentityFailure(LocalAdbClient.ShellResult(0, shell)))
    }

    @Test
    fun `the right user in the wrong domain is refused`() {
        // The failure this prevents: adbd authenticates, the push succeeds, and the payload then fails
        // on the first thing that needs tracefs - reported as the exploit's fault, not the shell's.
        val shell = "uid=2000(shell) gid=2000(shell) context=u:r:untrusted_app:s0:c512,c768"

        val reason = localAdbShellIdentityFailure(LocalAdbClient.ShellResult(0, shell))

        assertNotEquals(null, reason)
        assertTrue(reason!!.contains("u:r:shell:s0"))
    }

    @Test
    fun `root is not a shell this transport may run a payload through`() {
        val shell = "uid=0(root) gid=0(root) context=u:r:shell:s0"

        assertNotEquals(null, localAdbShellIdentityFailure(LocalAdbClient.ShellResult(0, shell)))
    }

    @Test
    fun `a shell that did not answer is refused before its output is read`() {
        val reason = localAdbShellIdentityFailure(
            LocalAdbClient.ShellResult(LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE, ""),
        )

        assertEquals("the shell did not answer (exit -1)", reason)
    }

    @Test
    fun `the public key is sent in adb's own wire format`() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val publicKey = generator.generateKeyPair().public as RSAPublicKey

        val encoded = AdbKeyManager.encodeAdbPublicKey(publicKey, "rootmygalaxy@localhost")
        val text = String(encoded)

        assertTrue(text.startsWith("QAAAA"))
        assertTrue(text.trimEnd('\u0000').endsWith(" rootmygalaxy@localhost"))
        // Base64 of the fixed-size structure plus " name\0", which is what adbd reads before the name.
        val base64 = text.substringBefore(' ')
        assertEquals(524, Base64.getDecoder().decode(base64).size)
    }

    @Test
    fun `the same key encodes the same way twice, and a different key does not`() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val first = generator.generateKeyPair().public as RSAPublicKey
        val second = generator.generateKeyPair().public as RSAPublicKey

        assertEquals(
            String(AdbKeyManager.encodeAdbPublicKey(first, "n")),
            String(AdbKeyManager.encodeAdbPublicKey(first, "n")),
        )
        assertNotEquals(
            String(AdbKeyManager.encodeAdbPublicKey(first, "n")),
            String(AdbKeyManager.encodeAdbPublicKey(second, "n")),
        )
    }

    @Test
    fun `the wire key carries the modulus and exponent, not a DER blob`() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val publicKey = generator.generateKeyPair().public as RSAPublicKey

        val bytes = Base64.getDecoder().decode(
            String(AdbKeyManager.encodeAdbPublicKey(publicKey, "n")).substringBefore(' '),
        )

        // Word count, Montgomery inverse, the modulus, r^2 -- each as little-endian words -- and the
        // exponent. The modulus is rebuilt from those words and compared to the real key, because a
        // wrong byte order here is a key adbd will reject rather than a key it will read.
        assertEquals(64, readInt(bytes, 0))
        assertFalse(readInt(bytes, 4) == 0)
        assertEquals(publicKey.modulus, decodeLittleEndianWords(bytes, offset = 8, words = 64))
        assertEquals(65537, readInt(bytes, 520))
    }

    /** A big-endian BigInteger from [words] little-endian 32-bit words, the way ADB lays them out. */
    private fun decodeLittleEndianWords(bytes: ByteArray, offset: Int, words: Int): BigInteger {
        val littleEndian = ByteArray(words * 4)
        for (index in 0 until words) {
            val word = readInt(bytes, offset + index * 4)
            for (byte in 0..3) {
                littleEndian[index * 4 + byte] = ((word shr (8 * byte)) and 0xFF).toByte()
            }
        }
        return BigInteger(1, littleEndian.reversedArray())
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
