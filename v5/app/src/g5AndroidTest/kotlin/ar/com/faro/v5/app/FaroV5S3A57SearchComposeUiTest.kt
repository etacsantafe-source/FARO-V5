package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * X06 genuine Android API35 DEV federated Search UI with real canonical
 * Workspace source, no model/Apply/production/physical G15. X08 source-failure
 * branch is NOT induced or certified by this test.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57SearchComposeUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun buttons(root: View): List<Button> = buildList {
        fun collect(view: View) {
            if (view is Button) add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                collect(view.getChildAt(i))
            }
        }
        collect(root)
    }

    private fun openRealDevChat() {
        rule.runOnUiThread {
            val content = rule.activity.findViewById<View>(android.R.id.content)
            val button = buttons(content).single {
                it.text.toString() == "Abrir Chat local de desarrollo"
            }
            assertTrue(button.isEnabled)
            assertTrue(button.performClick())
        }
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en esta conversación",
                    useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun sendCanonicalUserText(): String {
        val body = "X06SearchWorkspace-" + UUID.randomUUID().toString()
        val editors = rule.onAllNodes(hasSetTextAction())
        assertTrue(editors.fetchSemanticsNodes().size >= 2)
        editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled()
        rule.onNodeWithText("Enviar").performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onNodeWithText("Enviar").assertIsNotEnabled()
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        return body
    }

    private fun openRealSearchDestination() {
        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText("Buscar").performClick()
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en FARO",
                    useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun searchFor(query: String) {
        val editors = rule.onAllNodes(hasSetTextAction())
        assertEquals(1, editors.fetchSemanticsNodes().size)
        editors[0].performTextInput(query)
        // Avoid two non-clickable "Buscar" texts (TopAppBar and page title).
        // Select the real Material Button through its semantics click action.
        rule.onNode(hasText("Buscar") and hasClickAction()).performClick()
    }

    @Test
    fun x06RealWorkspaceSearchHitRevalidatesAndReturnsToExactChat() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        openRealDevChat()
        val message = sendCanonicalUserText()
        openRealSearchDestination()
        searchFor(message)
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Fuente: workspace.conversations",
                    substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        // A read-only federated result is not permission to write to Reality.
        rule.onAllNodesWithText("Solicitar Apply").assertCountEquals(0)
        rule.onNode(hasText("Fuente: workspace.conversations", substring = true)
            and hasClickAction()).performClick()
        // Shell must re-read canonical Workspace after the owner's validated
        // selection; no optimistic navigation to a cached conversation ID.
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en esta conversación",
                    useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText(message, useUnmergedTree = true)
                        .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("Fuente: workspace.conversations",
            substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
    }

    @Test
    fun x06NoMatchSearchNeverConflatesPartialAndCompleteMessaging() {
        openRealDevChat()
        openRealSearchDestination()
        searchFor("X06NeverPresent-" + UUID.randomUUID().toString())
        val finalStates = listOf(
            "No se encontraron resultados en las fuentes consultadas.",
            "Cobertura parcial: pueden existir más resultados.",
            "Búsqueda no disponible. No se leyeron datos."
        )
        rule.waitUntil(45_000L) {
            runCatching {
                finalStates.count { state ->
                    rule.onAllNodesWithText(state).fetchSemanticsNodes().isNotEmpty()
                } == 1
            }.getOrDefault(false)
        }
        assertEquals(1, finalStates.count {
            rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
        })
        rule.onAllNodesWithText("Resultados de las fuentes autorizadas.")
            .assertCountEquals(0)
        rule.onAllNodesWithText(
            "Cobertura parcial: hay resultados que todavía no se pudieron consultar."
        ).assertCountEquals(0)
    }
}
