package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class S4W02ObjectivesAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5W02ObjectivesDevActivity>()

    @Test fun objectivesFromChatCommitsProgressAndRestoresAtFontScale200() {
        rule.activityRule.scenario.onActivity { activity ->
            assertEquals("ar.com.faro.alpha.w02objectivesdev", activity.packageName)
            assertTrue(activity.resources.configuration.fontScale >= 1.95f)
        }
        click("Abrir Chat local de desarrollo")
        objectives()
        waitForText("Todavía no hay objetivos")
        click("Nuevo objetivo")
        fill("Nombre del objetivo", "Borrador descartado")
        click("Revisar objetivo")
        click("Descartar revisión")
        waitForText("Todavía no hay objetivos")

        click("Nuevo objetivo")
        fill("Nombre del objetivo", "Dibujo Android")
        fill("Finalidad", "Practicar en el taller")
        fill("Meta numérica opcional", "8")
        fill("Unidad o código de moneda", "láminas")
        click("Revisar objetivo")
        click("Ver revisión en Chat")
        clickComposeText("Revisar cambios")
        clickComposeText("Confirmar propuesta")
        clickComposeText("Ver propuesta aprobada")
        clickComposeText("Aplicar cambios")
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Aplicado y registrado").fetchSemanticsNodes().isNotEmpty()
        }

        objectives()
        click("Dibujo Android")
        waitForText("Meta 8 láminas")
        click("Editar objetivo")
        fill("Finalidad", "Practicar dibujo técnico")
        click("Revisar objetivo")
        commitNative()
        waitForText("Practicar dibujo técnico")

        click("Registrar avance")
        fill("Explicación del avance", "Primeras láminas terminadas")
        fill("Cantidad confirmada (láminas)", "3")
        click("Revisar avance")
        commitNative()
        waitForText("Avance confirmado 3 láminas")
        click("Corregir avance #1")
        fill("Cantidad confirmada (láminas)", "4")
        fill("Motivo de la corrección", "Faltaba una lámina")
        click("Revisar avance")
        commitNative()
        waitForText("Avance confirmado 4 láminas")

        click("Agregar próximo paso")
        fill("Nombre del paso", "Revisar cotas")
        click("Revisar paso")
        commitNative()
        waitForText("Pendiente · Revisar cotas")
        click("Completar Revisar cotas")
        commitNative()
        waitForText("Completado · Revisar cotas")
        assertAccessibleHeading("Historial")
        assertAccessibleHeading("Próximos pasos")

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForText("Avance confirmado 4 láminas")

        rule.activityRule.scenario.recreate()
        click("Abrir Chat local de desarrollo")
        objectives()
        click("Dibujo Android")
        waitForText("Avance confirmado 4 láminas")
        waitForText("Completado · Revisar cotas")
    }

    private fun objectives() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription("Abrir menú").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Abrir menú").assertIsDisplayed().performClick()
        clickComposeText("Objetivos")
    }

    private fun clickComposeText(label: String) {
        rule.waitUntil(20_000) { rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(label).performClick()
    }

    private fun commitNative() {
        click("Confirmar propuesta")
        click("Aplicar cambios")
        waitForText("Recibo ", prefix = true)
    }

    private fun click(label: String) {
        native<Button>({ it.text.toString() == label }) { assertTrue(it.performClick()) }
    }

    private fun fill(hint: String, value: String) {
        native<EditText>({ it.hint?.toString() == hint }) { it.setText(value) }
    }

    private fun waitForText(label: String, prefix: Boolean = false) {
        native<TextView>({ view ->
            if (prefix) view.text.toString().startsWith(label) else view.text.toString() == label
        }) {}
    }

    private fun assertAccessibleHeading(label: String) {
        native<TextView>({ it.text.toString() == label }) { assertTrue(it.isAccessibilityHeading) }
    }

    private inline fun <reified T : View> native(
        crossinline matches: (T) -> Boolean,
        crossinline onMatch: (T) -> Unit,
    ) {
        rule.waitUntil(timeoutMillis = 20_000) {
            var matched = false
            rule.activityRule.scenario.onActivity { activity ->
                val view = children(activity.window.decorView).filterIsInstance<T>()
                    .firstOrNull { it.isShown && matches(it) }
                if (view != null) {
                    onMatch(view)
                    matched = true
                }
            }
            matched
        }
    }

    private fun children(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            yieldAll(children(view.getChildAt(index)))
        }
    }
}
