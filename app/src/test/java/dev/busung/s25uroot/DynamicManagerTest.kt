package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure halves of manager registration: reading a key out of the daemon's answers, and
 * building the commands that ask for it.
 *
 * The parser is not a convenience. The daemon prints the same key in two different bases - `debug
 * get-sign` writes the size in hexadecimal and `kernel dynamic-manager get` writes it in decimal -
 * and the `set` command's own argument parser accepts only the decimal one, because it takes a `u32`
 * by `FromStr`. So the value that comes back from one has to be converted to be given to the other,
 * and a mismatch here is a registration that never lands with nothing on screen to say why.
 *
 * The other half is the state mapping, which is where a mistake is expensive rather than merely
 * wrong: "the kernel holds no key" and "nothing could be asked" are one boolean apart, and reading
 * the second as the first tells a user to register a manager on a phone whose kernel was never
 * reached.
 */
class DynamicManagerTest {

    /** Exactly 64 characters, because that is the only length the kernel's own validation accepts. */
    private val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    // --- reading a key ------------------------------------------------------------------------------

    @Test
    fun `reads the hexadecimal size the daemon prints for an APK`() {
        // What `ksud debug get-sign` actually writes: `println!("size: {:#x}, hash: {}", …)`.
        val parsed = parseManagerSignature("size: 0x300, hash: $hash")

        assertEquals(ManagerSignature(size = 768, hash = hash), parsed)
    }

    @Test
    fun `reads the decimal size the daemon prints for the kernel's own setting`() {
        // What `ksud kernel dynamic-manager get` writes: `println!("size: {}, hash: {}", …)`. The two
        // are the same key, and they have to arrive here as the same value.
        val parsed = parseManagerSignature("size: 768, hash: $hash")

        assertEquals(parseManagerSignature("size: 0x300, hash: $hash"), parsed)
    }

    @Test
    fun `the size goes back out in the decimal form the set command parses`() {
        // The round trip that matters. `set` takes `size: u32`, parsed from the argument by
        // `FromStr`, which reads decimal only - so handing back the `0x300` the read printed is a
        // command that fails inside the daemon's own argument parser, before anything reaches the
        // kernel, with an error about a digit rather than about a manager.
        val signature = parseManagerSignature("size: 0x300, hash: $hash")!!

        val command = dynamicManagerSetCommand(signature)

        assertTrue(command, command.contains(" set 768 $hash"))
        assertFalse(
            "the daemon's hexadecimal size was passed straight back to a parser that reads decimal",
            command.contains("0x300"),
        )
    }

    @Test
    fun `a hash is normalised so two readings of one key compare equal`() {
        assertEquals("aabb", parseManagerSignature("size: 1, hash: ${"AABB".padEnd(64, '0')}")!!.hash.take(4))
    }

    @Test
    fun `a size of zero is not a signature`() {
        // Zero is what the kernel's own struct starts at, so an empty answer would otherwise read as
        // a registration.
        assertNull(parseManagerSignature("size: 0, hash: $hash"))
        assertNull(parseManagerSignature("size: 0x0, hash: $hash"))
    }

    @Test
    fun `an answer without a whole hash is not a signature`() {
        assertNull(parseManagerSignature("size: 0x300, hash: ${hash.take(63)}"))
        assertNull(parseManagerSignature("size: 0x300, hash: $hash$hash"))
        assertNull(parseManagerSignature("size: 0x300"))
        assertNull(parseManagerSignature("size: 0x300, hash: not-a-hash"))
        assertNull(parseManagerSignature(""))
        assertNull(parseManagerSignature("Error: ioctl failed: No data available (os error 61)"))
    }

    // --- what an answer means ----------------------------------------------------------------------

    @Test
    fun `a key in a successful answer is what the kernel holds`() {
        val reading = dynamicManagerReading(exitCode = 0, output = "size: 768, hash: $hash")

        assertEquals(DynamicManagerState.Held, reading.state)
        assertEquals(hash, reading.signature?.hash)
    }

    @Test
    fun `the kernel saying it holds nothing is the one failure that is an answer`() {
        // The kernel replies `-ENODATA`, and the daemon prints its errno description rather than an
        // empty line - so "nothing is registered" arrives as a failure, and it is the only failure
        // that may be read as one.
        val reading = dynamicManagerReading(
            exitCode = 1,
            output = "Error: ioctl failed: No data available (os error 61)",
        )

        assertEquals(DynamicManagerState.Unset, reading.state)
        assertNull(reading.signature)
    }

    @Test
    fun `every other failure claims nothing about the kernel`() {
        // Three ways to fail that are not "no key registered", and each would be a lie if it were:
        // no kernel loaded at all, a daemon that does not have the subcommand, and a successful exit
        // that carried no signature after all.
        val cases = mapOf(
            "no driver fd" to "Error: could not retrieve kernelsu driver fd",
            "no such subcommand" to "error: unrecognized subcommand 'dynamic-manager'",
            "empty output with a failure" to "",
            "zero exit and no line" to "",
        )

        for ((name, output) in cases) {
            val exit = if (name == "zero exit and no line") 0 else 1
            assertEquals(
                name,
                DynamicManagerState.Unreadable,
                dynamicManagerReading(exit, output).state,
            )
        }
    }

    // --- the commands ------------------------------------------------------------------------------

    @Test
    fun `every command resolves the daemon the same way the version probe does`() {
        // One resolution, so the daemon that is asked about a signature is the daemon that was asked
        // about its version: `/data/adb` first, because that is where the payload leaves it, and
        // `PATH` second for a device where it is somewhere else.
        for (command in listOf(
            managerSignatureCommand("/data/app/x/base.apk"),
            dynamicManagerReadCommand(),
            dynamicManagerSetCommand(ManagerSignature(768, hash)),
        )) {
            assertTrue(command, command.contains("K=/data/adb/ksud; [ -x \"\$K\" ] || K=ksud"))
            assertTrue(command, command.contains("\"\$K\""))
        }
    }

    @Test
    fun `the commands name the subcommands that read and write the key`() {
        assertTrue(managerSignatureCommand("/data/app/x/base.apk").contains("debug get-sign '/data/app/x/base.apk'"))
        assertTrue(dynamicManagerReadCommand().contains("kernel dynamic-manager get"))
        assertTrue(
            dynamicManagerSetCommand(ManagerSignature(768, hash))
                .contains("kernel dynamic-manager set 768 $hash"),
        )
    }

    @Test
    fun `an APK path is quoted, because it is a string the app read off the device`() {
        // A package name is a file name is a shell word, and an installed path is the app's own text
        // rather than ours: `sourceDir` of a package whose name carries a quote would otherwise end
        // the word early and hand the daemon a different file.
        val command = managerSignatureCommand("/data/app/weird'name/base.apk")

        assertTrue(command, command.contains("""'/data/app/weird'\''name/base.apk'"""))
    }

    // --- the short form ----------------------------------------------------------------------------

    @Test
    fun `the short form keeps the size and the start of the hash`() {
        // One line is all the row has, and the size is what the kernel's own table matches first.
        assertEquals("0x300 · 01234567", ManagerSignature(768, hash).shortLabel())
        assertEquals("0x1c0 · aabbccdd", ManagerSignature(448, "aabbccdd".padEnd(64, '0')).shortLabel())
    }
}
