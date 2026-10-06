package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of the two accounts a finished late-load is judged by.
 *
 * The case this exists for is the second one: KernelSU's driver fd is opened through a `reboot`
 * magic and then checked for a version and two flag bits, so a module that loaded while that route
 * is refused exits non-zero with the kernel holding it. Judging the run on the exit code alone
 * recorded that as a failed load, which left no receipt for the boot and no manager to hand over.
 */
class LoadVerdictTest {

    @Test
    fun `a payload that passed and a reading that agrees is confirmed`() {
        assertEquals(
            LoadVerdict.Confirmed,
            loadVerdict(0, readings(moduleLoaded = true)),
        )
    }

    @Test
    fun `a payload that failed its own probe does not outrank a kernel listing the module`() {
        // The codes the payload's probe returns: 13 is no driver fd, 14 is a driver that answered
        // but not with a live late-loaded LKM.
        for (code in listOf(13, 14)) {
            assertEquals(
                "rc=$code over a module list",
                LoadVerdict.ConfirmedDespitePayload,
                loadVerdict(code, readings(moduleLoaded = true)),
            )
        }
    }

    @Test
    fun `a payload that failed is outvoted by any of the four readings, not only the module list`() {
        for (readings in listOf(
            readings(nativeProbe = true),
            readings(shizukuElevated = true),
            // The helper's own report, from the route the exit code does not have to take.
            readings(helperOutput = "KernelSU control verified version=33294 flags=0x7 uapi=4 features=0x3"),
        )) {
            assertEquals(
                readings.summary(),
                LoadVerdict.ConfirmedDespitePayload,
                loadVerdict(14, readings),
            )
        }
    }

    @Test
    fun `a payload that refused with nothing to contradict it carries its own reason`() {
        // Even a kernel that was looked at and has no module: the payload's line is the more specific
        // account of why its probe gave up, so the refusal is built from it.
        assertEquals(
            LoadVerdict.RefusedByPayload,
            loadVerdict(13, readings(moduleLoaded = false)),
        )
        assertEquals(
            LoadVerdict.RefusedByPayload,
            loadVerdict(13, readings()),
        )
    }

    @Test
    fun `a payload that passed over a kernel with no module is a load that did not land`() {
        assertEquals(
            LoadVerdict.NotLoaded,
            loadVerdict(0, readings(moduleLoaded = false)),
        )
    }

    @Test
    fun `a payload that passed with nothing able to look says exactly that`() {
        // No shell, so no module list; no root granted, so no native probe and no su. The run is
        // neither a success nor a failure, and the message sends the reader to the manager.
        assertEquals(
            LoadVerdict.Unconfirmed,
            loadVerdict(0, readings()),
        )
    }

    @Test
    fun `the whole table is reachable, so no verdict is dead code`() {
        val reachable = buildSet {
            for (code in listOf(0, 13, 14)) {
                for (readings in listOf(
                    readings(),
                    readings(moduleLoaded = false),
                    readings(moduleLoaded = true),
                    readings(nativeProbe = true),
                )) {
                    add(loadVerdict(code, readings))
                }
            }
        }
        assertEquals(LoadVerdict.entries.toSet(), reachable)
    }

    private fun readings(
        nativeProbe: Boolean = false,
        shizukuElevated: Boolean = false,
        helperOutput: String = "",
        moduleLoaded: Boolean? = null,
    ) = ControlReadings(
        nativeProbe = nativeProbe,
        appSuFailure = SuProbe.Failure.NONE,
        shizukuElevated = shizukuElevated,
        helperOutput = helperOutput,
        moduleLoaded = moduleLoaded,
    )
}
