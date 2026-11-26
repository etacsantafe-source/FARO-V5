package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household13DevStockSnapshotTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val pointer get() = FileActiveGenerationPointer(root)

    private fun purchase(
        ref: String, item: String, quantity: Int, unit: String = "UNIT",
    ) = RealityHousehold12PurchaseRow(
        ref, item, quantity, unit, "human", 1L, "MANUAL_EXPLICIT", 1L,
    )

    private fun receipt(
        ref: String, purchaseRef: String, item: String, quantity: Int,
        revision: Long, unit: String = "UNIT",
    ) = RealityHousehold11ReceiptRow(
        ref, "intent-" + ref, purchaseRef, item, quantity, unit, 0L, 20L + revision, revision,
    )

    private fun rows() = RealityHousehold13CompensationRows(
        RealityHousehold12PurchaseReceiptRows(
            listOf(purchase("p1", "yerba", 10_000)),
            RealityHousehold11ReceiptRows(3L, listOf(
                receipt("r1", "p1", "yerba", 7_000, 1L),
                receipt("r2", "p1", "yerba", 3_000, 2L),
            )),
        ),
        listOf(RealityHousehold13CompensationRow(
            "undo-r1", "undo-intent-r1", "r1", "p1", 7_000, "UNIT", "human", 24L, 3L,
        )),
    )

    private fun stage(snapshot: RealityHousehold13CompensationRows): ActiveGenerationPointer {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("a2a-origin")))
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("a2a-sql13"), "a2a-dev-fixture", 1L),
        )
        val staged = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("a2a-finance9"), fresh, origin, "a".repeat(64), 2L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(staged, pointer).close()
        val reality = staged.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(reality)
        CommercialEvidenceStore(
            fileName = staged.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        RealityHousehold13CompensationRowsPort.restoreOrVerifyTrustedSealedStageThirteen(reality, snapshot)
        return checkNotNull(pointer.compareAndSet(origin.generationId, staged.descriptor.generationId))
    }

    private fun open(selected: ActiveGenerationPointer, admitted: () -> Boolean = { true }) =
        FaroV5Household13ActiveDevReadHost.reopen(root, selected, gate, admitted)

    @Test
    fun realSql13ReceiptAndCompensationYieldPhysicalThreeWithAvailabilityUnknown() {
        val selected = stage(rows())
        open(selected).use { host ->
            val snapshot = checkNotNull(FaroV5Household13DevStockSnapshot.read(host))
            assertEquals(3L, snapshot.ledgerRevision)
            val line = snapshot.positions.single()
            assertEquals("yerba", line.stockItemRef)
            assertEquals("UNIT", line.unit)
            assertEquals(3_000L, line.physicalNetThousandths)
            assertNull(line.reservedThousandths)
            assertNull(line.consumedThousandths)
            assertNull(line.availableThousandths)
        }
        open(selected).use {
            assertEquals(3_000L, FaroV5Household13DevStockSnapshot.read(it)?.positions?.single()?.physicalNetThousandths)
        }
        assertEquals(selected, pointer.read())
    }

    @Test
    fun multiplePurchasesSameSkuAggregateButUnitsRemainSeparate() {
        val source = rows()
        val extra = source.copy(
            historical = RealityHousehold12PurchaseReceiptRows(
                listOf(purchase("p1", "yerba", 10_000),
                    purchase("p2", "yerba", 5_000),
                    purchase("p3", "yerba", 100_000, "GRAM")),
                RealityHousehold11ReceiptRows(5L, source.historical.receipts.receipts + listOf(
                    receipt("r3", "p2", "yerba", 2_000, 4L),
                    receipt("r4", "p3", "yerba", 100_000, 5L, "GRAM"),
                )),
            ),
        )
        val positions = checkNotNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(extra)).positions
        assertEquals(2, positions.size)
        assertEquals(100_000L, positions[0].physicalNetThousandths)
        assertEquals("GRAM", positions[0].unit)
        assertEquals(5_000L, positions[1].physicalNetThousandths)
        assertEquals("UNIT", positions[1].unit)
        assertTrue(positions.all { it.availableThousandths == null })
    }

    @Test
    fun invalidLinkDuplicateReceiptAndOverReceiveFailClosed() {
        val original = rows()
        assertNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(original.copy(
            compensations = original.compensations.map { it.copy(receiptId = "not-present") },
        )))
        assertNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(original.copy(
            historical = original.historical.copy(receipts = original.historical.receipts.copy(
                receipts = original.historical.receipts.receipts + original.historical.receipts.receipts.first(),
            )),
        )))
        assertNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(original.copy(
            historical = original.historical.copy(receipts = original.historical.receipts.copy(
                receipts = original.historical.receipts.receipts.map {
                    if (it.receiptId == "r2") it.copy(quantityThousandths = 4_000) else it
                },
            )),
        )))
    }

    @Test
    fun revokedGrantFullPointerAbaAndExclusiveLeaseFailClosed() {
        val selected = stage(rows())
        var admitted = true
        open(selected) { admitted }.use { host ->
            assertNotNull(FaroV5Household13DevStockSnapshot.read(host))
            assertThrows(IllegalStateException::class.java) { open(selected) }
            admitted = false
            assertNull(FaroV5Household13DevStockSnapshot.read(host))
            admitted = true
            val foreign = checkNotNull(pointer.compareAndSet(
                selected.generationId, StorageGenerationId("a2a-foreign"),
            ))
            val returned = checkNotNull(pointer.compareAndSet(
                foreign.generationId, selected.generationId,
            ))
            assertNotEquals(selected, returned)
            assertNull(FaroV5Household13DevStockSnapshot.read(host))
        }
        assertThrows(IllegalStateException::class.java) { open(selected) }
    }

    @Test
    fun emptyAndFullyCompensatedReceiptsNeverInventAvailability() {
        val original = rows()
        val zero = original.copy(
            historical = original.historical.copy(receipts = RealityHousehold11ReceiptRows(2L,
                listOf(receipt("r1", "p1", "yerba", 7_000, 1L)))),
            compensations = listOf(original.compensations.single().copy(revision = 2L)),
        )
        val position = checkNotNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(zero)).positions.single()
        assertEquals(0L, position.physicalNetThousandths)
        assertNull(position.availableThousandths)
        val empty = RealityHousehold13CompensationRows(
            RealityHousehold12PurchaseReceiptRows(emptyList(), RealityHousehold11ReceiptRows(0L, emptyList())),
            emptyList(),
        )
        val snapshot = checkNotNull(FaroV5Household13DevStockSnapshot.fromVerifiedHistory(empty))
        assertTrue(snapshot.positions.isEmpty())
        assertEquals(0L, snapshot.ledgerRevision)
    }
}
