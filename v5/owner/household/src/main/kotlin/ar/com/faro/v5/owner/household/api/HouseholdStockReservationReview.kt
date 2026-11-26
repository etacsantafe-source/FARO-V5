package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

enum class HouseholdStockReservationAction(val operation: OperationId, internal val wireCode: Int) {
    RESERVE(OperationId("stock.reserve"), 1),
    RELEASE(OperationId("stock.release"), 2),
}

data class HouseholdStockReservationDevRequest(
    val action: HouseholdStockReservationAction,
    val context: HouseholdStockReceiveDevContext,
    val stockItem: String,
    val quantity: HouseholdNeedQuantity,
    val intentKey: String,
    val ledgerRevision: Long,
    val reservationRef: String? = null,
) {
    internal fun isWellFormed(): Boolean =
        context.isolatedDev && context.foreground && context.hostEpoch > 0 && ledgerRevision >= 0 &&
            listOf(context.principal, context.activePointer.id, stockItem, intentKey).all(::validToken) &&
            when (action) {
                HouseholdStockReservationAction.RESERVE -> reservationRef == null
                HouseholdStockReservationAction.RELEASE -> reservationRef != null && validToken(reservationRef)
            }

    private fun validToken(value: String): Boolean =
        value.codePointCount(0, value.length) in 1..128 &&
            value.none { it <= '\u0020' || it == '\u007f' } &&
            value.toByteArray(Charsets.UTF_8).let { it.size <= 384 && it.toString(Charsets.UTF_8) == value }
}

/** A canonical Review payload tied to one action, intent, owner and ledger revision. */
data class HouseholdStockReservationReview(val request: HouseholdStockReservationDevRequest) {
    init {
        require(request.isWellFormed())
    }

    val payload: CanonicalPayload
        get() = CanonicalPayload.fromEncoded(FORMAT, ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                val context = request.context
                out.writeByte(request.action.wireCode)
                out.writeUTF(context.activePointer.id)
                out.writeLong(context.activePointer.sequence)
                out.writeUTF(context.principal)
                out.writeLong(context.hostEpoch)
                out.writeUTF(request.stockItem)
                out.writeUTF(request.quantity.unit.name)
                out.writeLong(request.quantity.thousandths)
                out.writeUTF(request.intentKey)
                out.writeLong(request.ledgerRevision)
                request.reservationRef?.let(out::writeUTF)
            }
        }.toByteArray())

    companion object {
        private const val FORMAT = "faro.household.stock.reservation.v1"

        fun decode(payload: CanonicalPayload): HouseholdStockReservationReview? {
            if (payload.formatId != FORMAT || payload.size !in 1..4096) return null
            return runCatching {
                DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                    val actionCode = input.readUnsignedByte()
                    val action = HouseholdStockReservationAction.entries.single { it.wireCode == actionCode }
                    val generation = HouseholdReviewGeneration(input.readUTF(), input.readLong())
                    val context = HouseholdStockReceiveDevContext(input.readUTF(), input.readLong(), generation,
                        isolatedDev = true, foreground = true)
                    val stockItem = input.readUTF()
                    val unit = HouseholdNeedUnit.valueOf(input.readUTF())
                    val quantity = HouseholdNeedQuantity(input.readLong(), unit)
                    val intentKey = input.readUTF()
                    val revision = input.readLong()
                    val reference = if (action == HouseholdStockReservationAction.RELEASE) input.readUTF() else null
                    val review = HouseholdStockReservationReview(
                        HouseholdStockReservationDevRequest(action, context, stockItem, quantity, intentKey, revision, reference),
                    )
                    check(input.read() == -1 && review.payload == payload)
                    review
                }
            }.getOrNull()
        }
    }
}
