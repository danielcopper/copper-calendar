package dev.cupr.calendar.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Palettes
import dev.cupr.calendar.render.Probe.has
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Each widget draws the calendar's notice into its header and its parts with
 * the calendar's palettes, the light ones into its light views and the dark
 * ones into its dark views (D48, D51, D52).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoticeAndPalettesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    /** Colours no built-in palette has, one accent and one text colour per mode. */
    private val palettes =
        Palettes(
            light = Palettes.BUILT_IN.light.copy(accent = LIGHT_ACCENT, text = LIGHT_TEXT),
            dark = Palettes.BUILT_IN.dark.copy(accent = DARK_ACCENT, text = DARK_TEXT),
        )

    /** A widget kind: its views of a calendar, and its rows' light and dark drawings. */
    private class Kind(
        val name: String,
        val views: (Application, Content.Calendar) -> RemoteViews,
        val rowLight: Int,
        val rowDark: Int,
    )

    private val kinds =
        listOf(
            Kind(
                "Monat",
                { c, calendar -> MonthWidget.views(c, WidgetHost.WIDTH_DP, calendar = calendar) },
                R.id.week_light,
                R.id.week_dark,
            ),
            Kind(
                "Woche",
                { c, calendar -> WeekWidget.views(c, WidgetHost.WIDTH_DP, calendar = calendar) },
                R.id.day_light,
                R.id.day_dark,
            ),
            Kind(
                "Woche quer",
                { c, calendar -> WeekColumnsWidget.views(c, WidgetHost.WIDTH_DP, calendar = calendar) },
                R.id.week_light,
                R.id.week_dark,
            ),
        )

    private fun widget(
        kind: Kind,
        notice: Notice?,
    ): View {
        val calendar = SampleData.calendar().copy(palettes = palettes, notice = notice)
        return WidgetHost.laidOut(context, views = { c, _ -> kind.views(c as Application, calendar) })
    }

    private fun View.bitmap(id: Int): Bitmap = (findViewById<ImageView>(id).drawable as BitmapDrawable).bitmap

    private fun Bitmap.has(color: Int) = has(color, 0f, 0f, width.toFloat(), height.toFloat())

    @Test
    fun `the notice to sign in again is drawn into each header in the accent of its mode`() {
        for (kind in kinds) {
            val plain = widget(kind, null)
            val signIn = widget(kind, Notice.SignInAgain("Neu anmelden"))
            val stale = widget(kind, Notice.Stale("Stand 08:29"))

            assertFalse("${kind.name}: no notice", plain.bitmap(R.id.header_light).has(LIGHT_ACCENT))
            assertTrue("${kind.name}: light header", signIn.bitmap(R.id.header_light).has(LIGHT_ACCENT))
            assertFalse("${kind.name}: light header", signIn.bitmap(R.id.header_light).has(DARK_ACCENT))
            assertTrue("${kind.name}: dark header", signIn.bitmap(R.id.header_dark).has(DARK_ACCENT))
            assertFalse("${kind.name}: dark header", signIn.bitmap(R.id.header_dark).has(LIGHT_ACCENT))
            assertFalse("${kind.name}: stale", stale.bitmap(R.id.header_light).has(LIGHT_ACCENT))
            assertFalse("${kind.name}: stale", stale.bitmap(R.id.header_light).sameAs(plain.bitmap(R.id.header_light)))
        }
    }

    @Test
    fun `each widget draws its light views with the light palette and its dark views with the dark one`() {
        for (kind in kinds) {
            val widget = widget(kind, null)
            val list = widget.findViewById<ListView>(R.id.list)
            val rows = (0 until list.childCount).map { list.getChildAt(it) }
            val light = rows.map { it.bitmap(kind.rowLight) }
            val dark = rows.map { it.bitmap(kind.rowDark) }

            assertTrue("${kind.name}: light +", widget.bitmap(R.id.fab_light).has(LIGHT_ACCENT))
            assertFalse("${kind.name}: light +", widget.bitmap(R.id.fab_light).has(DARK_ACCENT))
            assertTrue("${kind.name}: dark +", widget.bitmap(R.id.fab_dark).has(DARK_ACCENT))
            assertFalse("${kind.name}: dark +", widget.bitmap(R.id.fab_dark).has(LIGHT_ACCENT))
            // Past days are drawn muted, so only some of the rows shown need to have the text colour.
            assertTrue("${kind.name}: light rows", light.any { it.has(LIGHT_TEXT) } && light.none { it.has(DARK_TEXT) })
            assertTrue("${kind.name}: dark rows", dark.any { it.has(DARK_TEXT) } && dark.none { it.has(LIGHT_TEXT) })
        }
    }

    private companion object {
        val LIGHT_ACCENT = 0xFFE0106A.toInt()
        val DARK_ACCENT = 0xFF10E06A.toInt()
        val LIGHT_TEXT = 0xFF6A10E0.toInt()
        val DARK_TEXT = 0xFFE06A10.toInt()
    }
}
