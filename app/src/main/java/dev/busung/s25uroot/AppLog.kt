package ctrl.mietze.veyraroot

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How loud a line is. The letters are the ones logcat uses, so a log copied out of the app still
 * reads the way the same statement would on a workstation.
 */
enum class AppLogLevel(val mark: Char) {
    Debug('D'),
    Info('I'),
    Warn('W'),
    Error('E'),
}

/**
 * What the app files its own lines under.
 *
 * A purpose rather than a class name, because that is what someone reading the tab is scanning for -
 * and "why did this not work" has three or four different answers, which is why the run, the boot gate,
 * the transport and the catalog each get their own word to look under.
 */
internal object AppLogTags {
    const val RUN = "Run"
    const val BOOT = "Boot"
    const val SHIZUKU = "Shizuku"
    const val KERNEL_SU = "KernelSU"
    const val CATALOG = "Catalog"
    const val WIRELESS_ADB = "Wireless-ADB"
    const val PERMISSIONS = "Permissions"
    const val UPDATER = "Updater"
    const val BUILDER = "Builder"

    /**
     * A restart this app asked for, including the ones that leave Android - a phone that comes back from
     * Odin or Download mode has this line to say why it went there.
     */
    const val RESTART = "Restart"

    /** What this app leaves on the device, read back the way another app would see it. */
    const val STAGING = "Staging"
    const val APP = "App"
}

/** One line of the app's own log: when, how loud, who, and what. */
data class AppLogEntry(
    val atMillis: Long,
    val level: AppLogLevel,
    val tag: String,
    val message: String,
)

/**
 * The app's own log, kept where the app can show it.
 *
 * History holds what a payload printed; this holds what the app did around it - which transport a run
 * chose, why a boot install stood down, what a catalog fetch answered, what a grant attempt was
 * refused for. Every one of those is a question whose only answer is in logcat today, which is no
 * answer at all on a phone with no ADB in reach - the exact situation where someone is looking.
 *
 * Written to a file rather than kept in memory, for two reasons this app cannot avoid: the boot and
 * auto-root services run in their own processes, so a buffer in the UI process would never see their
 * lines; and the run the log is being read for may have happened before any screen existed.
 *
 * A file also decides how it is written. Each line is one open-append-close, never a held-open
 * stream, because the trim below replaces the file and a stream held across that would go on
 * appending to an unlinked inode for the life of the process - the boot service would fall silent for
 * good after one trim. Appends to a file opened for append are atomic for a line this size, so two
 * processes writing at once interleave whole lines rather than halves of them.
 */
object AppLog {
    private const val FILE_NAME = "app-log.txt"

    /** Past this, the file is rewritten down to [KEEP_FILE_CHARS]. Both are character counts. */
    private const val MAX_FILE_CHARS = 512 * 1024
    private const val KEEP_FILE_CHARS = 256 * 1024

    /** The ceiling on what is held for the screen, which is a fraction of the file it was read from. */
    private const val MAX_ENTRIES = 4000
    private const val MAX_MESSAGE_CHARS = 1000

    /**
     * A trim costs a read and a rewrite of the file, so the size is asked for once every this many
     * writes rather than on each one.
     */
    private const val TRIM_CHECK_EVERY = 64

    private val lock = Any()
    private val buffer = ArrayDeque<AppLogEntry>()
    private var file: File? = null
    private var writesSinceTrimCheck = 0

    private val mutableLog = MutableStateFlow<List<AppLogEntry>>(emptyList())

    /** The whole log, oldest first. Replaced wholesale by [reload]. */
    val log: StateFlow<List<AppLogEntry>> = mutableLog.asStateFlow()

    /**
     * Points the log at this app's files directory.
     *
     * Deliberately does no reading: this runs in every process at startup, including the boot
     * service's, and none of them needs the file's contents to append to it. A screen that shows the
     * log calls [reload] itself, off the main thread.
     */
    fun install(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    fun debug(tag: String, message: String) = record(AppLogLevel.Debug, tag, message)

    fun info(tag: String, message: String) = record(AppLogLevel.Info, tag, message)

    fun warn(tag: String, message: String) = record(AppLogLevel.Warn, tag, message)

    fun error(tag: String, message: String) = record(AppLogLevel.Error, tag, message)

    /** Records a failure with the throwable's own words, since the message alone rarely says why. */
    fun error(tag: String, message: String, error: Throwable) =
        record(AppLogLevel.Error, tag, "$message: ${error.javaClass.simpleName}: ${error.message}")

    fun record(level: AppLogLevel, tag: String, message: String) {
        val entry = AppLogEntry(
            atMillis = System.currentTimeMillis(),
            level = level,
            tag = AppLogFormat.normalizeTag(tag),
            message = AppLogFormat.oneline(message, MAX_MESSAGE_CHARS),
        )
        // logcat as well, so the habits that already work - `adb logcat -s` - keep working.
        Log.println(level.priority, entry.tag, entry.message)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            append(AppLogFormat.line(entry))
            mutableLog.value = buffer.toList()
        }
    }

    /**
     * Reads the file back, which is what brings in lines other processes wrote.
     *
     * The buffer is replaced rather than merged: the file is the record, and every line in the buffer
     * was written to it, so what is on disk is what happened.
     */
    fun reload() {
        val target = file ?: return
        val text = runCatching { target.readText() }.getOrDefault("")
        val parsed = text.lineSequence().mapNotNull(AppLogFormat::parse).toList()
        synchronized(lock) {
            buffer.clear()
            buffer.addAll(parsed.takeLast(MAX_ENTRIES))
            mutableLog.value = buffer.toList()
        }
    }

    /**
     * Empties the log, on disk and on screen.
     *
     * Nothing is kept: someone clearing this is starting from here, and a "cleared" line left behind
     * would be the only thing in an otherwise empty log.
     */
    fun clear() {
        val target = file ?: return
        synchronized(lock) {
            buffer.clear()
            mutableLog.value = emptyList()
            runCatching { target.delete() }
            writesSinceTrimCheck = 0
        }
    }

    /** Everything in [entries] as one block of text, oldest first, for copying out. */
    fun asText(entries: List<AppLogEntry>): String =
        entries.joinToString(separator = "\n", transform = AppLogFormat::line)

    private fun append(line: String) {
        val target = file ?: return
        runCatching { target.appendText(line + "\n") }.onFailure { error ->
            Log.w("AppLog", "Unable to write the app log: ${error.message}")
        }
        writesSinceTrimCheck++
        if (writesSinceTrimCheck >= TRIM_CHECK_EVERY) {
            writesSinceTrimCheck = 0
            trimIfOversized(target)
        }
    }

    /**
     * Keeps the newest [KEEP_FILE_CHARS] of an oversized file, by writing a new file and renaming it
     * over the old one.
     *
     * Through a rename because the alternative - truncating in place - would have a reader between
     * the two halves of the trim, and this file is read by a screen. What the rename costs is the
     * lines another process appends during it: they go to the file being replaced, and they are gone.
     * That is a few lines out of several thousand, once every 512 KB, which is why it is done this
     * way rather than under a lock every process would have to take to write a log line.
     */
    private fun trimIfOversized(target: File) {
        val text = runCatching { target.readText() }.getOrNull() ?: return
        if (text.length <= MAX_FILE_CHARS) return
        runCatching {
            val replacement = File(target.parentFile, "$FILE_NAME.trim")
            replacement.writeText(AppLogFormat.keepTailLines(text, KEEP_FILE_CHARS))
            if (!replacement.renameTo(target)) replacement.delete()
        }
    }
}

/**
 * The file's line format, in the pieces that can be wrong on their own.
 *
 * Pure on purpose: this is the part of the log that can be checked without a device - that a line
 * round-trips, that a message with a newline in it does not become two lines, that a trim lands on a
 * line boundary - and all of it would otherwise be buried in code that needs a `Context` to run.
 */
internal object AppLogFormat {
    /**
     * `09-17 21:04:33.123 I InstallViewModel text`. No year, because a phone's log does not live long
     * enough for one to be worth the four characters it would take from every line; [parse] assumes
     * the current one, which is what makes a log that spans New Year mis-sort rather than misread.
     */
    private val linePattern = Regex(
        """^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3}) ([DIWE]) (\S+) (.*)$""",
    )

    /**
     * Newlines are the one thing a message cannot keep.
     *
     * One entry is one line, which is what makes the file parseable, trimmable on a boundary, and
     * readable by eye. A stack trace or a shell command with a newline in it arrives here, so the
     * line breaks are marked instead of dropped: `\n` is written as a literal backslash-n.
     */
    fun oneline(message: String, maxChars: Int = MAX_ONELINE_CHARS): String {
        val flattened = message
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", " \\n ")
        return if (flattened.length <= maxChars) {
            flattened
        } else {
            flattened.take(maxChars) + " …"
        }
    }

    /** A tag is a word: whitespace would put a space in the middle of the parsed line's fields. */
    fun normalizeTag(tag: String): String {
        val collapsed = tag.trim().replace(Regex("""\s+"""), "-")
        return collapsed.ifBlank { "App" }
    }

    fun line(entry: AppLogEntry): String = buildString {
        append(stamp(entry.atMillis))
        append(' ')
        append(entry.level.mark)
        append(' ')
        append(normalizeTag(entry.tag))
        append(' ')
        append(oneline(entry.message, Int.MAX_VALUE))
    }

    /** Long enough for a shell command or a URL, short enough that one line cannot fill the file. */
    const val MAX_ONELINE_CHARS = 1000

    /** Null for anything that is not one of our lines - a partial write, or a foreign file. */
    fun parse(line: String, now: Long = System.currentTimeMillis()): AppLogEntry? {
        val match = linePattern.matchEntire(line.trimEnd()) ?: return null
        val (month, day, hour, minute, second, millis, mark, tag, message) = match.destructured
        val level = AppLogLevel.entries.firstOrNull { it.mark == mark.first() } ?: return null
        return AppLogEntry(
            atMillis = stampOf(month, day, hour, minute, second, millis, now) ?: return null,
            level = level,
            tag = tag,
            message = message,
        )
    }

    /**
     * The newest [maxChars] of [text], starting at a line boundary so what is kept stays parseable.
     *
     * The first line is dropped unless the text fits whole, because the cut almost never lands on a
     * boundary and a fragment at the top would be read as a line with no stamp - which is exactly the
     * shape [parse] rejects.
     */
    fun keepTailLines(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val tail = text.takeLast(maxChars)
        val firstBreak = tail.indexOf('\n')
        return if (firstBreak == -1) "" else tail.substring(firstBreak + 1)
    }

    /**
     * Whether a line passes the screen's two questions: loud enough, and containing what was typed.
     *
     * A level is a floor rather than a match - the reason to look at a log is usually a warning, and
     * a warning's cause is in the lines below it. The query looks at the tag as well as the message,
     * since "everything from AutoRootService" is as ordinary a question as "everything about
     * Shizuku".
     */
    fun matches(entry: AppLogEntry, minLevel: AppLogLevel, query: String): Boolean {
        if (entry.level.ordinal < minLevel.ordinal) return false
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return entry.message.contains(needle, ignoreCase = true) ||
            entry.tag.contains(needle, ignoreCase = true)
    }

    /** The stamp as the file writes it, so the screen shows exactly what the file holds. */
    fun stamp(atMillis: Long): String =
        stampFormat.get().format(Date(atMillis))

    private fun stampOf(
        month: String,
        day: String,
        hour: String,
        minute: String,
        second: String,
        millis: String,
        now: Long,
    ): Long? = runCatching {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MONTH, month.toInt() - 1)
            set(Calendar.DAY_OF_MONTH, day.toInt())
            set(Calendar.HOUR_OF_DAY, hour.toInt())
            set(Calendar.MINUTE, minute.toInt())
            set(Calendar.SECOND, second.toInt())
            set(Calendar.MILLISECOND, millis.toInt())
        }.timeInMillis
    }.getOrNull()

    // SimpleDateFormat is not thread safe and this is written to from whatever thread logged.
    private val stampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

/** The logcat level this entry is filed under, so the app's log and logcat stay the same story. */
internal val AppLogLevel.priority: Int
    get() = when (this) {
        AppLogLevel.Debug -> Log.DEBUG
        AppLogLevel.Info -> Log.INFO
        AppLogLevel.Warn -> Log.WARN
        AppLogLevel.Error -> Log.ERROR
    }
