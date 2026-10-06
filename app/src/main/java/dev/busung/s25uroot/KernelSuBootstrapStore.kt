package ctrl.mietze.veyraroot

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream

/**
 * App-private, deterministic KernelSU source for the root-side helper.
 *
 * The CZG3 helper promotes this verified copy into /data/local/tmp only after bootstrap UID 0 lands.
 * Keeping it here makes the App transport independent of Wireless ADB/Shizuku while preserving the
 * original RMG late-load ordering.
 */
internal object KernelSuBootstrapStore {
    private const val DIRECTORY = "ksu-bootstrap"
    const val FILE_NAME = "ksud-s25u-kdp"

    @Synchronized
    fun prepare(context: Context, payloads: VerifiedPayloads): File {
        val artifact = payloads.profile.kernelSu
        val source = payloads.kernelSu
        require(fileMatchesArtifact(source, artifact)) {
            "KernelSU bootstrap source failed verification"
        }

        val directory = File(context.filesDir, DIRECTORY).apply {
            require(mkdirs() || isDirectory) {
                "Unable to create KernelSU bootstrap directory"
            }
        }
        val destination = File(directory, FILE_NAME)
        if (fileMatchesArtifact(destination, artifact)) {
            Os.chmod(destination.absolutePath, 0b111101101)
            return destination
        }

        val temporary = File(directory, ".$FILE_NAME-${System.nanoTime()}.tmp")
        temporary.delete()
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            Os.chmod(temporary.absolutePath, 0b111101101)
            require(fileMatchesArtifact(temporary, artifact)) {
                "KernelSU bootstrap copy failed final verification"
            }
            Os.rename(temporary.absolutePath, destination.absolutePath)
            Os.chmod(destination.absolutePath, 0b111101101)
            require(fileMatchesArtifact(destination, artifact)) {
                "Published KernelSU bootstrap source failed verification"
            }
            return destination
        } finally {
            temporary.delete()
        }
    }
}
