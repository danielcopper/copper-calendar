package dev.cupr.calendar.fetch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.cupr.calendar.app.Services
import java.util.concurrent.TimeUnit

/**
 * Runs one fetch ([Fetcher]). A failed fetch is not retried on its own: the
 * next periodic fetch tries again (D46). Without a usable sign-in the
 * periodic fetch stops, decided under the fetch's lock, until the next
 * sign-in starts it again.
 */
class FetchWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result =
        when (Services.fetcher(applicationContext).fetch { FetchWork.stopPeriodic(applicationContext) }) {
            is Fetcher.Outcome.Failed -> Result.failure()
            else -> Result.success()
        }
}

/**
 * When the app fetches (D3, D46): every 15 minutes, the least interval
 * WorkManager allows for periodic work, and at once after signing in and
 * whenever the app's screen opens; each only with a network connection.
 */
object FetchWork {
    /** The periodic fetch's unique name. */
    const val PERIODIC = "fetch"

    /** The fetch started at once; the sign-in screen watches it to show the new fetch time. */
    const val NOW = "fetch-now"

    private const val INTERVAL_MINUTES = 15L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Fetches now, as expedited work where the system's quota allows, and keeps the periodic fetch scheduled. */
    fun start(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, nowRequest())
        manager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FetchWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).setConstraints(network).build(),
        )
    }

    /** The fetch at once: expedited where the system's quota allows, else as soon as it may run, with a network. */
    internal fun nowRequest() =
        OneTimeWorkRequestBuilder<FetchWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    /** Stops both fetches, on signing out. */
    fun stop(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(NOW)
        manager.cancelUniqueWork(PERIODIC)
    }

    /** Stops the periodic fetch, when there is no usable sign-in to fetch with. */
    fun stopPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
