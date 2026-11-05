package ar.com.faro.v5.app

import ar.com.faro.v5.store.reality.RealityTimedReceiptCursor
import ar.com.faro.v5.store.reality.RealityTimedReceiptHistoryReadPort
import ar.com.faro.v5.store.reality.RealityTimedReceiptQuery
import ar.com.faro.v5.store.reality.RealityTimedReceipt
import kotlinx.coroutines.CancellationException

/**
 * N015 Reality-only chronological receipts. A host-issued read admission,
 * generation identity and ACL epoch are mandatory. This is NOT history.query
 * federation, a global count, or a Home indicator binding.
 */
data class FaroV5RealityActivityPage(
    val receipts: List<RealityTimedReceipt>,
    val nextCursor: RealityTimedReceiptCursor?,
    val legacyUndated: Boolean,
) {
    val canonicalSource: String get() = "reality.execution_receipt"
    val coverage: String get() = "REALITY_ONLY_NOT_FEDERATED"
    val globallyComplete: Boolean get() = false
}

class FaroV5RealityTimedActivityReadSource(
    private val expectedGeneration: String,
    private val history: RealityTimedReceiptHistoryReadPort,
    private val currentGeneration: () -> String?,
    private val aclEpoch: () -> String?,
    private val admitted: () -> Boolean,
) {
    init { require(expectedGeneration.isNotBlank()) }

    private fun stamp(): String? = try {
        if (!admitted() || currentGeneration() != expectedGeneration) {
            null
        } else {
            val epoch = aclEpoch()?.takeIf { it.isNotBlank() }
            if (epoch == null || !admitted() ||
                currentGeneration() != expectedGeneration || aclEpoch() != epoch
            ) null else epoch
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /**
     * Never read the store without a current grant, and never expose a page
     * after its grant, pointer or ACL epoch was revoked during the query.
     * The host must include the durable pointer sequence in admitted().
     */
    fun readAuthorized(
        before: RealityTimedReceiptCursor? = null,
        limit: Int = 50,
    ): FaroV5RealityActivityPage? {
        require(limit in 1..100)
        val initial = stamp() ?: return null
        val page = try {
            history.query(RealityTimedReceiptQuery(before, limit))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (stamp() != initial) return null
        // Preserve the owner cursor and unknown legacy coverage unchanged.
        // No sum/count is offered to Home until all canonical owners exist.
        return FaroV5RealityActivityPage(
            receipts = page.receipts.toList(),
            nextCursor = page.nextCursor,
            legacyUndated = page.legacyUndated,
        )
    }
}
