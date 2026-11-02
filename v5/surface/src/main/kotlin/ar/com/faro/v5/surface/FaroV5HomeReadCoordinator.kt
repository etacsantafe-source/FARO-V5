package ar.com.faro.v5.surface

import kotlinx.coroutines.CancellationException

/**
 * N-022 composition seam, not a store or a new OperationContract.
 * A caller may supply only already-authorized canonical read owners.
 * Missing, revoked or failed owners stay UNKNOWN; they never become zero.
 */
fun interface FaroV5HomeIndicatorOwner {
    suspend fun readCount(): FaroV5HomeCount?
}

/** Provenance is explicitly bound to the owner, never inferred from a label. */
data class FaroV5HomeOwnerBinding(
    val expectedCanonicalSource: String,
    val readPort: FaroV5HomeIndicatorOwner,
) {
    init {
        require(expectedCanonicalSource.isNotBlank())
    }
}

/**
 * Read-only, fail-closed projection of independently available owners.
 * This class deliberately does not invent sources or install a V2 host.
 */
class FaroV5HomeReadCoordinator(
    private val owners: Map<FaroV5HomeIndicator, FaroV5HomeOwnerBinding>,
) : FaroV5HomeReadPort {
    override suspend fun readIndicators(): Map<FaroV5HomeIndicator, FaroV5HomeCount> {
        val visible = linkedMapOf<FaroV5HomeIndicator, FaroV5HomeCount>()
        for (indicator in FaroV5HomeIndicator.entries) {
            val binding = owners[indicator] ?: continue
            val count = try {
                binding.readPort.readCount()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed or unauthorized owner is not an empty result.
                null
            }
            // A miswired owner must not show another domain's number.
            if (count != null && count.canonicalSource == binding.expectedCanonicalSource) {
                visible[indicator] = count
            }
        }
        return visible
    }
}
