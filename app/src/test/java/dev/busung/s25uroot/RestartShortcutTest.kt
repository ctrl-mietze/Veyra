package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launcher's restart shortcuts, and the three files that have to agree for either to work.
 *
 * The action is a string written twice - once in the shortcut XML the launcher reads, once in the constant the
 * activity compares against - and nothing in the build joins them. A mismatch is invisible to the compiler and
 * to every run of the app from a tap: the long press opens the app and does nothing else, which reads as a
 * shortcut that is broken rather than as a typo.
 *
 * The same for the shortcut's own attributes: the launcher requires a short label and an icon, and one that is
 * missing an attribute it requires is not an error anywhere - the entry simply does not appear.
 *
 * Every test walks every shortcut in the file rather than the first one, because the second one was added
 * after the first and a test that read only the first would have passed with an unusable entry beside it.
 */
class RestartShortcutTest {

    @Test
    fun `every shortcut sends an action the app handles`() {
        val shortcuts = shortcuts()

        assertEquals("the file offers no restart shortcut at all", 2, shortcuts.size)
        shortcuts.forEach { shortcut ->
            val action = shortcut.attribute("android:action")
            assertNotNull(
                "the launcher sends \"$action\", which restartShortcutOf does not answer, so a long press " +
                    "opens the app and does nothing else",
                restartShortcutOf(action),
            )
        }
    }

    @Test
    fun `the two shortcuts ask the app for different things`() {
        val actions = shortcuts().map { it.attribute("android:action") }
        val ids = shortcuts().map { it.attribute("android:shortcutId") }

        assertEquals(
            "two shortcuts send the same action, so one of them does what the other does and its own label lies",
            actions.size,
            actions.distinct().size,
        )
        assertEquals("two shortcuts share an id, and the launcher keeps only one", ids.size, ids.distinct().size)
        assertEquals(
            "the soft restart is not the shortcut that asks for the soft restart",
            setOf(ACTION_RESTART_OPTIONS, ACTION_SOFT_RESTART),
            actions.toSet(),
        )
    }

    @Test
    fun `every shortcut opens the app's own window`() {
        // Two different names, and this is the file where they are easiest to confuse. The launcher
        // matches `targetPackage` against the *install* id, which is the build's applicationId; the
        // class it then loads lives in the Java package, which this fork deliberately leaves upstream's.
        // Reading them from the build and from this test's own package - rather than typing either -
        // is what makes the pair fail here instead of on someone's launcher.
        val applicationId = BuildConfig.APPLICATION_ID
        val javaPackage = javaClass.packageName

        shortcuts().forEach { shortcut ->
            assertEquals(
                "the shortcut points at a package that is not this install, so its long press opens the " +
                    "other app or nothing at all",
                applicationId,
                shortcut.attribute("android:targetPackage"),
            )
            assertEquals(
                "the shortcut targets something other than the launcher activity, so it opens a screen that " +
                    "does not know about restart",
                "$javaPackage.MainActivity",
                shortcut.attribute("android:targetClass"),
            )
        }
    }

    @Test
    fun `every shortcut carries everything a launcher needs to show it`() {
        shortcuts().forEach { shortcut ->
            listOf("android:shortcutId", "android:icon", "android:shortcutShortLabel").forEach { attribute ->
                assertTrue(
                    "a shortcut has no $attribute, and a launcher that cannot fill one in shows nothing at all",
                    shortcut.attribute(attribute).isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `the soft restart is labelled as the target it runs`() {
        val shortcut = shortcuts().single { it.attribute("android:action") == ACTION_SOFT_RESTART }

        // The short label is the sheet's own for that row rather than a second wording of it: the shortcut runs
        // the same thing the row does, and a name invented for the launcher is a name that has to be kept in
        // step with the one on the row by hand. The long label is its own string, because it is the one place
        // that still explains what a soft restart is - the row it used to share that line with has only its
        // name now.
        assertEquals("@string/reboot_target_soft_restart", shortcut.attribute("android:shortcutShortLabel"))
        assertEquals("@string/shortcut_soft_restart_long", shortcut.attribute("android:shortcutLongLabel"))
    }

    @Test
    fun `the sheet's shortcut is labelled by the sheet it opens`() {
        val shortcut = shortcuts().single { it.attribute("android:action") == ACTION_RESTART_OPTIONS }

        // The long label is what a launcher with room shows, and it is the sheet's own title: the two saying
        // the same thing is the point of the shortcut, since what it opens is that sheet.
        assertEquals("@string/reboot_sheet_title", shortcut.attribute("android:shortcutLongLabel"))
    }

    @Test
    fun `the manifest hands the shortcut file to the launcher activity`() {
        val manifest = source("main/AndroidManifest.xml")
        val activity = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("</activity>")

        assertTrue(
            "the shortcut file is not declared, so the launcher never reads it",
            activity.contains("android:name=\"android.app.shortcuts\"") &&
                activity.contains("android:resource=\"@xml/shortcuts\""),
        )
    }

    private fun shortcutXml(): String = source("main/res/xml/shortcuts.xml")

    /**
     * Every shortcut in the file, as the attributes of the shortcut and of the intent inside it.
     *
     * Matched on the element rather than on its name alone: `<shortcut` is a prefix of the `<shortcuts>` root,
     * and the difference is reading the root's namespace instead of the shortcut's.
     */
    private fun shortcuts(): List<Attributes> =
        Regex("<shortcut\\s([^>]*)>(.*?)</shortcut>", RegexOption.DOT_MATCHES_ALL)
            .findAll(shortcutXml())
            .map { match -> Attributes(match.groupValues[1], match.groupValues[2]) }
            .toList()

    /** One shortcut's attributes, and the intent it carries as a second set of them. */
    private inner class Attributes(private val own: String, private val body: String) {
        fun attribute(name: String): String = own.attribute(name).ifBlank { body.attribute(name) }
    }

    private fun String.attribute(name: String): String =
        substringAfter("$name=\"", "").substringBefore("\"")

    private fun source(relative: String): String {
        val file = candidateRoots().map { File(it, relative) }.firstOrNull(File::isFile)
        requireNotNull(file) { "$relative was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun candidateRoots(): List<String> = listOf("app/src", "src").filter { File(it).isDirectory }
}
