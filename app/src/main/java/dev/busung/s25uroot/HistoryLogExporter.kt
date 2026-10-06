package ctrl.mietze.veyraroot

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes run logs out of the activity instead of inside it.
 *
 * A finished run's log is the payload's whole output, and a page-scanning profile can be printing
 * for an hour, so copying one to a picked document is enough work to be seen as a freeze. Both
 * exports run on one background thread and report their result on the main one.
 *
 * A run that is still going is left out rather than being written half-finished: its log is still
 * being appended to, and the file it would produce has no end.
 */
internal object HistoryLogExporter {
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "history-log-export").apply { isDaemon = true }
    }
    // Lazy: nothing here needs the main thread until a write reports back, and building a handler
    // needs a looper the caller may not have.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Name for an archive, counting only the runs that can go into it. */
    fun archiveFileName(
        entries: Collection<InstallHistoryEntry>,
        now: Long = System.currentTimeMillis(),
    ): String = "VeyraRoot-logs-" +
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now)) +
        "-${exportable(entries).size}.zip"

    /** Stable human-readable file name: app, timestamp and result, without an opaque run id. */
    fun entryFileName(entry: InstallHistoryEntry): String = "VeyraRoot-" +
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(entry.startedAtMillis)) +
        "-${entry.result.name.lowercase(Locale.US)}.log"

    /** Writes every completed run into one zip. */
    fun saveArchive(context: Context, uri: Uri, entries: Collection<InstallHistoryEntry>) {
        val appContext = context.applicationContext
        val snapshot = exportable(entries)
        write(
            context = appContext,
            uri = uri,
            saved = appContext.getString(R.string.export_logs_saved, snapshot.size),
            failed = appContext.getString(R.string.export_logs_failed),
        ) { output ->
            require(snapshot.isNotEmpty()) { "No completed run to export" }
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                snapshot.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entryFileName(entry)))
                    zip.write(entry.logOrPlaceholder(appContext).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }

    /** Writes a single run's log. */
    fun saveLog(context: Context, uri: Uri, entry: InstallHistoryEntry) {
        val appContext = context.applicationContext
        write(
            context = appContext,
            uri = uri,
            saved = appContext.getString(R.string.export_log_saved),
            failed = appContext.getString(R.string.export_log_failed),
        ) { output ->
            output.write(entry.logOrPlaceholder(appContext).toByteArray(Charsets.UTF_8))
        }
    }

    private fun exportable(entries: Collection<InstallHistoryEntry>): List<InstallHistoryEntry> =
        entries.filter { it.result != InstallRunResult.Running }

    private fun write(
        context: Context,
        uri: Uri,
        saved: String,
        failed: String,
        block: (OutputStream) -> Unit,
    ) {
        ioExecutor.execute {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use(block)
                    ?: error("Unable to open the destination")
                true
            }.getOrDefault(false)
            mainHandler.post {
                Toast.makeText(
                    context,
                    if (written) saved else failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun InstallHistoryEntry.logOrPlaceholder(context: Context): String =
        log.ifBlank { context.getString(R.string.history_log_empty) }
}
