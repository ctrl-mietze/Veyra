package ctrl.mietze.veyraroot

import android.annotation.SuppressLint
import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * This app's own ADB identity: one RSA key used both to authenticate to adbd and to pair with it.
 *
 * Wireless debugging will not let a client in without a key it knows, so the app has to be its own
 * device - the same thing `~/.android/adbkey` is on a computer. Generating it once and keeping it in
 * app-private storage is what makes a pairing survive; a key regenerated each launch would pair, work
 * for a minute, and then be refused as an unknown client on the next connection.
 *
 * The TLS client certificate is self-signed from that same key, which is what adbd expects: it does
 * not validate the chain, it compares the key against the ones it has been paired with.
 */
class AdbKeyManager(context: Context) {

    private val keyDir = File(context.filesDir, KEY_DIR)
    private val keyPair: KeyPair

    /** The public half in ADB's own wire format, as sent when pairing. */
    val adbPublicKey: ByteArray

    val sslContext: SSLContext

    init {
        keyDir.mkdirs()
        keyPair = loadOrGenerate()
        adbPublicKey = encodeAdbPublicKey(keyPair.public as RSAPublicKey, KEY_COMMENT)
        sslContext = buildSslContext()
    }

    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
    val privateKey: RSAPrivateKey get() = keyPair.private as RSAPrivateKey

    private fun loadOrGenerate(): KeyPair {
        val privateFile = File(keyDir, PRIVATE_KEY_FILE)
        val publicFile = File(keyDir, PUBLIC_KEY_FILE)
        if (privateFile.exists() && publicFile.exists()) {
            val factory = KeyFactory.getInstance("RSA")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())) as RSAPublicKey,
                factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())) as RSAPrivateKey,
            )
        }
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(KEY_BITS)
        val generated = generator.generateKeyPair()
        privateFile.writeBytes(generated.private.encoded)
        publicFile.writeBytes(generated.public.encoded)
        return generated
    }

    private fun buildSslContext(): SSLContext {
        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val holder = X509v3CertificateBuilder(
            X500Name(CERTIFICATE_SUBJECT),
            BigInteger.ONE,
            Date(0),
            Date(CERTIFICATE_NOT_AFTER_MILLIS),
            Locale.ROOT,
            X500Name(CERTIFICATE_SUBJECT),
            SubjectPublicKeyInfo.getInstance(publicKey.encoded),
        ).build(signer)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(holder.encoded)) as X509Certificate

        val keyManager = object : X509ExtendedKeyManager() {
            override fun chooseClientAlias(
                keyTypes: Array<out String>?,
                issuers: Array<out Principal>?,
                socket: Socket?,
            ): String? = if (keyTypes?.any { it == "RSA" } == true) ALIAS else null

            override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
                if (alias == ALIAS) arrayOf(certificate) else null

            override fun getPrivateKey(alias: String?): PrivateKey? =
                if (alias == ALIAS) privateKey else null

            override fun getClientAliases(
                keyType: String?,
                issuers: Array<out Principal>?,
            ): Array<String>? = null

            override fun getServerAliases(
                keyType: String?,
                issuers: Array<out Principal>?,
            ): Array<String>? = null

            override fun chooseServerAlias(
                keyType: String?,
                issuers: Array<out Principal>?,
                socket: Socket?,
            ): String? = null
        }

        // adbd neither presents a trusted chain nor asks to be verified: authentication is the client
        // certificate it checks against its own paired keys, which is why accepting the server side
        // here is not a weakening of anything - there is no server identity to check that would make
        // the connection more trustworthy than the key exchange that established the pairing.
        val trustManager = @SuppressLint("TrustAllX509TrustManager")
        object : X509ExtendedTrustManager() {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
                socket: Socket?,
            ) = Unit

            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
                engine: SSLEngine?,
            ) = Unit

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
                socket: Socket?,
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
                engine: SSLEngine?,
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        return SSLContext.getInstance("TLSv1.3").apply {
            init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
        }
    }

    companion object {
        private const val KEY_DIR = "adb_keys"
        const val PRIVATE_KEY_FILE = "adb_private.der"
        const val PUBLIC_KEY_FILE = "adb_public.der"
        private const val ALIAS = "key"
        private const val KEY_BITS = 2048
        private const val KEY_COMMENT = "rootmygalaxy@localhost"
        private const val CERTIFICATE_SUBJECT = "CN=00"
        private const val CERTIFICATE_NOT_AFTER_MILLIS = 2461449600L * 1000

        private const val MODULUS_BYTES = 2048 / 8
        private const val MODULUS_WORDS = MODULUS_BYTES / 4
        private const val WIRE_KEY_BYTES = 524

        /**
         * Encodes an RSA public key in Android's ADB wire format, the same one `adbkey.pub` holds.
         *
         * adbd parses this structure rather than the key's own DER, so the word count, the modulus in
         * 32-bit little-endian words, `r^2 mod n` and the Montgomery inverse have to be exactly the
         * shape it expects - which is why this is written out rather than delegated to a library.
         */
        fun encodeAdbPublicKey(publicKey: RSAPublicKey, name: String): ByteArray {
            val r32 = BigInteger.ZERO.setBit(32)
            val n0inv = publicKey.modulus.remainder(r32).modInverse(r32).negate()
            val r = BigInteger.ZERO.setBit(MODULUS_BYTES * 8)
            val rr = r.modPow(BigInteger.valueOf(2), publicKey.modulus)

            val buffer = ByteBuffer.allocate(WIRE_KEY_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(MODULUS_WORDS)
            buffer.putInt(n0inv.toInt())
            publicKey.modulus.toAdbWords().forEach { buffer.putInt(it) }
            rr.toAdbWords().forEach { buffer.putInt(it) }
            buffer.putInt(publicKey.publicExponent.toInt())

            val encoded = Base64.getEncoder().encode(buffer.array())
            val nameBytes = " $name\u0000".toByteArray()
            return ByteArray(encoded.size + nameBytes.size).also { bytes ->
                encoded.copyInto(bytes)
                nameBytes.copyInto(bytes, encoded.size)
            }
        }

        private fun BigInteger.toAdbWords(): IntArray {
            val words = IntArray(MODULUS_WORDS)
            val r32 = BigInteger.ZERO.setBit(32)
            var remaining = this.add(BigInteger.ZERO)
            for (index in 0 until MODULUS_WORDS) {
                val (quotient, remainder) = remaining.divideAndRemainder(r32)
                remaining = quotient
                words[index] = remainder.toInt()
            }
            return words
        }
    }
}
