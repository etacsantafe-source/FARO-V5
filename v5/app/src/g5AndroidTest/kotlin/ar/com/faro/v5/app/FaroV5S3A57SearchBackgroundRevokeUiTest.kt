package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.ComposeTimeoutException
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

    /** Privacy-safe failure snapshot: view types and counts, never user text. */
    private fun nativeStatusSnapshot(): String {
        val views = mutableListOf<String>()
        val viewError = runCatching {
            rule.runOnUiThread {
                val root = rule.activity.findViewById<View>(android.R.id.content)
                fun visit(view: View, depth: Int) {
                    if (views.size >= 32 || depth > 6) return
                    views.add(view.javaClass.simpleName + ":" +
                        if (view.isShown) "shown" else "hidden")
                    if (view is ViewGroup) {
                        for (index in 0 until minOf(view.childCount, 8)) {
                            visit(view.getChildAt(index), depth + 1)
                        }
                    }
                }
                visit(root, 0)
            }
        }.exceptionOrNull()?.javaClass?.simpleName ?: "none"
        fun countFixedLabel(label: String): Int = runCatching {
            rule.onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        }.getOrDefault(-1)
        val editorCount = runCatching {
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        }.getOrDefault(-1)
        return "views=" + views.joinToString(",") + "; viewError=" + viewError +
            "; chatLabel=" + countFixedLabel("Buscar en esta conversación") +
            "; send=" + countFixedLabel("Enviar") +
            "; searchLabel=" + countFixedLabel("Buscar en FARO") +
            "; editorCount=" + editorCount
    }

    private fun explicitlyOpenChat(expectRestoredSearch: Boolean = false) {
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
                    if (expectRestoredSearch) {
                        // The shell saved [CHAT, BUSCAR]; Activity recreation retains BUSCAR.
                        // A new native click reopens the owner but must not rewrite navigation.
                        rule.onAllNodesWithText("Buscar en FARO",
                            useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
                    } else {
                        rule.onAllNodesWithText("Buscar en esta conversación",
                            useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                            rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
                    }
                }.getOrDefault(false)
            }
        } catch (failure: ComposeTimeoutException) {
            // ComposeTimeoutException extends Throwable directly; catch only this
            // timeout, preserve native DEV status and original failure as cause.
            throw AssertionError(
                "DEV " + (if (expectRestoredSearch) "restored Search" else "Chat") +
                    " mount failed after explicit native click: " + nativeStatusSnapshot(),
                failure,
            )
        }
        rule.waitForIdle()
    }

    private fun search(query: String, alreadyInSearch: Boolean = false) {
        if (!alreadyInSearch) {
            rule.onNodeWithContentDescription("Abrir menú").performClick()
            rule.onNodeWithText("Buscar").performClick()
        }
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
        // rememberSaveable restores the Search destination; requiring Chat here
        // would test the wrong route, not Search owner recovery.
        rule.activityRule.scenario.recreate()
        explicitlyOpenChat(expectRestoredSearch = true)
        search(body, alreadyInSearch = true)
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
