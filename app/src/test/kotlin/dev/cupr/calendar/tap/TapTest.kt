package dev.cupr.calendar.tap

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class TapTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val day = LocalDate.of(2026, 11, 12)
    private val calendar = "homeassistant://navigate/dashboard-zuhause/kalender"

    /** The Home Assistant app as its minimal build, so the tests see it found by the link's scheme, not by name. */
    private val homeAssistant = "io.homeassistant.companion.android.minimal"
    private val launchActivity =
        ComponentName(homeAssistant, "io.homeassistant.companion.android.launch.LaunchActivity")

    @Test
    fun `the header opens the calendar page`() {
        assertEquals(calendar, TapTargets.uri(Tap.Header))
    }

    @Test
    fun `a day opens the calendar page on that day`() {
        assertEquals("$calendar?day=2026-11-12", TapTargets.uri(Tap.Day(day)))
    }

    @Test
    fun `plus opens the form for a new entry on the day it is given`() {
        assertEquals("$calendar?new=2026-11-12", TapTargets.uri(Tap.New(day)))
    }

    @Test
    fun `an entry opens the calendar page on that entry and its day`() {
        assertEquals("$calendar?entry=abc-123&day=2026-11-12", TapTargets.uri(Tap.Entry("abc-123", day)))
    }

    @Test
    fun `an entry's UID is encoded for the link`() {
        assertEquals(
            "$calendar?entry=a%2Fb%26day%3D1%40x%20y&day=2026-11-12",
            TapTargets.uri(Tap.Entry("a/b&day=1@x y", day)),
        )
    }

    @Test
    fun `an entry's tap carries its UID and day to the tap activity`() {
        val tap = Tap.Entry("abc-123", day)
        assertEquals(tap, Taps.read(Taps.fillIn(tap)))
        val activity = launch(Taps.fillIn(tap))
        assertEquals("$calendar?entry=abc-123&day=2026-11-12", started().last().dataString)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `an entry without a UID or a day opens nothing`() {
        assertNull(Taps.read(tapExtras("ENTRY", "2026-11-12")))
        assertNull(Taps.read(tapExtras("ENTRY", "2026-11-12").putExtra("dev.cupr.calendar.tap.UID", "")))
        assertNull(Taps.read(tapExtras("ENTRY", "2026-11-12").putExtra("dev.cupr.calendar.tap.UID", " ")))
        assertNull(
            Taps.read(
                Intent()
                    .putExtra(
                        "dev.cupr.calendar.tap.ELEMENT",
                        "ENTRY",
                    ).putExtra("dev.cupr.calendar.tap.UID", "abc"),
            ),
        )
    }

    @Test
    fun `an entry's tap needs a UID that is not blank`() {
        for (uid in listOf("", " ")) {
            assertThrows(IllegalArgumentException::class.java) { Tap.Entry(uid, day) }
        }
    }

    @Test
    fun `a target is its element's template with the day filled in`() {
        assertEquals("$calendar?day={day}", TapTargets.defaults[TapElement.DAY])
        assertEquals("other://x/2026-11-12/y", TapTargets.uri(Tap.New(day), "other://x/{day}/y"))
    }

    @Test
    fun `the tap activity opens the tapped day's target and finishes`() {
        val activity = launch(Taps.fillIn(Tap.Day(day)))
        val opened = started().last()
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("$calendar?day=2026-11-12", opened.dataString)
        assertTrue(activity.isFinishing)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun `a Home Assistant target opens on top of that app started as its icon starts it, in a cleared task`() {
        installLinkApp()
        launch(Taps.fillIn(Tap.Day(day)))
        val opened = started()
        assertEquals(2, opened.size)
        val (app, target) = opened
        assertEquals(launchActivity, app.component)
        assertEquals(Intent.ACTION_MAIN, app.action)
        assertTrue(app.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, app.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, app.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertEquals("$calendar?day=2026-11-12", target.dataString)
        assertEquals(0, target.flags)
    }

    @Test
    fun `without an app for Home Assistant links, the target is started alone in a new task and the log says so`() {
        launch(Taps.fillIn(Tap.Day(day)))
        val opened = started().single()
        assertEquals("$calendar?day=2026-11-12", opened.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.flags)
        assertEquals(
            listOf("No app is seen to open $calendar?day=2026-11-12; opening the link alone"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `when two apps open Home Assistant links and none is the default, the target opens alone in a new task`() {
        installLinkApp()
        installLinkApp(app = "io.homeassistant.companion.android")
        shadowOf(application.packageManager).setShouldShowActivityChooser(true)
        launch(Taps.fillIn(Tap.Header))
        val opened = started().single()
        assertEquals(calendar, opened.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.flags)
        assertEquals(
            listOf("2 apps open $calendar and none is the default; opening the link alone"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `when the app for Home Assistant links has no launcher entry, the target opens alone and the log says so`() {
        installLinkApp(launcher = false)
        launch(Taps.fillIn(Tap.Header))
        val opened = started().single()
        assertEquals(calendar, opened.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.flags)
        assertEquals(
            listOf("$homeAssistant opens $calendar but has no launcher entry; opening the link alone"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `when starting the Home Assistant app is not allowed, the target is not started and the toast says so`() {
        installLinkApp()
        val opened = mutableListOf<Intent>()
        openTarget(application, calendar) {
            if (it.component == launchActivity) throw SecurityException("denied")
            opened += it
        }
        assertEquals(emptyList<Intent>(), opened)
        assertEquals(
            listOf("Not allowed to open ${launchActivity.flattenToShortString()} (tapped $calendar)"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `when the link finds no app after the Home Assistant app started, the toast says the target does not open`() {
        installLinkApp()
        val opened = mutableListOf<Intent>()
        openTarget(application, calendar) {
            if (it.component != launchActivity) throw ActivityNotFoundException("none")
            opened += it
        }
        assertEquals(listOf(launchActivity), opened.map { it.component })
        assertEquals(listOf("No app opens $calendar"), ShadowLog.getLogsForTag("TapActivity").map { it.msg })
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `when the Home Assistant app's launcher start finds no app, the toast says it is missing`() {
        installLinkApp()
        val opened = mutableListOf<Intent>()
        openTarget(application, calendar) {
            if (it.component == launchActivity) throw ActivityNotFoundException("none")
            opened += it
        }
        assertEquals(emptyList<Intent>(), opened)
        assertEquals(
            listOf("No app opens ${launchActivity.flattenToShortString()} (tapped $calendar)"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Home-Assistant-App nicht gefunden", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a target outside the Home Assistant app opens alone, in a new task it does not clear`() {
        // An app that opens the target and has a launcher entry: it is still not started as its icon does.
        installLinkApp(
            app = "x.other.app",
            links =
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("other")
                },
        )
        val opened = mutableListOf<Intent>()
        openTarget(application, "other://x/2026-11-12") { opened += it }
        assertEquals(listOf("other://x/2026-11-12"), opened.map { it.dataString })
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.single().flags)
    }

    @Test
    fun `the manifest's query matches the Home Assistant app's filter for navigate links`() {
        // Android 11 and later show this app another app only when a query matches one of its filters; without that,
        // the Home Assistant app stays hidden and every tap opens its link alone. Android turns a query's <intent>
        // into an intent with the data scheme://host/* (ParsingPackageUtils.parseQueries) and matches it against the
        // filters (AppsFilterUtils), as built here.
        val manifest =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val queries = manifest.getElementsByTagName("queries").item(0) as Element
        val intent = queries.getElementsByTagName("intent").item(0) as Element
        val action = (intent.getElementsByTagName("action").item(0) as Element).getAttribute("android:name")
        val data = intent.getElementsByTagName("data").item(0) as Element
        val uri =
            Uri
                .Builder()
                .scheme(data.getAttribute("android:scheme"))
                .authority(data.getAttribute("android:host").ifEmpty { null })
                .path("*")
                .build()
        val match = linkFilter().match(action, null, uri.scheme, uri, null, "TapTest")
        assertTrue("the query $action $uri matches no filter of the Home Assistant app", match > 0)
    }

    @Test
    fun `the fixed parts' pending intents carry their tap to the tap activity`() {
        // Both made before either is read, so the test sees it if the second overwrites the first.
        val header = Taps.pendingIntent(application, Tap.Header)
        val plus = Taps.pendingIntent(application, Tap.New(day))
        val headerIntent = shadowOf(header).savedIntent
        assertEquals(TapActivity::class.java.name, headerIntent.component?.className)
        assertEquals(Tap.Header, Taps.read(headerIntent))
        assertEquals(Tap.New(day), Taps.read(shadowOf(plus).savedIntent))
    }

    @Test
    fun `nothing crashes when no app handles the target`() {
        shadowOf(application).checkActivities(true)
        val activity = launch(Taps.fillIn(Tap.New(day)))
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `an intent without a complete tap opens nothing and is logged with what it carries`() {
        val extras = Intent().putExtra("dev.cupr.calendar.tap.ELEMENT", "DAY").putExtra("a.stray.EXTRA", 1)
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertEquals(
            listOf(
                "An intent without a complete tap: element=DAY, day=null, " +
                    "extras: [a.stray.EXTRA, dev.cupr.calendar.tap.ELEMENT]",
            ),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `a malformed day opens nothing and is logged as given`() {
        val extras = tapExtras("DAY", "2026-13-45")
        assertNull(Taps.read(extras))
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertTrue(
            ShadowLog
                .getLogsForTag("TapActivity")
                .single()
                .msg
                .contains("element=DAY, day=2026-13-45"),
        )
    }

    @Test
    fun `an unknown element opens nothing and is logged as given`() {
        val extras = tapExtras("FOOTER", "2026-11-12")
        assertNull(Taps.read(extras))
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertTrue(
            ShadowLog
                .getLogsForTag("TapActivity")
                .single()
                .msg
                .contains("element=FOOTER, day=2026-11-12"),
        )
    }

    @Test
    fun `a target no app handles is logged and shown, not thrown`() {
        openTarget(application, "$calendar?day=2026-11-12") { throw ActivityNotFoundException("none") }
        assertEquals(
            listOf(
                "No app is seen to open $calendar?day=2026-11-12; opening the link alone",
                "No app opens $calendar?day=2026-11-12",
            ),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Home-Assistant-App nicht gefunden", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a Home Assistant target this app may not start says it does not open, not that the app is missing`() {
        openTarget(application, calendar) { throw SecurityException("denied") }
        assertEquals(
            listOf("No app is seen to open $calendar; opening the link alone", "Not allowed to open $calendar"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a target outside the Home Assistant app that does not open says so`() {
        openTarget(application, "other://x/2026-11-12") { throw ActivityNotFoundException("none") }
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
        openTarget(application, "other://x/2026-11-12") { throw SecurityException("denied") }
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
        assertEquals(2, ShadowToast.shownToastCount())
    }

    /** Every intent started since the last call, oldest first (Robolectric hands out the newest first). */
    private fun started(): List<Intent> =
        generateSequence { shadowOf(application).nextStartedActivity }.toList().asReversed()

    /**
     * Installs an app as far as a tap sees it: an activity for its links, by
     * default the Home Assistant app's navigate links, and, unless told
     * otherwise, its launcher entry.
     */
    private fun installLinkApp(
        app: String = homeAssistant,
        launcher: Boolean = true,
        links: IntentFilter = linkFilter(),
    ) {
        val packageManager = shadowOf(application.packageManager)
        val linkActivity = ComponentName(app, "io.homeassistant.companion.android.launch.link.LinkActivity")
        packageManager.addActivityIfNotPresent(linkActivity)
        packageManager.addIntentFilterForActivity(linkActivity, links)
        if (launcher) {
            val launch = ComponentName(app, launchActivity.className)
            packageManager.addActivityIfNotPresent(launch)
            packageManager.addIntentFilterForActivity(
                launch,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    /** The Home Assistant app's filter for navigate links, as its manifest has it (home-assistant/android c6eb57c). */
    private fun linkFilter() =
        IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme(TapTargets.HOME_ASSISTANT_SCHEME)
            addDataAuthority("navigate", null)
        }

    private fun tapExtras(
        element: String,
        day: String,
    ) = Intent().putExtra("dev.cupr.calendar.tap.ELEMENT", element).putExtra("dev.cupr.calendar.tap.DAY", day)

    private fun launch(extras: Intent): TapActivity =
        Robolectric
            .buildActivity(TapActivity::class.java, Intent(extras).setClass(application, TapActivity::class.java))
            .create()
            .get()
}
