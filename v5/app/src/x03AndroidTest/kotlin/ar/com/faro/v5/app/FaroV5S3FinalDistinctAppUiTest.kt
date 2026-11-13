package ar.com.faro.v5.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only new-variant admission/identity deltas. No provider, key or fixture. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3FinalDistinctAppUiTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun views(v: View): List<View> = buildList {
        add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(views(v.getChildAt(i)))
    }
    private fun requireEmulator() {
        check(android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.MODEL.contains("sdk", true))
        assertEquals("ar.com.faro.alpha.x03aidev", context.packageName)
    }
    @Test fun distinctPackageHasMinimalApplicationAndPrivateComponents() {
        requireEmulator()
        assertEquals(android.app.Application::class.java, context.applicationContext.javaClass)
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertTrue(context.noBackupFilesDir.canonicalPath.contains(context.packageName))
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS or
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS)
        assertNull(info.sharedUserId)
        assertTrue(info.providers.isNullOrEmpty())
        assertTrue(info.services.isNullOrEmpty())
        assertTrue(info.receivers.isNullOrEmpty())
        assertEquals(setOf(FaroV5X03U02IsolatedAiActivity::class.java.name,
            FaroV5AiSettingsActivity::class.java.name), info.activities.map { it.name }.toSet())
        assertFalse(info.activities.single { it.name == FaroV5AiSettingsActivity::class.java.name }.exported)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        @Suppress("DEPRECATION")
        val launchers = context.packageManager.queryIntentActivities(launch, 0)
        assertEquals(listOf(FaroV5X03U02IsolatedAiActivity::class.java.name), launchers.map { it.activityInfo.name })
        assertEquals(setOf("android.permission.INTERNET", context.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
            info.requestedPermissions.toSet())
        // CI creates this marker using S2's UID before launching instrumentation.
        assertFalse(java.io.File("/data/user/0/ar.com.faro.alpha.s2isolated.v5dev/files/s3-final-s2-sentinel").canRead())
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
    @Test fun distinctAppStartsWithoutRemoteConsentAndNeverRestoresFreeGrant() {
        requireEmulator()
        assertFalse(runBlocking { FaroV5TrustedAssistantControlOwner.get(context).read() }.remoteReasoningEnabled)
        ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val all = views(a.window.decorView)
                assertFalse(all.filterIsInstance<CheckBox>().single().isChecked)
                all.filterIsInstance<Button>().single { it.text == "Abrir Chat IA U02" }.performClick()
                assertNull(a.javaClass.getDeclaredField("host").apply { isAccessible = true }.get(a))
                all.filterIsInstance<CheckBox>().single().isChecked = true
            }
            scenario.recreate()
            scenario.onActivity { a ->
                assertFalse(views(a.window.decorView).filterIsInstance<CheckBox>().single().isChecked)
                assertNull(a.javaClass.getDeclaredField("host").apply { isAccessible = true }.get(a))
            }
        }
        ActivityScenario.launch(FaroV5AiSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                val field = views(a.window.decorView).filterIsInstance<EditText>().single()
                assertEquals("", field.text.toString())
                assertFalse(field.isSaveEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, field.importantForAutofill)
            }
        }
        assertFalse(runBlocking { FaroV5TrustedAssistantControlOwner.get(context).read() }.remoteReasoningEnabled)
    }
}
