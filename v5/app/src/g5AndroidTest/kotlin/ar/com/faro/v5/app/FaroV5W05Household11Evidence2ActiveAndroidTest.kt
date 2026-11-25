package ar.com.faro.v5.app

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.generation.FaroV5Household11Evidence2ActiveDevReadHost.Status
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaroV5W05Household11Evidence2ActiveAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun startup(fixture: String, foreground: () -> Boolean = { true }) =
        FaroV5W05Household11Evidence2DevStartup(
            File(context.filesDir, "c2c1/" + fixture).toPath(),
            context.packageName, foreground, { true },
            isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )

    @Test fun frameworkReadOnlyOpensClosesAndReopensCommittedEvidence2() {
        repeat(2) {
            startup("sealed").use { host ->
                val observed = host.reopenReadOnly()
                assertEquals(Status.ACTIVE_ELEVEN, observed.status)
                assertEquals(1L, observed.needs)
                assertEquals(2L, observed.receipts)
                assertEquals(3L, observed.needRevision)
                assertEquals(2L, observed.receiptRevision)
            }
        }
    }

    @Test fun frameworkReadsWalAndRejectsCommittedRevisionDrift() {
        startup("wal").use { host ->
            assertEquals(Status.BLOCKED, host.reopenReadOnly().status)
        }
    }

    @Test fun revocationReleasesAndroidReadLease() {
        var foreground = true
        startup("sealed") { foreground }.use { host ->
            assertEquals(Status.ACTIVE_ELEVEN, host.reopenReadOnly().status)
            foreground = false
            assertEquals(Status.BLOCKED, host.reopenReadOnly().status)
            startup("sealed").use { second ->
                assertEquals(Status.ACTIVE_ELEVEN, second.reopenReadOnly().status)
            }
        }
    }
}
