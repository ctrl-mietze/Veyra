package ctrl.mietze.veyraroot

/** Where a run's payload is executed. */
internal enum class RunTransport {
    /** The app's own process, running the helper directly. The default for a normal payload. */
    App,

    /** Shizuku's shell, and the helper staged where that shell can reach it. */
    Shizuku,

    /** The device's own adbd over wireless debugging, paired by this app. */
    LocalAdb,
}

/** What a run has available, from the feed's policy and from the device. */
internal data class TransportAvailability(
    val shellRequired: Boolean,
    val shizukuRequested: Boolean,
    val shizukuUsable: Boolean,
    val localAdbPaired: Boolean,
)

/**
 * Which transport a run gets, or null when nothing can carry it.
 *
 * Two rules, and both matter more than the ordering of the rest:
 *
 * - **A payload that needs a shell never falls back to the app's own domain.** The feed says a target
 *   only works from a shell context, which is a statement about what the payload can do there; running
 *   it as the app anyway would produce a failure that looks like the payload's fault. So the answer is
 *   null, and the run says which of the two transports it needed and what is wrong with each.
 * - **A usable Shizuku wins over a paired local ADB**, because a Shizuku session is already
 *   authenticated and needs no window in which wireless debugging is on - the local ADB path turns a
 *   device setting on and off around itself, which is worth avoiding when it does not have to happen.
 *   A *requested but unusable* Shizuku does not win: the point of asking is to get a shell, and a
 *   pairing that is actually there beats a preference that is not.
 *
 * Pure, so every combination can be checked without a device - which matters because the ones that go
 * wrong are the ones where two things are half-true at once.
 */
internal fun chooseRunTransport(
    shellRequired: Boolean,
    shizukuRequested: Boolean,
    shizukuUsable: Boolean,
    localAdbPaired: Boolean,
    shellPreferred: Boolean = false,
): RunTransport? = when {
    shellRequired -> when {
        shizukuRequested && shizukuUsable -> RunTransport.Shizuku
        localAdbPaired -> RunTransport.LocalAdb
        else -> null
    }
    shizukuRequested && shizukuUsable -> RunTransport.Shizuku
    shellPreferred && localAdbPaired -> RunTransport.LocalAdb
    else -> RunTransport.App
}

/**
 * What a run does about Shizuku when it asked for it and Shizuku is not there yet.
 *
 * A separate question from [chooseRunTransport], asked in a different place and by a different kind of
 * caller. The transport rule is asked *inside* a run that has already begun, and it falls back rather
 * than waiting - which is right on the install screen, where a fallback is visible to the person
 * watching. This one is asked *before* a run starts, by the only caller that runs unattended, and its
 * answer can hold the run back.
 */
internal enum class ShizukuWait {
    /** Use Shizuku is off: nothing waits, and the run takes whichever transport it otherwise would. */
    NotRequested,

    /** Shizuku is up and this app may use it: the run goes through it, with no waiting. */
    Ready,

    /**
     * Asked for, and nothing on this device can start it.
     *
     * The wait would be a delay in front of the one attempt a boot gets, and it would end by saying the
     * same thing this says now - so it is refused instead, naming what is missing.
     */
    Unstartable,

    /** Something here can start it: hold until it is usable. */
    Await,
}

/**
 * Whether a run should hold for Shizuku, from the setting and two readings of the device.
 *
 * [startable] is what keeps the wait honest. Shizuku cannot be started by another app without one of
 * the routes that do not need it - this device's root, a stored wireless pairing, or a start token - and
 * [ShizukuWait.Await] on a device with none of them spends the boot's window waiting for something
 * nothing is bringing. Pure, for the same reason as the transport rule: the interesting case is the one
 * where two things are half-true at once, and it is the one nobody can try by hand.
 */
internal fun shizukuWait(
    requested: Boolean,
    usable: Boolean,
    startable: Boolean,
): ShizukuWait = when {
    !requested -> ShizukuWait.NotRequested
    usable -> ShizukuWait.Ready
    !startable -> ShizukuWait.Unstartable
    else -> ShizukuWait.Await
}

/**
 * What a boot run should do about Shizuku, from the setting, the device, and the payload itself.
 *
 * The gate is the one caller that cannot fall back by itself, so its whole rule is a value rather than
 * whatever branch it happens to take. The input that is new here is the payload's own answer - whether it
 * needs a shell at all - and it is what turns "Shizuku did not come up" from a refusal into a run. It
 * also settles a contradiction that was there before: a run whose payload does not need a shell already
 * goes the standard way when Shizuku is silent ([chooseRunTransport]), and the gate holding that same
 * payload back - for two minutes and then with a refusal about a transport it never needed - was a
 * refusal about the wrong thing.
 */
internal enum class BootShizukuPlan {
    /** Run as the settings ask: Shizuku is off, or it is up and this app may use it. */
    AsAsked,

    /**
     * Run now, without a shell.
     *
     * Use Shizuku is on and Shizuku is not up, and the payload does not need a shell - so the wait and
     * the refusal would both be about a transport this run does not need. This is the same run the
     * screen offers by hand as "run without Shizuku"; here the payload's own policy chooses it instead
     * of a person, which is the only way an unattended boot can make that choice at all.
     */
    WithoutShell,

    /** The payload needs a shell and something here can start one: hold until it is usable. */
    Wait,

    /** The payload needs a shell and nothing here can start one: refuse, naming what is missing. */
    Unstartable,
}

/**
 * The Shizuku wait's answer, adjusted by whether the payload needs the transport being waited for.
 *
 * Pure, and built on [shizukuWait] rather than repeating any of it, because both are asked about the
 * same device: a second copy of the setting/usable/startable rules is how a boot comes to disagree with
 * a run about when to wait. Note what cannot happen here - a payload that does not need a shell produces
 * neither [BootShizukuPlan.Wait] nor [BootShizukuPlan.Unstartable], because both are answers about
 * getting a shell, and this payload does not need one.
 */
internal fun bootShizukuPlan(
    requested: Boolean,
    usable: Boolean,
    startable: Boolean,
    shellRequired: Boolean,
): BootShizukuPlan = when (shizukuWait(requested, usable, startable)) {
    ShizukuWait.NotRequested, ShizukuWait.Ready -> BootShizukuPlan.AsAsked
    // Both ends of "it is not coming": neither the wait nor the refusal is about this payload.
    ShizukuWait.Await -> if (shellRequired) BootShizukuPlan.Wait else BootShizukuPlan.WithoutShell
    ShizukuWait.Unstartable ->
        if (shellRequired) BootShizukuPlan.Unstartable else BootShizukuPlan.WithoutShell
}

/**
 * Whether a run that is about to start should stop and ask about Shizuku instead of going ahead.
 *
 * Use Shizuku says the run should go through Shizuku, and the only thing that changes Shizuku not
 * running is starting it - which is something this app can do. So a manual run asks, where it used to
 * do one of two worse things: a payload that needs a shell failed outright, and a payload that does not
 * quietly went another way, which is a setting being ignored without saying so.
 *
 * Unattended is not a fallback here but a different answer: a boot has nobody to ask, so it either waits
 * for Shizuku before the run or refuses - see [shizukuWait]. [ignoringShizuku] is what the person's own
 * answer to this question turns into on the run that follows, and it is why asking cannot become a loop.
 */
internal fun shouldHoldForShizuku(
    unattended: Boolean,
    requested: Boolean,
    running: Boolean,
    ignoringShizuku: Boolean,
): Boolean = !unattended && requested && !running && !ignoringShizuku

/**
 * What the local-ADB command prints when it is done, since the ADB shell carries no exit code.
 *
 * It is deliberately not ADB's own `__ADB_EXIT__=` marker: this one is part of the *payload* command's
 * output, and the two must stay distinguishable when the transport reports a failure of its own.
 */
internal const val ADB_EXIT_MARKER = "RMG_PAYLOAD_EXIT="

/**
 * The exit code a local-ADB payload run reported, or a failure code when it reported none.
 *
 * The last marker is used, because the payload's own output is streamed ahead of it and could contain
 * the marker's text; a run that could not report a code is [LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE],
 * so "no answer" is never read as success.
 */
internal fun localAdbExploitExitCode(output: String): Int {
    val markerIndex = output.lastIndexOf(ADB_EXIT_MARKER)
    if (markerIndex < 0) return LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE
    return output.substring(markerIndex + ADB_EXIT_MARKER.length)
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.toIntOrNull()
        ?: LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE
}

/**
 * Why a run could not start, as the string that names both transports it could have used.
 *
 * Naming both is the point: "no shell transport" leaves the user guessing whether a setting is off,
 * whether a pairing is missing, or whether Shizuku is installed but not running, and each of those has
 * a different answer. Two cases rather than one because "Shizuku is on and silent" and "Shizuku is off"
 * send the user to different places - one to a button that starts it, the other to a setting.
 */
internal fun shellTransportRefusalStringId(shizukuRequested: Boolean): Int =
    if (shizukuRequested) {
        R.string.error_shell_transport_shizuku_silent
    } else {
        R.string.error_shell_transport_shizuku_off
    }
