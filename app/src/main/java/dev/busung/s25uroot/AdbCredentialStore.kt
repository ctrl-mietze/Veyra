package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * What this app knows about its own ADB identity, and how to get rid of it.
 *
 * The stored "paired" flag is a record that a pairing transaction once succeeded - it is **not** proof
 * that adbd still accepts the key, because the pairing can be revoked on the device side without
 * anything here changing. Whether the key still works is a question only [WirelessAdbDiagnostics] can
 * answer, and keeping the two apart is what stops the app from telling someone their transport is
 * ready when adbd has already forgotten them.
 */
internal object AdbCredentialStore {

    fun hasStoredKey(context: Context): Boolean {
        val directory = keyDirectory(context)
        return File(directory, AdbKeyManager.PRIVATE_KEY_FILE).isFile &&
            File(directory, AdbKeyManager.PUBLIC_KEY_FILE).isFile
    }

    /**
     * A short fingerprint of the public key, for telling one identity from another.
     *
     * Eight bytes rather than the whole digest: this is shown to say "the key I am using now is the
     * one I paired with", not to be compared against anything outside the app.
     */
    fun fingerprint(context: Context): String? {
        val publicFile = File(keyDirectory(context), AdbKeyManager.PUBLIC_KEY_FILE)
        if (!publicFile.isFile) return null
        return runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(publicFile.readBytes())
                .take(FINGERPRINT_BYTES)
                .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        }.getOrNull()
    }

    /**
     * Removes this app's local identity, so the next pairing starts from a key adbd has not seen.
     *
     * The device-side entry is deliberately left alone: it is the user's own paired-device list in
     * Developer options, and an app that silently edited it would be removing a record it did not
     * create. What this does clear is the app's claim that it is paired, because after this it is not.
     */
    fun forgetLocalCredential(context: Context): Boolean {
        AppPreferences.setAdbPaired(context, false)
        val directory = keyDirectory(context)
        var deleted = true
        listOf(AdbKeyManager.PRIVATE_KEY_FILE, AdbKeyManager.PUBLIC_KEY_FILE).forEach { name ->
            val file = File(directory, name)
            if (file.exists() && !file.delete()) deleted = false
        }
        if (directory.exists() && directory.listFiles().isNullOrEmpty()) directory.delete()
        return deleted && !hasStoredKey(context)
    }

    private fun keyDirectory(context: Context): File = File(context.filesDir, KEY_DIR)

    private const val KEY_DIR = "adb_keys"
    private const val FINGERPRINT_BYTES = 8
}
