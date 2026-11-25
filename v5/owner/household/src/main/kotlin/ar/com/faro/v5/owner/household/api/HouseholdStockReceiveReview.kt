package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The approved physical reception and the two owner revisions it observed. */
data class HouseholdStockReceiveReview(
    val request: HouseholdStockReceiveDevRequest,
    val ledgerRevision: Long,
) {
    init {
        val context = request.context
        require(request.operation == OPERATION && context.isolatedDev && context.foreground)
        require(context.hostEpoch > 0 && request.purchaseRevision > 0 && ledgerRevision >= 0)
        require(listOf(context.principal, request.stockItem, request.purchaseReference, request.intentKey).all { value ->
            value.length in 1..128 && value.none { it.isWhitespace() || it.isISOControl() } &&
                value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value
        })
    }

    val payload: CanonicalPayload
        get() = CanonicalPayload.fromEncoded(FORMAT, ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                val context = request.context
                out.writeUTF(context.activePointer.id)
                out.writeLong(context.activePointer.sequence)
                out.writeUTF(context.principal)
                out.writeLong(context.hostEpoch)
                out.writeUTF(request.stockItem)
                out.writeUTF(request.purchaseReference)
                out.writeLong(request.purchaseRevision)
                out.writeUTF(request.intentKey)
                out.writeLong(request.quantity.thousandths)
                out.writeUTF(request.quantity.unit.name)
                out.writeLong(ledgerRevision)
            }
        }.toByteArray())

    companion object {
        val OPERATION = OperationId("stock.receive")
        private const val FORMAT = "faro.household.stock.receive.v1"

        fun decode(payload: CanonicalPayload): HouseholdStockReceiveReview? {
            if (payload.formatId != FORMAT || payload.size !in 1..4096) return null
            return runCatching {
                DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                    val generation = HouseholdReviewGeneration(input.readUTF(), input.readLong())
                    val context = HouseholdStockReceiveDevContext(input.readUTF(), input.readLong(), generation,
                        isolatedDev = true, foreground = true)
                    val request = HouseholdStockReceiveDevRequest(
                        OPERATION, context, input.readUTF(), input.readUTF(), input.readLong(), input.readUTF(),
                        HouseholdNeedQuantity(input.readLong(), HouseholdNeedUnit.valueOf(input.readUTF())),
                    )
                    val review = HouseholdStockReceiveReview(request, input.readLong())
                    check(input.read() == -1 && review.payload == payload)
                    review
                }
            }.getOrNull()
        }
    }
}
