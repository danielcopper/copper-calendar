package dev.cupr.calendar.tap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * Opens target with start, an Activity's startActivity (another context's
 * would need a new task for every link).
 *
 * A Home Assistant target ([forHomeAssistant]) opens on top of the Home
 * Assistant app started as its icon starts it, in a cleared task (D30); when
 * no single app is seen to handle the link or that app has no launcher entry,
 * the link opens alone in a new task, and the log says why. Any other target
 * opens alone in a new task. When no app
 * handles a link or this app may not start it, the rest is not started, and
 * the log and a short Toast say so (D25): that the Home Assistant app is
 * missing when no app handles a Home Assistant target, otherwise that the
 * target does not open.
 */
internal fun openTarget(
    context: Context,
    target: String,
    start: (Intent) -> Unit,
) {
    val view = Intent(Intent.ACTION_VIEW, target.toUri())
    // Two starts, not startActivities, which creates the lower activity only once the user returns to it. They reach
    // two different activities of the Home Assistant app, so the second link cannot land in the first one's instance
    // (D30). Back to the target alone, without clearing, once that app opens navigate links in place
    // (home-assistant/android issue 5381, pull request 7163).
    val app = if (view.forHomeAssistant) launcherStart(context, view, target) else null
    val links = if (app != null) listOf(app, view) else listOf(view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    links.forEachIndexed { index, link ->
        val first = index == 0
        val name = if (link === view) target else "${link.component?.flattenToShortString()} (tapped $target)"
        if (!opened(context, link, name, missing = first && view.forHomeAssistant, start = start)) return
    }
}

/** A link the Home Assistant app opens: its scheme, [TapTargets.HOME_ASSISTANT_SCHEME]. */
private val Intent.forHomeAssistant: Boolean get() = data?.scheme == TapTargets.HOME_ASSISTANT_SCHEME

/**
 * The app that handles link started the way its icon starts it, in a cleared
 * task: its launch intent from the package manager, which sees that app
 * through the manifest's `<queries>`. None, and a log line why, when no app is
 * seen to handle the link, when several do and none is the default (the link
 * resolves to the chooser), or when the one that does has no launcher entry.
 */
private fun launcherStart(
    context: Context,
    link: Intent,
    target: String,
): Intent? {
    val packageManager = context.packageManager
    val resolved = packageManager.resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val handlers =
        packageManager
            .queryIntentActivities(link, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
    val launch = resolved?.takeIf { it in handlers }?.let(packageManager::getLaunchIntentForPackage)
    val fallback =
        when {
            resolved == null -> "No app is seen to open $target"
            resolved !in handlers -> "${handlers.size} apps open $target and none is the default"
            launch == null -> "$resolved opens $target but has no launcher entry"
            else -> null
        }
    if (fallback != null) Log.w(TAG, "$fallback; opening the link alone")
    return launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/**
 * Starts one link with start; when that fails, logs and toasts it under name
 * and answers false. missing: no app handling it means the Home Assistant app
 * is missing.
 */
private fun opened(
    context: Context,
    link: Intent,
    name: String,
    missing: Boolean,
    start: (Intent) -> Unit,
): Boolean =
    try {
        start(link)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app opens $name", e)
        toast(context, if (missing) R.string.target_home_assistant_missing else R.string.target_not_openable)
        false
    } catch (e: SecurityException) {
        // An app that handles it is there, so even for Home Assistant's scheme the app is not missing.
        Log.w(TAG, "Not allowed to open $name", e)
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
