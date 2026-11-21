package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.util.Collections

/** An ephemeral snapshot of explicit product and pack choices. */
internal class S4W04CommercialCartDraft private constructor(
    val householdRevision: Long,
    val readFence: CommercialNeedReadFence,
    private val choices: List<PackChoice>,
) {
    data class PackChoice(
        val candidate: CommercialNeedProductCandidate,
        val requestedPackCount: Int,
    )

    data class Line(
        val needId: HouseholdNeedId,
        val needRevision: Long,
        val needName: HouseholdNeedName,
        val needQuantityThousandths: Long,
        val needUnit: HouseholdNeedUnit,
        val productKey: CommercialProductKey,
        val requestedPackCount: Int,
    )

    enum class Rejection {
        NOT_AUTHORIZED,
        STALE_CANDIDATE,
        INVALID_PACK_COUNT,
        EMPTY_OR_TOO_MANY_LINES,
        DUPLICATE_NEED,
        INCONSISTENT_READ_FENCE,
    }

    sealed interface Decision {
        data class Draft(val cartDraft: S4W04CommercialCartDraft) : Decision
        data class Rejected(val reason: Rejection) : Decision
    }

    val lines: List<Line> = Collections.unmodifiableList(choices.map { (candidate, packs) ->
        val need = candidate.need
        Line(need.id, need.revision, need.name, need.quantity.thousandths,
            need.quantity.unit, candidate.productKey, packs)
    })
    val lineCount: Int get() = lines.size

    val fingerprint: String = CanonicalHashBuilder.domain("w04.d0.cart-draft.v1")
        .string("snapshotRevision", householdRevision.toString())
        .string("generationId", readFence.generationId)
        .string("pointerSequence", readFence.pointerSequence.toString())
        .string("hostEpoch", readFence.hostEpoch)
        .string("principal", readFence.principal)
        .string("lineCount", lineCount.toString())
        .apply {
            for (line in lines) {
                string("needId", line.needId.value)
                string("needRevision", line.needRevision.toString())
                string("needName", line.needName.value)
                string("needQuantityThousandths", line.needQuantityThousandths.toString())
                string("needUnit", line.needUnit.name)
                string("productKey", line.productKey.value)
                string("requestedPackCount", line.requestedPackCount.toString())
            }
        }
        .sha256Hex()

    companion object {
        private const val MAX_LINES = 200
        private const val MAX_PACKS = 10_000

        fun build(
            snapshot: HouseholdNeedsSnapshot?,
            readFence: CommercialNeedReadFence?,
            choices: List<PackChoice>,
            isOwnerAdmitted: () -> Boolean,
        ): Decision {
            if (!isOwnerAdmitted() || snapshot == null || readFence == null || snapshot.revision < 0L) {
                return Decision.Rejected(Rejection.NOT_AUTHORIZED)
            }
            if (choices.size !in 1..MAX_LINES) {
                return Decision.Rejected(Rejection.EMPTY_OR_TOO_MANY_LINES)
            }
            val ordered = choices.sortedWith(compareBy(
                { it.candidate.need.id.value }, { it.candidate.productKey.value },
            ))
            validateChoices(ordered, snapshot, readFence, isOwnerAdmitted)?.let {
                return Decision.Rejected(it)
            }
            return Decision.Draft(S4W04CommercialCartDraft(snapshot.revision, readFence, ordered))
        }

        fun revalidate(
            draft: S4W04CommercialCartDraft,
            currentSnapshot: HouseholdNeedsSnapshot?,
            currentFence: CommercialNeedReadFence?,
            isOwnerAdmitted: () -> Boolean,
        ): Decision {
            if (!isOwnerAdmitted() || currentSnapshot == null || currentFence == null ||
                currentSnapshot.revision < 0L
            ) {
                return Decision.Rejected(Rejection.NOT_AUTHORIZED)
            }
            validateChoices(draft.choices, currentSnapshot, currentFence, isOwnerAdmitted)?.let {
                return Decision.Rejected(it)
            }
            return Decision.Draft(draft)
        }

        private fun validateChoices(
            choices: List<PackChoice>,
            snapshot: HouseholdNeedsSnapshot,
            fence: CommercialNeedReadFence,
            isOwnerAdmitted: () -> Boolean,
        ): Rejection? {
            val seenNeeds = mutableSetOf<HouseholdNeedId>()
            for ((candidate, packs) in choices) {
                if (packs !in 1..MAX_PACKS) return Rejection.INVALID_PACK_COUNT
                if (!seenNeeds.add(candidate.need.id)) return Rejection.DUPLICATE_NEED
                when (val result = S4W04CommercialNeedProductSelection.revalidate(
                    candidate, snapshot, fence, isOwnerAdmitted,
                )) {
                    is CommercialNeedProductDecision.Candidate -> Unit
                    is CommercialNeedProductDecision.Rejected -> return when (result.reason) {
                        CommercialNeedProductRejection.NOT_AUTHORIZED -> Rejection.NOT_AUTHORIZED
                        CommercialNeedProductRejection.STALE_READ_FENCE -> Rejection.INCONSISTENT_READ_FENCE
                        else -> Rejection.STALE_CANDIDATE
                    }
                }
            }
            return null
        }
    }
}
