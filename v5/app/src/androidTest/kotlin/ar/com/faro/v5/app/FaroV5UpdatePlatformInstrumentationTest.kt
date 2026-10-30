package ar.com.faro.v5.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.platform.android.update.AndroidInstalledUpdateIdentityPort
import ar.com.faro.v5.platform.android.update.FaroUpdateInstallStatusReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaroV5UpdatePlatformInstrumentationTest {
    @Test
    fun updatePlatformWiringUsesOwnInstalledIdentityAndNonExportedCallback() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val application = target.applicationContext
        assertTrue(application is FaroV5Application)

        val identity = AndroidInstalledUpdateIdentityPort(target).current()
        assertEquals(target.packageName, identity.packageName)
        assertTrue(identity.versionCode > 0L)
        assertNotNull(identity.signingIdentity)

        val component =
            ComponentName(target, FaroUpdateInstallStatusReceiver::class.java)
        val receiver =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                target.packageManager.getReceiverInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                target.packageManager.getReceiverInfo(component, 0)
            }
        assertFalse(receiver.exported)

        val root = compositionRoot(application as FaroV5Application)
        assertNotNull(root.updateRuntime.readiness())
        root.updateRuntime.reconcilePending()
    }

    private fun compositionRoot(application: FaroV5Application): FaroV5CompositionRoot {
        val field = FaroV5Application::class.java.getDeclaredField("compositionRoot")
        field.isAccessible = true
        return field.get(application) as FaroV5CompositionRoot
    }
}
