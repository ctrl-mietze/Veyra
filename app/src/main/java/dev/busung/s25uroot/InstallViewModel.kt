package ctrl.mietze.veyraroot

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class InstallPhase {
    /**
     * The app reading this device, before any run exists.
     *
     * The value the state is constructed with, and **not** a step of a run: nothing is in flight, nothing can
     * be stopped, and no stored result corresponds to it. It is apart from [Checking] - which is the first
     * *step of a run*, where the device is resolved and a stop is a meaningful thing to press - because the two
     * want opposite answers to "is anything happening".
     *
     * Under one phase meaning both, the first frame the app ever drew was a run: a spinner where the status
     * glyph belongs, a title nothing had written to, a tap the card ignored, and - on the run screen - a Stop
     * button for a run that did not exist. None of that was a colour or a copy problem; the state was simply
     * untrue.
     */
    Probing,

    /** The first step of a run: resolving this device, its firmware, and the payload the run will use. */
    Checking,
    Ready,
    Settling,
    Downloading,
    Exploiting,
    LoadingKernelSu,
    Installed,

    /**
     * Root was obtained and KernelSU was not loaded, because loading is switched off.
     *
     * A terminal state of its own rather than [Installed]: the run is over and it worked, but the
     * device is not running KernelSU, and a screen that says "Installed" about that would be the app
     * claiming something it did not do.
     */
    RootOnly,
    Failed,

    /**
     * The user stopped the run.
     *
     * Its own phase rather than [Failed], because nothing here failed and the difference is what the
     * screen should say next: a failure is the app's account of what went wrong, and this is the record
     * of a decision the user made. The log says which stage was interrupted, because "stopped during the
     * exploit" and "stopped while downloading" leave the device in very different states.
     */
    Stopped,
}

data class InstallUiState(
    val phase: InstallPhase = InstallPhase.Probing,
    val message: String = "",
    val probeOutput: String = "",
    val log: String = "",
    /** Set when [phase] is [InstallPhase.Failed], so the screen can name the stage and the cause. */
    val failure: RunFailure? = null,
    /**
     * The stage a run was stopped in, when it was stopped rather than failed.
     *
     * Kept apart from [failure] because the card reads the two differently: a failure marks the step
     * that went wrong, and a stop marks the step that was in flight when it was abandoned.
     */
    val stoppedAt: RunStage? = null,
    /**
     * A run that has stopped before it began, because Shizuku was asked for and is not running.
     *
     * Not a failure, and deliberately not a phase of its own: nothing was attempted, nothing is in
     * flight, and the two things that can follow are both starts. The screen shows this as the question
     * it is, and the run that follows either goes through Shizuku or says it is not to.
     */
    val transportPrompt: TransportPrompt? = null,
) {
    /**
     * Whether a run is under way, which is not the same question as whether the app is working.
     *
     * [InstallPhase.Probing] is the one phase in which the app is working with no run behind it, and it is
     * absent from this set on purpose: that absence is the whole of what keeps the first frame from being a run.
     */
    val busy: Boolean
        get() = phase in setOf(
            InstallPhase.Checking,
            InstallPhase.Settling,
            InstallPhase.Downloading,
            InstallPhase.Exploiting,
            InstallPhase.LoadingKernelSu,
        )

}

/**
 * A run waiting on an answer about Shizuku, and what the attempt to start it said.
 *
 * [startDetail] is only about the attempt: it is null until one is made, and it holds the reason after
 * a failed one, so the dialog can say why retrying might not be worth it without swallowing the fact
 * that the person asked. [starting] keeps the dialog from being pressed twice.
 */
data class TransportPrompt(
    val starting: Boolean = false,
    val startDetail: String? = null,
)

/**
 * What a run is handed, for the run-plan screen: the variables the app sets for the payload, the
 * arguments only the Shizuku transport adds, and the cut-offs the app itself enforces. Keeping it
 * a value type means the screen shows what a run would use rather than a second copy of the rules,
 * and the rules stay testable without a device.
 */
internal data class ExploitPlan(
    /**
     * The boot-uptime floor a run waits for before the exploit starts; part of the plan because it is
     * a cut-off the app enforces on the run, and the plan is where those are shown.
     */
    val bootSettleSeconds: Int = 0,
    /**
     * The policy the run will use, with the origin of the three values a user can override.
     *
     * Held here rather than only folded into [environment], because the plan has to say *which side*
     * chose an attempt budget - the environment alone shows the number, which is the half of the answer
     * a reader cannot get anywhere else.
     */
    val routePolicy: EffectiveRoutePolicy,
    val environment: Map<String, String>,
    val shizukuArguments: Map<String, String>,
    /** Null when no stall watchdog applies, which is the case for a fresh session. */
    val stallLimitMillis: Long?,
    val totalLimitMillis: Long,
    val helperLimitMillis: Long,
)

data class TargetCatalogUiState(
    val loading: Boolean = false,
    val profiles: List<TargetProfile> = emptyList(),
    val error: String? = null,
    val sourceFailures: List<String> = emptyList(),
)

private data class CommandResult(val code: Int, val output: String)

/**
 * Which writer still owns the install screen.
 *
 * Two of them publish the whole state - the catalog lookup that says what the device supports, and a
 * run - and the lookup cannot be interrupted in the middle of its work. `Job.cancel()` only takes
 * effect at a suspension point, and there is none between its blocking fetch and its write, so a
 * lookup that started before a run lands its write *after* the run has begun.
 *
 * What that looked like: an offline run already at the kernel exploit sat behind a card reading "Not
 * installed / Ready to install", with the run's own log replaced by the probe the lookup had written
 * and every later payload line appended to that instead - so the screen was live and wrong at the same
 * time, and the run looked like nothing was happening.
 *
 * A claim taken before the work and checked before every publish makes the late write a no-op: the run
 * claims the screen on the caller's thread, so by the time the lookup's fetch returns, its claim is
 * stale. Claims are taken from the main thread only - both refresh and a run start there.
 */
internal class PublishClaim {

    private val next = java.util.concurrent.atomic.AtomicInteger(0)

    /** Takes ownership of the screen, invalidating every claim handed out before this one. */
    fun claim(): Int = next.incrementAndGet()

    /** Whether [token] is still the claim that owns the screen. */
    fun holds(token: Int): Boolean = next.get() == token
}

/**
 * Payloads are truncated to a fixed release size, so a rebuild of a target --
 * or a different target padded to the same size -- has exactly the length of
 * whatever is already staged, and would keep running in its place.
 */
/** The tag runs are filed under in the app log; see [AppLogTags]. */
internal const val RUN_LOG_TAG = AppLogTags.RUN

internal fun stagedFileIsCurrent(staged: File, source: File): Boolean {
    if (!staged.exists()) return false
    val stagedDigest = sha256OrNull(staged) ?: return false
    return stagedDigest == sha256OrNull(source)
}

private fun sha256OrNull(file: File): String? = runCatching {
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}.getOrNull()

class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = PayloadRepository(application)
    private val historyStore = InstallHistoryStore(application)
    // Carries a title, because this is the frame the Home card draws before anything has been read. What the
    // state says it is doing has to be the state's business: the card cannot invent words for a phase, and a
    // button with nothing written on it is a button nobody presses.
    private val mutableState = MutableStateFlow(
        InstallUiState(message = app.getString(R.string.status_checking_device)),
    )
    // The run in flight is named to this, because a run this app's *other* process is on must not be
    // closed here as an interrupted one: the gate installs from its own process, and opening the app
    // while it works used to mark that run failed.
    private val mutableHistory = MutableStateFlow(
        historyStore.closeInterruptedRuns(RunInFlight.holder(application)),
    )
    private val mutableTargetCatalog = MutableStateFlow(TargetCatalogUiState())
    private var discoveryJob: Job? = null
    private var installJob: Job? = null

    /**
     * Whether the run in flight was started by the boot gate rather than by a screen.
     *
     * Held because [setPhase] needs it and is called from all over the run: the gate has a notification of
     * its own, and a second one from here would be two notifications for one install.
     */
    private var runIsUnattended = false
    private var activeHistoryEntry: InstallHistoryEntry? = null

    /** Which stage the run is in, for the failure report. */
    private var activeStage = RunStage.Target

    @Volatile
    private var activeRunShizuku: Boolean? = null

    /** Which transport this run's payload goes through, frozen when the run starts. */
    @Volatile
    private var activeRunTransport: RunTransport? = null

    /** Exact helper paired with the active payload; null outside a run. */
    @Volatile
    private var activeRootHelper: File? = null

    /**
     * The ceilings this run is being held to, resolved when it started.
     *
     * Frozen like the transport and the KernelSU decision, for the same reason: a limit changed in the
     * settings while a run is in flight must not be able to move the point at which that run is cut off.
     */
    @Volatile
    private var activeCeilings: RunCeilings = RunLimits.defaultCeilings(freshSession = false)

    /** Set by the run screen's override while a boot-settle wait is in progress. */
    @Volatile
    private var bootSettleOverridden = false

    /**
     * The settle this run was held to, so the payload's own window can be derived from the decision
     * the run actually made. A second setting would be a second thing to keep in step.
     */
    private var bootSettleRequiredSeconds = BootSettle.DEFAULT_SECONDS

    /**
     * Set when the user stops the run, so its cancellation is not read as a failure.
     *
     * A cancellation is delivered as an exception in the run's own coroutine, which is the same shape
     * a real failure arrives in, so the two are told apart by this rather than by the exception: the
     * exception type says the job was cancelled, not that a person asked for it.
     */
    @Volatile
    private var stopRequested = false

    /**
     * Where in this run's log the image partitions were set read-only, and how many devices it covered.
     *
     * Kept so a failure can be attributed to the protection rather than blamed on it: only a refusal
     * recorded *after* this point, on a run where devices were actually set, can be its doing. Reset with
     * each run, because the protection is per boot and a count from the last one says nothing about this
     * one's log.
     */
    private var protectedFrom = -1
    private var protectedDevices = 0

    /**
     * Set when this run's payload could not be confirmed stopped.
     *
     * Read when the failure is built, because it is the one failure fact that changes what the screen
     * may offer afterwards: a retry in this boot is not on the table while something may still be
     * running. Reset with each run like the protection above, for the same reason.
     */
    @Volatile
    private var payloadTerminationUnconfirmed = false

    /**
     * What the screen said before a run stopped to ask about Shizuku.
     *
     * Kept so dismissing the question puts the screen back rather than leaving it on a message that only
     * ever says why nothing ran - which is what a retry from a failure screen would do, and that screen's
     * log is the reason the retry was reached for.
     */
    private var stateBeforeHold: InstallUiState? = null
    private val publishClaim = PublishClaim()
    val state: StateFlow<InstallUiState> = mutableState.asStateFlow()
    val history: StateFlow<List<InstallHistoryEntry>> = mutableHistory.asStateFlow()

    /**
     * The history entry of this process's run, for as long as the process lives.
     *
     * Kept past the end of the run rather than cleared with it, because two things are still about that
     * run after it has finished: the notification left in the shade for a run that did not simply succeed,
     * and the screen that notification opens. Both are built after the entry has been closed, and an id
     * that vanished with the run would send a tap at that notification to a fresh install screen instead of
     * to the run it is about.
     */
    @Volatile
    var activeRunId: String? = null
        private set
    val targetCatalog: StateFlow<TargetCatalogUiState> = mutableTargetCatalog.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (installJob?.isActive == true) return
        mutableHistory.value = historyStore.load()
        discoveryJob?.cancel()
        // Claimed before the work starts and checked before each publish. Cancelling the job is not
        // enough on its own: nothing between the probe and the write suspends, so a cancel arrives
        // after the write it was meant to prevent.
        val claim = publishClaim.claim()
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            val probe = NativeProbe.run()
            if (detectInstalled()) {
                if (!publishClaim.holds(claim)) return@launch
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Installed,
                    message = app.getString(R.string.status_ksu_active),
                    probeOutput = probe,
                    log = probe,
                )
                return@launch
            }
            try {
                // Offline mode resolves nothing: the cached payload already names its own target, and
                // a support lookup would put the very network this mode exists without in front of the
                // first screen the user sees.
                val profile = if (AppPreferences.payloadMode(app) == PayloadMode.Offline) {
                    cachedProfileFor(null)
                } else {
                    repository.resolveTarget(DeviceSnapshot.current())
                }
                if (!publishClaim.holds(claim)) return@launch
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Ready,
                    message = app.getString(R.string.status_not_installed),
                    probeOutput = probe,
                    log = "$probe\n${app.getString(R.string.log_profile, profile.profileId)}",
                )
            } catch (error: Throwable) {
                if (!publishClaim.holds(claim)) return@launch
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Failed,
                    // A missing cached payload and an unreachable catalog are the same failure to look
                    // up support; what differs is which of them the user can do something about, and
                    // the log line below carries the one that happened.
                    message = app.getString(
                        if (AppPreferences.payloadMode(app) == PayloadMode.Offline) {
                            R.string.status_cache_missing
                        } else {
                            R.string.status_support_failed
                        },
                    ),
                    probeOutput = probe,
                    log = "$probe\n[-] ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun deleteHistoryEntries(ids: Collection<String>) {
        val runningId = activeHistoryEntry?.id
        val toDelete = ids.filterNot { it == runningId }
        if (toDelete.isEmpty()) return
        toDelete.forEach(historyStore::delete)
        mutableHistory.value = mutableHistory.value.filterNot { it.id in toDelete }
    }

    /**
     * Puts deleted runs back, which is what makes the delete undoable.
     *
     * The entries are the caller's, taken before it deleted them: a log is a file on this phone and nothing
     * else holds a copy, so an undo that re-read a directory would restore nothing. Writing them back is the
     * same save the run itself uses, which is why an undone entry is indistinguishable from one that was
     * never deleted - same id, same log, same place in the list by its own start time.
     */
    fun restoreHistoryEntries(entries: Collection<InstallHistoryEntry>) {
        if (entries.isEmpty()) return
        entries.forEach(historyStore::save)
        val restored = (mutableHistory.value + entries).distinctBy { it.id }
        mutableHistory.value = restored.sortedByDescending { it.startedAtMillis }
    }

    fun loadTargetCatalog() {
        if (mutableTargetCatalog.value.loading) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableTargetCatalog.value = TargetCatalogUiState(loading = true)
            mutableTargetCatalog.value = try {
                val catalog = repository.loadCatalog()
                TargetCatalogUiState(
                    profiles = catalog.targets.sortedWith(
                        compareBy(
                            TargetProfile::displayName,
                            TargetProfile::profileId,
                            TargetProfile::sourceLabel,
                        ),
                    ),
                    sourceFailures = catalog.sourceFailures,
                )
            } catch (error: Throwable) {
                TargetCatalogUiState(error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /**
     * Runs an install and returns once it reaches a terminal phase, for callers outside the
     * install screen that have to keep a foreground service alive for exactly as long as the run
     * takes — the boot gate, which cannot wait on the UI state itself.
     *
     * [unattended] marks a run nobody is watching. It is not a transport preference: it means the run
     * cannot show a permission prompt and cannot ask for Shizuku to be started, because both are
     * conversations with a person who is not there.
     */
    suspend fun runToCompletion(
        selectionId: String? = null,
        unattended: Boolean = false,
        payloadOffline: Boolean = false,
        preferAttemptedPayload: Boolean = false,
        /**
         * Runs this attempt the way it would run with Use Shizuku off.
         *
         * The boot gate's own answer, and the only way an unattended run goes another transport: the
         * gate reads the payload's policy before it decides about Shizuku ([bootShizukuPlan]), and this
         * is how that decision travels - the run itself may not make it, because a run that quietly went
         * a different way from the one it was promised is the silence the refusal below exists to stop.
         */
        withoutShizuku: Boolean = false,
    ) {
        install(selectionId, unattended, payloadOffline, preferAttemptedPayload, withoutShizuku)
        installJob?.join()
    }

    /**
     * Ends a boot-settle wait on the user's word.
     *
     * A plain flag rather than a cancellation, because the wait is not the run: what is being skipped
     * is the pause in front of it, and the run continues from there with nothing else changed.
     */
    fun skipBootSettle() {
        bootSettleOverridden = true
    }

    /**
     * Starts Shizuku for a run that stopped to ask, and starts that run when it comes up.
     *
     * The start goes through the same [ShizukuStarter] the settings row uses, with the same routes, so
     * "retry" here means the same set of attempts: this device's own root, a stored pairing, or a start
     * token. A failure keeps the question on screen with the reason beside it, because the other answer -
     * run without it - is still open, and a start that failed once can succeed on a second ask.
     */
    fun startShizukuForHeldRun(selectionId: String?) {
        val prompt = mutableState.value.transportPrompt ?: return
        if (prompt.starting) return
        mutableState.value = mutableState.value.copy(
            transportPrompt = prompt.copy(starting = true, startDetail = null),
        )
        viewModelScope.launch(Dispatchers.IO) {
            // Caught rather than left to the coroutine machinery, because what is on screen while this
            // runs is a question whose two buttons are answered through this state: a throw would leave
            // [TransportPrompt.starting] true for good, and every way out of the question is disabled
            // while it is - a dialog that says "starting" and takes no answer, which is worse than the
            // refusal it replaced. A route that broke is reported like a route that did not work.
            val outcome = runCatching {
                ShizukuStarter.start(
                    context = app,
                    shell = { command ->
                        KernelSuRuntime.rootShell(command) ?: ShizukuController.ShellResult(
                            NO_ROOT_SHELL_EXIT,
                            app.getString(R.string.error_shizuku_start_no_root),
                        )
                    },
                )
            }.getOrElse { error ->
                ShizukuStartOutcome(
                    started = false,
                    detail = error.message ?: error.javaClass.simpleName,
                )
            }
            // The question can be answered while the attempt is in flight - "Run without Shizuku" is
            // deliberately still live - and a start that lands after that answer must not start a second
            // run over the one the person asked for.
            val stillHeld = mutableState.value.transportPrompt != null
            // Running, not merely started: the outcome says a binder was seen, and this asks the same
            // question the next run will ask, so a start that only almost worked is still a question.
            if (outcome.started && ShizukuController.isRunning()) {
                AppLog.info(
                    RUN_LOG_TAG,
                    "Shizuku started from the run screen via ${outcome.method ?: "an unnamed route"}" +
                        if (stillHeld) "; resuming" else "; the run has already gone another way",
                )
                if (!stillHeld) return@launch
                mutableState.value = mutableState.value.copy(transportPrompt = null)
                install(selectionId)
            } else {
                AppLog.warn(
                    RUN_LOG_TAG,
                    "Shizuku was not started from the run screen: " +
                        outcome.detail.ifBlank { "no route reported why" },
                )
                if (!stillHeld) return@launch
                mutableState.value = mutableState.value.copy(
                    transportPrompt = TransportPrompt(
                        starting = false,
                        startDetail = outcome.detail.ifBlank {
                            app.getString(R.string.error_shizuku_start_no_root)
                        },
                    ),
                )
            }
        }
    }

    /**
     * Runs a run that stopped to ask, the way it would run with Use Shizuku off.
     *
     * The setting is left as it is. What this says is "not this run", and a payload that needs a shell
     * without a pairing to carry it is refused by the run itself, in its own words, rather than here.
     */
    fun runHeldRunWithoutShizuku(selectionId: String?) {
        AppLog.warn(RUN_LOG_TAG, "Running without Shizuku, on the user's word at the hold")
        install(selectionId = selectionId, withoutShizuku = true)
    }

    /**
     * Puts the question away without running anything.
     *
     * Dismissing is a real answer - the run does not start - so the hold is cleared rather than left to
     * reappear, and the screen goes back to saying what the last run did.
     */
    fun dismissTransportPrompt() {
        mutableState.value = (stateBeforeHold ?: mutableState.value).copy(transportPrompt = null)
        stateBeforeHold = null
    }

    /**
     * Ends the run on the user's word.
     *
     * Cancelling the coroutine is what stops it: every wait in the run is cancellable, and the places
     * that hold a process of their own release it in a `finally`, so a stop reaches the payload rather
     * than merely stopping the app from watching it. What it cannot promise is that the payload process
     * is gone - a process started through a transport is the device's until it exits - so the screen
     * says so instead of claiming a clean stop.
     *
     * The flag is set before the cancel, and the run's cancellation handler reads it: a cancellation
     * arrives in the same shape as a failure, and only the flag distinguishes "stopped" from "broke".
     */
    fun stopRun() {
        if (installJob?.isActive != true) return
        stopRequested = true
        // Said before the cancel, because what it is cancelling is a payload on the device.
        AppLog.warn(RUN_LOG_TAG, "Stop requested; cancelling the run")
        installJob?.cancel()
    }

    /**
     * Arms one retry for the next boot and asks the phone to reboot into it.
     *
     * Returns whether the reboot was actually requested. The arming happens first and is committed to
     * disk, because the reboot can beat an asynchronous write and take the decision with it - and
     * because the armed retry is what makes a failed reboot recoverable: the user reboots by hand and
     * gets the install they asked for either way.
     *
     * The boot it was armed in is recorded with it, so the same boot cannot consume it: an install
     * started without a reboot would be the attempt the user turned down when they chose to reboot.
     */
    suspend fun armRetryAfterReboot(): Boolean {
        AppPreferences.setRetryAfterReboot(app, currentBootToken())
        val requested = requestReboot(app)
        // The arming is what matters and it has already happened, so a reboot that could not be
        // requested is worth a line rather than a failure: the user can still restart by hand.
        AppLog.warn(
            RUN_LOG_TAG,
            if (requested) {
                "Retry armed for the next boot, restart requested"
            } else {
                "Retry armed for the next boot, but the restart was refused"
            },
        )
        return requested
    }

    fun install(
        selectionId: String? = null,
        /**
         * Marks a run nobody is watching, which cannot prompt for anything.
         *
         * The boot gate sets it. It used to mean "do not use Shizuku", which is how a device with the
         * setting on ended up installing in the app's own process after every reboot: the preference was
         * not consulted at all. It is consulted now, and the one thing this takes away is the ability to
         * ask - so an unattended run that was promised Shizuku gets a refusal rather than another
         * transport, because it cannot say afterwards that it went a different way. The gate may unpromise
         * it before the run starts, which is a decision rather than a fallback: see [withoutShizuku].
         */
        unattended: Boolean = false,
        /**
         * Forces this run to use the cached payload.
         *
         * A boot-time run sets it because at boot there may be no network to download from and
         * nobody to wait for one, so the run has to be able to say "cached, or not at all" without
         * changing the mode the user chose for their own runs.
         */
        payloadOffline: Boolean = false,
        /**
         * Resolves the payload from the last attempt rather than from the cache.
         *
         * Set by a retry that survived a reboot. The two are not interchangeable and the difference is
         * the whole point of the setting: the cache holds the last payload that *worked*, and a retry is
         * a request to run the one that failed, which on a device somebody is testing on is a different
         * payload - another source, another commit, or the other KernelSU project.
         */
        preferAttemptedPayload: Boolean = false,
        /**
         * Runs this attempt the way it would run with Use Shizuku off.
         *
         * The answer to the prompt below, carried on the run rather than written to the setting: the
         * choice is about this run, and quietly turning the setting off afterwards would be the same
         * kind of silence the prompt exists to end.
         */
        withoutShizuku: Boolean = false,
    ) {
        if (installJob?.isActive == true || mutableState.value.phase == InstallPhase.Installed) {
            AppLog.debug(RUN_LOG_TAG, "Run not started: this screen is already on a run")
            return
        }
        // A run is one attempt at the exploit, and this app can be on two at once. The guards above are
        // about *this* screen and this view model, and the app is not one thing: the boot gate installs from
        // its own process, and a second screen brings a second view model in this one. Both are the same
        // failure - two exploits racing one kernel, with the second one's staging swept or overwritten under
        // it - so both are refused here, before anything is written, claimed, staged or stopped.
        runInFlightElsewhere()?.let { other ->
            AppLog.warn(
                RUN_LOG_TAG,
                "Run not started: a run is already in flight (pid ${other.pid}, entry ${other.entryId})",
            )
            // Reported as a failure rather than as silence, because nothing else on the screen would say
            // why the tap did nothing - and as a failure *without* a history entry: no attempt was made, so
            // there is nothing to record, and the next run's history is not this one's.
            mutableState.value = InstallUiState(
                phase = InstallPhase.Failed,
                message = app.getString(R.string.status_run_in_flight),
                probeOutput = mutableState.value.probeOutput,
                failure = RunFailure.of(
                    stage = RunStage.Transport,
                    reason = app.getString(R.string.install_run_in_flight),
                ),
            )
            appendLog(app.getString(R.string.log_run_in_flight))
            return
        }
        // Taken before the question below rather than after it, because the question is itself a state
        // of this screen and this screen has a writer in flight: the lookup that runs when the screen
        // opens publishes "not installed, ready to install" whenever its fetch returns, and cancelling
        // it is not enough - nothing between its fetch and its write suspends, so a cancel arrives after
        // the write it was meant to prevent. Held states are the newest case of the same race: the
        // lookup's write landed 54 ms after the hold was set and replaced it with a fresh
        // [InstallUiState], which is a constructor with no prompt in it - so the question appeared and
        // vanished, and the screen sat on a run that had not started and could not be asked about.
        discoveryJob?.cancel()
        publishClaim.claim()
        // Asked before anything else is taken or written, so a run that is going to be a question leaves
        // no trace of one that ran: no history entry, no log, and a screen that can still say what the
        // question is.
        if (shouldHoldForShizuku(
                unattended = unattended,
                requested = AppPreferences.shizukuMode(app),
                running = ShizukuController.isRunning(),
                ignoringShizuku = withoutShizuku,
            )
        ) {
            stateBeforeHold = mutableState.value
            mutableState.value = InstallUiState(
                phase = InstallPhase.Ready,
                message = app.getString(R.string.status_shizuku_hold),
                probeOutput = mutableState.value.probeOutput,
                transportPrompt = TransportPrompt(),
            )
            AppLog.warn(RUN_LOG_TAG, "Run held: Use Shizuku is on and Shizuku is not running")
            return
        }
        AppLog.info(
            RUN_LOG_TAG,
            "Run started: unattended=$unattended, offline=$payloadOffline, " +
                "retry=$preferAttemptedPayload, withoutShizuku=$withoutShizuku",
        )
        bootSettleOverridden = false
        stopRequested = false
        // The protection is per boot and per run, so this run starts with no attribution to make.
        protectedFrom = -1
        protectedDevices = 0
        payloadTerminationUnconfirmed = false
        runIsUnattended = unattended
        // A stop written by an earlier run's notification is not this run's: the request names a process,
        // and one left in place would stop the next run on its first tick.
        RunStopSignal.clear(app)
        installJob = viewModelScope.launch(Dispatchers.IO) {

            mutableState.value = InstallUiState(
                phase = InstallPhase.Checking,
                message = app.getString(R.string.status_checking_device),
                probeOutput = mutableState.value.probeOutput,
            )
            startHistory()
            // Said before anything is staged, so a sweep in the app's other process cannot take the
            // payload out from under this run. Best-effort and silent: a boot that cannot be read means
            // no record, which is the behaviour this app had before the record existed. The entry is
            // named because it is created above and is already on disk: it is what lets another process
            // tell this run's record from one a killed run left behind.
            RunInFlight.begin(app, currentBootToken(), activeRunId)
            if (!unattended) {
                runCatching { RunGuardService.start(app) }
                    .onFailure { error ->
                        AppLog.warn(
                            RUN_LOG_TAG,
                            "Manual run guard could not start: ${error.javaClass.simpleName}",
                        )
                    }
            }
            // First line of every run, and part of its stored history with the rest of the log: a
            // result is only reproducible if the build that produced it is on the record. The
            // version code is part of that identity, not decoration: the name only names the
            // commit, and several local builds share one commit.
            appendLog(
                app.getString(
                    R.string.version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
            )
            // Freeze the transport for the whole run so a mid-run preference
            // change cannot mix Shizuku and standalone execution between the
            // exploit and the KernelSU staging steps. The boot service asks for
            // standalone explicitly rather than by toggling the stored
            // preference, which would leave it wrong if the run never finished.
            //
            // Whether this run has already swept its staging. A run that is about to ask for a userspace
            // restart does it early, because that request ends this process - so the sweep in the finally
            // below must not then do it a second time.
            var stagingSwept = false
            try {
                activeStage = RunStage.Target
                setPhase(InstallPhase.Checking, app.getString(R.string.status_checking_github))
                // Offline mode is what makes a run possible with no network at all, so it resolves
                // nothing: the cached payload already names its target, and asking the catalog would
                // be the very thing this mode exists to avoid.
                val offline = payloadOffline || AppPreferences.payloadMode(app) == PayloadMode.Offline
                // Read once and used twice: resolving an attempt hashes the files it verifies, which is
                // not work to do again a few lines later.
                val retriedPayload = if (offline && preferAttemptedPayload) {
                    AttemptedPayloadStore.resolve(app)
                } else {
                    null
                }
                val targetSnapshot = DeviceSnapshot.current()
                val selectedFlavor = AppPreferences.kernelsuFlavor(app)
                val requestedMethod = AppPreferences.rootMethod(app)
                val enhancedFallbackPlan =
                    DfPlusPlanner.plan(app, targetSnapshot, selectedFlavor)
                val requestedDfPlan = when (requestedMethod) {
                    RootMethod.DfCompatible ->
                        DfPlusPlanner.compatiblePlan(app, targetSnapshot, selectedFlavor)
                    RootMethod.Fast -> enhancedFallbackPlan
                    RootMethod.Standard -> null
                }
                val requestedDfAllowed = requestedDfPlan?.available == true
                val fastFallbackAllowed = enhancedFallbackPlan.available

                val profile = when {
                    // Attempted and unusable is a refusal, not a fallback. The run the user asked for is
                    // the one that failed, and quietly running the last payload that worked would install
                    // something they did not choose - possibly the other KernelSU project.
                    retriedPayload != null -> retriedPayload.getOrElse { failure ->
                        error(failure.message ?: failure.javaClass.simpleName)
                    }.profile
                    offline -> cachedProfileFor(selectionId)
                    selectionId != null -> repository.resolveTarget(selectionId)
                    requestedMethod.usesDf && requestedDfAllowed ->
                        repository.resolveFastTarget(targetSnapshot, selectedFlavor)
                    else -> {
                        runCatching {
                            repository.resolveTarget(targetSnapshot)
                        }.getOrElse { standardFailure ->
                            if (!fastFallbackAllowed) throw standardFailure
                            val fastProfile = runCatching {
                                repository.resolveFastTarget(targetSnapshot, selectedFlavor)
                            }.getOrNull() ?: throw standardFailure
                            AppPreferences.setRootMethod(app, RootMethod.Fast)
                            appendLog(
                                "[*] Standard has no matching profile; Veyra DF+ selected automatically.",
                            )
                            fastProfile
                        }
                    }
                }
                // Frozen with the profile, for the whole run, and resolved in one place: the payload's
                // own numbers unless the user opted to override them, with the fresh-session rule on
                // top. The run-plan screen calls the same function with the same stored settings, so
                // what this run enforces is what that screen said it would.
                val routePolicy = ExploitOverride.resolve(
                    policy = profile.routePolicy,
                    override = AppPreferences.exploitOverride(app),
                    freshSession = profile.requiresFreshP0Session,
                )
                // Said before anything is attempted, because the flavour is a property of the entry
                // that was chosen and not of the app's setting: a catalog that carries only the other
                // project's payloads serves that one, and the log is where that becomes visible.
                appendLog(app.getString(R.string.run_flavor_label, profile.flavor.label))
                // Which kind of match this run is on, when it is not the exact build. An entry that
                // lists only the three-part version is not tied to this firmware by the feed, and that
                // is the first thing to weigh when a run fails - said before the payload runs rather
                // than left to be found in the sheet afterwards.
                val matchSnapshot = DeviceSnapshot.current()
                if (profile.kernelMatch(matchSnapshot) == KernelMatch.Version) {
                    appendLog(
                        app.getString(
                            R.string.run_kernel_version_match,
                            matchSnapshot.kernelRelease,
                            profile.supportedKernelVersions,
                        ),
                    )
                }

                // One flavour per boot. Both projects hook the same syscall paths and a loader refuses
                // a module into a kernel that already carries the other one, so this is a restart
                // away rather than a failure worth retrying - which is what the message says.
                AppPreferences.loadedFlavor(app)?.let { loaded ->
                    require(loaded == profile.flavor) {
                        app.getString(
                            R.string.run_flavor_conflict,
                            loaded.label,
                            profile.flavor.label,
                        )
                    }
                }

                // The transport is chosen here rather than beside the Shizuku check, because the
                // profile is what decides whether a shell is required at all - and a target that
                // requires one can be carried by a pairing instead of by Shizuku. Frozen for the
                // whole run, so a mid-run preference change cannot mix transports between the exploit
                // and the KernelSU staging steps.
                // Frozen here with the transport, for the same reason: a preference changed from the
                // settings screen while this run is in flight must not be able to produce a half-load
                // - staged on one reading and skipped on another.
                val loadKernelSu = AppPreferences.loadKernelSu(app)
                // The run's own answer to the question above, honoured for the whole run: this is what
                // makes "Run without Shizuku" mean it rather than asking again a moment later.
                val shizukuRequested = AppPreferences.shizukuMode(app) && !withoutShizuku
                val shizukuUsable = ShizukuController.isRunning() && ShizukuController.isGranted()
                // The gate has already waited for Shizuku, so a binder that is missing here is not the
                // thing to fall back from: this run was started on the promise that it would go through
                // Shizuku, and a payload that runs in the app's process instead is a different run.
                if (unattended && shizukuRequested && !shizukuUsable) {
                    error(app.getString(R.string.error_shizuku_unavailable))
                }
                val localAdbPaired = AdbCredentialStore.hasStoredKey(app) && AppPreferences.adbPaired(app)
                // Root acquisition follows the payload profile, not CVeyra availability. CZG3 is an
                // App-route target in the maintained RMG feed; CVeyra remains available for staging,
                // diagnostics and post-root work but must not silently move the kernel race into the
                // uid=2000 shell domain.
                val transport = chooseRunTransport(
                    shellRequired = routePolicy.policy.prefersShellTransport,
                    shizukuRequested = shizukuRequested,
                    shizukuUsable = shizukuUsable,
                    localAdbPaired = localAdbPaired,
                    shellPreferred = false,
                ) ?: error(app.getString(shellTransportRefusalStringId(shizukuRequested)))
                activeRunTransport = transport
                activeRunShizuku = transport == RunTransport.Shizuku
                // Requested and used, side by side: the one outcome that used to be invisible from
                // anywhere was a run that was set to go through Shizuku and went another way.
                AppLog.info(
                    RUN_LOG_TAG,
                    "Transport ${transport.name} (shell required=" +
                        "${routePolicy.policy.prefersShellTransport}, CVeyra does not override exploit transport, " +
                        "Shizuku requested=$shizukuRequested, usable=$shizukuUsable, " +
                        "pairing saved=$localAdbPaired)",
                )

                if (transport == RunTransport.Shizuku) {
                    activeStage = RunStage.Transport
                    appendLog(app.getString(R.string.log_shizuku_prepare))
                    if (!ShizukuController.isRunning() && !ShizukuController.pingUntilRunning()) {
                        error(app.getString(R.string.error_shizuku_unavailable))
                    }
                    if (!ShizukuController.isGranted() &&
                        (unattended || !ShizukuController.requestPermission())
                    ) {
                        error(app.getString(R.string.error_shizuku_permission))
                    }
                    appendLog(app.getString(R.string.log_shizuku_permission))
                    AppLog.info(RUN_LOG_TAG, "Shizuku ready: running and granted")
                }
                appendLog(
                    app.getString(
                        when (transport) {
                            RunTransport.Shizuku -> R.string.log_transport_shizuku
                            RunTransport.LocalAdb -> R.string.log_transport_local_adb
                            RunTransport.App -> R.string.log_transport_app
                        },
                    ),
                )
                appendLog(app.getString(R.string.log_profile, profile.profileId))
                if (profile.sourceLabel.isNotEmpty()) {
                    appendLog(app.getString(R.string.log_payload_source, profile.sourceLabel))
                }
                updateHistoryTarget(profile)
                // Resolved here rather than read where each ceiling is enforced, so the whole run is
                // held to one reading - and stated in the log, because a run cut off by a ceiling the
                // user set should say so in the place a user looks for the reason.
                activeCeilings = runCeilings(app, profile.requiresFreshP0Session)
                appendLog(
                    app.getString(
                        if (profile.requiresFreshP0Session) {
                            R.string.log_run_ceilings_fresh
                        } else {
                            R.string.log_run_ceilings
                        },
                        RunLimits.durationLabel(activeCeilings.totalMillis),
                        RunLimits.durationLabel(activeCeilings.stallMillis),
                        RunLimits.durationLabel(activeCeilings.helperMillis),
                    ),
                )

                // Before anything the run has to wait for, because a boot that has spent its pipe page
                // budget cannot run this exploit at all: settling a boot for minutes and only then being
                // refused is the wait the refusal exists to save. The record comes from the payload's own
                // output in an earlier run, so this can only refuse a boot the device already answered
                // for. The exploit is the step marked, because that is the step that cannot happen.
                activeStage = RunStage.Exploit
                require(!PipeBudget.spentInBoot(app, currentBootToken())) {
                    app.getString(R.string.error_pipe_budget_spent)
                }

                // Before the download, so the wait is the first thing the screen reports rather than
                // something that appears after the payload is already staged.
                awaitBootSettle(AppPreferences.bootSettleSeconds(app))

                val payloads = if (offline) {
                    activeStage = RunStage.Download
                    setPhase(InstallPhase.Downloading, app.getString(R.string.status_loading_cached))
                    if (retriedPayload != null) {
                        retriedPayload.getOrThrow().also { attempted ->
                            appendLog(
                                app.getString(
                                    R.string.log_retry_attempted,
                                    attempted.profile.displayName.ifBlank { attempted.profile.profileId },
                                ),
                            )
                        }
                    } else {
                        KnownGoodPayloadStore.load(app, profile.profileId).also { cached ->
                            appendLog(app.getString(R.string.log_payload_cached, cached.exploit.name))
                        }
                    }
                } else {
                    activeStage = RunStage.Download
                    setPhase(InstallPhase.Downloading, app.getString(R.string.status_downloading_payload))
                    repository.download(profile) { appendLog("[*] $it") }.also {
                        appendLog(app.getString(R.string.log_download_verified))
                    }
                }

                activeRootHelper = payloads.rootHelper
                KernelSuBootstrapStore.prepare(app, payloads)
                appendLog("[+] KernelSU bootstrap source prepared for root-side auto-late-load")

                // Before the exploit, because the record exists for the runs that fail during it: a
                // record written after would only ever describe runs that did not need one. Best-effort,
                // but said out loud - a missing record is what makes the next retry fall back to the
                // cached payload instead of the one that failed.
                if (payloads.origin == PayloadOrigin.Downloaded) {
                    runCatching { AttemptedPayloadStore.save(app, payloads) }
                        .onFailure { error ->
                            appendLog(
                                app.getString(
                                    R.string.log_attempted_record_failed,
                                    error.message ?: error.javaClass.simpleName,
                                ),
                            )
                        }
                }

                activeStage = RunStage.Exploit
                setPhase(InstallPhase.Exploiting, app.getString(R.string.status_exploit_running))
                // Stated before the payload runs, so a failed run says which policy produced it - and
                // which side chose the three numbers a user can move.
                appendLog(routePolicy.policy.describe())
                appendLog(app.getString(R.string.log_payload_origin, payloads.origin.name.lowercase()))
                val kernelSuRecoveredAfterPayloadKill = executeExploit(
                    payloads,
                    profile.requiresFreshP0Session,
                    routePolicy.policy,
                )

                // Optional, and before the KernelSU load rather than after: what it protects against
                // is a write made while bootstrap root is the only root on the device.
                if (AppPreferences.partitionReadOnlyMode(app)) setPartitionBlocksToRo()

                if (loadKernelSu) {
                    val modulesSkipped = if (AppPreferences.disableKsuModules(app)) {
                        moveModulesAside()
                    } else {
                        appendLog(app.getString(R.string.log_ksu_modules_disabled_off))
                        false
                    }

                    try {
                        activeStage = RunStage.KernelSu
                        setPhase(
                            InstallPhase.LoadingKernelSu,
                            app.getString(R.string.status_ksu_loading),
                        )
                        installKernelSu(
                            payloads,
                            kernelSuAlreadyActive = kernelSuRecoveredAfterPayloadKill,
                        )
                    } finally {
                        // Modules are only meant to sit out the load itself. Restoring here also
                        // covers a load that fails, which is where leaving them aside would strand
                        // them with nothing in the app to bring them back.
                        if (modulesSkipped) restoreModules()
                    }
                } else {
                    // Nothing is loaded, so nothing is staged, nothing is verified, and there is
                    // nothing for the modules to sit out: the run ends at the root the exploit won.
                    appendLog(app.getString(R.string.log_ksu_loading_off))
                }

                // While the run still holds the root it just obtained: the permission below cannot be
                // given any other way on the device, and the Shizuku start is what makes the next run
                // possible without a cable. Neither can fail the install.
                runCatching { PermissionGrant.writeSecureSettings(app) }
                    .onSuccess { outcome -> appendLog(outcome.logLine(app)) }
                    .onFailure { error ->
                        appendLog(
                            app.getString(
                                R.string.log_postroot_permission_failed,
                                error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    }
                if (AppPreferences.shizukuBootMode(app)) ShizukuBootService.start(app)

                setPhase(
                    if (loadKernelSu) InstallPhase.Installed else InstallPhase.RootOnly,
                    app.getString(
                        if (loadKernelSu) R.string.status_ksu_active else R.string.status_root_only,
                    ),
                )
                appendLog(
                    app.getString(
                        if (loadKernelSu) R.string.log_install_complete else R.string.log_root_only_complete,
                    ),
                )
                // A payload becomes the offline fallback only here, once KernelSU is verified: that is
                // what makes "known good" mean something, and it is why nothing is written while the
                // exploit is running. Publishing is best-effort - a full disk must not turn a root
                // that worked into a failure - but it is said out loud when it does not happen.
                if (payloads.origin == PayloadOrigin.Downloaded) {
                    runCatching { KnownGoodPayloadStore.publish(app, payloads) }
                        .onSuccess { cached ->
                            appendLog(app.getString(R.string.log_payload_cached_now, cached.profileId))
                        }
                        .onFailure { error ->
                            appendLog(
                                app.getString(
                                    R.string.log_payload_cache_failed,
                                    error.message ?: error.javaClass.simpleName,
                                ),
                            )
                        }
                }
                AppLog.info(
                    RUN_LOG_TAG,
                    if (loadKernelSu) {
                        "Run finished: ${profile.flavor.label} loaded"
                    } else {
                        "Run finished: root only, nothing loaded"
                    },
                )
                finishHistory(
                    if (loadKernelSu) InstallRunResult.Succeeded else InstallRunResult.RootOnly,
                )
                // Swept here rather than left to the finally below, and the order is the whole point of
                // this line: requesting the userspace restart ends everything this process is in the
                // middle of, so a sweep left to the finally on a successful run never got to run a shell
                // at all - the process was gone first. That is how a loaded root came to leave its
                // staged helper and payload in /data/local/tmp for a detector to find, on exactly the
                // runs that worked. Nothing staged is needed by a run that has finished: the module
                // lives in /data/adb, and the daemon the restart reaches is the installed one.
                stagingSwept = true
                sweepStaging(app)
                // Last, and only for a run that loaded KernelSU: modules take effect when the userspace
                // is built again, and KernelSU's own soft reboot is the way that walks their lifecycle
                // in the normal order. After the result is written, because the restart ends everything
                // this process is in the middle of - a result that had not been persisted yet would go
                // with it. Opt-in, and a refusal is a line in the log: the run already succeeded.
                if (loadKernelSu && AppPreferences.restartAfterRoot(app)) {
                    appendLog(app.getString(R.string.log_restart_after_root))
                    val restart = runRecoveryAction(app, RecoveryTool.SoftReboot)
                    appendLog(
                        if (restart.accepted) {
                            app.getString(R.string.log_restart_after_root_accepted)
                        } else {
                            app.getString(R.string.log_restart_after_root_refused, restart.detail)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                // Stopped, not failed: the run was cancelled, and the flag says whether a person asked
                // for it. Either way this must be rethrown - swallowing a cancellation would leave the
                // coroutine machinery believing the work is still running.
                val stage = activeStage
                appendLog(
                    app.getString(
                        if (stopRequested) R.string.log_run_stopped else R.string.log_run_interrupted,
                        app.getString(stage.label),
                    ),
                )
                if (stopRequested) {
                    updateHistory { entry ->
                        entry.copy(failureStage = stage, failureReason = "Stopped before it finished")
                    }
                    finishHistory(InstallRunResult.Stopped)
                    setPhase(InstallPhase.Stopped, app.getString(R.string.status_run_stopped))
                    mutableState.value = mutableState.value.copy(stoppedAt = stage)
                }
                AppLog.warn(
                    RUN_LOG_TAG,
                    if (stopRequested) {
                        "Run stopped at ${app.getString(stage.label)}"
                    } else {
                        "Run interrupted at ${app.getString(stage.label)}"
                    },
                )
                throw cancelled
            } catch (error: Throwable) {
                // The stage and the last payload output travel with the failure: the message alone
                // is the same for a download that failed and an exploit that gave up, and only one
                // of those is worth retrying straight away.
                val stage = activeStage
                val reason = error.message ?: error.javaClass.simpleName
                val log = mutableState.value.log
                // An exploit failure has a cause the payload can name that the app's own ceilings
                // cannot: the kernel refusing it the pipe pages its race needs. When that is what the
                // output says, it is the answer - the ceiling only says where the run was cut off, and
                // a further attempt in this boot fails the same way for the same spent budget.
                val pipeEvidence = if (stage == RunStage.Exploit) PipeBudget.evidenceIn(log) else null
                if (pipeEvidence != null) {
                    appendLog(app.getString(R.string.log_pipe_budget, pipeEvidence))
                    // Recorded for the boot, because the budget is the boot's: the next run refuses
                    // before it stages anything rather than spending another attempt on a boot that
                    // cannot take one. A restart ends the record by changing the token.
                    currentBootToken()?.let { token -> PipeBudget.rememberSpent(app, token) }
                }
                val failure = RunFailure.of(
                    stage = stage,
                    reason = if (pipeEvidence != null) app.getString(R.string.failure_pipe_budget) else reason,
                    evidence = failureEvidence(log),
                    readOnlyWall = refusedByProtection(
                        log = mutableState.value.log,
                        protectedFrom = protectedFrom,
                        protectedDevices = protectedDevices,
                        reason = reason,
                    ),
                    inBootRetryBlocked = when {
                        payloadTerminationUnconfirmed -> InBootRetryBlock.PayloadMayStillRun
                        pipeEvidence != null -> InBootRetryBlock.PipeBudgetSpent
                        else -> null
                    },
                )
                appendLog("[-] $reason")
                // Named where the failure is, and only when it is this protection's doing: a wall the
                // run put up itself is the one failure whose fix is a switch in this app, and the log
                // is where someone looks first.
                if (failure.readOnlyWall) appendLog(app.getString(R.string.log_read_only_wall))
                setPhase(
                    InstallPhase.Failed,
                    app.getString(R.string.status_stage_failed, app.getString(stage.label)),
                )
                mutableState.value = mutableState.value.copy(failure = failure)
                updateHistory { entry ->
                    entry.copy(failureStage = stage, failureReason = reason)
                }
                AppLog.error(
                    RUN_LOG_TAG,
                    "Run failed at ${app.getString(stage.label)}: $reason",
                )
                if (failure.readOnlyWall) {
                    AppLog.warn(RUN_LOG_TAG, app.getString(R.string.log_read_only_wall))
                }
                finishHistory(InstallRunResult.Failed)
            } finally {
                activeRunShizuku = null
                activeRunTransport = null
                activeRootHelper = null
                // The run is over, so its files are no longer anything but evidence: swept here rather
                // than at the next launch because this is the one moment that knows the run has
                // finished, including the runs that failed or were stopped. A successful run that is
                // about to restart the userspace has already swept for the same reason, and it says so.
                RunInFlight.end(app)
                if (!stagingSwept) sweepStaging(app)
                // The run is over. A success clears the notification, because Home's card is the account of
                // it and a shade line saying "done" about the thing you just did is noise - but anything else
                // stays, wearing its verdict: a run that failed while the phone was in a pocket is exactly
                // the one whose outcome nobody saw. An unattended run leaves its own notification alone:
                // the gate owns that one.
                if (!runIsUnattended) {
                    runCatching { RunGuardService.stop(app) }
                    val outcome = runVerdict(mutableState.value.phase, busy = false)
                    if (outcome == RunVerdict.Succeeded) {
                        RunNotification.clear(app)
                    } else {
                        RunNotification.finish(
                            context = app,
                            message = mutableState.value.failure?.reason
                                ?: mutableState.value.message,
                            verdict = outcome,
                            runId = activeRunId,
                        )
                    }
                }
            }
        }
    }

    /**
     * Sweeps what the run staged, and files what came of it.
     *
     * The order inside is the same one the runner's own `finally` used to do inline, and it matters:
     * `RunInFlight.end` comes first, because the record of this run exists so that no sweep takes the
     * payload out from under it - and the run is over by the time this is called. Anything still holding
     * a record belongs to another process's run, and the sweep stands down for that one.
     *
     * Called from two places now - the one that is about to end this process, and the one that catches
     * everything else - so it is a function rather than a pair of identical paragraphs. Blocking, and on
     * the run's own IO dispatcher, like every other shell this app runs.
     */
    private fun sweepStaging(context: Context): SweepOutcome {
        RunInFlight.end(context)
        val outcome = StagingSweep.sweepWhenQuiet(context)
        outcome.logLine(context)?.let { line -> AppLog.info(AppLogTags.STAGING, line) }
        return outcome
    }

    /**
     * Moves the module directory aside so the late-load starts without any module, and reports
     * whether they are actually out of the way. It never fails the run: the helper holds no
     * raised privileges of its own, and a device where the move is refused is a device whose
     * modules were never in the load's way to begin with.
     *
     * The move script first puts back a directory left over from a run that was killed between
     * the two moves, so a stranding cannot outlive one interrupted run.
     */
    private suspend fun moveModulesAside(): Boolean {
        val result = runMaintenance(MODULES_ASIDE_SCRIPT)
        when {
            result.code == MODULES_BACKUP_EXISTS -> appendLog(
                app.getString(R.string.log_ksu_modules_backup_present, MODULES_BACKUP_DIRECTORY),
            )
            result.code != 0 -> {
                appendLog(
                    app.getString(
                        R.string.log_ksu_modules_move_failed,
                        result.output.ifBlank { "exit ${result.code}" },
                    ),
                )
                appendReadOnlyWallIfProtected(result.output)
            }
            result.output.contains(MODULES_MOVED_MARKER) -> {
                appendLog(app.getString(R.string.log_ksu_modules_moved, MODULES_BACKUP_DIRECTORY))
                return true
            }
            else -> appendLog(app.getString(R.string.log_ksu_modules_absent, MODULES_DIRECTORY))
        }
        return false
    }

    /**
     * Names the protection in the log when a module step's own output says a write was refused.
     *
     * A module step that fails behind the wall reports EROFS from wherever the write went, and the two
     * lines above it name a directory and an exit status - neither of which says that a switch in this
     * app is what refused it. Gated on this run having set devices, so it cannot be said about a boot
     * where the protection was on and did nothing.
     */
    private fun appendReadOnlyWallIfProtected(output: String) {
        if (protectedDevices <= 0) return
        if (!PartitionReadOnly.refusedByReadOnly(output)) return
        appendLog(app.getString(R.string.log_read_only_wall))
    }

    private suspend fun restoreModules() {
        val result = runMaintenance(MODULES_RESTORE_SCRIPT)
        when {
            result.code != 0 -> {
                appendLog(
                    app.getString(
                        R.string.log_ksu_modules_restore_failed,
                        MODULES_BACKUP_DIRECTORY,
                        MODULES_DIRECTORY,
                        result.output.ifBlank { "exit ${result.code}" },
                    ),
                )
                appendReadOnlyWallIfProtected(result.output)
            }
            result.output.contains(MODULES_RESTORED_MARKER) ->
                appendLog(app.getString(R.string.log_ksu_modules_restored))
            else -> appendLog(app.getString(R.string.log_ksu_modules_restore_skipped))
        }
    }

    /**
     * Puts the KernelSU daemon where the payload will look for it, before the payload runs.
     *
     * The order is the whole point. The load of KernelSU is done from the exploit process while
     * bootstrap root is still live - the payload's own helper has the kernel primitives, and they are
     * gone when it exits - so a daemon staged afterwards is a daemon nobody is left to load. Staged
     * here, the payload finds it at the moment root lands; the staging in [installKernelSu] then
     * re-checks the same two paths by hash and rewrites nothing.
     *
     * Only a transport that can write to the device before root (Shizuku, or the paired wireless
     * shell) makes this possible, and the route that has neither says so rather than pretending.
     */
    private suspend fun stageKernelSuBeforeExploit(kernelSu: File) {
        when (activeRunTransport) {
            RunTransport.Shizuku -> {
                shizukuStage(kernelSu, SHIZUKU_KSUD_PATH, "755")
                shizukuStage(kernelSu, SHIZUKU_KSUD_STAGE_PATH, "755")
                appendLog(app.getString(R.string.log_ksu_staged_early))
            }
            RunTransport.LocalAdb -> Unit // The session itself pushes it, inside its own block.
            // App transport uses KernelSuBootstrapStore; the root-side helper promotes that verified
            // private copy only after bootstrap UID 0 lands.
            RunTransport.App -> Unit
            null -> Unit
        }
    }

    private suspend fun executeExploit(
        payloads: VerifiedPayloads,
        requiresFreshP0Session: Boolean,
        routePolicy: ExploitRoutePolicy,
    ): Boolean {
        val preferredMethod = AppPreferences.rootMethod(app)
        val snapshot = DeviceSnapshot.current()
        val dfPlan = when (preferredMethod) {
            RootMethod.DfCompatible ->
                DfPlusPlanner.compatiblePlan(app, snapshot, payloads.profile.flavor)
            RootMethod.Fast ->
                DfPlusPlanner.plan(app, snapshot, payloads.profile.flavor)
            RootMethod.Standard -> null
        }
        if (preferredMethod.usesDf && dfPlan?.available == true) {
            appendLog("[*] Root method: ${dfPlan.mode.label}")
            return executeFastRoot(payloads.profile.flavor, preferredMethod)
        }
        if (preferredMethod.usesDf) {
            appendLog(
                "[!] ${preferredMethod.label} unavailable here; using Standard. " +
                    (dfPlan?.blockers?.joinToString("; ")
                        ?: "No coherent DF route is available."),
            )
        } else {
            appendLog("[*] Root method: Standard")
        }

        val payload = payloads.exploit
        if (activeRunTransport == RunTransport.LocalAdb) {
            return executeExploitOverLocalAdb(
                payload,
                payloads.kernelSu,
                payloads.rootHelper,
                requiresFreshP0Session,
                routePolicy,
            )
        }
        val shizuku = shizukuEnabled()
        stageKernelSuBeforeExploit(payloads.kernelSu)
        val logFile = if (shizuku) File(SHIZUKU_LOG_PATH) else File(app.filesDir, "exploit.log")
        if (shizuku) {
            ShizukuController.exec(arrayOf("rm", "-f", SHIZUKU_LOG_PATH)).waitFor()
        } else {
            logFile.delete()
        }
        val helper = helperFile()
        if (!shizuku) {
            require(helper.canExecute()) { app.getString(R.string.error_helper_unavailable) }
        }
        val logPrefix = mutableState.value.log
        val bootToken = currentBootToken()
        val cachedP0Offset = if (requiresFreshP0Session) null else cachedP0Offset(bootToken)
        val process = if (shizuku) {
            val stagedPayload = shizukuStage(payload, SHIZUKU_PAYLOAD_PATH, "755")
            ShizukuController.exec(
                arrayOf("/system/bin/sh", "-c", "true"),
                shizukuEnvironment(
                    stagedPayload.absolutePath,
                    helper.absolutePath,
                    requiresFreshP0Session,
                    cachedP0Offset,
                    routePolicy,
                ),
            )
        } else {
            val processBuilder = ProcessBuilder(
                helper.absolutePath,
                "--run-payload",
                payload.absolutePath,
                helper.absolutePath,
                logFile.absolutePath,
            ).redirectErrorStream(true)
            processBuilder.environment().putAll(
                exploitEnvironment(
                    requiresFreshP0Session,
                    cachedP0Offset,
                    routePolicy,
                    payloadQuietWindowSeconds(),
                ),
            )
            processBuilder.start()
        }
        val captured = StringBuilder()
        val readLog: () -> String = if (shizuku) {
            { drainProcessOutput(process, captured) }
        } else {
            // Keep draining stdout while polling: if the helper fills the OS
            // pipe buffer it blocks on write and stops making log progress,
            // which would trip the stall detector spuriously.
            { drainProcessOutput(process, captured); logFile.readTextIfPresent() }
        }

        var recoveredKernelSu = false
        try {
            val startedAt = SystemClock.elapsedRealtime()
            var lastProgressAt = startedAt
            var lastRawLog = ""
            while (process.isAlive) {
                val rawLog = readLog()
                if (rawLog != lastRawLog) {
                    if (!requiresFreshP0Session) cacheP0Offset(bootToken, rawLog)
                    publishExploitLog(logPrefix, rawLog)
                    lastRawLog = rawLog
                    lastProgressAt = SystemClock.elapsedRealtime()
                }
                val now = SystemClock.elapsedRealtime()
                if (!requiresFreshP0Session) {
                    require(now - lastProgressAt < activeCeilings.stallMillis) {
                        app.getString(
                            R.string.error_exploit_stalled,
                            (activeCeilings.stallMillis / 1000L).toInt(),
                        )
                    }
                }
                require(now - startedAt < activeCeilings.totalMillis) {
                    // Reported in minutes of the ceiling that actually applies - which is the one from
                    // the settings, and an hour for a fresh session whatever that says.
                    app.getString(
                        R.string.error_exploit_timeout,
                        (activeCeilings.totalMillis / 60_000L).toInt(),
                    )
                }
                // The notification's Stop arrives here: this loop is the run's longest wait by far, and
                // checking it on the same tick that already reads the payload's output costs nothing.
                stopIfAskedFromOutside()
                delay(if (shizuku) SHIZUKU_LOG_POLL_INTERVAL else LOG_POLL_INTERVAL)
            }

            val exitCode = process.waitFor()
            val rawLog = readLog()
            if (!requiresFreshP0Session) cacheP0Offset(bootToken, rawLog)
            publishExploitLog(logPrefix, rawLog)
            // A late SIGKILL is not automatically a failed exploit. On this Samsung path the native
            // payload can lose its userspace process after the kernel-side KernelSU load has already
            // landed. Accept that only when the log proves the exploit reached the late physical/KASLR
            // stage AND an independent live probe proves KernelSU is present now.
            recoveredKernelSu = recoverKernelSuAfterPayloadExit(exitCode, rawLog)
            if (!recoveredKernelSu) {
                // Both transports drain into `captured` during the poll loop, so a child that still
                // holds the pipe open cannot block the loop; nothing here reads it, because what the
                // payload said belongs in the log rather than in a failure message.
                require(exitCode == 0) {
                    // The payload's output is the log, so the message says what the status means instead
                    // of repeating it: inlining the whole output here is what turned a failed run into a
                    // page of unreadable text.
                    app.getString(R.string.error_payload_exit, exitCode, payloadExitDetail(exitCode))
                }
                require(rawLog.contains("exploit completed") && rawLog.contains("done=1 root=1")) {
                    app.getString(R.string.error_success_marker)
                }
            }
        } finally {
            // Stopping the payload is a request, not a fact, when the process lives behind Shizuku's
            // binder: the app can only ask and then look. What it finds is recorded, because a payload
            // that outlived the run makes a retry a second payload rather than another attempt at the
            // first - which is the one thing this device cannot take.
            if (process.stopConfirmed() == Termination.Unconfirmed) {
                payloadTerminationUnconfirmed = true
                appendLog(app.getString(R.string.log_payload_may_still_run))
            }
        }
        appendLog(app.getString(R.string.log_bootstrap_root))
        return recoveredKernelSu
    }

    private suspend fun executeFastRoot(
        flavor: KernelSuFlavor,
        method: RootMethod,
    ): Boolean {
        val next = flavor == KernelSuFlavor.KernelSuNext
        val snapshot = DeviceSnapshot.current()
        val plan = when (method) {
            RootMethod.DfCompatible -> DfPlusPlanner.compatiblePlan(app, snapshot, flavor)
            RootMethod.Fast -> DfPlusPlanner.plan(app, snapshot, flavor)
            RootMethod.Standard -> error("Standard cannot execute the DF engine")
        }
        require(plan.available) {
            "Veyra DF+ preflight blocked: " + plan.blockers.joinToString("; ")
        }
        val kmi = requireNotNull(plan.kmi)
        val koAsset = plan.koAsset
        if (method == RootMethod.Fast) {
            require(plan.exactKmiAsset && koAsset != null) {
                "Veyra DF+ requires an exact external KMI module for $kmi"
            }
            withContext(Dispatchers.IO) {
                app.assets.open(koAsset).use { input ->
                    require(input.read() >= 0) { "Empty DFRoot KMI module: $koAsset" }
                }
            }
        }
        appendLog("[*] ${plan.routeLabel}")
        plan.notes.forEach { note -> appendLog("[DF+] $note") }
        if (method == RootMethod.Fast) {
            appendLog("[DF+] exact module evidence: $koAsset")
        } else {
            appendLog("[DF Compatible] native embedded KMI route: $kmi")
        }
        val managerPackage = KernelSuManager.installedFor(app, flavor)?.packageName
        managerPackage?.let { appendLog("[DF] manager daemon source candidate: $it") }
        val report = mutableListOf<String>()
        val reporter = df.root.IReporter { line ->
            synchronized(report) {
                report += line
            }
        }

        val rc = try {
            withContext(Dispatchers.IO) {
                df.root.DFBridge.run(
                    app,
                    next,
                    false,
                    managerPackage,
                    reporter,
                )
            }
        } finally {
            synchronized(report) {
                report.forEach { line ->
                    if (line.isNotBlank()) appendLog(line.trimEnd())
                }
            }
        }

        require(rc == 0) {
            "DF engine failed (rc=$rc)"
        }

        appendLog(app.getString(R.string.log_bootstrap_root))

        repeat(8) { attempt ->
            val status = runCatching { KernelSuRuntime.status(app) }.getOrNull()
            if (status == KernelSuStatus.Active) {
                appendLog("[+] DF route: KernelSU control path active")
                return true
            }
            if (attempt < 7) delay(250)
        }

        appendLog("[*] DF route: root landed; KernelSU will be verified/loaded by Veyra")
        return false
    }

    /**
     * Runs the payload through the device's own adbd, over wireless debugging.
     *
     * This is the path for a target whose feed entry says it only works from a shell, on a device with
     * no usable Shizuku. The shell context is what such a payload needs, and the pairing is what
     * provides one without a cable.
     *
     * Two differences from the other transports are worth knowing. The helper and the payload are
     * *pushed* rather than staged through a binder, because there is no binder here. And the command
     * owns one open shell for its whole life, streamed: adbd kills a backgrounded process the moment
     * its shell closes, so a payload that ran detached would be killed at the start and the run would
     * wait out its whole ceiling for a process that no longer existed.
     *
     * Wireless debugging is turned on for this and off again afterwards by [TemporaryWirelessAdb], so
     * the window exists only while the payload does.
     */
    private suspend fun executeExploitOverLocalAdb(
        payload: File,
        kernelSu: File,
        rootHelper: File,
        requiresFreshP0Session: Boolean,
        routePolicy: ExploitRoutePolicy,
    ): Boolean {
        val bootToken = currentBootToken()
        val cachedP0Offset = if (requiresFreshP0Session) null else cachedP0Offset(bootToken)
        val logPrefix = mutableState.value.log
        val helper = rootHelper
        require(helper.isFile) { app.getString(R.string.error_helper_unavailable) }

        val totalMillis = activeCeilings.totalMillis
        // A socket handshake and a pushed upload are blocking work, and the run itself is driven from
        // the main dispatcher, so the whole transport lives on the IO dispatcher.
        val output = withContext(Dispatchers.IO) {
            TemporaryWirelessAdb.use(app) {
            WirelessAdbSession.open(app).use { session ->
                // Asked before anything is pushed, and required. Being authenticated is not the same as
                // being in the shell domain, and a payload started from the wrong one fails on the first
                // thing it reaches for - which reads like the exploit's fault rather than the shell's.
                localAdbShellIdentityFailure(session.shell("id"))?.let { reason ->
                    throw IllegalStateException(app.getString(R.string.error_local_adb_shell, reason))
                }
                appendLog(app.getString(R.string.log_local_adb_shell_ready))
                session.push(helper, ADB_HELPER_PATH, executable = true)
                session.push(payload, ADB_PAYLOAD_PATH)
                // Ahead of the payload for the same reason as the Shizuku route: the load happens
                // from the exploit process, so the daemon has to be there when it does.
                session.push(kernelSu, ADB_KSUD_PATH, executable = true)
                appendLog(app.getString(R.string.log_ksu_staged_early))
                session.runStreaming(
                    command = localAdbExploitCommand(
                        requiresFreshP0Session,
                        cachedP0Offset,
                        routePolicy,
                    ),
                    overallTimeoutMs = totalMillis,
                    // A fresh session is deliberately allowed to sit silent for as long as its ceiling:
                    // what it is doing is scanning, and a stall limit there would cut off the very run
                    // the ceiling was set for.
                    stallTimeoutMs = if (requiresFreshP0Session) totalMillis else activeCeilings.stallMillis,
                    shouldStop = { !mutableState.value.busy },
                ) { raw ->
                    if (!requiresFreshP0Session) cacheP0Offset(bootToken, raw)
                    publishExploitLog(logPrefix, raw)
                }
            }
        }
        }

        val exitCode = localAdbExploitExitCode(output)
        val recoveredKernelSu = recoverKernelSuAfterPayloadExit(exitCode, output)
        if (!recoveredKernelSu) {
            require(exitCode == 0) {
                app.getString(R.string.error_payload_exit, exitCode, payloadExitDetail(exitCode))
            }
            require(output.contains("exploit completed") && output.contains("done=1 root=1")) {
                app.getString(R.string.error_success_marker)
            }
        }
        appendLog(app.getString(R.string.log_bootstrap_root))
        return recoveredKernelSu
    }

    /**
     * A SIGKILL is recoverable only in the very narrow case we observed on the CZG3 payload:
     * the exploit already crossed the physical/KASLR stage and KernelSU is independently visible
     * afterwards. The signal alone is never accepted, and neither are the late-stage log markers
     * without a live kernel/root proof.
     */
    private suspend fun recoverKernelSuAfterPayloadExit(
        exitCode: Int,
        rawLog: String,
    ): Boolean {
        if (exitCode != PAYLOAD_SIGKILL_EXIT) return false
        val reachedLateKernelStage =
            rawLog.contains("slide-kaslr-ok") && rawLog.contains("fresh physrw pipe")
        if (!reachedLateKernelStage) return false

        repeat(PAYLOAD_KSU_RECOVERY_PROBES) { attempt ->
            val proofs = linkedSetOf<String>()

            if (RootStatusProbe.isActiveQuick()) {
                proofs += "native probe"
            }

            if (runCatching { KernelSuRuntime.moduleLoaded(app) }.getOrNull() == true) {
                proofs += "kernel module list"
            }

            if (runCatching { moduleListAsRoot() }.getOrNull() == true) {
                proofs += "bootstrap-root module list"
            }

            val rootAnswer = runCatching {
                KernelSuRuntime.rootShell("id", PAYLOAD_KSU_RECOVERY_SU_TIMEOUT_SECONDS)
            }.getOrNull()
            if (isRootAnswer(rootAnswer)) {
                proofs += "KernelSU su"
            }

            if (proofs.isNotEmpty()) {
                val proofText = proofs.joinToString()
                appendLog(app.getString(R.string.log_payload_sigkill_recovered, proofText))
                AppLog.warn(
                    RUN_LOG_TAG,
                    "Payload exited 137 after late kernel stage, but KernelSU is active ($proofText); continuing",
                )
                return true
            }

            if (attempt + 1 < PAYLOAD_KSU_RECOVERY_PROBES) {
                delay(PAYLOAD_KSU_RECOVERY_POLL_MILLIS)
            }
        }
        return false
    }

    private fun drainProcessOutput(process: Process, buffer: StringBuilder): String {
        return try {
            drainStream(process.inputStream, buffer)
            drainStream(process.errorStream, buffer)
            buffer.toString()
        } catch (_: Throwable) {
            buffer.toString()
        }
    }

    private fun drainStream(stream: InputStream, buffer: StringBuilder) {
        val data = ByteArray(4096)
        while (stream.available() > 0) {
            val count = stream.read(data)
            if (count <= 0) break
            buffer.append(String(data, 0, count, Charsets.UTF_8))
        }
    }

    private fun publishExploitLog(prefix: String, rawLog: String) {
        mutableState.value = mutableState.value.copy(
            log = listOf(prefix, stripAnsi(rawLog))
                .filter(String::isNotBlank)
                .joinToString("\n"),
        )
        updateHistoryLog()
    }

    private suspend fun waitForKernelSuAutoLateLoad(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + AUTO_LATE_LOAD_WAIT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val helperProbe = runCatching { runHelper("--ksu-info") }.getOrNull()
            if (helperProbe?.code == 0) return true

            if (runCatching { RootStatusProbe.isActive() }.getOrDefault(false)) return true
            if (runCatching { KernelSuRuntime.moduleLoaded(app) }.getOrNull() == true) return true

            val rootAnswer = runCatching {
                KernelSuRuntime.rootShell("id", AUTO_LATE_LOAD_ROOT_PROBE_SECONDS)
            }.getOrNull()
            if (isRootAnswer(rootAnswer)) return true

            delay(AUTO_LATE_LOAD_POLL_MILLIS)
        }
        return false
    }

    private suspend fun reSukiSuKmiLateLoad(
        payloads: VerifiedPayloads,
        kmi: String,
    ): CommandResult {
        val destination = "/data/local/tmp/ksud-pixel"
        val source = shellQuote(payloads.kernelSu.absolutePath)
        val stage = runHelper(
            "-c",
            "/system/bin/cp $source $destination && /system/bin/chmod 755 $destination",
        )
        require(stage.code == 0) {
            app.getString(R.string.error_ksu_stage, stage.output)
        }
        appendLog("[+] ReSukiSU staged for KMI $kmi")
        return runHelper(
            "-c",
            "$destination late-load --kmi " + shellQuote(kmi),
        )
    }

    private suspend fun installKernelSu(
        payloads: VerifiedPayloads,
        kernelSuAlreadyActive: Boolean = false,
    ) {
        val profileKmi = payloads.profile.kernelKmi
        val autoLoaded = kernelSuAlreadyActive || waitForKernelSuAutoLateLoad()
        val lateLoad = if (autoLoaded) {
            appendLog("[+] KernelSU auto-late-load verified; skipping duplicate late-load")
            CommandResult(code = 0, output = "KernelSU auto-late-load already active")
        } else if (
            payloads.profile.flavor == KernelSuFlavor.ReSukiSU &&
            !profileKmi.isNullOrBlank()
        ) {
            reSukiSuKmiLateLoad(payloads, profileKmi)
        } else {
            if (shizukuEnabled()) {
                shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_PATH, "755")
                shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_STAGE_PATH, "755")
                appendLog(app.getString(R.string.log_ksu_staged))
            } else {
                val source = shellQuote(payloads.kernelSu.absolutePath)
                val stageCommand =
                    "/system/bin/cp $source $SHIZUKU_KSUD_PATH && " +
                        "/system/bin/cp $source $SHIZUKU_KSUD_STAGE_PATH && " +
                        "/system/bin/chmod 755 $SHIZUKU_KSUD_PATH $SHIZUKU_KSUD_STAGE_PATH"
                val stage = runMaintenance(stageCommand)
                require(stage.code == 0) {
                    app.getString(R.string.error_ksu_stage, stage.output)
                }
                appendLog(app.getString(R.string.log_ksu_staged))
            }

            runHelper("--late-load")
        }
        // The payload's line is logged before anything is judged from it: on a non-zero exit it is
        // usually the payload's own diagnosis ("driver fd unavailable", "control check failed
        // ret=… flags=…"), and a refusal that swallows it sends the reader after a cause the run
        // already wrote down.
        if (lateLoad.output.isNotBlank()) appendLog(lateLoad.output)
        activeStage = RunStage.Verify
        // An exit code says the late-load command finished, not that anything is reachable now, so
        // the run states which independent reading confirmed the control channel before it claims
        // success - and refuses to claim it when none of them did.
        val firstLook = KernelSuRuntime.readings(app, lateLoad.output)
        // Then the route those readings cannot take on their own. Three of the four need a door a first
        // install has not opened, and the fourth - the kernel's own module list - is read either by the
        // app, which policy denies, or through a shell, which a run can be without. This run is holding
        // bootstrap root, so it asks again the way the module handling above does, and only when nothing
        // has answered yet: a reading that already spoke is not second-guessed.
        val readings = firstLook.withModuleList(
            if (firstLook.moduleLoaded == null && firstLook.proofs.isEmpty()) moduleListAsRoot() else null,
        )
        // Said every time, not only on a refusal: the readings are how the next person to read the
        // log tells an unusable load from a check that could not see a healthy one.
        appendLog(app.getString(R.string.log_ksu_control_readings, readings.summary()))
        // The readings outrank the payload's code rather than the other way round. That code reports
        // what the payload's own probe could reach - a driver fd opened through the `reboot` magic,
        // then a version and two flag bits - so a module that loaded while that route is refused
        // comes back non-zero, and judging the run on the code alone recorded a landed load as a
        // failed one: no receipt for the boot, and no manager handed over.
        when (loadVerdict(lateLoad.code, readings)) {
            LoadVerdict.Confirmed -> appendLog(
                app.getString(
                    R.string.log_ksu_control_verified,
                    readings.proofs.joinToString { it.label },
                ),
            )

            // The load landed and its own probe could not say so. Kept as a load, and said out loud,
            // because the receipt below is what makes the manager worth opening and the next run
            // refuse a second load for a boot that already has one.
            LoadVerdict.ConfirmedDespitePayload -> appendLog(
                app.getString(
                    R.string.log_ksu_control_verified_despite,
                    lateLoad.code,
                    readings.proofs.joinToString { it.label },
                ),
            )

            // The refusals, in the order they send a reader: the payload's own account first, then a
            // kernel that was looked at and lists no module, then a check that could not be made at all.
            LoadVerdict.RefusedByPayload -> error(
                app.getString(R.string.error_ksu_verify, lateLoad.code, lateLoad.output),
            )

            LoadVerdict.NotLoaded -> error(app.getString(R.string.error_ksu_not_ready))
            LoadVerdict.Unconfirmed -> error(app.getString(R.string.error_ksu_unconfirmed))
        }
        // Recorded for the boot, which is as long as a late-loaded module exists: it is what makes a
        // later run of the other flavour refuse with a restart instead of failing inside the loader.
        val loadedBootToken = AutoRootSupport.currentBootToken()
        AppPreferences.setLoadedFlavor(app, payloads.profile.flavor, loadedBootToken)
        RootMigrationStore.markCurrentProvider(app, app.packageName)
        storeInstallReceipt()

        // VeyraKSU's bridge state is deliberately independent from the future permission manager.
        // The bootstrap helper still owns root at this exact point, so it can leave a per-boot receipt
        // even before KernelSU has granted Veyra's normal app UID its own su profile.
        runCatching {
            val bridgeDir = "/data/adb/veyra_ksu"
            val flavor = shellQuote(payloads.profile.flavor.name)
            val boot = shellQuote(loadedBootToken.orEmpty())
            val bridge = runMaintenance(
                "mkdir -p $bridgeDir && chmod 700 $bridgeDir && " +
                    "printf 'flavor=%s\\nboot_id=%s\\nstarted_at=%s\\n' " +
                    "$flavor $boot \"$(date +%s 2>/dev/null)\" > $bridgeDir/temp_root.properties && " +
                    ": > $bridgeDir/direct_root_verified && " +
                    "chmod 600 $bridgeDir/temp_root.properties $bridgeDir/direct_root_verified",
            )
            if (bridge.code == 0) {
                appendLog(app.getString(R.string.log_veyra_ksu_temp_root_state))
            }
        }

        if (VeyraKsuPreferences.autoInstallAfterRoot(app)) {
            runCatching {
                val module = VeyraKsuModule.export(app)
                val install = runMaintenance(VeyraKsuModule.installCommand(module.path))
                require(install.code == 0 && install.output.contains("VeyraKSU_INSTALL_OK")) {
                    install.output.ifBlank {
                        app.getString(R.string.veyra_ksu_install_failed)
                    }
                }
                appendLog(app.getString(R.string.log_veyra_ksu_auto_installed))

                // The module is installed after KernelSU itself came up, so give the daemon one
                // idempotent late-load pass to consume modules_update and run the module lifecycle.
                // runMaintenance prefers KernelSU's own root broker and falls back to bootstrap root.
                val reload = runMaintenance(
                    "K=$(command -v ksud 2>/dev/null); " +
                        "[ -n \"${'$'}K\" ] || K=/data/adb/ksud; " +
                        "[ -x \"${'$'}K\" ] || exit 127; " +
                        "if \"${'$'}K\" --help 2>&1 | grep -q late-load; " +
                        "then \"${'$'}K\" late-load; else exit 0; fi",
                )
                require(reload.code == 0) {
                    reload.output.ifBlank {
                        "KernelSU module lifecycle returned ${reload.code}"
                    }
                }

                val verify = runMaintenance(VeyraKsuModule.verifyInstallCommand())
                require(verify.code == 0) {
                    verify.output.ifBlank {
                        "VeyraKSU module verification returned ${verify.code}"
                    }
                }
                appendLog(app.getString(R.string.log_veyra_ksu_auto_loaded))
            }.onFailure { error ->
                appendLog(
                    app.getString(
                        R.string.log_veyra_ksu_auto_failed,
                        error.message ?: error.javaClass.simpleName,
                    ),
                )
            }
        }

        val migration = RootMigrationStore.plan(app)
        if (migration.stage == RootMigrationStage.RebootRequested ||
            migration.stage == RootMigrationStage.Completing
        ) {
            RootMigrationStore.markCompleting(app)
            val provider = migration.providerPackage
            var cleanupSucceeded = true
            if (!provider.isNullOrBlank() && provider != app.packageName) {
                val quotedProvider = shellQuote(provider)
                val cleanup = runHelper(
                    "-c",
                    "pm disable-user --user 0 $quotedProvider >/dev/null 2>&1 || exit 41; " +
                        "pm clear --user 0 $quotedProvider >/dev/null 2>&1 || exit 42",
                )
                cleanupSucceeded = cleanup.code == 0
                if (cleanupSucceeded) {
                    appendLog(app.getString(R.string.log_migration_old_provider_disabled, provider))
                } else {
                    appendLog(app.getString(R.string.log_migration_old_provider_cleanup_failed, provider))
                }
            }
            if (cleanupSucceeded) {
                RootMigrationStore.complete(app)
                appendLog(app.getString(R.string.log_migration_completed))
            } else {
                RootMigrationStore.fail(app)
                appendLog(app.getString(R.string.log_migration_failed_cleanup))
            }
        }
        // The load is done, and a manager that was open while it happened is still showing what it read
        // before it. Stopping it is what makes the next open report the module this run just loaded,
        // rather than the "KernelSU not installed" its own stale read produces.
        val refreshed = KernelSuManagerRefresh.afterLoad(app, flavor = payloads.profile.flavor)
        refreshed.forEach { packageName ->
            appendLog(app.getString(R.string.log_manager_refreshed, packageName))
        }
    }

    private fun detectInstalled(): Boolean {
        // Called from the run's own dispatcher, so the `su` fallback behind this is off the main
        // thread: the native paths alone under-report on this hardware once the system locks down.
        if (RootStatusProbe.isActive()) return true
        val bootToken = currentBootToken() ?: return false
        val receipt = app.getSharedPreferences(INSTALL_RECEIPT, Application.MODE_PRIVATE)
        return receipt.getString(RECEIPT_BOOT_TOKEN, null) == bootToken &&
            receipt.getBoolean(RECEIPT_VERIFIED, false)
    }

    private fun storeInstallReceipt() {
        val bootToken = currentBootToken() ?: error(app.getString(R.string.error_boot_id))
        val stored = app.getSharedPreferences(INSTALL_RECEIPT, Application.MODE_PRIVATE)
            .edit()
            .putString(RECEIPT_BOOT_TOKEN, bootToken)
            .putBoolean(RECEIPT_VERIFIED, true)
            .commit()
        require(stored) { app.getString(R.string.error_receipt) }
    }

    private fun currentBootToken(): String? = kernelBootToken()

    /** The slide offset cached for this boot, if an earlier run found one. */
    internal fun cachedOffsetForThisBoot(): String? = cachedP0Offset(currentBootToken())

    private fun cachedP0Offset(bootToken: String?): String? {
        if (bootToken == null) return null
        val stored = app.getSharedPreferences(P0_CACHE, Application.MODE_PRIVATE)
        if (stored.getString(P0_CACHE_BOOT_TOKEN, null) != bootToken) return null
        return stored.getString(P0_CACHE_OFFSET, null)
    }

    private fun cacheP0Offset(bootToken: String?, log: String) {
        if (bootToken == null) return
        val match = P0_OFFSET_PATTERN.findAll(log).lastOrNull() ?: return
        val offset = match.groupValues[1].toLongOrNull(16) ?: return
        if (offset !in 0..P0_OFFSET_MAX || offset and P0_OFFSET_MASK != 0L) return
        val value = "0x${offset.toString(16)}"
        val stored = app.getSharedPreferences(P0_CACHE, Application.MODE_PRIVATE)
        if (stored.getString(P0_CACHE_BOOT_TOKEN, null) == bootToken &&
            stored.getString(P0_CACHE_OFFSET, null) == value
        ) return
        stored.edit()
            .putString(P0_CACHE_BOOT_TOKEN, bootToken)
            .putString(P0_CACHE_OFFSET, value)
            .apply()
    }

    private fun localAdbExploitCommand(
        requiresFreshP0Session: Boolean,
        cachedP0Offset: String?,
        routePolicy: ExploitRoutePolicy,
    ): String = buildString {
        // The environment comes first, quoted as values, because this is a shell command rather than
        // a process spawn with an environment attached.
        exploitEnvironment(
            requiresFreshP0Session,
            cachedP0Offset,
            routePolicy,
            payloadQuietWindowSeconds(),
        ).forEach { (name, value) ->
            append(name).append('=').append(shellQuote(value)).append(' ')
        }
        append(shellQuote(ADB_HELPER_PATH))
        append(" --run-payload")
        append(' ').append(shellQuote(ADB_PAYLOAD_PATH))
        append(' ').append(shellQuote(ADB_HELPER_PATH))
        append(' ').append(shellQuote(ADB_LOG_PATH))
        // The raw `shell:` service does not carry an exit code, so the command reports its own on the
        // end of the stream. Reading it back is what separates "the payload failed" from "the payload
        // finished and the run got nothing", which need different answers.
        append("; rc=").append('$').append('?').append("; printf '\\n")
        append(ADB_EXIT_MARKER).append("%s\\n' \"").append('$').append("rc\"")
    }

    private fun helperFile(): File {
        val source = activeRootHelper ?: nativeHelperFile()
        return if (shizukuEnabled()) {
            shizukuStage(source, SHIZUKU_HELPER_PATH, "755")
        } else {
            source
        }
    }

    private fun nativeHelperFile() = File(app.applicationInfo.nativeLibraryDir, "libcve43499root.so")

    private fun shizukuEnabled(): Boolean = activeRunShizuku ?: AppPreferences.shizukuMode(app)

    private fun shizukuStage(source: File, target: String, mode: String): File {
        val staged = File(target)
        if (stagedFileIsCurrent(staged, source)) return staged
        try {
            ShizukuController.writeFile(target, mode, source.inputStream())
        } catch (error: Throwable) {
            throw IllegalStateException(
                app.getString(R.string.error_shizuku_stage, target, error.message.orEmpty()),
                error,
            )
        }
        return staged
    }

    private fun shizukuEnvironment(
        payloadPath: String,
        helperPath: String,
        requiresFreshP0Session: Boolean,
        cachedP0Offset: String?,
        routePolicy: ExploitRoutePolicy,
    ): Array<String> = buildList {
        exploitEnvironment(
            requiresFreshP0Session,
            cachedP0Offset,
            routePolicy,
            payloadQuietWindowSeconds(),
        ).forEach { (name, value) ->
            add("$name=$value")
        }
        add("CVE43499_ROOT_HELPER=$helperPath")
        add("LD_PRELOAD=$payloadPath")
    }.toTypedArray()

    /**
     * Runs a privileged maintenance script through the best transport available.
     *
     * The helper's handoff socket only exists to cross the pre-KernelSU boundary, and once
     * KernelSU has loaded a Samsung kernel may refuse new connects to it while KernelSU itself is
     * healthy - which is exactly when these module scripts run. KernelSU's own root shell is
     * therefore tried first and the helper stays as the fallback for a boot where the temporary
     * socket is still the only way in.
     */
    /**
     * Marks the image partitions read-only, using the bootstrap root the exploit just produced.
     *
     * It goes through the helper directly rather than through [runMaintenance], and deliberately: at
     * this point KernelSU has not been loaded, so the transport KernelSU's own shell would need does
     * not exist yet. The helper's temporary socket is the only root here, which is the same reason
     * this runs now - the window it closes is exactly the window it runs in.
     */
    private suspend fun setPartitionBlocksToRo() {
        val script = runCatching {
            app.assets.open(PartitionReadOnly.SCRIPT_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (script == null) {
            // A build without the asset is a packaging fault, not a device condition, and saying so
            // is more useful than reporting that the devices could not be set.
            appendLog(app.getString(R.string.log_ro_blocks_script_missing))
            return
        }
        val result = runHelper("-c", script)
        val count = PartitionReadOnly.countFrom(result.output)
        if (count >= 1) {
            appendLog(app.getString(R.string.log_ro_blocks_successful, count))
        } else {
            appendLog(app.getString(R.string.log_ro_blocks_failed))
        }
        protectedDevices = count
        AppLog.info(
            RUN_LOG_TAG,
            if (count >= 1) {
                "Set $count partitions read-only for this boot"
            } else {
                "Partition protection was on, but no block device could be set read-only"
            },
        )
        // Marked after the line above, so the scan that attributes a later failure to the protection
        // starts below the line that reports it rather than reading it back as evidence.
        protectedFrom = mutableState.value.log.length
        // Written to the boot as well as kept here: a repair action run from Settings later in this
        // boot is a different process from this run, and without this it could only guess whether the
        // wall it just hit was this app's.
        AppPreferences.setReadOnlyProtectedDevices(app, kernelBootToken(), count)
    }

    private suspend fun runMaintenance(command: String): CommandResult {
        // Once KernelSU has landed, its own su route is more reliable than the bootstrap handoff:
        // Samsung may close the latter as the new kernel-side control path becomes active. Before
        // KernelSU exists this simply returns null and the exact legacy helper path is used.
        val viaKernelSu = KernelSuRuntime.rootShell(command)
            ?: if (CVeyraPreferences.enabled(app)) {
                CVeyraController.rootShell(app, command)
            } else {
                null
            }
        return viaKernelSu
            ?.let { CommandResult(it.exitCode, it.output) }
            ?: runHelper("-c", command)
    }

    /**
     * The kernel's module list, read with the root this run is still holding.
     *
     * The reading [KernelSuRuntime.moduleLoaded] cannot make on its own here: its two routes are the app's
     * own read of `/proc/modules`, which policy denies, and a shell through Shizuku, which a run of this app
     * can be without - and on a first install it usually is. That leaves the bootstrap root the exploit just
     * obtained, which is not a lesser answer: the module list is a fact about the kernel and this is the same
     * transport the module handling above uses to act on that kernel.
     *
     * Null when nothing answered, including the case the handoff is designed to have: a Samsung kernel may
     * refuse new connections to it once KernelSU is loaded and healthy. That is not a reason for the run to
     * die here - the refusal that follows is a refusal to claim a load, not a proof of one.
     */
    private suspend fun moduleListAsRoot(): Boolean? {
        val result = runCatching { runMaintenance(MODULE_LIST_COMMAND) }.getOrNull() ?: return null
        return when (result.code) {
            0 -> true
            // grep's own "nothing matched", which is an answer rather than a failure.
            1 -> false
            else -> null
        }
    }

    /**
     * Runs the bootstrap helper for a short management command. Unlike the
     * exploit run there is no log file to poll, so output is drained inline
     * and a hard deadline guards against a helper that never exits — without
     * this, a hung `--late-load` leaves the install stuck in LoadingKernelSu
     * indefinitely.
     */
    private suspend fun runHelper(vararg arguments: String): CommandResult {
        val helper = helperFile()
        val process = if (shizukuEnabled()) {
            ShizukuController.exec(arrayOf(helper.absolutePath) + arguments)
        } else {
            ProcessBuilder(listOf(helper.absolutePath) + arguments)
                .redirectErrorStream(true)
                .start()
        }
        val captured = StringBuilder()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (process.isAlive) {
                drainProcessOutput(process, captured)
                require(SystemClock.elapsedRealtime() - startedAt < activeCeilings.helperMillis) {
                    app.getString(
                        R.string.error_helper_timeout,
                        captured.toString().trim().takeIf(String::isNotBlank)
                            ?.let { ": $it" } ?: "",
                    )
                }
                delay(HELPER_POLL_INTERVAL)
            }
            drainProcessOutput(process, captured)
            val exitCode = process.waitFor()
            return CommandResult(exitCode, stripAnsi(captured.toString().trim()))
        } finally {
            if (process.isAlive) {
                process.destroy()
                delay(500.milliseconds)
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

    /**
     * The target the cached payload names, refusing a selection that is not it.
     *
     * A run asked for offline cannot fall back to the network, so the selection has to agree with the
     * cache rather than be resolved against a catalog: there is no catalog to resolve it against.
     */
    private fun cachedProfileFor(selectionId: String?): TargetProfile = KnownGoodPayloadStore.profileFor(
        app,
        selectionId?.let { profileFromSelectionId(it) },
    )

    /**
     * The payload's own post-boot window, from this run's settle decision.
     *
     * Read where the environment is built rather than where the run starts, so a mid-run override is
     * part of the value: the user tapping "run anyway" while the countdown is on screen is exactly the
     * case that used to skip this app's wait and then sit out the payload's.
     */
    private fun payloadQuietWindowSeconds(): Int =
        BootSettle.payloadQuietWindowSeconds(bootSettleRequiredSeconds, bootSettleOverridden)

    /**
     * Holds the run until the device has been up long enough, reporting the remaining time as it goes.
     *
     * The countdown is the phase message, so it is on the run screen's status card rather than only in
     * the log, and the wait ends early when the user says so. The elapsed clock is read every tick
     * rather than accumulated, so the app sleeping through part of the wait does not make the run
     * believe it waited longer than it did.
     */
    private suspend fun awaitBootSettle(requiredSeconds: Int) {
        val required = BootSettle.normalize(requiredSeconds)
        bootSettleRequiredSeconds = required
        if (required <= 0) return
        val remaining = BootSettle.remainingMillis(required, BootSettle.elapsedMillis())
        if (remaining <= 0L) {
            appendLog(app.getString(R.string.log_boot_settled, BootSettle.label(required)))
            AppLog.info(RUN_LOG_TAG, "Boot settle already met (${BootSettle.label(required)})")
            return
        }
        appendLog(app.getString(R.string.log_boot_settle, BootSettle.formatRemaining(remaining)))
        AppLog.info(RUN_LOG_TAG, "Waiting ${BootSettle.formatRemaining(remaining)} for the boot to settle")
        while (true) {
            if (bootSettleOverridden) {
                appendLog(app.getString(R.string.log_boot_settle_skipped))
                AppLog.warn(RUN_LOG_TAG, "Boot settle skipped on the user's word")
                // Says what the skip does not skip: the payload keeps a moment of its own over the same
                // boot, and without this the pause that follows reads as a run that hung. Its own log
                // line names the gate it used either way, so the two together account for the wait.
                AppLog.info(
                    RUN_LOG_TAG,
                    "Payload still waits ${BootSettle.label(payloadQuietWindowSeconds())} " +
                        "for the boot's allocator",
                )
                return
            }
            // Minutes of waiting, and the one other place a stop reaches: the settle is the longest part
            // of a run that is not the exploit, and a run that has not started the payload yet is the
            // safest one to stop.
            stopIfAskedFromOutside()
            val left = BootSettle.remainingMillis(required, BootSettle.elapsedMillis())
            if (left <= 0L) {
                appendLog(app.getString(R.string.log_boot_settled, BootSettle.label(required)))
                return
            }
            setPhase(
                InstallPhase.Settling,
                app.getString(R.string.status_boot_settle, BootSettle.formatRemaining(left)),
            )
            delay(BOOT_SETTLE_TICK_MILLIS)
        }
    }

    /**
     * Ends the run when the Stop in its notification was tapped.
     *
     * The same ending as [stopRun], reached the other way: the flag is set first, because the cancellation
     * handler reads it to tell "stopped" from "broke", and the exception is what actually cancels - thrown
     * from inside the run rather than from a `cancel()` call, so it arrives at the next tick of whatever wait
     * the run is in rather than at the first suspension point of some other coroutine.
     *
     * Called from the two waits that can last minutes: the boot settle and the exploit's own poll loop.
     */
    private fun stopIfAskedFromOutside() {
        if (!RunStopSignal.consumeFor(app, currentBootToken())) return
        stopRequested = true
        AppLog.warn(RUN_LOG_TAG, "Stop requested from the run notification")
        throw CancellationException("stop requested from the run notification")
    }

    private fun setPhase(phase: InstallPhase, message: String) {
        mutableState.value = mutableState.value.copy(phase = phase, message = message)
        // Written down as it moves, not only in memory: the phase is what a bar is drawn from, and the bar
        // has to be drawable by a screen in the app's own process for a run that is happening in another
        // one. The entry is saved per log line already, so this adds no write of its own.
        updateHistory { entry -> entry.copy(phase = phase) }
        appendLog("[*] $message")
        // The run, in the shade, for the length of a run that is usually spent with the phone in a pocket.
        // Not for an unattended run: that one has the boot gate's own notification, and two of them saying
        // the same thing is how the shade stops being read.
        if (!runIsUnattended) {
            RunNotification.post(
                context = app,
                message = message,
                progress = installProgress(phase, failureStage = null),
                runId = activeRunId,
            )
        }
    }

    private fun appendLog(line: String) {
        val cleanLine = stripAnsi(line).trim()
        if (cleanLine.isBlank()) return
        mutableState.value = mutableState.value.copy(
            log = (mutableState.value.log + "\n" + cleanLine).trim(),
        )
        updateHistoryLog()
    }

    private fun startHistory() {
        val entry = historyStore.create()
        activeHistoryEntry = entry
        activeRunId = entry.id
        publishHistory(entry)
    }

    /**
     * A run in flight that is not this view model's own, or null.
     *
     * The record is written per *process*, and a process can hold more than one view model - the screens
     * each keep their own - so a pid on its own cannot say whether the run belongs to this one. The entry id
     * can: a run names the history entry it is writing, and this view model knows which entry it started.
     *
     * The difference matters in one direction only. Reading another process's run as this one's own would
     * let the second attempt through, which is the bug; reading this process's own run as another's would
     * refuse a run that should have started, and that is what the entry id is for.
     */
    private fun runInFlightElsewhere(): RunHolder? {
        val holder = RunInFlight.holder(app) ?: return null
        val mine = holder.pid == android.os.Process.myPid() &&
            holder.entryId != null &&
            holder.entryId == activeRunId
        return holder.takeUnless { mine }
    }

    /**
     * Re-reads the run history from disk.
     *
     * The store is written as a run goes - every log line is saved with it - so this is what makes one
     * run's record live on a screen that is not in the process running it. The boot gate installs from its
     * own process, and a run that failed with its process gone is the other case: both are on disk and
     * neither is in this process's memory.
     */
    fun reloadHistory() {
        mutableHistory.value = historyStore.load()
    }

    private fun updateHistory(transform: (InstallHistoryEntry) -> InstallHistoryEntry) {
        val entry = activeHistoryEntry ?: return
        val updated = transform(entry)
        activeHistoryEntry = updated
        historyStore.save(updated)
        publishHistory(updated)
    }

    private fun updateHistoryLog() =
        updateHistory { it.copy(log = mutableState.value.log) }

    /**
     * Records which target ran and which catalog defined it, including the commit that catalog was
     * read at, so a finished run can be traced back to a revision rather than to a branch name.
     */
    private fun updateHistoryTarget(profile: TargetProfile) =
        updateHistory { entry ->
            entry.copy(
                profileId = profile.profileId,
                sourceId = profile.sourceId.takeIf(String::isNotBlank),
                sourceLabel = profile.sourceLabel.takeIf(String::isNotBlank),
                sourceCommit = profile.sourceCommit.takeIf(String::isNotBlank),
            )
        }

    private fun finishHistory(result: InstallRunResult) {
        updateHistory { entry ->
            entry.copy(
                completedAtMillis = System.currentTimeMillis(),
                result = result,
                log = mutableState.value.log,
                // Cleared, because it means "where a run in flight has got to": a finished record that still
                // carried a phase would be one a screen could draw a live bar for, which is the state this
                // field exists to make visible and must not outlive.
                phase = null,
            )
        }
        activeHistoryEntry = null
    }

    private fun publishHistory(entry: InstallHistoryEntry) {
        mutableHistory.value = (mutableHistory.value.filterNot { it.id == entry.id } + entry)
            .sortedByDescending(InstallHistoryEntry::startedAtMillis)
    }

    private fun File.readTextIfPresent(): String = if (exists()) readText() else ""

    companion object {
        /** How often the settle countdown is redrawn; a second would look like it stutters. */
        private const val BOOT_SETTLE_TICK_MILLIS = 500L

        // The ceilings themselves live in [RunLimits], which is where their defaults, the values the
        // settings offer and the fresh-session floor are kept together with the rule that resolves them.
        // A profile that needs one fresh P0 session hands the pacing to the payload, and the payloads
        // that ask for it scan pages for far longer than the cached multi-attempt budget ever needed:
        // the fresh-session proposal allowed a single 840-second attempt, and the controlled-page-scan
        // one 1200 s of scan plus 2200 s of attempt. A fifteen-minute ceiling would cut exactly those
        // runs off, which is why the settings cannot lower a fresh session below the app's own hour.
        // It is a limit, not a schedule - a run still ends when the payload finishes.

        private const val MODULES_DIRECTORY = "/data/adb/modules"
        private const val MODULES_BACKUP_DIRECTORY = "/data/adb/modules_rmg_backup"
        private const val MODULES_MOVED_MARKER = "modules-moved"
        private const val MODULES_RESTORED_MARKER = "modules-restored"
        private const val MODULES_BACKUP_EXISTS = 5

        // `mv` into an existing directory nests the source inside it, so the two scripts below
        // check for the backup first and refuse rather than bury a module tree somewhere else.
        /**
         * Where a wireless run stages its helper, payload and log, all under the shell's own directory.
         *
         * The `rmgnext-` prefix is the fork's own generation of names. Both installs share one
         * `/data/local/tmp`, and the names this app is free to choose are chosen apart from the ones the
         * app it came from writes - [StagedResidue] is the whole picture, and [StagingSweep] is what
         * reads it.
         */
        private const val ADB_HELPER_PATH = "/data/local/tmp/rmgnext-ksud-helper"
        private const val ADB_PAYLOAD_PATH = "/data/local/tmp/rmgnext-payload"
        private const val ADB_LOG_PATH = "/data/local/tmp/rmgnext-exploit.log"

        /**
         * Where the KernelSU daemon goes, whichever transport put it there.
         *
         * One path and not one per transport: the name is the payload's, not the app's - its helper
         * looks for the daemon at this exact path when it loads KernelSU itself - so a run that
         * staged it anywhere else would leave the payload with nothing to load. That is also why it is
         * the one name here that does not carry the fork's prefix, and why a sweep only asks about it
         * while there is no other install on the device that writes it too.
         */
        private const val KSUD_PATH = "/data/local/tmp/ksud-s25u-kdp"

        /** The copy the payload's late-load reads, and the second of the two names it owns. */
        private const val KSUD_STAGE_PATH = "/data/local/tmp/.ksud-stage"
        private const val ADB_KSUD_PATH = KSUD_PATH

        private val MODULES_ASIDE_SCRIPT = """
            if [ -d $MODULES_BACKUP_DIRECTORY ] && [ ! -e $MODULES_DIRECTORY ]; then
                /system/bin/mv $MODULES_BACKUP_DIRECTORY $MODULES_DIRECTORY || exit 3
            fi
            if [ -d $MODULES_BACKUP_DIRECTORY ]; then
                exit $MODULES_BACKUP_EXISTS
            fi
            if [ -d $MODULES_DIRECTORY ]; then
                /system/bin/mv $MODULES_DIRECTORY $MODULES_BACKUP_DIRECTORY || exit 4
                echo $MODULES_MOVED_MARKER
            fi
        """.trimIndent()

        private val MODULES_RESTORE_SCRIPT = """
            if [ -d $MODULES_BACKUP_DIRECTORY ]; then
                if [ -e $MODULES_DIRECTORY ]; then
                    echo modules-already-present
                else
                    /system/bin/mv $MODULES_BACKUP_DIRECTORY $MODULES_DIRECTORY || exit 3
                    echo $MODULES_RESTORED_MARKER
                fi
            fi
        """.trimIndent()
        private const val INSTALL_RECEIPT = "install_receipt"
        private const val RECEIPT_BOOT_TOKEN = "kernel_boot_id"
        private const val RECEIPT_VERIFIED = "verified"
        private const val P0_CACHE = "p0_cache"
        private const val P0_CACHE_BOOT_TOKEN = "kernel_boot_id"
        private const val P0_CACHE_OFFSET = "offset"
        private const val P0_OFFSET_MAX = 0x1f0000L
        private const val P0_OFFSET_MASK = 0xffffL
        private const val SHIZUKU_LOG_PATH = "/data/local/tmp/rmgnext-shizuku-exploit.log"
        private const val SHIZUKU_HELPER_PATH = "/data/local/tmp/rmgnext-helper"
        private const val SHIZUKU_PAYLOAD_PATH = "/data/local/tmp/rmgnext-shizuku-payload"
        private const val SHIZUKU_KSUD_PATH = KSUD_PATH
        private const val SHIZUKU_KSUD_STAGE_PATH = KSUD_STAGE_PATH

        /** The kernel's loaded modules, by name; grep's exit code is the answer. */
        private const val MODULE_LIST_COMMAND = "/system/bin/grep -w kernelsu /proc/modules"
        private const val PAYLOAD_SIGKILL_EXIT = 137
        private const val PAYLOAD_KSU_RECOVERY_PROBES = 3
        private const val PAYLOAD_KSU_RECOVERY_POLL_MILLIS = 500L
        private const val PAYLOAD_KSU_RECOVERY_SU_TIMEOUT_SECONDS = 3L
        private const val AUTO_LATE_LOAD_WAIT_MILLIS = 8_000L
        private const val AUTO_LATE_LOAD_POLL_MILLIS = 400L
        private const val AUTO_LATE_LOAD_ROOT_PROBE_SECONDS = 2L
        private val LOG_POLL_INTERVAL = 250.milliseconds
        private val HELPER_POLL_INTERVAL = 250.milliseconds
        private val SHIZUKU_LOG_POLL_INTERVAL = 1.seconds
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
        private val P0_OFFSET_PATTERN = Regex(
            "slide-kaslr-ok[^\\n]*slide=([0-9a-fA-F]{16})",
        )

        /** The ceilings a run would get right now, from the settings. */
        internal fun runCeilings(context: Context, freshSession: Boolean): RunCeilings = RunLimits.resolve(
            stallSeconds = AppPreferences.runStallSeconds(context),
            totalSeconds = AppPreferences.runTotalSeconds(context),
            helperSeconds = AppPreferences.runHelperSeconds(context),
            freshSession = freshSession,
        )

        /**
         * The environment and cut-offs a run gets, assembled from the same constants the run uses
         * so the run-plan screen cannot drift from what actually happens. The direct transport
         * inherits the app's environment and only overrides these names; the Shizuku transport
         * passes its whole environment as `NAME=value` arguments, so the staged payload and helper
         * paths are part of it.
         */
        internal fun exploitPlan(
            requiresFreshP0Session: Boolean,
            cachedP0Offset: String?,
            shizuku: Boolean,
            // Required, with no default: a default here would have to guess whether the run is a fresh
            // session, and guessing wrong is exactly the drift - origins that describe a policy other
            // than the one being planned - that EffectiveRoutePolicy exists to prevent.
            routePolicy: EffectiveRoutePolicy,
            bootSettleSeconds: Int = 0,
            ceilings: RunCeilings = RunLimits.defaultCeilings(requiresFreshP0Session),
        ): ExploitPlan = ExploitPlan(
            // The same resolved ceilings the run enforces, so the plan cannot describe a limit the run
            // will not apply - and so a value chosen in the settings shows up in both.
            bootSettleSeconds = BootSettle.normalize(bootSettleSeconds),
            routePolicy = routePolicy,
            // `overridden = false` because a plan is read before a run exists: the override is offered
            // while the countdown is on screen, so the value here is the setting's own - and the run's
            // log records what it actually handed over.
            environment = exploitEnvironment(
                requiresFreshP0Session,
                cachedP0Offset,
                routePolicy.policy,
                BootSettle.payloadQuietWindowSeconds(bootSettleSeconds, overridden = false),
            ),
            shizukuArguments = if (shizuku) {
                mapOf(
                    "CVE43499_ROOT_HELPER" to SHIZUKU_HELPER_PATH,
                    "LD_PRELOAD" to SHIZUKU_PAYLOAD_PATH,
                )
            } else {
                emptyMap()
            },
            stallLimitMillis = if (requiresFreshP0Session) null else ceilings.stallMillis,
            totalLimitMillis = ceilings.totalMillis,
            helperLimitMillis = ceilings.helperMillis,
        )

        internal fun exploitEnvironment(
            requiresFreshP0Session: Boolean,
            cachedP0Offset: String?,
            routePolicy: ExploitRoutePolicy = ExploitRoutePolicy.LEGACY,
            // Required, with no default: a default here would have to guess the run's settle decision,
            // and guessing it is how the app's gate and the payload's came to disagree about one boot.
            payloadQuietWindowSec: Int,
        ): Map<String, String> = buildMap {
            // First, because it is the wait rather than a knob: by the time the payload reads this the
            // app has finished its own settle, and this is what the payload waits on top of it.
            put(BootSettle.PAYLOAD_QUIET_WINDOW_ENV, payloadQuietWindowSec.toString())
            // A fresh-session profile hands its pacing to the payload, so the policy's attempt and
            // timeout budget does not apply to it. The route still does: which way the payload finds
            // the slide is a different question from how many tries it gets.
            put(
                "EXPLOIT_ATTEMPTS",
                if (requiresFreshP0Session) "1" else routePolicy.attempts.toString(),
            )
            if (!requiresFreshP0Session) {
                put("P0_ATTEMPT_TIMEOUT_SEC", routePolicy.p0AttemptTimeoutSec.toString())
                put("EXPLOIT_ATTEMPT_TIMEOUT_SEC", routePolicy.attemptTimeoutSec.toString())
                if (routePolicy.p0OffsetCache) {
                    cachedP0Offset?.let { put(ExploitRoutePolicy.P0_OFFSET_ENV, it) }
                }
            }
            routePolicy.slideRoute.env?.let { put(ExploitRoutePolicy.SLIDE_SOURCE_ENV, it) }
        }

        private fun stripAnsi(value: String): String = ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
