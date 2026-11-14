package ar.com.faro.v5.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable API35 emulator only; no data, click, key, provider or physical device. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3H05X03InsetsUiTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun views(v: View): List<View> = buildList {
        add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(views(v.getChildAt(i)))
    }
    private fun capture(name: String) {
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "h05-evidence").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun font200NativeTitleAndLastControlStayInsideSystemBars() {
        check(android.os.Build.VERSION.SDK_INT == 35)
        check(android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.MODEL.contains("sdk", true))
        ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            // Wait for real OS insets/layout rather than synthesizing the safe area.
            val deadline = System.currentTimeMillis() + 15000
            var laidOut = false
            while (!laidOut && System.currentTimeMillis() < deadline) {
                scenario.onActivity { a ->
                    laidOut = a.window.decorView.rootWindowInsets != null && a.window.decorView.height > 0
                }
                if (!laidOut) Thread.sleep(50)
            }
            assertTrue("No window layout", laidOut)
            scenario.onActivity { a ->
                assertEquals(2f, a.resources.configuration.fontScale, 0.01f)
                val all = views(a.window.decorView)
                val title = all.filterIsInstance<TextView>().single { it.text == "FARO X03 · IA aislada" }
                val safe = a.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val rect = Rect(); assertTrue(title.getGlobalVisibleRect(rect))
                assertTrue("Title overlaps status bar: $rect vs $safe", rect.top >= safe.top)
                assertEquals(title.height, rect.height())
                assertEquals(null, title.ellipsize)
                val scroll = all.filterIsInstance<ScrollView>().single()
                val before = listOf(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, scroll.paddingBottom)
                scroll.dispatchApplyWindowInsets(a.window.decorView.rootWindowInsets)
                scroll.dispatchApplyWindowInsets(a.window.decorView.rootWindowInsets)
                assertEquals(before, listOf(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, scroll.paddingBottom))
            }
            capture("title")
            scenario.onActivity { a -> views(a.window.decorView).filterIsInstance<ScrollView>().single().fullScroll(View.FOCUS_DOWN) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { a ->
                val all = views(a.window.decorView)
                val button = all.filterIsInstance<Button>().single { it.text == "Abrir Chat y Actividad offline" }
                val rect = Rect(); assertTrue(button.getGlobalVisibleRect(rect))
                val safe = a.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val screen = a.windowManager.currentWindowMetrics.bounds
                assertEquals("Last control clipped", button.height, rect.height())
                assertTrue("Last control below navigation bar: $rect vs $safe", rect.bottom <= screen.bottom - safe.bottom)
                assertTrue(rect.left >= safe.left && rect.right <= screen.right - safe.right)
                assertTrue(button.height >= (48 * a.resources.displayMetrics.density).toInt())
                assertTrue(button.isEnabled && button.isClickable && button.isFocusable)
            }
            capture("bottom")
            instrumentation.waitForIdleSync()
        }
    }
}
