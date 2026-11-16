package dev.cupr.calendar.app

import android.app.Application
import androidx.work.Configuration

/**
 * The app's Application, for WorkManager's on-demand initialization: the
 * manifest removes WorkManager's start-up initializer, so its database opens
 * only when a fetch is scheduled or watched, not with every process the
 * widgets' updates start (D46).
 */
class CopperApp :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
