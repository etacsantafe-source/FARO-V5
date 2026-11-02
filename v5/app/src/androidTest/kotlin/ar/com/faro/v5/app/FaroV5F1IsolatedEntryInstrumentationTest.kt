package ar.com.faro.v5.app

import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real debug launcher and consented Workspace mounting; no remote request. */
@RunWith(AndroidJUnit4::class)
class FaroV5F1IsolatedEntryInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun findButton(view: View): Button? {
        if (view is Button && view.text.toString() == "Abrir Chat aislado") return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findButton(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun containsText(view: View, needle: String): Boolean {
        if (view is TextView && view.text.toString().contains(needle)) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            if (containsText(view.getChildAt(i), needle)) return true
        }
        return false
    }

    @Test fun deniedConsentCreatesNoStoreThenConsentedChatMountsWithoutRemoteCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.noBackupFilesDir, "faro-s2-f1-debug-only")
        assertFalse("Fresh isolated test must start without a stage", root.exists())
        val owner = FaroV5TrustedAssistantControlOwner.get(context)
        val before = runBlocking { owner.read() }
        assertFalse("No implicit Groq consent in a fresh debug app", before.remoteReasoningEnabled)
        ActivityScenario.launch(FaroV5F1IsolatedChatActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // DEBUG Chat is screenshot-friendly; credential Settings is secure.
                assertEquals(0, activity.window.attributes.flags and
                    WindowManager.LayoutParams.FLAG_SECURE)
                requireNotNull(findButton(activity.window.decorView)).performClick()
            }
            var denied = false
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (!denied && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    denied = containsText(activity.window.decorView, "No se abrió Chat")
                }
                if (!denied) Thread.sleep(100L)
            }
            assertTrue("Denied Groq must fail closed", denied)
            assertFalse("Denied Groq must not create Workspace", root.exists())
        }
        val syntheticCredential = ("test_" + UUID.randomUUID().toString()
            .replace("-", "")).toByteArray(Charsets.UTF_8)
        try {
            assertTrue(runBlocking {
                owner.enableWithCredential(syntheticCredential, before.revision)
            } is FaroV5TrustedAssistantControlOwner.Change.Saved)
            ActivityScenario.launch(FaroV5F1IsolatedChatActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    requireNotNull(findButton(activity.window.decorView)).performClick()
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue("Consented debug Chat must have a real staged Workspace", root.exists())
                assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
                // No send is performed: fake Keystore credential never reaches Groq.
            }
        } finally {
            syntheticCredential.fill(0)
            val current = runBlocking { owner.read() }
            if (current.remoteReasoningEnabled) {
                runBlocking { owner.disable(current.revision) }
            }
            root.deleteRecursively()
        }
    }
}
