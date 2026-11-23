package dev.cupr.calendar.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.cupr.design.CopperColorScheme

/** The app's screens in copper's colour scheme (D65), light or dark with the system. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CopperColorScheme.dark else CopperColorScheme.light,
        content = content,
    )
}
