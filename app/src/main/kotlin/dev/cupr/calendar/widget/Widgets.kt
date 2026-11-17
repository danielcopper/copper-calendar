package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.cupr.calendar.app.Settings
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The app's own updates of all its widgets (D27): after each fetch, a new
 * sign-in or signing out, and at midnight. Each reads what to show once for
 * all widgets and schedules the next midnight anew ([Midnight]). The
 * launcher's own updates (every 30 minutes, a new or resized widget) go
 * through each provider alone and leave WorkManager alone, so it does not
 * start with every process they start (D46). Besides: the redraw of one
 * widget whose settings were just saved ([saved]), and forgetting the
 * widgets' lists after an update of the app ([appUpdated]).
 */
object Widgets {
    private const val TAG = "Widgets"

    /** The app's widget providers, one of each [WidgetKind]. */
    internal val providers: List<DrawnWidgetProvider>
        get() = listOf(MonthWidgetProvider(), WeekWidgetProvider(), WeekColumnsWidgetProvider())

    /** The app's provider named component; none for another app's or another class. */
    internal fun provider(
        context: Context,
        component: ComponentName,
    ): DrawnWidgetProvider? = providers.firstOrNull { ComponentName(context, it.javaClass) == component }

    /** Redraws every widget and schedules the next midnight anew. */
    fun update(context: Context) {
        redraw(context, Redraw.UPDATE)
        Midnight.schedule(context, ExistingWorkPolicy.REPLACE)
    }

    private val lock = Any()

    /** Redraws every widget from what the app keeps now, for redraw. */
    internal fun redraw(
        context: Context,
        redraw: Redraw,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        withContent(context) { content ->
            providers.forEach { it.updateAll(context, manager, it.ids(context, manager), content, redraw) }
        }
    }

    /**
     * Redraws the widget id, a widget of kind, alone, its settings just
     * saved, so it shows them at once, the month's and "Woche"'s list back at
     * today ([Redraw.SAVED]); false, redrawing nothing, when id is no longer
     * a widget of kind.
     */
    fun saved(
        context: Context,
        id: Int,
        kind: WidgetKind,
    ): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val provider = providers.single { it.kind == kind }
        if (id !in provider.ids(context, manager)) return false
        withContent(context) { provider.updateAll(context, manager, intArrayOf(id), it, Redraw.SAVED) }
        return true
    }

    /**
     * Forgets the lists of the month's and "Woche"'s widgets after an update
     * of the app ([AppUpdatedReceiver]): the system shows each widget's first
     * layout again and asks for an update, which builds the list anew, so the
     * update after it scrolls to today (D59).
     */
    fun appUpdated(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = providers.filter { it.kind.backToToday }.flatMap { it.ids(context, manager).asList() }
        Settings.lastScrolls(context).forget(ids.toIntArray())
        Log.i(TAG, "The app was updated: the lists of widgets $ids are built anew")
    }

    /**
     * Runs [update] with what the widgets show now
     * ([DrawnWidgetProvider.content]) under one lock, from reading it until
     * [update] has handed its views to the launcher. The app's updates and
     * the launcher's all take it, so an update that read before a sign-out
     * cannot hand its calendar over after the sign-out's update.
     */
    internal fun withContent(
        context: Context,
        update: (Content) -> Unit,
    ) {
        synchronized(lock) { update(DrawnWidgetProvider.content(context)) }
    }
}

/**
 * The update at midnight (D27, D54): one unique one-time job without a
 * network constraint, due at the next local midnight, scheduled anew by each
 * of the app's updates ([Widgets.update]); it redraws every widget, so the
 * new day is today, and schedules the next one. WorkManager may run it a
 * little late.
 */
object Midnight {
    /** The job's unique name. */
    const val NAME = "midnight"

    /**
     * Schedules the job for the next midnight: an update replaces the one
     * scheduled; the job itself appends the next one to itself, which then
     * waits from the moment the running one ends.
     */
    internal fun schedule(
        context: Context,
        policy: ExistingWorkPolicy,
    ) {
        val delay = untilMidnight(Clock.systemDefaultZone())
        val request =
            OneTimeWorkRequestBuilder<MidnightWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
    }

    /** From clock's now to the start of the next day in its zone. */
    internal fun untilMidnight(clock: Clock): Duration {
        val next = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone)
        return Duration.between(clock.instant(), next.toInstant())
    }
}

/**
 * Redraws every widget at midnight and schedules the next midnight
 * ([Midnight]). A redraw that fails is logged and the next midnight is
 * scheduled all the same (D54).
 */
class MidnightWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    // A failure of the redraw must not end the midnight chain (D54).
    @Suppress("TooGenericExceptionCaught")
    override fun doWork(): Result {
        try {
            Widgets.redraw(applicationContext, Redraw.MIDNIGHT)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Midnight: the widgets could not be redrawn; the next midnight is scheduled all the same", e)
        }
        // Appended to this job, as replacing it would cancel this job while it runs.
        Midnight.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    private companion object {
        const val TAG = "Widgets"
    }
}
