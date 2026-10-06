package ctrl.mietze.veyraroot

import android.content.Context

/**
 * A manager APK's signing key, as ReSukiSU's kernel identifies it.
 *
 * Not a package name and not a version: the kernel decides who its manager is by reading the APK's
 * v2 signature out of the file, so the identity it holds is this pair - the certificate block's size
 * and the SHA-256 of the certificate. Everything else about the APK can change without the kernel
 * noticing, which is exactly why a manager can be rebuilt, or shipped for a different ABI, and still
 * be recognised.
 *
 * [size] is an `Int` rather than the `Long` a file size would be, because that is what the kernel's
 * own command carries (`u32`) and the range is bounded at both ends: it refuses anything under `0x100`
 * or over `0x1000`.
 */
internal data class ManagerSignature(val size: Int, val hash: String)

/**
 * The line `ksud` prints when it is asked about a signature, in either of the two forms it uses.
 *
 * `debug get-sign` writes the size in hexadecimal (`size: 0x300, hash: …`) and `kernel
 * dynamic-manager get` writes it in decimal (`size: 768, hash: …`), which is a difference worth
 * parsing rather than a difference worth avoiding: the two questions are answered by different parts
 * of the daemon and only one of them is the app's to choose. Both are read here so the two can be
 * compared, and so the value that goes back out is the decimal one the `set` command's own argument
 * parser accepts.
 *
 * The hash is 64 characters because that is what the kernel validates character by character
 * (`0-9a-f`), and because the daemon's `set` takes exactly 64 bytes. A line that does not carry one
 * is not a signature, so this returns null rather than a guess - an unreadable answer and a manager
 * with no key are different states and the screens say so differently.
 *
 * A size of zero is not a signature: it is what the kernel's own struct starts at, and treating it
 * as one would let an empty answer read as a registration. And the hash is bounded on both sides,
 * so a longer run of hex characters is not read as its first 64 - the kernel's own check is that
 * there are exactly 64 of them, and a parse that accepted more would be lenient about the one thing
 * the kernel is strict about.
 */
private val SIGNATURE_LINE = Regex(
    """size:\s*(0[xX][0-9a-fA-F]+|\d+)\s*,\s*hash:\s*([0-9a-fA-F]{64})(?![0-9a-fA-F])""",
)

/** The signature in an answer from `ksud`, or null when the answer did not carry one. */
internal fun parseManagerSignature(output: String): ManagerSignature? {
    val match = SIGNATURE_LINE.find(output) ?: return null
    val (size, hash) = match.destructured
    val value = if (size.startsWith("0x", ignoreCase = true)) {
        size.drop(2).toIntOrNull(16)
    } else {
        size.toIntOrNull()
    }
    return value?.takeIf { it > 0 }?.let { ManagerSignature(size = it, hash = hash.lowercase()) }
}

/**
 * What the kernel holds, and whether the question could be asked at all.
 *
 * Three states rather than two, because the two ways of having nothing are not the same thing to a
 * reader: a ReSukiSU kernel that has never been given a key, and a kernel whose daemon does not have
 * this feature at all, look the same in a boolean and need different sentences. They need different
 * actions too - the first is one tap away from working, and the second is not a task this app can do.
 */
internal enum class DynamicManagerState {
    /** The kernel answered and holds a key, which is [DynamicManagerReading.signature]. */
    Held,

    /** The kernel answered and holds none. */
    Unset,

    /** Nothing that could answer did: no root, no kernel, or a daemon without the feature. */
    Unreadable,
}

/** The reading behind the settings row: which of the three it is, and the key when there is one. */
internal data class DynamicManagerReading(
    val state: DynamicManagerState,
    val signature: ManagerSignature? = null,
)

/**
 * Whether a `get` that failed is the kernel saying it holds nothing.
 *
 * The kernel answers the read with `-ENODATA` when no key has been set, and the daemon turns that
 * into its own error line rather than an empty answer - so "nothing is registered" arrives as a
 * failure, and a failure is otherwise unreadable. Matched on the kernel's own `errno` description
 * rather than on its number, which differs between architectures and is not what the daemon prints.
 */
private fun String.reportsNoData(): Boolean =
    contains("No data available", ignoreCase = true)

/**
 * What one answer to `dynamic-manager get` means.
 *
 * Pure, and separated from the shell that produces it, because the decision it makes is the one this
 * whole feature rests on: a kernel that holds no key and a kernel that could not be asked are one
 * boolean apart, and reading the second as the first would have the screen say "no key registered"
 * about a phone where the question never reached anything - and then send a user to register a
 * manager that may already be registered.
 *
 * So only two things count as an answer: exit zero carrying a parsed signature, and a failure whose
 * text is the kernel's own "no data". Everything else - no driver fd, a daemon without the
 * subcommand, a truncated line - is unreadable, which is the state that claims nothing.
 */
internal fun dynamicManagerReading(exitCode: Int, output: String): DynamicManagerReading = when {
    exitCode == 0 -> parseManagerSignature(output)
        ?.let { DynamicManagerReading(DynamicManagerState.Held, it) }
        ?: DynamicManagerReading(DynamicManagerState.Unreadable)

    output.reportsNoData() -> DynamicManagerReading(DynamicManagerState.Unset)
    else -> DynamicManagerReading(DynamicManagerState.Unreadable)
}

/**
 * The KernelSU daemon a command should use.
 *
 * Resolved in the shell rather than in the app because the answer is a property of the phone: the
 * payload leaves its `ksud` in `/data/adb`, and a device where that path is missing may still have one
 * on `PATH`. This is the same resolution the version probe makes, kept in one place so the two cannot
 * disagree about which daemon answered.
 */
private const val KSUD = "K=/data/adb/ksud; [ -x \"\$K\" ] || K=ksud"

/**
 * The signature of one APK file, asked of the daemon.
 *
 * A local read of the file - no kernel and no ioctl - but it still goes through `ksud`, because that
 * is the only implementation of this parse on the phone and, importantly, the same one the kernel
 * uses: computing the hash a second way here would be a second answer to a question that has to have
 * exactly one.
 */
internal fun managerSignatureCommand(apkPath: String): String =
    "$KSUD; \"\$K\" debug get-sign ${shellQuote(apkPath)}"

/** What the kernel currently holds, or a failure when it holds nothing. */
internal fun dynamicManagerReadCommand(): String =
    "$KSUD; \"\$K\" kernel dynamic-manager get"

/**
 * Registers [signature] as the manager key.
 *
 * The size goes out in decimal because that is the only form the daemon's argument parser accepts -
 * it takes a `u32` by `FromStr`, so the `0x300` the read above prints is not a value it can be given
 * back. That conversion is [parseManagerSignature]'s, and this command is the reason it exists.
 */
internal fun dynamicManagerSetCommand(signature: ManagerSignature): String =
    "$KSUD; \"\$K\" kernel dynamic-manager set ${signature.size} ${signature.hash}"

/** What came of an attempt to register a manager, in the words the screens need. */
internal enum class RegistrationOutcome {
    /** The kernel now holds this manager's key. */
    Registered,

    /** The kernel already held it, so nothing was written. */
    AlreadyRegistered,

    /** No manager of this flavour is installed, so there is no APK to register. */
    NoManager,

    /** Root could not be reached, so nothing was asked - neither of the daemon nor of the kernel. */
    NoShell,

    /** The daemon refused, or could not read the APK. [RegistrationReport.detail] is what it said. */
    Refused,

    /**
     * The write was accepted and the kernel reports a different key than the one intended.
     *
     * Kept apart from [Registered] because it is the one outcome that must never be reported as a
     * success: a registration that landed wrongly is a manager the kernel will still refuse, and the
     * next attempt would find the same thing.
     */
    NotHeld,
}

/** The result of one attempt, with what the kernel holds afterwards when that could be read. */
internal data class RegistrationReport(
    val outcome: RegistrationOutcome,
    /** The key the kernel holds, or the one the attempt was about when it was never written. */
    val signature: ManagerSignature? = null,
    /** The daemon's own words, when it spoke. Never invented, and empty when it did not. */
    val detail: String = "",
)

/**
 * Registering a manager with the kernel, for the one flavour whose module can be told.
 *
 * ReSukiSU's kernel decides which APK is its manager by comparing the APK's signature against a table
 * compiled into the module. That table is the project's own release key, which is the right default
 * and a dead end for anyone else: a manager rebuilt from the same source - to change its name, to
 * carry a different icon, to be signed by whoever built it - is refused by a kernel that cannot be
 * recompiled to accept it.
 *
 * The kernel carries a second path for exactly that case, and this is it: a key can be given to it at
 * runtime, and an APK carrying that key is then accepted as the manager. The write is what makes the
 * difference, and it is deliberately not part of a run - it is a decision about what this phone will
 * trust as its manager, so it is a tap on a settings row that says so, not something a root install
 * does on the way past.
 *
 * What the write does **not** do is persist in the kernel: it is a runtime setting, and a reboot
 * clears it. The daemon keeps the pair in its own file and re-applies it on the way up, which is why
 * `ksud` does this and not an ioctl from here - registering once is enough, and doing it from this
 * app's side of the run would leave a phone that is registered until it restarts.
 */
internal object DynamicManager {

    /**
     * What the kernel holds, from one root shell.
     *
     * Off the main thread by construction: this starts `su` on a device that has not answered a grant
     * prompt yet, which is a wait rather than a failure. Callers are expected to be on `Dispatchers.IO`.
     */
    fun read(context: Context): DynamicManagerReading {
        val result = KernelSuRuntime.rootShell(dynamicManagerReadCommand()) ?: return unreadable()
        return dynamicManagerReading(result.exitCode, result.output)
    }

    /**
     * Registers the installed manager of [flavor] with the kernel, and verifies it landed.
     *
     * The order is the whole of it. The APK's own key is read first, because a registration is a
     * statement about a particular file and the kernel's table is keyed on exactly that pair; the
     * write follows; and the kernel is then asked what it holds, because the write's exit code says
     * an ioctl finished rather than that the setting is the one intended. That last read is the only
     * thing that separates a registration from a request, and it is why this returns a report rather
     * than a boolean.
     *
     * Nothing is written when the key is already held, so the common case - a phone whose manager the
     * kernel already accepts - costs one read and no write.
     */
    fun register(context: Context, flavor: KernelSuFlavor): RegistrationReport {
        val installed = KernelSuManager.installedFor(context, flavor)
            ?: return RegistrationReport(RegistrationOutcome.NoManager)
        val apkPath = KernelSuManager.apkPathOf(context, installed.packageName)
            ?: return RegistrationReport(RegistrationOutcome.NoManager)

        val signature = when (val attempt = readSignatureOf(apkPath)) {
            is SignatureAttempt.Read -> attempt.signature
            // No shell is not a refusal by the daemon: nothing was asked, and the two need different
            // sentences because only one of them is a thing the user can act on.
            SignatureAttempt.NoShell -> return RegistrationReport(RegistrationOutcome.NoShell)
            is SignatureAttempt.Failed ->
                return RegistrationReport(RegistrationOutcome.Refused, detail = attempt.detail)
        }

        val before = read(context)
        if (before.state == DynamicManagerState.Held && before.signature == signature) {
            return RegistrationReport(RegistrationOutcome.AlreadyRegistered, signature)
        }

        val written = KernelSuRuntime.rootShell(dynamicManagerSetCommand(signature))
            ?: return RegistrationReport(RegistrationOutcome.NoShell, signature)
        if (written.exitCode != 0) {
            return RegistrationReport(
                RegistrationOutcome.Refused,
                signature = signature,
                detail = written.output.trim(),
            )
        }

        val after = read(context)
        return if (after.state == DynamicManagerState.Held && after.signature == signature) {
            RegistrationReport(RegistrationOutcome.Registered, signature)
        } else {
            RegistrationReport(
                RegistrationOutcome.NotHeld,
                signature = after.signature ?: signature,
                detail = after.signature?.hash?.take(16).orEmpty(),
            )
        }
    }

    /** One APK's own key, or why it could not be read. */
    private sealed interface SignatureAttempt {
        /** The daemon answered with the file's key. */
        data class Read(val signature: ManagerSignature) : SignatureAttempt

        /** The daemon ran and produced no key; [detail] is what it said. */
        data class Failed(val detail: String) : SignatureAttempt

        /** No root shell could be had, so the daemon was never asked. */
        object NoShell : SignatureAttempt
    }

    /**
     * One APK's own key, from the daemon.
     *
     * The daemon's words are carried back with the failure, because the reason a signature could not
     * be read is the useful half of it and it happens inside a helper the caller never sees: the
     * daemon says which of the file's structures it could not find, and an APK with a v3 scheme and
     * no v2 one is a real answer rather than a broken build.
     */
    private fun readSignatureOf(apkPath: String): SignatureAttempt {
        val result = KernelSuRuntime.rootShell(managerSignatureCommand(apkPath))
            ?: return SignatureAttempt.NoShell
        if (result.exitCode != 0) return SignatureAttempt.Failed(result.output.trim())
        val signature = parseManagerSignature(result.output)
            ?: return SignatureAttempt.Failed(result.output.trim())
        return SignatureAttempt.Read(signature)
    }

    private fun unreadable() = DynamicManagerReading(DynamicManagerState.Unreadable)
}

/**
 * The short form of a key, for a row that has one line to say it in.
 *
 * A 64-character hash is not a thing anyone reads, and the whole of it is not a thing anyone acts on
 * either: what the row has to convey is *that* the kernel holds a key, and which one only matters
 * when two are being compared. The size in hexadecimal is kept in front of it because that is the
 * half the kernel's own table matches first, and because it is what `get-sign` prints.
 */
internal fun ManagerSignature.shortLabel(): String = "0x${size.toString(16)} · ${hash.take(8)}"
