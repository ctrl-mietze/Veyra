package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Veyra's current install/source identity.
 *
 * The project used to live through two different identities while it was being migrated. That is no
 * longer the architecture: Veyra owns both the Android application id and the Kotlin namespace.
 * The one deliberate legacy namespace reference is NativeProbeCompat, because the shipped native
 * library still exports the old JNI symbol names.
 */
class InstallIdentityTest {

    @Test
    fun `Veyra owns its application id and namespace`() {
        assertEquals("ctrl.mietze.veyraroot", BuildConfig.APPLICATION_ID)

        val build = moduleRoot().resolve("build.gradle.kts").readText()
        assertTrue(build.contains("namespace = \"ctrl.mietze.veyraroot\""))
        assertTrue(build.contains("applicationId = \"ctrl.mietze.veyraroot\""))
    }

    @Test
    fun `launcher shortcuts target the package that is actually installed`() {
        val shortcuts = moduleRoot().resolve("src/main/res/xml/shortcuts.xml").readText()
        val targets = Regex("""android:targetPackage="([^"]+)"""")
            .findAll(shortcuts)
            .map { it.groupValues[1] }
            .toList()

        assertTrue("no shortcut target packages were found", targets.isNotEmpty())
        assertEquals(
            List(targets.size) { BuildConfig.APPLICATION_ID },
            targets,
        )
        assertTrue(shortcuts.contains("android:targetClass=\"ctrl.mietze.veyraroot.MainActivity\""))
    }

    @Test
    fun `the previous install id is used only to detect the sibling app`() {
        val oldInstallId = "dev.rushiranpise.rmgnext"
        val uses = shippedFiles().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> line.contains(oldInstallId) }
                .map { (index, line) -> Triple(file, index + 1, line.trim()) }
        }

        assertTrue("the sibling identity disappeared, so coexistence can no longer be detected", uses.isNotEmpty())
        val offenders = uses.filterNot { (file, _, line) ->
            file.name == "SiblingInstall.kt" &&
                line.contains("const val PACKAGE = \"$oldInstallId\"")
        }
        assertEquals(
            "the previous install id escaped the sibling-detection boundary",
            emptyList<Triple<File, Int, String>>(),
            offenders,
        )
    }

    @Test
    fun `legacy JNI namespace references stay inside NativeProbeCompat`() {
        val legacyProbe = "dev.busung.s25uroot.NativeProbe"
        val uses = shippedFiles().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> line.contains(legacyProbe) }
                .map { (index, line) -> Triple(file, index + 1, line.trim()) }
        }

        assertTrue("the JNI compatibility shim no longer exercises the legacy symbols", uses.isNotEmpty())
        assertEquals(
            emptyList<Triple<File, Int, String>>(),
            uses.filterNot { (file, _, _) -> file.name == "NativeProbeCompat.kt" },
        )
    }

    private fun shippedFiles(): List<File> {
        val root = moduleRoot()
        return listOf(
            root.resolve("src/main/java"),
            root.resolve("src/main/res"),
        ).flatMap { directory ->
            directory.walkTopDown().filter(File::isFile).toList()
        } + listOf(
            root.resolve("src/main/AndroidManifest.xml"),
            root.resolve("build.gradle.kts"),
        )
    }

    private fun moduleRoot(): File =
        listOf(File("."), File("app"))
            .firstOrNull { File(it, "src/main").isDirectory }
            ?: throw AssertionError("app module not found")
}
