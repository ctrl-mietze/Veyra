package ctrl.mietze.veyraroot

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A way out of the running Android, as the reboot menu offers them.
 *
 * The same six the KernelSU manager's own power menu offers, and they are one list rather than five
 * features because they differ in exactly two ways: what the phone is asked for, and what the app needs in
 * order to ask. Both are properties of the target, so both are written here instead of at the button that
 * happens to press it.
 *
 * [leavesAndroid] is the one that decides whether the choice is confirmed first. A reboot is something this
 * app already asks for on its own during a retry; recovery, Odin, download and EDL are places the phone
 * does not come back from by itself, and the last of those is not even accepted by every device.
 */
internal enum class RebootTarget(
    @StringRes val label: Int,
    /** The argument the phone is asked to reboot with, or null for a plain reboot. */
    val argument: String? = null,
    /**
     * True when this is the KernelSU daemon's own restart rather than a reboot request.
     *
     * It is not a reboot and the kernel does not go away: the daemon emulates one for the userspace, which
     * is why it needs the daemon and therefore root, where every other target here needs only a shell.
     */
    val viaDaemon: Boolean = false,
    /** True when the target is not Android, so the phone has to be brought back by hand. */
    val leavesAndroid: Boolean = false,
) {
    Reboot(R.string.reboot_target_reboot),
    SoftRestart(
        label = R.string.reboot_target_soft_restart,
        viaDaemon = true,
    ),
    Recovery(
        label = R.string.reboot_target_recovery,
        argument = "recovery",
        leavesAndroid = true,
    ),
    Bootloader(
        label = R.string.reboot_target_bootloader,
        argument = "bootloader",
        leavesAndroid = true,
    ),
    Download(
        label = R.string.reboot_target_download,
        argument = "download",
        leavesAndroid = true,
    ),
    Edl(
        label = R.string.reboot_target_edl,
        argument = "edl",
        leavesAndroid = true,
    ),
}

/**
 * Whether this tier can ask for [target].
 *
 * A root shell can ask for everything. The plain shell a running Shizuku server offers is uid 2000, and the
 * `shell` user may reboot the phone - that is how `adb reboot recovery` works - with the target travelling
 * as the command's own argument, so every reboot here survives the loss of root. What does not is the
 * daemon's soft restart, which is not a reboot request at all.
 */
internal fun ShellTier.canAskFor(target: RebootTarget): Boolean = when (this) {
    ShellTier.Root -> true
    ShellTier.Unprivileged -> !target.viaDaemon
    ShellTier.None -> false
}

/**
 * The commands that ask the phone for [target], in the order they are tried.
 *
 * Empty for the daemon's own restart, which is asked for through the recovery actions rather than here -
 * that path owns the checks that make it verifiable, and a second implementation of it would be a second
 * place for those rules to drift.
 *
 * `svc power reboot` after `reboot` for the same reason the plain reboot tries both: some builds ship one
 * and not the other, and both mean the same thing to the user.
 */
internal fun rebootCommands(target: RebootTarget): List<String> {
    if (target.viaDaemon) return emptyList()
    val argument = target.argument?.let { " $it" }.orEmpty()
    return listOf("/system/bin/reboot$argument", "/system/bin/svc power reboot$argument")
}

/**
 * Why a target cannot be asked for, as the two answers with different fixes.
 *
 * Two and not a boolean, because "grant this app root" and "start Shizuku" are different instructions, and
 * a greyed row that does not say which one is missing is a row that sends people to look in the wrong place.
 */
internal enum class RebootRefusal {
    /** This one needs the daemon, and only a shell is available. */
    NeedsRoot,

    /** Neither root nor a shell answered, so there is nothing to ask with. */
    NothingToAskWith,
}

/**
 * The action the launcher's restart shortcut sends.
 *
 * The literal is written twice - here and in `res/xml/shortcuts.xml`, which is where the launcher reads it -
 * and nothing in the build joins the two, so a test compares them: a shortcut whose action nothing handles is
 * a long press that opens the app and does nothing else, which is the kind of breakage no compile catches.
 */
internal const val ACTION_RESTART_OPTIONS = "ctrl.mietze.veyraroot.action.RESTART_OPTIONS"

/**
 * The action the launcher's soft-restart shortcut sends.
 *
 * Separate from the sheet's action because it asks for a different thing: not "show me the ways out" but "take
 * the one this app would take". The two are read by one function below, so the shortcut XML and the app cannot
 * disagree about which is which.
 */
internal const val ACTION_SOFT_RESTART = "ctrl.mietze.veyraroot.action.SOFT_RESTART"

/** What a launcher shortcut asked this app for. */
internal enum class RestartShortcut {
    /** The sheet: the six ways out, with what this device will allow decided on each. */
    Options,

    /**
     * The daemon's own userspace restart, asked for without opening anything first.
     *
     * The one target that is offered directly, and the reason is what it does: it restarts the Android
     * userspace and leaves the kernel and its modules alone, so a tap that turns out to be a mistake costs
     * fifteen seconds. The four that leave Android are behind the sheet's confirmation, and stay there.
     */
    SoftRestart,
}

/**
 * What an intent's action asks for, or null for a launch that asked for nothing.
 *
 * Null rather than a default, because the default is the app opening normally: an intent from anywhere else,
 * including a plain tap on the icon, must land on the home screen and not on a restart.
 */
internal fun restartShortcutOf(action: String?): RestartShortcut? = when (action) {
    ACTION_RESTART_OPTIONS -> RestartShortcut.Options
    ACTION_SOFT_RESTART -> RestartShortcut.SoftRestart
    else -> null
}

/**
 * What the direct soft-restart shortcut turned into, as the two things the app can do about it.
 */
internal sealed interface SoftRestartShortcutOutcome {
    /** The daemon took it. There is nothing left to show: the userspace it restarted is going away. */
    data object Requested : SoftRestartShortcutOutcome

    /**
     * The sheet, which is where the reason is.
     *
     * [report] carries a request the daemon refused, rather than one that was never made: the sheet probes for
     * itself and names whichever of root and a shell is missing, so the one thing it cannot say on its own is
     * a refusal that came back from a daemon that was there.
     */
    data class OpenSheet(val report: RecoveryOutcome? = null) : SoftRestartShortcutOutcome
}

/**
 * Runs the userspace restart a shortcut asked for, or says why the sheet has to open instead.
 *
 * The probe comes first because it is the whole of what separates this from the sheet: the daemon's restart is
 * the one target an unprivileged shell cannot ask for, so running the command anyway would fail on a phone
 * with only Shizuku, where the sheet says which of the two answers is missing. It reads the same
 * [currentShellTier] the sheet reads, so the shortcut and the menu cannot disagree about this phone.
 */
internal suspend fun runSoftRestartShortcut(context: Context): SoftRestartShortcutOutcome {
    if (rebootRefusalFor(currentShellTier(context), RebootTarget.SoftRestart) != null) {
        return SoftRestartShortcutOutcome.OpenSheet()
    }
    val outcome = runRebootTarget(context, RebootTarget.SoftRestart)
    return if (outcome.accepted) {
        SoftRestartShortcutOutcome.Requested
    } else {
        SoftRestartShortcutOutcome.OpenSheet(outcome)
    }
}

/** The refusal for [target] on this tier, or null when the row is offered. */
internal fun rebootRefusalFor(tier: ShellTier, target: RebootTarget): RebootRefusal? = when {
    tier.canAskFor(target) -> null
    // With nothing to ask with, that is the whole story. Which of the six wanted root is a second question,
    // and the fix - a grant, or a running Shizuku - is the same for all of them.
    tier == ShellTier.None -> RebootRefusal.NothingToAskWith
    else -> RebootRefusal.NeedsRoot
}

/**
 * Which part of the group a target's row is, for the one card the six of them draw together.
 *
 * Indexed rather than named one by one, because the list is what decides it: a row written as `Top` by hand
 * would be wrong the moment a target is added, removed or reordered, and wrong in a way only the eye catches -
 * two rounded tops in a row, or a flat bottom left hanging in the middle of the card. It reads the same
 * vocabulary the settings list groups its rows with, `GroupedSingle` included, so a list that ever comes down
 * to one row rounds at both ends instead of drawing a cap on top and a seam below.
 */
internal fun rebootRowPosition(index: Int, count: Int): SettingsCardPosition = when {
    count <= 1 -> SettingsCardPosition.GroupedSingle
    index <= 0 -> SettingsCardPosition.Top
    index >= count - 1 -> SettingsCardPosition.Bottom
    else -> SettingsCardPosition.Middle
}

/**
 * The widest shell this device will give the app right now, asked for rather than assumed.
 *
 * The same probe the repair actions make, kept in one place for the menu that has to answer the question
 * before anything is pressed: the in-process reading of KernelSU can say no on a device where root is
 * usable, and a row greyed out on that reading would be a row lying about the phone.
 */
internal suspend fun currentShellTier(context: Context): ShellTier = withContext(Dispatchers.IO) {
    val rootReachable = KernelSuRuntime.rootShell("id") != null
    shellTier(
        rootReachable = rootReachable,
        // CVeyra owns the non-root broker. It may currently carry the request over local ADB or
        // its compatibility backend, but the reboot UI and policy only depend on the CVeyra API.
        unprivilegedReachable = !rootReachable && CVeyraController.shell(context, "id") != null,
    )
}

/**
 * Asks the phone for [target], and says whether the request was taken.
 *
 * Best effort in the same way the retry's reboot is: a reboot takes the transport down with it, so the
 * answer to the command that caused it is not always readable, and an unread answer is not a failure. What
 * must not happen is the opposite - reporting a reboot that was never asked for - so a refusal from every
 * command is what comes back, in the device's own words where there are any.
 */
internal suspend fun runRebootTarget(context: Context, target: RebootTarget): RecoveryOutcome =
    withContext(Dispatchers.IO) {
        val outcome = if (target.viaDaemon) {
            runRecoveryAction(context, RecoveryTool.SoftReboot)
        } else {
            askForReboot(context, target)
        }
        // Logged here rather than at the button that pressed it: this is the one place a restart is asked
        // for through this menu, and a phone that comes back from Odin or Download mode should have a line
        // on it saying why it went there.
        AppLog.info(
            AppLogTags.RESTART,
            "Restart ${target.name}: accepted=${outcome.accepted}" +
                if (outcome.accepted) "" else " (${outcome.detail.take(160)})",
        )
        outcome
    }

/** The reboot itself, for the five targets that are reboot requests rather than the daemon's own restart. */
private suspend fun askForReboot(context: Context, target: RebootTarget): RecoveryOutcome {
    val tier = currentShellTier(context)
    if (!tier.canAskFor(target)) {
        val refusal = rebootRefusalFor(tier, target) ?: RebootRefusal.NothingToAskWith
        return RecoveryOutcome(
            accepted = false,
            detail = context.getString(refusal.lineRes()),
        )
    }
    val shell: (String) -> ShizukuController.ShellResult? = when (tier) {
        ShellTier.Root -> { command -> KernelSuRuntime.rootShell(command) }
        else -> { command -> CVeyraController.shell(context, command) }
    }
    var lastRefusal = ""
    rebootCommands(target).forEach { command ->
        val result = shell(command)
        if (result != null && result.exitCode == 0) {
            return RecoveryOutcome(accepted = true, detail = "")
        }
        lastRefusal = result?.output?.trim().orEmpty().ifBlank { lastRefusal }
    }
    return RecoveryOutcome(
        accepted = false,
        detail = lastRefusal.ifBlank { context.getString(R.string.reboot_refused) },
    )
}

/** The line a refusal is shown as, which is the whole of what makes it useful. */
internal fun RebootRefusal.lineRes(): Int = when (this) {
    RebootRefusal.NeedsRoot -> R.string.reboot_need_root
    RebootRefusal.NothingToAskWith -> R.string.reboot_need_shell
}
