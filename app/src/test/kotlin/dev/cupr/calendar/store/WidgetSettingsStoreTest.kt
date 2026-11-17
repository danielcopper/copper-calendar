package dev.cupr.calendar.store

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.week.WeekDays
import org.junit.Assert.assertEquals
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
        )

    @Test
    fun `a widget without stored settings has the defaults`() {
        assertEquals(
            WidgetSettings(emptySet(), WeekDays.MONDAY_TO_SUNDAY, BackToToday.EACH_UPDATE, PlusCorner.BOTTOM_RIGHT),
            store.read(7),
        )
        assertEquals(WidgetSettings(), WidgetSettings.DEFAULT)
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
        assertEquals(setOf("8.left_out", "8.days", "8.back_to_today", "8.plus_corner"), prefs.all.keys)
    }
}
