package ctrl.mietze.veyraroot

import android.content.Context

/**
 * The KernelSU this phone is actually running, as far as it can be read.
 *
 * [nothingRead] is a real answer and not an error: before the first successful run there is no
 * KernelSU on the phone to ask, and a manager that was installed by hand is the only thing there is.
 * The app says what it could read and claims nothing about what it could not, because a version pair
 * invented on a missing reading would be a warning about nothing.
 */
internal data class KernelSuVersionReading(
    /**
     * The userspace KernelSU - `ksud` - and the release it was built from, e.g. `3.3.0`.
     *
     * This is what a payload stages: the same build's kernel module is what a manager talks to, so
     * comparing a manager against this compares it against the KernelSU in this boot. The kernel-side
     * width of that pairing ([kernelCode]) is read in the same round trip and kept for the log, but it
     * is not what the comparison uses: it is a number with no name, and the manager's own version is a
     * name.
     */
    val daemon: String? = null,

    /** KernelSU's own `version` number for the loaded module, when the kernel answered for it. */
    val kernelCode: Int? = null,
) {
    /** Whether anything answered at all. */
    val isRead: Boolean get() = daemon != null || kernelCode != null
}

/** `ksud 3.3.0 (uapi: 3)`, and the same with the project's name or the `v` in front of it. */
private val DOTTED_VERSION = Regex("""(\d+\.\d+(?:\.\d+)*)""")

/** The line `ksud debug version` prints for the kernel-side module. */
private val KERNEL_VERSION_LINE = Regex("""Kernel\s+Version:\s*(\d+)""")

private const val DAEMON_MARKER = "RMG_DAEMON="
private const val KERNEL_MARKER = "RMG_KERNEL="

/**
 * The version in one line of `ksud --version` output, or null when there is not one.
 *
 * `ksud` is clap-based with `version = defs::FULL_VERSION`, which is `"{VERSION_NAME} (uapi: {n})"`
 * with the tag's leading `v` stripped - so the first dotted number is the release, and the `uapi`
 * beside it is a protocol number that must never be mistaken for one. That is why this looks for a
 * dotted number rather than for a number: `3` alone is not a version this app will compare against a
 * manager's.
 */
internal fun parseKsudVersion(output: String): String? =
    DOTTED_VERSION.find(output)?.groupValues?.get(1)

/**
 * The kernel-side version number `ksud debug version` reports, or null when it did not.
 *
 * Zero is not a version: the call reports zero when the module is not there to answer, which is the
 * difference between "no KernelSU" and "KernelSU 0".
 */
internal fun parseKernelVersionCode(output: String): Int? =
    KERNEL_VERSION_LINE.find(output)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

/** Both readings from one shell round trip's output, which is why they are marked apart. */
internal fun parseVersionReadings(output: String): KernelSuVersionReading {
    var daemon: String? = null
    var kernelCode: Int? = null
    output.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith(DAEMON_MARKER) ->
                daemon = parseKsudVersion(trimmed.removePrefix(DAEMON_MARKER))
            trimmed.startsWith(KERNEL_MARKER) ->
                kernelCode = parseKernelVersionCode(trimmed.removePrefix(KERNEL_MARKER))
        }
    }
    return KernelSuVersionReading(daemon = daemon, kernelCode = kernelCode)
}

/**
 * The release part of a version name, or null when the name has no release in it.
 *
 * A build can carry a suffix the release does not - `3.3.0-ksun` against `3.3.0` - and a tag can
 * carry a `v`, so the dotted number is taken out of the name rather than the name trimmed to match.
 * Anything with no dotted number in it is not a version this app will compare: a package manager
 * answer of `unknown` is a reading that failed, and comparing it as text is how an unreadable manager
 * becomes a warning about a mismatch nobody has.
 */
internal fun releaseOf(version: String?): String? =
    DOTTED_VERSION.find(version?.trim().orEmpty())?.groupValues?.get(1)

/** What the app can say about the manager on the phone against the KernelSU that is running. */
internal enum class ManagerVersionState {
    /** One of the two readings is missing, so there is nothing to compare and nothing to claim. */
    Unknown,

    /** The manager's version and the running KernelSU's are the same release. */
    Matching,

    /** They are not the same release, which is the case the app could not see until now. */
    Differing,
}

/**
 * Whether the installed manager matches the KernelSU this boot is running.
 *
 * What this is for: a manager is a plain app that talks to the loaded module over KernelSU's socket,
 * so any version can be installed at any time - and a manager from one line against a kernel from
 * another is the one pairing the app had no way to see, having only ever known the version it would
 * install itself. Nothing here refuses either of them; it is a reading, and the screen says it.
 */
internal fun managerVersionState(managerVersion: String?, kernelVersion: String?): ManagerVersionState {
    val manager = releaseOf(managerVersion) ?: return ManagerVersionState.Unknown
    val kernel = releaseOf(kernelVersion) ?: return ManagerVersionState.Unknown
    return if (manager == kernel) ManagerVersionState.Matching else ManagerVersionState.Differing
}

/**
 * Which of two releases is newer, or 0 when they are the same release.
 *
 * Component by component and as numbers, because comparing these as text gets the one case that
 * matters wrong: `3.10.0` sorts before `3.9.0` as a string, and a payload declaring the newer KernelSU
 * would then read as older than the boot. A component that one of them does not have counts as zero,
 * so `3.4` and `3.4.0` are the same release rather than two.
 */
internal fun compareReleases(left: String, right: String): Int {
    val one = left.split(".")
    val two = right.split(".")
    for (index in 0 until maxOf(one.size, two.size)) {
        val a = one.getOrNull(index)?.toIntOrNull() ?: 0
        val b = two.getOrNull(index)?.toIntOrNull() ?: 0
        if (a != b) return a.compareTo(b)
    }
    return 0
}

/**
 * How the KernelSU a payload declares stands beside the KernelSU this boot is running.
 *
 * [Ahead] and [Behind] are not the same problem wearing two names, which is why they are two states: a
 * payload that declares a newer release than the boot installs a manager that will match as soon as a
 * run loads it, while a payload that declares an older one asks the phone to walk backwards from a
 * KernelSU it already has. Only the second one is the app offering a release the phone has moved past.
 */
internal enum class PayloadKernelState {
    /** Nothing declared, or nothing read from the boot: there is no pair to compare. */
    Unknown,

    /** The payload declares the release this boot is running. */
    Same,

    /** The payload declares a newer release than this boot runs: it arrives with the next run. */
    Ahead,

    /** This boot runs a newer release than the payload declares. */
    Behind,
}

/**
 * The payload's declared KernelSU against this boot's, in the release form both are compared in.
 *
 * A reading rather than a message, so the row, the dialog and the tests all describe the same pair of
 * numbers - and so a screen cannot print a flag whose two versions it worked out separately.
 */
internal data class PayloadKernelReading(
    /** The release the payload declares, or null when it declares none. */
    val declared: String?,

    /** The release this boot is running, or null when nothing could be read. */
    val running: String?,

    val state: PayloadKernelState,
) {
    /** Whether this is worth saying out loud, which is the two states that disagree. */
    val flagged: Boolean
        get() = state == PayloadKernelState.Ahead || state == PayloadKernelState.Behind
}

/**
 * The KernelSU a payload declares, read against the one this boot is running.
 *
 * The boot's own version is the one the app cannot offer instead: the payload decides what the *next*
 * run loads, so its release is the right number to install - except where the phone is already ahead of
 * it, and this is the check that says so. Where either side is missing or carries no release the answer
 * is [PayloadKernelState.Unknown]: a version pair invented on a reading that did not happen would be a
 * warning about nothing, which is exactly what the manager rows refuse to print.
 */
internal fun payloadKernelReading(declared: String?, running: String?): PayloadKernelReading {
    val payload = releaseOf(declared)
    val boot = releaseOf(running)
    val state = when {
        payload == null || boot == null -> PayloadKernelState.Unknown
        else -> when {
            compareReleases(payload, boot) > 0 -> PayloadKernelState.Ahead
            compareReleases(payload, boot) < 0 -> PayloadKernelState.Behind
            else -> PayloadKernelState.Same
        }
    }
    return PayloadKernelReading(declared = payload, running = boot, state = state)
}

/**
 * The version to offer once the payload is behind this boot, or null when it is not.
 *
 * Mirroring [managerMismatchTarget]: a state with a fix names it, and every other state names nothing,
 * so a control cannot appear on a row whose problem it does not solve. The fix here is the running
 * KernelSU rather than the payload's, because the payload's is the release the phone has moved past -
 * and naming it as the manager version is what stops the offer from pointing back at it.
 */
internal fun payloadBehindTarget(reading: PayloadKernelReading): String? =
    reading.running?.takeIf { reading.state == PayloadKernelState.Behind }

/**
 * The pair a versions card prints: the two readings, and whether they line up.
 *
 * Both values are in the *release* form, which is the form [managerVersionState] compares - and that is
 * the point rather than a detail. A daemon built with a suffix reports `3.3.0-ksun`, and printing the
 * raw readings beside a manager's `3.3.0` would put two visibly different strings on screen under a
 * line that calls them a match. A reader would believe the strings and distrust the line, which is the
 * one thing a comparison screen must not do.
 *
 * Where a reading carries no release at all there is nothing to take, so the raw reading is shown: an
 * answer of `unknown` from the package manager is worth seeing as itself, and the state is
 * [ManagerVersionState.Unknown] either way. Null is kept as null so the screen can name the absence -
 * "not installed" and "not read" are different, and neither is a version.
 */
internal data class VersionPairDisplay(
    val manager: String?,
    val kernel: String?,
    /**
     * What the comparison made of these two.
     *
     * Carried rather than asked for separately at the call site, because a card that shows a pair and a
     * mark has to take both from one decision: two calls could disagree on the suffix case above, and
     * the screen would print `3.3.0` beside `3.3.0` and call it a mismatch.
     */
    val state: ManagerVersionState,
) {
    /** Whether the mark belongs on this pair. */
    val mismatched: Boolean get() = state == ManagerVersionState.Differing
}

/** The two readings as the screen should show them, with the state they produced. */
internal fun versionPairDisplay(managerVersion: String?, kernelVersion: String?): VersionPairDisplay =
    VersionPairDisplay(
        manager = versionToShow(managerVersion),
        kernel = versionToShow(kernelVersion),
        state = managerVersionState(managerVersion, kernelVersion),
    )

/** A reading's release, or the reading itself when it carries no release to take. */
private fun versionToShow(version: String?): String? =
    version?.trim()?.takeIf(String::isNotBlank)?.let { releaseOf(it) ?: it }

/**
 * The version the warning's own action should install, or null when there is nothing to act on.
 *
 * Only a mismatch, and only when the version to install is a version rather than an empty reading: an
 * "install" button on a row where nothing could be read would be a control that cannot work, which is
 * the same as no control. The version offered is the **running KernelSU's**, not the flavour's default:
 * the default is a decision this app made, and the kernel is the thing actually asking.
 */
internal fun managerMismatchTarget(
    state: ManagerVersionState,
    kernelVersion: String?,
): String? = kernelVersion?.takeIf { state == ManagerVersionState.Differing }

/**
 * The KernelSU version, read from the device.
 *
 * One root shell for both readings, because each shell is a process of its own and the two questions
 * are asked at the same moment: `ksud -V` for the userspace build, and `ksud debug version` for the
 * kernel-side module. `ksud` is asked where the payload leaves it first - `/data/adb/ksud` - and by
 * name second, so a device whose daemon is elsewhere is still readable.
 *
 * The read goes through [KernelSuRuntime.rootShell], which is KernelSU's own `su` or a Shizuku server
 * that already holds root. That makes the reading available exactly when it means something: with no
 * KernelSU loaded there is no daemon to ask about, and no shell to ask with.
 */
internal object KernelSuVersionProbe {

    /**
     * The command, in one `su -c` invocation.
     *
     * `2>/dev/null` inside rather than outside, so a `ksud` that is missing or refuses prints nothing
     * for its own line and leaves the other one intact. The markers are what keep the two answers
     * apart, since both are read as one stream of output.
     */
    private val COMMAND = listOf(
        "K=/data/adb/ksud",
        "[ -x \"${'$'}K\" ] || K=ksud",
        "echo \"$DAEMON_MARKER${'$'}(\"${'$'}K\" -V 2>/dev/null || \"${'$'}K\" --version 2>/dev/null)\"",
        "echo \"$KERNEL_MARKER${'$'}(\"${'$'}K\" debug version 2>/dev/null)\"",
    ).joinToString("; ")

    /** The reading already made in this boot, which does not change until the phone restarts. */
    @Volatile
    private var cachedBootToken: String? = null

    @Volatile
    private var cached: KernelSuVersionReading? = null

    /**
     * Reads the running KernelSU's version, or says that nothing answered.
     *
     * Cached per kernel boot, and only when something answered: the daemon cannot change without a
     * restart or a new run, and a reading of nothing is worth asking again - the usual reason for it
     * is a grant this app has not been given yet, which is exactly what changes between two visits to
     * this screen.
     */
    fun read(context: Context): KernelSuVersionReading {
        val bootToken = kernelBootToken()
        if (bootToken != null && bootToken == cachedBootToken) {
            cached?.let { return it }
        }
        val reading = runCatching { readNow() }.getOrNull() ?: KernelSuVersionReading()
        AppLog.debug(
            AppLogTags.KERNEL_SU,
            "Read the running KernelSU: " +
                "daemon=${reading.daemon ?: "no answer"}, kernel=${reading.kernelCode ?: "no answer"}",
        )
        if (bootToken != null && reading.isRead) {
            cachedBootToken = bootToken
            cached = reading
        }
        return reading
    }

    private fun readNow(): KernelSuVersionReading? {
        val result = KernelSuRuntime.rootShell(COMMAND) ?: return null
        return parseVersionReadings(result.output)
    }
}
