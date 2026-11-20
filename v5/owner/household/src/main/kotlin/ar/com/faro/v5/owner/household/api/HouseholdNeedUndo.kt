package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.ReceiptId
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.household.spi.HouseholdNeedChange
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Frozen inverse intent. The host separately verifies the original COMMITTED Review and receipt. */
class HouseholdNeedUndo private constructor(
    val generation: HouseholdReviewGeneration,
    val expectedRevision: Long,
    val sourceReviewId: String,
    val sourceReceipt: ExecutionReceipt,
    val original: HouseholdNeedReview,
) {
    init {
        require(expectedRevision in (original.expectedRevision + 1) until Long.MAX_VALUE)
        require(sourceReviewId.length in 1..128 && sourceReviewId.none(Char::isISOControl))
        if (original.after == null) require(expectedRevision == original.expectedRevision + 1)
    }

    val targetId: HouseholdNeedId get() = original.targetId

    val summary = buildString {
        append("Deshacer cambio de necesidad\n")
        append(original.summary)
        if (original.before == null) append("\nAl deshacer se quitará la necesidad creada.")
        else append("\nAl deshacer se recuperará el estado de «Antes».")
    }

    val payload = CanonicalPayload.fromEncoded(FORMAT, ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use {
            it.writeUTF(generation.id)
            it.writeLong(generation.sequence)
            it.writeLong(expectedRevision)
            it.writeUTF(sourceReviewId)
            it.writeUTF(sourceReceipt.receiptId.value)
            it.writeUTF(sourceReceipt.transactionId.value)
            it.writeUTF(sourceReceipt.planFingerprint.value)
            it.writeUTF(original.payload.formatId)
            val canonicalOriginal = original.payload.copyBytes()
            it.writeInt(canonicalOriginal.size)
            it.write(canonicalOriginal)
        }
    }.toByteArray())

    fun matchesCurrentSnapshot(currentGeneration: HouseholdReviewGeneration, current: HouseholdNeedsSnapshot): Boolean {
        if (currentGeneration != generation || current.revision != expectedRevision) return false
        return current.needs.filter { it.id == targetId } == listOfNotNull(original.after)
    }

    fun toPersistenceChange() = HouseholdNeedChange.RestorePreimage(targetId, original.after, original.before)

    companion object {
        val OPERATION = OperationId("undo.execute")
        private const val FORMAT = "faro.household.need.undo.v1"
        private const val MAX_ORIGINAL = 4096

        fun propose(
            generation: HouseholdReviewGeneration,
            current: HouseholdNeedsSnapshot,
            sourceReviewId: String,
            sourceReceipt: ExecutionReceipt,
            original: HouseholdNeedReview,
        ): HouseholdNeedUndo? = try {
            HouseholdNeedUndo(generation, current.revision, sourceReviewId, sourceReceipt, original)
                .takeIf { it.matchesCurrentSnapshot(generation, current) }
        } catch (_: IllegalArgumentException) {
            null
        }

        fun decode(payload: CanonicalPayload): HouseholdNeedUndo? {
            if (payload.formatId != FORMAT || payload.size !in 1..8192) return null
            return try {
                DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                    val generation = HouseholdReviewGeneration(input.readUTF(), input.readLong())
                    val revision = input.readLong()
                    val reviewId = input.readUTF()
                    val receipt = ExecutionReceipt(ReceiptId(input.readUTF()), TransactionId(input.readUTF()), PlanFingerprint(input.readUTF()))
                    val format = input.readUTF()
                    val size = input.readInt()
                    if (size !in 1..MAX_ORIGINAL) return null
                    val original = HouseholdNeedReview.decode(CanonicalPayload.fromEncoded(format, ByteArray(size).also(input::readFully)))
                        ?: return null
                    val undo = HouseholdNeedUndo(generation, revision, reviewId, receipt, original)
                    undo.takeIf { input.read() == -1 && it.payload == payload }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
