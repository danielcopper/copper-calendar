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
 * Opens target with start. A Home Assistant target (its scheme,
 * [TapTargets.HOME_ASSISTANT_SCHEME]) opens on top of [TapTargets.START] in a
 * cleared task (D30); any other target opens alone. When no app handles a
 * link or this app may not start it, the rest is not started, and the log and
 * a short Toast say so (D25): that the Home Assistant app is missing when no
 * app handles a link of its scheme, otherwise that the target does not open.
 */
internal fun openTarget(
    context: Context,
    target: String,
    start: (Intent) -> Unit,
) {
    val view = Intent(Intent.ACTION_VIEW, target.toUri())
    // The Home Assistant app opens each link as one more page in its task (D30). Clearing that task and opening the
    // start page beneath the target leaves two pages, and back from the target returns to the start page. Two starts,
    // not startActivities, which creates the lower activity only once the user returns to it: the Home Assistant
    // app's link activity has to run to open its page. Back to the target alone, without clearing, once that app
    // opens navigate links in place (home-assistant/android issue 5381, pull request 7163).
    val links =
        if (view.data?.scheme == TapTargets.HOME_ASSISTANT_SCHEME) {
            val home =
                Intent(Intent.ACTION_VIEW, TapTargets.START.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            listOf(home, view)
        } else {
            listOf(view)
        }
    for (link in links) {
        if (!opened(context, link, start)) return
    }
}

/** Starts one link with start; when that fails, logs and toasts it and answers false. */
private fun opened(
    context: Context,
    link: Intent,
    start: (Intent) -> Unit,
): Boolean =
    try {
        start(link)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app opens ${link.dataString}", e)
        val homeAssistant = link.data?.scheme == TapTargets.HOME_ASSISTANT_SCHEME
        toast(context, if (homeAssistant) R.string.target_home_assistant_missing else R.string.target_not_openable)
        false
    } catch (e: SecurityException) {
        // An app that handles it is there, so even for Home Assistant's scheme the app is not missing.
        Log.w(TAG, "Not allowed to open ${link.dataString}", e)
        toast(context, R.string.target_not_openable)
        false
    }

private fun toast(
    context: Context,
    @StringRes text: Int,
) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private const val TAG = "TapActivity"
