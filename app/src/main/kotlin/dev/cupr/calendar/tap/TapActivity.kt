package dev.cupr.calendar.tap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri

/**
 * The invisible activity a widget's taps start (D23): it shows nothing,
 * resolves the tapped element's target, opens it and finishes at once. When
 * the intent carries no complete tap, or no app handles or may open its
 * target, nothing opens and the log says why.
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tap = Taps.read(intent)
        if (tap == null) {
            Log.w(TAG, "An intent without a complete tap: ${Taps.describe(intent)}")
        } else {
            openTarget(TapTargets.uri(tap), ::startActivity)
        }
        finish()
    }
}

/** Opens target with start; when no app handles it or may not be started, logs that instead. */
internal fun openTarget(
    target: String,
    start: (Intent) -> Unit,
) {
    try {
        start(Intent(Intent.ACTION_VIEW, target.toUri()))
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app opens $target", e)
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to open $target", e)
    }
}

private const val TAG = "TapActivity"
