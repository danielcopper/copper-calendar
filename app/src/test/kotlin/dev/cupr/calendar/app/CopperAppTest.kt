package dev.cupr.calendar.app

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.signin.SignInActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** What the merged manifest gives the app. */
@RunWith(RobolectricTestRunner::class)
class CopperAppTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val packageManager = application.packageManager

    @Test
    fun `WorkManager does not start with the app, only on demand`() {
        assertTrue(application is CopperApp)
        val startup =
            packageManager.getProviderInfo(
                ComponentName(application, "androidx.startup.InitializationProvider"),
                PackageManager.GET_META_DATA,
            )
        assertFalse(
            startup.metaData.orEmptyKeys().toString(),
            "androidx.work.WorkManagerInitializer" in startup.metaData.orEmptyKeys(),
        )
    }

    @Test
    fun `the launcher opens the sign-in screen`() {
        val launch = packageManager.getLaunchIntentForPackage(application.packageName)

        assertEquals(SignInActivity::class.java.name, launch?.component?.className)
    }

    @Test
    fun `the app may use the internet and see whether it has a network`() {
        val info = packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS)

        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        // Services.online asks the ConnectivityManager, which throws without it.
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.ACCESS_NETWORK_STATE))
    }

    @Test
    fun `nothing is backed up or moved to a new device`() {
        val excluded = mutableMapOf<String, MutableSet<String>>()
        application.resources.getXml(R.xml.data_extraction_rules).use { xml ->
            var section = ""
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "cloud-backup", "device-transfer" -> section = xml.name
                    "exclude" -> excluded.getOrPut(section) { mutableSetOf() } += xml.getAttributeValue(null, "domain")
                }
            }
        }

        val all = setOf("root", "file", "database", "sharedpref", "external")
        assertEquals(mapOf("cloud-backup" to all, "device-transfer" to all), excluded)
    }

    private fun android.os.Bundle?.orEmptyKeys(): Set<String> = this?.keySet().orEmpty()
}
