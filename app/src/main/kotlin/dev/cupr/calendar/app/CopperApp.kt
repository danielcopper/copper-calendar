package dev.cupr.calendar.app

import android.app.Application
import androidx.work.Configuration

/**
 * The app's Application, for WorkManager's on-demand initialization: the
 * manifest removes WorkManager's start-up initializer, so its database opens
 * only when the app schedules or watches work, a fetch or the update at
 * midnight, not with every process the launcher's widget updates start
 * (D46, D54).
 */
class CopperApp :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
