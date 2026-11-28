package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5Household15StockPositionProjectionTest {
    private val pointer = ActiveGenerationPointer(1, StorageGenerationId("dev-stock"))
    private val purchase = RealityHousehold12PurchaseRow("p1", "yerba", 10_000, "UNIT", "owner", 1, "MANUAL_EXPLICIT", 1)
    private val receipt = RealityHousehold11ReceiptRow("r1", "i-r1", "p1", "yerba", 7_000, "UNIT", 0, 2, 1)
    private val reserve = RealityHousehold14ReservationRow(
        "s1", "i-s1", RealityHousehold14ReservationType.RESERVE, "s1", "yerba", "UNIT", 2_000, "owner", 3, 2,
    )
    private val consume = RealityHousehold15ConsumptionRow(
        "c1", "i-c1", RealityHousehold15ConsumptionType.CONSUME, "c1", "yerba", "UNIT", 1_000,
        "owner", "use1", null, 4, 4, 3,
    )
    private val undo = consume.copy(
        eventId = "u1", intentKey = "i-u1", type = RealityHousehold15ConsumptionType.CONSUME_UNDO,
        occurredAtMs = 5, recordedAtMs = 5, revision = 4,
    )
    private val release = reserve.copy(
        eventId = "l1", intentKey = "i-l1", type = RealityHousehold14ReservationType.RELEASE,
        recordedAtMs = 6, revision = 5,
    )

    private fun ledger(
        purchases: List<RealityHousehold12PurchaseRow> = listOf(purchase),
        receipts: List<RealityHousehold11ReceiptRow> = listOf(receipt),
        reservations: List<RealityHousehold14ReservationRow> = emptyList(),
        consumptions: List<RealityHousehold15ConsumptionRow> = emptyList(),
        compensations: List<RealityHousehold13CompensationRow> = emptyList(),
    ) = RealityHousehold15ConsumptionRows(
        RealityHousehold14ReservationRows(
            RealityHousehold13CompensationRows(
                RealityHousehold12PurchaseReceiptRows(
                    purchases,
                    RealityHousehold11ReceiptRows(
                        maxOf(receipts.maxOfOrNull { it.revision } ?: 0, compensations.maxOfOrNull { it.revision } ?: 0),
                        receipts,
                    ),
                ),
                compensations,
            ),
            reservations,
        ),
        consumptions,
    )

    private fun project(rows: RealityHousehold15ConsumptionRows, principal: String = "owner", at: Long = 10) =
        FaroV5Household15StockPositionProjection.fromVerifiedHistory(rows, principal, pointer, at)

    private fun verified(rows: RealityHousehold15ConsumptionRows, principal: String = "owner") =
        project(rows, principal) as FaroV5StockReadResult.Verified

    @Test fun purchaseReceiptReserveConsumeUndoRelease() {
        val snapshots = listOf(
            ledger(reservations = listOf(reserve)),
            ledger(reservations = listOf(reserve), consumptions = listOf(consume)),
            ledger(reservations = listOf(reserve), consumptions = listOf(consume, undo)),
            ledger(reservations = listOf(reserve, release), consumptions = listOf(consume, undo)),
        ).map { verified(it) }
        assertEquals(
            listOf(
                FaroV5StockPosition("yerba", "UNIT", 7_000, 2_000, 0, 5_000, 3_000),
                FaroV5StockPosition("yerba", "UNIT", 6_000, 2_000, 1_000, 4_000, 3_000),
                FaroV5StockPosition("yerba", "UNIT", 7_000, 2_000, 0, 5_000, 3_000),
                FaroV5StockPosition("yerba", "UNIT", 7_000, 0, 0, 7_000, 3_000),
            ),
            snapshots.map { it.positions.single() },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), snapshots.last().history.map { it.revision })
        assertEquals("c1", snapshots.last().history[3].originEventId)
        assertEquals(pointer, snapshots.last().pointer)
        assertEquals(snapshots.last(), verified(snapshotsInput()))
    }

    private fun snapshotsInput() = ledger(reservations = listOf(reserve, release), consumptions = listOf(consume, undo))

    @Test fun receiptCompensationRestoresPendingQuantity() {
        val reversed = RealityHousehold13CompensationRow("u-r1", "i-ur1", "r1", "p1", 7_000, "UNIT", "owner", 3, 2)
        val projected = verified(ledger(compensations = listOf(reversed)))
        assertEquals(FaroV5StockPosition("yerba", "UNIT", 0, 0, 0, 0, 10_000), projected.positions.single())
        assertEquals("r1", projected.history.last().originEventId)
        assertEquals(projected, verified(ledger(compensations = listOf(reversed))))
    }

    @Test fun keepsUnitsAndPrincipalsSeparate() {
        val rows = ledger(
            purchases = listOf(purchase.copy(purchaseRef = "grams", unit = "GRAM"), purchase,
                purchase.copy(purchaseRef = "other-p", confirmedBy = "other")),
            receipts = listOf(receipt, receipt.copy(receiptId = "r2", intentKey = "i-r2",
                purchaseRef = "other-p", quantityThousandths = 5_000, revision = 2)),
        )
        assertEquals(listOf("GRAM", "UNIT"), verified(rows).positions.map { it.unit })
        assertEquals(listOf(0L, 7_000L), verified(rows).positions.map { it.physicalThousandths })
        assertEquals(listOf("r1"), verified(rows).history.map { it.eventId })
        assertEquals(listOf("r2"), verified(rows, "other").history.map { it.eventId })
        assertEquals(5_000L, verified(rows, "other").positions.single().physicalThousandths)
    }

    @Test fun confirmedEmptyAndPurchaseWithoutReceiptAreDistinctFromUnavailable() {
        val empty = verified(ledger(purchases = emptyList(), receipts = emptyList()))
        assertEquals(FaroV5StockCoverage.EMPTY_CONFIRMED, empty.coverage)
        assertTrue(empty.positions.isEmpty())
        val purchaseOnly = verified(ledger(receipts = emptyList()))
        assertEquals(FaroV5StockCoverage.READY, purchaseOnly.coverage)
        assertEquals(0L, purchaseOnly.positions.single().availableThousandths)
        assertEquals(10_000L, purchaseOnly.positions.single().pendingReceiptThousandths)
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, project(ledger(purchases = emptyList())))
        assertEquals(FaroV5StockReadResult.Hidden.DENIED, project(ledger(), ""))
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, project(ledger(), at = 1))
    }

    @Test fun rejectsReservedConsumptionExhaustionAndOverRelease() {
        listOf(
            ledger(reservations = listOf(reserve), consumptions = listOf(consume.copy(reservationRef = "s1"))),
            ledger(reservations = listOf(reserve), consumptions = listOf(consume.copy(quantityThousandths = 5_001))),
            ledger(reservations = listOf(reserve.copy(quantityThousandths = 7_001))),
            ledger(reservations = listOf(reserve, release.copy(quantityThousandths = 2_001))),
        ).forEach { assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, project(it)) }
    }

    @Test fun rejectsForgedUndoAndRevisionOrIntentReplay() {
        listOf(
            ledger(consumptions = listOf(consume, undo.copy(sourceRef = "forged"))),
            ledger(consumptions = listOf(consume, undo.copy(quantityThousandths = 1_001))),
            ledger(consumptions = listOf(undo, consume)),
            ledger(consumptions = listOf(consume.copy(revision = 1))),
            ledger(consumptions = listOf(consume.copy(intentKey = "i-r1"))),
        ).forEach { assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, project(it)) }
    }

    @Test fun checksExactLongArithmeticWithoutClamping() {
        val balance = FaroV5Household15StockPositionProjection.Balance(physical = Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) {
            balance.after(FaroV5StockEventType.RECEIPT, 1)
        }
        val wide = verified(ledger(
            purchases = listOf(purchase.copy(quantityThousandths = Int.MAX_VALUE),
                purchase.copy(purchaseRef = "p2", quantityThousandths = Int.MAX_VALUE)),
            receipts = listOf(receipt.copy(quantityThousandths = Int.MAX_VALUE),
                receipt.copy(receiptId = "r2", intentKey = "i-r2", purchaseRef = "p2",
                    quantityThousandths = Int.MAX_VALUE, revision = 2)),
        ))
        assertEquals(2L * Int.MAX_VALUE, wide.positions.single().availableThousandths)
    }
}
