package dev.cupr.calendar.tap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import dev.cupr.calendar.R

/**
 * The invisible activity a widget's taps start (D23): it shows nothing,
 * resolves the tapped element's target, opens it and finishes at once. When
 * the intent carries no complete tap, nothing opens and the log says why;
 * when no app handles or may open its target, a Toast says so too (D25).
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tap = Taps.read(intent)
        if (tap == null) {
            Log.w(TAG, "An intent without a complete tap: ${Taps.describe(intent)}")
        } else {
            openTarget(this, TapTargets.uri(tap), ::startActivity)
        }
        finish()
    }
}

/**
 * Opens target with start; when no app handles it or this app may not start
 * it, logs that and shows a short Toast (D25): when no app handles a target
 * the Home Assistant app opens (its scheme,
 * [TapTargets.HOME_ASSISTANT_SCHEME]), that the app is missing; otherwise
 * that the target does not open.
 */
internal fun openTarget(
    context: Context,
    target: String,
    start: (Intent) -> Unit,
) {
    val uri = target.toUri()
    try {
        start(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app opens $target", e)
        val homeAssistant = uri.scheme == TapTargets.HOME_ASSISTANT_SCHEME
        toast(context, if (homeAssistant) R.string.target_home_assistant_missing else R.string.target_not_openable)
    } catch (e: SecurityException) {
        // An app that handles it is there, so even for Home Assistant's scheme the app is not missing.
        Log.w(TAG, "Not allowed to open $target", e)
        toast(context, R.string.target_not_openable)
    }
}

private fun toast(
    context: Context,
    @StringRes text: Int,
) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private const val TAG = "TapActivity"
