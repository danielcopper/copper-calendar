package dev.cupr.calendar.render

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.render.Probe.has
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The text a header shows at its right instead of a week's range and the chevron (D51, D52). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderNoteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fonts = Fonts(context.assets)
    private val chrome =
        ChromeRenderer(fonts, Icons(context.assets), context.resources.getStringArray(R.array.weekday_heads).toList())
    private val scale = Probe.scale
    private val widthPx = Probe.widthPx
    private val palette = SampleData.light
    private val rowBottom = scale.px(PhoneMonth.TITLE_TOP + PhoneMonth.TITLE_ROW)
    private val chevronLeft = widthPx - scale.px(PhoneMonth.CHEVRON)

    @Test
    fun `a note takes the chevron's place`() {
        val plain = chrome.header(SampleData.month, widthPx, scale, palette)
        // A blank note draws nothing itself, so whatever stays in the chevron's place would be the chevron.
        val blank = chrome.header(SampleData.month, widthPx, scale, palette, HeaderNote(" ", accent = false))
        assertTrue(plain.has(palette.tertiaryText, chevronLeft, 0f, widthPx.toFloat(), rowBottom))
        assertFalse(blank.has(palette.tertiaryText, chevronLeft, 0f, widthPx.toFloat(), rowBottom))
    }

    @Test
    fun `a stale note is muted, one asking to sign in again is in the accent colour`() {
        val stale = chrome.header(SampleData.month, widthPx, scale, palette, HeaderNote("Stand 14:30", accent = false))
        val signIn = chrome.header(SampleData.month, widthPx, scale, palette, HeaderNote("Neu anmelden", accent = true))
        val right = widthPx / 2f
        assertTrue(stale.has(palette.tertiaryText, right, 0f, widthPx.toFloat(), rowBottom))
        assertFalse(stale.has(palette.accent, 0f, 0f, widthPx.toFloat(), rowBottom))
        assertTrue(signIn.has(palette.accent, right, 0f, widthPx.toFloat(), rowBottom))
    }

    @Test
    fun `a week header with a note draws no range`() {
        val note = HeaderNote("Stand gestern 18:30", accent = false)
        val one = chrome.weekHeader("Diese Woche", "9.–15. Nov.", null, widthPx, scale, palette, note)
        val other = chrome.weekHeader("Diese Woche", "30. Nov. – 6. Dez.", null, widthPx, scale, palette, note)
        assertTrue(one.sameAs(other))
        assertFalse(one.sameAs(chrome.weekHeader("Diese Woche", "9.–15. Nov.", null, widthPx, scale, palette)))
    }

    @Test
    fun `a note wider than its room is cut and never starts left of the header`() {
        val (shown, x) = chrome.atRight("Stand gestern 18:30", scale.px(40f), scale)
        assertTrue(shown, shown.endsWith("…") && shown.length < "Stand gestern 18:30".length)
        assertTrue("$x", x >= 0f)
    }
}
