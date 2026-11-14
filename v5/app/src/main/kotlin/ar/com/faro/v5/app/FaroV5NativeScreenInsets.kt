package ar.com.faro.v5.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Native screens only. Compose owns its own safe area and IME resize. */
internal object FaroV5NativeScreenInsets {
    fun protect(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            target.setPadding(left + safe.left, top + safe.top,
                right + safe.right, bottom + safe.bottom)
            // Do not consume descendant insets or change the Compose IME contract.
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
