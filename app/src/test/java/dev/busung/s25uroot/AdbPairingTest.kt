package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbPairingTest {

    @Test
    fun `the published port is read from the first line that says anything`() {
        assertEquals(41234, parseAdbTlsPort("41234\n"))
        assertEquals(41234, parseAdbTlsPort("\n  41234  \n"))
        // getprop prints nothing when the property is unset, and a stray warning must not become a
        // port that then gets connected to.
        assertNull(parseAdbTlsPort(""))
        assertNull(parseAdbTlsPort("getprop: not found\n41234\n"))
    }

    @Test
    fun `anything outside the port range is not a port`() {
        assertNull(parseAdbTlsPort("0"))
        assertNull(parseAdbTlsPort("-1"))
        assertNull(parseAdbTlsPort("65536"))
        assertNull(parseAdbTlsPort("not a number"))
        assertEquals(1, parseAdbTlsPort("1"))
        assertEquals(65535, parseAdbTlsPort("65535"))
    }
}
