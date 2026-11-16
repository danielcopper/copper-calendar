package dev.cupr.calendar.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaAddressTest {
    @Test
    fun `an address without a scheme is taken as https`() {
        assertEquals("https://abc.ui.nabu.casa/", HaAddress.parse("  abc.ui.nabu.casa ")?.url.toString())
    }

    @Test
    fun `the address is the instance's root, its port kept`() {
        assertEquals(
            "https://abc.ui.nabu.casa/",
            HaAddress.parse("https://abc.ui.nabu.casa/lovelace/0?x=1#y")?.url.toString(),
        )
        assertEquals("https://home.example:8443/", HaAddress.parse("https://home.example:8443/")?.url.toString())
    }

    @Test
    fun `a stored address is cut to its root again, plain http included, and no address is none`() {
        assertEquals("https://home.example/", HaAddress.stored("https://home.example/")?.url.toString())
        assertEquals("http://localhost:41234/", HaAddress.stored("http://localhost:41234/x/y?z=1")?.url.toString())
        assertNull(HaAddress.stored("no address"))
    }

    @Test
    fun `an http address, an empty one or one that is no address is refused`() {
        assertNull(HaAddress.parse("http://homeassistant.local:8123"))
        assertNull(HaAddress.parse("   "))
        assertNull(HaAddress.parse("https://"))
        assertNull(HaAddress.parse("ftp://home.example"))
    }
}
