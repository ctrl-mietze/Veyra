package ctrl.mietze.veyraroot

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The client half of SPAKE2 over Ed25519, which is what ADB's wireless pairing authenticates with.
 *
 * It is written out here rather than taken from a library because adbd's side is BoringSSL's
 * `spake25519` and AOSP's `pairing_auth`: a textbook SPAKE2 that computes the same shared point but a
 * different transcript, or a different key derivation, produces a pairing code that is always
 * rejected with no way to tell why. So the shape is BoringSSL's - the mask points `M` and `N`, the
 * "clear the low three bits" adjustment to the password scalar, the length-prefixed transcript, and
 * AOSP's HKDF label.
 *
 * This is the client (`spake2_role_alice`). The password is the pairing code followed by the TLS
 * exported key material, so a party that does not hold the TLS session cannot complete the exchange
 * even knowing the code.
 */
class Spake2(private val password: ByteArray) {

    private val random = SecureRandom()

    // BoringSSL's spake25519 mask points, compressed Ed25519: M masks for alice, and N unmasks the
    // peer's message.
    private val mPoint = hexToBytes("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e")
    private val nPoint = hexToBytes("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778")

    /** The prime-order subgroup order, L. */
    private val groupOrder = BigInteger(
        "7237005577332262213973186563042994240857116359379907606001950938285454250989",
    )

    // ADB's pairing names include the NUL, because AOSP sends them with C's sizeof().
    private val clientName = "adb pair client\u0000".toByteArray()
    private val serverName = "adb pair server\u0000".toByteArray()

    private val x: ByteArray
    private val w: ByteArray
    private val passwordHash: ByteArray

    /** Our SPAKE2 message, T. */
    val ourMessage: ByteArray

    private var aesKey: ByteArray? = null
    private var encryptionSequence: Long = 0
    private var decryptionSequence: Long = 0

    init {
        passwordHash = MessageDigest.getInstance("SHA-512").digest(password)

        // x = 64 random bytes reduced mod L, with the cofactor cleared by multiplying by 8.
        val randBytes = ByteArray(64)
        random.nextBytes(randBytes)
        val scalar = BigInteger(1, randBytes.reversedArray()).mod(groupOrder).shiftLeft(3)
        x = bigToLittleEndian32(scalar)

        w = passwordToScalar(passwordHash)

        // T = x*B + w*M
        ourMessage = ed25519PointAdd(
            ed25519ScalarMult(x, basePoint),
            ed25519ScalarMult(w, mPoint),
        )
    }

    /**
     * Mixes the peer's message in and derives the session key.
     *
     * Returns false for a message of the wrong size, which is the one thing that can be checked about
     * it before the arithmetic: a wrong pairing code produces a perfectly valid message and only fails
     * later, when the peer's PeerInfo will not decrypt.
     */
    fun processTheirMessage(theirMessage: ByteArray): Boolean {
        if (theirMessage.size != POINT_BYTES) return false

        // K = x * (S - w*N)
        val wN = ed25519ScalarMult(w, nPoint).copyOf()
        wN[POINT_BYTES - 1] = (wN[POINT_BYTES - 1].toInt() xor 0x80).toByte() // negate the point
        val k = ed25519ScalarMult(x, ed25519PointAdd(theirMessage, wN))

        val transcript = MessageDigest.getInstance("SHA-512")
        transcript.updateLengthPrefixed(clientName)
        transcript.updateLengthPrefixed(serverName)
        transcript.updateLengthPrefixed(ourMessage)
        transcript.updateLengthPrefixed(theirMessage)
        transcript.updateLengthPrefixed(k)
        transcript.updateLengthPrefixed(passwordHash)
        val keyMaterial = transcript.digest()

        aesKey = hkdfSha256(keyMaterial, HKDF_INFO.toByteArray(), KEY_BYTES)
        return true
    }

    /**
     * Encrypts one pairing payload.
     *
     * The nonce is a little-endian counter, as AOSP's pairing uses, so the two sides stay in step
     * without exchanging anything; a repeated nonce with GCM would be a break, which is why the
     * counter is part of this class rather than something a caller passes in.
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val key = aesKey ?: error("The SPAKE2 exchange has not completed")
        val nonce = nonceFor(encryptionSequence++)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(MAC_BITS, nonce))
        return cipher.doFinal(plaintext)
    }

    /** Decrypts one pairing payload; null when it does not authenticate, which is a wrong code. */
    fun decrypt(ciphertext: ByteArray): ByteArray? {
        val key = aesKey ?: error("The SPAKE2 exchange has not completed")
        val nonce = nonceFor(decryptionSequence++)
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(MAC_BITS, nonce))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    private fun nonceFor(sequence: Long): ByteArray = ByteArray(NONCE_BYTES).also { nonce ->
        for (index in 0 until SEQUENCE_BYTES) {
            nonce[index] = ((sequence shr (index * 8)) and 0xFF).toByte()
        }
    }

    /**
     * w = SHA512(password) reduced mod L, then BoringSSL's low-bit adjustment: L, 2L and 4L are added
     * when the corresponding bit is set, which clears the low three bits and makes w a multiple of 8.
     */
    private fun passwordToScalar(hash: ByteArray): ByteArray {
        var w = BigInteger(1, hash.reversedArray()).mod(groupOrder)
        var order = groupOrder
        if (w.testBit(0)) w = w.add(order)
        order = order.shiftLeft(1)
        if (w.testBit(1)) w = w.add(order)
        order = order.shiftLeft(1)
        if (w.testBit(2)) w = w.add(order)
        return bigToLittleEndian32(w)
    }

    private fun ed25519ScalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        var bits = BigInteger(1, scalar.reversedArray())
        if (bits == BigInteger.ZERO) return identityPoint
        var result = identityPoint
        var addend = point
        while (bits > BigInteger.ZERO) {
            if (bits.testBit(0)) result = ed25519PointAdd(result, addend)
            addend = ed25519PointAdd(addend, addend)
            bits = bits.shiftRight(1)
        }
        return result
    }

    /** Point addition in affine coordinates on -x^2 + y^2 = 1 + d*x^2*y^2 over p = 2^255 - 19. */
    private fun ed25519PointAdd(p: ByteArray, q: ByteArray): ByteArray {
        val (x1, y1) = decompress(p)
        val (x2, y2) = decompress(q)
        val dxy = d.multiply(x1).multiply(x2).multiply(y1).multiply(y2).mod(fieldP)
        val x3 = x1.multiply(y2).add(y1.multiply(x2)).mod(fieldP)
            .multiply(BigInteger.ONE.add(dxy).mod(fieldP).modInverse(fieldP))
            .mod(fieldP)
        val y3 = y1.multiply(y2).add(x1.multiply(x2)).mod(fieldP)
            .multiply(BigInteger.ONE.subtract(dxy).mod(fieldP).modInverse(fieldP))
            .mod(fieldP)
        return compress(x3, y3)
    }

    private fun decompress(encoded: ByteArray): Pair<BigInteger, BigInteger> {
        val yBytes = encoded.copyOf()
        val sign = (yBytes[31].toInt() shr 7) and 1
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
        val y = BigInteger(1, yBytes.reversedArray())
        val y2 = y.multiply(y).mod(fieldP)
        val x2 = y2.subtract(BigInteger.ONE).mod(fieldP)
            .multiply(d.multiply(y2).add(BigInteger.ONE).mod(fieldP).modInverse(fieldP))
            .mod(fieldP)
        var x = x2.modPow(fieldP.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), fieldP)
        if (x.multiply(x).mod(fieldP) != x2) x = x.multiply(sqrtM1).mod(fieldP)
        if (x.testBit(0) != (sign == 1)) x = fieldP.subtract(x)
        return x to y
    }

    private fun compress(x: BigInteger, y: BigInteger): ByteArray {
        val encoded = y.toByteArray().reversedArray()
        val result = ByteArray(POINT_BYTES)
        encoded.copyInto(result, 0, 0, minOf(encoded.size, POINT_BYTES))
        if (x.testBit(0)) result[POINT_BYTES - 1] = (result[POINT_BYTES - 1].toInt() or 0x80).toByte()
        return result
    }

    private fun bigToLittleEndian32(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        val result = ByteArray(POINT_BYTES)
        val source = if (bytes.size > POINT_BYTES) {
            bytes.copyOfRange(bytes.size - POINT_BYTES, bytes.size)
        } else {
            bytes
        }
        for (index in source.indices) result[index] = source[source.size - 1 - index]
        return result
    }

    private fun MessageDigest.updateLengthPrefixed(data: ByteArray) {
        val length = ByteArray(SEQUENCE_BYTES)
        var remaining = data.size.toLong()
        for (index in 0 until SEQUENCE_BYTES) {
            length[index] = (remaining and 0xFF).toByte()
            remaining = remaining ushr 8
        }
        update(length)
        update(data)
    }

    private fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(ByteArray(32), "HmacSHA256")) // salt: 32 zero bytes
        val prk = hmac.doFinal(ikm)
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        hmac.update(info)
        hmac.update(0x01.toByte())
        return hmac.doFinal().copyOf(length)
    }

    companion object {
        private const val POINT_BYTES = 32
        private const val KEY_BYTES = 16
        private const val MAC_BITS = 128
        private const val NONCE_BYTES = 12
        private const val SEQUENCE_BYTES = 8
        private const val HKDF_INFO = "adb pairing_auth aes-128-gcm key"

        private val identityPoint = ByteArray(POINT_BYTES).also { it[0] = 1 }
        private val basePoint = hexToBytes(
            "5866666666666666666666666666666666666666666666666666666666666666",
        )
        private val fieldP = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
        private val d = BigInteger("-121665")
            .multiply(BigInteger("121666").modInverse(fieldP))
            .mod(fieldP)
        private val sqrtM1 = BigInteger.valueOf(2).modPow(
            fieldP.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)),
            fieldP,
        )

        internal fun hexToBytes(hex: String): ByteArray {
            val data = ByteArray(hex.length / 2)
            for (index in 0 until hex.length step 2) {
                data[index / 2] =
                    ((Character.digit(hex[index], 16) shl 4) + Character.digit(hex[index + 1], 16)).toByte()
            }
            return data
        }
    }
}
