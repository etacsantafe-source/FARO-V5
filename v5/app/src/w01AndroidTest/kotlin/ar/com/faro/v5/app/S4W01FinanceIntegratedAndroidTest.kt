package ar.com.faro.v5.app

import android.app.Application
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class S4W01FinanceIntegratedAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun nativeFinanceRecordsAccountsMovementTransferAndBudgetAndReopensReceipts() {
        val app = instrumentation.targetContext.applicationContext as Application
        assertEquals(S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID, app.packageName)
        val authority = FaroV5V2CoreDevAuthority(app.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root"), w01Finance9 = true)
        authority.open().close()
        authority.upgradePlanning8AfterExplicitForegroundRequest { true }.close()
        authority.upgradeFinance9AfterExplicitForegroundRequest { true }.close()

        ActivityScenario.launch(FaroV5W01FinanceDevLauncherActivity::class.java).use { launcher ->
            val open = "Finanzas (DEV · sin huella ni PIN)"
            launcher.onActivity { root -> findButton(root.window.decorView, open).performClick() }
            waitText("Sin cuentas registradas")
            verifyAccessibility()

            click("Nueva cuenta")
            fill("Nombre de la cuenta", "Descartada Android")
            fill("Saldo inicial declarado", "2.00")
            click("Revisar")
            click("Descartar revisión")
            waitText("Revisión descartada.")
            waitText("Sin cuentas registradas")

            addAccount("Caja Android W01", "25.00")
            addAccount("Ahorro Android W01", "0")
            click("Ingreso o gasto")
            fill("Importe", "3.00")
            fill("Concepto", "Ingreso Android W01")
            fill("Categoría", "Otros")
            click("Revisar")
            confirm()
            waitText("Ingreso Android W01")

            click("Transferencia interna")
            onFinance { root ->
                val from = spinner(root, "Cuenta de origen")
                val to = spinner(root, "Cuenta de destino")
                from.setSelection(spinnerIndex(from, "Caja Android W01"))
                to.setSelection(spinnerIndex(to, "Ahorro Android W01"))
            }
            fill("Importe", "5.00")
            fill("Concepto", "Transferencia comprobada Android")
            click("Revisar")
            confirm()
            waitText("Transferencia comprobada Android")

            click("Presupuesto mensual")
            fill("Categoría", "Otros")
            fill("Límite mensual", "30.00")
            click("Revisar")
            confirm()
            waitText("Recibo ")
            click("Presupuesto mensual")
            fill("Categoría", "Otros")
            click("Consultar presupuesto")
            waitText("Límite: ARS 30.00")

            click("Deudas, calendarios y cuotas")
            click("Declarar deuda")
            fill("Nombre de la deuda", "Deuda de prueba Android")
            fill("Acreedor", "Dato simulado")
            fill("Importe inicial", "100.00")
            click("Revisar")
            confirm()
            waitFor { root -> views(root).filterIsInstance<Button>().none { it.text == "Confirmar registro" } }
            click("Deudas, calendarios y cuotas")
            click("Consultar vencimientos")
            waitText("Deuda de prueba Android")
            val debt = matchingLine("Deuda Deuda de prueba Android").substringAfterLast(" · ")

            click("Crear calendario")
            fill("Identificador de deuda (opcional)", debt)
            fill("Importe de cada cuota", "25.00")
            click("Revisar")
            confirm()
            click("Deudas, calendarios y cuotas")
            click("Consultar vencimientos")
            waitText("Calendario schedule-")
            val schedule = matchingLine("Calendario schedule-").substringAfter("Calendario ").substringBefore(" · ")

            click("Declarar cuota")
            fill("Identificador de calendario (opcional)", schedule)
            fill("Versión si hay calendario", "1")
            fill("Identificador de deuda (opcional)", debt)
            fill("Importe previsto", "25.00")
            click("Revisar")
            confirm()
            click("Deudas, calendarios y cuotas")
            click("Consultar vencimientos")
            waitText("Vencido · ARS 25.00")
            val item = matchingLine("Vencido · ARS 25.00").substringAfterLast(" · ")

            click("Registrar pago")
            fill("Identificador de cuota", item)
            fill("Identificador de deuda (opcional)", debt)
            fill("Importe pagado", "10.00")
            fill("Concepto", "Pago parcial Android W01")
            fill("Categoría", "Cuotas")
            click("Revisar")
            confirm()
            click("Deudas, calendarios y cuotas")
            click("Consultar vencimientos")
            waitText("Pago parcial · ARS 15.00")

            click("Volver al Chat DEV")
            launcher.onActivity { root -> findButton(root.window.decorView, open).performClick() }
            waitText("Caja Android W01")
            waitText("Recibo ")
            onFinance { root ->
                assertTrue(views(root).filterIsInstance<TextView>().none { it.text.contains("Descartada Android") })
            }
            click("Volver al Chat DEV")
        }
    }

    private fun verifyAccessibility() {
        onFinance { root ->
            val navigation = findButton(root, "Nueva cuenta")
            assertTrue(navigation.minimumHeight >= (48 * navigation.resources.displayMetrics.density).toInt())
            assertTrue(navigation.resources.configuration.fontScale >= 1.9f)
            assertTrue(views(root).any { it is ScrollView })
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text == "Operaciones" && it.isAccessibilityHeading })
        }
    }

    private fun addAccount(name: String, opening: String) {
        click("Nueva cuenta")
        fill("Nombre de la cuenta", name)
        fill("Saldo inicial declarado", opening)
        click("Revisar")
        confirm()
        waitText(name)
    }

    private fun confirm() {
        waitFor { root -> views(root).filterIsInstance<Button>().any { it.text == "Confirmar registro" } }
        onFinance { root ->
            val button = findButton(root, "Confirmar registro")
            button.performClick()
            button.performClick()
        }
        waitFor { root -> views(root).filterIsInstance<Button>().none { it.text == "Confirmar registro" } }
    }

    private fun matchingLine(text: String): String {
        var result = ""
        onFinance { root ->
            result = views(root).filterIsInstance<TextView>().flatMap { it.text.toString().lineSequence() }
                .first { it.contains(text) }
        }
        return result
    }

    private fun click(label: String) {
        waitFor { root -> views(root).filterIsInstance<Button>().any { it.text.toString() == label } }
        onFinance { root -> findButton(root, label).performClick() }
    }

    private fun fill(label: String, value: String) = onFinance { root ->
        val text = views(root).filterIsInstance<TextView>().first { it.text.toString() == label && it.labelFor != View.NO_ID }
        root.findViewById<EditText>(text.labelFor).setText(value)
    }

    private fun spinner(root: View, label: String): Spinner {
        val text = views(root).filterIsInstance<TextView>().first { it.text.toString() == label && it.labelFor != View.NO_ID }
        return root.findViewById(text.labelFor)
    }

    private fun spinnerIndex(spinner: Spinner, name: String): Int =
        (0 until spinner.count).first { spinner.getItemAtPosition(it).toString().startsWith(name) }

    private fun waitText(text: String) =
        waitFor { root -> views(root).filterIsInstance<TextView>().any { it.text.toString().contains(text) } }

    private fun waitFor(predicate: (View) -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15_000
        do {
            var matches = false
            instrumentation.runOnMainSync {
                val screen = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FaroV5W01FinanceReadDevActivity>().singleOrNull()
                matches = screen?.window?.decorView?.let(predicate) ?: false
            }
            if (matches) return
            SystemClock.sleep(75)
        } while (SystemClock.elapsedRealtime() < until)
        error("No se encontró el estado esperado de Finanzas")
    }

    private fun onFinance(action: (View) -> Unit) = instrumentation.runOnMainSync {
        val screen = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<FaroV5W01FinanceReadDevActivity>().single()
        action(screen.window.decorView)
    }

    private fun findButton(root: View, label: String): Button =
        views(root).filterIsInstance<Button>().first { it.text.toString() == label }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
}
