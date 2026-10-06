package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ceilings a run is held to, and the one rule that can override a choice.
 *
 * Two things matter here beyond the arithmetic. The offers are menus rather than free-form numbers, so a
 * stored value has to resolve to one of them whatever is on disk. And a fresh-session profile hands its
 * pacing to the payload, so the whole-run setting can raise that ceiling but never cut a payload-native
 * attempt short — the case a user would most easily get wrong, and the one the app has to protect them
 * from.
 */
class RunLimitsTest {

    @Test
    fun `the defaults are the shipped ones`() {
        val ceilings = RunLimits.defaultCeilings(freshSession = false)

        assertEquals(900_000L, ceilings.totalMillis)
        assertEquals(120_000L, ceilings.helperMillis)
    }

    /**
     * The silence limit cannot fire before the whole-run deadline, which is the point.
     *
     * This is the regression the upstream baseline found the hard way: a ninety-second watchdog over a
     * payload whose root helper is deliberately quiet killed healthy runs, and the value it settled on is
     * the run's own ceiling. A shorter default here would be that bug returning, so it is pinned rather
     * than left to whoever edits the constant next.
     */
    @Test
    fun `the shipped silence limit sits at the whole-run ceiling`() {
        val ceilings = RunLimits.defaultCeilings(freshSession = false)

        assertEquals(RunLimits.DEFAULT_TOTAL_SECONDS * 1_000L, ceilings.stallMillis)
        assertTrue(ceilings.stallMillis >= ceilings.totalMillis)
    }

    /** Nothing is offered below the value that was measured killing healthy runs. */
    @Test
    fun `no offer is short enough to kill a quiet but healthy payload`() {
        assertTrue(RunLimits.allowedStallSeconds.min() >= 120)
    }

    @Test
    fun `a chosen value is the one the run gets`() {
        val ceilings = RunLimits.resolve(
            stallSeconds = 180,
            totalSeconds = 1800,
            helperSeconds = 300,
            freshSession = false,
        )

        assertEquals(1_800_000L, ceilings.totalMillis)
        assertEquals(180_000L, ceilings.stallMillis)
        assertEquals(300_000L, ceilings.helperMillis)
    }

    /** The setting raises a fresh session's ceiling, and never lowers it below the app's own hour. */
    @Test
    fun `a fresh session is never cut below the app's floor`() {
        assertEquals(
            3_600_000L,
            RunLimits.resolve(90, 300, 120, freshSession = true).totalMillis,
        )
        assertEquals(
            7_200_000L,
            RunLimits.resolve(90, 7200, 120, freshSession = true).totalMillis,
        )
    }

    /** Only the whole-run ceiling has that rule: a stall limit is simply not applied to a fresh session. */
    @Test
    fun `the other two ceilings are the chosen ones whatever the session is`() {
        val fresh = RunLimits.resolve(180, 7200, 300, freshSession = true)

        assertEquals(180_000L, fresh.stallMillis)
        assertEquals(300_000L, fresh.helperMillis)
    }

    @Test
    fun `a stored value that is not offered resolves to the nearest one`() {
        assertEquals(900, RunLimits.normalizeTotalSeconds(1000))
        assertEquals(180, RunLimits.normalizeStallSeconds(200))
        assertEquals(120, RunLimits.normalizeHelperSeconds(150))
    }

    /** A ninety-second limit stored by an older build does not survive as one. */
    @Test
    fun `a stored silence limit below the offers resolves upward to the shortest offer`() {
        assertEquals(120, RunLimits.normalizeStallSeconds(90))
        assertEquals(120, RunLimits.normalizeStallSeconds(0))
    }

    @Test
    fun `every offer is in its own menu and the menus are ordered`() {
        RunLimit.entries.forEach { limit ->
            val options = RunLimits.options(limit)

            assertTrue("$limit offers nothing", options.isNotEmpty())
            assertEquals("$limit is out of order", options.sorted(), options)
        }
        assertTrue(RunLimits.allowedTotalSeconds.contains(RunLimits.DEFAULT_TOTAL_SECONDS))
        assertTrue(RunLimits.allowedStallSeconds.contains(RunLimits.DEFAULT_STALL_SECONDS))
        assertTrue(RunLimits.allowedHelperSeconds.contains(RunLimits.DEFAULT_HELPER_SECONDS))
    }

    /** Normalizing an offer returns the offer, which is what makes the menu the whole value space. */
    @Test
    fun `an offered value survives normalization`() {
        RunLimit.entries.forEach { limit ->
            RunLimits.options(limit).forEach { seconds ->
                assertEquals(seconds, RunLimits.normalize(limit, seconds))
            }
        }
    }

    /**
     * A reset is the defaults, per ceiling and as a set.
     *
     * The dialog resets by putting each ceiling back through the ordinary change path, so what this pins
     * is that those three writes land exactly where an untouched install starts.
     */
    @Test
    fun `resetting every ceiling puts the run back to the shipped ones`() {
        val defaults = RunLimits.defaults()

        RunLimit.entries.forEach { limit ->
            assertEquals(
                "$limit's default is not what a reset writes",
                RunLimits.defaultSeconds(limit),
                when (limit) {
                    RunLimit.Total -> defaults.totalSeconds
                    RunLimit.Stall -> defaults.stallSeconds
                    RunLimit.Helper -> defaults.helperSeconds
                },
            )
        }
        assertEquals(
            RunLimits.defaultCeilings(freshSession = false),
            RunLimits.resolve(defaults, freshSession = false),
        )
        assertEquals(
            RunLimits.defaultCeilings(freshSession = true),
            RunLimits.resolve(defaults, freshSession = true),
        )
    }

    /** What the settings row and the plan show, so a value reads the same in both places. */
    @Test
    fun `labels say what they mean`() {
        assertEquals("30 s", RunLimits.label(30))
        assertEquals("5 min", RunLimits.label(300))
        assertEquals("1 min 30 s", RunLimits.label(90))
        assertEquals("2 h", RunLimits.durationLabel(7_200_000L))
    }

    /** The settings show the effective value, so a fresh session's floor is visible before a run starts. */
    @Test
    fun `the effective value is the resolved one`() {
        assertEquals(3600, RunLimits.effectiveSeconds(RunLimit.Total, 300, freshSession = true))
        assertEquals(300, RunLimits.effectiveSeconds(RunLimit.Total, 300, freshSession = false))
        assertEquals(180, RunLimits.effectiveSeconds(RunLimit.Stall, 180, freshSession = true))
    }
}
