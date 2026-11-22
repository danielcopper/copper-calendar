package dev.cupr.calendar.setup

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Xml
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.ha.SensorCalendar
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.CardOpacity
import dev.cupr.calendar.store.ListState
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.store.WidgetSettingsStore
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.widget.MonthWidgetProvider
import dev.cupr.calendar.widget.SampleContent
import dev.cupr.calendar.widget.WeekColumnsWidgetProvider
import dev.cupr.calendar.widget.WeekWidget
import dev.cupr.calendar.widget.WeekWidgetProvider
import dev.cupr.calendar.widget.WidgetHost
import dev.cupr.calendar.widget.WidgetKind
import dev.cupr.calendar.widget.Widgets
import dev.cupr.calendar.widget.cardOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDateTime

/** A widget's setup: which widget it sets up, the calendars it lists, and its settings going with the widget. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupTest {
    @get:Rule
    val sampleContent = SampleContent()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(context)

    private fun entry(calendar: String) =
        Entry(
            start = LocalDateTime.of(2026, 11, 16, 0, 0),
            end = LocalDateTime.of(2026, 11, 17, 0, 0),
            allDay = true,
            title = "Eintrag",
            calendar = calendar,
        )

    @Test
    fun `each of the app's widgets is its kind, with the settings it has`() {
        val kinds =
            listOf(
                MonthWidgetProvider::class.java,
                WeekWidgetProvider::class.java,
                WeekColumnsWidgetProvider::class.java,
            ).map { WidgetKind.of(context, ComponentName(context, it)) }

        assertEquals(listOf(WidgetKind.MONTH, WidgetKind.WEEK, WidgetKind.WEEK_COLUMNS), kinds)
        assertEquals(listOf(false, true, true), kinds.map { it?.days })
        assertEquals(listOf(true, true, false), kinds.map { it?.backToToday })
        assertEquals(listOf(false, true, false), kinds.map { it?.entryTaps })
    }

    @Test
    fun `each provider's widgets are of the provider's kind`() {
        for (provider in Widgets.providers) {
            assertEquals(provider.kind, WidgetKind.of(context, ComponentName(context, provider.javaClass)))
        }
        assertEquals(WidgetKind.entries, Widgets.providers.map { it.kind })
    }

    @Test
    fun `a provider of another app or another class is no kind`() {
        assertNull(WidgetKind.of(context, ComponentName("other.app", MonthWidgetProvider::class.java.name)))
        assertNull(WidgetKind.of(context, ComponentName(context, AppWidgetProvider::class.java)))
    }

    @Test
    fun `without the sensor's calendars, the setup lists those seen and those left out by id, each once, sorted`() {
        val entries = listOf(entry("special"), entry("calendar.b"), entry("special"), entry("calendar.a"))

        assertEquals(
            listOf("calendar.a", "calendar.b", "calendar.gone", "special").map { SensorCalendar(it) },
            setupCalendars(null, entries, setOf("calendar.gone", "calendar.b")),
        )
        assertEquals(emptyList<SensorCalendar>(), setupCalendars(null, emptyList(), emptySet()))
    }

    @Test
    fun `with the sensor's calendars, the setup lists them by name in their order, then any other by id`() {
        val sensor = listOf(SensorCalendar("special", "Besondere Tage"), SensorCalendar("calendar.b", "B"))
        val entries = listOf(entry("calendar.z"), entry("calendar.b"), entry("calendar.c"))

        assertEquals(
            sensor +
                listOf(
                    SensorCalendar("calendar.c"),
                    SensorCalendar("calendar.gone"),
                    SensorCalendar("calendar.z"),
                ),
            setupCalendars(sensor, entries, setOf("calendar.gone", "special")),
        )
    }

    @Test
    fun `opened for a widget of this app, the setup shows`() {
        val id = place(WEEK_ID, WeekWidgetProvider::class.java)

        val activity = launch(id)

        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals(id, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun `opened without a widget, or for none of this app's, the setup finishes at once and logs it`() {
        val other = place(OTHER_ID, AppWidgetProvider::class.java)

        for (id in listOf(AppWidgetManager.INVALID_APPWIDGET_ID, other, other + 1)) {
            val activity = launch(id)

            assertTrue("widget $id", activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        }
        val logs = ShadowLog.getLogsForTag("Setup").joinToString { it.msg }
        assertTrue(logs, logs.contains("Setup opened for widget $other, which is none of this app's widgets"))
    }

    @Test
    fun `a widget removed from the home screen loses its settings and last scroll, the others keep theirs`() {
        val store: WidgetSettingsStore = Settings.widgets(context)
        val lists = Settings.lists(context)
        val left = WidgetSettings(leftOut = setOf("special"))
        val at = Instant.ofEpochMilli(1_000)
        for (id in listOf(3, 4)) {
            store.write(id, left)
            lists.keep(id, ListState.SETTLED, at)
        }

        WeekWidgetProvider().onDeleted(context, intArrayOf(3))

        assertEquals(WidgetSettings.DEFAULT, store.read(3))
        assertNull(lists.lastScroll(3))
        assertEquals(ListState.TO_BUILD, lists.state(3))
        assertEquals(left, store.read(4))
        assertEquals(at, lists.lastScroll(4))
        assertEquals(ListState.SETTLED, lists.state(4))
    }

    @Test
    fun `the settings shown are kept across a configuration change by their stored names`() {
        val scope = SaverScope { true }
        val save = { settings: WidgetSettings? -> with(SettingsSaver) { scope.save(settings) } }
        val all =
            WeekDays.entries.flatMap { days ->
                BackToToday.entries.flatMap { back ->
                    PlusCorner.entries.flatMap { corner ->
                        listOf(emptySet(), setOf("calendar.a", "special")).map { left ->
                            WidgetSettings(left, days, back, corner)
                        }
                    }
                }
            }

        val app = TapTarget.App("com.example.app")
        val taps =
            listOf(TapTarget.CalendarPage, app, TapTarget.None).map { TapTargets(it, it, TapTarget.CalendarPage, it) } +
                listOf(TapTarget.EntryDay, TapTarget.AsDay).map { TapTargets(entry = it) }
        val opacities = CardOpacity.steps.map { WidgetSettings(opacity = it) }
        for (settings in all + taps.map { WidgetSettings(taps = it) } + opacities) {
            assertEquals(settings, SettingsSaver.restore(checkNotNull(save(settings))))
        }
        val left =
            WidgetSettings(
                setOf("special"),
                WeekDays.FROM_TODAY,
                BackToToday.EVERY_30_MINUTES,
                PlusCorner.BOTTOM_LEFT,
                TapTargets(app, TapTarget.None, TapTarget.EntryDay, TapTarget.CalendarPage),
                opacity = cardOpacity(50),
            )
        val saved =
            listOf("from_today", "every_30", "bottom_left", "50") +
                listOf("app:com.example.app", "nothing", "day", "calendar", "special")
        assertEquals(saved, save(left))
        assertNull(save(null))
        val choices = listOf("from_today", "every_30", "bottom_left", "50")
        assertNull(SettingsSaver.restore(listOf("FROM_TODAY", "every_30", "bottom_left", "50") + saved.subList(4, 8)))
        // An opacity that is no step of 10 from 0 to 100, or no number.
        for (opacity in listOf("55", "110", "-10", "halb")) {
            val unreadable = listOf("from_today", "every_30", "bottom_left", opacity) + saved.subList(4, 8)
            assertNull(opacity, SettingsSaver.restore(unreadable))
        }
        assertNull(SettingsSaver.restore(choices))
        // An entry cannot have another app, nor the header an entry's day.
        assertNull(SettingsSaver.restore(choices + listOf("calendar", "calendar", "app:a", "calendar")))
        assertNull(SettingsSaver.restore(choices + listOf("day", "calendar", "calendar", "calendar")))
    }

    @Test
    fun `saving stores the settings and redraws that widget`() {
        val id = place(WEEK_ID, WeekWidgetProvider::class.java)
        val settings = WidgetSettings(leftOut = setOf("special"), days = WeekDays.FROM_TODAY)
        ShadowLog.clear()

        assertTrue(save(context, id, WidgetKind.WEEK, settings))

        assertEquals(settings, Settings.widgets(context).read(id))
        // From today, today is the first row; from Monday, the sample's Wednesday is the third.
        val drawn = ShadowLog.getLogsForTag(WeekWidget.TAG).map { it.msg }.single { it.startsWith("Widget $id: drew ") }
        assertTrue(drawn, drawn.contains("; today is row 0, "))
    }

    @Test
    fun `saving for a widget removed meanwhile keeps nothing, and the log says so`() {
        val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        assertFalse(save(context, OTHER_ID, WidgetKind.WEEK, WidgetSettings(leftOut = setOf("special"))))

        assertEquals(emptyList<String>(), prefs.all.keys.filter { it.startsWith("$OTHER_ID.") })
        val logs = ShadowLog.getLogsForTag("Setup").joinToString { it.msg }
        assertTrue(logs, logs.contains("Widget $OTHER_ID: saved, but no longer one of the app's widgets"))
    }

    @Test
    fun `the setup ends with RESULT_OK for its widget once saved, and without it when not`() {
        val id = place(WEEK_ID, WeekWidgetProvider::class.java)

        val saved = launch(id).apply { done(saved = true) }
        val gone = launch(id).apply { done(saved = false) }

        assertTrue(saved.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(saved).resultCode)
        assertEquals(id, shadowOf(saved).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertTrue(gone.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(gone).resultCode)
    }

    @Test
    fun `each widget names the setup as its configuration, which it may skip and open again`() {
        for (info in listOf(R.xml.widget_month_info, R.xml.widget_week_info, R.xml.widget_week_columns_info)) {
            val name = context.resources.getResourceEntryName(info)

            assertEquals(name, SetupActivity::class.java.name, providerString(info, android.R.attr.configure))
            assertEquals(
                name,
                AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE or
                    AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL,
                WidgetHost.providerAttribute(context, info, android.R.attr.widgetFeatures),
            )
        }
    }

    @Test
    fun `the launcher's configure intent opens the setup, which it may start`() {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setPackage(context.packageName)

        val found = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo }

        assertEquals(listOf(SetupActivity::class.java.name), found.map { it.name })
        assertTrue(found.single().exported)
    }

    /** A string attribute of a widget's provider info (res/xml). */
    private fun providerString(
        xml: Int,
        attribute: Int,
    ): String? {
        val parser = context.resources.getXml(xml)
        try {
            while (parser.next() != XmlPullParser.START_TAG) continue
            val array = context.obtainStyledAttributes(Xml.asAttributeSet(parser), intArrayOf(attribute))
            try {
                return array.getString(0)
            } finally {
                array.recycle()
            }
        } finally {
            parser.close()
        }
    }

    /** Places a widget of provider under id, as the launcher binds it. */
    private fun place(
        id: Int,
        provider: Class<out AppWidgetProvider>,
    ): Int {
        val info = AppWidgetProviderInfo()
        info.provider = ComponentName(context, provider)
        shadowOf(manager).addBoundWidget(id, info)
        return id
    }

    private fun launch(id: Int): SetupActivity =
        Robolectric
            .buildActivity(
                SetupActivity::class.java,
                Intent(context, SetupActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            ).create()
            .get()

    private companion object {
        const val WEEK_ID = 11
        const val OTHER_ID = 12
    }
}
