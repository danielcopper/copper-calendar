package dev.cupr.calendar.setup

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.cupr.calendar.app.PhoneTheme
import dev.cupr.calendar.widget.WidgetKind

/**
 * A widget's setup, which the launcher opens from the widget's long-press
 * menu; placing a widget opens nothing, it draws with the defaults
 * (`configuration_optional`). Opened for an id that is none of this app's
 * widgets, it shows nothing and finishes, and the log says so. Going back
 * keeps the stored settings; saving stores the new ones and finishes.
 */
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        val kind = provider?.let { WidgetKind.of(this, it) }
        if (kind == null) {
            Log.w(TAG, "Setup opened for widget $id, which is none of this app's widgets (provider $provider)")
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                SetupScreen(id, kind) {
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }

    private companion object {
        const val TAG = "Setup"
    }
}
