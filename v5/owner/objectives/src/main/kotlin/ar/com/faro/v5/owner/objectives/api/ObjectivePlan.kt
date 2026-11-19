package ar.com.faro.v5.owner.objectives.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDate

data class ObjectiveNextStep(
    val id: String,
    val title: String,
    val dueDate: LocalDate? = null,
    val done: Boolean = false,
) {
    init {
        require(validPlanId(id))
        require(title.isNotBlank() && title == title.trim() && title.length <= 160)
    }
}

enum class ObjectiveLinkOwner { OBJECTIVE, MATTER, FINANCE }

data class ObjectiveLink(val owner: ObjectiveLinkOwner, val id: String) {
    init { require(validPlanId(id)) }
}

data class ObjectivePlan(
    val steps: List<ObjectiveNextStep> = emptyList(),
    val links: Set<ObjectiveLink> = emptySet(),
) {
    init {
        require(steps.size <= 48 && steps.map { it.id }.distinct().size == steps.size)
        require(links.size <= 48)
    }
}

sealed interface ObjectiveLinkView {
    data class Visible(val link: ObjectiveLink, val label: String) : ObjectiveLinkView
    data class Unavailable(val owner: ObjectiveLinkOwner) : ObjectiveLinkView
}

/** Capability lookup and source access are explicit, and independently scoped to the link. */
class ObjectiveReferenceAuthority(
    private val epochOrNull: (ObjectiveLink) -> String?,
    private val readLabel: (ObjectiveLink, String) -> String?,
) {
    fun current(link: ObjectiveLink, epoch: String): Boolean =
        link.owner != ObjectiveLinkOwner.OBJECTIVE &&
            runCatching { epochOrNull(link) == epoch }.getOrDefault(false)

    fun authorized(link: ObjectiveLink): Pair<String, String>? = runCatching {
        if (link.owner == ObjectiveLinkOwner.OBJECTIVE) return@runCatching null
        val epoch = epochOrNull(link)?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val label = readLabel(link, epoch)?.takeIf { it.isNotBlank() && it.length <= 160 }
            ?: return@runCatching null
        if (current(link, epoch)) epoch to label else null
    }.getOrNull()

    fun observe(link: ObjectiveLink): ObjectiveLinkView =
        authorized(link)?.let { ObjectiveLinkView.Visible(link, it.second) }
            ?: ObjectiveLinkView.Unavailable(link.owner)
}

sealed interface ObjectivePlanChange {
    data class PutStep(val step: ObjectiveNextStep) : ObjectivePlanChange
    data class RemoveStep(val id: String) : ObjectivePlanChange {
        init { require(validPlanId(id)) }
    }
    data class SetLink(val link: ObjectiveLink, val add: Boolean) : ObjectivePlanChange
}

data class ObjectivePlanIntent(
    val objectiveId: ObjectiveId,
    val expectedRevision: Long,
    val editedAtEpochMillis: Long,
    val change: ObjectivePlanChange,
) {
    init { require(expectedRevision >= 0 && editedAtEpochMillis >= 0) }

    fun externalLinkToAdd(): ObjectiveLink? =
        (change as? ObjectivePlanChange.SetLink)?.takeIf {
            it.add && it.link.owner != ObjectiveLinkOwner.OBJECTIVE
        }?.link

    fun applyOn(snapshot: ObjectiveSnapshot): ObjectiveRecord? = runCatching {
        if (snapshot.revision != expectedRevision) return@runCatching null
        val record = snapshot.objectives.firstOrNull { it.id == objectiveId } ?: return@runCatching null
        if (editedAtEpochMillis <= record.updatedAtEpochMillis) return@runCatching null
        val plan = record.details.plan
        val updated = when (val item = change) {
            is ObjectivePlanChange.PutStep -> {
                val existing = plan.steps.indexOfFirst { it.id == item.step.id }
                val steps = if (existing < 0) plan.steps + item.step
                else plan.steps.mapIndexed { position, step -> if (position == existing) item.step else step }
                plan.copy(steps = steps)
            }
            is ObjectivePlanChange.RemoveStep -> plan.copy(steps = plan.steps.filterNot { it.id == item.id })
            is ObjectivePlanChange.SetLink -> plan.copy(
                links = if (item.add) plan.links + item.link else plan.links - item.link,
            )
        }
        if (updated == plan) return@runCatching null
        val replacement = record.copy(
            updatedAtEpochMillis = editedAtEpochMillis,
            details = record.details.copy(plan = updated),
        )
        ObjectiveSnapshot(snapshot.revision, snapshot.objectives.map { if (it.id == objectiveId) replacement else it })
        replacement
    }.getOrNull()
}

internal fun requireAcyclicObjectiveLinks(objectives: List<ObjectiveRecord>) {
    if (objectives.none { goal -> goal.details.plan.links.any { it.owner == ObjectiveLinkOwner.OBJECTIVE } }) return
    val edges = objectives.associate { record ->
        record.id to record.details.plan.links.filter { it.owner == ObjectiveLinkOwner.OBJECTIVE }.map {
            ObjectiveId.of(it.id)
        }
    }
    val indegree = edges.keys.associateWith { 0 }.toMutableMap()
    for (targets in edges.values) for (target in targets) {
        require(target in indegree) { "Missing objective dependency" }
        indegree[target] = indegree.getValue(target) + 1
    }
    val pending = ArrayDeque(indegree.filterValues { it == 0 }.keys)
    var visited = 0
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        visited++
        for (target in edges.getValue(current)) {
            val count = indegree.getValue(target) - 1
            indegree[target] = count
            if (count == 0) pending.addLast(target)
        }
    }
    require(visited == edges.size) { "Cyclic objective dependency" }
}

object ObjectivePlanCodec {
    const val MAX_BYTES = 40_000
    private const val FORMAT = "objectives.plan.v1"

    fun encode(plan: ObjectivePlan): ByteArray = bytes {
        writeInt(plan.steps.size)
        plan.steps.forEach { writeStep(it) }
        writeInt(plan.links.size)
        plan.links.sortedWith(compareBy({ it.owner.name }, { it.id })).forEach { writeLink(it) }
    }

    fun decode(encoded: ByteArray): ObjectivePlan = decodeCanonical(encoded, ::encode) {
        val steps = List(readInt().also { require(it in 0..48) }) { readStep() }
        val links = List(readInt().also { require(it in 0..48) }) { readLink() }
        ObjectivePlan(steps, links.toSet())
    }

    fun encodeIntent(intent: ObjectivePlanIntent): CanonicalPayload = CanonicalPayload.fromEncoded(FORMAT, intentBytes(intent))

    fun decodeIntent(payload: CanonicalPayload): ObjectivePlanIntent? {
        if (payload.formatId != FORMAT || payload.size !in 1..MAX_BYTES) return null
        return runCatching {
            decodeCanonical(payload.copyBytes(), ::intentBytes) {
                val id = ObjectiveId.of(readUTF())
                val revision = readLong()
                val at = readLong()
                val change = when (readUnsignedByte()) {
                    1 -> ObjectivePlanChange.PutStep(readStep())
                    2 -> ObjectivePlanChange.RemoveStep(readUTF())
                    3 -> ObjectivePlanChange.SetLink(readLink(), readBoolean())
                    else -> error("Invalid objective plan change")
                }
                ObjectivePlanIntent(id, revision, at, change)
            }
        }.getOrNull()
    }

    private fun intentBytes(intent: ObjectivePlanIntent) = bytes {
        writeUTF(intent.objectiveId.value)
        writeLong(intent.expectedRevision)
        writeLong(intent.editedAtEpochMillis)
        when (val change = intent.change) {
            is ObjectivePlanChange.PutStep -> {
                writeByte(1)
                writeStep(change.step)
            }
            is ObjectivePlanChange.RemoveStep -> {
                writeByte(2)
                writeUTF(change.id)
            }
            is ObjectivePlanChange.SetLink -> {
                writeByte(3)
                writeLink(change.link)
                writeBoolean(change.add)
            }
        }
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { it.block() }
        buffer.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    private fun <T> decodeCanonical(
        bytes: ByteArray,
        encode: (T) -> ByteArray,
        decode: DataInputStream.() -> T,
    ): T {
        require(bytes.size in 1..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { it.decode() }.also {
            require(encode(it).contentEquals(bytes))
        }
    }

    private fun DataOutputStream.writeStep(step: ObjectiveNextStep) {
        writeUTF(step.id)
        writeUTF(step.title)
        writeUTF(step.dueDate?.toString().orEmpty())
        writeBoolean(step.done)
    }

    private fun DataInputStream.readStep() = ObjectiveNextStep(
        readUTF(), readUTF(), readUTF().takeIf { it.isNotEmpty() }?.let(LocalDate::parse), readBoolean(),
    )

    private fun DataOutputStream.writeLink(link: ObjectiveLink) {
        writeUTF(link.owner.name)
        writeUTF(link.id)
    }

    private fun DataInputStream.readLink() = ObjectiveLink(ObjectiveLinkOwner.valueOf(readUTF()), readUTF())
}

private fun validPlanId(value: String): Boolean =
    value.isNotBlank() && value.length <= 128 && value == value.trim() && value.none(Char::isISOControl)
