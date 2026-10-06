package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the running KernelSU, and comparing a manager against it.
 *
 * The readings are taken from the two commands KernelSU's own source defines - `ksud --version`
 * (`defs::FULL_VERSION`, which is `"{VERSION_NAME} (uapi: {n})"`) and `ksud debug version`
 * (`"Kernel Version: {n}"`) - so what is pinned here is what those actually print, including the
 * protocol number sitting inside the same line as the version. A parser that took the wrong number
 * would compare a manager's `3.3.0` against a `3` and report a mismatch on every device.
 */
class KernelSuVersionTest {

    // --- the daemon's own version ---------------------------------------------------------------------

    @Test
    fun `the release is read out of ksud's version line`() {
        // clap prints the binary's name before the version string.
        assertEquals("3.3.0", parseKsudVersion("ksud 3.3.0 (uapi: 3)"))
        // And the bare FULL_VERSION when something prints it without the name.
        assertEquals("3.3.0", parseKsudVersion("3.3.0 (uapi: 3)"))
        assertEquals("3.3.0", parseKsudVersion("KernelSU-Next 3.3.0 (uapi: 4)"))
    }

    /** The `uapi` number is a protocol, not a version, and it is what must not be mistaken for one. */
    @Test
    fun `a line with only a protocol number names no version`() {
        assertNull(parseKsudVersion("ksud (uapi: 3)"))
        assertNull(parseKsudVersion(""))
        assertNull(parseKsudVersion("error: unexpected argument '-V' found"))
    }

    // --- the kernel-side module -----------------------------------------------------------------------

    @Test
    fun `the kernel version code is read from its own line`() {
        assertEquals(32601, parseKernelVersionCode("Kernel Version: 32601"))
        assertEquals(32601, parseKernelVersionCode("  Kernel Version:   32601  "))
    }

    /** Zero is how the call reports a module that is not there to answer. */
    @Test
    fun `a zero kernel version is not a reading`() {
        assertNull(parseKernelVersionCode("Kernel Version: 0"))
        assertNull(parseKernelVersionCode("no output"))
    }

    // --- one round trip, both answers -----------------------------------------------------------------

    @Test
    fun `both readings come out of the markers they were printed under`() {
        val reading = parseVersionReadings(
            """
            RMG_DAEMON=ksud 3.3.0 (uapi: 3)
            RMG_KERNEL=Kernel Version: 32601
            """.trimIndent(),
        )

        assertEquals("3.3.0", reading.daemon)
        assertEquals(32601, reading.kernelCode)
        assertEquals(true, reading.isRead)
    }

    /** A `ksud` that is not there prints nothing for its own line and leaves the other one intact. */
    @Test
    fun `one silent command does not swallow the other`() {
        val reading = parseVersionReadings("RMG_DAEMON=\nRMG_KERNEL=Kernel Version: 32601")

        assertNull(reading.daemon)
        assertEquals(32601, reading.kernelCode)
    }

    @Test
    fun `nothing answered reads as nothing, not as a version`() {
        val reading = parseVersionReadings("RMG_DAEMON=\nRMG_KERNEL=")

        assertEquals(KernelSuVersionReading(), reading)
        assertEquals(false, reading.isRead)
    }

    // --- manager against kernel -----------------------------------------------------------------------

    @Test
    fun `a manager and a kernel from the same release match`() {
        assertEquals(
            ManagerVersionState.Matching,
            managerVersionState("3.3.0", "3.3.0"),
        )
    }

    /**
     * A build's suffix is not a release.
     *
     * The daemon's `VERSION_NAME` comes from `git describe`, so it can carry a suffix the manager's own
     * `versionName` does not - and treating that as a mismatch would warn about a pair that is right.
     */
    @Test
    fun `a suffix and a leading v do not make a different release`() {
        assertEquals(ManagerVersionState.Matching, managerVersionState("3.3.0", "3.3.0-ksun"))
        assertEquals(ManagerVersionState.Matching, managerVersionState("v3.3.0", "3.3.0"))
    }

    @Test
    fun `a manager from another line is the case this exists for`() {
        assertEquals(
            ManagerVersionState.Differing,
            managerVersionState("3.2.5", "3.3.0"),
        )
        assertEquals(
            ManagerVersionState.Differing,
            managerVersionState("3.3.0", "3.4.1"),
        )
    }

    /** Nothing to compare, so nothing is claimed: neither a warning nor an all-clear. */
    @Test
    fun `a missing reading leaves the pair unknown`() {
        assertEquals(ManagerVersionState.Unknown, managerVersionState(null, "3.3.0"))
        assertEquals(ManagerVersionState.Unknown, managerVersionState("3.3.0", null))
        assertEquals(ManagerVersionState.Unknown, managerVersionState("", null))
        assertEquals(ManagerVersionState.Unknown, managerVersionState("unknown", "3.3.0"))
    }

    // --- the pair as a screen shows it -----------------------------------------------------------------

    /**
     * The two numbers printed are the two numbers that were compared.
     *
     * The case this exists for is the suffix: a daemon reports `3.3.0-ksun` and a manager reports
     * `3.3.0`, and showing both raw would put two different strings on screen under a line calling them
     * a match. A reader would believe the strings and distrust the line.
     */
    @Test
    fun `a suffix is shown as the release it is, not as itself`() {
        val pair = versionPairDisplay("3.3.0", "3.3.0-ksun")

        assertEquals("3.3.0", pair.manager)
        assertEquals("3.3.0", pair.kernel)
        assertEquals(ManagerVersionState.Matching, pair.state)
        assertEquals(false, pair.mismatched)
    }

    @Test
    fun `a leading v does not survive into what is shown`() {
        val pair = versionPairDisplay("v3.3.0", "3.3.0")

        assertEquals("3.3.0", pair.manager)
        assertEquals(false, pair.mismatched)
    }

    @Test
    fun `a real mismatch is the pair that carries the mark`() {
        val pair = versionPairDisplay("3.2.5", "3.3.0")

        assertEquals("3.2.5", pair.manager)
        assertEquals("3.3.0", pair.kernel)
        assertEquals(true, pair.mismatched)
    }

    /**
     * A reading with no release in it is shown as itself and claims nothing.
     *
     * `unknown` is what the package manager answers when it would not say, and hiding it would leave a
     * row that looks like a comparison nobody made. It is not a version, so it is not compared - which
     * is what keeps an unreadable manager from becoming a mismatch nobody has.
     */
    @Test
    fun `an unreadable reading is shown and not compared`() {
        val pair = versionPairDisplay("unknown", "3.3.0")

        assertEquals("unknown", pair.manager)
        assertEquals(ManagerVersionState.Unknown, pair.state)
        assertEquals(false, pair.mismatched)
    }

    /** Nothing installed and nothing read are different absences, and neither is a version. */
    @Test
    fun `an absent reading stays absent so the screen can name it`() {
        val pair = versionPairDisplay(null, null)

        assertNull(pair.manager)
        assertNull(pair.kernel)
        assertEquals(false, pair.mismatched)

        val blank = versionPairDisplay("   ", "")
        assertNull(blank.manager)
        assertNull(blank.kernel)
    }

    // --- the fix offered with the warning -------------------------------------------------------------

    /**
     * The button under a mismatch installs the version the *kernel* is running.
     *
     * Not the flavour's default, which is a decision this app made and may itself be the thing that is
     * wrong: the phone is the one that knows what the manager has to talk to.
     */
    @Test
    fun `a mismatch offers the running version to install`() {
        assertEquals(
            "3.3.0",
            managerMismatchTarget(ManagerVersionState.Differing, "3.3.0"),
        )
    }

    /** A button that installs something on a row with no problem to fix is the thing to avoid. */
    @Test
    fun `nothing is offered when there is no mismatch to act on`() {
        assertNull(managerMismatchTarget(ManagerVersionState.Matching, "3.3.0"))
        assertNull(managerMismatchTarget(ManagerVersionState.Unknown, "3.3.0"))
        assertNull(managerMismatchTarget(ManagerVersionState.Differing, null))
    }

    // --- the payload's KernelSU against this boot's -----------------------------------------------------

    /**
     * The comparison is numeric, which is the one thing about it worth pinning.
     *
     * KernelSU's releases are dotted numbers and `3.10.0` is a release that will exist; compared as
     * text it sorts *before* `3.9.0`, and the app would then tell a user whose payload is newer than
     * their boot that their boot is newer than the payload - the two states being opposite fixes.
     */
    @Test
    fun `a newer release is newer than a later one`() {
        assertEquals(PayloadKernelState.Ahead, payloadKernelReading("3.10.0", "3.9.0").state)
        assertEquals(PayloadKernelState.Behind, payloadKernelReading("3.9.0", "3.10.0").state)
        assertEquals(PayloadKernelState.Same, payloadKernelReading("3.10.0", "3.10.0").state)
    }

    /** A payload that declares the release this boot runs is the case with nothing to say. */
    @Test
    fun `a payload matching this boot is not flagged`() {
        val reading = payloadKernelReading("3.3.0", "3.3.0")
        assertEquals(PayloadKernelState.Same, reading.state)
        assertFalse(reading.flagged)
        assertEquals("3.3.0", reading.declared)
        assertEquals("3.3.0", reading.running)
    }

    /**
     * The payload being ahead and being behind are two sentences, not one warning.
     *
     * Ahead means the manager being offered will match as soon as a run loads it; behind means the app
     * is offering a release the phone has already moved past, which is the one the offer must not make
     * look unremarkable. Both are flagged; the state is what the screen words differently.
     */
    @Test
    fun `both directions are flagged, and told apart`() {
        val ahead = payloadKernelReading("3.4.0", "3.3.0")
        assertEquals(PayloadKernelState.Ahead, ahead.state)
        assertTrue(ahead.flagged)
        assertEquals("3.4.0", ahead.declared)
        assertEquals("3.3.0", ahead.running)

        val behind = payloadKernelReading("3.3.0", "3.4.0")
        assertEquals(PayloadKernelState.Behind, behind.state)
        assertTrue(behind.flagged)
    }

    /**
     * A reading that did not happen is not a warning about anything.
     *
     * The same rule the manager pair follows, and for the same reason: an entry that declares no
     * version, a phone whose daemon could not be asked, and a package manager answer of `unknown` are
     * all absences - and an absence compared as if it were a version is how the screen would warn about
     * a mismatch nobody has.
     */
    @Test
    fun `a payload or a boot that said nothing is not a disagreement`() {
        listOf(
            payloadKernelReading(null, "3.3.0"),
            payloadKernelReading("3.3.0", null),
            payloadKernelReading(null, null),
            payloadKernelReading("unknown", "3.3.0"),
            payloadKernelReading("3.3.0", ""),
        ).forEach { reading ->
            assertEquals(PayloadKernelState.Unknown, reading.state)
            assertFalse(reading.flagged)
        }
    }

    /** The forms a real daemon and a real tag carry must not read as two releases. */
    @Test
    fun `a suffix and a leading v are the same release`() {
        assertEquals(PayloadKernelState.Same, payloadKernelReading("3.3.0-ksun", "3.3.0").state)
        assertEquals(PayloadKernelState.Same, payloadKernelReading("v3.4.0", "3.4.0").state)
        assertEquals(PayloadKernelState.Same, payloadKernelReading("3.4", "3.4.0").state)
    }

    /**
     * The fix for a payload the phone has passed is the version the phone is running.
     *
     * Not the payload's - that is the number being complained about - and nothing at all in the two
     * states that have no such problem, so the row cannot offer a control that does nothing.
     */
    @Test
    fun `only a payload behind this boot names a version to keep`() {
        assertEquals("3.4.0", payloadBehindTarget(payloadKernelReading("3.3.0", "3.4.0")))
        assertNull(payloadBehindTarget(payloadKernelReading("3.4.0", "3.3.0")))
        assertNull(payloadBehindTarget(payloadKernelReading("3.3.0", "3.3.0")))
        assertNull(payloadBehindTarget(payloadKernelReading(null, "3.4.0")))
    }

    /** The ordering itself, where the two readings' own fields cannot show it. */
    @Test
    fun `releases compare component by component`() {
        assertEquals(1, compareReleases("3.10.0", "3.9.0"))
        assertEquals(-1, compareReleases("3.9.0", "3.10.0"))
        assertEquals(0, compareReleases("3.4", "3.4.0"))
        assertEquals(1, compareReleases("3.4.1", "3.4"))
    }
}
