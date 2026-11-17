package ar.com.faro.v5.app

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DORMANT Android lifecycle adapter for the DEV-only SQL read reconciler.
 * No product Activity/CompositionRoot creates this adapter. Does not register
 * a ContentObserver, read an account, or grant authority on ON_RESUME.
 * A future UI owner must still revalidate SQL AND human auth before rendering.
 */
internal class S4W01FinanceReadForegroundLifecycleAdapter(
    private val lifecycle: Lifecycle,
    private val reconciler: S4W01FinanceReadForegroundReconciliation,
) : AutoCloseable {
    private val finished = AtomicBoolean(false)
    private val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> reconciler.onResume()
            Lifecycle.Event.ON_PAUSE -> reconciler.onPause()
            Lifecycle.Event.ON_STOP -> reconciler.onStop()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Foreground DEV reconciler lifecycle must be registered on main thread"
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) close()
    }

    override fun close() {
        if (!finished.compareAndSet(false, true)) return
        reconciler.close() // immediately poison any pending read
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycle.removeObserver(observer)
        } else {
            Handler(Looper.getMainLooper()).post {
                lifecycle.removeObserver(observer)
            }
        }
    }
}
