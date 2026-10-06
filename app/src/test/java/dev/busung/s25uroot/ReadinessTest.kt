package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping behind the Overview status line.
 *
 * The third state is the one worth pinning. A status line wants a single word, and both of the wrong
 * ways to give it one have already cost this app a bug: calling "could not look" a no is what told a
 * rooted phone it was unrooted, and calling a refusal to answer a yes is how a start button came to
 * offer what was already running. So the two readings are kept as they are and the ambiguity is
 * reported as itself.
 */
class ReadinessTest {

    @Test
    fun `a reading that answers yes is loaded`() {
        assertEquals(KernelSuStatus.Active, kernelSuStatus(active = true, moduleLoaded = null))
    }

    @Test
    fun `the module list is evidence even when no shell of ours can run`() {
        // The case this hardware produces: KernelSU is loaded, nothing has granted this app a root
        // shell, so the only source that can speak is the kernel's own list.
        assertEquals(KernelSuStatus.Active, kernelSuStatus(active = false, moduleLoaded = true))
    }

    @Test
    fun `a list that was read and had no kernelsu is a no`() {
        assertEquals(KernelSuStatus.NotLoaded, kernelSuStatus(active = false, moduleLoaded = false))
    }

    @Test
    fun `nothing able to answer is neither yes nor no`() {
        assertEquals(
            KernelSuStatus.Unreadable,
            kernelSuStatus(active = false, moduleLoaded = null),
        )
    }

    @Test
    fun `a live root shell outranks a list that says otherwise`() {
        // Two sources disagree, which means one of them is being filtered rather than that the device
        // is in two states. A shell that ran `uid=0` is the stronger evidence, so the precedence is
        // stated here rather than left to the order of the branches.
        assertEquals(KernelSuStatus.Active, kernelSuStatus(active = true, moduleLoaded = false))
    }

    // --- the manager apps ---------------------------------------------------------------------------

    @Test
    fun `each manager app is reported under its own flavour`() {
        val presence = ManagerPresence.of(
            listOf(
                InstalledManager("me.weishu.kernelsu", "KernelSU", "3.3.0", KernelSuFlavor.KernelSu, false),
                InstalledManager(
                    "com.rifsxd.ksunext",
                    "KernelSU-Next",
                    "3.3.0",
                    KernelSuFlavor.KernelSuNext,
                    false,
                ),
            ),
        )

        assertTrue(presence.installed(KernelSuFlavor.KernelSu))
        assertTrue(presence.installed(KernelSuFlavor.KernelSuNext))
    }

    @Test
    fun `every project the app can install is reported under its own flavour`() {
        // Driven from the flavour list rather than from three named fields, which is the point: the
        // card draws one row per flavour the app knows, so a project added there has to be answerable
        // here without anyone remembering to add it in a second place.
        val presence = ManagerPresence.of(
            KernelSuFlavor.entries.map { flavor ->
                InstalledManager(
                    packageName = "com.example.${flavor.id}",
                    label = flavor.label,
                    versionName = "1.0.0",
                    flavor = flavor,
                    spoofed = false,
                )
            },
        )

        for (flavor in KernelSuFlavor.entries) {
            assertTrue(flavor.label, presence.installed(flavor))
        }
    }

    @Test
    fun `the third project's manager is not claimed by either of the other two`() {
        val presence = ManagerPresence.of(
            listOf(
                InstalledManager(
                    "com.resukisu.resukisu",
                    "ReSukiSU",
                    "4.2.0-rc2",
                    KernelSuFlavor.ReSukiSU,
                    false,
                ),
            ),
        )

        assertTrue(presence.installed(KernelSuFlavor.ReSukiSU))
        assertFalse(presence.installed(KernelSuFlavor.KernelSu))
        assertFalse(presence.installed(KernelSuFlavor.KernelSuNext))
    }

    @Test
    fun `a manager on the phone alone does not vouch for the other one`() {
        // These are separate apps and only one of them can be in the kernel, so having one says
        // nothing about the other - and the phone with a manager installed and nothing loaded is the
        // state a fresh install leaves, which is the one worth seeing before a run rather than after.
        val presence = ManagerPresence.of(
            listOf(
                InstalledManager(
                    "me.weishu.kernelsu",
                    "KernelSU",
                    "3.3.0",
                    KernelSuFlavor.KernelSu,
                    false,
                ),
            ),
        )

        assertTrue(presence.installed(KernelSuFlavor.KernelSu))
        assertFalse(presence.installed(KernelSuFlavor.KernelSuNext))
    }

    @Test
    fun `a manager whose package and label say neither flavour is filed under neither`() {
        // KernelSU-Next's spoofed manager build rewrites its package to three random words, so an
        // unnameable one is a real state rather than a parse failure - and attributing it to a project
        // would put it in the wrong row.
        val presence = ManagerPresence.of(
            listOf(InstalledManager("com.three.random.words", "Settings", null, null, true)),
        )

        assertFalse(presence.installed(KernelSuFlavor.KernelSu))
        assertFalse(presence.installed(KernelSuFlavor.KernelSuNext))
    }

    @Test
    fun `nothing installed reads as neither installed`() {
        assertFalse(ManagerPresence.of(emptyList()).installed(KernelSuFlavor.KernelSu))
        assertFalse(ManagerPresence().installed(KernelSuFlavor.KernelSuNext))
    }
}
