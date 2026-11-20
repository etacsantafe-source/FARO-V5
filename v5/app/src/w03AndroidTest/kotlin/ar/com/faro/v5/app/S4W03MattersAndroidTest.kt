package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** W03-G: real isolated DEV graph and Android Activity; never mocks Apply receipts. */
@RunWith(AndroidJUnit4::class)
class S4W03MattersAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5W03MattersDevActivity>()

    @Test fun mattersReminderOwnerReviewApplyAndForegroundRecoveryFont200() {
        rule.activityRule.scenario.onActivity {
            assertEquals("ar.com.faro.alpha.w03mattersdev", it.packageName)
            assertTrue("Font must be 200%", it.resources.configuration.fontScale >= 1.95f)
        }

        // A newly installed W03 DEV UID starts with an ordinary isolated core.
        // Every upgrade uses a separate foreground click and closes its previous session.
        bootstrap("Abrir Chat local de desarrollo")
        bootstrap("Crear Reality8 vacía (DEV)")
        bootstrap("Activar Finance9 (DEV, después de Reality8)")
        bootstrap("Activar Auxiliary4 (DEV, después de Finance9)")
        click("Abrir Chat y Avisos locales")
        awaitMenu()
        openMatters()
        click("Abrir Asuntos")
        waitForText("No hay asuntos.")

        click("Nuevo asunto")
        fill("Título", "Borrador que no debe guardarse")
        click("Revisar propuesta")
        waitForText("Revisión de cambios")
        click("Descartar revisión")
        click("Abrir Asuntos")
        waitForText("No hay asuntos.")

        click("Nuevo asunto")
        fill("Título", "Asunto W03 Android")
        fill("Detalle", "Validación de Review y recibo")
        click("Revisar propuesta")
        commit()
        waitForText("Asunto W03 Android · Abierto")

        click("Asunto W03 Android · Abierto")
        waitForText("Estado: Abierto", prefix = true)
        click("Cambiar estado")
        fill("Motivo", "Seguimiento iniciado")
        click("Revisar estado")
        commit()
        waitForText("Estado: En curso", prefix = true)
        accessibleHeading("Historial")
        // Undo must add a compensating event, not delete the original status event.
        click("Revertir último cambio de estado")
        commit()
        waitForText("Estado: Abierto", prefix = true)
        accessibleHeading("Historial")
        click("Cambiar estado")
        fill("Motivo", "Continuar tras Undo certificado")
        click("Revisar estado")
        commit()
        waitForText("Estado: En curso", prefix = true)

        // Validation errors must not silently change Reality or bypass Review.
        click("Cambiar vencimiento")
        fill("Fecha AAAA-MM-DD (vacío para quitar)", "fecha inválida")
        fill("Motivo", "No debe aplicarse")
        click("Revisar fecha")
        waitForText("Revisá campos, permisos, fechas y motivos obligatorios.")
        click("Cancelar")
        waitForText("Estado: En curso", prefix = true)

        click("Cambiar vencimiento")
        fill("Fecha AAAA-MM-DD (vacío para quitar)", "2031-06-15")
        fill("Motivo", "Vencimiento revisado y confirmado")
        click("Revisar fecha")
        commit()
        waitForText("Vence: 2031-06-15")

        click("Editar asunto")
        fill("Detalle", "Edición persistente validada en Android")
        fill("Motivo de edición", "Actualización de información")
        click("Revisar propuesta")
        commit()
        waitForText("Edición persistente validada en Android")
        accessibleHeading("Historial")

        click("Recordatorios")
        waitForText("Recordatorios")
        click("Nuevo recordatorio")
        fill("Título", "Recordatorio W03 Android")
        click("Revisar recordatorio")
        commit()
        waitForText("Recordatorio W03 Android")
        click("Conciliar avisos locales")
        click("Confirmar conciliación local")
        waitForText("Avisos creados: 1", prefix = true)
        click("Volver a Recordatorios")
        openAvisos()
        awaitComposeText("Recordatorio W03 Android")
        clickComposeText("Revisar visto")
        clickComposeText("Confirmar visto")
        // The first click is asynchronous. A NEW canonical owner read must show
        // SEEN before Dismiss can use its updated revision (CAS otherwise rejects).
        awaitComposeText("Visto")
        clickComposeText("Revisar descarte")
        clickComposeText("Confirmar descarte")
        awaitComposeText("No hay avisos abiertos.")
        rule.activityRule.scenario.recreate()
        click("Abrir Chat local de desarrollo")
        awaitMenu()
        openMatters()
        click("Abrir Recordatorios")
        click("Recordatorio W03 Android")
        click("Editar recordatorio")
        fill("Título", "Recordatorio W03 actualizado")
        click("Revisar recordatorio")
        commit()
        waitForText("Recordatorio W03 actualizado")
        click("Desactivar regla")
        commit()
        click("Volver a Recordatorios")
        waitForText("Recordatorio W03 actualizado · Desactivado")

        // On background, stale per-view grants must be revoked and never auto-resurrect.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForText("Lectura revocada")
        click("Abrir Asuntos")
        waitForText("Asunto W03 Android · En curso")

        // A reopened DEV session must read the canonical Reality state, not a cached view.
        rule.activityRule.scenario.recreate()
        click("Abrir Chat local de desarrollo")
        awaitMenu()
        openMatters()
        click("Abrir Asuntos")
        waitForText("Asunto W03 Android · En curso")
        click("Asunto W03 Android · En curso")
        waitForText("Edición persistente validada en Android")
        waitForText("Vence: 2031-06-15")
        accessibleHeading("Historial")

        // An authorized Matter must be selectable by label, without knowing its UUID.
        click("Recordatorios")
        waitForText("Recordatorios")
        click("Nuevo recordatorio")
        fill("Título", "Recordatorio vinculado W03")
        click("Elegir asunto permitido")
        click("Vincular: Asunto W03 Android")
        waitForText("Seleccionado: Asunto W03 Android")
        click("Revisar recordatorio")
        commit()
        click("Recordatorio vinculado W03")
        waitForText("Asunto vinculado:", prefix = true)

        rule.activityRule.scenario.recreate()
        click("Abrir Chat local de desarrollo")
        awaitMenu()
        openMatters()
        click("Abrir Recordatorios")
        click("Recordatorio vinculado W03")
        waitForText("Asunto vinculado:", prefix = true)
    }

    private fun bootstrap(button: String) {
        click(button)
        awaitMenu()
        rule.activityRule.scenario.recreate()
    }

    private fun awaitMenu() {
        try {
            rule.waitUntil(30_000) {
                rule.onAllNodesWithContentDescription("Abrir menú").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Exception) {
            val tree = rule.onRoot(useUnmergedTree = true).printToString().take(5000)
            throw AssertionError("No se abrió Chat DEV: " + tree, failure)
        }
    }

    private fun openMatters() {
        rule.onNodeWithContentDescription("Abrir menú").assertIsDisplayed().performClick()
        val target = hasText("Asuntos") and hasClickAction()
        rule.waitUntil(25_000) {
            rule.onAllNodes(target).fetchSemanticsNodes().size == 1
        }
        rule.onNode(target).performClick()
        waitForText("Asuntos y Recordatorios")
    }

    private fun openAvisos() {
        rule.onNodeWithContentDescription("Abrir menú").assertIsDisplayed().performClick()
        val target = hasText("Avisos") and hasClickAction()
        rule.waitUntil(25_000) { rule.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        rule.onNode(target).performClick()
        awaitComposeText("Avisos abiertos de la fuente autorizada.")
    }

    private fun clickComposeText(label: String) {
        awaitComposeText(label)
        rule.onNodeWithText(label).performClick()
    }

    private fun awaitComposeText(label: String) {
        try {
            rule.waitUntil(25_000) {
                rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Exception) {
            val tree = rule.onRoot(useUnmergedTree = true).printToString().take(7_000)
            throw AssertionError("W03-G no aparece '$label' en Compose. Estado real:\n" + tree, failure)
        }
    }

    private fun commit() {
        waitForText("Revisión de cambios")
        click("Aprobar propuesta")
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
        native<TextView>({ if (prefix) it.text.toString().startsWith(label)
            else it.text.toString() == label }) {}
    }
    private fun accessibleHeading(label: String) {
        native<TextView>({ it.text.toString() == label }) { assertTrue(it.isAccessibilityHeading) }
    }

    private inline fun <reified T: View> native(
        crossinline match: (T) -> Boolean,
        crossinline action: (T) -> Unit,
    ) {
        rule.waitUntil(timeoutMillis = 30_000) {
            var matched = false
            rule.activityRule.scenario.onActivity { activity ->
                val v = descendants(activity.window.decorView).filterIsInstance<T>()
                    .firstOrNull { it.isShown && match(it) }
                if (v != null) { action(v); matched = true }
            }
            matched
        }
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount)
            yieldAll(descendants(view.getChildAt(index)))
    }
}
