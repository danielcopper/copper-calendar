package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
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
 * start with every process they start (D46).
 */
object Widgets {
    private val providers: List<DrawnWidgetProvider>
        get() = listOf(MonthWidgetProvider(), WeekWidgetProvider(), WeekColumnsWidgetProvider())

    /** Redraws every widget and schedules the next midnight anew. */
    fun update(context: Context) {
        redraw(context)
        Midnight.schedule(context, ExistingWorkPolicy.REPLACE)
    }

    /** Redraws every widget from what the app keeps now ([DrawnWidgetProvider.content]). */
    internal fun redraw(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val content = DrawnWidgetProvider.content(context)
        providers.forEach { it.updateAll(context, manager, it.ids(context, manager), content) }
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
        clock: Clock = Clock.systemDefaultZone(),
    ) {
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request(clock))
    }

    /** The job, due at the next midnight after clock's now. */
    internal fun request(clock: Clock): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<MidnightWorker>()
            .setInitialDelay(untilMidnight(clock).toMillis(), TimeUnit.MILLISECONDS)
            .build()

    /** From clock's now to the start of the next day in its zone. */
    internal fun untilMidnight(clock: Clock): Duration {
        val next = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone)
        return Duration.between(clock.instant(), next.toInstant())
    }
}

/** Redraws every widget at midnight and schedules the next midnight ([Midnight]). */
class MidnightWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        Widgets.redraw(applicationContext)
        // Appended to this job, as replacing it would cancel this job while it runs.
        Midnight.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
