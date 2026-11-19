package dev.cupr.calendar.setup

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import java.text.Collator

/** An app with a launcher icon: its package, the name and the activity its icon shows. */
internal data class LauncherApp(
    val packageName: String,
    val name: String,
    val activity: ComponentName,
)

/**
 * The apps with a launcher icon, which the manifest's `<queries>` lets this
 * app see, one per package, sorted by name as the phone's language sorts.
 */
internal fun launcherApps(packageManager: PackageManager): List<LauncherApp> {
    val collator = Collator.getInstance()
    return packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo }
        .distinctBy { it.packageName }
        .map { activity ->
            val name = activity.loadLabel(packageManager).toString()
            LauncherApp(activity.packageName, name, ComponentName(activity.packageName, activity.name))
        }.sortedWith(compareBy(collator) { it.name })
}

/** The name of the app of packageName as its icon shows it; none when it is not installed or has no icon. */
internal fun launcherName(
    packageManager: PackageManager,
    packageName: String,
): String? {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    return packageManager.resolveActivity(launch, 0)?.loadLabel(packageManager)?.toString()
}

/** The icon of app's launcher activity; none, and a log line, when the app has gone meanwhile. */
internal fun launcherIcon(
    packageManager: PackageManager,
    app: LauncherApp,
): Drawable? =
    try {
        packageManager.getActivityIcon(app.activity)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w("Setup", "No icon for ${app.activity.flattenToShortString()}", e)
        null
    }
