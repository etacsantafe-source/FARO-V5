package ar.com.faro.v5.app

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
        try {
            store.addNoticeCommitListener(noticeListener)
        } catch (failure: RuntimeException) {
            gate.removeListener(readinessListener)
            throw failure
        }
        awaitClose {
            store.removeNoticeCommitListener(noticeListener)
            gate.removeListener(readinessListener)
        }
    }
}
