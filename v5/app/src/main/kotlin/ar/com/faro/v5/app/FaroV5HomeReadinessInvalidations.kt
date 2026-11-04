package ar.com.faro.v5.app

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore

/**
 * Observe the existing process-local startup owner. This invalidates a live Home
 * readPort on recovery/maintenance/readiness transitions; it does NOT claim
 * to observe standalone ACL revocation or generation changes.
 */
object FaroV5HomeReadinessInvalidations {
    @JvmStatic
    @JvmName("from")
    internal fun from(gate: FaroV5StartupReadinessGate): Flow<Unit> = callbackFlow {
        val listener = FaroV5StartupReadinessListener { trySend(Unit) }
        gate.addListener(listener)
        awaitClose { gate.removeListener(listener) }
    }

    /** Real Auxiliary owner commit + readiness owner, on the SAME Home port. */
    @JvmStatic
    @JvmName("from")
    internal fun from(
        gate: FaroV5StartupReadinessGate,
        store: AuxiliaryDeliveryStore,
    ): Flow<Unit> = callbackFlow {
        val readinessListener = FaroV5StartupReadinessListener { trySend(Unit) }
        val noticeListener = Runnable { trySend(Unit) }
        gate.addListener(readinessListener)
        // A generation can close between readPort capture and collector startup.
        // The closed owner is UNKNOWN, not a fatal presentation exception.
        val noticeSubscribed = try {
            store.addNoticeCommitListener(noticeListener)
            true
        } catch (_: IllegalStateException) {
            false
        }
        // Reconcile AFTER both owner listeners are registered. A commit or
        // readiness transition between the UI's first read and subscription
        // must not leave a stale count. This is a re-read, not a fake commit.
        trySend(Unit)
        awaitClose {
            if (noticeSubscribed) store.removeNoticeCommitListener(noticeListener)
            gate.removeListener(readinessListener)
        }
    }

    /** Genuine process-local pointer owner signal; not an independent ACL emitter. */
    @JvmStatic
    @JvmName("from")
    internal fun from(
        gate: FaroV5StartupReadinessGate,
        store: AuxiliaryDeliveryStore,
        pointerChanges: Flow<Unit>,
    ): Flow<Unit> = merge(from(gate, store), pointerChanges)
}
