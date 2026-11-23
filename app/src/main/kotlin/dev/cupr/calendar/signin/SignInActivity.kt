package dev.cupr.calendar.signin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.cupr.calendar.app.AppTheme
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.enableEdgeToEdgeFromCopper
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
        enableEdgeToEdgeFromCopper()
        setContent { AppTheme { SignInScreen(starts) } }
    }

    override fun onStart() {
        super.onStart()
        starts++
        lifecycleScope.launch(Services.io) { Services.opened(applicationContext) }
    }
}
