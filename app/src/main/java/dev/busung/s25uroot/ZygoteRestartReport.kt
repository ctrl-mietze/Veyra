package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File

/**
 * What a framework restart came back with.
 *
 * The restart is the one repair action whose result the app cannot report at the time, and for a
 * reason that is not a limitation of the app: `setprop ctl.restart zygote` answers whether init took
 * the property, and the framework that would have shown the answer is the thing being replaced. Every
 * other action reports itself in the dialog that started it; this one used to report only that the
 * request was made, which is the same thing the app says when init ignores it.
 *
 * So the restart's detached child stays behind and asks the phone what replaced the framework, and
 * writes down what it found where the app can read it without a shell - the app's own files directory,
 * which root may write and the app may read. The app reads that record when it next runs.
 */
internal enum class FrameworkReturn {
    /** A new system_server: the framework really was replaced. */
    Restarted,

    /** Zygote's pid never changed, so init never restarted the service. */
    NotRestarted,

    /** Zygote was replaced and no system_server came back within the wait. */
    FrameworkMissing,

    /** The phone rebooted while this was being checked, so this boot has nothing to report on. */
    BootChanged,

    /** Nothing about the restart could be read, so nothing is claimed about it. */
    Unreadable,
}

/**
 * What the framework that came back has of the modules that inject into it.
 *
 * Two of these are findings and two are not, which is the reason for the type: "no module code is
 * mapped into the new framework" is a restart that came back without the modules it was spent on,
 * while "the mapping could not be read" is nothing at all - a device where `/proc` does not answer is
 * not a device whose modules failed to load, and reporting one as the other is the mistake this whole
 * app avoids everywhere else.
 */
internal enum class ModuleCodeInFramework {
    /** Module code is mapped into the new framework, so injection is live in it. */
    Mapped,

    /** The new framework has no module code mapped into it. */
    Absent,

    /** `/proc/<pid>/maps` could not be read, so this is unknown rather than absent. */
    Unreadable,

    /** No enabled module carries a `zygisk` directory, so there was nothing to inject. */
    NothingToInject,
}

/** One restart's verification, as the child that ran it wrote it down. */
internal data class FrameworkRestartReport(
    val bootToken: String,
    val returned: FrameworkReturn,
    /** The Zygote process after the restart, or "-" when it could not be read. */
    val zygotePid: String,
    /** The system_server process after the restart, or "-" when there was none to read. */
    val systemServerPid: String,
    /** Roughly how long the framework took to come back, in whole seconds. */
    val waitedSeconds: Int,
    val moduleCode: ModuleCodeInFramework,
    /**
     * Enabled modules whose userspace service was not running when the framework was back.
     *
     * Comma-separated in the record rather than space-separated like every other list on the device:
     * the record is itself a space-separated list of fields, so a list inside it needs a separator of
     * its own.
     */
    val missingServices: List<String>,
) {
    /**
     * Whether this is something to do about, rather than a restart that did what it said.
     *
     * A restart that came back with the modules is the whole point of the action and needs no
     * decoration; anything else is a finding, and a finding is worth showing.
     */
    val needsAttention: Boolean
        get() = returned != FrameworkReturn.Restarted ||
            moduleCode == ModuleCodeInFramework.Absent ||
            missingServices.isNotEmpty()
}

/**
 * The restart's verification, in the one place its record is written and read.
 *
 * Both halves live here for the reason this codebase keeps one rule in one place: the child's line and
 * the parser that reads it are one format, and split across two files they would drift the first time
 * a field was added.
 *
 * The snippets below are interpolated into the restart child's own script, which is why they may use
 * `EXPECTED_BOOT` and `current_boot()`: they are written to be read in that script's body, not on their
 * own.
 */
internal object ZygoteRestartReport {

    /** What the child's line starts with, and the only thing that identifies it as this record. */
    internal const val RECORD_PREFIX = "RMG_FRAMEWORK_RESTART"

    /**
     * How long the framework is given to come back, in whole seconds.
     *
     * Counted in iterations of a one-second wait rather than measured with `date`, so the record's own
     * "took" is the same number the loop counted and nothing depends on a clock the child may not have.
     * A framework restart takes tens of seconds on a device with modules; this is longer than that and
     * still bounded, because the app is gone by the time it runs and nothing is waiting on it.
     */
    internal const val FRAMEWORK_WAIT_ITERATIONS = 45

    /**
     * How long module code is given to appear in the new framework.
     *
     * The injection is done by the modules' own daemons as the new processes fork, so it trails the
     * framework by a moment rather than arriving with it. Short, because a missing mapping after this
     * is a finding rather than a wait.
     */
    internal const val INJECTION_WAIT_ITERATIONS = 15

    /** Where the record is kept, in the app's own files directory so it needs no shell to read. */
    internal fun file(context: Context): File = File(context.filesDir, "framework-restart-report")

    /**
     * What the restart is replacing, read before init is asked for anything.
     *
     * After the request there is no "before" left to compare against, which is the only way to tell a
     * restart that happened from a `ctl.restart` that init never acted on. The module count is read
     * from the modules themselves for the same reason the mount probe counts them that way: installing
     * or disabling one changes the expectation without this script having to know which modules exist.
     *
     * An earlier restart's report is removed here, before the request, rather than after the new one is
     * written: a restart that never comes back must not leave a stale record behind to be read as its.
     */
    internal fun baselineSnippet(reportPath: String): String = """
        REPORT=${shellQuote(reportPath)}
        rmg_zygote_before=${'$'}(pidof zygote zygote64 2>/dev/null)
        rmg_ss_before=${'$'}(pidof system_server 2>/dev/null)
        rmg_injectors=0
        for rmg_inject_dir in /data/adb/modules/*/; do
            [ -e "${'$'}rmg_inject_dir/disable" ] && continue
            [ -e "${'$'}rmg_inject_dir/remove" ] && continue
            [ -d "${'$'}rmg_inject_dir/zygisk" ] && rmg_injectors=${'$'}((rmg_injectors + 1))
        done
        rm -f -- "${'$'}REPORT"
    """.trimIndent()

    /**
     * What the framework came back with, asked of the phone rather than assumed from the request.
     *
     * The question is answered in the order the evidence arrives. A system_server with a different pid
     * is the framework having been replaced. When it does not appear, Zygote's own pid says which
     * failure it was: unchanged means init never restarted the service at all, and changed means the
     * framework did not come back. Both are findings the acceptance alone cannot distinguish, and the
     * second one is a phone that needs a reboot.
     *
     * The modules are then asked about in two ways, because they are the reason the restart was worth
     * spending: their services, in the same reading the restart refused on, and whether module code is
     * mapped into the new framework - the injection itself, which is what "the modules are loaded in
     * this framework" actually means. An unreadable `/proc/<pid>/maps` is recorded as unreadable rather
     * than as absent, because an unreadable map is not an empty one.
     */
    internal fun verificationSnippet(): String = """
        rmg_waited=0
        rmg_ss_after=
        while [ "${'$'}rmg_waited" -lt $FRAMEWORK_WAIT_ITERATIONS ]; do
            rmg_now=${'$'}(pidof system_server 2>/dev/null)
            if [ -n "${'$'}rmg_now" ] && [ "${'$'}rmg_now" != "${'$'}rmg_ss_before" ]; then
                rmg_ss_after=${'$'}rmg_now
                break
            fi
            # A reboot replaces the framework too, and a record about a different boot would be a record
            # about a framework this one never had.
            [ "${'$'}(current_boot)" = "${'$'}EXPECTED_BOOT" ] || break
            rmg_waited=${'$'}((rmg_waited + 1))
            sleep 1
        done
        rmg_zygote_after=${'$'}(pidof zygote zygote64 2>/dev/null)
        [ -n "${'$'}rmg_zygote_after" ] || rmg_zygote_after=-
        [ -n "${'$'}rmg_ss_after" ] || rmg_ss_after=-

        rmg_verdict=unreadable
        if [ "${'$'}(current_boot)" != "${'$'}EXPECTED_BOOT" ]; then
            rmg_verdict=boot-changed
        elif [ "${'$'}rmg_ss_after" != - ]; then
            rmg_verdict=restarted
        elif [ -z "${'$'}rmg_zygote_before" ] || [ "${'$'}rmg_zygote_after" = - ]; then
            rmg_verdict=unreadable
        elif [ "${'$'}rmg_zygote_after" = "${'$'}rmg_zygote_before" ]; then
            rmg_verdict=not-restarted
        else
            rmg_verdict=framework-missing
        fi

        rmg_mapped=-
        rmg_missing_after=-
        if [ "${'$'}rmg_verdict" = restarted ]; then
            ${moduleServiceWaitSnippet()}
            # Comma-separated, because the record itself is a space-separated list of fields: a list
            # inside it needs a separator of its own, or the parser reads the second id as a field it
            # does not recognise and silently keeps only the first.
            rmg_missing_after=${'$'}(printf '%s' "${'$'}{rmg_missing_services# }" | tr ' ' ',')
            [ -n "${'$'}rmg_missing_after" ] || rmg_missing_after=-
            if [ "${'$'}rmg_injectors" -eq 0 ]; then
                rmg_mapped=none
            elif [ -r "/proc/${'$'}rmg_ss_after/maps" ]; then
                rmg_mapped=no
                rmg_map_waited=0
                while [ "${'$'}rmg_map_waited" -lt $INJECTION_WAIT_ITERATIONS ]; do
                    if /system/bin/grep -qE '/data/adb/modules(_update)?/[^ ]*/zygisk/' \
                        "/proc/${'$'}rmg_ss_after/maps" 2>/dev/null; then
                        rmg_mapped=yes
                        break
                    fi
                    # The pid is re-read: a framework that restarted again would have put a different
                    # process behind this path, so the reading would be about that one and not this.
                    # A pid that is gone leaves the mapping unreadable rather than absent - the process
                    # whose maps these were is no longer the framework, so nothing is claimed about it.
                    [ "${'$'}(pidof system_server 2>/dev/null)" = "${'$'}rmg_ss_after" ] || {
                        rmg_mapped=unreadable
                        break
                    }
                    rmg_map_waited=${'$'}((rmg_map_waited + 1))
                    sleep 1
                done
            else
                rmg_mapped=unreadable
            fi
        fi

        rmg_record="${'$'}RECORD_PREFIX boot=${'$'}EXPECTED_BOOT verdict=${'$'}rmg_verdict zygote=${'$'}rmg_zygote_after system_server=${'$'}rmg_ss_after took=${'$'}rmg_waited mapped=${'$'}rmg_mapped missing=${'$'}rmg_missing_after"
        # Written where the app can read it without a shell, and echoed as well: the launcher points this
        # child's output at its log, so a record whose file write is refused is still on disk in the log
        # rather than lost.
        printf '%s\n' "${'$'}rmg_record" > "${'$'}REPORT" 2>/dev/null || true
        chmod 0644 "${'$'}REPORT" 2>/dev/null || true
        printf '%s\n' "${'$'}rmg_record"
    """.trimIndent()

    /**
     * The record, or null when the output does not carry one this app can describe.
     *
     * A verdict this build does not know is [FrameworkReturn.Unreadable] rather than nothing: the phone
     * answered and the app cannot say what it answered, which is a fact worth reporting, and silence is
     * the one answer that reads as "no news". A record missing the fields every verdict depends on is
     * not described at all.
     */
    internal fun parse(output: String): FrameworkRestartReport? {
        val line = output.lineSequence().map(String::trim)
            .firstOrNull { it.startsWith(RECORD_PREFIX) }
            ?: return null
        val fields = line.substringAfter(RECORD_PREFIX).trim()
            .split(' ')
            .mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.take(separator) to field.substring(separator + 1)
            }
            .toMap()
        val waited = fields["took"]?.toIntOrNull() ?: return null
        val verdict = when (fields["verdict"]) {
            "restarted" -> FrameworkReturn.Restarted
            "not-restarted" -> FrameworkReturn.NotRestarted
            "framework-missing" -> FrameworkReturn.FrameworkMissing
            "boot-changed" -> FrameworkReturn.BootChanged
            "unreadable", null -> FrameworkReturn.Unreadable
            else -> FrameworkReturn.Unreadable
        }
        val code = when (fields["mapped"]) {
            "yes" -> ModuleCodeInFramework.Mapped
            "no" -> ModuleCodeInFramework.Absent
            "none" -> ModuleCodeInFramework.NothingToInject
            else -> ModuleCodeInFramework.Unreadable
        }
        return FrameworkRestartReport(
            bootToken = fields["boot"].orEmpty(),
            returned = verdict,
            zygotePid = fields["zygote"]?.takeIf(String::isNotBlank) ?: "-",
            systemServerPid = fields["system_server"]?.takeIf(String::isNotBlank) ?: "-",
            waitedSeconds = waited,
            moduleCode = code,
            missingServices = fields["missing"]
                ?.split(',')
                ?.filter { it.isNotBlank() && it != "-" }
                ?: emptyList(),
        )
    }

    /**
     * The record for [bootToken], reading the file away as it goes, or null when there is nothing to
     * read.
     *
     * Read once and removed, because a restart's result is news: a record left in place would be read
     * again by every process that starts afterwards and reported as if it had just happened. A record
     * from another boot is discarded rather than reported, since it describes a framework this boot
     * does not have - the restart that wrote it ended in a reboot or was never checked in this one.
     *
     * A boot this app cannot name at all - a device that will not answer for its own boot id - leaves
     * the file where it is rather than reading it away: the record may well be this boot's, and
     * discarding it would be throwing away the only account of a restart on the strength of a failed
     * reading.
     */
    internal fun consume(file: File, bootToken: String?): FrameworkRestartReport? {
        if (bootToken == null) return null
        val text = runCatching { if (file.exists()) file.readText() else null }.getOrNull() ?: return null
        runCatching { file.delete() }
        val report = parse(text) ?: return null
        return report.takeIf { it.bootToken == bootToken }
    }

    /**
     * The record in the words the user reads, one line per fact.
     *
     * Here rather than at the screen for the same reason the refusal tokens' sentences are next to the
     * scripts: the log line and the card are the same account of the same event, and two copies of it
     * would be two accounts the day one was edited.
     */
    internal fun describe(context: Context, report: FrameworkRestartReport): List<String> {
        val lines = mutableListOf(
            when (report.returned) {
                FrameworkReturn.Restarted -> context.getString(
                    R.string.framework_restart_came_back,
                    report.waitedSeconds,
                    report.zygotePid,
                    report.systemServerPid,
                )
                FrameworkReturn.NotRestarted -> context.getString(
                    R.string.framework_restart_not_restarted,
                    report.zygotePid,
                    report.waitedSeconds,
                )
                FrameworkReturn.FrameworkMissing -> context.getString(
                    R.string.framework_restart_framework_missing,
                    report.waitedSeconds,
                )
                FrameworkReturn.BootChanged -> context.getString(R.string.framework_restart_boot_changed)
                FrameworkReturn.Unreadable -> context.getString(R.string.framework_restart_unreadable)
            },
        )
        if (report.returned == FrameworkReturn.Restarted) {
            when (report.moduleCode) {
                ModuleCodeInFramework.Mapped ->
                    lines += context.getString(R.string.framework_restart_module_code_mapped)
                ModuleCodeInFramework.Absent ->
                    lines += context.getString(R.string.framework_restart_module_code_absent)
                ModuleCodeInFramework.Unreadable ->
                    lines += context.getString(R.string.framework_restart_module_code_unreadable)
                ModuleCodeInFramework.NothingToInject -> Unit
            }
        }
        if (report.missingServices.isNotEmpty()) {
            lines += context.getString(
                R.string.framework_restart_module_services_missing,
                report.missingServices.joinToString(", "),
            )
        }
        return lines
    }

    /** The same account as one line for the app's log, which is where a result outlives the card. */
    internal fun logLine(context: Context, report: FrameworkRestartReport): String =
        context.getString(
            R.string.log_framework_restart_report,
            describe(context, report).joinToString(" "),
        )
}
