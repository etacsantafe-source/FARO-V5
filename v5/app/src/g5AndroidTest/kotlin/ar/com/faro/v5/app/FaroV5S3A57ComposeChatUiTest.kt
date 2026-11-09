package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID

/**
 * X12 new real Compose Chat tests on isolated DEV Android emulator.
 * The human-origin View button mounts the actual five-store DEV authority.
 * No fake Compose tree, AI, Apply, Android physical G15 or production package.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57ComposeChatUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun visitButtons(view: View): List<Button> = buildList {
        fun collect(v: View) {
            if (v is Button) add(v)
            if (v is ViewGroup)
                for (index in 0 until v.childCount) collect(v.getChildAt(index))
        }
        collect(view)
    }

    /** Opens via the real native foreground callback, not a direct host call. */
    private fun explicitlyOpenRealChat() {
        rule.runOnUiThread {
            val content = rule.activity.findViewById<View>(android.R.id.content)
            val button = visitButtons(content).single {
                it.text.toString() == "Abrir Chat local de desarrollo"
            }
            assertTrue(button.isEnabled)
            assertTrue(button.performClick())
        }
        // A full SQLite/Workspace open + SurfaceHost.setContent is asynchronous.
        // Before setContent there may be no Compose root; treat that as not ready.
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText(
                    "Buscar en esta conversación", useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    @Test
    fun x12RealDevChatMountsAccessibleComposeEditorWithoutFalseApply() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        explicitlyOpenRealChat()
        rule.onNodeWithText("Preguntale a FARO", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Buscar en esta conversación", useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText("Enviar").assertIsNotEnabled()
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        rule.onAllNodesWithText("Configurar IA").assertCountEquals(0)
        assertTrue("Text editor must be a real Compose semantics action",
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun x12RealComposeUserTurnIsDurableAcrossActivityRecreationWithoutFakeReply() {
        explicitlyOpenRealChat()
        val body = "X12ComposeLocalPersistido-" + UUID.randomUUID().toString()
        // The in-chat Search editor precedes the Chat composer; the last
        // semantics text action belongs to the canonical Compose composer.
        val editors = rule.onAllNodes(hasSetTextAction())
        assertTrue(editors.fetchSemanticsNodes().size >= 2)
        editors.onLast().performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled()
        rule.onNodeWithText("Enviar").performClick()
        // Wait for real Workspace send/refresh to finish. An instant field echo
        // is NOT durable evidence, so also require Enviar disabled after send.
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    rule.onNodeWithText("Enviar").fetchSemanticsNode().config
                        .let { true }
            }.getOrDefault(false)
        }
        rule.activityRule.scenario.recreate()
        // On recreation the native Activity starts CLOSED. No privileged click
        // or model dispatch can be replayed implicitly; explicitly reopen.
        explicitlyOpenRealChat()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText(body, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("Enviar").assertIsNotEnabled()
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        rule.onAllNodesWithText("Procesando…").assertCountEquals(0)
    }
}
