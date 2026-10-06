package ctrl.mietze.veyraroot

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.StringRes
import java.util.Locale

/**
 * What this app has left in `/data/local/tmp`, read the way a root detector reads it.
 *
 * The app stages there by necessity: the daemon, the helper and the exploit payload have to be
 * executable by a shell, and `/data/local/tmp` is the one directory that is both writable by the
 * transports this app uses and outside the app's own sandbox. The cost of that is that the staging is
 * *public in the way that matters*: the directory is mode `0771`, so any app on the device may reach a
 * path inside it by name, and the files this app leaves there are world-readable. A detector does not
 * need root or a shell to find them - it needs a list of names, and the names this app uses are fixed
 * and published in its own source.
 *
 * That is the whole reason this object exists, and it is why the reading is *this app's own context*
 * rather than a shell's. A shell reading would answer "what is on disk", which is a different and much
 * less interesting question: what matters is what another app can see, and the only honest way to
 * answer that is to look the way one would.
 *
 * ## Why a catalog, and why a listing on top of it
 *
 * `/data/local/tmp` is `drwxrwx--x shell shell`. The `--x` for other is deliberate, and it is exactly
 * the permission this check turns on: an app may traverse the directory but not read it, so it can
 * stat a path it already knows and cannot list the directory to discover one. Asked on this app's own
 * device, `ls /data/local/tmp` answers `Permission denied` while `ls -l /data/local/tmp/ksu-helper`
 * answers with the file. So the check enumerates the paths this app writes, from the same constants
 * the staging code uses - and that alone was the whole answer until it was not enough.
 *
 * What a catalog cannot do is see a name it does not already know: a payload's own `ksu_late_load.log`,
 * a marker written by a script, a file left by anything else at all. On a device where several of
 * those were sitting in the directory the card read "Nothing left", which is the same statement as a
 * clean device and the opposite of the truth - the mistake [ResidueReading.Unreadable] exists to
 * prevent, arrived at by a different route.
 *
 * So the reading has two halves. The names come from [StagedDirectory], which lists the directory
 * *through a shell*: this app's own context may not read the directory, but the `shell` uid that owns
 * it may, and this app already has that door open for the sweep. Anything the listing names that the
 * catalog does not is carried as an extra, and an extra is enough to stop the card reading clean. When
 * no shell answers there is no listing, and the card says the weaker sentence it has earned instead.
 *
 * ## What this deliberately does not cover
 *
 * The app's own files - the run history, the app log, the cached payloads - are not here. Nothing
 * outside the app can read them, so they are not residue in the sense this is about. The catalog is
 * only the world-readable staging.
 */
internal enum class ResidueRole(@StringRes val labelRes: Int) {
    /** The KernelSU daemon. */
    Daemon(R.string.residue_role_daemon),

    /** The root helper the exploit is loaded with. */
    Helper(R.string.residue_role_helper),

    /** The exploit payload itself. */
    Payload(R.string.residue_role_payload),

    /** A script a repair action runs on the device. */
    Script(R.string.residue_role_script),

    /** A log a staged script writes. */
    Log(R.string.residue_role_log),

    /** A zero-byte or timestamp-only file a staged script coordinates through. */
    Marker(R.string.residue_role_marker),

    /** A socket the staged daemon leaves open. */
    Socket(R.string.residue_role_socket),
}

/**
 * One path this app is known to write, with what it is for.
 *
 * The role is carried because a name alone does not say it, and because the two names for one thing
 * (`ksu-helper` from the Shizuku route, `rmg-ksud-helper` from the wireless-ADB one) are otherwise
 * indistinguishable in a list of leftovers.
 */
internal data class StagedPath(val path: String, val role: ResidueRole) {
    /** The name on the device, which is what a detector's own catalog matches on. */
    val name: String get() = path.substringAfterLast('/')
}

/** What looking for a [StagedPath] found. */
internal sealed interface ResidueReading {
    /** It is there, with the size and age the filesystem reports. */
    data class Present(val sizeBytes: Long, val modifiedAtMillis: Long) : ResidueReading

    /** The filesystem answered, and it is not there. */
    data object Gone : ResidueReading

    /**
     * The filesystem did not answer, so this path is neither present nor absent.
     *
     * Its own case rather than being folded into [Gone], because the two mean opposite things to
     * whoever is reading: "gone" is good news and "unreadable" is no news at all. Reporting a denied
     * read as an absence is the same mistake as reporting a failed probe as a clean device, and this
     * app makes a point of not making it.
     */
    data object Unreadable : ResidueReading
}

/** One path and what came of looking for it. */
internal data class ResidueFinding(val staged: StagedPath, val reading: ResidueReading)

/**
 * One entry in the directory that the catalog does not name.
 *
 * Its own type rather than a [StagedPath] with an "unknown" role, because nothing here is known beyond
 * the name: who wrote it, what it is for and whether anything still wants it are all open questions,
 * and answering them by assumption is how a list stops being a reading. What it does carry is the same
 * stat the catalog gets, taken the same way, because "this app can see it" is the standard the whole
 * check is held to.
 */
internal data class TempEntry(
    val name: String,
    val reading: ResidueReading,
    /** A directory rather than a file, which the listing cannot say and the stat can. */
    val isDirectory: Boolean = false,
) {
    /**
     * Where it is, put together the same way the catalogue's own paths are.
     *
     * A name is all the listing gives, and a delete needs a path: building it here rather than at each
     * caller keeps one spelling of the directory in the app, which is the same rule the catalogue
     * follows.
     */
    val path: String get() = "${StagedResidue.DIRECTORY}/$name"
}

/** What one stat says: the reading, and the one other thing a stat can say. */
internal data class StagedStat(val reading: ResidueReading, val isDirectory: Boolean)

/**
 * The whole reading: every catalogued path, plus whether the app could see into the directory at all.
 *
 * The directory reading is the control. Without it, a page of "unreadable" cannot be told from a SELinux
 * policy that denies this app `/data/local/tmp` outright, and the two want different sentences: one is
 * about a file, the other is about the whole check being blind on this device.
 */
internal data class ResidueReport(
    val findings: List<ResidueFinding>,
    val directoryVisible: Boolean,
    /**
     * Everything else in the directory, which the catalog does not name.
     *
     * Not folded into [findings], because the two are not the same claim: a finding is a path this app
     * wrote and can account for, and one of these is a name that is simply there and is not this app's
     * to explain. What they have in common is the only thing that matters to whoever is reading - a
     * detector sees either of them.
     */
    val extras: List<TempEntry> = emptyList(),
    /**
     * Whether the directory itself was listed, which only a shell can do.
     *
     * False is the ordinary state of a device with no shell answering, and it changes the sentence the
     * reading has earned: a clean answer taken by name is not the same statement as a clean directory.
     */
    val directoryListed: Boolean = false,
) {

    /** What is actually there, which is the list a detector would produce. */
    val present: List<ResidueFinding> get() = findings.filter { it.reading is ResidueReading.Present }

    /** Everything left behind, added up. */
    val totalBytes: Long
        get() = present.sumOf { (it.reading as ResidueReading.Present).sizeBytes }

    /** The oldest thing there, which is the one that says how long this has been accumulating. */
    val oldestMillis: Long?
        get() = present.minOfOrNull { (it.reading as ResidueReading.Present).modifiedAtMillis }

    /**
     * Whether this reading saw anything at all.
     *
     * True when the directory itself could not be stat'd, or when nothing in the catalog could be - the
     * two ways the check comes back with no information. An empty `present` list on a readable reading
     * is not this: it is a clean device, and it has to look like one.
     */
    val blind: Boolean
        get() = !directoryVisible ||
            (extras.isEmpty() &&
                findings.isNotEmpty() &&
                findings.all { it.reading is ResidueReading.Unreadable })

    /**
     * What this reading amounts to, decided without a `Context` so it can be tested.
     *
     * The order of these cases is the whole rule and the reason it is here rather than in the sentence
     * below: anything else in the directory outranks a clean answer, and a clean answer by name is not
     * the same case as a clean answer from a listing.
     */
    val verdict: ResidueVerdict
        get() = when {
            blind -> ResidueVerdict.Blind
            present.isNotEmpty() && extras.isNotEmpty() -> ResidueVerdict.StagedAndOthers
            present.isNotEmpty() -> ResidueVerdict.Staged
            extras.isNotEmpty() -> ResidueVerdict.Others
            directoryListed -> ResidueVerdict.Clean
            else -> ResidueVerdict.CleanByName
        }

    /**
     * The one line the card and the app log both say, so the two cannot disagree about the reading.
     *
     * The size alone when there is something there. A settings card has room for a value, not for a
     * report, and both of the facts that used to be appended to it are in the list behind it: which
     * files they are, and how long each has been there.
     */
    /**
     * The card's value, which is about this app's own staging and nothing else.
     *
     * What else is in the directory is named in the list behind the card and counted in the app log,
     * and deliberately not in this line: the card answers one question, and a value a sentence long was
     * answering a different one. Nothing here claims the directory is empty either - an entry this app
     * cannot account for reads as "nothing this app staged" rather than as "nothing left".
     */
    fun summaryLine(context: Context): String = when (verdict) {
        ResidueVerdict.Blind -> context.getString(R.string.residue_blind)
        ResidueVerdict.Clean -> context.getString(R.string.residue_clean)
        ResidueVerdict.CleanByName -> context.getString(R.string.residue_clean_by_name)
        ResidueVerdict.Others -> context.getString(R.string.residue_nothing_staged)
        ResidueVerdict.Staged, ResidueVerdict.StagedAndOthers ->
            StagedResidue.sizeLabel(totalBytes)
    }

    /** The same facts with the names, which is what makes a line in the log actionable. */
    fun logLine(context: Context): String = when (verdict) {
        ResidueVerdict.Blind -> context.getString(R.string.residue_log_blind, StagedResidue.DIRECTORY)
        ResidueVerdict.Clean -> context.getString(
            R.string.residue_log_clean_listed,
            findings.size,
            StagedResidue.DIRECTORY,
        )
        ResidueVerdict.CleanByName -> context.getString(
            R.string.residue_log_clean_by_name,
            findings.size,
            StagedResidue.DIRECTORY,
        )
        ResidueVerdict.Staged -> context.getString(
            R.string.residue_log_present,
            present.size,
            StagedResidue.sizeLabel(totalBytes),
            present.joinToString(", ") { it.staged.name },
        )
        ResidueVerdict.Others -> context.getString(
            R.string.residue_log_others,
            extras.size,
            extraNames(),
        )
        ResidueVerdict.StagedAndOthers -> context.getString(
            R.string.residue_log_staged_and_others,
            present.size,
            StagedResidue.sizeLabel(totalBytes),
            present.joinToString(", ") { it.staged.name },
            extras.size,
            extraNames(),
        )
    }

    /** The names a log line names, capped so a crowded directory cannot fill the file it is filed in. */
    private fun extraNames(): String {
        val named = extras.take(EXTRA_NAMES_LIMIT).joinToString(", ") { it.name }
        return if (extras.size > EXTRA_NAMES_LIMIT) "$named, …" else named
    }
}

/** How many of the extra names a log line carries before it says "and more". */
private const val EXTRA_NAMES_LIMIT = 12

/**
 * What a reading comes to, in the six ways it can.
 *
 * An enum rather than one string per case, for the same reason [SweepVerdict] is one: which sentence a
 * device has earned is the part worth testing, and a `Context` in the middle of it would be the part
 * that cannot be. [ResidueReport.summaryLine] and [ResidueReport.logLine] are then two spellings of the
 * same decision - the card short, the log with every name - and a case added here has to be spelled in
 * both.
 */
internal enum class ResidueVerdict {
    /** This app cannot see the directory at all, so nothing can be said about it. */
    Blind,

    /** The directory was listed and holds nothing: not this app's staging, and nothing else either. */
    Clean,

    /**
     * Nothing was found by name, and the directory itself could not be listed.
     *
     * Its own case because its sentence has to be weaker: the check looked for the names this app
     * writes, and a name it does not write could be sitting there unseen.
     */
    CleanByName,

    /** This app's own staging, and nothing else. */
    Staged,

    /** Nothing of this app's, but the directory is not empty. */
    Others,

    /** Both, which is the case the two halves of this reading exist for. */
    StagedAndOthers,
}

/**
 * The check itself: a catalog, a stat per path, and the two label formats that go with them.
 *
 * Everything the answer depends on is either in the catalog or in [readingFor], which is pure and is
 * where the tests are. What is left here is the syscall and the arithmetic.
 */
internal object StagedResidue {

    /** Where the staging goes, which is also what the directory control is read for. */
    const val DIRECTORY = "/data/local/tmp"

    /**
     * What this build writes, in the names it is free to choose.
     *
     * The prefix is not decoration. `/data/local/tmp` is one directory for the whole device, and the app
     * this fork came from installs beside this one and writes the same directory - neither can tell the
     * other's staged file from its own. So every path this app is allowed to name is named apart from
     * every path that one names, which is what keeps a sweep in either install from deleting a file the
     * other is about to execute.
     *
     * The first three entries are the exception, and they are not this app's to rename: the payload's own
     * loader reads the daemon at `ksud-s25u-kdp` and the stage copy at `.ksud-stage`, and the su daemon
     * the payload leaves running creates `temp_su.sock`. Those three names travel with the payload, so
     * they are the same for both installs - which is the whole reason [sharedWithTheOtherInstall]
     * exists.
     */
    private val staged: List<StagedPath> = listOf(
        StagedPath("/data/local/tmp/ksud-s25u-kdp", ResidueRole.Daemon),
        StagedPath("/data/local/tmp/.ksud-stage", ResidueRole.Daemon),
        StagedPath("/data/local/tmp/temp_su.sock", ResidueRole.Socket),
        StagedPath("/data/local/tmp/rmgnext-helper", ResidueRole.Helper),
        StagedPath("/data/local/tmp/rmgnext-shizuku-payload", ResidueRole.Payload),
        StagedPath("/data/local/tmp/rmgnext-shizuku-exploit.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmgnext-ksud-helper", ResidueRole.Helper),
        StagedPath("/data/local/tmp/rmgnext-payload", ResidueRole.Payload),
        StagedPath("/data/local/tmp/rmgnext-exploit.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmgnext-restart-zygote.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmgnext-restart-zygote.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmgnext-restart-zygote-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmgnext-soft-reboot-keeper.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmgnext-soft-reboot.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmgnext-soft-reboot-ksud.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmgnext-soft-reboot-owner", ResidueRole.Marker),
        StagedPath("/data/local/tmp/.rmgnext-soft-reboot-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmgnext-reboot.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmgnext-reboot.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmgnext-reboot-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmgnext-reload-modules.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmgnext-reload-modules.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmgnext-reload-modules-ksud.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmgnext-reload-modules-owner", ResidueRole.Marker),
        StagedPath("/data/local/tmp/.rmgnext-reload-modules-accepted", ResidueRole.Marker),
    )

    /**
     * The names the builds before this one wrote, under upstream's prefixes.
     *
     * Read, and no longer written. Dropping them from the catalogue would have been the tidy-looking
     * change with the worse answer: a device that ran one of those builds has a `ksu-helper` or an
     * `rmg-payload` in the directory right now, and a check that stops naming it reports a clean device
     * with a detector's favourite names sitting in it.
     */
    private val legacy: List<StagedPath> = listOf(
        StagedPath("/data/local/tmp/ksu-helper", ResidueRole.Helper),
        StagedPath("/data/local/tmp/ksu-payload", ResidueRole.Payload),
        StagedPath("/data/local/tmp/ksu-exploit.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-ksud-helper", ResidueRole.Helper),
        StagedPath("/data/local/tmp/rmg-payload", ResidueRole.Payload),
        StagedPath("/data/local/tmp/rmg-exploit.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-restart-zygote.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-restart-zygote.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-restart-zygote-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmg-soft-reboot-keeper.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-soft-reboot.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-soft-reboot-ksud.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-soft-reboot-owner", ResidueRole.Marker),
        StagedPath("/data/local/tmp/.rmg-soft-reboot-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmg-reboot.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-reboot.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-reboot-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmg-reload-modules.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-reload-modules.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-reload-modules-ksud.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-reload-modules-owner", ResidueRole.Marker),
        StagedPath("/data/local/tmp/.rmg-reload-modules-accepted", ResidueRole.Marker),
    )

    /**
     * Everything this app is or has been responsible for in the directory, in two halves.
     *
     * [staged] is what this build writes and [legacy] is what earlier builds wrote, held apart so that
     * the first can be changed without the second being written again. Everything that wants a
     * catalogue reads this one: the check on the Settings screen, and the sweep that empties the
     * directory.
     *
     * Both transports are listed because both are reachable on one device: a run goes out through
     * Shizuku or through a paired adb, and whichever one it used leaves its own names behind. The
     * repair actions are here for the same reason, including the files their scripts only touch - a
     * marker that is never removed is still a file a detector can list.
     *
     * A hand-kept list, and that is the one thing about this object that can rot: a list that misses a
     * path reports a clean device for a file that is sitting right there. What keeps it whole is a test
     * that reads the app's own sources, collects every `/data/local/tmp/...` literal the staging code
     * writes, and fails when one of them is not in here - the same trick [ManifestPermissionTest] uses
     * for the permissions, and for the same reason.
     *
     * That scan can only cover the names this app writes itself, which is why one entry is here for a
     * different reason: `temp_su.sock` is the daemon's, created by the staged binary rather than by
     * anything in this source tree. It is still this app's residue - it appears on a device because a
     * run of this app put the daemon there - and it is the one path no scan of this code could discover.
     */
    val catalog: List<StagedPath> = staged + legacy

    /**
     * The names both installs write, and can therefore write at the same time.
     *
     * Five of them are upstream's own staged paths - the daemon, its stage copy, and the helper, payload
     * and log its two transports use - and every one of them is a name this fork no longer chooses, so
     * the same names are on a device from either app. The sixth is the su socket, which neither app
     * writes: the payload's daemon does, and it is the same payload on both.
     *
     * Only [StagingSweep] reads this, and only to leave these alone while the other install is present.
     */
    val sharedWithTheOtherInstall: Set<String> = setOf(
        "ksud-s25u-kdp",
        ".ksud-stage",
        "temp_su.sock",
        "ksu-helper",
        "ksu-payload",
        "ksu-exploit.log",
    )

    /**
     * Reads the whole catalog.
     *
     * Blocking and unbuffered on purpose: it is a stat per path, so it is tens of microseconds of work
     * rather than a shell, and a caller that wants it off the main thread only has to say so.
     */
    /**
     * Reads the whole catalog, and the directory through a shell when one answers.
     *
     * Blocking in both halves, and the shell half is the one with a cost: a round trip to a shell that
     * may have to be asked for a grant first. A caller that wants this off the main thread has to say
     * so, and both callers do.
     */
    fun read(): ResidueReport {
        val findings = catalog.map { staged -> ResidueFinding(staged, readingOf(staged.path)) }
        val listed = StagedDirectory.list()
        val catalogued = catalog.mapTo(HashSet()) { it.name }
        return ResidueReport(
            findings = findings,
            directoryVisible = runCatching { Os.stat(DIRECTORY) }.isSuccess,
            extras = listed.orEmpty().filterNot { it.name in catalogued },
            directoryListed = listed != null,
        )
    }

    /** One path, as a reading. */
    private fun readingOf(path: String): ResidueReading = statReading(path).reading

    /**
     * One stat, as a reading plus whether the path is a directory.
     *
     * Shared with [StagedDirectory], which stats the names a listing handed it the same way: a name
     * that arrives by a different route should not get a different answer.
     */
    internal fun statReading(path: String): StagedStat {
        val attempt = runCatching { Os.stat(path) }
        val stat = attempt.getOrNull()
            ?: return StagedStat(
                reading = readingFor(
                    (attempt.exceptionOrNull() as? ErrnoException)?.errno ?: UNKNOWN_ERRNO,
                ),
                isDirectory = false,
            )
        return StagedStat(
            reading = ResidueReading.Present(
                sizeBytes = stat.st_size,
                // Seconds on the device, milliseconds everywhere else in this app.
                modifiedAtMillis = stat.st_mtime * 1_000L,
            ),
            isDirectory = OsConstants.S_ISDIR(stat.st_mode),
        )
    }

    /**
     * What an errno means for the check. The one rule here that is worth a test of its own.
     *
     * Only "there is no such file" is reported as an absence, and everything else - a denied read, a
     * loop, an error this app has never seen - is reported as no answer. The default is the point: a
     * stat this app cannot explain must never come back as a device with nothing on it.
     *
     * The two errnos are written out rather than read from `OsConstants`, and that is a deliberate
     * trade: `OsConstants` holds no compile-time values - they are filled in from the platform at
     * runtime - so a local unit test sees every one of them as zero and could not tell this rule from
     * any other. What is written here instead is the Linux ABI's own numbering, which no Android
     * device varies: `ENOENT` is 2 and `ENOTDIR` is 20 on every architecture this app ships for.
     */
    internal fun readingFor(errno: Int): ResidueReading =
        if (errno == ERRNO_NO_SUCH_FILE || errno == ERRNO_NOT_A_DIRECTORY) {
            ResidueReading.Gone
        } else {
            ResidueReading.Unreadable
        }

    /** `ENOENT`: the name is not there. */
    private const val ERRNO_NO_SUCH_FILE = 2

    /** `ENOTDIR`: a path component is not a directory, which is the same answer arrived at differently. */
    private const val ERRNO_NOT_A_DIRECTORY = 20

    /** The size a person reads, which is the one the platform's own tools would print. */
    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1_000_000L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    /** How long something has been sitting there, from its own timestamp. */
    fun ageLabelOf(modifiedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        val stamps = modifiedAtMillis ?: return "unknown"
        val days = (nowMillis - stamps).coerceAtLeast(0L) / DAY_MILLIS
        return when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "$days days"
            days < 14L -> "a week"
            days < 60L -> "${days / 7} weeks"
            days < 365L -> "${days / 30} months"
            else -> "${days / 365} years"
        }
    }

    private const val DAY_MILLIS = 86_400_000L

    /** What an exception that is not an [ErrnoException] is treated as, which is "no answer". */
    private const val UNKNOWN_ERRNO = Int.MIN_VALUE
}

/**
 * The directory itself, listed through a shell - the half of the reading a catalog cannot do.
 *
 * This app may not read `/data/local/tmp`: the mode is `0771`, and the `x` without the `r` beside it is
 * exactly what that costs it. A shell may, because the directory is owned by the `shell` uid, and this
 * app already opens one for the sweep - KernelSU's `su`, or the `shell` server Shizuku provides. So the
 * listing is asked for in those same two places, in the reverse order: reading needs no privilege and
 * deleting does, so the unprivileged shell is asked first.
 *
 * One command, one round trip, and one marker in the output. The marker is what separates "the directory
 * is empty" from "the shell never got as far as listing it": without it a refusal, a missing `sh` or an
 * error would all read as a directory with nothing in it, which is the one failure this whole check
 * exists to not make.
 */
internal object StagedDirectory {

    /** What a name is printed under. A real name cannot mimic it: names arrive bare. */
    internal const val NAME_PREFIX = "has "

    /** The last line, which is the difference between an empty answer and no answer. */
    internal const val LISTED_MARK = "listed"

    /**
     * The listing, as one command.
     *
     * A glob rather than `ls`, and both halves of the glob because a dot-name is exactly the shape a
     * staging marker takes (`.ksud-stage`, `.rmg-reboot-accepted`). `[ -e ]` is what makes an unmatched
     * glob mean nothing rather than a file called `*`. Built from [StagedResidue.DIRECTORY] so the
     * directory is named once in this source tree.
     */
    internal fun command(): String {
        val directory = StagedResidue.DIRECTORY
        return "for e in $directory/* $directory/.[!.]*; do [ -e \"\$e\" ] || continue; " +
            "printf '$NAME_PREFIX%s\\n' \"\${e##*/}\"; done; " +
            "printf '$LISTED_MARK\\n'; exit 0"
    }

    /**
     * The names in a listing's output, or null when the listing never ran.
     *
     * Everything that is not a marked name is passed over, which is what lets a shell's own complaint
     * travel in the same stream without being read as a file.
     */
    internal fun namesIn(output: String): List<String>? {
        val lines = output.lineSequence().map { it.trim() }.toList()
        if (lines.none { it == LISTED_MARK }) return null
        return lines.filter { it.startsWith(NAME_PREFIX) }
            .map { it.removePrefix(NAME_PREFIX) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * The listing, or null when no shell answered.
     *
     * Every name it brings back is then stat'd here, in this app's own context, because that is the
     * reading that matters: another app's view of this directory is this app's view of it. So a name
     * this app cannot stat is still reported - as [ResidueReading.Unreadable] - rather than dropped.
     */
    fun list(): List<TempEntry>? {
        val command = command()
        val result = runCatching {
            KernelSuRuntime.unprivilegedShell(command)
                ?: KernelSuRuntime.rootShell(command, timeoutSeconds = LISTING_TIMEOUT_SECONDS)
        }.getOrNull() ?: return null
        val names = namesIn(result.output) ?: return null
        return names.map { name ->
            val stat = StagedResidue.statReading("${StagedResidue.DIRECTORY}/$name")
            TempEntry(name = name, reading = stat.reading, isDirectory = stat.isDirectory)
        }
    }

    /**
     * How long a listing waits for a root shell.
     *
     * Deliberately shorter than a command is given in general: this one is asked for while a screen is
     * opening, and the only thing that can make it wait is KernelSU showing the user its grant prompt. A
     * listing that has not answered by then is reported as no listing, which the card has a sentence
     * for - where a Settings screen that sat there for half a minute has no sentence at all.
     */
    private const val LISTING_TIMEOUT_SECONDS = 8L
}
