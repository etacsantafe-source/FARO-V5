package ar.com.faro.v5.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * W01 B3B1B1B1B1B0 — DEV-only one-shot lifecycle termination of a PENDING
 * biometric read challenge. NOT a Finance authorization, token, display lease,
 * read ACL grant or source of invalidations from a second OS process.
 *
 * Once STOP/DESTROY/explicit close occurs, RESUME never reopens this instance:
 * a NEW foreground user action and NEW biometric challenge are required.
 */
internal class S4W01FinanceReadSessionLifecycle(
    private val onTerminated: () -> Unit,
) : AutoCloseable {
    private val open = AtomicBoolean(true)

    fun isOpen(): Boolean = open.get()

    fun onStop() = terminate()
    fun onDestroy() = terminate()

    /** Inspect only. NEVER mutate state to true on RESUME. */
    fun onResume(): Boolean = isOpen()

    private fun terminate() {
        if (open.compareAndSet(true, false)) onTerminated()
    }

    /** Explicit completion/close suppresses any future lifecycle callback. */
    override fun close() {
        open.set(false)
    }
}
