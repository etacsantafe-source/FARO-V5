package ar.com.faro.v5.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.surface.FaroV5AssistantRequestMode
import ar.com.faro.v5.surface.FaroV5SurfaceHost
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite Workspace and real host/shell UI. Dispatch fake exists only in test APK. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57X03ExplicitConsultUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun waitForEditor() {
        rule.waitUntil(60_000) {
            runCatching { rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().size == 1 }
                .getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun send(body: String) {
        waitForEditor()
        val nodes = rule.onAllNodes(hasSetTextAction())
        nodes[nodes.fetchSemanticsNodes().lastIndex].performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled().performClick()
        rule.waitUntil(45_000) {
            runCatching { rule.onAllNodesWithText(body, useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1 }.getOrDefault(false)
        }
        rule.waitForIdle()
        rule.onNodeWithText("Enviar").assertIsNotEnabled()
    }

    private fun openLocal() {
        rule.runOnUiThread {
            fun buttons(view: View): List<Button> = buildList {
                if (view is Button) add(view)
                if (view is ViewGroup) for (i in 0 until view.childCount) addAll(buttons(view.getChildAt(i)))
            }
            val button = buttons(rule.activity.findViewById(android.R.id.content))
                .single { it.text.toString() == "Abrir Chat local de desarrollo" }
            assertTrue(button.performClick())
        }
        waitForEditor()
    }

    @Test fun explicitSavedUserTurnRequiresSeparateConsultClickAndNoDoubleDispatch() {
        val path = File(rule.activity.noBackupFilesDir, "x03-ui-${UUID.randomUUID()}.db")
        val workspace = ConversationWorkspaceStore.withPlanVerifier(path.absolutePath, CanonicalPreparedPlanEnvelopeVerifier)
        try {
            val generation = "x03-test"
            val chat = ConversationId("x03-${UUID.randomUUID()}")
            val pointer = Supplier { generation }
            val ready = BooleanSupplier { true }
            val gate = SingleProcessMaterialWriteGate()
            val selection = FaroV5ActiveChatSelectionSession(generation, workspace, gate, pointer, ready)
            runBlocking { assertTrue(selection.port.createAndSelect(chat, 1L,
                IdempotencyKey(UUID.randomUUID().toString())) is ConversationSelectionResult.Saved) }
            val composer = FaroV5ActiveChatComposerSession(generation, workspace, gate, pointer, ready)
            val read = FaroV5ActiveChatReadSession(generation, workspace, workspace, pointer, ready)
            val calls = CopyOnWriteArrayList<ConversationAcceptedUserTurn>()
            val dispatch = ConversationAssistantTurnDispatchPort { turn, _ -> calls.add(turn); true }
            fun mount(explicit: Boolean) = rule.runOnUiThread {
                if (explicit) FaroV5SurfaceHost.install(rule.activity,
                    workspaceReadPort = read.workspaceReadPort, timelineReadPort = read.timelineReadPort,
                    selectionPort = selection.port, composerPort = composer.port,
                    assistantTurnPort = dispatch, assistantRequestMode = FaroV5AssistantRequestMode.EXPLICIT_CONSULT)
                else FaroV5SurfaceHost.install(rule.activity,
                    workspaceReadPort = read.workspaceReadPort, timelineReadPort = read.timelineReadPort,
                    selectionPort = selection.port, composerPort = composer.port, assistantTurnPort = dispatch)
            }
            mount(true)
            val body = "X03-explicit-${UUID.randomUUID()}"
            send(body)
            assertEquals(0, calls.size)
            rule.onNodeWithText("Consultar FARO").assertIsEnabled()
            val click = rule.onNodeWithText("Consultar FARO").fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action!!
            rule.runOnUiThread { assertTrue(click()); click() }
            rule.waitUntil(10_000) { calls.size == 1 }
            rule.waitForIdle()
            val canonical = runBlocking { read.timelineReadPort.readTimeline(chat) }!!
            val user = canonical.messages.single { it.text == body }
            assertEquals(ConversationAcceptedUserTurn(chat, user.id, user.turnId, user.createdAtEpochMillis), calls.single())
            rule.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
            rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
            // Dispose and remount: never reconstruct an egress request from old messages.
            rule.activityRule.scenario.recreate()
            mount(true)
            waitForEditor()
            rule.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
            assertEquals(1, calls.size)
            // A fresh receipt is revoked on pause/recreation even before its click.
            send("X03-revoked-${UUID.randomUUID()}")
            rule.onNodeWithText("Consultar FARO").assertExists()
            rule.activityRule.scenario.recreate()
            mount(true)
            waitForEditor()
            rule.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
            assertEquals(1, calls.size)
            // Existing callers omit the new parameter: retain AUTOMATIC_ON_SEND.
            mount(false)
            send("X03-default-${UUID.randomUUID()}")
            rule.waitUntil(10_000) { calls.size == 2 }
            rule.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
        } finally {
            rule.activityRule.scenario.recreate()
            workspace.close()
        }
    }

    @Test fun localV2DevNoInternetNoImplicitModelOrApplyAfterRecreate() {
        assertEquals(PackageManager.PERMISSION_DENIED,
            rule.activity.checkSelfPermission(Manifest.permission.INTERNET))
        val info = rule.activity.packageManager.getPackageInfo(rule.activity.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        openLocal()
        val body = "X03-local-${UUID.randomUUID()}"
        send(body)
        rule.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        rule.onAllNodesWithText("Procesando…").assertCountEquals(0)
        rule.activityRule.scenario.recreate()
        openLocal()
        rule.waitUntil(45_000) { runCatching { rule.onAllNodesWithText(body, useUnmergedTree = true)
            .fetchSemanticsNodes().size == 1 }.getOrDefault(false) }
        rule.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
        rule.onAllNodesWithText("Configurar IA").assertCountEquals(0)
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        rule.onAllNodesWithText("Procesando…").assertCountEquals(0)
    }
}
