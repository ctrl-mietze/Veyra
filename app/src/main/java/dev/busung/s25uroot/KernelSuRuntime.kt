package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File

/**
 * Proof that the KernelSU control channel is live for this boot.
 *
 * The app used to take the helper's exit code for that, which says the late-load command finished
 * rather than that anything is reachable afterwards: a loaded-but-unreachable channel and a healthy
 * one were the same result. Each proof below is independent and one is enough, because a single
 * reliable check would have to be one the app cannot always make - the boot-time late-load runs as
 * shell, and a Samsung kernel may refuse new connects to the handoff socket even though KernelSU
 * itself is healthy.
 */
internal enum class ControlProof(val label: String) {
    /** The in-process native probe sees KernelSU from the app's own process. */
    NativeProbe("native probe"),

    /** KernelSU's own `su` answered through Shizuku, which also proves a usable root shell. */
    ShizukuElevation("KernelSU su through Shizuku"),

    /** The helper printed a live control report, which is a reading rather than an exit status. */
    HelperReport("helper control report"),

    /** The kernel's module list carries a `kernelsu` entry, which is proof of the load itself. */
    ModuleLoaded("kernel module list"),
}

/** KernelSU's own reading of the control channel, as the helper prints it. */
internal data class KernelSuControl(
    val version: Int,
    val flags: Int,
    val uapi: Int,
    val features: Int,
)

private val CONTROL_REPORT = Regex(
    "version=(\\d+)\\s+flags=(\\S+)\\s+uapi=(\\d+)\\s+features=(\\S+)",
)

private fun String.hexOrNull(): Int? =
    removePrefix("0x").removePrefix("0X").toIntOrNull(16)

/**
 * Reads the helper's control line, e.g.
 * `KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3`.
 *
 * A version of zero is not a live channel, so it does not count as a report.
 */
internal fun parseControlReport(output: String): KernelSuControl? {
    val match = CONTROL_REPORT.find(output) ?: return null
    val (version, flags, uapi, features) = match.destructured
    val control = KernelSuControl(
        version = version.toIntOrNull() ?: return null,
        flags = flags.hexOrNull() ?: return null,
        uapi = uapi.toIntOrNull() ?: return null,
        features = features.hexOrNull() ?: return null,
    )
    return control.takeIf { it.version > 0 }
}

/**
 * The proof set from three independent readings.
 *
 * Pure, so which proof sets count as ready can be tested without a rooted device.
 */
internal fun controlProofs(
    nativeProbe: Boolean,
    shizukuElevated: Boolean,
    helperOutput: String,
    moduleLoaded: Boolean = false,
): Set<ControlProof> = buildSet {
    if (nativeProbe) add(ControlProof.NativeProbe)
    if (shizukuElevated) add(ControlProof.ShizukuElevation)
    if (parseControlReport(helperOutput) != null) add(ControlProof.HelperReport)
    if (moduleLoaded) add(ControlProof.ModuleLoaded)
}

/**
 * Whether a kernel module list carries a `kernelsu` entry.
 *
 * The test is the module's *name* and nothing else: the line's other fields differ between a module
 * built into the image and one loaded by the payload's own loader, and a size or address that does
 * not look like a stock module's is not a reason to call a loaded KernelSU absent.
 */
internal fun parseModuleList(output: String): Boolean =
    output.lineSequence().any { line ->
        line.trim().split(' ', '\t').firstOrNull() == "kernelsu"
    }

/**
 * What each reading said, kept as words rather than as a yes/no.
 *
 * A refusal is only useful if it says which of the three readings answered and which did not, and
 * what the silent one actually printed - "no control channel answered" on its own is a dead end for
 * whoever reads the log next, because the three have nothing in common: the native paths are hidden
 * by policy on this hardware, `su` answers only the manager KernelSU knows, and the helper prints a
 * report only when the load reached the point of having one.
 */
internal data class ControlReadings(
    val nativeProbe: Boolean,
    val appSuFailure: SuProbe.Failure,
    val shizukuElevated: Boolean,
    val helperOutput: String,
    /** Null when the module list could not be read at all, which is not the same as an empty one. */
    val moduleLoaded: Boolean? = null,
) {
    val proofs: Set<ControlProof> =
        controlProofs(nativeProbe, shizukuElevated, helperOutput, moduleLoaded == true)

    /** One line for the run log, in the order the readings are made. */
    fun summary(): String = buildString {
        append("native probe ").append(if (nativeProbe) "yes" else "no")
        if (!nativeProbe && appSuFailure != SuProbe.Failure.NONE) {
            append(" (the app's own su: ").append(appSuFailure.name.lowercase()).append(")")
        }
        append("; su through Shizuku ").append(if (shizukuElevated) "yes" else "no")
        append("; helper ").append(
            when {
                parseControlReport(helperOutput) != null -> "reported a live control channel"
                helperOutput.isBlank() -> "printed nothing"
                else -> "printed " + helperOutput.lineSequence().count { it.isNotBlank() } +
                    " line(s) without a control report"
            },
        )
        append("; module list ").append(
            when (moduleLoaded) {
                true -> "reports kernelsu"
                false -> "has no kernelsu"
                null -> "not readable"
            },
        )
    }
}

/**
 * The same readings, with the module list supplied by a route the first attempt could not use.
 *
 * The list is the one proof a device with nothing granted yet can produce - the app's own read of
 * `/proc/modules` is denied by policy and the shell route needs Shizuku - so a run that holds bootstrap root
 * and nothing else has to be able to fill this reading in itself, or a healthy load is refused with no way
 * to have seen it.
 *
 * A null [loaded] changes nothing: it means the second route could not look either, which is what the
 * readings already say. A list already read is never overridden, because a reading that answered is a
 * reading, whoever else looks afterwards.
 */
internal fun ControlReadings.withModuleList(loaded: Boolean?): ControlReadings =
    if (loaded == null || moduleLoaded != null) this else copy(moduleLoaded = loaded)

/**
 * Whether a reading that could have confirmed the load actually answered, rather than every one of them
 * being silent or unable to look.
 *
 * The module list is the reading this is about, and it has three answers rather than two: the kernel has
 * the module, it does not, and the list could not be read. "Could not be read" is the common one on a
 * first install, where nothing has been granted and there may be no shell - and it needs different words
 * from a load that was looked for and not found, because only the second one is a reason to try again.
 */
internal fun ControlReadings.lookedAtTheKernel(): Boolean =
    moduleLoaded != null || helperOutput.isNotBlank()

/**
 * What a finished late-load is judged to be.
 *
 * The payload's exit code is an account of *its own* probe, not of the kernel. `su_daemon` opens
 * KernelSU's driver fd through the `reboot` magic and then requires a version and two flags, so a
 * load that landed while that route is refused - or while the flags test disagrees - comes back
 * non-zero with the module sitting in the kernel. Judging the run by that code alone is how a
 * successful load became a failed run: nothing was left behind for the next one, and with nothing
 * recorded as loaded there was no manager to hand over either.
 *
 * So the code is compared with the readings rather than instead of them, and a reading outranks it.
 * The payload failing to confirm itself is a fact about the payload's route to the kernel; the
 * kernel listing the module is a fact about the kernel.
 */
internal enum class LoadVerdict {
    /** The payload's probe passed and a reading backs it. */
    Confirmed,

    /** The payload's probe did not pass, and the kernel says the module is there anyway. */
    ConfirmedDespitePayload,

    /** The payload refused, and nothing here could see a loaded module either. */
    RefusedByPayload,

    /** The payload finished, and the kernel has no kernelsu when something did look. */
    NotLoaded,

    /** The payload finished and nothing could look at the kernel at all. */
    Unconfirmed,
}

/**
 * The verdict from the two things the run has: the payload's code and the readings.
 *
 * Pure, so every combination can be tested without a rooted device - which is the only way this one
 * gets tested at all, since the case it exists for needs a kernel that refuses the driver fd while
 * running the module the fd is for.
 */
internal fun loadVerdict(payloadCode: Int, readings: ControlReadings): LoadVerdict = when {
    readings.proofs.isNotEmpty() ->
        if (payloadCode == 0) LoadVerdict.Confirmed else LoadVerdict.ConfirmedDespitePayload

    // Nothing could see a loaded module, so the payload's own account is the best one left - and a
    // refusal has to carry it, because its line names what its probe found rather than what a
    // reading could not make.
    payloadCode != 0 -> LoadVerdict.RefusedByPayload

    readings.lookedAtTheKernel() -> LoadVerdict.NotLoaded
    else -> LoadVerdict.Unconfirmed
}

/**
 * The live probes behind [controlProofs], for the app to call on the device.
 *
 * Everything here is best-effort and reports absence rather than throwing: a probe failing is a
 * reason not to claim control, not a reason for a run to die with a stack trace.
 */
internal object KernelSuRuntime {
    fun proofs(context: Context, helperOutput: String): Set<ControlProof> =
        readings(context, helperOutput).proofs

    fun readings(context: Context, helperOutput: String): ControlReadings {
        val native = runCatching { RootStatusProbe.isActive() }.getOrDefault(false)
        return ControlReadings(
            nativeProbe = native,
            // Read after the probe, whose native path may fall back to it: this is the app's own
            // reading, where the one below goes through the Shizuku server instead.
            appSuFailure = SuProbe.lastFailure,
            shizukuElevated = shizukuElevation(),
            helperOutput = helperOutput,
            moduleLoaded = moduleLoaded(context),
        )
    }

    /**
     * Whether the kernel's own module list carries KernelSU - the one reading that needs nothing
     * granted first.
     *
     * The other three all ask a door that a first install has not opened yet. On this hardware the
     * module is loaded by the payload's root helper as a plain LKM that never appears under
     * `/sys/module`, so the native probe cannot see it; and `su` answers only the manager KernelSU
     * has been told about, so on a device where nobody has been granted root yet it says nothing at
     * all. The module list is readable from the shell domain, and it is what the payload's own
     * documentation says to check first - a load that happened is a fact about the kernel, not about
     * any app's permissions.
     *
     * Null when the list cannot be read: "did not load" and "could not look" are different answers,
     * and only the first is a reason to refuse.
     */
    fun moduleLoaded(context: Context? = null): Boolean? {
        directModuleList()?.let { return parseModuleList(it) }

        if (ShizukuController.isRunning() && ShizukuController.isGranted()) {
            val result = runCatching {
                ShizukuController.shell("/system/bin/grep -w kernelsu /proc/modules")
            }.getOrNull()
            when (result?.exitCode) {
                0 -> return true
                1 -> return false
            }
        }

        // Samsung commonly hides /proc/modules from an ordinary app while the shell domain can still
        // read it. CVeyra's detached uid=2000 agent gives the status card that same independent view
        // without keeping Wireless ADB enabled or depending on the Veyra app process staying alive.
        if (context != null) {
            val result = CVeyraAgent.shell(
                context,
                "/system/bin/grep -w kernelsu /proc/modules",
                timeoutMillis = 2_000,
            )
            when (result?.exitCode) {
                0 -> return true
                1 -> return false
            }
        }
        return null
    }

    /** The app's own read of the list, which policy usually denies and which costs nothing when not. */
    private fun directModuleList(): String? = runCatching {
        File("/proc/modules").takeIf(File::canRead)?.readText()
    }.getOrNull()

    /**
     * Runs [command] as root, using KernelSU itself rather than the bootstrap handoff.
     *
     * The helper's handoff socket exists only to cross the pre-KernelSU boundary; once KernelSU
     * has loaded, a Samsung kernel may refuse new connects to it while KernelSU is perfectly
     * healthy. A command that has to keep working after root therefore asks KernelSU directly.
     *
     * Two routes, in this order, because they fail for unrelated reasons. **Shizuku** answers when it
     * is running and has granted this app, and needs no prompt; asking it first keeps the quiet route
     * the common one. **`su`** ([SuShell]) is KernelSU's own answer for this app and needs nothing else
     * running, so it is what a device without Shizuku - or one where nobody has granted it - has left.
     * Returns null only when neither route could run the command, so the caller can refuse instead of
     * failing.
     */
    fun rootShell(
        command: String,
        timeoutSeconds: Long = SuShell.COMMAND_TIMEOUT_SECONDS,
    ): ShizukuController.ShellResult? =
        shizukuRootShell(command) ?: SuShell.run(command, timeoutSeconds)

    /**
     * Whether KernelSU is loaded in this boot, by any reading that needs no shell to make.
     *
     * This is the question "can anything here expect root", as opposed to "can this app run a command
     * as root": the answer decides whether a refusal to act is about the phone or about a permission,
     * and those two need different words and different fixes.
     *
     * [KernelSuStatus.Unreadable] counts as loaded, which is deliberate. Both of the messages this
     * feeds are advice, and when nothing could answer, the advice that survives being wrong is the one
     * pointing at a grant: if KernelSU is in fact loaded and simply not allowed to answer this app,
     * sending the user to re-run the install is the mistake this check was fixed for once already.
     */
    fun loadedInThisBoot(context: Context? = null): Boolean =
        status(context) != KernelSuStatus.NotLoaded

    /**
     * The same question for the Overview status line, which has to say when nothing could answer.
     *
     * Off the main thread: the authoritative reading may start `su` through [RootStatusProbe], and a
     * status line is not worth a frozen frame.
     */
    fun status(context: Context? = null): KernelSuStatus = kernelSuStatus(
        active = runCatching { RootStatusProbe.isActive() }.getOrDefault(false),
        moduleLoaded = moduleLoaded(context),
    )

    /**
     * The plain shell a running Shizuku server offers, which is the widest transport left with no root.
     *
     * This is deliberately *not* [rootShell]: the commands it may run are the ones the `shell` user
     * itself holds, the most useful of which is asking for a reboot. It exists so an action that a
     * shell can do is done rather than refused, and so an action that needs root can be refused for the
     * right reason - the shell is there, root is not.
     */
    fun unprivilegedShell(command: String): ShizukuController.ShellResult? {
        if (!ShizukuController.isRunning() || !ShizukuController.isGranted()) return null
        return runCatching { ShizukuController.shell(command) }.getOrNull()
    }

    private fun shizukuRootShell(command: String): ShizukuController.ShellResult? {
        if (!ShizukuController.isRunning() || !ShizukuController.isGranted()) return null
        // Shizuku's own process is already the shell uid, so a device that granted the late-load's
        // shell allowance answers `id` as root without a second escalation hop.
        val direct = runCatching { ShizukuController.shell("id") }.getOrNull()
        if (direct != null && direct.isRoot()) {
            return runCatching { ShizukuController.shell(command) }.getOrNull()
        }
        if (!(runCatching { ShizukuController.shell("su -c id") }.getOrNull()?.isRoot() == true)) {
            return null
        }
        return runCatching {
            ShizukuController.shell("su -c ${shellQuote(command)}")
        }.getOrNull()
    }

    private fun ShizukuController.ShellResult.isRoot(): Boolean =
        exitCode == 0 && output.contains("uid=0")

    /** Asks KernelSU itself for a root shell through the Shizuku server that is already running. */
    private fun shizukuElevation(): Boolean =
        runCatching { rootShell("id") }.getOrNull()?.isRoot() == true
}
