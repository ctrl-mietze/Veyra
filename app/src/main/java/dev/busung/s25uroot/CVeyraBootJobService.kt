package ctrl.mietze.veyraroot

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

/**
 * Boot-safe CVeyra bootstrap.
 *
 * Android 15/16 restrict starting a dataSync foreground service directly from BOOT_COMPLETED.
 * JobScheduler is the durable boot entry point; once the detached uid=2000 broker is alive, no
 * Android service needs to remain running.
 */
class CVeyraBootJobService : JobService() {
    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!CVeyraPreferences.enabled(this) || !CVeyraPreferences.startOnBoot(this)) {
            CVeyraPreferences.setRuntimeState(
                this,
                running = false,
                rootBroker = false,
                backend = CVeyraBackend.None,
            )
            return false
        }

        worker = Thread({
            var reschedule = false
            try {
                if (params.extras.getBoolean(EXTRA_PROMOTE_ROOT, false)) {
                    CVeyraPreferences.setStartAsRoot(this, true)
                }
                val backend = CVeyraController.probeBackend(this)
                if (backend == CVeyraBackend.None) {
                    CVeyraPreferences.setRuntimeState(
                        this,
                        running = false,
                        rootBroker = false,
                        backend = CVeyraBackend.None,
                    )
                }
            } catch (error: Throwable) {
                AppLog.error(AppLogTags.WIRELESS_ADB, "CVeyra boot job failed", error)
                CVeyraPreferences.setRuntimeState(
                    this,
                    running = false,
                    rootBroker = false,
                    backend = CVeyraBackend.None,
                )
            } finally {
                worker = null
                jobFinished(params, reschedule)
            }
        }, "CVeyraBootJob").also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }

    companion object {
        private const val JOB_ID = 0x43565942

        private const val EXTRA_PROMOTE_ROOT = "promote_root"

        fun schedule(context: Context, promoteRoot: Boolean = false) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val extras = PersistableBundle().apply {
                putBoolean(EXTRA_PROMOTE_ROOT, promoteRoot)
            }
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, CVeyraBootJobService::class.java),
            )
                .setExtras(extras)
                .setMinimumLatency(1_500)
                .setOverrideDeadline(15_000)
                .build()
            val result = scheduler.schedule(info)
            if (result == JobScheduler.RESULT_SUCCESS) {
                AppLog.info(AppLogTags.BOOT, "CVeyra boot bootstrap scheduled")
            } else {
                AppLog.warn(AppLogTags.BOOT, "CVeyra boot bootstrap could not be scheduled")
            }
        }
    }
}
