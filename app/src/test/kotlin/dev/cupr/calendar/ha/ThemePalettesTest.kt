package dev.cupr.calendar.ha

import dev.cupr.calendar.model.Palettes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePalettesTest {
    private fun theme(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun readable(json: String) = ThemePalettes.read(theme(json)) as ThemePalettes.Read.Readable

    /** The variables the app reads, with the values of homelab's theme file. */
    private val zuhause =
        """
        {"ha-card-border-radius":"18px","modes":{
          "dark":{"card-background-color":"#1a1f25","ha-card-border-color":"#262c34","primary-text-color":"#eceef1",
            "secondary-text-color":"#a3acb7","zuhause-text-tertiary":"#7d8793","divider-color":"#262c34",
            "primary-color":"#6fb1ff","text-primary-color":"#0d1a2a","zuhause-accent-soft":"rgba(111, 177, 255, 0.16)",
            "zuhause-calendar-holiday":"#ff7a7a","zuhause-sheet-shadow":"rgba(0, 0, 0, 0.5)",
            "zuhause-calendar-birthdays":"#ffd700","zuhause-calendar-school":"#40e0d0",
            "zuhause-calendar-public":"#a9a9a9","zuhause-calendar-special":"#ff8c00"},
          "light":{"card-background-color":"#ffffff","ha-card-border-color":"#dde2e9","primary-text-color":"#161b22",
            "secondary-text-color":"#5b6573","zuhause-text-tertiary":"#666e7a","divider-color":"#e6eaf0",
            "primary-color":"#1f6fd6","text-primary-color":"#ffffff","zuhause-accent-soft":"rgba(31, 111, 214, 0.12)",
            "zuhause-calendar-holiday":"#b52222","zuhause-sheet-shadow":"rgba(22, 27, 34, 0.18)",
            "zuhause-calendar-birthdays":"#a38a00","zuhause-calendar-school":"#2d9d92",
            "zuhause-calendar-public":"#8c8c8c","zuhause-calendar-special":"#d67500"}}}
        """.trimIndent()

    @Test
    fun `the theme Zuhause reads as the built-in palettes, which copy it`() {
        val read = readable(zuhause)

        assertEquals(Palettes.BUILT_IN, read.palettes)
        assertEquals(emptyList<String>(), read.missing)
    }

    @Test
    fun `a mode's own variable wins over the theme's top level, which stands in where the mode has none`() {
        val read = readable("""{"primary-color":"#000001","divider-color":"#000002","modes":{"dark":{"primary-color":"red"}}}""")

        assertEquals(0xFFFF0000.toInt(), read.palettes.dark.accent)
        assertEquals(0xFF000001.toInt(), read.palettes.light.accent)
        assertEquals(0xFF000002.toInt(), read.palettes.dark.divider)
        assertEquals(0xFF000002.toInt(), read.palettes.light.divider)
    }

    @Test
    fun `a variable the theme lacks is the built-in one and named`() {
        val read = readable("""{"modes":{"light":{"primary-color":"#000001"},"dark":{}}}""")

        assertEquals(0xFF000001.toInt(), read.palettes.light.accent)
        assertEquals(Palettes.BUILT_IN.dark.accent, read.palettes.dark.accent)
        assertEquals(Palettes.BUILT_IN.light.calendars, read.palettes.light.calendars)
        assertTrue(read.missing.toString(), "dark primary-color" in read.missing)
        assertTrue(read.missing.toString(), "light card-background-color" in read.missing)
        assertTrue(read.missing.toString(), "dark zuhause-calendar-special" in read.missing)
        assertTrue(read.missing.toString(), "light primary-color" !in read.missing)
    }

    @Test
    fun `a value that is no colour the app reads makes the theme unreadable`() {
        assertEquals(
            ThemePalettes.Read.Unreadable("light primary-color is not a colour: \"var(--accent)\""),
            ThemePalettes.read(theme("""{"modes":{"light":{"primary-color":"var(--accent)"}}}""")),
        )
        assertEquals(
            ThemePalettes.Read.Unreadable("light divider-color is not a colour: 3"),
            ThemePalettes.read(theme("""{"divider-color":3}""")),
        )
    }

    @Test
    fun `modes or a mode that is no object makes the theme unreadable`() {
        assertEquals(ThemePalettes.Read.Unreadable("modes is not an object"), ThemePalettes.read(theme("""{"modes":"x"}""")))
        assertEquals(
            ThemePalettes.Read.Unreadable("modes.dark is not an object"),
            ThemePalettes.read(theme("""{"modes":{"dark":[]}}""")),
        )
    }

    @Test
    fun `rgb and rgba read with their opacity rounded to a byte`() {
        assertEquals(0x296FB1FF, ThemePalettes.parseColor("rgba(111, 177, 255, 0.16)"))
        assertEquals(0x80000000.toInt(), ThemePalettes.parseColor("rgba(0,0,0,.5)"))
        assertEquals(0xFF0A141E.toInt(), ThemePalettes.parseColor(" rgb(10, 20, 30) "))
        assertEquals(0x000A141E, ThemePalettes.parseColor("rgba(10, 20, 30, 0)"))
        assertEquals(0xFFFF69B4.toInt(), ThemePalettes.parseColor("HotPink"))
        assertEquals(0xFF1F6FD6.toInt(), ThemePalettes.parseColor("#1f6fd6"))
    }

    @Test
    fun `rgb with a part out of range or an opacity above 1 is no colour`() {
        assertNull(ThemePalettes.parseColor("rgb(256, 0, 0)"))
        assertNull(ThemePalettes.parseColor("rgba(0, 0, 0, 1.5)"))
        assertNull(ThemePalettes.parseColor("rgba(0, 0, 0, -1)"))
        assertNull(ThemePalettes.parseColor("rgb(0, 0)"))
        assertNull(ThemePalettes.parseColor("hsl(0, 0%, 0%)"))
    }
}
