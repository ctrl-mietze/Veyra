package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes Shizuku start attempts across this app's processes.
 *
 * Two of them can want a binder at the same time: the boot service starts Shizuku after a reboot, the
 * settings screen starts it when asked, and the automatic install may start it before a run. A
 * process-local mutex covers none of that, because they are not the same process - so the same moment
 * can see two callers conclude "no binder" and launch two servers, which is the one outcome Shizuku's
 * own documentation warns about. The lock is an OS file lock, so it holds across processes and is
 * released by the kernel if the holder dies.
 *
 * A lock that cannot be taken is not treated as a failure: the worst a missing lock costs is a second
 * starter racing one that is already running, and the binder re-probe immediately before each launch
 * catches exactly that.
 */
internal object ShizukuStartCoordinator {

    private val localMutex = Mutex()

    suspend fun <T> withStartLock(context: Context, block: suspend () -> T): T = localMutex.withLock {
        withContext(Dispatchers.IO) {
            val handle = openLockFile(context) ?: return@withContext block()
            try {
                val lock = runCatching { handle.channel.lock() }.getOrNull()
                try {
                    block()
                } finally {
                    runCatching { lock?.release() }
                }
            } finally {
                runCatching { handle.close() }
            }
        }
    }

    private fun openLockFile(context: Context): RandomAccessFile? = runCatching {
        RandomAccessFile(File(context.noBackupFilesDir, LOCK_FILE_NAME), "rw")
    }.getOrNull()

    private const val LOCK_FILE_NAME = "shizuku-start.lock"
}
