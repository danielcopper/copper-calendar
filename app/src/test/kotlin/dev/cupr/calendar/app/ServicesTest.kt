package dev.cupr.calendar.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class ServicesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private fun capabilities(vararg capability: Int) =
        ShadowNetworkCapabilities.newInstance().also { caps ->
            capability.forEach { shadowOf(caps).addCapability(it) }
        }

    @Test
    fun `the phone is online with a network that reaches the internet`() {
        shadowOf(connectivity).setNetworkCapabilities(
            connectivity.activeNetwork,
            capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        )

        assertTrue(Services.online(context))
    }

    @Test
    fun `a network without the internet, or none, is offline`() {
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities())
        assertFalse(Services.online(context))

        shadowOf(connectivity).setDefaultNetworkActive(false)
        shadowOf(connectivity).setActiveNetworkInfo(null)
        assertFalse(Services.online(context))
    }
}
