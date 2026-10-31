package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S2 F3 Android consent surface: no restore journal, no V2 certification,
 * no external picker, and no SAF provider access. T4 physical is separate.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5F3SafConsentInstrumentationTest {
    private fun views(root: View): List<View> =
        listOf(root) + if (root is ViewGroup)
            (0 until root.childCount).flatMap { views(root.getChildAt(it)) }
        else emptyList()

    @Test fun noConsentOrV2CertificationCannotExportOrRestore() {
        ActivityScenario.launch(FaroV5FiveStoreSafConsentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val all = views(activity.findViewById(android.R.id.content))
                val export = all.filterIsInstance<Button>()
                    .single { it.text.toString() == "Exportar respaldo cifrado" }
                val restore = all.filterIsInstance<Button>()
                    .single { it.text.toString().startsWith("Elegir archivo") }
                val confirm = all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Confirmo") }
                val share = all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Para exportar") }
                val password = all.filterIsInstance<EditText>().single()
                assertFalse("No trusted restore journal provided", restore.isEnabled)
                export.performClick()
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("requiere confirmar")
                })
                confirm.isChecked = true
                share.isChecked = true
                password.setText("synthetic-passphrase-123")
                export.performClick()
            }
            var denied = false
            for (attempt in 0 until 100) {
                scenario.onActivity { activity ->
                    denied = views(activity.findViewById(android.R.id.content))
                        .filterIsInstance<TextView>()
                        .any { it.text.toString().contains("Exportación no autorizada") }
                }
                if (denied) break
                Thread.sleep(50)
            }
            assertTrue("V2 OFF must deny export before SAF picker", denied)
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        }
    }
}
