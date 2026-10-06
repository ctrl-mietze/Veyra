package ctrl.mietze.veyraroot

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/** Records Android's persisted reason for previous Veyra process exits. */
internal object ProcessExitDiagnostics {
    private const val PREFS = "process_exit_diagnostics"
    private const val LAST_RECORDED_AT = "last_recorded_at"
    private const val MAX_RECORDS = 8

    fun recordRecent(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        recordRecentApi30(context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun recordRecentApi30(context: Context) {
        val manager = context.getSystemService(ActivityManager::class.java)
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousCutoff = preferences.getLong(LAST_RECORDED_AT, 0L)
        val records = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
        }.onFailure { error ->
            AppLog.warn(
                AppLogTags.APP,
                "Unable to read historical process exits: ${error.javaClass.simpleName}: ${error.message}",
            )
        }.getOrNull().orEmpty()

        val fresh = records
            .filter { it.timestamp > previousCutoff }
            .sortedBy { it.timestamp }

        fresh.forEach { info ->
            val description = info.description?.toString()?.takeIf { it.isNotBlank() }
            val message = buildString {
                append("Previous Veyra process exit: reason=").append(info.reason)
                append(" status=").append(info.status)
                append(" importance=").append(info.importance)
                append(" pss=").append(info.pss)
                append(" rss=").append(info.rss)
                append(" at=").append(info.timestamp)
                if (description != null) append(" description=").append(description)
            }
            AppLog.warn(AppLogTags.APP, message)
        }

        fresh.maxOfOrNull { it.timestamp }?.let { newest ->
            preferences.edit().putLong(LAST_RECORDED_AT, newest).apply()
        }
    }
}
