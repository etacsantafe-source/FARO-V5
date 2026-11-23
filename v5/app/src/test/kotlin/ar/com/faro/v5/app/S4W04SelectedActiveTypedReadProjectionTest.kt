package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.PortableCommercialObservation
import ar.com.faro.v5.evidence.PortableCommercialTypedObservation
import ar.com.faro.v5.generation.FaroV5Household10Evidence2DevSelectionReadPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** New F2C1B0 pure adapter cases only; no F2C0 SQLite or Android reruns. */
class S4W04SelectedActiveTypedReadProjectionTest {
    private val generation = ActiveGenerationPointer(7L, StorageGenerationId("dev-typed-gen"))
    private val needId = HouseholdNeedId("need-yerba")
    private val productKey = CommercialProductKey("sku-yerba")
    private val watermark = "a".repeat(64)

    private fun record(
        product: String = "sku-yerba",
        terms: CommercialEvidenceTypedTerms? = null,
    ) = PortableCommercialTypedObservation(
        PortableCommercialObservation(
            product, "Comercio humano", "ARS", 12000L,
            "MANUAL", "manual:local", 900L, 950L,
        ),
        terms,
    )

    private fun selected(
        row: RealityHousehold10Row = RealityHousehold10Row(
            "need-yerba", "Yerba", 1000, "UNIT", "NORMAL", "OPEN", 2L, null,
        ),
        revision: Long = 9L,
        records: List<PortableCommercialTypedObservation> = listOf(record()),
        gen: ActiveGenerationPointer = generation,
    ) = FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead(
        gen,
        revision,
        row,
        CommercialEvidenceStagedTypedPortabilitySnapshot(2, watermark, records),
    )

    private fun project(
        read: FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead? = selected(),
        need: HouseholdNeedId = needId,
        product: CommercialProductKey = productKey,
        expected: ActiveGenerationPointer = generation,
        principal: String = "w04-local-principal",
        epoch: String = "w04-dev-epoch",
        generationNow: () -> ActiveGenerationPointer? = { generation },
        admitted: () -> Boolean = { true },
    ) = S4W04SelectedActiveTypedReadProjection.project(
        read, expected, need, product, principal, epoch, generationNow, admitted,
    )

    @Test fun t01SameGenerationSelectedOpenNeedAndProductBindExactF1Contracts() {
        val data = assertNotNull(project())
        assertEquals(9L, data.household.revision)
        assertEquals(1, data.household.needs.size)
        val need = data.household.needs.single()
        assertEquals(needId, need.id)
        assertEquals("Yerba", need.name.value)
        assertEquals(HouseholdNeedUnit.UNIT, need.quantity.unit)
        assertEquals(HouseholdNeedStatus.OPEN, need.status)
        assertEquals(2L, need.revision)
        assertEquals(CommercialNeedReadFence("dev-typed-gen", 7L,
            "w04-dev-epoch", "w04-local-principal"), data.fence)
        assertEquals("sku-yerba", data.evidence.records.single().observation.productKey)
        assertEquals(2, data.evidence.schemaVersion)
    }

    @Test fun t02NeedProductOrGenerationMismatchAndForeignRecordsNeverProject() {
        assertNull(project(need = HouseholdNeedId("need-other")))
        assertNull(project(product = CommercialProductKey("sku-other")))
        assertNull(project(read = selected(records = listOf(record("sku-other")))))
        assertNull(project(read = selected(gen = ActiveGenerationPointer(
            8L, StorageGenerationId("dev-typed-gen")))))
        assertNull(project(generationNow = { ActiveGenerationPointer(
            8L, StorageGenerationId("dev-typed-gen")) }))
    }

    @Test fun t03CompletedInvalidQuantityAndRevisionFailClosed() {
        val good = selected()
        assertNull(project(read = selected(row = good.need.copy(status = "COMPLETED"))))
        assertNull(project(read = selected(row = good.need.copy(quantityThousandths = 0))))
        assertNull(project(read = selected(row = good.need.copy(revision = 10L))))
        assertNull(project(read = selected(revision = -1L)))
        assertNull(project(read = selected(row = good.need.copy(unit = "UNRECOGNIZED"))))
    }

    @Test fun t04BeforeAndAfterRevocationOrThrownCallbackDenied() {
        assertNull(project(read = null))
        assertNull(project(admitted = { false }))
        assertNull(project(admitted = { error("revoked") }))
        var calls = 0
        assertNull(project(admitted = { ++calls == 1 }))
        assertEquals(2, calls)
        var reads = 0
        assertNull(project(generationNow = {
            reads++
            if (reads == 1) generation else null
        }))
        assertEquals(2, reads)
    }

    @Test fun t05WatermarkUnknownTermsAndRecordCopyNeverChange() {
        val mutable = mutableListOf(record(terms = null),
            record(terms = CommercialEvidenceTypedTerms.UNKNOWN))
        val data = assertNotNull(project(read = selected(records = mutable)))
        assertEquals(watermark, data.evidence.sourceWatermark)
        assertEquals(2, data.evidence.records.size)
        assertNull(data.evidence.records[0].terms)
        assertEquals(CommercialEvidenceTypedTerms.UNKNOWN, data.evidence.records[1].terms)
        mutable.clear()
        assertEquals(2, data.evidence.records.size)
        assertTrue(runCatching { (data.evidence.records as MutableList).clear() }.isFailure)
    }

    @Test fun t06BadPrincipalEpochOrRowIdentityRefused() {
        assertNull(project(principal = " "))
        assertNull(project(principal = "wrong\nprincipal"))
        assertNull(project(epoch = " "))
        assertNull(project(epoch = "revoked\nepoch"))
        assertNull(project(read = selected(row = selected().need.copy(name = " ")) ))
        assertNull(project(read = selected(row = selected().need.copy(id = "need-other"))))
    }
}
