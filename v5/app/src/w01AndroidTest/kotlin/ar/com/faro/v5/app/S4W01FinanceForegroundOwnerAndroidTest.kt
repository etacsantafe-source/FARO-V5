package ar.com.faro.v5.app

import android.content.ComponentName
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** D1B0: real Android lifecycle; NO SQL, biometric or user balance test. */
@RunWith(AndroidJUnit4::class)
class S4W01FinanceForegroundOwnerAndroidTest {
    @Test fun t01PrivateFinanceReaderLaunchesDeniedAndChatLauncherPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ar.com.faro.alpha.w01financedev", context.packageName)
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(
            context.packageName,
        ))
        assertEquals(
            FaroV5W01FinanceDevLauncherActivity::class.java.name,
            intent.component?.className,
        )
        @Suppress("DEPRECATION")
        val reader = context.packageManager.getActivityInfo(
            ComponentName(context, FaroV5W01FinanceReadDevActivity::class.java),
            0,
        )
        assertFalse("Finance reader MUST be non-exported", reader.exported)
        ActivityScenario.launch(FaroV5W01FinanceReadDevActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Real Android foreground owner not mounted",
                    activity.closedForAndroidTest())
                assertTrue("No SQL/auth permission means no Finance read",
                    activity.readIsDeniedForAndroidTest())
                assertTrue("ON_RESUME must invalidate before observation",
                    activity.clearsForAndroidTest() >= 1)
            }
        }
        println("W01_D1B0_PRIVATE_FRAGMENTACTIVITY_NO_FINANCE_READ_PASS")
    }

    @Test fun t02PauseStopResumeAndDestroyDoNotReviveFinanceAuthority() {
        var destroyed: FaroV5W01FinanceReadDevActivity? = null
        var firstClearCount = -1
        ActivityScenario.launch(FaroV5W01FinanceReadDevActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                destroyed = activity
                firstClearCount = activity.clearsForAndroidTest()
                assertFalse(activity.closedForAndroidTest())
                assertTrue(activity.readIsDeniedForAndroidTest())
            }
            scenario.moveToState(Lifecycle.State.CREATED) // real ON_PAUSE + ON_STOP
            scenario.moveToState(Lifecycle.State.RESUMED) // real ON_RESUME
            scenario.onActivity { activity ->
                assertTrue("Android lifecycle did not clear on stop/resume",
                    activity.clearsForAndroidTest() >= firstClearCount + 2)
                assertTrue("Resume never issues read authorization",
                    activity.readIsDeniedForAndroidTest())
                assertFalse(activity.closedForAndroidTest())
            }
        }
        assertTrue("Activity destroy must poison reconciler",
            requireNotNull(destroyed).closedForAndroidTest())
        println("W01_D1B0_ANDROID_REAL_STOP_RESUME_DESTROY_FAIL_CLOSED_PASS")
    }
}
