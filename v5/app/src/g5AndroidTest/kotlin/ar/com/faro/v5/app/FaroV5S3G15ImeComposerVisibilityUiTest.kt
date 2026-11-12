package ar.com.faro.v5.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Only the disposable API35 emulator; never send a message or access a physical device. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3G15ImeComposerVisibilityUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val draft = "IME visibility synthetic draft only"
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun openChat() {
        check(android.os.Build.FINGERPRINT.contains("emulator") ||
            android.os.Build.MODEL.contains("sdk", ignoreCase = true))
        assertEquals("ar.com.faro.g15isolated.v2coredev", rule.activity.packageName)
        rule.runOnUiThread {
            fun buttons(view: View): List<Button> = buildList {
                if (view is Button) add(view)
                if (view is ViewGroup) for (i in 0 until view.childCount) addAll(buttons(view.getChildAt(i)))
            }
            assertTrue(buttons(rule.activity.findViewById(android.R.id.content))
                .single { it.text.toString() == "Abrir Chat local de desarrollo" }.performClick())
        }
        rule.waitUntil(60_000) {
            runCatching { rule.onAllNodes(hasSetTextAction() and hasText("Preguntale a FARO"))
                .fetchSemanticsNodes().size == 1 }.getOrDefault(false)
        }
        val composer = rule.onNode(hasSetTextAction() and hasText("Preguntale a FARO"))
        composer.performTouchInput { click() }
        composer.performTextReplacement(draft)
    }

    private fun imeVisible(): Boolean {
        var visible = false
        rule.runOnUiThread { visible = rule.activity.window.decorView.rootWindowInsets
            ?.isVisible(WindowInsets.Type.ime()) == true }
        return visible
    }

    private fun visibleAboveIme(name: String) {
        try {
            rule.waitUntil(30_000) { imeVisible() }
        } catch (failure: Throwable) {
            val dir = File(rule.activity.getExternalFilesDir(null), "ime-evidence").apply { mkdirs() }
            File(dir, "$name-timeout.txt").writeText(
                "windowFocus=${rule.activity.hasWindowFocus()}\nlifecycle=${rule.activity.lifecycle.currentState}\n" +
                    shell("dumpsys input_method") + shell("dumpsys window windows"))
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(dir, "$name-timeout.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            throw failure
        }
        rule.waitForIdle()
        var imeTop = 0
        var imeHeight = 0
        rule.runOnUiThread {
            imeHeight = rule.activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
            imeTop = rule.activity.windowManager.currentWindowMetrics.bounds.height() - imeHeight
        }
        assertTrue("A real OS IME must occupy screen pixels", imeHeight > 0)
        val composer = rule.onNode(hasSetTextAction() and hasText(draft))
        val send = rule.onNodeWithText("Enviar")
        composer.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        send.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        val bounds = listOf(composer, send).map { it.fetchSemanticsNode().boundsInWindow }

        val dir = File(rule.activity.getExternalFilesDir(null), "ime-evidence").apply { mkdirs() }
        File(dir, "$name.txt").writeText("imeVisible=true\nimeHeight=$imeHeight\nimeTop=$imeTop\ncontrols=$bounds\n" +
            shell("dumpsys input_method"))
        val bitmap = checkNotNull(automation.takeScreenshot())
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        bounds.forEach {
            assertTrue("control $it overlaps actual IME top=$imeTop height=$imeHeight",
                it.top >= 0f && it.height > 0f && it.bottom <= imeTop + 1f)
        }
        // Clicking the editor by real touch proves reachability without sending anything.
        composer.performTouchInput { click() }
    }

    private fun hideAndVerify() {
        shell("input keyevent 4")
        rule.waitUntil(30_000) { !imeVisible() }
        rule.onNode(hasSetTextAction() and hasText(draft)).assertIsDisplayed()
        rule.onNodeWithText("Enviar").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun imeComposerAndSendAboveRealKeyboardThenHidden() {
        openChat()
        visibleAboveIme("shown")
        hideAndVerify()
    }

    @Test fun imeComposerRemainsReachableAfterBackgroundAndResume() {
        openChat()
        visibleAboveIme("before-background")
        hideAndVerify()
        // A real task transition, rather than lifecycle simulation without window focus.
        shell("input keyevent KEYCODE_HOME")
        rule.waitUntil(30_000) { !rule.activity.hasWindowFocus() }
        val launch = shell("am start -W -n ar.com.faro.g15isolated.v2coredev/ar.com.faro.v5.app.FaroV5V2CoreDevActivity")
        assertTrue("Return to the existing singleTask Activity: $launch", launch.contains("Status: ok"))
        rule.waitUntil(30_000) {
            rule.activity.lifecycle.currentState == Lifecycle.State.RESUMED && rule.activity.hasWindowFocus()
        }
        rule.onNode(hasSetTextAction() and hasText(draft)).performTouchInput { click() }
        visibleAboveIme("resumed")
        hideAndVerify()
    }
}
