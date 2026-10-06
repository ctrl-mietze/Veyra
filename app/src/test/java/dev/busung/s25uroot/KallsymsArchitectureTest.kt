package ctrl.mietze.veyraroot

import com.kernelpack.kallsyms.KallsymsFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KallsymsArchitectureTest {
    @Test
    fun rawArm64ImageHeaderDoesNotNeedPeMzPrefix() {
        val image = ByteArray(0x80)
        image[0x38] = 'A'.code.toByte()
        image[0x39] = 'R'.code.toByte()
        image[0x3a] = 'M'.code.toByte()
        image[0x3b] = 'd'.code.toByte()

        val guess = KallsymsFinder.guessArchitectureFromImage(image)

        assertNotNull(guess)
        assertEquals("aarch64", guess!!.name)
        assertTrue(guess.is64Bit)
        assertFalse(guess.isBigEndian)
    }

    @Test
    fun elf64Aarch64IsRecognizedBeforeInstructionHeuristics() {
        val image = ByteArray(0x80)
        image[0] = 0x7f
        image[1] = 'E'.code.toByte()
        image[2] = 'L'.code.toByte()
        image[3] = 'F'.code.toByte()
        image[4] = 2 // ELFCLASS64
        image[5] = 1 // little endian
        image[18] = 183.toByte() // EM_AARCH64
        image[19] = 0

        val guess = KallsymsFinder.guessArchitectureFromImage(image)

        assertNotNull(guess)
        assertEquals("aarch64", guess!!.name)
        assertTrue(guess.is64Bit)
    }

    @Test
    fun kernelBannerCanBeReadWithoutDecodingKallsyms() {
        val banner = "Linux version 4.19.152-perf+ (compiler@host) #1 SMP PREEMPT"
        val image = ByteArray(4096)
        val bytes = banner.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(image, destinationOffset = 1536)

        val result = KallsymsFinder.linuxVersionFromImage(image)

        assertNotNull(result)
        assertEquals("4.19.152", result!!.second)
        assertEquals(banner, result.first)
    }
}
