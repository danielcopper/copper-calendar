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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.render.Probe
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.Snapshot
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.store.cardOpacity
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.widget.MonthWidgetProvider
import dev.cupr.calendar.widget.SampleContent
import dev.cupr.calendar.widget.WeekColumnsWidgetProvider
import dev.cupr.calendar.widget.WeekWidget
import dev.cupr.calendar.widget.WeekWidgetProvider
import dev.cupr.design.CopperColorScheme
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
import java.io.File
import kotlin.coroutines.CoroutineContext

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

        tap(R.string.setup_calendars)
        box("Ferien").performClick().assertIsOff()
        box("Arbeit").assertIsOn()
        press(R.string.setup_ok)
        choose(R.string.setup_days, R.string.setup_days_from_today)
        choose(R.string.setup_back_to_today, R.string.setup_back_midnight)
        choose(R.string.setup_plus_corner, R.string.setup_plus_bottom_left)
        assertRow(R.string.setup_calendars, "2 von 3")
        assertRow(R.string.setup_days, R.string.setup_days_from_today)
        assertRow(R.string.setup_back_to_today, R.string.setup_back_midnight)
        assertRow(R.string.setup_plus_corner, R.string.setup_plus_bottom_left)
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

        assertRow(R.string.setup_calendars, "2 von 4")
        assertRow(R.string.setup_days, R.string.setup_days_from_today)
        assertRow(R.string.setup_back_to_today, R.string.setup_back_never)
        assertRow(R.string.setup_plus_corner, R.string.setup_plus_bottom_left)
        tap(R.string.setup_days)
        option(R.string.setup_days_from_today).assertIsSelected()
        option(R.string.setup_days_monday_to_sunday).assertIsNotSelected()
        press(R.string.setup_cancel)
        tap(R.string.setup_calendars)
        box("Ferien").assertIsOff()
        box("Arbeit").assertIsOn()
        // Left out, but no calendar of the sensor: listed by its id, so it can be ticked again.
        box(GONE).assertIsOff()
        box("Ferien").performClick()
        box(GONE).performClick()
        press(R.string.setup_ok)
        assertRow(R.string.setup_calendars, R.string.setup_calendars_all)
        tap(R.string.setup_save)

        assertEquals(stored.copy(leftOut = emptySet()), Settings.widgets(context).read(ID))
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test
    fun `the calendars' boxes take effect with OK only, and a choice's dialog closes on a pick or Abbrechen`() {
        writeSnapshot()
        open(WeekWidgetProvider::class.java)
        assertRow(R.string.setup_calendars, R.string.setup_calendars_all)

        tap(R.string.setup_calendars)
        box("Ferien").performClick()
        press(R.string.setup_cancel)
        assertRow(R.string.setup_calendars, R.string.setup_calendars_all)
        tap(R.string.setup_calendars)
        box("Ferien").assertIsOn().performClick()
        press(R.string.setup_ok)
        assertRow(R.string.setup_calendars, "2 von 3")
        box("Ferien").assertDoesNotExist()
        tap(R.string.setup_back_to_today)
        press(R.string.setup_cancel)
        option(R.string.setup_back_each_update).assertDoesNotExist()
        choose(R.string.setup_back_to_today, R.string.setup_back_never)
        option(R.string.setup_back_each_update).assertDoesNotExist()
        tap(R.string.setup_save)

        assertEquals(
            WidgetSettings(setOf(HOLIDAYS), backToToday = BackToToday.NEVER),
            Settings.widgets(context).read(ID),
        )
    }

    @Test
    fun `the settings shown outlive a configuration change, and saving after it stores them`() {
        writeSnapshot()
        val controller = open(WeekWidgetProvider::class.java)
        choose(R.string.setup_days, R.string.setup_days_from_today)
        tap(R.string.setup_calendars)
        box("Ferien").performClick()
        press(R.string.setup_ok)
        slide(60f)

        controller.recreate()
        compose.waitForIdle()

        assertRow(R.string.setup_days, R.string.setup_days_from_today)
        assertRow(R.string.setup_calendars, "2 von 3")
        assertOpacity(60)
        tap(R.string.setup_save)
        assertEquals(
            WidgetSettings(setOf(HOLIDAYS), WeekDays.FROM_TODAY, opacity = cardOpacity(60)),
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
        tap(R.string.setup_calendars)
        box("Ferien").performClick()
        press(R.string.setup_ok)
        choose(R.string.setup_days, R.string.setup_days_from_today)
        choose(R.string.setup_plus_corner, R.string.setup_plus_bottom_right)

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
        node(R.string.setup_tap_nothing).assertDoesNotExist()
        option(R.string.setup_tap_as_day).assertExists()
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
    fun `a stored app's row shows nothing while its name is looked up, and then its name`() {
        installLauncherApp(ALPHA, "Alpha")
        Settings.widgets(context).write(ID, WidgetSettings(taps = TapTargets(header = TapTarget.App(ALPHA))))
        val held = HeldDispatcher()
        Services.io = held
        open(MonthWidgetProvider::class.java)
        // The stored settings and calendars.
        held.runHeld()
        compose.waitForIdle()

        node(R.string.setup_tap_header).assertExists()
        node(R.string.setup_tap_app_missing).assertDoesNotExist()
        node("Alpha").assertDoesNotExist()
        // The app's name.
        held.runHeld()
        compose.waitForIdle()
        node("Alpha").assertExists()
        node(R.string.setup_tap_app_missing).assertDoesNotExist()
    }

    @Test
    fun `without any calendar known, the setup says so, and its row opens nothing`() {
        open(WeekWidgetProvider::class.java)

        row(R.string.setup_calendars, context.getString(R.string.setup_no_calendars)).assertHasNoClickAction()
    }

    @Test
    fun `the sensor's calendars are listed by name, one without a name by its id, all ticked`() {
        writeSnapshot()

        open(MonthWidgetProvider::class.java)
        assertRow(R.string.setup_calendars, R.string.setup_calendars_all)
        tap(R.string.setup_calendars)

        box("Arbeit").assertIsOn()
        box("Ferien").assertIsOn()
        box(SCHOOL).assertIsOn()
        compose.onNodeWithText(WORK).assertDoesNotExist()
        compose.onNodeWithText(HOLIDAYS).assertDoesNotExist()
        node(R.string.setup_no_calendars).assertDoesNotExist()
    }

    @Test
    fun `the background's slider shows its value at once as it moves, opens no dialog, and saving stores it`() {
        open(MonthWidgetProvider::class.java)

        assertOpacity(100)
        // Set in place: its name opens nothing.
        node(R.string.setup_opacity).assertHasNoClickAction()
        slide(30f)
        assertOpacity(30)
        node(percent(100)).assertDoesNotExist()
        node(R.string.setup_ok).assertDoesNotExist()
        node(R.string.setup_cancel).assertDoesNotExist()
        // Between two steps, the nearest.
        slide(52f)
        assertOpacity(50)
        slide(0f)
        assertOpacity(0)
        tap(R.string.setup_save)

        assertEquals(WidgetSettings(opacity = cardOpacity(0)), Settings.widgets(context).read(ID))
    }

    @Test
    fun `the background's name stands above its slider, its value beside it, after plus's corner, before the taps`() {
        Settings.widgets(context).write(ID, WidgetSettings(opacity = cardOpacity(70)))
        open(WeekWidgetProvider::class.java)

        assertOpacity(70)
        assertEquals("Deckkraft des Hintergrunds", context.getString(R.string.setup_opacity))
        assertEquals("70\u00A0%", percent(70))
        val slider = slider().fetchSemanticsNode().boundsInRoot
        val value = node(percent(70)).fetchSemanticsNode().boundsInRoot
        assertTrue(top(context.getString(R.string.setup_plus_corner)) < top(context.getString(R.string.setup_opacity)))
        assertTrue(node(R.string.setup_opacity).fetchSemanticsNode().boundsInRoot.bottom <= slider.top)
        assertTrue(slider.bottom < top(context.getString(R.string.setup_taps)))
        assertTrue(slider.right <= value.left)
        assertTrue(value.center.y in slider.top..slider.bottom)
    }

    @Test
    fun `a screen reader names the background's slider by the name above it`() {
        open(MonthWidgetProvider::class.java)

        slider().assert(hasContentDescriptionExactly(context.getString(R.string.setup_opacity)))
    }

    @Test
    fun `the taps' rows stand indented below their heading`() {
        open(WeekWidgetProvider::class.java)

        val heading = node(R.string.setup_taps).fetchSemanticsNode().boundsInRoot.left
        val indent = 16 * context.resources.displayMetrics.density
        for (element in listOf(R.string.setup_tap_header, R.string.setup_tap_entry, R.string.setup_tap_new)) {
            assertEquals(heading + indent, node(element).fetchSemanticsNode().boundsInRoot.left, 0.5f)
        }
        assertEquals(heading, node(R.string.setup_plus_corner).fetchSemanticsNode().boundsInRoot.left, 0.5f)
    }

    @Test
    // A phone's height without its status and navigation bars, as on a 6.1" phone.
    @Config(qualifiers = "w393dp-h780dp")
    fun `on a phone, the whole setup of the week fits without scrolling`() {
        writeSnapshot()

        open(WeekWidgetProvider::class.java)

        node(R.string.setup_opacity).assertIsDisplayed()
        slider().assertIsDisplayed()
        node(R.string.setup_tap_new).assertIsDisplayed()
        node(R.string.setup_save).assertIsDisplayed()
        // The screen has nothing to scroll, so nothing lies below its bottom edge.
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), 0f)
        val image = compose.onRoot().captureToImage()
        Probe.renderDir.mkdirs()
        File(Probe.renderDir, "setup-week-phone.png").writeBytes(Probe.png(image.asAndroidBitmap()))
    }

    @Test
    fun `in the light, the setup is in copper's light colours`() {
        open(WeekWidgetProvider::class.java)

        val light = CopperColorScheme.light.surface.toArgb()
        assertNotEquals(CopperColorScheme.dark.surface.toArgb(), light)
        assertEquals(light, background())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in the dark, the setup is in copper's dark colours`() {
        open(WeekWidgetProvider::class.java)

        assertEquals(CopperColorScheme.dark.surface.toArgb(), background())
    }

    /**
     * The title names the kind; a setting's row shows, on its default,
     * exactly when the kind has it, and the taps a row for the header, a day
     * and "+", and for an entry exactly when the kind has entry areas, each on
     * its default.
     */
    private fun assertSections(
        @StringRes kind: Int,
        days: Boolean,
        backToToday: Boolean,
        entryTaps: Boolean,
    ) {
        compose.onNodeWithText(context.getString(R.string.setup_title, context.getString(kind))).assertExists()
        assertRow(R.string.setup_calendars, R.string.setup_no_calendars)
        assertRow(R.string.setup_plus_corner, R.string.setup_plus_bottom_right)
        node(R.string.setup_opacity).assertExists()
        assertOpacity(100)
        node(R.string.setup_save).assertExists()
        node(R.string.setup_taps).assertExists()
        val shownWhen =
            listOf(
                R.string.setup_days to days,
                R.string.setup_days_monday_to_sunday to days,
                R.string.setup_back_to_today to backToToday,
                R.string.setup_back_each_update to backToToday,
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

    /** Opens the row named row and picks option in its dialog. */
    private fun choose(
        @StringRes row: Int,
        @StringRes option: Int,
    ) {
        tap(row)
        option(option).performClick()
    }

    /** The row named name, showing value. */
    private fun row(
        @StringRes name: Int,
        value: String,
    ) = compose.onNode(hasText(context.getString(name)) and hasText(value))

    private fun assertRow(
        @StringRes name: Int,
        value: String,
    ) = row(name, value).assertExists()

    private fun assertRow(
        @StringRes name: Int,
        @StringRes value: Int,
    ) = assertRow(name, context.getString(value))

    /** An opacity as the setup shows it. */
    private fun percent(opacity: Int) = context.getString(R.string.setup_opacity_percent, opacity)

    /** The background's slider. */
    private fun slider() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    /** Moves the background's slider to percent, as a drag that ends there does. */
    private fun slide(percent: Float) =
        slider().performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(percent) }

    /**
     * The background's slider stands at opacity, from 0 to 100 % in steps of
     * 10, and the screen shows its value in percent.
     */
    private fun assertOpacity(opacity: Int) {
        val info = slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        // The slider snaps to its step in floats, 60.000004 for 60.
        assertEquals(opacity.toFloat(), info.current, 0.01f)
        assertEquals(0f..100f, info.range)
        assertEquals(9, info.steps)
        node(percent(opacity)).assertExists()
    }

    /** A calendar's box in the calendars' dialog. */
    private fun box(name: String) = compose.onNode(hasText(name) and isToggleable())

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

    /** Holds what is dispatched to it until [runHeld] runs it, so a test sees the screen before its reads end. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            held += block
        }

        fun runHeld() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
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
