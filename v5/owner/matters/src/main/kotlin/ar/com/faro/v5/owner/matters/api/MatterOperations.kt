package ar.com.faro.v5.owner.matters.api

import ar.com.faro.v5.kernel.contracts.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate

/** Exactly the existing N-031 operation catalog, without introducing new IDs. */
object MatterReviewOperations {
    val CREATE = OperationId("matter.create")
    val UPDATE = OperationId("matter.update")
    val LIFECYCLE = OperationId("matter.lifecycle.set")
    val SNOOZE = OperationId("matter.snooze")
    val EXECUTOR = ExecutorId("planning")
    val supported = setOf(CREATE, UPDATE, LIFECYCLE, SNOOZE)

    fun allows(contract: OperationContractDescriptor): Boolean =
        contract.operationId in supported &&
            contract.capabilityId == CapabilityId("N-031") &&
            contract.canonicalExecutor == EXECUTOR &&
            contract.executionProfile == ExecutionProfile.REALITY_MUTATION &&
            contract.exposure == ContractExposure.BINDABLE
}

data class MatterReviewForm(
    val title: MatterTitle,
    val detail: MatterDetail,
    val priority: MatterPriority = MatterPriority.NORMAL,
    val reviewDate: LocalDate? = null,
    val dueAtEpochMillis: Long? = null,
) {
    init { require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L) }
}

sealed interface MatterReviewChange {
    data class Create(val form: MatterReviewForm) : MatterReviewChange
    data class Edit(val form: MatterReviewForm, val reason: String) : MatterReviewChange
    data class Transition(
        val before: MatterStatus,
        val after: MatterStatus,
        val reason: String,
    ) : MatterReviewChange
    data class Snooze(val dueAtEpochMillis: Long?, val reason: String) : MatterReviewChange {
        init { require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L) }
    }
}

fun nextMatterStates(status: MatterStatus): Set<MatterStatus> = when (status) {
    MatterStatus.OPEN -> setOf(MatterStatus.IN_PROGRESS, MatterStatus.WAITING, MatterStatus.RESOLVED, MatterStatus.ARCHIVED)
    MatterStatus.IN_PROGRESS -> setOf(MatterStatus.OPEN, MatterStatus.WAITING, MatterStatus.RESOLVED, MatterStatus.ARCHIVED)
    MatterStatus.WAITING -> setOf(MatterStatus.OPEN, MatterStatus.IN_PROGRESS, MatterStatus.RESOLVED, MatterStatus.ARCHIVED)
    MatterStatus.RESOLVED -> setOf(MatterStatus.OPEN, MatterStatus.ARCHIVED)
    MatterStatus.ARCHIVED -> setOf(MatterStatus.OPEN)
}

/** Undo is a compensating reviewed mutation; no previous event is deleted. */
data class MatterReviewIntent(
    val id: MatterId,
    val expectedRevision: Long,
    val recordedAtEpochMillis: Long,
    val change: MatterReviewChange,
    val undoOfSequence: Int? = null,
) {
    init {
        require(expectedRevision >= 0L && recordedAtEpochMillis >= 0L)
        require(undoOfSequence == null || undoOfSequence in 1..100_000)
        require(undoOfSequence == null || change !is MatterReviewChange.Create)
        val reason = when (change) {
            is MatterReviewChange.Create -> ""
            is MatterReviewChange.Edit -> change.reason
            is MatterReviewChange.Transition -> change.reason
            is MatterReviewChange.Snooze -> change.reason
        }
        require(reason == reason.trim() && reason.length <= 400 &&
            (change is MatterReviewChange.Create || reason.isNotEmpty()) &&
            reason.none(Char::isISOControl))
    }

    val operation: OperationId get() = when (change) {
        is MatterReviewChange.Create -> MatterReviewOperations.CREATE
        is MatterReviewChange.Edit -> MatterReviewOperations.UPDATE
        is MatterReviewChange.Transition -> MatterReviewOperations.LIFECYCLE
        is MatterReviewChange.Snooze -> MatterReviewOperations.SNOOZE
    }

    fun applyOn(snapshot: MatterSnapshot): MatterRecord? {
        if (snapshot.revision != expectedRevision) return null
        val previous = snapshot.matters.firstOrNull { it.id == id }
        if (previous != null && recordedAtEpochMillis <= previous.updatedAtEpochMillis) return null
        if (undoOfSequence != null && previous?.details?.history?.lastOrNull()?.sequence != undoOfSequence) return null
        val reason = when (val action = change) {
            is MatterReviewChange.Create -> ""
            is MatterReviewChange.Edit -> action.reason
            is MatterReviewChange.Transition -> action.reason
            is MatterReviewChange.Snooze -> action.reason
        }
        val auditedReason = if (undoOfSequence != null) "Undo #$undoOfSequence: $reason" else reason
        if (auditedReason.length > 512) return null
        val nextSequence = (previous?.details?.history?.size ?: 0) + 1
        if (nextSequence > 100_000) return null
        return when (val action = change) {
            is MatterReviewChange.Create -> {
                if (previous != null) return null
                val event = MatterHistoryEntry(1, recordedAtEpochMillis, MatterHistoryKind.CREATED, MatterStatus.OPEN, "")
                MatterRecord(id, action.form.title, action.form.detail, MatterStatus.OPEN,
                    action.form.dueAtEpochMillis, recordedAtEpochMillis,
                    MatterDetails(action.form.priority, action.form.reviewDate, listOf(event)))
            }
            is MatterReviewChange.Edit -> {
                val old = previous ?: return null
                val event = MatterHistoryEntry(nextSequence, recordedAtEpochMillis,
                    MatterHistoryKind.EDITED, old.status, auditedReason)
                old.copy(title = action.form.title, detail = action.form.detail,
                    dueAtEpochMillis = action.form.dueAtEpochMillis,
                    updatedAtEpochMillis = recordedAtEpochMillis,
                    details = old.details.copy(priority = action.form.priority,
                        reviewDate = action.form.reviewDate, history = old.details.history + event))
            }
            is MatterReviewChange.Transition -> {
                val old = previous ?: return null
                if (old.status != action.before || action.after !in nextMatterStates(old.status)) return null
                val event = MatterHistoryEntry(nextSequence, recordedAtEpochMillis,
                    MatterHistoryKind.STATUS_CHANGED, action.after, auditedReason)
                old.copy(status = action.after, updatedAtEpochMillis = recordedAtEpochMillis,
                    details = old.details.copy(history = old.details.history + event))
            }
            is MatterReviewChange.Snooze -> {
                val old = previous ?: return null
                val event = MatterHistoryEntry(nextSequence, recordedAtEpochMillis,
                    MatterHistoryKind.EDITED, old.status, auditedReason)
                old.copy(dueAtEpochMillis = action.dueAtEpochMillis,
                    updatedAtEpochMillis = recordedAtEpochMillis,
                    details = old.details.copy(history = old.details.history + event))
            }
        }
    }
}

/** Byte-canonical typed payload: decode must produce the identical representation. */
object MatterReviewPayload {
    private const val FORMAT = "matters.reviewed-mutation.v1"
    private const val MAX_BYTES = 16_384

    fun encode(intent: MatterReviewIntent): CanonicalPayload {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(1)
                output.writeUTF(intent.id.value)
                output.writeLong(intent.expectedRevision)
                output.writeLong(intent.recordedAtEpochMillis)
                output.writeInt(intent.undoOfSequence ?: 0)
                when (val action = intent.change) {
                    is MatterReviewChange.Create -> {
                        output.writeByte(1); output.writeForm(action.form)
                    }
                    is MatterReviewChange.Edit -> {
                        output.writeByte(2); output.writeForm(action.form); output.writeUTF(action.reason)
                    }
                    is MatterReviewChange.Transition -> {
                        output.writeByte(3); output.writeUTF(action.before.name)
                        output.writeUTF(action.after.name); output.writeUTF(action.reason)
                    }
                    is MatterReviewChange.Snooze -> {
                        output.writeByte(4)
                        output.writeLong(action.dueAtEpochMillis ?: -1L)
                        output.writeUTF(action.reason)
                    }
                }
            }
            buffer.toByteArray()
        }
        require(bytes.size in 1..MAX_BYTES)
        return CanonicalPayload.fromEncoded(FORMAT, bytes)
    }

    fun decode(payload: CanonicalPayload): MatterReviewIntent? {
        if (payload.formatId != FORMAT || payload.size !in 1..MAX_BYTES) return null
        val bytes = payload.copyBytes()
        return runCatching {
            val intent = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == 1)
                val id = MatterId.of(input.readUTF())
                val revision = input.readLong()
                val at = input.readLong()
                val undo = input.readInt().takeIf { it != 0 }
                val action = when (input.readUnsignedByte()) {
                    1 -> MatterReviewChange.Create(input.readForm())
                    2 -> MatterReviewChange.Edit(input.readForm(), input.readUTF())
                    3 -> MatterReviewChange.Transition(
                        MatterStatus.valueOf(input.readUTF()), MatterStatus.valueOf(input.readUTF()), input.readUTF())
                    4 -> MatterReviewChange.Snooze(input.readLong().takeIf { it != -1L }, input.readUTF())
                    else -> error("invalid Matter mutation kind")
                }
                require(input.available() == 0)
                MatterReviewIntent(id, revision, at, action, undo)
            }
            require(encode(intent).copyBytes().contentEquals(bytes))
            intent
        }.getOrNull()
    }

    private fun DataOutputStream.writeForm(form: MatterReviewForm) {
        writeUTF(form.title.value); writeUTF(form.detail.value)
        writeUTF(form.priority.name); writeUTF(form.reviewDate?.toString().orEmpty())
        writeLong(form.dueAtEpochMillis ?: -1L)
    }

    private fun DataInputStream.readForm(): MatterReviewForm = MatterReviewForm(
        MatterTitle.of(readUTF()), MatterDetail.of(readUTF()),
        MatterPriority.valueOf(readUTF()),
        readUTF().takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
        readLong().takeIf { it != -1L },
    )
}
