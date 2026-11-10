package dev.cupr.calendar.render

import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** A day number's colour and weight, in the order the dashboard's styles give them. */
class NumberStyleTest {
    private val palette = SampleData.light
    private val sunday = LocalDate.of(2026, 11, 15)
    private val friday = LocalDate.of(2026, 11, 13)

    @Test
    fun `today wins over holiday and weekend, past over holiday and weekend, then holiday, then weekend`() {
        val cases =
            listOf(
                Probe.day(sunday, today = true, holiday = true) to palette.onAccent,
                Probe.day(sunday, past = true, holiday = true) to palette.tertiaryText,
                Probe.day(sunday, holiday = true) to palette.holiday,
                Probe.day(sunday) to palette.secondaryText,
                Probe.day(friday) to palette.text,
            )
        for ((day, color) in cases) assertEquals(day.toString(), color, numberColor(day, palette))
    }

    @Test
    fun `today and a public holiday are bold, other days medium`() {
        assertEquals(FontWeight.BOLD, numberWeight(Probe.day(friday, today = true)))
        assertEquals(FontWeight.BOLD, numberWeight(Probe.day(friday, holiday = true, past = true)))
        assertEquals(FontWeight.MEDIUM, numberWeight(Probe.day(friday, past = true)))
    }
}
