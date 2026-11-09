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
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** X08/X12: real DEV Android onPause revokes ALL Search owner read grants.
 * No fake data source, provider, Apply, product activation or physical G15.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57SearchBackgroundRevokeUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun nativeButtons(root: View): List<Button> = buildList {
        fun visit(view: View) {
            if (view is Button) add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                visit(view.getChildAt(index))
            }
        }
        visit(root)
    }

    /** Native views remain visible when asynchronous DEV graph admission fails. */
    private fun nativeStatusSnapshot(): String {
        val labels = mutableListOf<String>()
        rule.runOnUiThread {
            fun visit(view: View) {
                if (view is android.widget.TextView) labels.add(view.text.toString())
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
            visit(rule.activity.findViewById<View>(android.R.id.content))
        }
        return labels.joinToString(" | ").take(1000)
    }


    private fun explicitlyOpenChat() {
        rule.runOnUiThread {
            val root = rule.activity.findViewById<View>(android.R.id.content)
            val button = nativeButtons(root).single {
                it.text.toString() == "Abrir Chat local de desarrollo"
            }
            assertTrue(button.isEnabled)
            assertTrue(button.performClick())
        }
        // Distinguish native DEV open error from a Compose mount timeout.
        // A timeout alone is NOT evidence that Search ACL revocation is wrong.
        try {
            rule.waitUntil(60_000L) {
                runCatching {
                    rule.onAllNodesWithText("Buscar en esta conversación",
                        useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                        rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
        } catch (failure: AssertionError) {
            // ComposeTimeoutException is an assertion failure, not Exception.
            // Keep a native DEV status snapshot and the original failure as cause.
            throw AssertionError(
                "DEV Chat mount failed after explicit native click: " + nativeStatusSnapshot(),
                failure,
            )
        }
        rule.waitForIdle()
    }

    private fun search(query: String) {
        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText("Buscar").performClick()
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en FARO",
                    useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(query)
        rule.onAllNodes(hasText("Buscar") and hasClickAction())[0].performClick()
    }

    @Test
    fun x08ForegroundLossRevokesSearchAndRequiresNewUserClick() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        explicitlyOpenChat()
        val body = "X08RevocationRealWorkspace-" + UUID.randomUUID().toString()
        val fields = rule.onAllNodes(hasSetTextAction())
        assertTrue(fields.fetchSemanticsNodes().size >= 2)
        fields[fields.fetchSemanticsNodes().lastIndex].performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled()
        rule.onNodeWithText("Enviar").performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onNodeWithText("Enviar").assertIsNotEnabled()
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        search(body)
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Fuente: workspace.conversations",
                    substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // Actual Android lifecycle, not a manually injected Flow event.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Fuente: workspace.conversations",
                    substring = true).fetchSemanticsNodes().isEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("Fuente: workspace.conversations", substring = true)
            .assertCountEquals(0)
        rule.onAllNodesWithText("Resultados de las fuentes autorizadas.")
            .assertCountEquals(0)

        // The same query after foreground return must NOT resurrect a read grant.
        rule.onAllNodes(hasText("Buscar") and hasClickAction())[0].performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Búsqueda no disponible. No se leyeron datos.")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("No se encontraron resultados en las fuentes consultadas.")
            .assertCountEquals(0)
        rule.onAllNodesWithText("Fuente: workspace.conversations", substring = true)
            .assertCountEquals(0)
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)

        // Reopen ONLY via a fresh native user click after Activity recreation.
        rule.activityRule.scenario.recreate()
        explicitlyOpenChat()
        search(body)
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Fuente: workspace.conversations",
                    substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("Fuente: workspace.conversations",
            substring = true).assertCountEquals(1)
    }
}
