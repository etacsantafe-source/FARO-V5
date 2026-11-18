package ar.com.faro.v5.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class S4W01Finance9SessionAndroidTest {
    @Test fun activatesAndReopensTheSameAuthorizedFinance9Generation() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        assertEquals(S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID, application.packageName)
        val root = Files.createTempDirectory(application.noBackupFilesDir.toPath(), "finance9-session-test-")
        val authority = FaroV5V2CoreDevAuthority(root, w01Finance9 = true)
        val pointer = FileActiveGenerationPointer(root)
        try {
            authority.open().close()
            authority.upgradePlanning8AfterExplicitForegroundRequest { true }.close()
            val origin = checkNotNull(pointer.read())
            assertTrue(runCatching { authority.upgradeFinance9AfterExplicitForegroundRequest { false } }.isFailure)
            assertEquals(origin, pointer.read())

            val active = authority.upgradeFinance9AfterExplicitForegroundRequest { true }.use { session ->
                val selected = checkNotNull(pointer.read())
                assertNotEquals(origin.generationId, selected.generationId)
                assertEquals(origin.sequence + 1L, selected.sequence)
                assertTrue(runCatching { authority.open().close() }.isFailure)
                val loan = checkNotNull(session.borrowW01ActiveFinanceRead(application) { true })
                loan.use {
                    val read = checkNotNull(it.observedForDevOnly())
                    assertEquals(selected.generationId.value, read.generationId)
                    assertEquals(selected.sequence, read.pointerSequence)
                    assertNull(session.borrowW01ActiveFinanceRead(application) { true })
                }
                assertNull(loan.observedForDevOnly())
                selected
            }
            authority.open().use { reopened ->
                assertEquals(active, pointer.read())
                checkNotNull(reopened.borrowW01ActiveFinanceRead(application) { true }).use { loan ->
                    assertEquals(active.generationId.value, checkNotNull(loan.observedForDevOnly()).generationId)
                }
            }
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) } }
        }
    }

    @Test fun w01LauncherOffersExplicitFinance9Activation() {
        ActivityScenario.launch(FaroV5W01FinanceDevLauncherActivity::class.java).use { scenario ->
            scenario.onActivity { launcher ->
                assertEquals(S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID, launcher.packageName)
                assertTrue(launcher.enablesW01Finance9())
                assertTrue(hasActivationButton(launcher.window.decorView))
            }
        }
    }

    private fun hasActivationButton(view: View): Boolean =
        if (view is Button) view.text.toString() == "Activar Finance9 (DEV)"
        else view is ViewGroup && (0 until view.childCount).any { hasActivationButton(view.getChildAt(it)) }
}
