package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two package names this app has, and the rule that keeps them apart.
 *
 * A fork can move the id it installs under without moving the Java package it is written in, which is
 * what this one did - so from here on the same string means two different things depending on where it
 * appears. `package ctrl.mietze.veyraroot` at the top of a file is correct and always will be. The same
 * text inside a command that grants a permission, a resource that names the app a shortcut opens, or a
 * lookup for an installed package is a bug: it compiles, it runs, and it is aimed at an app that is not
 * this one.
 *
 * That is the class of mistake this test exists for, and it is not hypothetical. This fork's rename left
 * four such references behind - a shortcut resource pointing at the other install, an update check
 * reading the other install's releases, and a grant command naming a package that is not installed - and
 * the only reason they were caught is that they were looked for by hand, twice.
 *
 * ## What it says, and what it cannot
 *
 * It reads text, so it can say where the old prefix appears and hold every use to a fixed list of the
 * ones that are not this app's identity. What it cannot say is that an allowed use is *correct*: no grep
 * can prove that an action string is only ever sent to this app, and the sweep's own catalogue has the
 * same limit for the same reason. That claim is made by the tests next to the code that sends one - and
 * for the shortcut resource, by [RestartShortcutTest], which reads the same value out of the build.
 */
class InstallIdentityTest {

    @Test
    fun `nothing ships that aims at the package this fork moved off`() {
        val offenders = shippedFiles().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> line.contains(OLD_PACKAGE_PREFIX) }
                .filterNot { (_, line) -> allowedUses.any { allowed -> allowed.matches(file, line) } }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }

        assertEquals(
            "these name the package this fork moved off, in a way that is not one of the uses allowed " +
                "below - so they are aimed at an app that is not installed on this device",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the id this app installs under is read from the build and never typed`() {
        // The same mistake one rename later. With the id spelled out in a source file, moving it again
        // leaves a line that still compiles and now points at nothing. The places that cannot read a
        // build value keep their copy - the manifest uses ${applicationId}, the shortcut resource spells
        // the package out - and both are held to this same value by the tests that read them.
        val installId = BuildConfig.APPLICATION_ID

        val typed = shippedFiles()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> line.contains(installId) }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }

        assertEquals(
            "the installed package is written into a source file. It belongs in the build, and in the " +
                "resources that cannot read one; everywhere in Kotlin it is read instead - BuildConfig " +
                "for the grant command, context.packageName for the provider authority",
            emptyList<String>(),
            typed,
        )
    }

    @Test
    fun `every use this scan allows is still in the tree`() {
        // So the list cannot rot into permission for something that no longer exists. An allowance left
        // behind after its code is gone still reads like a use that is there, and the next person to
        // move the id would take it for one.
        val lines = shippedFiles().flatMap { file -> file.readLines().map { file to it } }

        allowedUses.forEach { allowed ->
            assertTrue(
                "no line in the app is ${allowed.reason}, so this allowance describes nothing",
                lines.any { (file, line) -> allowed.matches(file, line) },
            )
        }
    }

    /**
     * One use of the old prefix that is not this app's identity, and the test that says so.
     *
     * A predicate and a phrase rather than a list of line numbers: the point of this test is that it
     * keeps working when the code around it moves, and an allowance pinned to `AutoRootService.kt:773`
     * would fail on the next edit to a file it has nothing to say about.
     */
    private class AllowedUse(
        /** What the use is, phrased to finish the sentence "this line is ..." in a failure message. */
        val reason: String,
        val matches: (File, String) -> Boolean,
    )

    private val allowedUses: List<AllowedUse> = listOf(
        AllowedUse("the Java package, and the namespace that declares it") { _, line ->
            val code = line.trimStart()
            code.startsWith("package $OLD_PACKAGE_PREFIX") ||
                code.startsWith("import $OLD_PACKAGE_PREFIX") ||
                code.startsWith("namespace = \"$OLD_PACKAGE_PREFIX")
        },
        AllowedUse("an action string this app sends to itself") { _, line ->
            line.contains("\"${OLD_PACKAGE_PREFIX}s25uroot.action.")
        },
        AllowedUse("the extra key one of this app's own intents carries") { _, line ->
            line.contains("\"${OLD_PACKAGE_PREFIX}s25uroot.settings_target\"")
        },
        AllowedUse("the activity's own class name, which the shortcut resource has to spell out") { _, line ->
            line.contains("${OLD_PACKAGE_PREFIX}s25uroot.MainActivity")
        },
        AllowedUse("the check for whether the app it came from is installed") { file, line ->
            file.name == "SiblingInstall.kt" && line.contains("\"${OLD_PACKAGE_PREFIX}s25uroot\"")
        },
    )

    /**
     * Everything that ships in the app module: its Kotlin, its manifest, its resources, and the build
     * file that decides the id it installs under.
     *
     * The build file is in here on purpose. `applicationId` is the one line whose value *is* the answer
     * to this whole test, and a scan that skipped it would be a scan that cannot see the mistake it is
     * named after.
     */
    private fun shippedFiles(): List<File> {
        val files = moduleRoots()
            .flatMap { root ->
                listOf(
                    root.resolve("src/main/java"),
                    root.resolve("src/main/res"),
                ).flatMap { directory -> directory.walkTopDown().filter(File::isFile).toList() } +
                    listOf(
                        root.resolve("src/main/AndroidManifest.xml"),
                        root.resolve("build.gradle.kts"),
                    )
            }
            .filter { it.isFile && it.extension.lowercase() in SHIPPED_EXTENSIONS }
            .distinctBy { it.absolutePath }

        // A scan that finds nothing satisfies every assertion in this file, which is the one way it can
        // be wrong while looking right - so an empty file set is a failure of the test, not a pass of it.
        assertTrue(
            "no shipped files were found; the scan is looking at the wrong directory",
            files.any { it.extension == "kt" } && files.any { it.extension == "xml" },
        )
        return files
    }

    /** Where the app module is, whichever directory the test JVM was started in. */
    private fun moduleRoots(): List<File> = listOf(File("."), File("app"))
        .filter { File(it, "src/main").isDirectory }
}

/**
 * The vendor prefix every build of the app carried until this fork moved its id off it.
 *
 * Split from the rest of the id so the failure messages can name the whole thing: the package, the
 * action strings and the app this fork came from all begin here, and they are told apart by what
 * follows rather than by this.
 */
private const val OLD_PACKAGE_PREFIX = "dev.busung."

/** The kinds of file that end up on a device, plus the build file above them. */
private val SHIPPED_EXTENSIONS = setOf("kt", "xml", "kts")
