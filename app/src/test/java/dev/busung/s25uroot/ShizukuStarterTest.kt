package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShizukuStarterTest {

    @Test
    fun `the native starter is asked for its own apk`() {
        val command = shizukuStarterCommand(
            starterPath = "/data/app/moe.shizuku.privileged.api/lib/arm64/libshizuku.so",
            apkPath = "/data/app/moe.shizuku.privileged.api/base.apk",
        )

        assertEquals(
            "'/data/app/moe.shizuku.privileged.api/lib/arm64/libshizuku.so' " +
                "--apk='/data/app/moe.shizuku.privileged.api/base.apk'",
            command,
        )
    }

    @Test
    fun `a path with a space stays one argument`() {
        val command = shizukuStarterCommand("/data/local/tmp/my dir/libshizuku.so", "/data/app/base.apk")

        assertEquals(
            "'/data/local/tmp/my dir/libshizuku.so' --apk='/data/app/base.apk'",
            command,
        )
    }

    @Test
    fun `a quote in a path cannot break out of the argument`() {
        // The whole point of quoting here is that a crafted path in shared storage must not be able
        // to become a second command in the root shell.
        val command = shizukuStarterCommand("/tmp/star'ter", "/data/app/base.apk")

        assertEquals("'/tmp/star'\\''ter' --apk='/data/app/base.apk'", command)
    }

    @Test
    fun `the first present legacy script wins`() {
        val candidates = listOf("/sdcard/one.sh", "/storage/emulated/0/two.sh")

        assertEquals(
            "/sdcard/one.sh",
            firstPresent(candidates) { it == "/sdcard/one.sh" },
        )
    }

    @Test
    fun `no legacy script is not a failure`() {
        assertNull(firstPresent(listOf("/sdcard/one.sh")) { false })
        assertNull(firstPresent(emptyList()) { true })
    }

    @Test
    fun `a command that never ran is not reported as a command that failed`() {
        // 127 is what both the boot service and the settings action use with no root shell at all,
        // so a starter that sees it must not treat it as a command that ran and failed.
        assertEquals(127, NO_ROOT_SHELL_EXIT)
    }
}
