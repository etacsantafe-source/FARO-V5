package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.generation.FaroV5Household10Evidence2DevSelectionReadPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.time.LocalDate
import java.util.Collections

/**
 * F2C1B0: ONLY converts one already-certified F2C0 selected ACTIVE read into
 * F1/D0 contracts. No SQLite, Android grant issuer, network, UI, cart selection,
 * payment, price authority, Apply, or persisted/cached permission exists here.
 *
 * The future trusted Android DEV host MUST freshly read and revalidate the
 * source for each coordinator callback; a Projected object is NOT a grant.
 */
internal object S4W04SelectedActiveTypedReadProjection {
    data class Projected(
        val household: HouseholdNeedsSnapshot,
        val fence: CommercialNeedReadFence,
        val evidence: CommercialEvidenceStagedTypedPortabilitySnapshot,
    )

    fun project(
        read: FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead?,
        expected: ActiveGenerationPointer,
        needId: HouseholdNeedId,
        productKey: CommercialProductKey,
        principal: String,
        hostEpoch: String,
        currentGeneration: () -> ActiveGenerationPointer?,
        admitted: () -> Boolean,
    ): Projected? {
        fun admittedNow(): Boolean = runCatching {
            admitted() && currentGeneration() == expected
        }.getOrDefault(false)

        if (!admittedNow() || read == null) return null
        return runCatching {
            if (read.generation != expected || read.householdRevision < 0L ||
                principal.length !in 1..160 || principal != principal.trim() ||
                principal.any(Char::isISOControl) ||
                hostEpoch.length !in 1..160 || hostEpoch != hostEpoch.trim() ||
                hostEpoch.any(Char::isISOControl)
            ) return@runCatching null

            val row = read.need
            if (row.id != needId.value || row.status != HouseholdNeedStatus.OPEN.name ||
                row.revision !in 0L..read.householdRevision ||
                read.typedHistory.schemaVersion != 2 ||
                read.typedHistory.records.any { it.observation.productKey != productKey.value }
            ) return@runCatching null

            // All typed constructors reject malformed row values; never repair
            // UNKNOWN terms or invent absent locality, validity or prices.
            val need = HouseholdNeed(
                HouseholdNeedId(row.id),
                HouseholdNeedName(row.name),
                HouseholdNeedQuantity(row.quantityThousandths.toLong(),
                    HouseholdNeedUnit.valueOf(row.unit)),
                HouseholdNeedPriority.valueOf(row.priority),
                HouseholdNeedStatus.valueOf(row.status),
                row.revision,
                row.dueEpochDay?.let(LocalDate::ofEpochDay),
            )
            val snapshot = HouseholdNeedsSnapshot(
                read.householdRevision, Collections.unmodifiableList(listOf(need)),
            )
            val fence = CommercialNeedReadFence(
                read.generation.generationId.value, read.generation.sequence, hostEpoch, principal,
            )

            // Preserve F2C0's unfiltered full-source watermark while restricting
            // records to the exact explicitly selected product. No aliases.
            val recordCopy = Collections.unmodifiableList(ArrayList(read.typedHistory.records))
            val evidence = read.typedHistory.copy(records = recordCopy)
            if (!admittedNow()) return@runCatching null
            Projected(snapshot, fence, evidence)
        }.getOrNull()
    }
}
