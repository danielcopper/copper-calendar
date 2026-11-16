package dev.cupr.calendar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RunThenTest {
    @Test
    fun `after runs once the block returns, and the block's value is returned`() {
        val ran = mutableListOf<String>()

        val value = runThen({ "block".also(ran::add) }) { ran += "after" }

        assertEquals("block", value)
        assertEquals(listOf("block", "after"), ran)
    }

    @Test
    fun `when the block throws, after runs, and when it throws too its exception travels in the block's`() {
        val failed = IllegalStateException("block")
        val alsoFailed = IllegalArgumentException("after")

        val thrown = assertThrows(IllegalStateException::class.java) { runThen({ throw failed }) { throw alsoFailed } }

        assertSame(failed, thrown)
        assertEquals(listOf(alsoFailed), thrown.suppressed.toList())
    }

    @Test
    fun `when only after throws, its exception travels`() {
        val failed = IllegalArgumentException("after")

        val thrown = assertThrows(IllegalArgumentException::class.java) { runThen({}) { throw failed } }

        assertSame(failed, thrown)
    }
}
