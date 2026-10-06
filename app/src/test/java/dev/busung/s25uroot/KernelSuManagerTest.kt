package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding a manager whose package name is not the published one.
 *
 * KernelSU-Next's spoofed manager build rewrites `com.rifsxd.ksunext` to three random words, freshly
 * generated on every release, so the app can never hold that name. What it can hold is the label,
 * which the spoof does not touch, and this is the decision that turns the two into a flavour.
 *
 * The unknown case matters as much as the others: a package that carries a KernelSU daemon but says
 * nothing about which project it is belongs to neither row rather than being guessed into one.
 */
class KernelSuManagerTest {

    @Test
    fun `a published package name decides on its own`() {
        val ksu = identifyManager("me.weishu.kernelsu", "KernelSU")
        assertEquals(KernelSuFlavor.KernelSu, ksu.flavor)
        assertFalse(ksu.spoofed)

        val next = identifyManager("com.rifsxd.ksunext", "KernelSU-Next")
        assertEquals(KernelSuFlavor.KernelSuNext, next.flavor)
        assertFalse(next.spoofed)
    }

    @Test
    fun `a published package name is matched without case`() {
        assertFalse(identifyManager("  COM.RIFSXD.KSUNEXT  ", "whatever").spoofed)
        assertEquals(
            KernelSuFlavor.ReSukiSU,
            identifyManager("com.resukisu.resukisu", "whatever").flavor,
        )
    }

    @Test
    fun `the third project is read from its label too`() {
        // A repacked manager of this one keeps the same package, so the label is not the usual path -
        // but it is the only signal left if a build does change the package, and reading it as KernelSU
        // would put it in the wrong row.
        val byLabel = identifyManager("qwerty.asdfgh.zxcvbn", "ReSukiSU")
        assertEquals(KernelSuFlavor.ReSukiSU, byLabel.flavor)
        assertTrue(byLabel.spoofed)

        assertEquals(
            KernelSuFlavor.ReSukiSU,
            identifyManager("qwerty.asdfgh.zxcvbn", "re_suki_su").flavor,
        )
    }

    @Test
    fun `a rewritten package is attributed by its label`() {
        // What the spoofed build actually looks like: three random words, label untouched.
        val spoofed = identifyManager("qwerty.asdfgh.zxcvbn", "KernelSU-Next")
        assertEquals(KernelSuFlavor.KernelSuNext, spoofed.flavor)
        assertTrue(spoofed.spoofed)

        val spoofedKsu = identifyManager("qwerty.asdfgh.zxcvbn", "KernelSU")
        assertEquals(KernelSuFlavor.KernelSu, spoofedKsu.flavor)
        assertTrue(spoofedKsu.spoofed)
    }

    @Test
    fun `a label is read with its separators normalised`() {
        assertEquals(
            KernelSuFlavor.KernelSuNext,
            identifyManager("x.y.z", "KernelSU_Next").flavor,
        )
        assertEquals(
            KernelSuFlavor.KernelSu,
            identifyManager("x.y.z", "Kernel SU").flavor,
        )
        assertEquals(
            KernelSuFlavor.KernelSuNext,
            identifyManager("x.y.z", "kernelsu next manager").flavor,
        )
    }

    @Test
    fun `a label that says nothing leaves the flavour unknown`() {
        // It still carries a daemon, so it is still a manager; it just does not belong to a row.
        val unknown = identifyManager("qwerty.asdfgh.zxcvbn", "Superuser")

        assertNull(unknown.flavor)
        assertTrue(unknown.spoofed)
    }

    @Test
    fun `a manager's own name is shown only when it is not the flavour's`() {
        fun manager(label: String) = InstalledManager(
            packageName = "x.y.z",
            label = label,
            versionName = "3.3.0",
            flavor = KernelSuFlavor.KernelSu,
            spoofed = false,
        )

        // The published build: its label is the name the row above the version already carries, so
        // showing it is the duplication the version used to be in a different word.
        assertFalse(
            "the published manager's own name is printed beside a row that already names it",
            managerNameWorthShowing(manager("KernelSU"), KernelSuFlavor.KernelSu),
        )
        assertFalse(
            "the same name in different case is the same name",
            managerNameWorthShowing(manager("kernelsu "), KernelSuFlavor.KernelSu),
        )

        // The spoofed build: three random words whose package name changes every release, so the
        // label is the only thing on the phone that says which manager it is.
        assertTrue(
            "a manager that does not carry the flavour's name is printed without it",
            managerNameWorthShowing(manager("sturdy lantern pelican"), KernelSuFlavor.KernelSu),
        )
        assertTrue(
            "the other flavour's name is not this flavour's name",
            managerNameWorthShowing(manager("KernelSU-Next"), KernelSuFlavor.KernelSu),
        )
    }

    @Test
    fun `the Manager row shows a version only when the phone has one`() {
        fun manager(version: String?) = InstalledManager(
            packageName = "me.weishu.kernelsu",
            label = "KernelSU",
            versionName = version,
            flavor = KernelSuFlavor.KernelSu,
            spoofed = false,
        )

        assertEquals("3.3.0", managerRowValue(manager("3.3.0")))
        assertEquals(
            "nothing installed is no version to show, and not the one the app would install",
            "",
            managerRowValue(null),
        )
        assertEquals(
            "a package that will not answer is not a version the phone has",
            "",
            managerRowValue(manager(null)),
        )
    }

    @Test
    fun `the Manager row's value is never the version the app would install`() {
        // The defect this pins: the band fell back to the offered version, so a phone with no manager had
        // its next-row number printed on this row - and printed twice, in the value and in the description.
        val row = source("src/main/java/dev/busung/s25uroot/MainActivity.kt")

        assertTrue(
            "the value band no longer comes from the one reading that says what the phone has",
            row.contains("value = managerRowValue(installedManager)"),
        )
        assertFalse(
            "the offered version is on the row again as a fallback for a version this phone does not have",
            row.contains("managerVersion ?: offeredManagerVersion"),
        )
    }

    @Test
    fun `the offered release is the flavour's own default until one is named`() {
        assertEquals(
            "KernelSU_Next_v3.4.0_33294-release.apk",
            KernelSuFlavor.KernelSuNext.defaultManagerRelease.assetName,
        )
        assertEquals(
            "https://github.com/KernelSU-Next/KernelSU-Next/releases/download/v3.4.0/" +
                "KernelSU_Next_v3.4.0_33294-release.apk",
            KernelSuFlavor.KernelSuNext.defaultManagerRelease.url,
        )
    }

    /**
     * The lookup that names a version is a network read, and every caller of it is a tap.
     *
     * A tap handler runs on the main thread, where the read's own socket is refused before it opens -
     * which is not a crash but a sentence: "could not read the KernelSU-Next 3.4.0 release", for a
     * release that was published and reachable. Nothing about the failure points at threading, and the
     * version that appears in the picker beside it comes from the listing, which does run on `IO` - so
     * the one line that decides this is worth a test of its own.
     */
    @Test
    fun `the lookup that names a version is started off the calling thread`() {
        val body = source("src/main/java/dev/busung/s25uroot/KernelSuManager.kt")
            .substringAfter("private fun openDownload(")
            .substringBefore("private val lookups")
        val started = body.indexOf("lookups.launch")
        // The call, not the variable it reads into: what matters is where the read is, and a rename
        // here should not be able to disarm the guard that keeps it off the main thread.
        val read = body.indexOf("resolve(context, flavor,")
        assertTrue(
            "openDownload never starts the lookup on lookups: the read would run on the tapping thread",
            started >= 0,
        )
        assertTrue(
            "openDownload no longer reads a release at all",
            read >= 0,
        )
        assertTrue(
            "openDownload reads the release before it leaves the calling thread",
            read > started,
        )
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
