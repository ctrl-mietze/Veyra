package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permissions the code actually asks for, against the ones the manifest declares.
 *
 * A missing declaration is not a degraded run: the call throws `SecurityException` at the point of
 * use. In a service that point can be a boot path, where the throw kills the process the app was
 * relying on and the only symptom is the app crashing after a reboot - which is how a wake lock
 * arrived here without `WAKE_LOCK` and the boot gate died a second after it started.
 *
 * Deliberately one-directional: declared-but-unused is not a failure, because a permission can be
 * declared for a code path that is off, and demanding the manifest match the code exactly would make
 * this a style test rather than a correctness one.
 */
class ManifestPermissionTest {

    @Test
    fun `every permission the code asks for is declared`() {
        val manifest = manifestFile().readText()
        val sources = sourceFiles()
        assertTrue("no sources were found; the scan is looking at the wrong directory", sources.isNotEmpty())
        val code = sources.joinToString("\n", transform = File::readText)

        REQUIREMENTS.forEach { (marker, permission) ->
            if (!code.contains(marker)) return@forEach
            assertTrue(
                "the code calls $marker, which needs $permission, and the manifest does not declare it",
                manifest.contains(permission),
            )
        }
    }

    /**
     * A foreground service type is a permission of its own on every supported API level.
     *
     * The type is written in the manifest next to the service that uses it, so this is read from the
     * manifest alone - the mismatch it catches is two lines in the same file disagreeing.
     */
    @Test
    fun `every foreground service type has its permission`() {
        val manifest = manifestFile().readText()
        val declared = FOREGROUND_SERVICE_TYPE.findAll(manifest)
            .flatMap { match -> match.groupValues[1].split('|') }
            .filter { type -> type != "none" }
            .toList()
        assertTrue("the manifest declares no foreground service type at all", declared.isNotEmpty())

        declared.forEach { type ->
            val permission = "android.permission.FOREGROUND_SERVICE_${type.uppercaseSnake()}"
            assertTrue(
                "a service is declared $type without $permission",
                manifest.contains(permission),
            )
        }
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun manifestFile(): File = listOf(
        File("src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
    ).firstOrNull(File::isFile)
        ?: throw AssertionError("the manifest was not found from ${File(".").absolutePath}")

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)

    private fun String.uppercaseSnake(): String = buildString {
        this@uppercaseSnake.forEach { character ->
            if (character.isUpperCase() && isNotEmpty()) append('_')
            append(character.uppercaseChar())
        }
    }

    private companion object {
        val FOREGROUND_SERVICE_TYPE = Regex("""android:foregroundServiceType="([A-Za-z|]+)"""")

        /** The API that needs the permission, and the permission it needs. */
        val REQUIREMENTS = listOf(
            "newWakeLock" to "android.permission.WAKE_LOCK",
            "Settings.Global.putInt" to "android.permission.WRITE_SECURE_SETTINGS",
            "Settings.Secure.putInt" to "android.permission.WRITE_SECURE_SETTINGS",
            "Settings.Secure.putString" to "android.permission.WRITE_SECURE_SETTINGS",
            "createMulticastLock" to "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "getActiveNetwork" to "android.permission.ACCESS_NETWORK_STATE",
            "openConnection" to "android.permission.INTERNET",
        )
    }
}
