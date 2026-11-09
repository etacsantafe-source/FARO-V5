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
 * X08 real Workspace canonical truncation (>4096 chars) yields PARTIAL,
 * never false "no results". Actual isolated DEV app + Android API35 emulator.
 * NO mocked Search owner, unsafe API, production V2 or physical Moto G15.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57SearchPartialCanonicalUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun nativeButtons(view: View): List<Button> = buildList {
        fun walk(v: View) {
            if (v is Button) add(v)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(view)
    }

    private fun openIsolatedRealChat() {
        rule.runOnUiThread {
            val view = rule.activity.findViewById<View>(android.R.id.content)
            val button = nativeButtons(view).single {
                it.text.toString() == "Abrir Chat local de desarrollo"
            }
            assertTrue(button.isEnabled)
            assertTrue(button.performClick())
        }
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
    fun x08RealCanonicalWorkspaceTruncationReportsPartialNotFalseEmpty() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        openIsolatedRealChat()
        // >4096 chars are ACCEPTED in Workspace but capped to 4096 in the
        // canonical readSearchableConversationText projection. A real owner
        // must mark the returned snapshot incomplete, not a complete no-match.
        val tailOnlyToken = "X08HiddenTail-" + UUID.randomUUID().toString()
        val body = "x".repeat(5000) + " " + tailOnlyToken
        assertTrue(body.length in 4_097..32_000)
        assertEquals(0, body.take(4096).indexOf(tailOnlyToken).coerceAtLeast(0))
        val editors = rule.onAllNodes(hasSetTextAction())
        assertTrue("Search + canonical Composer editors required",
            editors.fetchSemanticsNodes().size >= 2)
        editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled()
        rule.onNodeWithText("Enviar").performClick()
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onNodeWithText("Enviar").assertIsNotEnabled()
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }

        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText("Buscar").performClick()
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en FARO", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }

        rule.onAllNodes(hasSetTextAction())[0].performTextInput(tailOnlyToken)
        // First clickable "Buscar" is the visible Material Button; the other
        // is an offscreen navigation Tab (verified X06 Android RED_FIX B7).
        rule.onAllNodes(hasText("Buscar") and hasClickAction())[0].performClick()
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText(
                    "Cobertura parcial: pueden existir más resultados."
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onNodeWithText("Cobertura parcial: pueden existir más resultados.")
            .assertExists()
        rule.onAllNodesWithText("No se encontraron resultados en las fuentes consultadas.")
            .assertCountEquals(0)
        rule.onAllNodesWithText("Búsqueda no disponible. No se leyeron datos.")
            .assertCountEquals(0)
        rule.onAllNodesWithText("Resultados de las fuentes autorizadas.")
            .assertCountEquals(0)
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
    }
}
