package ar.com.faro.v5.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Activity + callback integration without launching a real document
 * provider or granting consent. Real SAF picker behavior remains a physical T4
 * case; callback injection is intentionally scoped to this debug Activity.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5T4SafHardeningInstrumentationTest {
    private fun descendants(v: View): List<View> =
        listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap {
            descendants(v.getChildAt(it))
        } else emptyList()

    private fun views(activity: FaroV5T4SafIntegralActivity) =
        descendants(activity.findViewById(android.R.id.content))

    private fun consent(activity: FaroV5T4SafIntegralActivity): CheckBox =
        views(activity).filterIsInstance<CheckBox>().single()

    private fun status(activity: FaroV5T4SafIntegralActivity): TextView =
        views(activity).filterIsInstance<TextView>().last()

    private fun injectCallback(
        activity: FaroV5T4SafIntegralActivity,
        operation: String,
        result: ActivityResult,
    ) {
        val stepClass = Class.forName(
            "ar.com.faro.v5.app.FaroV5T4SafIntegralActivity\$Step"
        )
        val step = stepClass.enumConstants.single {
            (it as Enum<*>).name == operation
        }
        val callback = FaroV5T4SafIntegralActivity::class.java.getDeclaredMethod(
            "handlePicked", stepClass, ActivityResult::class.java
        )
        callback.isAccessible = true
        callback.invoke(activity, step, result)
    }

    @Test fun pickerResultNeedsFreshCheckboxOnAndroidAndRecreationResetsIt() {
        ActivityScenario.launch(FaroV5T4SafIntegralActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(consent(activity).isChecked)
                assertFalse(consent(activity).isEnabled)
                injectCallback(activity, "RESTORE",
                    ActivityResult(Activity.RESULT_OK,
                        Intent().setData(Uri.parse("content://t4.test/nonexistent"))))
                assertTrue(consent(activity).isEnabled)
                assertFalse(consent(activity).isChecked)
                // Recreated Activity may recover a URI, never the checkbox.
                consent(activity).isChecked = true
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(consent(activity).isEnabled)
                assertFalse(consent(activity).isChecked)
                views(activity).filterIsInstance<Button>().single {
                    it.text.toString().contains("Confirmar operación")
                }.performClick()
                assertTrue(status(activity).text.toString().contains("Falta consentimiento"))
            }
        }
    }

    @Test fun cancelledPickerCannotLeavePriorGrantOrEnabledConsent() {
        ActivityScenario.launch(FaroV5T4SafIntegralActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                injectCallback(activity, "EXPORT",
                    ActivityResult(Activity.RESULT_OK,
                        Intent().setData(Uri.parse("content://t4.test/selected"))))
                assertTrue(consent(activity).isEnabled)
                injectCallback(activity, "EXPORT",
                    ActivityResult(Activity.RESULT_CANCELED, null))
                assertFalse(consent(activity).isEnabled)
                assertFalse(consent(activity).isChecked)
                assertTrue(status(activity).text.toString().contains("CANCELADO"))
            }
        }
    }

    @Test fun invalidSchemeIsRejectedBeforeAnyWriting() {
        ActivityScenario.launch(FaroV5T4SafIntegralActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                injectCallback(activity, "RESTORE",
                    ActivityResult(Activity.RESULT_OK,
                        Intent().setData(Uri.parse("file:///tmp/unsafe.farobak"))))
                assertFalse(consent(activity).isEnabled)
                assertTrue(status(activity).text.toString().contains("URI distinto"))
                assertFalse(consent(activity).isChecked)
            }
        }
    }

    @Test fun pickerOkWithoutDocumentDoesNotGrantConsent() {
        ActivityScenario.launch(FaroV5T4SafIntegralActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                injectCallback(activity, "RESTORE",
                    ActivityResult(Activity.RESULT_OK, Intent()))
                assertFalse(consent(activity).isEnabled)
                assertTrue(status(activity).text.toString().contains("OK sin archivo"))
            }
        }
    }
}
