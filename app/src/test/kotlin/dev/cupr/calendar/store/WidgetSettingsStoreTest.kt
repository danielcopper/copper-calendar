package dev.cupr.calendar.store

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.week.WeekDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class WidgetSettingsStoreTest {
    private val prefs =
        ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val store = WidgetSettingsStore(prefs)

    private val changed =
        WidgetSettings(
            leftOut = setOf("calendar.contact_birthdays", "special"),
            days = WeekDays.FROM_TODAY,
            backToToday = BackToToday.EVERY_30_MINUTES,
            plusCorner = PlusCorner.BOTTOM_LEFT,
            taps =
                TapTargets(
                    header = TapTarget.App("com.example.app"),
                    day = TapTarget.None,
                    entry = TapTarget.EntryDay,
                    new = TapTarget.None,
                ),
            opacity = 30,
        )

    @Test
    fun `a widget without stored settings has the defaults`() {
        assertEquals(
            WidgetSettings(emptySet(), WeekDays.MONDAY_TO_SUNDAY, BackToToday.EACH_UPDATE, PlusCorner.BOTTOM_RIGHT),
            store.read(7),
        )
        assertEquals(WidgetSettings(), WidgetSettings.DEFAULT)
        val calendar = TapTarget.CalendarPage
        assertEquals(TapTargets(calendar, calendar, calendar, calendar), store.read(7).taps)
        assertEquals(100, store.read(7).opacity)
    }

    @Test
    fun `every opacity from 0 to 100 in steps of 10 reads back as it was written, in percent`() {
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100), CardOpacity.steps)
        for (opacity in CardOpacity.steps) {
            store.write(7, WidgetSettings(opacity = opacity))
            assertEquals(opacity, store.read(7).opacity)
            assertEquals(opacity.toString(), prefs.getString("7.opacity", null))
        }
    }

    @Test
    fun `an opacity that is no number or no step of 10 from 0 to 100 reads as full, and is logged`() {
        for (stored in listOf("55", "110", "-10", "halb", "", "50 %")) {
            prefs.edit { putString("7.opacity", stored) }
            ShadowLog.clear()

            assertEquals(stored, WidgetSettings.DEFAULT, store.read(7))
            val line =
                "Widget 7: its stored opacity \"$stored\" is no opacity from 0 to 100 in steps of 10; it reads as 100"
            assertEquals(listOf(line), ShadowLog.getLogsForTag("WidgetSettings").map { it.msg })
        }
    }

    @Test
    fun `settings hold only an opacity from 0 to 100 in steps of 10`() {
        for (opacity in listOf(-10, 5, 110)) {
            assertThrows(IllegalArgumentException::class.java) { WidgetSettings(opacity = opacity) }
        }
    }

    @Test
    fun `settings read back as they were written, for their widget alone`() {
        store.write(7, changed)

        assertEquals(changed, store.read(7))
        assertEquals(WidgetSettings.DEFAULT, store.read(8))
    }

    @Test
    fun `every choice reads back as it was written, under its own fixed name`() {
        for (days in WeekDays.entries) {
            store.write(7, WidgetSettings(days = days))
            assertEquals(days, store.read(7).days)
            assertEquals(days.stored, prefs.getString("7.days", null))
        }
        for (back in BackToToday.entries) {
            store.write(7, WidgetSettings(backToToday = back))
            assertEquals(back, store.read(7).backToToday)
            assertEquals(back.stored, prefs.getString("7.back_to_today", null))
        }
        for (corner in PlusCorner.entries) {
            store.write(7, WidgetSettings(plusCorner = corner))
            assertEquals(corner, store.read(7).plusCorner)
            assertEquals(corner.stored, prefs.getString("7.plus_corner", null))
        }
        assertEquals(listOf("monday_to_sunday", "from_today"), WeekDays.entries.map { it.stored })
        assertEquals(
            listOf("each_update", "midnight", "never", "every_15", "every_30", "every_60"),
            BackToToday.entries.map { it.stored },
        )
        assertEquals(listOf("bottom_right", "bottom_left"), PlusCorner.entries.map { it.stored })
    }

    @Test
    fun `every element's every target reads back as it was written, under its own fixed name`() {
        val app = TapTarget.App("com.example.app")
        val parts = listOf(TapTarget.CalendarPage, app, TapTarget.None)
        for (target in parts) {
            val taps = TapTargets(header = target, day = target, new = target)
            store.write(7, WidgetSettings(taps = taps))
            assertEquals(taps, store.read(7).taps)
        }
        for (target in listOf(TapTarget.CalendarPage, TapTarget.EntryDay, TapTarget.AsDay)) {
            store.write(7, WidgetSettings(taps = TapTargets(entry = target)))
            assertEquals(TapTargets(entry = target), store.read(7).taps)
        }
        store.write(7, changed)
        assertEquals(
            listOf("app:com.example.app", "nothing", "day", "nothing"),
            listOf("header", "day", "entry", "new").map { prefs.getString("7.tap_$it", null) },
        )
        assertEquals(listOf("calendar", "app:com.example.app", "nothing"), parts.map { it.stored })
        assertEquals(listOf("day", "as_day"), listOf(TapTarget.EntryDay, TapTarget.AsDay).map { it.stored })
    }

    @Test
    fun `a target this app does not know, or that its element cannot have, reads as its default, and is logged`() {
        prefs.edit {
            putString("7.tap_header", "day")
            putString("7.tap_day", "app:")
            putString("7.tap_entry", "nothing")
            putString("7.tap_new", "Nothing")
        }

        assertEquals(TapTargets.DEFAULT, store.read(7).taps)
        val logs = ShadowLog.getLogsForTag("WidgetSettings").joinToString { it.msg }
        val stored = listOf("header" to "day", "day" to "app:", "entry" to "nothing", "new" to "Nothing")
        for ((field, name) in stored) {
            val line =
                "Widget 7: its stored tap_$field \"$name\" is unknown or not one this element can have; " +
                    "it reads as calendar"
            assertTrue(logs, logs.contains(line))
        }
    }

    @Test
    fun `writing again replaces the calendars left out, so one ticked again shows`() {
        store.write(7, changed)

        store.write(7, changed.copy(leftOut = setOf("special")))

        assertEquals(setOf("special"), store.read(7).leftOut)
    }

    @Test
    fun `a choice this app does not know reads as its default, and is logged`() {
        prefs.edit {
            putString("7.days", "FROM_TODAY")
            putString("7.back_to_today", "every_5")
            putString("7.plus_corner", "top_left")
        }

        assertEquals(WidgetSettings.DEFAULT, store.read(7))
        val logs = ShadowLog.getLogsForTag("WidgetSettings").joinToString { it.msg }
        assertTrue(
            logs,
            logs.contains("Widget 7: its stored days \"FROM_TODAY\" is unknown; it reads as monday_to_sunday"),
        )
        assertTrue(
            logs,
            logs.contains("Widget 7: its stored back_to_today \"every_5\" is unknown; it reads as each_update"),
        )
        assertTrue(
            logs,
            logs.contains("Widget 7: its stored plus_corner \"top_left\" is unknown; it reads as bottom_right"),
        )
    }

    @Test
    fun `removed widgets lose their settings, the others keep theirs`() {
        store.write(7, changed)
        store.write(8, changed)
        store.write(9, changed)

        store.remove(intArrayOf(7, 9))

        assertEquals(WidgetSettings.DEFAULT, store.read(7))
        assertEquals(changed, store.read(8))
        assertEquals(
            setOf(
                "8.left_out",
                "8.days",
                "8.back_to_today",
                "8.plus_corner",
                "8.opacity",
                "8.tap_header",
                "8.tap_day",
                "8.tap_entry",
                "8.tap_new",
            ),
            prefs.all.keys,
        )
    }
}
