package ctrl.mietze.veyraroot

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * A payload the user imported from the device instead of the one a source provides, for testing a
 * payload on a device the feed does not cover yet.
 *
 * The file is copied into app storage at import time rather than kept as a document URI. A run can
 * be started by the boot service, where no activity grant on a picked document exists, and a
 * persisted URI grant can be revoked or its provider uninstalled long after the imported payload
 * was chosen. The copy is validated before it can replace a payload — `.so` name, a size cap, and
 * an ELF header — and a rejected file leaves the previously imported one in place, so importing
 * cannot leave the next run without an exploit.
 */
object LocalPayload {
    private const val DIRECTORY = "local-payload"
    private const val FILE_NAME = "local-exploit.so"
    private const val PART_SUFFIX = ".part"
    const val MAX_BYTES = 16L * 1024 * 1024
    private val ELF_MAGIC = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    /** The imported payload, or null when none is set or the stored file is unusable. */
    fun file(context: Context): File? = File(directory(context), FILE_NAME)
        .takeIf { it.isFile && it.length() > 0L }

    /** The name the picker reported, for the UI; falls back to the stored file name. */
    fun displayName(context: Context): String? = file(context)?.let { stored ->
        AppPreferences.localPayloadName(context) ?: stored.name
    }

    /**
     * Reads [uri] into app storage and returns the name to show for it. Throws with a user-facing
     * message when the file is unusable; the stored payload is only replaced by a completed copy.
     */
    fun import(context: Context, uri: Uri): String {
        val name = queryDisplayName(context, uri) ?: FILE_NAME
        require(isAcceptedName(name)) { context.getString(R.string.local_payload_not_so) }
        val destination = File(directory(context).apply { mkdirs() }, FILE_NAME)
        val temporary = File(destination.parentFile, "$FILE_NAME$PART_SUFFIX")
        try {
            val total = copyInto(context, uri, temporary)
            require(total > 0L && startsWithElfMagic(temporary)) {
                context.getString(R.string.local_payload_not_elf)
            }
            require(temporary.renameToReplacing(destination)) {
                context.getString(R.string.repo_finalize_failed, name)
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        AppPreferences.setLocalPayloadName(context, name)
        AppPreferences.setUseLocalPayload(context, true)
        return name
    }

    /**
     * Stores a builder-produced payload as the currently selected local payload.
     *
     * The same validation rules as an imported document apply. The write is atomic, so a failed
     * builder save cannot replace a previously working local payload.
     */
    fun saveBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        selected: Boolean = true,
    ): String {
        require(bytes.isNotEmpty() && isWithinLimit(bytes.size.toLong())) {
            context.getString(R.string.local_payload_too_large)
        }
        require(isAcceptedName(displayName)) { context.getString(R.string.local_payload_not_so) }
        require(isElf(bytes.take(ELF_MAGIC.size).toByteArray())) {
            context.getString(R.string.local_payload_not_elf)
        }

        val destination = File(directory(context).apply { mkdirs() }, FILE_NAME)
        val temporary = File(destination.parentFile, "$FILE_NAME$PART_SUFFIX")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            require(temporary.renameToReplacing(destination)) {
                context.getString(R.string.repo_finalize_failed, displayName)
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        AppPreferences.setLocalPayloadName(context, displayName)
        AppPreferences.setUseLocalPayload(context, selected)
        return displayName
    }

    /** Copies the imported payload into the directory the install flow stages from. */
    fun stage(context: Context, destination: File): File {
        val source = requireNotNull(file(context)) {
            context.getString(R.string.local_payload_missing)
        }
        val temporary = File(destination.parentFile, "${destination.name}$PART_SUFFIX")
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.fd.sync()
                }
            }
            require(temporary.renameToReplacing(destination)) {
                context.getString(R.string.repo_finalize_failed, source.name)
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        return destination
    }

    fun clear(context: Context) {
        val directory = directory(context)
        File(directory, FILE_NAME).delete()
        File(directory, "$FILE_NAME$PART_SUFFIX").delete()
        AppPreferences.setLocalPayloadName(context, null)
        AppPreferences.setUseLocalPayload(context, false)
    }

    private fun copyInto(context: Context, uri: Uri, temporary: File): Long {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.local_payload_read_failed) }
            FileOutputStream(temporary).use { output ->
                var total = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(isWithinLimit(total)) { context.getString(R.string.local_payload_too_large) }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
                return total
            }
        }
    }

    private fun startsWithElfMagic(file: File): Boolean = file.inputStream().use { stream ->
        val header = ByteArray(ELF_MAGIC.size)
        stream.read(header) == header.size && isElf(header)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY)

    /** Rename that refuses to nest: a directory named like the destination is not silently kept. */
    private fun File.renameToReplacing(destination: File): Boolean {
        if (destination.exists()) destination.delete()
        return renameTo(destination)
    }

    /** The picker returns whatever name the provider reports, so the extension is checked. */
    internal fun isAcceptedName(name: String): Boolean = name.endsWith(".so", ignoreCase = true)

    internal fun isWithinLimit(total: Long): Boolean = total <= MAX_BYTES

    /** A payload that is not an ELF binary cannot be loaded at all, so it is refused up front. */
    internal fun isElf(header: ByteArray): Boolean =
        header.size >= ELF_MAGIC.size && header.copyOf(ELF_MAGIC.size).contentEquals(ELF_MAGIC)
}
