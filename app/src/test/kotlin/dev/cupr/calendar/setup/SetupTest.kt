package dev.cupr.calendar.setup

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.ha.SensorCalendar
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.store.WidgetSettingsStore
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.widget.MonthWidgetProvider
import dev.cupr.calendar.widget.WeekColumnsWidgetProvider
import dev.cupr.calendar.widget.WeekWidgetProvider
import dev.cupr.calendar.widget.WidgetKind
import dev.cupr.calendar.widget.Widgets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import java.time.Instant
import java.time.LocalDateTime

/** A widget's setup: which widget it sets up, the calendars it lists, and its settings going with the widget. */
@RunWith(RobolectricTestRunner::class)
class SetupTest {
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
        val scrolls = Settings.lastScrolls(context)
        val left = WidgetSettings(leftOut = setOf("special"))
        val at = Instant.ofEpochMilli(1_000)
        for (id in listOf(3, 4)) {
            store.write(id, left)
            scrolls.write(id, at)
        }

        WeekWidgetProvider().onDeleted(context, intArrayOf(3))

        assertEquals(WidgetSettings.DEFAULT, store.read(3))
        assertNull(scrolls.read(3))
        assertEquals(left, store.read(4))
        assertEquals(at, scrolls.read(4))
    }

    @Test
    fun `the settings shown are kept across a configuration change by their stored names`() {
        val scope = SaverScope { true }
        val save = { settings: WidgetSettings? -> with(SettingsSaver) { scope.save(settings) } }
        val all =
            WeekDays.entries.flatMap { days ->
                BackToToday.entries.flatMap { back ->
                    PlusCorner.entries.flatMap { corner ->
                        listOf(emptySet(), setOf("calendar.a", "special")).map { WidgetSettings(it, days, back, corner) }
                    }
                }
            }

        for (settings in all) {
            assertEquals(settings, SettingsSaver.restore(checkNotNull(save(settings))))
        }
        assertEquals(
            listOf("from_today", "every_30", "bottom_left", "special"),
            save(WidgetSettings(setOf("special"), WeekDays.FROM_TODAY, BackToToday.EVERY_30_MINUTES, PlusCorner.BOTTOM_LEFT)),
        )
        assertNull(save(null))
        assertNull(SettingsSaver.restore(listOf("FROM_TODAY", "every_30", "bottom_left")))
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

    private fun launch(id: Int): Activity =
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
