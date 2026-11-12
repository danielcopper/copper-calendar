package dev.cupr.calendar.signin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import dev.cupr.calendar.app.Services
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The app's screen: signing in with Home Assistant, who is signed in and
 * when the last fetch was, and signing out. Each time it starts while
 * signed in, the app fetches (D46), and the screen reads the status anew.
 */
class SignInActivity : ComponentActivity() {
    private var starts by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The phone's own colours (Material You), light or dark with the system.
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            ) {
                SignInScreen(starts)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        starts++
        lifecycleScope.launch(Dispatchers.IO) { Services.opened(applicationContext) }
    }
}
