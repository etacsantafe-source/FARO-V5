package ar.com.faro.v5.app

import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceRead

/**
 * W01-B3B1B1B1B1B1A DEV/STAGED9 foreground read reconciler.
 *
 * Neither onResume nor an external signal can authenticate a human, issue an
 * ACL policy, publish Android IPC, expose Finance balances, or turn the
 * resulting observed SQL DTO into an authorization to render.
 *
 * A fresh B3B1B1B0 double SQL/ACL fence is constructed for EVERY observation.
 * The caller's clear callback must erase any UI cache it owns, synchronously,
 * before this instance can attempt a new observation. A failed clear poisons
 * the instance. Direct SQLite writes from another process need no notifyChange
 * to be detected by the next ON_RESUME reconciliation.
 */
internal class S4W01FinanceReadForegroundReconciliation(
    private val newObservedFence: () -> S4W01B3B1B1B0ObservedReadFence,
    private val clearBeforeRead: () -> Unit,
) : AutoCloseable {
    private enum class Event { RESUME, LEAVE, NOTICE }
    private val lock = Any()
    private var closed = false
    private var foreground = false
    private var clearedForRead = false
    private var sequence = 0L

    private fun invalidate(event: Event) {
        val revision = synchronized(lock) {
            if (closed) return
            if (sequence == Long.MAX_VALUE) {
                closed = true
                foreground = false
                clearedForRead = false
                return
            }
            when (event) {
                Event.RESUME -> foreground = true
                Event.LEAVE -> foreground = false
                Event.NOTICE -> Unit
            }
            clearedForRead = false
            ++sequence
        }
        // Invalidate BEFORE any source read, even when the process missed a
        // remote SQL revoke notification during STOP / process death.
        val cleared = runCatching { clearBeforeRead() }.isSuccess
        synchronized(lock) {
            if (!cleared) {
                closed = true
                foreground = false
                clearedForRead = false
            } else if (!closed && sequence == revision && foreground) {
                clearedForRead = true
            }
        }
    }

    fun onResume() = invalidate(Event.RESUME)
    fun onPause() = invalidate(Event.LEAVE)
    fun onStop() = invalidate(Event.LEAVE)

    /**
     * For FUTURE owner-delivered commit signals; this method DOES NOT claim
     * ContentObserver registration or notifications from raw SQLite writers.
     */
    fun onExternalSignal() = invalidate(Event.NOTICE)

    /** Result remains an informational DEV DTO, never a display lease. */
    fun observedAfterResumeForDevOnly(): RealityFinance9ObservedSourceRead? {
        val before = synchronized(lock) {
            if (closed || !foreground || !clearedForRead) return null
            sequence
        }
        val observed = runCatching {
            newObservedFence().use { it.observedForDevOnly() }
        }.getOrNull()
        return synchronized(lock) {
            observed.takeIf {
                !closed && foreground && clearedForRead && sequence == before
            }
        }
    }

    fun isClosed(): Boolean = synchronized(lock) { closed }

    override fun close() {
        val mustClear = synchronized(lock) {
            if (closed) false else {
                closed = true
                foreground = false
                clearedForRead = false
                if (sequence < Long.MAX_VALUE) ++sequence
                true
            }
        }
        if (mustClear) runCatching { clearBeforeRead() }
    }
}
