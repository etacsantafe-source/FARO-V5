package ar.com.faro.v5.owner.household.api

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.owner.household.spi.HouseholdNeedChange
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class HouseholdReviewGeneration(val id: String, val sequence: Long) {
    init {
        require(id.length in 1..128 && id.none { it.isWhitespace() || it.isISOControl() })
        require(sequence > 0)
    }
}

enum class HouseholdNeedReviewAction(id: String, internal val label: String) {
    ADD("shopping_need.add", "Agregar necesidad"),
    UPDATE("shopping_need.update", "Editar necesidad"),
    COMPLETE("shopping_need.complete", "Completar necesidad"),
    REMOVE("shopping_need.remove", "Eliminar necesidad");

    val operationId = OperationId(id)
}

sealed interface HouseholdNeedReviewRequest {
    data class Add(val fields: HouseholdNeedFields) : HouseholdNeedReviewRequest

    sealed interface ByName : HouseholdNeedReviewRequest {
        val name: HouseholdNeedName
    }

    data class Edit(override val name: HouseholdNeedName, val fields: HouseholdNeedFields) : ByName
    data class Complete(override val name: HouseholdNeedName) : ByName
    data class Remove(override val name: HouseholdNeedName) : ByName
}

sealed interface HouseholdNeedReviewResult {
    data class Ready(val review: HouseholdNeedReview) : HouseholdNeedReviewResult

    enum class Rejected(val message: String) : HouseholdNeedReviewResult {
        UNAVAILABLE("La lista de necesidades no está disponible."),
        MISSING("No se encontró una necesidad con ese nombre."),
        AMBIGUOUS("Hay varias necesidades con ese nombre. Aclará cuál querés cambiar."),
        ALREADY_COMPLETE("Esa necesidad ya está completada."),
        INVALID("Revisá los datos de la necesidad."),
    }
}

/** Frozen Household intent. The payload carries the exact preimage for later receipt-linked Undo. */
class HouseholdNeedReview private constructor(
    val generation: HouseholdReviewGeneration,
    val expectedRevision: Long,
    val action: HouseholdNeedReviewAction,
    val before: HouseholdNeed?,
    val after: HouseholdNeed?,
) {
    init {
        require(expectedRevision in 0 until Long.MAX_VALUE)
        val next = expectedRevision + 1
        require(before == null || before.revision <= expectedRevision)
        require(after == null || after.revision == next)
        require(listOfNotNull(before, after).all { isUtf8(it.name.value) && isUtf8(it.id.value) })
        require(isUtf8(generation.id))
        when (action) {
            HouseholdNeedReviewAction.ADD -> require(before == null && after?.status == HouseholdNeedStatus.OPEN)
            HouseholdNeedReviewAction.UPDATE -> require(before != null && after != null &&
                before.id == after.id && before.status == after.status)
            HouseholdNeedReviewAction.COMPLETE -> require(before?.status == HouseholdNeedStatus.OPEN &&
                after == before.copy(status = HouseholdNeedStatus.COMPLETED, revision = next))
            HouseholdNeedReviewAction.REMOVE -> require(before != null && after == null)
        }
    }

    val targetId: HouseholdNeedId get() = checkNotNull(before ?: after).id

    val payload: CanonicalPayload = CanonicalPayload.fromEncoded(
        FORMAT,
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeUTF(generation.id)
                it.writeLong(generation.sequence)
                it.writeLong(expectedRevision)
                it.writeUTF(action.operationId.value)
                it.writeNeed(before)
                it.writeNeed(after)
            }
        }.toByteArray(),
    )

    val summary: String = buildString {
        append(action.label)
        before?.let { append("\nAntes · ").append(description(it)) }
        after?.let { append("\nDespués · ").append(description(it)) }
    }

    fun matchesCurrentSnapshot(
        currentGeneration: HouseholdReviewGeneration,
        current: HouseholdNeedsSnapshot,
        approvedPayloadFingerprint: PayloadFingerprint,
    ): Boolean {
        if (generation != currentGeneration || expectedRevision != current.revision ||
            payload.fingerprint != approvedPayloadFingerprint) return false
        val matching = current.needs.filter { it.id == targetId }
        return if (before == null) matching.isEmpty() else matching == listOf(before)
    }

    fun toPersistenceChange(): HouseholdNeedChange = when (action) {
        HouseholdNeedReviewAction.ADD ->
            HouseholdNeedChange.Add(targetId, checkNotNull(after).fields())
        HouseholdNeedReviewAction.UPDATE ->
            HouseholdNeedChange.Edit(targetId, checkNotNull(before).revision, checkNotNull(after).fields())
        HouseholdNeedReviewAction.COMPLETE ->
            HouseholdNeedChange.Complete(targetId, checkNotNull(before).revision)
        HouseholdNeedReviewAction.REMOVE ->
            HouseholdNeedChange.Remove(targetId, checkNotNull(before).revision)
    }

    companion object {
        private const val FORMAT = "faro.household.need.review.v1"
        private const val MAX_PAYLOAD = 4096

        fun propose(
            generation: HouseholdReviewGeneration,
            snapshot: HouseholdNeedsSnapshot?,
            request: HouseholdNeedReviewRequest,
            requestIdentity: String,
        ): HouseholdNeedReviewResult {
            if (snapshot == null) return HouseholdNeedReviewResult.Rejected.UNAVAILABLE
            if (snapshot.revision !in 0 until Long.MAX_VALUE || snapshot.needs.size > 8192 ||
                requestIdentity.isBlank() || requestIdentity.length > 256 || !isUtf8(requestIdentity)) {
                return HouseholdNeedReviewResult.Rejected.INVALID
            }

            val before = if (request is HouseholdNeedReviewRequest.ByName) {
                val target = matchName(request.name.value)
                val matches = snapshot.needs.asSequence().filter { matchName(it.name.value) == target }.take(2).toList()
                if (matches.isEmpty()) return HouseholdNeedReviewResult.Rejected.MISSING
                if (matches.size > 1) return HouseholdNeedReviewResult.Rejected.AMBIGUOUS
                matches.single()
            } else {
                null
            }
            val next = snapshot.revision + 1
            val (action, after) = try {
                when (request) {
                    is HouseholdNeedReviewRequest.Add -> {
                        val id = HouseholdNeedId("need-" + CanonicalHashBuilder.domain(FORMAT)
                            .string("generation", generation.id)
                            .string("sequence", generation.sequence.toString())
                            .string("request", requestIdentity)
                            .sha256Hex())
                        if (snapshot.needs.any { it.id == id }) return HouseholdNeedReviewResult.Rejected.INVALID
                        HouseholdNeedReviewAction.ADD to request.fields.toNeed(id, HouseholdNeedStatus.OPEN, next)
                    }
                    is HouseholdNeedReviewRequest.Edit -> {
                        val old = checkNotNull(before)
                        HouseholdNeedReviewAction.UPDATE to request.fields.toNeed(old.id, old.status, next)
                    }
                    is HouseholdNeedReviewRequest.Complete -> {
                        val old = checkNotNull(before)
                        if (old.status != HouseholdNeedStatus.OPEN) return HouseholdNeedReviewResult.Rejected.ALREADY_COMPLETE
                        HouseholdNeedReviewAction.COMPLETE to old.copy(status = HouseholdNeedStatus.COMPLETED, revision = next)
                    }
                    is HouseholdNeedReviewRequest.Remove -> HouseholdNeedReviewAction.REMOVE to null
                }
            } catch (_: IllegalArgumentException) {
                return HouseholdNeedReviewResult.Rejected.INVALID
            }
            return try {
                HouseholdNeedReviewResult.Ready(HouseholdNeedReview(generation, snapshot.revision, action, before, after))
            } catch (_: IllegalArgumentException) {
                HouseholdNeedReviewResult.Rejected.INVALID
            }
        }

        fun decode(payload: CanonicalPayload): HouseholdNeedReview? {
            if (payload.formatId != FORMAT || payload.size !in 1..MAX_PAYLOAD) return null
            return try {
                DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                    val generation = HouseholdReviewGeneration(input.readUTF(), input.readLong())
                    val revision = input.readLong()
                    val operation = input.readUTF()
                    val action = HouseholdNeedReviewAction.entries.single { it.operationId.value == operation }
                    val review = HouseholdNeedReview(generation, revision, action, input.readNeed(), input.readNeed())
                    review.takeIf { input.read() == -1 && it.payload == payload }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun matchName(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT)

        private fun isUtf8(value: String): Boolean = value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value

        private fun HouseholdNeed.fields() = HouseholdNeedFields(name, quantity, priority, dueDate)

        private fun HouseholdNeedFields.toNeed(id: HouseholdNeedId, status: HouseholdNeedStatus, revision: Long) =
            HouseholdNeed(id, name, quantity, priority, status, revision, dueDate)

        private fun DataOutputStream.writeNeed(need: HouseholdNeed?) {
            writeBoolean(need != null)
            need ?: return
            writeUTF(need.id.value)
            writeUTF(need.name.value)
            writeLong(need.quantity.thousandths)
            writeUTF(need.quantity.unit.name)
            writeUTF(need.priority.name)
            writeUTF(need.status.name)
            writeLong(need.revision)
            writeBoolean(need.dueDate != null)
            need.dueDate?.let { writeLong(it.toEpochDay()) }
        }

        private fun DataInputStream.readNeed(): HouseholdNeed? {
            if (!readBoolean()) return null
            val id = HouseholdNeedId(readUTF())
            val name = HouseholdNeedName(readUTF())
            val quantity = HouseholdNeedQuantity(readLong(), HouseholdNeedUnit.valueOf(readUTF()))
            val priority = HouseholdNeedPriority.valueOf(readUTF())
            val status = HouseholdNeedStatus.valueOf(readUTF())
            val revision = readLong()
            val due = if (readBoolean()) LocalDate.ofEpochDay(readLong()) else null
            return HouseholdNeed(id, name, quantity, priority, status, revision, due)
        }

        private fun description(need: HouseholdNeed): String {
            val amount = BigDecimal.valueOf(need.quantity.thousandths, 3).stripTrailingZeros().toPlainString().replace('.', ',')
            val unit = when (need.quantity.unit) {
                HouseholdNeedUnit.UNIT -> "unid."
                HouseholdNeedUnit.PACKAGE -> "paq."
                HouseholdNeedUnit.GRAM -> "g"
                HouseholdNeedUnit.KILOGRAM -> "kg"
                HouseholdNeedUnit.MILLILITER -> "ml"
                HouseholdNeedUnit.LITER -> "l"
            }
            val status = if (need.status == HouseholdNeedStatus.OPEN) "pendiente" else "completada"
            val priority = when (need.priority) {
                HouseholdNeedPriority.LOW -> "baja"
                HouseholdNeedPriority.NORMAL -> "normal"
                HouseholdNeedPriority.HIGH -> "alta"
            }
            return "${need.name.value} · $amount $unit · $status · prioridad $priority · ${need.dueDate ?: "sin fecha"}"
        }
    }
}
