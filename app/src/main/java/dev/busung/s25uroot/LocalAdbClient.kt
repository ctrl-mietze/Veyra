package ctrl.mietze.veyraroot

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Signature
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * A minimal ADB client for the device's own `adbd`, reached over local wireless debugging.
 *
 * This exists because the app has transports that can be absent at the worst moment. A Shizuku binder
 * needs root or a computer to create, and the bootstrap helper's socket only exists during the window
 * it was staged in - while the device's own wireless debugging is a shell (`u:r:shell:s0`) that the
 * user can enable from Developer options with no cable and no root. It is the transport of last
 * resort, and the only one that works on a device that has neither.
 *
 * Both handshakes adbd accepts are supported: the STLS upgrade that wireless debugging requires,
 * where the app's own key is presented as a client certificate, and the older RSA token exchange.
 */
class LocalAdbClient(
    private val host: String,
    private val port: Int,
    private val keyManager: AdbKeyManager,
) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainInput: DataInputStream
    private lateinit var plainOutput: DataOutputStream
    private var useTls = false
    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsInput: DataInputStream
    private lateinit var tlsOutput: DataOutputStream

    // A dedicated reader drains complete ADB messages into this queue, so a read is never interrupted
    // mid-message. That matters because a shell here runs a fifteen-minute exploit: a caller that
    // polls with a timeout must not be able to cut a message in half.
    private val messageQueue = LinkedBlockingQueue<AdbMessage>()
    @Volatile private var readerError: Throwable? = null
    private var readerThread: Thread? = null

    /**
     * Writes run on their own thread so each can be bounded by [WRITE_TIMEOUT_MS].
     *
     * `soTimeout` bounds reads only. A silently dead transport - a Wi-Fi power-save black hole, a
     * wedged adbd - can otherwise block a 24-byte header forever with the buffers already full.
     */
    private val writeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "adb-writer").apply { isDaemon = true }
    }

    // A WRTE consumed by writeSync while hunting for its OKAY ack, kept so the push loop cannot lose
    // a result that arrived early.
    private var pendingMessage: AdbMessage? = null

    private val inputStream get() = if (useTls) tlsInput else plainInput
    private val outputStream get() = if (useTls) tlsOutput else plainOutput

    /** Connects and authenticates to adbd, upgrading to TLS when it asks for it. */
    fun connect() {
        socket = Socket()
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket.tcpNoDelay = true
        socket.soTimeout = READ_TIMEOUT_MS
        plainInput = DataInputStream(socket.getInputStream().buffered())
        plainOutput = DataOutputStream(socket.getOutputStream().buffered())

        write(A_CNXN, A_VERSION, A_MAXDATA, "host::features=cmd,shell_v2")
        var message = read()

        if (message.command == A_STLS) {
            write(A_STLS, A_STLS_VERSION, 0)
            tlsSocket = keyManager.sslContext
                .socketFactory
                .createSocket(socket, host, port, true) as SSLSocket
            try {
                tlsSocket.startHandshake()
                // TLS 1.3 delivers adbd's verdict on our client certificate *after* the handshake
                // returns, on the first application read, and as a plain SSLException rather than a
                // handshake failure - so the CNXN read below is also the read that surfaces a
                // rejection, and it is classified as such.
                tlsInput = DataInputStream(tlsSocket.inputStream)
                tlsOutput = DataOutputStream(tlsSocket.outputStream)
                useTls = true
                runCatching { socket.soTimeout = 10_000 }
                runCatching { tlsSocket.soTimeout = 10_000 }
                try {
                    message = read()
                } catch (error: Throwable) {
                    if (isPairingLostError(error)) throw pairingLostException(error)
                    throw error
                }
            } catch (error: SSLException) {
                // A rejected client certificate means the pairing was revoked device-side or this
                // app's key store was wiped; saying so is what lets the caller ask for a re-pair
                // instead of reporting a network fault.
                if (isPairingLostError(error)) throw pairingLostException(error)
                throw error
            }
        } else if (message.command == A_AUTH && message.arg0 == ADB_AUTH_TOKEN) {
            writeBytes(A_AUTH, ADB_AUTH_SIGNATURE, 0, signToken(adbAuthTokenToSign(message.data)))
            message = read()
            if (message.command != A_CNXN) {
                writeBytes(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, keyManager.adbPublicKey)
                message = read()
            }
        }

        if (message.command != A_CNXN) {
            error("ADB connection failed: 0x${message.command.toString(16)}")
        }
        AppLog.info(AppLogTags.WIRELESS_ADB, "Connected: ${String(message.data ?: ByteArray(0))}")

        // Up: reads may now block indefinitely, because a shell can go quiet for minutes without
        // being dead.
        runCatching { socket.soTimeout = 0 }
        runCatching { if (useTls) tlsSocket.soTimeout = 0 }
        startReader()
    }

    private fun startReader() {
        readerThread = Thread({
            try {
                while (true) messageQueue.put(read())
            } catch (error: Throwable) {
                readerError = error
                messageQueue.put(POISON)
            }
        }, "adb-reader").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * The next complete message, blocking forever when [timeoutMs] is zero.
     *
     * A message is never split across a timeout: the poll either sees a whole one or none.
     */
    private fun nextMessage(timeoutMs: Long = 0): AdbMessage {
        val message = if (timeoutMs <= 0) {
            messageQueue.take()
        } else {
            messageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw SocketTimeoutException("No ADB message within ${timeoutMs}ms")
        }
        if (message === POISON) throw readerError ?: IOException("ADB reader stopped")
        return message
    }

    /**
     * Discards messages left over from a finished operation.
     *
     * The transport is one multiplexed stream feeding one queue, but only one operation runs at a
     * time. When an operation ends it sends CLSE and returns, while adbd's last replies can arrive
     * afterwards: those sit in the queue and the next operation consumes them, shifting its whole
     * message sequence by one - which looks like an OKAY appearing where only WRTE or CLSE is valid.
     */
    private fun drainStaleMessages(context: String) {
        var drained = 0
        while (true) {
            val message = messageQueue.poll(150, TimeUnit.MILLISECONDS) ?: break
            if (message === POISON) throw readerError ?: IOException("ADB reader stopped")
            drained++
        }
        if (drained > 0) {
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "[$context] drained $drained stale message(s) before opening a new stream",
            )
        }
    }

    private fun commandName(command: Int): String = when (command) {
        A_CNXN -> "CNXN"
        A_AUTH -> "AUTH"
        A_OPEN -> "OPEN"
        A_OKAY -> "OKAY"
        A_CLSE -> "CLSE"
        A_WRTE -> "WRTE"
        A_STLS -> "STLS"
        else -> "0x${command.toString(16)}"
    }

    /**
     * Runs a command and returns its output and exit code.
     *
     * The raw `shell:` service does not carry an exit code - it always closes cleanly - so the command
     * is wrapped in a subshell that prints a marker carrying `$?`. The subshell is not decoration: an
     * inner `exit N` would otherwise end the wrapper before it could print anything.
     */
    fun shell(command: String): ShellResult {
        val localId = 1
        drainStaleMessages("shell")
        val escaped = command.replace("'", "'\\''")
        val wrapped = "sh -c '( $escaped ); rc=${'$'}?; echo $SHELL_EXIT_MARKER${'$'}rc'"
        write(A_OPEN, localId, 0, "shell:$wrapped")
        var message = nextMessage(IDLE_TIMEOUT_MS)
        val output = StringBuilder()

        when (message.command) {
            A_OKAY -> while (true) {
                message = nextMessage(IDLE_TIMEOUT_MS)
                val remoteId = message.arg0
                when (message.command) {
                    A_WRTE -> {
                        message.data?.let { data ->
                            if (data.isNotEmpty()) output.append(String(data))
                        }
                        write(A_OKAY, localId, remoteId)
                    }
                    A_CLSE -> {
                        write(A_CLSE, localId, remoteId)
                        break
                    }
                    A_OKAY -> AppLog.debug(AppLogTags.WIRELESS_ADB, "shell: stray OKAY ignored")
                    else -> error(
                        "Unexpected message in shell: ${commandName(message.command)} arg0=${message.arg0}",
                    )
                }
            }
            A_CLSE -> write(A_CLSE, localId, message.arg0)
            else -> error("Unexpected response to OPEN: ${commandName(message.command)}")
        }
        return adbShellResult(output.toString())
    }

    /**
     * Runs a command and hands its output over as it arrives, for as long as it runs.
     *
     * A long command has to own an open shell: adbd kills a backgrounded process the moment its
     * stream closes. The root helper writes only when there is new log content, so the stream can go
     * quiet for minutes without being dead - which is why the bound is on silence and on the whole
     * run, not on any single read.
     */
    fun shellStreaming(
        command: String,
        overallTimeoutMs: Long = DEFAULT_STREAM_TIMEOUT_MS,
        stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
        shouldStop: () -> Boolean = { false },
        onOutput: (String) -> Unit,
    ): ShellResult {
        val localId = 1
        drainStaleMessages("shellStreaming")
        write(A_OPEN, localId, 0, "shell:$command")
        var message = nextMessage(IDLE_TIMEOUT_MS)
        val output = StringBuilder()
        val deadline = System.currentTimeMillis() + overallTimeoutMs
        var lastOutputAt = System.currentTimeMillis()

        when (message.command) {
            A_OKAY -> while (true) {
                val now = System.currentTimeMillis()
                if (now > deadline) {
                    AppLog.warn(AppLogTags.WIRELESS_ADB, "shellStreaming: overall timeout reached")
                    break
                }
                if (now - lastOutputAt > stallTimeoutMs) {
                    AppLog.warn(AppLogTags.WIRELESS_ADB, "shellStreaming: stall timeout reached")
                    break
                }
                if (shouldStop()) {
                    write(A_CLSE, localId, message.arg0)
                    break
                }
                message = try {
                    nextMessage(STREAM_POLL_MILLIS)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val remoteId = message.arg0
                when (message.command) {
                    A_WRTE -> {
                        message.data?.let { data ->
                            if (data.isNotEmpty()) {
                                val chunk = String(data)
                                output.append(chunk)
                                onOutput(chunk)
                                lastOutputAt = System.currentTimeMillis()
                            }
                        }
                        write(A_OKAY, localId, remoteId)
                    }
                    A_CLSE -> {
                        write(A_CLSE, localId, remoteId)
                        break
                    }
                    A_OKAY -> AppLog.debug(AppLogTags.WIRELESS_ADB, "shellStreaming: stray OKAY ignored")
                    else -> error(
                        "Unexpected message in shellStreaming: " +
                            "${commandName(message.command)} arg0=${message.arg0}",
                    )
                }
            }
            A_CLSE -> write(A_CLSE, localId, message.arg0)
            else -> error("Unexpected response to OPEN: ${commandName(message.command)}")
        }
        return ShellResult(0, output.toString().trim())
    }

    /** Pushes a file through the ADB sync protocol. */
    fun push(localFile: File, remotePath: String, mode: Int = DEFAULT_FILE_MODE) {
        val localId = 1
        drainStaleMessages("push")
        write(A_OPEN, localId, 0, "sync:")
        var message = nextMessage(IDLE_TIMEOUT_MS)
        if (message.command != A_OKAY) {
            error("Failed to open sync: ${commandName(message.command)}")
        }
        val remoteId = message.arg0

        val pathWithMode = "$remotePath,$mode"
        val sendPayload = ByteBuffer.allocate(8 + pathWithMode.length).order(ByteOrder.LITTLE_ENDIAN)
        sendPayload.put("SEND".toByteArray())
        sendPayload.putInt(pathWithMode.length)
        sendPayload.put(pathWithMode.toByteArray())
        writeSync(localId, remoteId, sendPayload.array())

        val fileBytes = localFile.readBytes()
        var offset = 0
        while (offset < fileBytes.size) {
            val length = minOf(PUSH_CHUNK_BYTES, fileBytes.size - offset)
            val dataPayload = ByteBuffer.allocate(8 + length).order(ByteOrder.LITTLE_ENDIAN)
            dataPayload.put("DATA".toByteArray())
            dataPayload.putInt(length)
            dataPayload.put(fileBytes, offset, length)
            writeSync(localId, remoteId, dataPayload.array())
            offset += length
        }

        val donePayload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        donePayload.put("DONE".toByteArray())
        donePayload.putInt((System.currentTimeMillis() / 1000).toInt())
        writeSync(localId, remoteId, donePayload.array())

        // adbd may interleave OKAY acks before the WRTE carrying the sync result, and a result already
        // consumed by writeSync is replayed from the pending slot rather than lost.
        var sawResult = false
        while (!sawResult) {
            message = pendingMessage ?: nextMessage(IDLE_TIMEOUT_MS)
            pendingMessage = null
            when (message.command) {
                A_WRTE -> {
                    write(A_OKAY, localId, message.arg0)
                    val data = message.data
                    if (data != null && data.size >= 4 && String(data, 0, 4) == "FAIL") {
                        val failLength = ByteBuffer.wrap(data, 4, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .int
                        val failMessage = if (data.size > 8) {
                            String(data, 8, minOf(failLength, data.size - 8))
                        } else {
                            "unknown"
                        }
                        error("ADB push failed: $failMessage")
                    }
                    sawResult = true
                }
                A_OKAY -> Unit
                A_CLSE -> sawResult = true
                else -> error(
                    "Unexpected message in push: ${commandName(message.command)} arg0=${message.arg0}",
                )
            }
        }
        write(A_CLSE, localId, remoteId)
        AppLog.info(AppLogTags.WIRELESS_ADB, "push: done $remotePath")
    }

    /**
     * Pulls a remote file through the ADB sync RECV protocol without buffering the whole file in RAM.
     * This is the transport primitive used by CVeyra's Privileged Storage Proxy.
     */
    fun pull(remotePath: String, localFile: File) {
        val localId = 1
        drainStaleMessages("pull")
        write(A_OPEN, localId, 0, "sync:")
        var message = nextMessage(IDLE_TIMEOUT_MS)
        require(message.command == A_OKAY) {
            "Failed to open sync: ${commandName(message.command)}"
        }
        val remoteId = message.arg0

        localFile.parentFile?.let { parent ->
            require(parent.mkdirs() || parent.isDirectory) { "Unable to create ${parent.absolutePath}" }
        }
        val temporary = File(localFile.parentFile, ".${localFile.name}.${System.nanoTime()}.part")
        temporary.delete()

        try {
            FileOutputStream(temporary, false).use { output ->
                var syncBuffer = ByteArray(0)
                var done = false

                fun consume(chunk: ByteArray) {
                    if (chunk.isEmpty()) return
                    syncBuffer += chunk
                    while (syncBuffer.size >= SYNC_HEADER_SIZE) {
                        val id = String(syncBuffer, 0, 4, Charsets.US_ASCII)
                        val length = ByteBuffer.wrap(syncBuffer, 4, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .int
                        require(length in 0..MAX_SYNC_PAYLOAD_BYTES) {
                            "ADB pull framing error: $id length=$length"
                        }
                        val packetSize = SYNC_HEADER_SIZE + length
                        if (syncBuffer.size < packetSize) return
                        when (id) {
                            "DATA" -> output.write(syncBuffer, SYNC_HEADER_SIZE, length)
                            "DONE" -> {
                                done = true
                            }
                            "FAIL" -> {
                                val reason = String(
                                    syncBuffer,
                                    SYNC_HEADER_SIZE,
                                    length,
                                    Charsets.UTF_8,
                                )
                                error("ADB pull failed: $reason")
                            }
                            else -> error("ADB pull framing error: unexpected $id")
                        }
                        syncBuffer = syncBuffer.copyOfRange(packetSize, syncBuffer.size)
                        if (done) return
                    }
                }

                val pathBytes = remotePath.toByteArray(Charsets.UTF_8)
                val request = ByteBuffer.allocate(SYNC_HEADER_SIZE + pathBytes.size)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply {
                        put("RECV".toByteArray(Charsets.US_ASCII))
                        putInt(pathBytes.size)
                        put(pathBytes)
                    }
                    .array()
                writeBytes(A_WRTE, localId, remoteId, request)

                var requestAcked = false
                while (!done) {
                    message = nextMessage(IDLE_TIMEOUT_MS)
                    when (message.command) {
                        A_OKAY -> requestAcked = true
                        A_WRTE -> {
                            write(A_OKAY, localId, message.arg0)
                            message.data?.let(::consume)
                        }
                        A_CLSE -> {
                            if (!done) error("ADB pull stream closed before DONE")
                        }
                        else -> error(
                            "Unexpected message in pull: ${commandName(message.command)} arg0=${message.arg0}",
                        )
                    }
                }
                check(requestAcked) { "ADB pull request was never acknowledged" }
                output.fd.sync()
            }
            require(temporary.isFile) { "ADB pull produced no file" }
            if (localFile.exists()) require(localFile.delete()) { "Unable to replace ${localFile.name}" }
            require(temporary.renameTo(localFile)) { "Unable to publish pulled file ${localFile.name}" }
            write(A_CLSE, localId, remoteId)
            AppLog.info(
                AppLogTags.WIRELESS_ADB,
                "pull: done $remotePath -> ${localFile.absolutePath} (${localFile.length()} bytes)",
            )
        } catch (error: Throwable) {
            temporary.delete()
            runCatching { write(A_CLSE, localId, remoteId) }
            throw error
        }
    }
    private fun writeSync(localId: Int, remoteId: Int, payload: ByteArray) {
        writeBytes(A_WRTE, localId, remoteId, payload)
        // adbd acks each write, and may interleave sync data before the ack.
        repeat(MAX_INTERLEAVED_MESSAGES) {
            val ack = nextMessage()
            when (ack.command) {
                A_OKAY -> return
                A_WRTE -> {
                    write(A_OKAY, localId, ack.arg0)
                    pendingMessage = ack
                }
                else -> error(
                    "Sync write not acknowledged: ${commandName(ack.command)} arg0=${ack.arg0}",
                )
            }
        }
        error("Sync write not acknowledged after $MAX_INTERLEAVED_MESSAGES interleaved messages")
    }

    private fun signToken(token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyManager.privateKey)
        signature.update(token)
        return signature.sign()
    }

    private data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data: ByteArray?,
    )

    private fun writeBytes(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        writeRaw(command, arg0, arg1, data)
    }

    private fun write(command: Int, arg0: Int, arg1: Int) {
        writeRaw(command, arg0, arg1, null)
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) {
        writeRaw(command, arg0, arg1, "$data\u0000".toByteArray())
    }

    /** Bounded by a deadline on its own thread; a stalled write closes the transport. */
    private fun writeRaw(command: Int, arg0: Int, arg1: Int, payload: ByteArray?) {
        val future = writeExecutor.submit<Any?> {
            val length = payload?.size ?: 0
            val checksum = payload?.sumOf { it.toInt() and 0xFF } ?: 0
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(command)
            header.putInt(arg0)
            header.putInt(arg1)
            header.putInt(length)
            header.putInt(checksum)
            header.putInt(command xor -0x1)
            outputStream.write(header.array())
            if (payload != null) outputStream.write(payload)
            outputStream.flush()
            null
        }
        try {
            future.get(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (timeout: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            runCatching { socket.close() }
            runCatching { if (useTls) tlsSocket.close() }
            throw IOException("ADB write stalled over ${WRITE_TIMEOUT_MS}ms; transport closed", timeout)
        }
    }

    private fun read(): AdbMessage {
        val header = ByteArray(HEADER_SIZE)
        inputStream.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        buffer.int // checksum: advisory in the protocol, and deliberately not enforced
        val magic = buffer.int
        // Framing is checked before the length is trusted: one desynced frame would otherwise be read
        // as an attacker-chosen allocation, which is an OOM or a negative size in the middle of a boot.
        require(magic == command.inv()) { "ADB framing error: bad magic" }
        require(dataLength in 0..A_MAXDATA) { "ADB framing error: length $dataLength" }
        val data = if (dataLength > 0) {
            ByteArray(dataLength).also { inputStream.readFully(it) }
        } else {
            null
        }
        return AdbMessage(command, arg0, arg1, data)
    }

    override fun close() {
        runCatching { plainInput.close() }
        runCatching { plainOutput.close() }
        runCatching { socket.close() }
        if (useTls) {
            runCatching { tlsInput.close() }
            runCatching { tlsOutput.close() }
            runCatching { tlsSocket.close() }
        }
        // Closing unblocks a reader blocked in readFully, which then poisons the queue, and unblocks a
        // writer that was wedged.
        writeExecutor.shutdownNow()
        readerThread?.interrupt()
    }

    data class ShellResult(val exitCode: Int, val output: String)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val HEADER_SIZE = 24
        private const val PUSH_CHUNK_BYTES = 64 * 1024
        private const val SYNC_HEADER_SIZE = 8
        private const val MAX_SYNC_PAYLOAD_BYTES = 1024 * 1024
        private const val DEFAULT_FILE_MODE = 0b111101101
        private const val MAX_INTERLEAVED_MESSAGES = 8
        private const val STREAM_POLL_MILLIS = 1_000L

        /** The marker the shell wrapper prints, and what makes a result readable at all. */
        const val SHELL_EXIT_MARKER = "__ADB_EXIT__="

        /** Reported when the command's result could not be established, never as success. */
        const val UNKNOWN_SHELL_EXIT_CODE = -1

        const val DEFAULT_STREAM_TIMEOUT_MS = 15 * 60 * 1000L
        const val DEFAULT_STALL_TIMEOUT_MS = 5 * 60 * 1000L

        /** Sentinel pushed by the reader when it stops. */
        private val POISON = AdbMessage(0, 0, 0, null)

        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val A_STLS = 0x534c5453

        private const val A_VERSION = 0x01000000
        private const val A_MAXDATA = 256 * 1024
        private const val A_STLS_VERSION = 0x01000000

        private const val ADB_AUTH_TOKEN = 1
        private const val ADB_AUTH_SIGNATURE = 2
        private const val ADB_AUTH_RSAPUBLICKEY = 3

        /**
         * How long an operation may sit without hearing from adbd.
         *
         * The reader thread itself may block - `close` unblocks it - but the shell and push waits are
         * bounded so a dead transport becomes a failure the run can act on instead of a service frozen
         * at "running" forever.
         */
        const val IDLE_TIMEOUT_MS = 120_000L

        /** Hard deadline for a single message write. */
        const val WRITE_TIMEOUT_MS = 30_000L

        /** What a failure carries when adbd rejected this app's client certificate. */
        const val PAIRING_LOST_MARKER = "ADB_PAIRING_LOST"

        /**
         * `certificate_unknown` is what adbd sends when this app's key is not in its keystore.
         *
         * Every other TLS failure - a reset connection, a refused protocol, an untrusted chain - is
         * transient or environmental, so treating it as a lost pairing would ask the user to pair
         * again for no reason.
         */
        private val CERT_UNKNOWN = Regex(
            "SSLV3_ALERT_CERTIFICATE_UNKNOWN|certificate_unknown|certificate unknown",
            RegexOption.IGNORE_CASE,
        )

        /** Whether [error] is adbd rejecting this app's certificate, through its causes. */
        fun isPairingLostError(error: Throwable): Boolean {
            var cause: Throwable? = error
            var depth = 0
            while (cause != null && depth++ < MAX_CAUSE_DEPTH) {
                if (cause is SSLException && CERT_UNKNOWN.containsMatchIn(cause.message ?: "")) {
                    return true
                }
                cause = cause.cause
            }
            return false
        }

        private const val MAX_CAUSE_DEPTH = 8

        private fun pairingLostException(error: Throwable): IOException =
            IOException("$PAIRING_LOST_MARKER: ${error.message}", error)

        /** Connect, run one command, close. */
        fun shellOnce(
            host: String,
            port: Int,
            keyManager: AdbKeyManager,
            command: String,
        ): ShellResult = LocalAdbClient(host, port, keyManager).use { client ->
            client.connect()
            client.shell(command)
        }
    }
}

/**
 * Turns a raw shell stream into a result, using the exit marker as the transport's contract.
 *
 * A stream without a readable marker says the command's result is unknown, and unknown is reported as
 * neither success nor a specific exit code - the same rule the Shizuku transport follows with
 * [NO_ROOT_SHELL_EXIT], and for the same reason: a run that reads "no answer" as "worked" is worse
 * than one that reads it as a failure.
 */
internal fun adbShellResult(raw: String): LocalAdbClient.ShellResult {
    val markerIndex = raw.lastIndexOf(LocalAdbClient.SHELL_EXIT_MARKER)
    val code = if (markerIndex < 0) {
        null
    } else {
        raw.substring(markerIndex + LocalAdbClient.SHELL_EXIT_MARKER.length)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.toIntOrNull()
    }
    // Deliberately quiet: this runs in tests and in the middle of a run, and the unknown result is
    // itself what the caller reports. The caller logs it, where there is a run to log it to.
    if (code == null) {
        return LocalAdbClient.ShellResult(LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE, raw.trim())
    }
    return LocalAdbClient.ShellResult(code, raw.substring(0, markerIndex).trim())
}

/**
 * The token to sign out of an `A_AUTH`/`TOKEN` frame.
 *
 * The one place this client reads a frame from the far end on the strength of its *kind* rather than its
 * contents, which is why it is a function of its own: adbd always sends the twenty bytes, so an absent
 * payload means something else is on the port - and a refusal that says so is worth more than the
 * `NullPointerException` a bare assertion produces, whose message is empty by the time any screen or log
 * line shows it.
 */
internal fun adbAuthTokenToSign(frame: ByteArray?): ByteArray =
    frame ?: error("ADB asked for a signature with no token to sign")

/** The user a usable wireless-debugging shell runs as: `shell`, not `root` and not the app's own uid. */
private const val SHELL_UID = "uid=2000"

/** The domain SELinux has to report for the route that needs a shell: tracefs is denied outside it. */
private const val SHELL_CONTEXT = "u:r:shell:s0"

/**
 * Why this local ADB shell cannot be used for a run, or null when it can.
 *
 * A session this app is authenticated for is not the same thing as a session in the shell domain, and
 * the difference is invisible from the connection: adbd answers, the push succeeds, and the payload
 * then fails on the first thing it reaches for - which reads like the exploit's fault rather than the
 * transport's. So the identity is asked for and required *before* anything is staged, and the answer is
 * named rather than logged as a code.
 *
 * Both halves are required. Running as shell in the wrong domain cannot reach the places a payload
 * stages and runs from, and the right domain as the wrong user is not a shell this app should be
 * running a privileged payload through. This is the one check the transport can make about itself.
 */
internal fun localAdbShellIdentityFailure(result: LocalAdbClient.ShellResult): String? = when {
    result.exitCode != 0 -> "the shell did not answer (exit ${result.exitCode})"
    !result.output.contains(SHELL_UID) ->
        "it is not running as $SHELL_UID: ${result.output.trim().takeLast(180)}"
    !result.output.contains(SHELL_CONTEXT) ->
        "it is not running in $SHELL_CONTEXT: ${result.output.trim().takeLast(180)}"
    else -> null
}
