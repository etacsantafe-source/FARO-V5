package ar.com.faro.v5.surface

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * N-022 composition seam, not a store or a new OperationContract.
 * A caller may supply only already-authorized canonical read owners.
 * Missing, revoked or failed owners stay UNKNOWN; they never become zero.
 */
fun interface FaroV5HomeIndicatorOwner {
    suspend fun readCount(): FaroV5HomeCount?
}

/**
 * Owner-supplied, authorization-scoped active-generation stamp.
 * A null/blank stamp means that the owner cannot prove current read authority.
 * This is not a new owner or a substitute for its canonical ACL checks.
 */
fun interface FaroV5HomeAuthorityStamp {
    suspend fun readStamp(): String?
}

/** Provenance and read authority are bound to the canonical owner, never inferred from a label. */
data class FaroV5HomeOwnerBinding(
    val expectedCanonicalSource: String,
    val readPort: FaroV5HomeIndicatorOwner,
    val authorityStamp: FaroV5HomeAuthorityStamp,
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
    override val invalidations: Flow<Unit> = emptyFlow(),
) : FaroV5HomeReadPort {
    private suspend fun admittedStamp(binding: FaroV5HomeOwnerBinding): String? =
        try {
            binding.authorityStamp.readStamp()?.takeIf { it.isNotBlank() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    override suspend fun readIndicators(): Map<FaroV5HomeIndicator, FaroV5HomeCount> {
        // A previously read owner can be revoked while a later owner suspends.
        // Keep the batch provisional, then revalidate every included authority
        // without inventing a global generation/ACL owner or a new data source.
        val staged = linkedMapOf<FaroV5HomeIndicator, Triple<FaroV5HomeOwnerBinding, String, FaroV5HomeCount>>()
        for (indicator in FaroV5HomeIndicator.entries) {
            val binding = owners[indicator] ?: continue
            // Reject unavailable or revoked authority before touching owner data.
            val before = admittedStamp(binding) ?: continue
            val count = try {
                binding.readPort.readCount()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed or unauthorized owner is not an empty result.
                null
            }
            // A generation/ACL change during suspension invalidates the entire read.
            val after = admittedStamp(binding)
            if (after == before && count != null &&
                count.canonicalSource == binding.expectedCanonicalSource
            ) {
                staged[indicator] = Triple(binding, before, count)
            }
        }
        val visible = linkedMapOf<FaroV5HomeIndicator, FaroV5HomeCount>()
        for ((indicator, candidate) in staged) {
            val (binding, initialStamp, count) = candidate
            // A vanished/changed authority suppresses only its own count.
            // This does not replace owner-emitted invalidations after return.
            if (admittedStamp(binding) == initialStamp) {
                visible[indicator] = count
            }
        }
        return visible
    }
}
