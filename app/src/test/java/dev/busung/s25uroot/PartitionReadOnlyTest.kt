package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartitionReadOnlyTest {

    @Test
    fun `the count is the last number the script printed`() {
        assertEquals(4, PartitionReadOnly.countFrom("4\n"))
        // A shell that said something on the way in must not have its words read as the answer.
        assertEquals(9, PartitionReadOnly.countFrom("blockdev: /dev/block/by-name/boot: ok\n9\n"))
    }

    @Test
    fun `an unreadable answer counts as no protection at all`() {
        // Reporting protection that was not obtained is the one outcome this must not have.
        assertEquals(0, PartitionReadOnly.countFrom(""))
        assertEquals(0, PartitionReadOnly.countFrom("blockdev: not found\n"))
        assertEquals(0, PartitionReadOnly.countFrom("0"))
        assertEquals(0, PartitionReadOnly.countFrom("-3"))
    }

    @Test
    fun `the script covers the partitions that turn a rooted phone into a brick`() {
        val script = File(scriptPath()).readText()
        for (partition in listOf("boot", "dtbo", "init_boot", "vendor_boot", "super", "optics", "prism", "vbmeta")) {
            assertTrue("missing $partition", script.contains(partition))
        }
        // Both slots: the device boots whichever one the bootloader selected, and only the inactive
        // slot is written during a normal update.
        assertTrue(script.contains("\${p}_a"))
        assertTrue(script.contains("\${p}_b"))
        // The flag is what makes the write fail; a script that merely listed the devices would do
        // nothing at all.
        assertTrue(script.contains("blockdev --setro"))
    }

    @Test
    fun `the kernel's read-only wording is recognised and the app's own lines are not`() {
        assertTrue(PartitionReadOnly.refusedByReadOnly("cp: /system/app/X: Read-only file system"))
        assertTrue(PartitionReadOnly.refusedByReadOnly("dd: failed: Read-only file system"))
        assertTrue(PartitionReadOnly.refusedByReadOnly("mount: /system is read-only"))

        // The lines this app writes *about* the protection must never be read as its wall: a run that
        // blamed its own guard for a failure elsewhere would talk someone out of turning it on.
        assertFalse(PartitionReadOnly.refusedByReadOnly("[+] Set 6 partitions to read-only"))
        assertFalse(
            PartitionReadOnly.refusedByReadOnly(
                "[!] Read-only partition script is missing from this build",
            ),
        )
        assertFalse(PartitionReadOnly.refusedByReadOnly("[-] exploit gave up after 24 attempts"))
        assertFalse(PartitionReadOnly.refusedByReadOnly(""))
    }

    @Test
    fun `a failure is blamed on the protection only when the protection refused the write`() {
        val setup = "[*] bootstrap root ok\n[+] Set 6 partitions to read-only\n"
        val afterSetup = setup.length

        // The plain case: a write refused after the protection went up.
        assertTrue(
            refusedByProtection(
                log = setup + "[-] cp: /system/etc/x: Read-only file system\n",
                protectedFrom = afterSetup,
                protectedDevices = 6,
            ),
        )
        // Nothing was set, so nothing here refused anything - even with the wording in the log.
        assertFalse(
            refusedByProtection(
                log = setup + "[-] Read-only file system\n",
                protectedFrom = afterSetup,
                protectedDevices = 0,
            ),
        )
        // Refused before the protection existed: somebody else's wall, and not this switch's fault.
        val earlierRefusal = "cp: /vendor/x: Read-only file system\n"
        assertFalse(
            refusedByProtection(
                log = earlierRefusal + setup,
                protectedFrom = earlierRefusal.length + afterSetup,
                protectedDevices = 6,
            ),
        )
        // No run ever set anything in this boot, so there is no position to scan from at all.
        assertFalse(
            refusedByProtection(
                log = "Read-only file system\n",
                protectedFrom = -1,
                protectedDevices = 6,
            ),
        )
        // A command whose output never reached the log still names the wall in its own message.
        assertTrue(
            refusedByProtection(
                log = setup,
                protectedFrom = afterSetup,
                protectedDevices = 6,
                reason = "dd: /dev/block/by-name/boot: Read-only file system",
            ),
        )
        // And the reason alone is not enough on a boot that protected nothing.
        assertFalse(
            refusedByProtection(
                log = setup,
                protectedFrom = afterSetup,
                protectedDevices = 0,
                reason = "Read-only file system",
            ),
        )
    }

    /** The asset, from whichever directory the test JVM was started in. */
    private fun scriptPath(): String {
        val relative = "src/main/assets/${PartitionReadOnly.SCRIPT_ASSET}"
        return if (File(relative).isFile) relative else "app/$relative"
    }
}
