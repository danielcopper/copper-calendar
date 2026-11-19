package dev.cupr.calendar.setup

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.Snapshot
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.widget.MonthWidgetProvider
import dev.cupr.calendar.widget.SampleContent
import dev.cupr.calendar.widget.WeekColumnsWidgetProvider
import dev.cupr.calendar.widget.WeekWidget
import dev.cupr.calendar.widget.WeekWidgetProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/**
 * A widget's setup as the person sees and uses it: its sections for the
 * widget's kind, the calendars it lists, and what "Speichern" and going
 * back leave stored.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val sampleContent = SampleContent()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var io: CoroutineDispatcher
    private val opened = mutableListOf<ActivityController<SetupActivity>>()

    @Before
    fun setUp() {
        // The screen's reads and its save run at once, so the test sees their results without waiting.
        io = Services.io
        Services.io = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        for (controller in opened) controller.pause().stop().destroy()
        Services.io = io
    }

    @Test
    fun `the month's setup has its calendars, when it returns to today and the corner of plus`() {
        open(MonthWidgetProvider::class.java)

        assertSections(R.string.widget_month_label, days = false, backToToday = true, entryTaps = false)
    }

    @Test
    fun `the week's setup has its calendars, its days, when it returns to today and the corner of plus`() {
        open(WeekWidgetProvider::class.java)

        assertSections(R.string.widget_week_label, days = true, backToToday = true, entryTaps = true)
    }

    @Test
    fun `the week across's setup has its calendars, its days and the corner of plus`() {
        open(WeekColumnsWidgetProvider::class.java)

        assertSections(R.string.widget_week_columns_label, days = true, backToToday = false, entryTaps = false)
    }

    @Test
    fun `saving stores exactly the values shown, redraws that widget and ends with RESULT_OK`() {
        writeSnapshot()
        val activity = open(WeekWidgetProvider::class.java).get()
        ShadowLog.clear()

        tap("Ferien").assertIsOff()
        tap(R.string.setup_days_from_today).assertIsSelected()
        tap(R.string.setup_back_midnight).assertIsSelected()
        tap(R.string.setup_plus_bottom_left).assertIsSelected()
        node(R.string.setup_plus_bottom_right).assertIsNotSelected()
        node("Arbeit").assertIsOn()
        tap(R.string.setup_save)

        val saved = WidgetSettings(setOf(HOLIDAYS), WeekDays.FROM_TODAY, BackToToday.MIDNIGHT, PlusCorner.BOTTOM_LEFT)
        assertEquals(saved, Settings.widgets(context).read(ID))
        // From today, today is the first row; from Monday, the sample's Wednesday would be the third.
        val drawn = ShadowLog.getLogsForTag(WeekWidget.TAG).map { it.msg }.single { it.startsWith("Widget $ID: drew ") }
        assertTrue(drawn, drawn.contains("; today is row 0, "))
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(ID, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun `the setup shows the widget's stored settings, and saving after ticking all again keeps its choices`() {
        writeSnapshot()
        val stored =
            WidgetSettings(setOf(HOLIDAYS, GONE), WeekDays.FROM_TODAY, BackToToday.NEVER, PlusCorner.BOTTOM_LEFT)
        Settings.widgets(context).write(ID, stored)
        val activity = open(WeekWidgetProvider::class.java).get()

        node("Ferien").assertIsOff()
        node("Arbeit").assertIsOn()
        // Left out, but no calendar of the sensor: listed by its id, so it can be ticked again.
        node(GONE).assertIsOff()
        node(R.string.setup_days_from_today).assertIsSelected()
        node(R.string.setup_back_never).assertIsSelected()
        node(R.string.setup_plus_bottom_left).assertIsSelected()
        tap("Ferien")
        tap(GONE)
        tap(R.string.setup_save)

        assertEquals(stored.copy(leftOut = emptySet()), Settings.widgets(context).read(ID))
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test
    fun `the settings shown outlive a configuration change, and saving after it stores them`() {
        writeSnapshot()
        val controller = open(WeekWidgetProvider::class.java)
        tap(R.string.setup_days_from_today)
        tap("Ferien")

        controller.recreate()
        compose.waitForIdle()

        node(R.string.setup_days_from_today).assertIsSelected()
        node("Ferien").assertIsOff()
        tap(R.string.setup_save)
        assertEquals(
            WidgetSettings(setOf(HOLIDAYS), WeekDays.FROM_TODAY),
            Settings.widgets(context).read(ID),
        )
    }

    @Test
    fun `saving with nothing stored and no calendar known stores the defaults and ends with RESULT_OK`() {
        val activity = open(WeekWidgetProvider::class.java).get()

        tap(R.string.setup_save)

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(WidgetSettings.DEFAULT, Settings.widgets(context).read(ID))
    }

    @Test
    fun `going back without saving keeps the stored settings and redraws nothing`() {
        writeSnapshot()
        val stored =
            WidgetSettings(setOf(WORK), WeekDays.MONDAY_TO_SUNDAY, BackToToday.MIDNIGHT, PlusCorner.BOTTOM_LEFT)
        Settings.widgets(context).write(ID, stored)
        val activity = open(WeekWidgetProvider::class.java).get()
        ShadowLog.clear()
        tap("Ferien")
        tap(R.string.setup_days_from_today)
        tap(R.string.setup_plus_bottom_right)

        activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals(stored, Settings.widgets(context).read(ID))
        assertEquals(emptyList<String>(), ShadowLog.getLogsForTag(WeekWidget.TAG).map { it.msg })
    }

    @Test
    fun `each element's target is picked in its dialog, another app from a list by name, and saving stores them`() {
        installLauncherApp(ZEBRA, "Zebra")
        installLauncherApp(ALPHA, "Alpha")
        open(WeekWidgetProvider::class.java)

        tap(R.string.setup_tap_new)
        option(R.string.setup_tap_calendar_new).assertIsSelected()
        press(R.string.setup_tap_nothing_new)
        tap(R.string.setup_tap_entry)
        node(R.string.setup_tap_other_app).assertDoesNotExist()
        press(R.string.setup_tap_entry_day)
        tap(R.string.setup_tap_day)
        press(R.string.setup_tap_other_app)
        assertTrue(top("Alpha") < top(context.getString(R.string.app_name)))
        assertTrue(top(context.getString(R.string.app_name)) < top("Zebra"))
        press("Alpha")
        tap(R.string.setup_tap_header)
        press(R.string.setup_tap_nothing)

        // Each row names its target now: no dialog is open.
        node(R.string.setup_tap_nothing_new).assertExists()
        node(R.string.setup_tap_entry_day).assertExists()
        node("Alpha").assertExists()
        node(R.string.setup_tap_nothing).assertExists()
        node(R.string.setup_tap_calendar).assertDoesNotExist()
        tap(R.string.setup_save)
        assertEquals(
            TapTargets(TapTarget.None, TapTarget.App(ALPHA), TapTarget.EntryDay, TapTarget.None),
            Settings.widgets(context).read(ID).taps,
        )
    }

    @Test
    fun `a stored app shows by its name, one no longer there as not found, and cancelling keeps them`() {
        installLauncherApp(ALPHA, "Alpha")
        val taps = TapTargets(header = TapTarget.App(ALPHA), new = TapTarget.App("com.example.gone"))
        Settings.widgets(context).write(ID, WidgetSettings(taps = taps))
        open(MonthWidgetProvider::class.java)

        node("Alpha").assertExists()
        node(R.string.setup_tap_app_missing).assertExists()
        tap(R.string.setup_tap_header)
        option(R.string.setup_tap_other_app).assertIsSelected()
        press(R.string.setup_cancel)
        tap(R.string.setup_tap_new)
        press(R.string.setup_tap_other_app)
        press(R.string.setup_cancel)

        node(R.string.setup_cancel).assertDoesNotExist()
        tap(R.string.setup_save)
        assertEquals(taps, Settings.widgets(context).read(ID).taps)
    }

    @Test
    fun `without any calendar known, the setup says so`() {
        open(WeekWidgetProvider::class.java)

        node(R.string.setup_no_calendars).assertExists()
    }

    @Test
    fun `the sensor's calendars are listed by name, one without a name by its id, all ticked`() {
        writeSnapshot()

        open(MonthWidgetProvider::class.java)

        node("Arbeit").assertIsOn()
        node("Ferien").assertIsOn()
        node(SCHOOL).assertIsOn()
        compose.onNodeWithText(WORK).assertDoesNotExist()
        compose.onNodeWithText(HOLIDAYS).assertDoesNotExist()
        node(R.string.setup_no_calendars).assertDoesNotExist()
    }

    @Test
    fun `in the light, the setup is in the phone's light colours`() {
        open(WeekWidgetProvider::class.java)

        val light = dynamicLightColorScheme(context).surface.toArgb()
        assertNotEquals(dynamicDarkColorScheme(context).surface.toArgb(), light)
        assertEquals(light, background())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in the dark, the setup is in the phone's dark colours`() {
        open(WeekWidgetProvider::class.java)

        assertEquals(dynamicDarkColorScheme(context).surface.toArgb(), background())
    }

    /**
     * The title names the kind; a section shows its heading exactly when the
     * kind has it, and "Tippen" a row for the header, a day and "+", and for
     * an entry exactly when the kind has entry areas, each on its default.
     */
    private fun assertSections(
        @StringRes kind: Int,
        days: Boolean,
        backToToday: Boolean,
        entryTaps: Boolean,
    ) {
        compose.onNodeWithText(context.getString(R.string.setup_title, context.getString(kind))).assertExists()
        node(R.string.setup_calendars).assertExists()
        node(R.string.setup_plus_corner).assertExists()
        node(R.string.setup_save).assertExists()
        node(R.string.setup_taps).assertExists()
        val shownWhen =
            listOf(
                R.string.setup_days to days,
                R.string.setup_back_to_today to backToToday,
                R.string.setup_tap_header to true,
                R.string.setup_tap_calendar to true,
                R.string.setup_tap_day to true,
                R.string.setup_tap_calendar_day to true,
                R.string.setup_tap_entry to entryTaps,
                R.string.setup_tap_calendar_entry to entryTaps,
                R.string.setup_tap_new to true,
                R.string.setup_tap_calendar_new to true,
            )
        for ((text, shown) in shownWhen) {
            if (shown) node(text).assertExists() else node(text).assertDoesNotExist()
        }
    }

    /** The colour of the screen's corner, where nothing is drawn on its surface. */
    private fun background(): Int {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        return image.getPixel(image.width - 1, image.height - 1)
    }

    /** A snapshot whose sensor lists two calendars by name and one without. */
    private fun writeSnapshot() {
        val calendars =
            buildJsonArray {
                addJsonObject {
                    put("id", WORK)
                    put("name", "Arbeit")
                }
                addJsonObject {
                    put("id", HOLIDAYS)
                    put("name", "Ferien")
                }
                addJsonObject { put("id", SCHOOL) }
            }
        Services.snapshots(context).write(Snapshot(0, "Test", emptyList(), calendars = calendars))
    }

    private fun node(text: String) = compose.onNodeWithText(text)

    private fun node(
        @StringRes text: Int,
    ) = node(context.getString(text))

    private fun tap(text: String) = node(text).performScrollTo().performClick()

    private fun tap(
        @StringRes text: Int,
    ) = tap(context.getString(text))

    /** Clicks text in a dialog, which does not scroll. */
    private fun press(text: String) = node(text).performClick()

    private fun press(
        @StringRes text: Int,
    ) = press(context.getString(text))

    /** A dialog's option, apart from a row of the screen that names the same target. */
    private fun option(
        @StringRes text: Int,
    ) = compose.onNode(hasText(context.getString(text)) and isSelectable())

    private fun top(text: String) = node(text).fetchSemanticsNode().boundsInRoot.top

    /** Installs an app with a launcher icon named name, as the setup's list of apps sees it. */
    private fun installLauncherApp(
        packageName: String,
        name: String,
    ) {
        val activity =
            ActivityInfo().apply {
                this.packageName = packageName
                this.name = "$packageName.Main"
                nonLocalizedLabel = name
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        val packageManager = shadowOf(context.packageManager)
        packageManager.addOrUpdateActivity(activity)
        packageManager.addIntentFilterForActivity(
            ComponentName(packageName, activity.name),
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    /** Places a widget of provider under [ID], as the launcher binds it, and opens its setup as the launcher does. */
    private fun open(provider: Class<out AppWidgetProvider>): ActivityController<SetupActivity> {
        val info = AppWidgetProviderInfo()
        info.provider = ComponentName(context, provider)
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(ID, info)
        val intent = Intent(context, SetupActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ID)
        val controller = Robolectric.buildActivity(SetupActivity::class.java, intent).setup()
        opened += controller
        compose.waitForIdle()
        return controller
    }

    private companion object {
        const val ID = 21
        const val WORK = "calendar.work"
        const val HOLIDAYS = "calendar.holidays"
        const val SCHOOL = "calendar.school"

        /** A calendar left out that the sensor no longer lists. */
        const val GONE = "calendar.gone"

        const val ALPHA = "com.example.alpha"
        const val ZEBRA = "com.example.zebra"
    }
}
