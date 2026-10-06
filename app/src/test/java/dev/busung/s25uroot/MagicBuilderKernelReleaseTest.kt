package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Test

class MagicBuilderKernelReleaseTest {

    @Test
    fun `full Samsung kernel release is preserved from Linux banner`() {
        assertEquals(
            "6.6.98-android15-8-pd6ff1cd-abogkiS938BXXSBCZG3-4k",
            kernelReleaseFromBanner(
                "Linux version 6.6.98-android15-8-pd6ff1cd-abogkiS938BXXSBCZG3-4k " +
                    "(builder@host) (clang version 20.0.0) #1 SMP PREEMPT",
                "6.6.98",
            ),
        )
    }

    @Test
    fun `numeric version remains fallback when banner is incomplete`() {
        assertEquals(
            "6.6.98",
            kernelReleaseFromBanner("Linux version %s (%s)", "6.6.98"),
        )
    }
}
