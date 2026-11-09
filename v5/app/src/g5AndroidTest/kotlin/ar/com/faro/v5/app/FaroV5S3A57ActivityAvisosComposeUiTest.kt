package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
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

/**
 * X01/X02 directed real Android API35 emulated View -> Compose UI acceptance.
 * Only isolated V2CoreDev local Workspace owners. No production V2, IA,
 * Apply, OS notifications, physical G15, fake stores, or injected review.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57ActivityAvisosComposeUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun allButtons(root: View): List<Button> = buildList {
        fun visit(view: View) {
            if (view is Button) add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
    }

    /** Actual RESUMED native user click; no direct internal host authority call. */
    private fun openNativeChat(buttonLabel: String) {
        rule.runOnUiThread {
            val root = rule.activity.findViewById<View>(android.R.id.content)
            val button = allButtons(root).single { it.text.toString() == buttonLabel }
            assertTrue("Foreground native action is disabled", button.isEnabled)
            assertTrue("Native click was rejected", button.performClick())
        }
        // Five real stores and their canonical Workspace are opened on an executor.
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en esta conversación",
                    useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun goTo(destination: String) {
        // The real compact shell drawer is modal on the Pixel_5 API35 emulator.
        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText(destination).performClick()
        rule.waitForIdle()
    }

    private fun sendRealUserTurn(): String {
        val body = "X01ActivityWorkspaceUser-" + UUID.randomUUID().toString()
        val editors = rule.onAllNodes(hasSetTextAction())
        assertTrue("Search and Chat compose editors required",
            editors.fetchSemanticsNodes().size >= 2)
        editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled()
        rule.onNodeWithText("Enviar").performClick()
        // Do not confuse text still present in the edit field with an accepted
        // Workspace USER turn; it must be in timeline AND the draft cleared.
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onNodeWithText("Enviar").assertIsNotEnabled()
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        return body
    }

    @Test
    fun x01AuthorizedActivityShowsCanonicalUserEventAndRevokesOnBackground() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        openNativeChat("Abrir Chat local de desarrollo")
        val privateChatText = sendRealUserTurn()
        // Recreation closes the previous session and forces a separate real
        // native foreground authorization for the Activity read.
        rule.activityRule.scenario.recreate()
        openNativeChat("Abrir Chat y Actividad local")
        goTo("Actividad")
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Mensaje de usuario")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onNodeWithText("Mensaje de usuario").assertExists()
        assertTrue("Workspace provenance must not be fabricated",
            rule.onAllNodesWithText("Hora declarada por emisor (UTC)",
                substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText(privateChatText, substring = true)
            .assertCountEquals(0)
        rule.onAllNodesWithText("Resumen local solicitado:", substring = true)
            .assertCountEquals(0)
        rule.onNodeWithText("Ver resumen local").performClick()
        rule.waitUntil(15_000L) {
            runCatching {
                rule.onAllNodesWithText("en la página autorizada.",
                    substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("en la página autorizada.", substring = true)
            .assertCountEquals(1)
        // No durable privilege across native Android onPause even when the
        // screen has already shown a page.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Actividad no disponible. No se consultaron datos.")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("Mensaje de usuario").assertCountEquals(0)
        rule.onAllNodesWithText("Resumen local solicitado:", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun x01NormalChatNeverGrantsActivityReadOrInventsEmptyHistory() {
        openNativeChat("Abrir Chat local de desarrollo")
        goTo("Actividad")
        rule.onNodeWithText("Actividad no disponible. No se consultaron datos.")
            .assertExists()
        rule.onAllNodesWithText("No hay actividad en las dos fuentes autorizadas.",
            substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Ver resumen local").assertCountEquals(0)
        rule.onAllNodesWithText("Resumen local solicitado:", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun x02AbsentAvisosOwnerNeverShowsFalseZeroOrMarksRead() {
        openNativeChat("Abrir Chat local de desarrollo")
        goTo("Avisos")
        rule.onNodeWithText("Avisos no disponibles. No se consultaron datos.")
            .assertExists()
        rule.onAllNodesWithText("No hay avisos abiertos.").assertCountEquals(0)
        rule.onAllNodesWithText("Ver resumen local").assertCountEquals(0)
        rule.onAllNodesWithText("Resumen solicitado:", substring = true)
            .assertCountEquals(0)
    }
}
