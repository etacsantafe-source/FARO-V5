package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.MonotonicClock
import ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot

/**
 * Ephemeral provider capability cache.
 *
 * Provider observations are not authority and are never persisted by this component.
 * Expired observations disappear from routing input instead of being silently reused.
 */
class FreshProviderCapabilityCache(
    private val clock: MonotonicClock,
    private val ttlNanos: Long,
) {
    private data class Entry(
        val snapshot: ProviderCapabilitySnapshot,
        val observedAtNanos: Long,
    )

    private val entries = linkedMapOf<ModelBindingId, Entry>()

    init {
        require(ttlNanos > 0L) {
            "provider capability TTL must be positive"
        }
    }

    @Synchronized
    fun replace(snapshots: List<ProviderCapabilitySnapshot>) {
        val ids = snapshots.map { it.bindingId }
        require(ids.size == ids.distinct().size) {
            "provider capability snapshots must have unique binding ids"
        }

        val now = clock.readNanos()
        entries.clear()
        snapshots.forEach { snapshot ->
            entries[snapshot.bindingId] =
                Entry(
                    snapshot = snapshot,
                    observedAtNanos = now,
                )
        }
    }

    @Synchronized
    fun freshSnapshots(): List<ProviderCapabilitySnapshot> {
        val now = clock.readNanos()
        return entries
            .values
            .asSequence()
            .filter { entry ->
                val elapsed = now - entry.observedAtNanos
                elapsed >= 0L && elapsed < ttlNanos
            }
            .map { it.snapshot }
            .sortedBy { it.bindingId.value }
            .toList()
    }

    @Synchronized
    fun invalidate(bindingId: ModelBindingId) {
        entries.remove(bindingId)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
