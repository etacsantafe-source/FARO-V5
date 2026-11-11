package ar.com.faro.v5.app

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.kernel.contracts.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android host + Keystore + five stores. Never click Consult with a synthetic key. */
@RunWith(AndroidJUnit4::class)
class FaroV5X03U02IsolatedAiInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val root get() = File(context.noBackupFilesDir, "faro-s3-x03-u02-isolated-debug-only")
    private fun views(v: View): List<View> = buildList {
        add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(views(v.getChildAt(i)))
    }
    private fun open(a: FaroV5X03U02IsolatedAiActivity, confirm: Boolean) {
        val all = views(a.window.decorView)
        all.filterIsInstance<CheckBox>().single().isChecked = confirm
        assertTrue(all.filterIsInstance<Button>().single { it.text == "Abrir Chat IA U02" }.performClick())
    }
    private fun awaitNative(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>, text: String) {
        val end = SystemClock.elapsedRealtime() + 20_000
        var found = false
        while (!found && SystemClock.elapsedRealtime() < end) {
            s.onActivity { a -> found = views(a.window.decorView).filterIsInstance<TextView>()
                .any { it.text.contains(text) } }
            if (!found) Thread.sleep(50)
        }
        assertTrue("Missing native state: $text", found)
    }
    private fun awaitClosed(host: FaroV5QuarantinedV2ChatHostBinding) {
        val end = SystemClock.elapsedRealtime() + 15_000
        // Private state is inspected only from the test APK, not a shipping test hook.
        val field = host.javaClass.getDeclaredField("closed").apply { isAccessible = true }
        val lease = FaroV5X03U02IsolatedAiActivity::class.java.getDeclaredField("occupied").apply { isAccessible = true }
        fun released() = !(lease.get(null) as java.util.concurrent.atomic.AtomicBoolean).get()
        while ((!(field.get(host) as java.util.concurrent.atomic.AtomicBoolean).get() || !released()) &&
            SystemClock.elapsedRealtime() < end) Thread.sleep(50)
        assertTrue((field.get(host) as java.util.concurrent.atomic.AtomicBoolean).get())
        assertTrue("Resource cleanup must release exclusive lease", released())
    }
    private fun mounted(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>): FaroV5QuarantinedV2ChatHostBinding {
        compose.waitUntil(45_000) {
            runCatching { compose.onAllNodesWithText("Enviar").fetchSemanticsNodes().size == 1 }.getOrDefault(false)
        }
        var host: FaroV5QuarantinedV2ChatHostBinding? = null
        s.onActivity { a -> host = a.javaClass.getDeclaredField("host").apply {
            isAccessible = true
        }.get(a) as FaroV5QuarantinedV2ChatHostBinding }
        return requireNotNull(host)
    }
    private fun revoke() {
        val owner = FaroV5TrustedAssistantControlOwner.get(context)
        val snapshot = runBlocking { owner.read() }
        if (snapshot.remoteReasoningEnabled) assertTrue(runBlocking {
            owner.disable(snapshot.revision)
        } is FaroV5TrustedAssistantControlOwner.Change.Saved)
    }
    private fun s2Snapshot(): Map<String, List<Byte>> {
        val s2 = File(context.noBackupFilesDir, "faro-s2-f1-debug-only")
        return if (!s2.exists()) emptyMap() else s2.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(s2).path to it.readBytes().toList() }
    }

    @Test fun deniedConsentOrUnconfirmedFreeTierDoesNotCreateU02Stage() {
        assertFalse(root.exists())
        val s2 = s2Snapshot()
        val owner = FaroV5TrustedAssistantControlOwner.get(context)
        assertFalse(runBlocking { owner.read().remoteReasoningEnabled })
        ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { s ->
            s.onActivity { open(it, false) }
            awaitNative(s, "falta confirmar plan Free")
            assertFalse(root.exists())
            s.onActivity { open(it, true) }
            awaitNative(s, "revisá consentimiento")
            assertFalse(root.exists())
            s.recreate()
            s.onActivity { a ->
                assertFalse(views(a.window.decorView).filterIsInstance<CheckBox>().single().isChecked)
            }
        }
        assertEquals(s2, s2Snapshot())
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test fun syntheticKeystoreConsentMountsRealQuarantinedHostNoAutoDispatchAndClosesOnPause() {
        assertFalse(root.exists())
        val s2 = s2Snapshot()
        val owner = FaroV5TrustedAssistantControlOwner.get(context)
        val key = ("test_u02_" + UUID.randomUUID().toString().replace("-", "")).toByteArray()
        var lastHost: FaroV5QuarantinedV2ChatHostBinding? = null
        try {
            val before = runBlocking { owner.read() }
            assertTrue(runBlocking { owner.enableWithCredential(key, before.revision) }
                is FaroV5TrustedAssistantControlOwner.Change.Saved)
            ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { s ->
                // Valid consent alone still cannot create a stage without Free confirmation.
                s.onActivity { open(it, false) }
                awaitNative(s, "falta confirmar plan Free")
                assertFalse(root.exists())
                s.onActivity { open(it, true); open(it, true) } // double start denied
                val host = mounted(s)
                lastHost = host
                assertTrue(host.verifyQuarantined())
                assertEquals("x03-u02-isolated-debug-1", host.generationId)
                val body = "U02 local ${UUID.randomUUID()}"
                val editors = compose.onAllNodes(hasSetTextAction())
                editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(body)
                compose.onNodeWithText("Enviar").performClick()
                compose.waitUntil(30_000) {
                    runCatching { compose.onAllNodesWithText("Consultar FARO").fetchSemanticsNodes().size == 1 }
                        .getOrDefault(false)
                }
                compose.onNodeWithText("Consultar FARO").assertIsEnabled() // NO click / NO HTTP
                compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
                compose.onAllNodesWithText("Confirmar propuesta").assertCountEquals(0)
                val chat = runBlocking { host.workspaceReadPort.readSnapshot().activeConversationId }!!
                val timeline = runBlocking { host.timelineReadPort.readTimeline(chat) }!!
                assertEquals(listOf(body), timeline.messages.map { it.text })
                assertTrue(timeline.messages.all { it.role == ConversationMessageRole.USER &&
                    it.state == ConversationMessageState.ACCEPTED })
                val user = timeline.messages.single()
                val turn = ConversationAcceptedUserTurn(chat, user.id, user.turnId, user.createdAtEpochMillis)
                s.moveToState(Lifecycle.State.STARTED)
                assertFalse("Pause synchronously invalidates generation", host.verifyQuarantined())
                var callback = false
                assertFalse(host.assistantTurnPort.dispatchAcceptedTurn(turn) { callback = true })
                assertFalse(callback)
                awaitClosed(host)
                s.moveToState(Lifecycle.State.RESUMED)
                s.onActivity { a -> assertFalse(views(a.window.decorView).filterIsInstance<CheckBox>().single().isChecked) }
                s.recreate()
                s.onActivity { a -> assertFalse(views(a.window.decorView).filterIsInstance<CheckBox>().single().isChecked) }
                s.onActivity { open(it, true) }
                val reopened = mounted(s)
                lastHost = reopened
                assertEquals(timeline.messages, runBlocking { reopened.timelineReadPort.readTimeline(chat) }!!.messages)
                compose.onAllNodesWithText("Consultar FARO").assertCountEquals(0) // no replay
                // Revocation before dispatch must fail locally without reaching HTTP.
                revoke()
                assertNull(FaroV5TrustedModelGatewayFactory({ runBlocking { owner.read() } },
                    owner.secretReader()).create())
                s.moveToState(Lifecycle.State.STARTED)
                awaitClosed(reopened)
                s.moveToState(Lifecycle.State.RESUMED)
                s.onActivity { open(it, true) }
                awaitNative(s, "revisá consentimiento")
            }
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        } finally {
            key.fill(0)
            revoke()
            lastHost?.let { awaitClosed(it) }
            assertEquals(s2, s2Snapshot())
            assertTrue(root.deleteRecursively())
        }
    }
}
