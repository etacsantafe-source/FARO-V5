package ar.com.faro.v5.app

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM-testable fail-closed event gate. A notice is ONLY a request to clear and
 * re-read durable SQL; no balance DTO, human identity or lease lives here.
 */
internal class S4W01FinanceReadPrivateInvalidationGate(
    private val invalidateBeforeAnyNextRead: () -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var listening = false
    private var closed = false

    fun onRegistered(): Boolean {
        synchronized(lock) {
            if (closed || listening) return false
            listening = true
        }
        // Cover a remote COMMIT that happened before registration.
        return onSignal()
    }

    fun onSignal(): Boolean {
        synchronized(lock) { if (closed || !listening) return false }
        val cleared = runCatching { invalidateBeforeAnyNextRead() }.isSuccess
        return synchronized(lock) {
            if (!cleared) {
                closed = true
                listening = false
            }
            cleared && !closed
        }
    }

    fun isOpen(): Boolean = synchronized(lock) { listening && !closed }

    override fun close() {
        synchronized(lock) {
            closed = true
            listening = false
        }
    }
}

/**
 * DORMANT DEV-only Android ContentObserver adapter. Must be installed on main
 * thread by a future trusted READ host. Does not attach shipping UI.
 *
 * A callback is NOT proof of commit and never authenticates a human. A raw
 * sqlite3 writer in a second PID emits NO ContentResolver notice; the already
 * certified ON_RESUME SQL reconciliation remains compulsory.
 */
internal class S4W01FinanceReadPrivateInvalidation(
    context: Context,
    reconciler: S4W01FinanceReadForegroundReconciliation,
) : AutoCloseable {
    private val resolver = context.contentResolver
    private val uri = signalUri(context.packageName)
    private val gate = S4W01FinanceReadPrivateInvalidationGate {
        reconciler.onExternalSignal()
    }
    private val registered = AtomicBoolean(false)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!gate.onSignal()) close()
        }
    }

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "DEV invalidation listener must register on main"
        }
        try {
            resolver.registerContentObserver(uri, false, observer)
            registered.set(true)
            check(gate.onRegistered()) {
                "DEV invalidation registration did not clear previous read"
            }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    override fun close() {
        gate.close()
        if (!registered.compareAndSet(true, false)) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { resolver.unregisterContentObserver(observer) }
        } else {
            Handler(Looper.getMainLooper()).post {
                runCatching { resolver.unregisterContentObserver(observer) }
            }
        }
    }

    companion object {
        private fun signalUri(packageName: String): Uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(packageName + ".finance9read")
                .appendPath("invalidated")
                .build()

        /**
         * Future cooperative publisher only, callable AFTER a verified SQL
         * COMMIT. There is intentionally NO RealityStore caller in this unit;
         * this API alone does not certify any READ revoke transition.
         */
        internal fun publishAfterVerifiedCommitForDevOnly(context: Context): Boolean =
            runCatching {
                context.contentResolver.notifyChange(signalUri(context.packageName), null, 0)
                true
            }.getOrDefault(false)
    }
}
