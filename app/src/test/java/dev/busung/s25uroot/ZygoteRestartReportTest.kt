package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BOOT = "0f2a4c6e-1b2d-4f6a-8c0e-2d4f6a8c0e2d"
private const val REPORT = "/data/user/0/dev.rushiranpise.rmgnext/files/framework-restart-report"

class ZygoteRestartReportTest {

    /** The shape the child actually prints, prefix and all, with the app's own noise around it. */
    private fun record(
        verdict: String = "restarted",
        zygote: String = "1234",
        systemServer: String = "2345",
        took: String = "12",
        mapped: String = "yes",
        missing: String = "-",
        boot: String = BOOT,
    ) = "[keeper] 1755000000 requesting the restart\n" +
        "${ZygoteRestartReport.RECORD_PREFIX} boot=$boot verdict=$verdict zygote=$zygote " +
        "system_server=$systemServer took=$took mapped=$mapped missing=$missing\n"

    // --- what the child asks, and when -------------------------------------------------------------

    @Test
    fun `the restart asks what came back, and only after it asked for the restart`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        val accepted = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        val baseline = script.indexOf("rmg_zygote_before=")
        // The last occurrence: the secondary restart command literally starts with the primary's.
        val restart = script.lastIndexOf("setprop ctl.restart zygote")
        val verification = script.indexOf("rmg_verdict=unreadable")
        val written = script.indexOf("> \"\$REPORT\"")

        // What is being replaced has to be read while it is still there, and the framework that
        // replaces it can only be looked for once the request has been made.
        assertTrue("accepted", accepted > 0)
        assertTrue("baseline after the acknowledgement", baseline > accepted)
        assertTrue("restart after the baseline", restart > baseline)
        assertTrue("verification after the restart", verification > restart)
        assertTrue("record written last", written > verification)
    }

    @Test
    fun `the child compares the framework against the process that was there before`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        // A `ctl.restart` init ignores is the one outcome the acceptance cannot tell apart from a
        // restart that happened, and the pid is the only thing that can.
        assertTrue(script.contains("rmg_zygote_before=\$(pidof zygote zygote64 2>/dev/null)"))
        assertTrue(script.contains("rmg_ss_before=\$(pidof system_server 2>/dev/null)"))
        assertTrue(script.contains("[ \"\$rmg_zygote_after\" = \"\$rmg_zygote_before\" ]"))
        // Both namings of the primary Zygote, because a device that calls it zygote64 would otherwise
        // read as a device whose Zygote never restarted.
        assertTrue(script.contains("pidof zygote zygote64"))
    }

    @Test
    fun `a framework that did not come back is a different finding from one that never restarted`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        assertTrue(script.contains("rmg_verdict=not-restarted"))
        assertTrue(script.contains("rmg_verdict=framework-missing"))
        assertTrue(script.contains("rmg_verdict=boot-changed"))
        assertTrue(script.contains("rmg_verdict=unreadable"))
    }

    @Test
    fun `an unreadable process map is not recorded as an absent one`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        // Readable-but-empty is a finding; unreadable is nothing, and a check that could not run must
        // not be reported as the thing it was checking for.
        assertTrue(script.contains("[ -r \"/proc/\$rmg_ss_after/maps\" ]"))
        assertTrue(script.contains("rmg_mapped=unreadable"))
        assertTrue(script.contains("rmg_mapped=none"))
        // What is looked for is module code in the framework, not one module's library: which module
        // injects is the device's business, and a name pinned here would be wrong on the next one.
        assertTrue(script.contains("'/data/adb/modules(_update)?/[^ ]*/zygisk/'"))
        // And a framework whose process was gone while its maps were being read is unreadable too: an
        // absent process is not an absent module.
        assertTrue(
            script.substringAfter("rmg_map_waited=0").contains("rmg_mapped=unreadable"),
        )
    }

    @Test
    fun `the record is written where the app can read it without a shell`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        assertTrue(script.contains("REPORT='$REPORT'"))
        // The app's own files directory, not the shell-facing scratch space every other action uses:
        // the point of this record is that reading it needs no shell on the other side of a restart.
        assertFalse(script.contains("REPORT='/data/local/tmp"))
        // An earlier restart's record is removed before the request, so one that never comes back
        // cannot leave a stale account to be read as its own.
        assertTrue(script.indexOf("rm -f -- \"\$REPORT\"") < script.lastIndexOf("setprop ctl.restart"))
    }

    @Test
    fun `the record is echoed as well as written, so a refused write is not a lost one`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        val tail = script.substringAfter("> \"\$REPORT\" 2>/dev/null || true")
        assertTrue(tail.contains("printf '%s\\n' \"\$rmg_record\""))
    }

    // --- reading the record ------------------------------------------------------------------------

    @Test
    fun `a restart that came back is read back with the framework it came back with`() {
        val report = ZygoteRestartReport.parse(record())!!

        assertEquals(BOOT, report.bootToken)
        assertEquals(FrameworkReturn.Restarted, report.returned)
        assertEquals("1234", report.zygotePid)
        assertEquals("2345", report.systemServerPid)
        assertEquals(12, report.waitedSeconds)
        assertEquals(ModuleCodeInFramework.Mapped, report.moduleCode)
        assertTrue(report.missingServices.isEmpty())
        // The whole point of the action, and the one outcome that needs no decoration.
        assertFalse(report.needsAttention)
    }

    @Test
    fun `the findings the acceptance used to hide are all readings that need attention`() {
        // "Scheduled" was the same answer for a restart that did nothing, one that took the framework
        // down, and one that came back without the modules the restart was spent on.
        assertTrue(ZygoteRestartReport.parse(record(verdict = "not-restarted"))!!.needsAttention)
        assertTrue(
            ZygoteRestartReport.parse(record(verdict = "framework-missing"))!!.needsAttention,
        )
        assertTrue(ZygoteRestartReport.parse(record(mapped = "no"))!!.needsAttention)
        assertTrue(
            ZygoteRestartReport.parse(record(missing = "zygisk_lsposed"))!!.needsAttention,
        )
        // Nothing to inject is not a module that failed to inject, and a mapping that could not be
        // read is not an absent one.
        assertFalse(ZygoteRestartReport.parse(record(mapped = "none"))!!.needsAttention)
        assertFalse(ZygoteRestartReport.parse(record(mapped = "unreadable"))!!.needsAttention)
    }

    @Test
    fun `the module services the restart waits for are read back one by one`() {
        // Two modules missing has to arrive as two ids: the record is a space-separated list of fields,
        // so a space-separated list inside it reads as one id followed by a field nobody knows.
        val report = ZygoteRestartReport.parse(record(missing = "zygisksu,zygisk_lsposed"))!!

        assertEquals(listOf("zygisksu", "zygisk_lsposed"), report.missingServices)
    }

    @Test
    fun `the child writes the missing ids with a separator of their own`() {
        val script = RootRecovery.restartZygoteScript(BOOT, REPORT_PATH_FOR_ACCEPTED, REPORT)

        assertTrue(script.contains("tr ' ' ','"))
    }

    @Test
    fun `a record with nothing readable in it is not a verdict about the phone`() {
        assertNull(ZygoteRestartReport.parse("no record here"))
        assertNull(ZygoteRestartReport.parse(""))
        // Every verdict is about how long it waited, so a record without one says nothing.
        assertNull(ZygoteRestartReport.parse(record(took = "unknown")))
    }

    @Test
    fun `a verdict from a newer child is reported as unreadable, not as silence`() {
        // Silence is the one answer that reads as "no news", and this app already reports success and
        // failure in words; a verdict it cannot describe is a fact about the reading, not a reason to
        // say nothing.
        val report = ZygoteRestartReport.parse(record(verdict = "framework-slow"))

        assertNotNull(report)
        assertEquals(FrameworkReturn.Unreadable, report!!.returned)
        assertTrue(report.needsAttention)
    }

    @Test
    fun `an unknown mapping is unreadable rather than absent`() {
        assertEquals(
            ModuleCodeInFramework.Unreadable,
            ZygoteRestartReport.parse(record(mapped = "maybe"))!!.moduleCode,
        )
        assertEquals(
            ModuleCodeInFramework.Unreadable,
            ZygoteRestartReport.parse(record(mapped = "-"))!!.moduleCode,
        )
    }

    @Test
    fun `a missing process is a dash, not an empty claim`() {
        val report = ZygoteRestartReport.parse(
            record(verdict = "framework-missing", systemServer = "-", zygote = "1234"),
        )!!

        assertEquals("-", report.systemServerPid)
        assertEquals("1234", report.zygotePid)
    }

    // --- the handoff between the child and the app --------------------------------------------------

    @Test
    fun `a record is read away as it is read, so it cannot be reported twice`() {
        val file = File.createTempFile("framework-restart-report", ".txt")
        file.writeText(record())

        val report = ZygoteRestartReport.consume(file, BOOT)

        assertNotNull(report)
        assertFalse(file.exists())
    }

    @Test
    fun `a record from another boot is discarded, and not left to be read again`() {
        val file = File.createTempFile("framework-restart-report", ".txt")
        file.writeText(record(boot = "some-other-boot"))

        // It describes a framework this boot does not have: the restart that wrote it ended in a
        // reboot, or was never checked in the boot it names.
        assertNull(ZygoteRestartReport.consume(file, BOOT))
        assertFalse(file.exists())
    }

    @Test
    fun `a record is left alone when this boot cannot be named`() {
        val file = File.createTempFile("framework-restart-report", ".txt")
        file.writeText(record())

        // A device that will not answer for its own boot id must not cost the user the only account of
        // a restart it just did; a later launch that can read the boot id will report it.
        assertNull(ZygoteRestartReport.consume(file, bootToken = null))
        assertTrue(file.exists())
        assertNotNull(ZygoteRestartReport.consume(file, BOOT))
    }

    @Test
    fun `nothing to read is not a report`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "no-such-framework-restart-report")

        assertNull(ZygoteRestartReport.consume(missing, BOOT))
    }
}

/** The launcher's acknowledgement path, which this file's script checks use for their own reasons. */
private const val REPORT_PATH_FOR_ACCEPTED = "/data/local/tmp/.rmgnext-restart-zygote-accepted"
