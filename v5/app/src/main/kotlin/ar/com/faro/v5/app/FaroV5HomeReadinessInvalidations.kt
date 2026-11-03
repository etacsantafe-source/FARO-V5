package ar.com.faro.v5.app

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observe the existing process-local startup owner. This invalidates a live Home
 * readPort on recovery/maintenance/readiness transitions; it does NOT claim
 * to observe standalone ACL revocation or generation changes.
 */
object FaroV5HomeReadinessInvalidations {
    @JvmStatic
    internal fun from(gate: FaroV5StartupReadinessGate): Flow<Unit> = callbackFlow {
        val listener = FaroV5StartupReadinessListener { trySend(Unit) }
        gate.addListener(listener)
        awaitClose { gate.removeListener(listener) }
    }
}
