package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a refusal says about the three readings behind it.
 *
 * They have nothing in common - the native paths are hidden by policy on this hardware, `su` answers
 * only for the manager KernelSU knows, and the helper prints a report only when the load reached the
 * point of having one - so a single sentence covers a healthy load the app could not see and a load
 * that never happened. The summary is the part that tells them apart.
 */
class ControlReadingsTest {

    @Test
    fun `a silent helper is reported as silence, not as a failed report`() {
        val summary = readings(helperOutput = "").summary()
        assertTrue(summary, summary.contains("helper printed nothing"))
    }

    @Test
    fun `a module list is read by the module's name, however the line is laid out`() {
        // A load made by the payload's own loader: no size, no address, and the taint flag a stock
        // module would not carry. None of that makes it not loaded.
        assertTrue(parseModuleList("kernelsu 172032 0 - Live 0x0000000000000000 (O)\n"))
        assertTrue(parseModuleList("kernelsu\t172032\t0\t-\tLive\t0x0000000000000000\t(O)"))
        assertFalse(parseModuleList("kernelsu_next 172032 0 - Live 0x0 (O)\n"))
        assertFalse(parseModuleList("something_kernelsu 12 0 - Live 0x0\n"))
        assertFalse(parseModuleList(""))
    }

    @Test
    fun `a helper that spoke without a report says how much it said`() {
        val summary = readings(helperOutput = "line one\nline two\n").summary()
        assertTrue(summary, summary.contains("printed 2 line(s) without a control report"))
    }

    @Test
    fun `a live report is named as one`() {
        val report = "KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3"
        val summary = readings(helperOutput = report).summary()
        assertTrue(summary, summary.contains("helper reported a live control channel"))
    }

    @Test
    fun `a native probe that could not see KernelSU gives the app-side su reason`() {
        val summary = readings(
            nativeProbe = false,
            appSuFailure = SuProbe.Failure.ABSENT,
        ).summary()
        assertTrue(summary, summary.contains("native probe no (the app's own su: absent)"))
    }

    @Test
    fun `a reading that succeeded does not carry a failure with it`() {
        assertEquals(
            "native probe yes; su through Shizuku yes; helper printed nothing; " +
                "module list not readable",
            readings(nativeProbe = true, shizukuElevated = true, helperOutput = "").summary(),
        )
    }

    @Test
    fun `a load the app cannot otherwise see is still a healthy load`() {
        // The device this came from: KernelSU loaded by the payload's root helper, nothing granted to
        // the app or the shell yet, and a helper whose stdout the load itself moved out from under it.
        val readings = readings(moduleLoaded = true)
        assertEquals(setOf(ControlProof.ModuleLoaded), readings.proofs)
        assertTrue(readings.summary(), readings.summary().endsWith("module list reports kernelsu"))
    }

    @Test
    fun `an unreadable module list is not an empty one`() {
        assertTrue(readings(moduleLoaded = null).summary().endsWith("module list not readable"))
        assertTrue(readings(moduleLoaded = false).summary().endsWith("module list has no kernelsu"))
    }

    private fun readings(
        nativeProbe: Boolean = false,
        appSuFailure: SuProbe.Failure = SuProbe.Failure.NONE,
        shizukuElevated: Boolean = false,
        helperOutput: String = "",
        moduleLoaded: Boolean? = null,
    ) = ControlReadings(
        nativeProbe = nativeProbe,
        appSuFailure = appSuFailure,
        shizukuElevated = shizukuElevated,
        helperOutput = helperOutput,
        moduleLoaded = moduleLoaded,
    )
}
