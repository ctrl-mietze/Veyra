package ctrl.mietze.veyraroot

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Date
import java.util.Hashtable
import java.util.Locale
import org.bouncycastle.asn1.x509.Certificate as Asn1Certificate
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.CertificateEntry
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SignatureAndHashAlgorithm
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto

/** Thrown when the device did not accept the pairing code. */
class AdbInvalidPairingCodeException :
    Exception("The pairing code was rejected; check the code shown in Developer options")

/**
 * Pairs with the device's own wireless debugging, so this app can reach adbd without a computer.
 *
 * The exchange is adbd's, in three steps: a TLS 1.3 session whose exported key material becomes part
 * of the password, a SPAKE2 message exchange that proves both sides know the pairing code, and an
 * encrypted PeerInfo carrying this app's public key - which is what the device stores in its
 * paired-device list. Nothing about the pairing code is sent over the wire; the code only ever enters
 * the arithmetic.
 *
 * Bouncy Castle's TLS client is used rather than Conscrypt because the exported key material has to be
 * readable at exactly the point AOSP reads it, and because newer Android releases restrict the
 * platform's own hidden TLS entry points.
 */
class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairCode: String,
    private val adbKey: AdbKeyManager,
) : Closeable {

    private lateinit var socket: Socket
    private lateinit var protocol: TlsClientProtocol
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream
    private lateinit var spake2: Spake2

    fun start(): Boolean {
        setUpTls()
        if (!exchangeSpake2Messages()) return false
        return exchangePeerInfo()
    }

    private fun setUpTls() {
        socket = Socket(host, port).apply { tcpNoDelay = true }

        val crypto = BcTlsCrypto(SecureRandom())
        protocol = TlsClientProtocol(socket.getInputStream(), socket.getOutputStream())

        var exportedKeyMaterial: ByteArray? = null

        // adbd's pairing server asks for a client certificate (SSL_VERIFY_PEER), so one has to exist
        // even though it is not what identifies us: the pairing code and the stored public key are.
        val privateKey = adbKey.privateKey
        val holder = X509v3CertificateBuilder(
            X500Name(CERTIFICATE_SUBJECT),
            BigInteger.ONE,
            Date(0),
            Date(CERTIFICATE_NOT_AFTER_MILLIS),
            Locale.ROOT,
            X500Name(CERTIFICATE_SUBJECT),
            SubjectPublicKeyInfo.getInstance(adbKey.publicKey.encoded),
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(privateKey))
        val bcCertificate = BcTlsCertificate(crypto, Asn1Certificate.getInstance(holder.encoded))
        val bcPrivateKey = PrivateKeyFactory.createKey(privateKey.encoded)

        val tlsClient = object : DefaultTlsClient(crypto) {
            override fun getProtocolVersions(): Array<ProtocolVersion> = arrayOf(ProtocolVersion.TLSv13)

            override fun getAuthentication(): TlsAuthentication = object : TlsAuthentication {
                /** adbd presents a self-signed certificate; nothing here can authenticate it. */
                override fun notifyServerCertificate(serverCertificate: TlsServerCertificate?) = Unit

                override fun getClientCredentials(
                    certificateRequest: CertificateRequest?,
                ): TlsCredentials {
                    val requestContext = certificateRequest?.certificateRequestContext ?: ByteArray(0)
                    val entry = CertificateEntry(bcCertificate, Hashtable<Any?, Any?>())
                    return BcDefaultTlsCredentialedSigner(
                        TlsCryptoParameters(context),
                        crypto,
                        bcPrivateKey,
                        Certificate(requestContext, arrayOf(entry)),
                        SignatureAndHashAlgorithm.rsa_pss_rsae_sha256,
                    )
                }
            }

            override fun notifyHandshakeComplete() {
                super.notifyHandshakeComplete()
                // The exported key material is only valid once the handshake has completed, which is
                // why the password cannot be built any earlier than this.
                exportedKeyMaterial = context.exportKeyingMaterial(
                    EXPORTED_KEY_LABEL,
                    null,
                    EXPORTED_KEY_BYTES,
                )
            }
        }

        protocol.connect(tlsClient)

        val keyMaterial = exportedKeyMaterial
            ?: error("The TLS session exported no key material, so the pairing password cannot be built")

        inputStream = DataInputStream(protocol.inputStream)
        outputStream = DataOutputStream(protocol.outputStream)

        // The password is the code plus the TLS key material: knowing the code is not enough without
        // having been in this session, and being in the session is not enough without the code.
        val codeBytes = pairCode.toByteArray()
        spake2 = Spake2(ByteArray(codeBytes.size + keyMaterial.size).also { password ->
            codeBytes.copyInto(password)
            keyMaterial.copyInto(password, codeBytes.size)
        })
    }

    private fun exchangeSpake2Messages(): Boolean {
        val message = spake2.ourMessage
        writeHeader(PAIRING_TYPE_SPAKE2, message.size)
        outputStream.write(message)
        outputStream.flush()

        val header = readHeader() ?: return false
        if (header.type != PAIRING_TYPE_SPAKE2) return false

        val theirMessage = ByteArray(header.payload)
        inputStream.readFully(theirMessage)
        return spake2.processTheirMessage(theirMessage)
    }

    private fun exchangePeerInfo(): Boolean {
        // The peer info block is fixed size, and byte zero names the key type: our public key follows.
        val peerInfo = ByteArray(PEER_INFO_BYTES)
        peerInfo[0] = 0 // ADB_RSA_PUB_KEY
        val publicKey = adbKey.adbPublicKey
        publicKey.copyInto(peerInfo, 1, 0, publicKey.size.coerceAtMost(PEER_INFO_BYTES - 1))

        val encrypted = spake2.encrypt(peerInfo)
        writeHeader(PAIRING_TYPE_PEER_INFO, encrypted.size)
        outputStream.write(encrypted)
        outputStream.flush()

        val header = readHeader() ?: return false
        if (header.type != PAIRING_TYPE_PEER_INFO) return false

        val theirEncrypted = ByteArray(header.payload)
        inputStream.readFully(theirEncrypted)

        // A payload that does not authenticate is the only signal a wrong code gives: the exchange
        // itself cannot fail earlier, because both sides hold a well-formed message either way.
        val decrypted = spake2.decrypt(theirEncrypted) ?: throw AdbInvalidPairingCodeException()
        return decrypted.size == PEER_INFO_BYTES
    }

    private data class PairingHeader(val type: Byte, val payload: Int)

    private fun writeHeader(type: Byte, payloadSize: Int) {
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
        header.put(HEADER_VERSION)
        header.put(type)
        header.putInt(payloadSize)
        outputStream.write(header.array())
    }

    /**
     * Reads a pairing header, or null when it is not one this client understands.
     *
     * The payload length is checked before anything is allocated from it: it arrives from the peer,
     * and a huge or negative value would otherwise be a chosen allocation.
     */
    private fun readHeader(): PairingHeader? {
        val bytes = ByteArray(HEADER_BYTES)
        inputStream.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get()
        val type = buffer.get()
        val payload = buffer.int
        if (version != HEADER_VERSION) return null
        if (type != PAIRING_TYPE_SPAKE2 && type != PAIRING_TYPE_PEER_INFO) return null
        if (payload <= 0 || payload > MAX_PAYLOAD_BYTES) return null
        return PairingHeader(type, payload)
    }

    override fun close() {
        runCatching { protocol.close() }
        runCatching { socket.close() }
    }

    companion object {
        private const val HEADER_VERSION: Byte = 1
        private const val HEADER_BYTES = 6
        private const val PEER_INFO_BYTES = 8192
        private const val MAX_PAYLOAD_BYTES = PEER_INFO_BYTES * 2
        private const val EXPORTED_KEY_LABEL = "adb-label\u0000"
        private const val EXPORTED_KEY_BYTES = 64
        private const val CERTIFICATE_SUBJECT = "CN=00"
        private const val CERTIFICATE_NOT_AFTER_MILLIS = 2461449600L * 1000

        private const val PAIRING_TYPE_SPAKE2: Byte = 0
        private const val PAIRING_TYPE_PEER_INFO: Byte = 1
    }
}
