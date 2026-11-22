package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.store.CardOpacity
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.store.cardOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import kotlin.math.roundToInt

/**
 * The card's opacity per widget (D64): only the card, its fill and border
 * together, in every layout, the colours unchanged, and no card at 0 %.
 * The views are applied as a launcher applies them, so the calls they carry
 * are ones a widget's views may make.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardOpacityTest {
    @get:Rule
    val sampleContent = SampleContent()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(context)

    /** Each layout with a card, drawn at an opacity: the three widgets and the plain text. */
    private val layouts: Map<String, (Int) -> (Context, Int) -> RemoteViews> =
        mapOf(
            "month" to { opacity -> { c, width -> MonthWidget.views(c, width, opacity = opacity) } },
            "week" to { opacity -> { c, width -> WeekWidget.views(c, width, opacity = opacity) } },
            "week across" to { opacity -> { c, width -> WeekColumnsWidget.views(c, width, opacity = opacity) } },
            "plain" to { opacity ->
                { c, _ -> DrawnWidget.plainViews(c, R.string.widget_failed, opacity = cardOpacity(opacity)) }
            },
        )

    @Test
    fun `each layout's card takes the opacity, at 0 percent none, its colours unchanged`() {
        for ((name, views) in layouts) {
            for ((opacity, alpha) in listOf(0 to 0, 50 to 128, 100 to 255)) {
                val widget = WidgetHost.applied(context, views = views(opacity))
                assertCard("$name at $opacity %", widget, alpha)
            }
        }
    }

    @Test
    fun `the card lies below everything else, and nothing but it takes the opacity`() {
        for ((name, views) in layouts) {
            val root = WidgetHost.applied(context, views = views(50)) as ViewGroup
            assertSame(name, root.findViewById<View>(R.id.card), root.getChildAt(0))
            assertNull(name, root.background)
            for (image in listOf(R.id.header_light, R.id.header_dark, R.id.fab_light, R.id.fab_dark)) {
                root.findViewById<ImageView>(image)?.let { assertEquals("$name ${it.id}", 255, it.imageAlpha) }
            }
            assertEquals(name, 1f, root.alpha)
        }
    }

    @Test
    fun `drawn at 50 percent, the card's fill and border are their colours at half their alpha`() {
        assertDrawnCard(SampleData.light.card, SampleData.light.cardBorder)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode, drawn at 50 percent, the card's fill and border are the dark ones at half their alpha`() {
        assertDrawnCard(SampleData.dark.card, SampleData.dark.cardBorder)
    }

    @Test
    fun `a redraw sets the opacity again, so the launcher keeps no earlier one`() {
        for ((name, views) in layouts) {
            val redraws = listOf(Triple(50, 100, 255), Triple(0, 50, 128), Triple(100, 0, 0), Triple(30, 30, 77))
            for ((before, after, alpha) in redraws) {
                val widget = WidgetHost.applied(context, views = views(before))
                WidgetHost.reapplied(context, widget, views = views(after))
                assertCard("$name from $before to $after %", widget, alpha)
            }
        }
    }

    @Test
    fun `the fade of the week across shows only with the whole card, and below it the row ends at the list's edge`() {
        val columns = { opacity: Int, heightDp: Int ->
            { c: Context, width: Int -> WeekColumnsWidget.views(c, width, heightDp, opacity = opacity) }
        }
        // As tall as its list's content, without the fade's blank room: the row of a widget that holds it whole.
        val unfaded = rowHeight(columns(100, WidgetHost.HEIGHT_DP))
        assertEquals(View.VISIBLE, fade(columns(100, WidgetHost.LOW_HEIGHT_DP)).visibility)
        assertTrue(rowHeight(columns(100, WidgetHost.LOW_HEIGHT_DP)) > unfaded)
        for (opacity in listOf(90, 50, 0)) {
            assertEquals("$opacity %", View.GONE, fade(columns(opacity, WidgetHost.LOW_HEIGHT_DP)).visibility)
            assertEquals("$opacity %", unfaded, rowHeight(columns(opacity, WidgetHost.LOW_HEIGHT_DP)))
        }
        // A redraw below 100 % hides the fade an earlier one showed, and one at 100 % shows it again.
        val widget = WidgetHost.applied(context, views = columns(100, WidgetHost.LOW_HEIGHT_DP))
        WidgetHost.reapplied(context, widget, WidgetHost.LOW_HEIGHT_DP, columns(50, WidgetHost.LOW_HEIGHT_DP))
        assertEquals(View.GONE, widget.findViewById<View>(R.id.fade).visibility)
        WidgetHost.reapplied(context, widget, WidgetHost.LOW_HEIGHT_DP, columns(100, WidgetHost.LOW_HEIGHT_DP))
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.fade).visibility)
    }

    @Test
    fun `each step's card is drawn at its share of 255, rounded half up`() {
        val alphas = listOf(0, 26, 51, 77, 102, 128, 153, 179, 204, 230, 255)
        for ((opacity, alpha) in CardOpacity.steps.zip(alphas)) {
            val views = { c: Context, _: Int -> DrawnWidget.plainViews(c, R.string.widget_failed, opacity = opacity) }
            val card = WidgetHost.applied(context, views = views).findViewById<ImageView>(R.id.card)
            assertEquals("${opacity.percent} %", alpha, card.imageAlpha)
            assertEquals("${opacity.percent} %", if (alpha == 0) View.GONE else View.VISIBLE, card.visibility)
        }
    }

    @Test
    fun `below 100 percent the week across counts no fade's room against the bitmap limit`() {
        val sample = SampleData.calendar()
        val draw = { opacity: Int, limit: Long ->
            WeekColumnsWidget.draw(
                context,
                WidgetHost.WIDTH_DP,
                WidgetHost.LOW_HEIGHT_DP,
                Look(opacity = cardOpacity(opacity)),
                7,
                limit,
                sample,
            )
        }
        for (opacity in listOf(90, 50, 0)) {
            // Exactly what its bitmaps take without the fade's room: the whole week fits, nothing left out.
            val bytes = draw(opacity, Long.MAX_VALUE).bytes
            ShadowLog.clear()

            assertEquals("$opacity %", bytes, draw(opacity, bytes).bytes)
            assertEquals("$opacity %", emptyList<String>(), warnings())
        }
        // On the whole card the fade's room counts: at the bytes drawn without it, the week passes the limit
        // and an entry is left out.
        val unfaded = draw(50, Long.MAX_VALUE).bytes
        ShadowLog.clear()
        draw(100, unfaded)
        assertTrue(warnings().single().startsWith("Widget 7: "))
    }

    private fun warnings() =
        ShadowLog.getLogsForTag(WeekColumnsWidget.TAG).filter { it.type == Log.WARN }.map { it.msg }

    private fun fade(views: (Context, Int) -> RemoteViews): View =
        WidgetHost.applied(context, views = views).findViewById(R.id.fade)

    /** The height in px of the drawn row of "Woche quer" in views. */
    private fun rowHeight(views: (Context, Int) -> RemoteViews): Int {
        val list = WidgetHost.applied(context, views = views).findViewById<ListView>(R.id.list)
        val image = list.adapter.getView(0, null, list).findViewById<ImageView>(R.id.week_light)
        return (image.drawable as BitmapDrawable).bitmap.height
    }

    @Test
    fun `each provider draws its widget's card at the opacity stored for it`() {
        val providers =
            listOf(
                MonthWidgetProvider() to R.layout.widget_month,
                WeekWidgetProvider() to R.layout.widget_week,
                WeekColumnsWidgetProvider() to R.layout.widget_week_columns,
            )
        for ((provider, layout) in providers) {
            val id = shadowOf(manager).createWidget(provider.javaClass, layout)
            Settings.widgets(context).write(id, WidgetSettings(opacity = cardOpacity(40)))
            val other = shadowOf(manager).createWidget(provider.javaClass, layout)

            provider.onUpdate(context, manager, intArrayOf(id, other))

            assertCard("${provider.kind}", shadowOf(manager).getViewFor(id), 102)
            assertCard("${provider.kind}, without settings", shadowOf(manager).getViewFor(other), 255)
        }
    }

    @Test
    fun `the plain texts without data, failed or too full, stand on the card at the widget's opacity`() {
        val id = shadowOf(manager).createWidget(AppWidgetProvider::class.java, R.layout.widget_plain)
        Settings.widgets(context).write(id, WidgetSettings(opacity = cardOpacity(20)))

        DrawnWidgetProvider.content = { Content.NoData(R.string.widget_signed_out) }
        MonthWidgetProvider().onUpdate(context, manager, intArrayOf(id))
        assertCard("no data", shadowOf(manager).getViewFor(id), 51)

        DrawnWidgetProvider.content = { SampleData.calendar() }
        val failing =
            object : DrawnWidgetProvider() {
                override val kind = WidgetKind.MONTH
                override val tag = "Failing"

                override fun update(
                    context: Context,
                    manager: AppWidgetManager,
                    id: Int,
                    calendar: Content.Calendar,
                    look: Look,
                ): Boolean = error("failed")
            }
        failing.onUpdate(context, manager, intArrayOf(id))
        assertCard("failed", shadowOf(manager).getViewFor(id), 51)

        val pushed = mutableListOf<RemoteViews>()
        val drawn = Drawn(RemoteViews(context.packageName, R.layout.widget_week), 42, 41)
        DrawnWidget.push(context, id, drawn, R.string.widget_week_too_full, cardOpacity(20), "Test") {
            pushed += it
            if (pushed.size == 1) throw IllegalArgumentException("RemoteViews for widget update exceeds")
        }
        assertCard("too full", WidgetHost.applied(context, views = { _, _ -> pushed[1] }), 51)
    }

    /** The widget's card shows at alpha, its colours its own; at 0 it shows not at all. */
    private fun assertCard(
        what: String,
        widget: View,
        alpha: Int,
    ) {
        val card = widget.findViewById<ImageView>(R.id.card)
        assertEquals(what, if (alpha == 0) View.GONE else View.VISIBLE, card.visibility)
        if (alpha == 0) return
        assertEquals(what, alpha, card.imageAlpha)
        assertEquals(what, ImageView.ScaleType.FIT_XY, card.scaleType)
        val shape = card.drawable as GradientDrawable
        assertEquals(what, context.getColor(R.color.widget_card), shape.color?.defaultColor)
    }

    /**
     * The month widget drawn at 50 %: the card's fill, in the padding at its
     * left, and its border, at its left edge, are their colours at half
     * alpha; and at 0 % there is nothing there.
     */
    private fun assertDrawnCard(
        fill: Int,
        border: Int,
    ) {
        val scale = Scale(context.resources.displayMetrics.density)
        val half = drawn(50)
        val middle = half.height / 2
        assertArgb("fill", fill, 128, half.getPixel(scale.px(6f).roundToInt(), middle))
        assertArgb("border", border, 128, half.getPixel(0, middle))
        val full = drawn(100)
        assertArgb("full fill", fill, 255, full.getPixel(scale.px(6f).roundToInt(), middle))
        assertArgb("full border", border, 255, full.getPixel(0, middle))
        val none = drawn(0)
        assertEquals(0, Color.alpha(none.getPixel(scale.px(6f).roundToInt(), middle)))
        assertEquals(0, Color.alpha(none.getPixel(0, middle)))
    }

    private fun drawn(opacity: Int): Bitmap {
        val widget =
            WidgetHost.laidOut(context, views = { c, width -> MonthWidget.views(c, width, opacity = opacity) })
        val bitmap = Bitmap.createBitmap(widget.width, widget.height, Bitmap.Config.ARGB_8888)
        widget.draw(Canvas(bitmap))
        return bitmap
    }

    /** pixel is colour at alpha, give or take one step of rounding in the blend. */
    private fun assertArgb(
        what: String,
        colour: Int,
        alpha: Int,
        pixel: Int,
    ) {
        assertEquals(what, alpha.toFloat(), Color.alpha(pixel).toFloat(), 1f)
        for (channel in listOf(Color::red, Color::green, Color::blue)) {
            assertEquals(what, channel(colour).toFloat(), channel(pixel).toFloat(), 2f)
        }
    }
}
