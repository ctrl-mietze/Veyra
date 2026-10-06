package ctrl.mietze.veyraroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPayloadTest {

    @Test
    fun acceptsSoNamesInAnyCase() {
        assertTrue(LocalPayload.isAcceptedName("cve-2026-43499-app.so"))
        assertTrue(LocalPayload.isAcceptedName("PAYLOAD.SO"))
        // A name that is nothing but the extension is odd rather than harmful, and the ELF check
        // is what actually decides whether the file can be used.
        assertTrue(LocalPayload.isAcceptedName(".so"))
    }

    @Test
    fun rejectsNamesThatAreNotSoFiles() {
        assertFalse(LocalPayload.isAcceptedName("payload"))
        assertFalse(LocalPayload.isAcceptedName("payload.so.txt"))
        assertFalse(LocalPayload.isAcceptedName("payload.so "))
    }

    @Test
    fun acceptsElfMagicWithTrailingHeaderBytes() {
        assertTrue(LocalPayload.isElf(elfHeader() + byteArrayOf(0x02, 0x01, 0x01, 0x00)))
        assertTrue(LocalPayload.isElf(elfHeader()))
    }

    @Test
    fun rejectsAnythingThatIsNotAnElfBinary() {
        assertFalse(LocalPayload.isElf("#!/bin/sh".toByteArray()))
        assertFalse(LocalPayload.isElf(byteArrayOf(0x7f, 'E'.code.toByte())))
        assertFalse(LocalPayload.isElf(ByteArray(0)))
        // A Mach-O payload carries a different magic and could not be loaded on this platform.
        assertFalse(
            LocalPayload.isElf(
                byteArrayOf(0xcf.toByte(), 0xfa.toByte(), 0xed.toByte(), 0xfe.toByte()),
            ),
        )
    }

    @Test
    fun capsTheImportedSizeAndRejectsTheNextByte() {
        assertTrue(LocalPayload.isWithinLimit(4L))
        assertTrue(LocalPayload.isWithinLimit(LocalPayload.MAX_BYTES))
        assertFalse(LocalPayload.isWithinLimit(LocalPayload.MAX_BYTES + 1))
    }

    private fun elfHeader() =
        byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
}
