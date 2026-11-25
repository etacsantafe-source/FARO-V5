package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The exact declaration approved in the DEV purchase Review. */
data class HouseholdManualPurchaseReview(
    val generation: HouseholdReviewGeneration,
    val principal: String,
    val hostEpoch: String,
    val purchaseRef: String,
    val itemRef: String,
    val quantity: HouseholdNeedQuantity,
) {
    init {
        require(listOf(principal, hostEpoch, purchaseRef, itemRef).all { value ->
            value.length in 1..128 &&
                value.none { it.isWhitespace() || it.isISOControl() } &&
                value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value
        })
        require(quantity.thousandths in 1L..Int.MAX_VALUE.toLong())
    }

    val payload: CanonicalPayload
        get() = CanonicalPayload.fromEncoded(FORMAT, ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(generation.id)
                out.writeLong(generation.sequence)
                out.writeUTF(principal)
                out.writeUTF(hostEpoch)
                out.writeUTF(purchaseRef)
                out.writeUTF(itemRef)
                out.writeLong(quantity.thousandths)
                out.writeUTF(quantity.unit.name)
                out.writeUTF(PROVENANCE)
            }
        }.toByteArray())

    companion object {
        val OPERATION = OperationId("shopping.purchase.record")
        const val PROVENANCE = "MANUAL_EXPLICIT"
        private const val FORMAT = "faro.household.purchase.manual.v1"

        fun decode(payload: CanonicalPayload): HouseholdManualPurchaseReview? {
            if (payload.formatId != FORMAT || payload.size !in 1..4_096) return null
            return runCatching {
                DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                    val review = HouseholdManualPurchaseReview(
                        HouseholdReviewGeneration(input.readUTF(), input.readLong()),
                        input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(),
                        HouseholdNeedQuantity(input.readLong(), HouseholdNeedUnit.valueOf(input.readUTF())),
                    )
                    check(input.readUTF() == PROVENANCE && input.read() == -1 && review.payload == payload)
                    review
                }
            }.getOrNull()
        }
    }
}
