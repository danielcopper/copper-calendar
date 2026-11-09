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
 * no app handles the target, nothing opens.
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Taps.read(intent)?.let { (element, day) -> open(TapTargets.uri(element, day)) }
        finish()
    }

    private fun open(target: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app opens $target", e)
        }
    }

    private companion object {
        const val TAG = "TapActivity"
    }
}
