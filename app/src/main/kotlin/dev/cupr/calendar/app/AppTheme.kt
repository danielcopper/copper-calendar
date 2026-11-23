package dev.cupr.calendar.app

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import dev.cupr.design.CopperColorScheme

// Both read the mode from the configuration's isNightModeActive; the resources follow night mode through
// values-night.

/** The app's screens in copper's colour scheme (D65), light or dark with the system. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = copperScheme(LocalConfiguration.current.isNightModeActive), content = content)
}

/** Edge to edge, with the system bars' scrims copper's background, so the system draws no colour of its own there. */
fun ComponentActivity.enableEdgeToEdgeFromCopper() {
    val dark = resources.configuration.isNightModeActive
    val background = copperScheme(dark).background.toArgb()
    val bars = if (dark) SystemBarStyle.dark(background) else SystemBarStyle.light(background, background)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
}

/** Copper's colour scheme for dark or light mode. */
private fun copperScheme(dark: Boolean): ColorScheme = if (dark) CopperColorScheme.dark else CopperColorScheme.light
