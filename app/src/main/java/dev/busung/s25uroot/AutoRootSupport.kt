package ctrl.mietze.veyraroot

import android.content.Context

/**
 * Per-boot bookkeeping for the automatic install.
 *
 * Everything here is keyed by the kernel's boot id rather than by a timestamp, because the events
 * this has to tell apart are not the same kind of thing: a real reboot, a userspace restart that
 * re-emits `BOOT_COMPLETED` while the same kernel stays up, and two replies from the same boot.
 * A token that does not change is the only signal that distinguishes them.
 */
/** Why the gate does, or does not, start an automatic install for this boot. */
internal enum class AutoRootDecision {
    Run,

    /**
     * Nothing asked for an install in this boot: root on boot is off and no retry is armed.
     *
     * Either one asks, and either one is the same answer here - a boot with nothing behind it is not a
     * boot to report on, unlike [SkipKernelSuLoadingOff], where something *did* ask and could not be given
     * what it asked for.
     */
    SkipDisabled,

    /**
     * Root on boot is on and KernelSU loading is off, so there is nothing for a boot run to load.
     *
     * Its own reason rather than [SkipDisabled]: the setting is still on, and a boot that silently did
     * nothing would look exactly like the setting having been turned off - which is the one thing an
     * unattended feature must not do.
     */
    SkipKernelSuLoadingOff,
    SkipAlreadyRooted,
    SkipAlreadyVerified,
    SkipAttempted,

    /**
     * Another run already has the device, so this boot's automatic attempt would be a second one.
     *
     * Its own reason rather than [SkipAttempted]: nothing about this boot has been spent, and a boot that
     * stood down because the user was already installing is not a boot whose attempt is gone - the run in
     * flight is the attempt, made by hand, and its result is the one that matters.
     */
    SkipRunInFlight,
    NeedsPriorInstall,
}

/**
 * Whether the payload a boot run will use says it needs a shell.
 *
 * Pure, because the interesting part is not the reading but which reading counts: a boot that is
 * repeating an attempt runs the attempted payload, and a root-on-boot run runs the cached one, so the
 * two descriptors can disagree and only the one the run will actually resolve is the answer. Neither is
 * consulted at all on a device with nothing recorded.
 *
 * True when nothing answers, and that default is the conservative one. A boot whose payload cannot be
 * read is a boot that waits and then offers its two answers, which is exactly what it did before this
 * question existed; answering "no shell" there would spend the one attempt this boot gets running a
 * payload a way it may not be able to run.
 */
internal fun bootPayloadNeedsShell(
    preferAttempted: Boolean,
    attempted: CachedPayload?,
    cached: CachedPayload?,
): Boolean = ((if (preferAttempted) attempted else null) ?: cached)
    ?.routePolicy
    ?.prefersShellTransport
    ?: true

/**
 * Whether taking back the install in front of you also takes back the retry this device has armed.
 *
 * Asked when the notification's skip is tapped, and the answer is not "always": the retry has to be the
 * one that *asked for this boot*. A retry armed while this boot is already running is a request for the
 * next one - it was armed in this boot, which is exactly what makes it wait - and clearing it would take
 * back something the user just asked for, under a button that says the opposite.
 *
 * An unreadable boot id takes nothing back. The two ids are what the question is made of, and without one
 * of them there is no way to tell a request this boot is honouring from one it is not, so the armed retry
 * is left where it is - the gate cannot run anything without a boot id anyway.
 */
internal fun skipTakesBackRetry(armedForBoot: String?, bootToken: String?): Boolean =
    armedForBoot != null && bootToken != null && armedForBoot != bootToken

/**
 * The gate's whole rule, as one pure decision.
 *
 * Order matters and is the reason this is not spread through the service: a boot that already has
 * root is not a boot that needs an install, a boot whose attempt is spent does not get a second one,
 * and an install that was verified *in this boot* stays verified across the userspace restarts that
 * re-emit BOOT_COMPLETED. Only the last case asks anything of the user.
 *
 * [hasVerifiedInstall] means "this boot has something runnable behind it", not "a run once succeeded":
 * a retry is armed by a run that failed, and on a device testing a payload that has never completed
 * one, the attempt it repeats is the only thing there is to run from.
 */
internal fun autoRootDecision(
    enabled: Boolean,
    kernelSuLoadEnabled: Boolean,
    kernelSuActive: Boolean,
    runInFlight: Boolean,
    hasVerifiedInstall: Boolean,
    verifiedBootToken: String?,
    attemptedBootToken: String?,
    bootToken: String,
    /**
     * Whether a one-shot retry armed before the restart is what asked for this boot's install.
     *
     * A parameter rather than folded into [enabled] by the caller, because the two are different
     * requests with the same effect and only one is a setting: an armed retry is a tap the user made,
     * and it gets the boot's attempt even when root on boot is off. Folding it in hid that - the rule
     * looked as if the automation had been switched on, which is why a retry boot used to announce
     * itself as one and to offer to turn off a setting that was never why it ran.
     */
    retryArmed: Boolean = false,
): AutoRootDecision = when {
    !enabled && !retryArmed -> AutoRootDecision.SkipDisabled
    // A configuration refusal, so it sits with the setting above rather than with the readings: this
    // boot is not being asked to load anything, whatever the device looks like.
    !kernelSuLoadEnabled -> AutoRootDecision.SkipKernelSuLoadingOff
    kernelSuActive -> AutoRootDecision.SkipAlreadyRooted
    verifiedBootToken == bootToken -> AutoRootDecision.SkipAlreadyVerified
    attemptedBootToken == bootToken -> AutoRootDecision.SkipAttempted
    // Before the cache is asked about, because a run in flight is the answer to that question: the device
    // has something runnable behind it - it is being run right now - and telling someone to run one online
    // installation while they are watching one is the kind of line that reads as the app not looking.
    runInFlight -> AutoRootDecision.SkipRunInFlight
    !hasVerifiedInstall -> AutoRootDecision.NeedsPriorInstall
    else -> AutoRootDecision.Run
}

internal object AutoRootSupport {
    private const val RECEIPT = "install_receipt"
    private const val RECEIPT_VERIFIED = "verified"
    private const val RECEIPT_BOOT_TOKEN = "kernel_boot_id"
    private const val STATE = "auto_root_state"
    private const val LAST_BOOT_COMPLETED_TOKEN = "last_boot_completed_boot_id"
    private const val LAST_ATTEMPT_TOKEN = "last_attempt_boot_id"

    fun currentBootToken(): String? = kernelBootToken()

    /** Whether an install has been verified on this device at all, which is what a boot run needs. */
    fun hasVerifiedInstall(context: Context): Boolean =
        context.getSharedPreferences(RECEIPT, Context.MODE_PRIVATE)
            .getBoolean(RECEIPT_VERIFIED, false) && KnownGoodPayloadStore.hasValid(context)

    /**
     * Whether the payload this boot is about to run says it needs a shell.
     *
     * The descriptors are read here and the rule about which of them counts is [bootPayloadNeedsShell],
     * which is where a test can reach it: what this adds is the reading, from the same two places a run
     * resolves its payload from and not from the catalog, because a boot may have no network at all and
     * the payload a boot run uses is by definition one that is already on the device.
     */
    fun bootPayloadNeedsShell(context: Context, preferAttempted: Boolean): Boolean = bootPayloadNeedsShell(
        preferAttempted = preferAttempted,
        attempted = AttemptedPayloadStore.describe(context),
        cached = KnownGoodPayloadStore.describe(context),
    )

    /** The boot an install was last verified in, or null when none has been. */
    fun verifiedBootToken(context: Context): String? {
        val preferences = context.getSharedPreferences(RECEIPT, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(RECEIPT_VERIFIED, false)) return null
        return preferences.getString(RECEIPT_BOOT_TOKEN, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    /**
     * Whether this boot still needs an install.
     *
     * The token is the whole test: after a userspace restart the boot id is unchanged, and an install
     * that was verified in this boot is still valid, so a second `BOOT_COMPLETED` must not start one.
     */
    fun shouldRunForBoot(context: Context, bootToken: String): Boolean =
        verifiedBootToken(context) != bootToken

    /** Records that root was verified in [bootToken], which is what stops a re-run within it. */
    fun markVerifiedForBoot(context: Context, bootToken: String) {
        val stored = context.getSharedPreferences(RECEIPT, Context.MODE_PRIVATE)
            .edit()
            .putString(RECEIPT_BOOT_TOKEN, bootToken)
            .putBoolean(RECEIPT_VERIFIED, true)
            .commit()
        require(stored) { context.getString(R.string.error_receipt) }
    }

    /**
     * Consumes the framework's `BOOT_COMPLETED` for this kernel boot exactly once.
     *
     * A userspace restart can publish another one while the kernel stays up, and treating that as a
     * fresh boot is how a boot automation starts twice. Returns false when this boot has been seen.
     */
    @Synchronized
    fun claimBootCompletedForKernel(context: Context, bootToken: String): Boolean {
        val preferences = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (preferences.getString(LAST_BOOT_COMPLETED_TOKEN, null) == bootToken) return false
        return preferences.edit()
            .putString(LAST_BOOT_COMPLETED_TOKEN, bootToken)
            .commit()
    }

    fun hasAttemptedBoot(context: Context, bootToken: String): Boolean =
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            .getString(LAST_ATTEMPT_TOKEN, null) == bootToken

    /**
     * Claims this boot's single attempt.
     *
     * One attempt per boot is the point: the exploit is a race, and spending it twice in the same
     * boot tells the user nothing the first attempt did not, while the second attempt is what a
     * half-finished first one would collide with.
     */
    @Synchronized
    fun claimAttempt(context: Context, bootToken: String): Boolean {
        val preferences = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (preferences.getString(LAST_ATTEMPT_TOKEN, null) == bootToken) return false
        val stored = preferences.edit()
            .putString(LAST_ATTEMPT_TOKEN, bootToken)
            .commit()
        // Spending the attempt is what consumes a one-shot retry: the flag means "this boot gets an
        // install it was not otherwise owed", and it has now been given one.
        if (stored) AppPreferences.setRetryAfterReboot(context, null)
        return stored
    }

    /**
     * The gate's decision for this boot, from the rules in [autoRootDecision].
     *
     * [kernelSuActive] is passed in rather than probed here so the rule itself has no device in it,
     * and so a caller that has already probed does not have to probe again to ask the question.
     */
    fun decision(context: Context, bootToken: String, kernelSuActive: Boolean): AutoRootDecision {
        // A retry armed before a reboot is the other way this boot can have been asked for one install,
        // and it is passed as its own input rather than folded into the setting - the rule says what a
        // retry is worth to a boot (the attempt) without also claiming root on boot was switched on. It
        // only counts in a boot *other* than the one that armed it, so arming
        // it cannot start the attempt the user declined when they chose to reboot.
        val retryArmed = AppPreferences.retryPendingForBoot(context, bootToken)
        return autoRootDecision(
            enabled = AppPreferences.bootRootMode(context),
            retryArmed = retryArmed,
            kernelSuLoadEnabled = AppPreferences.loadKernelSu(context),
            kernelSuActive = kernelSuActive,
            // Read here rather than passed in, because it is the one input that is not a preference: the
            // record is whatever the processes on this device have written down, this one included - and
            // this one has not started anything yet, since the gate is what is asking.
            runInFlight = RunInFlight.holder(context) != null,
            // An install behind it, or an attempt to repeat. Requiring the first for a retry is how a
            // device testing a payload that has never completed a run was told to "run one online
            // installation first" about a retry it had asked for by hand - while the payload it was
            // testing sat on the device, recorded as the attempt that failed.
            hasVerifiedInstall = hasVerifiedInstall(context) ||
                (retryArmed && AttemptedPayloadStore.hasRecord(context)),
            verifiedBootToken = verifiedBootToken(context),
            attemptedBootToken = bootToken.takeIf { hasAttemptedBoot(context, bootToken) },
            bootToken = bootToken,
        )
    }

    /** Forgets the boot-scoped bookkeeping, so the next boot is treated as a fresh one. */
    @Synchronized
    fun reset(context: Context) {
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
