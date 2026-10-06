package ctrl.mietze.veyraroot

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.ShizukuProvider

/**
 * Application bootstrap for Veyra Root.
 *
 * Startup helpers are deliberately isolated from the launcher process: Shizuku or notification
 * housekeeping must never be able to take the whole UI down. A tiny crash recorder writes the
 * latest uncaught exception to Downloads so startup regressions can be diagnosed directly on-device.
 */
class RootMyGalaxyApplication : Application() {
    private var isShizukuProviderProcess = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installCrashRecorder(base)

        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            base.packageName
        }
        isShizukuProviderProcess = processName == base.packageName
        runCatching {
            ShizukuProvider.enableMultiProcessSupport(isShizukuProviderProcess)
        }.onFailure { error ->
            writeCrashReport(base, "Shizuku bootstrap warning", error)
        }
    }

    override fun onCreate() {
        super.onCreate()

        ReleaseGuard.install(this)
        AppLog.install(this)
        CVeyraAccessStore.enforce(this)

        if (AppPreferences.uiSoundsEnabled(this)) {
            runCatching { UiSoundEngine.preload(this) }
        }

        runCatching { ProcessExitDiagnostics.recordRecent(this) }
            .onFailure { error ->
                AppLog.error(AppLogTags.APP, "Process exit diagnostics failed", error)
            }

        runCatching { RunNotification.clearStale(this) }
            .onFailure { error ->
                AppLog.error(AppLogTags.APP, "Stale notification cleanup failed", error)
            }

        if (!isShizukuProviderProcess) {
            runCatching {
                ShizukuProvider.requestBinderForNonProviderProcess(this)
            }.onFailure { error ->
                AppLog.error(AppLogTags.SHIZUKU, "Binder request failed during startup", error)
            }
        }
    }

    private fun installCrashRecorder(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                writeCrashReport(
                    context,
                    "Fatal crash in ${thread.name}",
                    error,
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrashReport(context: Context, title: String, error: Throwable) {
        val trace = StringWriter().also { writer ->
            error.printStackTrace(PrintWriter(writer))
        }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val report = buildString {
            appendLine("Veyra Root crash report")
            appendLine("Time: $stamp")
            val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                context.packageName
            }
            appendLine("Process: $processName")
            appendLine("PID: ${Process.myPid()}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Title: $title")
            appendLine()
            append(trace)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "VeyraRoot-crash.txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri, "w")
                        ?.bufferedWriter()
                        ?.use { it.write(report) }
                }
            }
        }

        runCatching {
            java.io.File(context.filesDir, "last-crash.txt").writeText(report)
        }
    }
}
