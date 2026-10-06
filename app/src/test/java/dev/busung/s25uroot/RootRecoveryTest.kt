package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BOOT = "0f2a4c6e-1b2d-4f6a-8c0e-2d4f6a8c0e2d"
private const val ACCEPTED = "/data/local/tmp/.rmgnext-restart-zygote-accepted"
// Where the app's own data directory puts the report, spelled out rather than read from a Context:
// what these tests check is that the script writes and reads back the path it is handed.
private const val REPORT = "/data/user/0/dev.rushiranpise.rmgnext/files/framework-restart-report"


class RootRecoveryTest {

    // --- what the installed daemon can be asked to do -------------------------------------------------

    @Test
    fun `the daemon this app's feed installs has late-load but no soft reboot`() {
        // Verbatim shape of that daemon's clap command list: it lists late-load, and the only
        // mention of a soft reboot is a feature name.
        val help = """
            Usage: ksud <COMMAND>
            Commands:
              late-load    Load kernelsu.ko into a running kernel
              module       Manage KernelSU modules
              feature      Manage feature config
              emulated-soft-reboot
        """.trimIndent()

        val capabilities = parseKsudCapabilities(help)

        assertTrue(capabilities.lateLoad)
        assertFalse(capabilities.softReboot)
    }

    @Test
    fun `the fork's daemon is recognised as supporting a soft reboot`() {
        val help = "Commands:\n  late-load\n  soft-reboot\n  insmod\n  boot-patch"

        val capabilities = parseKsudCapabilities(help)

        assertTrue(capabilities.lateLoad)
        assertTrue(capabilities.softReboot)
    }

    @Test
    fun `a feature name containing the command's name is not the command`() {
        assertFalse(parseKsudCapabilities("emulated-soft-reboot").softReboot)
        assertFalse(parseKsudCapabilities("soft_reboot").softReboot)
        assertFalse(parseKsudCapabilities("soft-reboot-v2").softReboot)
    }

    @Test
    fun `help that lists nothing is nothing`() {
        assertFalse(parseKsudCapabilities("").any)
        assertFalse(parseKsudCapabilities("ksud: not found").any)
    }

    // --- the app's side of the handoff --------------------------------------------------------------

    @Test
    fun `the marker the child writes is what counts as accepted`() {
        val outcome = RootRecovery.parseHandoff("RMG_RECOVERY_ACCEPTED")

        assertTrue(outcome.accepted)
    }

    @Test
    fun `a child that refused says why, in words`() {
        val outcome = RootRecovery.parseHandoff("error:boot-changed")

        assertFalse(outcome.accepted)
        // The child's token is a name for a check; what the user reads has to say what it means for
        // the phone, which is why the app owns the words.
        assertEquals(
            "The phone rebooted before the action could run, so it was abandoned",
            outcome.detail,
        )
    }

    @Test
    fun `a refusal the app does not know is still reported, and its detail is kept`() {
        // A child from a newer build than this app must not be silenced by the gap.
        assertEquals("some new check", RootRecovery.refusalDetail("some-new-check"))
        assertEquals(
            "A module that injects into Zygote is enabled but its service is not running, so a restart " +
                "now would bring the framework back without it (zygisksu)",
            RootRecovery.refusalDetail("module-services-not-ready:zygisksu"),
        )
        assertEquals(
            "A module that injects into Zygote is enabled but its service is not running, so a restart " +
                "now would bring the framework back without it (zygisksu zygisk_lsposed)",
            RootRecovery.parseHandoff("error:module-services-not-ready:zygisksu zygisk_lsposed").detail,
        )
    }

    @Test
    fun `a silent child is a refusal, not an acceptance`() {
        // The whole point of the handoff: a fork is not an action.
        assertFalse(RootRecovery.parseHandoff("").accepted)
        assertFalse(RootRecovery.parseHandoff("8\n").accepted)
        assertEquals("The recovery action did not answer", RootRecovery.parseHandoff("").detail)
    }

    @Test
    fun `the log tail a failed launch prints is not mistaken for success`() {
        val outcome = RootRecovery.parseHandoff("the recovery action did not acknowledge the request\n")

        assertFalse(outcome.accepted)
        assertTrue(outcome.detail.isNotBlank())
    }

    // --- the launcher ------------------------------------------------------------------------------

    @Test
    fun `the launcher writes the script whole, then detaches it`() {
        val command = RootRecovery.detachedLaunchCommand(
            script = "#!/system/bin/sh\necho hi\n",
            scriptPath = "/data/local/tmp/x.sh",
            logPath = "/data/local/tmp/x.log",
            acceptedPath = ACCEPTED,
        )

        // Written aside and moved into place, so a half-written script can never run.
        assertTrue(command.contains("tmp=\"\$script.tmp.\$\$\""))
        assertTrue(command.contains("chmod 0700 \"\$tmp\""))
        assertTrue(command.contains("mv -f \"\$tmp\" \"\$script\""))
        // Cleared before the child starts, so an earlier attempt's marker cannot pass for this one's.
        assertTrue(command.indexOf("rm -f -- \"\$accepted\"") < command.indexOf("setsid sh"))
        assertTrue(command.contains("setsid sh \"\$script\""))
    }

    @Test
    fun `the script body is embedded literally, not expanded by the outer shell`() {
        val script = "#!/system/bin/sh\necho \"\$EXPECTED_BOOT\"\n"
        val command = RootRecovery.detachedLaunchCommand(script, "/x.sh", "/x.log", ACCEPTED)

        assertTrue(command.contains("<<'RMG_RECOVERY_EOF'"))
        assertTrue(command.contains("echo \"\$EXPECTED_BOOT\""))
    }

    @Test
    fun `the launcher waits for the child and reports its answer`() {
        val command = RootRecovery.detachedLaunchCommand("#!/system/bin/sh\n", "/x.sh", "/x.log", ACCEPTED)

        assertTrue(command.contains("RMG_RECOVERY_ACCEPTED"))
        assertTrue(command.contains("sleep 0.1"))
        assertTrue(command.contains("tail -n 8"))
        // A child that never answers exits non-zero rather than looking scheduled.
        assertTrue(command.trimEnd().endsWith("exit 78"))
    }

    // --- the restart -------------------------------------------------------------------------------

    @Test
    fun `zygote is restarted through init, never killed`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT)

        assertTrue(script.contains("setprop ctl.restart zygote"))
        assertFalse(script.contains("kill"))
        assertFalse(script.contains("SIGKILL"))
    }

    @Test
    fun `the secondary zygote is restarted first, and only when it runs`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT)

        val secondary = script.indexOf("ctl.restart zygote_secondary")
        // The last occurrence, because the secondary command literally starts with the primary's.
        val primary = script.lastIndexOf("ctl.restart zygote")
        assertTrue(secondary in 0 until primary)
        assertTrue(script.contains("[ \"\$(getprop init.svc.zygote_secondary 2>/dev/null)\" = \"running\" ]"))
    }

    @Test
    fun `the restart validates root, the boot and a live framework before it acts`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT)

        assertTrue(script.contains("[ \"\$(id -u 2>/dev/null)\" = \"0\" ] || reject_handoff 'not-root'"))
        assertTrue(script.contains("boot-changed"))
        assertTrue(script.contains("zygote-not-running"))
        // The acknowledgement follows those checks and precedes the restart, so the app hears about
        // the action while there is still a framework to hear it in.
        val accepted = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(accepted > 0)
        assertTrue(accepted < script.lastIndexOf("ctl.restart zygote"))
    }

    @Test
    fun `the restart waits for the modules that inject into Zygote`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT)

        // Mounted is not the same as up: creating a Zygote before these services run brings the
        // framework back without them, which is the opposite of what the restart is for.
        assertTrue(script.contains("zygisksu:zn-daemon"))
        assertTrue(script.contains("zygisk_lsposed:lspd"))
        // And it names which module was missing, because that is the part the app cannot work out for
        // itself: the sentence around it lives in the app, the ids live here.
        assertTrue(
            script.contains(
                "[ -z \"\$rmg_missing_services\" ] || " +
                    "reject_handoff \"module-services-not-ready:\${rmg_missing_services# }\"",
            ),
        )
    }

    @Test
    fun `the wait for module services is bounded and its check is exact`() {
        val snippet = moduleServiceWaitSnippet(timeoutSeconds = 7)

        // Bounded, so the child always answers inside the window the app waits in.
        assertTrue(snippet.contains("[ \"\$rmg_service_waited\" -lt 7 ]"))
        // Whole-name match: `lspd` also starts longer names, and a partial match would call a
        // service running when it is not.
        assertTrue(snippet.contains("grep -qx"))
        // A module that is absent or disabled is skipped rather than waited on.
        assertTrue(snippet.contains("[ -e \"\$rmg_module_dir/disable\" ] && continue"))
        assertTrue(snippet.contains("[ -e \"\$rmg_module_dir/remove\" ] && continue"))
    }

    @Test
    fun `the waited-for services come from one table`() {
        val snippet = moduleServiceWaitSnippet(
            services = listOf(ModuleService(moduleId = "some_module", processName = "some-daemon")),
        )

        assertTrue(snippet.contains("some_module:some-daemon"))
        assertFalse(snippet.contains("zygisksu:zn-daemon"))
    }

    // --- the soft reboot ---------------------------------------------------------------------------

    @Test
    fun `the soft reboot is handed to KernelSU and consumes the installed daemon`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("\"\$KSUD\" soft-reboot"))
        assertTrue(script.contains("KSUD=/data/adb/ksud"))
        assertTrue(script.contains("[ -x \"\$KSUD\" ] || reject_handoff 'installed-ksud-missing'"))
        // It must not stage a daemon of its own or replay the load: the verified load owns both.
        assertFalse(script.contains("late-load"))
        assertFalse(script.contains("/data/local/tmp/ksud"))
    }

    @Test
    fun `only one soft reboot owns a kernel boot`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("another-soft-reboot-owns-this-boot"))
        assertTrue(script.contains("LOCK='/data/local/tmp/.rmgnext-soft-reboot-owner'"))
    }

    @Test
    fun `the soft reboot waits for the boot to finish and checks it has not changed`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("getprop sys.boot_completed"))
        assertTrue(script.contains("boot-not-completed"))
        assertTrue(script.contains("boot-changed"))
    }

    @Test
    fun `the reboot is not reported as scheduled before the daemon accepted it`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        val call = script.indexOf("\"\$KSUD\" soft-reboot")
        val publish = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(call > 0)
        assertTrue(publish > call)
        assertTrue(script.contains("ksud-soft-reboot-failed-rc-"))
    }

    // --- the reboot ---------------------------------------------------------------------------------

    @Test
    fun `a soft reboot always answers inside the window the app waits in`() {
        val windowSeconds = windowSecondsFor(
            script = RootRecovery.softRebootScript(BOOT, ACCEPTED),
            attempts = RootRecovery.softRebootAcceptPollAttempts,
        )

        // The child's own worst case has to fit in the caller's, or the app reports a failure while
        // the child is still on its way to doing what was asked. The child counts iterations and the
        // app counts seconds, so its own two loops are converted here rather than compared as counts -
        // which is the comparison that let the restart's window pass this test while being half the
        // length of the wait behind it.
        assertTrue(
            "window ${windowSeconds}s against the child's " +
                "${RootRecovery.softRebootChildDeadlineSeconds}s",
            windowSeconds > RootRecovery.softRebootChildDeadlineSeconds,
        )
    }

    @Test
    fun `the restart's window outlasts the wait its child does before it can answer`() {
        val windowSeconds = windowSecondsFor(
            script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT),
            attempts = RootRecovery.restartZygoteAcceptPollAttempts,
        )

        assertTrue(
            "window ${windowSeconds}s against the child's " +
                "${RootRecovery.restartZygoteChildDeadlineSeconds}s",
            windowSeconds > RootRecovery.restartZygoteChildDeadlineSeconds,
        )
        // The window this action shipped with was exactly that bug: ten seconds of polling against a
        // child that cannot answer before twenty, so its refusal arrived after the app stopped
        // listening and was reported as silence instead.
        assertTrue(
            RootRecovery.ACCEPT_POLL_ATTEMPTS * RootRecovery.ACCEPT_POLL_INTERVAL_SECONDS <
                RootRecovery.restartZygoteChildDeadlineSeconds,
        )
    }

    @Test
    fun `no action acts after the app has given up on it`() {
        val scripts = listOf(
            RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT),
            RootRecovery.softRebootScript(BOOT, ACCEPTED),
            RootRecovery.rebootScript(BOOT, ACCEPTED),
        )

        scripts.forEach { script ->
            // Being written down is not being heard: the app removes the acknowledgement as it reads
            // it, so one still on disk means nobody read it and the action must not happen.
            assertTrue(script.contains("rmg_handoff_consumed()"))
            val published = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
            assertTrue(published > 0)
            assertTrue(script.indexOf("rmg_handoff_consumed ||", published) > published)
        }
    }

    /** The window an action is launched with, counted the way the launcher counts its own polls. */
    private fun windowSecondsFor(script: String, attempts: Int): Double = numberOf(
        RootRecovery.detachedLaunchCommand(
            script = script,
            scriptPath = "/data/local/tmp/x.sh",
            logPath = "/data/local/tmp/x.log",
            acceptedPath = ACCEPTED,
            acceptPollAttempts = attempts,
        ),
        "i\" -lt (\\d+)",
    ) * RootRecovery.ACCEPT_POLL_INTERVAL_SECONDS

    @Test
    fun `a daemon that has not returned is stopped, never left to fire later`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("\"\$KSUD\" soft-reboot >>\"\$KSUD_OUT\" 2>&1 &"))
        assertTrue(script.contains("kill -0 \"\$KSUD_PID\""))
        assertTrue(script.contains("kill \"\$KSUD_PID\""))
        assertTrue(script.contains("reject_handoff \"ksud-soft-reboot-timed-out"))
    }

    @Test
    fun `a failure quotes the daemon instead of only its exit code`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        // The daemon's own output is kept, and its last line is what a refusal carries.
        assertTrue(script.contains(">>\"\$KSUD_OUT\" 2>&1"))
        assertTrue(script.contains("ksud_words()"))
        assertTrue(script.contains("reject_handoff \"ksud-soft-reboot-failed-rc-\$RC \$(ksud_words)\""))
    }

    @Test
    fun `a lock left behind by a keeper that died does not lock the boot out`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        // The owner is recorded, and only a lock whose owner is still alive is an owner.
        assertTrue(script.contains("printf '%s\\n' \"\$\$\" > \"\$LOCK/pid\""))
        assertTrue(script.contains("kill -0 \"\$LOCK_PID\" 2>/dev/null; then"))
        assertTrue(script.contains("reject_handoff 'another-soft-reboot-owns-this-boot'"))
        assertTrue(script.contains("taking over a lock left by a keeper that is no longer running"))
    }

    /**
     * The last value [pattern] captures.
     *
     * Last, not first, for a launcher command: the script it embeds has waiting loops of its own, and
     * the one the launcher counts its polls with comes after all of them.
     */
    private fun numberOf(script: String, pattern: String): Int =
        Regex(pattern).findAll(script).lastOrNull()?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("not found in the script: $pattern")

    @Test
    fun `the reboot flushes what the app persisted before it goes`() {
        val script = RootRecovery.rebootScript(BOOT, ACCEPTED)

        val accepted = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(accepted > 0)
        assertTrue(script.indexOf("sync") < accepted)
        assertTrue(script.contains("/system/bin/reboot"))
        assertTrue(script.contains("boot-changed"))
    }

    @Test
    fun `the root form of the reboot still checks for root`() {
        assertTrue(RootRecovery.rebootScript(BOOT, ACCEPTED).contains("'not-root'"))
    }

    @Test
    fun `the shell form of the reboot asks with the shell user's own permission`() {
        val script = RootRecovery.rebootScript(BOOT, ACCEPTED, requiresRoot = false)

        // A plain Shizuku shell cannot run `/system/bin/reboot`, but the `shell` user holds the reboot
        // permission - which is how `adb reboot` works - so the same action is asked for through `svc`.
        assertFalse(script.contains("'not-root'"))
        assertTrue(script.contains("/system/bin/svc power reboot"))
        // The two halves that make any reboot script trustworthy are unchanged: it is refused for the
        // wrong boot, and it does not fire if nobody read the acknowledgement.
        assertTrue(script.contains("boot-changed"))
        val published = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(published > 0)
        assertTrue(script.indexOf("rmg_handoff_consumed ||", published) > published)
    }

    // --- the module reload -------------------------------------------------------------------------

    @Test
    fun `the reload re-applies the module lifecycle through the installed daemon`() {
        val script = RootRecovery.reloadModulesScript(BOOT, ACCEPTED)

        assertTrue(script.contains("\"\$KSUD\" late-load"))
        assertTrue(script.contains("KSUD=/data/adb/ksud"))
        assertTrue(script.contains("[ -x \"\$KSUD\" ] || reject_handoff 'installed-ksud-missing'"))
        // Nothing is loaded into the kernel from here and no second daemon is introduced: the module
        // is already in the kernel, and the daemon is the one the verified load installed. Loading a
        // module and staging a daemon are the soft reboot's business, not this action's.
        assertFalse(script.contains("insmod"))
        assertFalse(script.contains("allow_shell"))
        assertFalse(script.contains("/data/local/tmp/ksud"))
    }

    @Test
    fun `the reload stages the installed daemon byte for byte before it asks for anything`() {
        val script = RootRecovery.reloadModulesScript(BOOT, ACCEPTED)

        val staged = script.indexOf("/system/bin/cp \"\$KSUD\" \"\$STAGE\"")
        val run = script.indexOf("\"\$KSUD\" late-load")
        assertTrue(staged > 0 && run > staged)
        // The daemon consumes the stage file, so a reload without one fails inside the daemon for a
        // reason that has nothing to do with the modules.
        assertTrue(script.contains("STAGE='/data/local/tmp/.ksud-stage'"))
        // A copy that does not match the installed daemon is refused rather than run.
        assertTrue(script.contains("ksud-stage-hash-mismatch"))
    }

    @Test
    fun `the reload refuses a daemon that cannot do it`() {
        val script = RootRecovery.reloadModulesScript(BOOT, ACCEPTED)

        assertTrue(script.contains("grep -q late-load || reject_handoff 'daemon-has-no-late-load'"))
    }

    @Test
    fun `the reload reports a short mount count only when it did not improve it`() {
        val script = RootRecovery.reloadModulesScript(BOOT, ACCEPTED)

        // Read before and after: a count that was already short is the phone's own limit and not this
        // action's failure, while a count that fell is something this action did.
        assertTrue(script.contains("rmg_got_before=\$rmg_got"))
        assertTrue(script.contains("[ \"\$rmg_got\" -le \"\$rmg_got_before\" ]"))
        assertTrue(script.contains("modules-still-not-mounted:want=\${rmg_want} got=\${rmg_got}"))
        // And the reading is the shared one, so "what should be mounted" is one rule.
        assertTrue(script.contains("RMG_MODULE_MOUNTS").not())
        assertTrue(script.contains("rmg_want=0"))
    }

    @Test
    fun `a short mount count is explained with the numbers it was decided on`() {
        val outcome = RootRecovery.parseHandoff(
            "error:modules-still-not-mounted:want=2 got=1 before=1",
        )

        assertFalse(outcome.accepted)
        assertTrue(outcome.detail.startsWith("The modules are still not mounted"))
        assertTrue(outcome.detail.contains("want=2 got=1 before=1"))
    }

    @Test
    fun `only one reload owns a kernel boot`() {
        val script = RootRecovery.reloadModulesScript(BOOT, ACCEPTED)

        assertTrue(script.contains("another-reload-owns-this-boot"))
        assertTrue(script.contains("LOCK='/data/local/tmp/.rmgnext-reload-modules-owner'"))
        // Its own lock: a reload and a soft reboot are different actions and must not exclude each
        // other by sharing one owner file.
        assertFalse(script.contains(".rmgnext-soft-reboot-owner"))
    }

    @Test
    fun `the reload window outlasts the child's own watch`() {
        // The rule the restart's window was fixed for, applied to the action with the longest wait:
        // the window is longer than the child's worst case, and an iteration of the app's poll costs
        // at least ACCEPT_POLL_INTERVAL_SECONDS.
        val childDeadline = RootRecovery.reloadModulesChildDeadlineSeconds

        assertTrue(childDeadline >= RootRecovery.RELOAD_DAEMON_WATCH_ITERATIONS.toDouble())
        assertTrue(
            RootRecovery.reloadModulesAcceptPollAttempts.toDouble() *
                RootRecovery.ACCEPT_POLL_INTERVAL_SECONDS > childDeadline,
        )
    }

    @Test
    fun `the actions are listed cheapest first`() {
        // The order is what the settings screen shows, and it is the order worth trying them in: a
        // reload closes nothing, a framework restart closes every app, and the reboot ends the session.
        assertEquals(
            listOf(
                RecoveryTool.ReloadModules,
                RecoveryTool.RestartZygote,
                RecoveryTool.SoftReboot,
                RecoveryTool.RebootAndUnroot,
            ),
            RecoveryTool.entries.toList(),
        )
    }

    @Test
    fun `every action is scoped to the kernel boot it was asked for`() {
        val scripts = listOf(
            RootRecovery.reloadModulesScript(BOOT, ACCEPTED),
            RootRecovery.restartZygoteScript(BOOT, ACCEPTED, REPORT),
            RootRecovery.softRebootScript(BOOT, ACCEPTED),
            RootRecovery.rebootScript(BOOT, ACCEPTED),
        )

        scripts.forEach { script ->
            assertTrue(script.contains("EXPECTED_BOOT='$BOOT'"))
            assertTrue(script.contains("current_boot()"))
            assertTrue(script.contains("cat /proc/sys/kernel/random/boot_id"))
        }
    }
}
