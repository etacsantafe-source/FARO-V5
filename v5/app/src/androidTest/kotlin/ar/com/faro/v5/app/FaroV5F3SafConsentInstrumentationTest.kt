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
                val preview = all.filterIsInstance<Button>()
                    .single { it.text.toString().startsWith("Verificar archivo cifrado") }
                val previewConsent = all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Confirmo verificar") }
                val restore = all.filterIsInstance<Button>()
                    .single { it.text.toString().startsWith("Elegir archivo") }
                val exportConsent = all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Confirmo exportar") }
                val restoreConsent = all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Confirmo restaurar") }
                val share = all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Para exportar") }
                val password = all.filterIsInstance<EditText>().single()
                assertFalse("No trusted restore journal provided", restore.isEnabled)
                preview.performClick()
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("Verificar requiere confirmación")
                })
                assertFalse(previewConsent.isChecked)
                export.performClick()
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("requiere confirmar")
                })
                // Restore consent must never authorize export.
                restoreConsent.isChecked = true
                share.isChecked = true
                password.setText("synthetic-passphrase-123")
                export.performClick()
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("requiere confirmar")
                })
                restoreConsent.isChecked = false
                exportConsent.isChecked = true
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
            scenario.onActivity { activity ->
                val all = views(activity.findViewById(android.R.id.content))
                assertFalse(all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Confirmo exportar") }.isChecked)
                assertFalse(all.filterIsInstance<CheckBox>()
                    .single { it.text.toString().startsWith("Para exportar") }.isChecked)
            }
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        }
    }

    @Test fun invalidPassphrasesRevokeAllConsentBeforeOpeningSaf() {
        ActivityScenario.launch(FaroV5FiveStoreSafConsentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val all = views(activity.findViewById(android.R.id.content))
                val password = all.filterIsInstance<EditText>().single()
                val checks = all.filterIsInstance<CheckBox>()
                val previewConsent = checks.single {
                    it.text.toString().startsWith("Confirmo verificar")
                }
                val preview = all.filterIsInstance<Button>().single {
                    it.text.toString().startsWith("Verificar archivo cifrado")
                }
                previewConsent.isChecked = true
                password.setText(" ".repeat(12))
                preview.performClick()
                assertEquals("", password.text.toString())
                assertTrue(checks.all { !it.isChecked })
                assertTrue(preview.isEnabled)
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("entre 12 y 1024")
                })

                val exportConsent = checks.single {
                    it.text.toString().startsWith("Confirmo exportar")
                }
                val share = checks.single {
                    it.text.toString().startsWith("Para exportar")
                }
                val export = all.filterIsInstance<Button>().single {
                    it.text.toString() == "Exportar respaldo cifrado"
                }
                exportConsent.isChecked = true
                share.isChecked = true
                password.setText("x".repeat(1025))
                export.performClick()
                assertEquals("", password.text.toString())
                assertTrue(checks.all { !it.isChecked })
                assertTrue(export.isEnabled)
            }
        }
    }

    @Test fun credentialsAndConsentAreDiscardedOnActivityRecreation() {
        ActivityScenario.launch(FaroV5FiveStoreSafConsentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val all = views(activity.findViewById(android.R.id.content))
                all.filterIsInstance<EditText>().single().setText("synthetic-passphrase-123")
                all.filterIsInstance<CheckBox>().forEach { it.isChecked = true }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val all = views(activity.findViewById(android.R.id.content))
                assertEquals("", all.filterIsInstance<EditText>().single().text.toString())
                assertTrue(all.filterIsInstance<CheckBox>().all { !it.isChecked })
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("Sesión anterior descartada")
                })
                assertFalse(all.filterIsInstance<Button>()
                    .single { it.text.toString().startsWith("Elegir archivo") }.isEnabled)
            }
        }
    }


    @Test fun destroyWipesQueuedOrInFlightBackupPassphrase() {
        lateinit var secret: CharArray
        val scenario = ActivityScenario.launch(FaroV5FiveStoreSafConsentActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val input = views(activity.findViewById(android.R.id.content))
                    .filterIsInstance<EditText>().single()
                input.setText("synthetic-queued-secret-2026")
                val take = activity.javaClass.getDeclaredMethod("takePassphrase")
                take.isAccessible = true
                secret = take.invoke(activity) as CharArray
                assertTrue(secret.any { it != '\u0000' })
            }
        } finally {
            scenario.close()
        }
        assertTrue(secret.all { it == '\u0000' })
    }


    @Test fun debugQuarantinedRestoreRequiresConsentWithoutOpeningPicker() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val launch = android.content.Intent(
            context, FaroV5FiveStoreSafConsentActivity::class.java,
        ).putExtra(FaroV5FiveStoreSafConsentActivity.EXTRA_DEBUG_FRESH_RESTORE, true)
        ActivityScenario.launch<FaroV5FiveStoreSafConsentActivity>(launch).use { scenario ->
            scenario.onActivity { activity ->
                val all = views(activity.findViewById(android.R.id.content))
                val restore = all.filterIsInstance<Button>()
                    .single { it.text.toString().startsWith("Elegir archivo") }
                assertTrue("only debug test path may offer quarantine restore", restore.isEnabled)
                restore.performClick()
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.toString().contains("Restaurar requiere confirmación")
                })
                assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
            }
        }
    }
}
