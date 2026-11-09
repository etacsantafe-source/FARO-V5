package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X12 native entry acceptance, real isolated Android DEV on disposable emulator.
 * No Moto G15, no model/Apply, no claim about the Compose Chat subtree.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57AndroidNativeLifecycleTest {
    private val expectedActions = setOf(
        "Abrir Chat local de desarrollo",
        "Abrir Chat y Actividad local",
        "Consultar vencimientos registrados (DEV)",
        "Crear Reality8 vacía (DEV)",
    )

    private fun buttons(view: View): List<Button> = buildList {
        fun visit(child: View) {
            if (child is Button) add(child)
            if (child is ViewGroup)
                for (i in 0 until child.childCount) visit(child.getChildAt(i))
        }
        visit(view)
    }

    private fun texts(view: View): List<String> = buildList {
        fun visit(child: View) {
            if (child is TextView) add(child.text.toString())
            if (child is ViewGroup)
                for (i in 0 until child.childCount) visit(child.getChildAt(i))
        }
        visit(view)
    }

    private fun rootState(root: Path): List<String> {
        if (!Files.exists(root)) return emptyList()
        Files.walk(root).use { nodes ->
            return nodes.map { file ->
                val type = if (Files.isDirectory(file)) "dir" else "file"
                val size = if (Files.isRegularFile(file)) Files.size(file) else 0L
                root.relativize(file).toString() + "|" + type + "|" + size
            }.sorted().collect(Collectors.toList())
        }
    }

    @Test
    fun nativeLauncherHasFourAccessibleActionsWithMinimumTouchTargets() {
        ActivityScenario.launch(FaroV5V2CoreDevActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("ar.com.faro.alpha.v2coredev", activity.packageName)
                assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                val root = activity.findViewById<View>(android.R.id.content)
                val controls = buttons(root)
                assertEquals(expectedActions, controls.map { it.text.toString() }.toSet())
                assertEquals(4, controls.size)
                val px48 = (48f * activity.resources.displayMetrics.density).toInt()
                controls.forEach { control ->
                    assertTrue("Control disabled: " + control.text, control.isEnabled)
                    assertTrue("Control not clickable: " + control.text, control.isClickable)
                    assertTrue("Control not focusable: " + control.text, control.isFocusable)
                    assertTrue("Touch target too short: " + control.text,
                        control.height >= px48)
                    assertNotEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                        control.importantForAccessibility)
                }
                assertTrue(texts(root).any { it.contains("V2 habitual permanece apagada") })
                assertFalse(texts(root).any { it.contains("Solicitar Apply") })
            }
        }
    }

    @Test
    fun pausedUpgradeCannotSwitchRealityAndRecreateDoesNotReplayClick() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = context.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
        val before = rootState(storage)
        ActivityScenario.launch(FaroV5V2CoreDevActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                assertEquals(Lifecycle.State.STARTED, activity.lifecycle.currentState)
                val view = activity.findViewById<View>(android.R.id.content)
                val upgrade = buttons(view)
                    .single { it.text.toString() == "Crear Reality8 vacía (DEV)" }
                assertTrue(upgrade.performClick())
                assertTrue(texts(view).any {
                    it.contains("Sin abrir. V2 habitual permanece apagada")
                })
            }
            assertEquals("Paused click cannot modify Reality",
                before, rootState(storage))
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                val root = activity.findViewById<View>(android.R.id.content)
                assertEquals(expectedActions, buttons(root).map { it.text.toString() }.toSet())
                assertTrue(texts(root).any {
                    it.contains("Sin abrir. V2 habitual permanece apagada")
                })
            }
            assertEquals("Recreation cannot replay privileged upgrade",
                before, rootState(storage))
        }
    }
}
