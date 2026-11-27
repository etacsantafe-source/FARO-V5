package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Explicit isolated-DEV stock consumption request; Review has no execution authority. */
data class HouseholdStockConsumptionDevRequest(
    val context: HouseholdStockReceiveDevContext,
    val stockItem: String,
    val quantity: HouseholdNeedQuantity,
    val sourceRef: String,
    val intentKey: String,
    val ledgerRevision: Long,
    val reservationRef: String? = null,
) {
    internal fun isWellFormed(): Boolean =
        context.isolatedDev && context.foreground && context.hostEpoch > 0 &&
            ledgerRevision >= 0 && reservationRef == null &&
            listOf(context.principal, context.activePointer.id, stockItem, sourceRef, intentKey).all(::validToken) &&
            quantity.thousandths in 1L..Int.MAX_VALUE.toLong()

    private fun validToken(value: String): Boolean =
        value.codePointCount(0, value.length) in 1..128 &&
            value.none { it <= '\u0020' || it == '\u007f' } &&
            value.toByteArray(Charsets.UTF_8).let { it.size <= 384 && it.toString(Charsets.UTF_8) == value }
}

/** Canonical Review, bound to stock.consume only; no Apply or reserved consumption. */
data class HouseholdStockConsumptionReview(val request: HouseholdStockConsumptionDevRequest) {
    init { require(request.isWellFormed()) }

    val payload: CanonicalPayload
        get() = CanonicalPayload.fromEncoded(FORMAT, ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                val context = request.context
                out.writeByte(ACTION_CODE)
                out.writeUTF(context.activePointer.id)
                out.writeLong(context.activePointer.sequence)
                out.writeUTF(context.principal)
                out.writeLong(context.hostEpoch)
                out.writeUTF(request.stockItem)
                out.writeUTF(request.quantity.unit.name)
                out.writeLong(request.quantity.thousandths)
                out.writeUTF(request.sourceRef)
                out.writeUTF(request.intentKey)
                out.writeLong(request.ledgerRevision)
            }
        }.toByteArray())

    companion object {
        val OPERATION = OperationId("stock.consume")
        private const val ACTION_CODE = 1
        private const val FORMAT = "faro.household.stock.consume.v1"

        fun decode(payload: CanonicalPayload): HouseholdStockConsumptionReview? {
            if (payload.formatId != FORMAT || payload.size !in 1..4096) return null
            return runCatching {
                DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                    check(input.readUnsignedByte() == ACTION_CODE)
                    val generation = HouseholdReviewGeneration(input.readUTF(), input.readLong())
                    val context = HouseholdStockReceiveDevContext(input.readUTF(), input.readLong(), generation,
                        isolatedDev = true, foreground = true)
                    val stockItem = input.readUTF()
                    val unit = HouseholdNeedUnit.valueOf(input.readUTF())
                    val quantity = HouseholdNeedQuantity(input.readLong(), unit)
                    val sourceRef = input.readUTF()
                    val intentKey = input.readUTF()
                    val revision = input.readLong()
                    val review = HouseholdStockConsumptionReview(HouseholdStockConsumptionDevRequest(
                        context, stockItem, quantity, sourceRef, intentKey, revision,
                    ))
                    check(input.read() == -1 && review.payload == payload)
                    review
                }
            }.getOrNull()
        }
    }
}
