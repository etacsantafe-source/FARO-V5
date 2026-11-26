package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household15Fbs2CodecTest {
    @TempDir lateinit var root: Path
    private val codec = FaroV5Household15Fbs2Codec
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val secret = "Test-only authenticated FBS2v12".toCharArray()

    @Test
    fun canonicalRoundTripKeepsV11SectionByteForByteAndConsumptionUndo() {
        val value = fixture()
        val bytes = codec.encode(value)
        assertEquals(12, ByteBuffer.wrap(bytes).getInt(4))
        val baseSize = ByteBuffer.wrap(bytes).getInt(8)
        val canonicalV11 = FaroV5Household14Fbs2Codec.encode(value.base)
        assertArrayEquals(canonicalV11, bytes.copyOfRange(80, 80 + baseSize))
        assertEquals(value, codec.decode(bytes))
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)))
    }

    @Test
    fun emptyConsumptionLedgerIsValidButDoesNotActivateStock() {
        val value = fixture().copy(consumptions = emptyList())
        val encoded = codec.encode(value)
        assertEquals(value, codec.decode(encoded))
        assertEquals(4, ByteBuffer.wrap(encoded).getInt(12))
    }

    @Test
    fun enforcesAvailableStockAtEveryRevisionNotJustFinalBalance() {
        val value = fixture()
        val (consume, undo) = value.consumptions
        val exactFreeBalance = value.copy(consumptions = listOf(
            consume.copy(quantityThousandths = 2_000),
            undo.copy(quantityThousandths = 2_000),
        ))
        assertEquals(exactFreeBalance, codec.decode(codec.encode(exactFreeBalance)))
        rejects { codec.encode(value.copy(consumptions = listOf(
            consume.copy(quantityThousandths = 2_001),
            undo.copy(quantityThousandths = 2_001),
        ))) }
        rejects { codec.encode(value.copy(consumptions = listOf(
            consume.copy(quantityThousandths = 3_000),
            undo.copy(quantityThousandths = 3_000),
        ))) }
        rejects { codec.encode(value.copy(consumptions = listOf(undo, consume))) }
    }

    @Test
    fun deniesInvalidOriginPartialUndoAndSourceMismatch() {
        val value = fixture()
        val (consume, undo) = value.consumptions
        val cases = listOf(
            listOf(undo), listOf(consume, undo.copy(consumptionRef = "missing")),
            listOf(consume, undo.copy(sourceRef = "other")),
            listOf(consume, undo.copy(stockItemRef = "other")),
            listOf(consume, undo.copy(unit = "GRAM")),
            listOf(consume, undo.copy(principal = "other")),
            listOf(consume, undo.copy(quantityThousandths = 1_001)),
            listOf(consume, undo.copy(recordedAtMs = consume.recordedAtMs - 1)),
            listOf(consume, undo.copy(occurredAtMs = consume.occurredAtMs - 1)),
            listOf(consume, undo.copy(consumptionRef = undo.eventId)),
            listOf(consume, undo, undo.copy(eventId = "undo-2",
                intentKey = "undo-intent-2", quantityThousandths = 1, revision = 8)),
        )
        for (events in cases) rejects { codec.encode(value.copy(consumptions = events)) }
    }

    @Test
    fun rejectsReservationsCollisionsMalformedRefsAndForbiddenAmounts() {
        val value = fixture()
        val (consume, undo) = value.consumptions
        val cases = listOf(
            listOf(consume.copy(reservationRef = "reserve-1")),
            listOf(consume.copy(unit = "ITEM")),
            listOf(consume.copy(quantityThousandths = 0)),
            listOf(consume.copy(quantityThousandths = Int.MIN_VALUE)),
            listOf(consume.copy(intentKey = "bad intent")),
            listOf(consume.copy(eventId = "\ud800")),
            listOf(consume.copy(sourceRef = "bad\u0000ref")),
            listOf(consume.copy(eventId = "r01")),
            listOf(consume.copy(intentKey = "reserve-intent")),
            listOf(consume.copy(revision = 4)),
            listOf(consume.copy(revision = 3)),
            listOf(consume.copy(revision = 0)),
            listOf(consume.copy(recordedAtMs = 0)),
            listOf(consume.copy(occurredAtMs = -1)),
            listOf(consume.copy(consumptionRef = "other")),
            listOf(consume, undo.copy(intentKey = consume.intentKey)),
            listOf(consume, undo.copy(revision = consume.revision)),
            listOf(consume.copy(eventId = "duplicate"), consume.copy(eventId = "duplicate",
                intentKey = "intent-other", consumptionRef = "duplicate", revision = 8)),
        )
        for (events in cases) rejects { codec.encode(value.copy(consumptions = events)) }
    }

    @Test
    fun rejectsTruncationHeaderManipulationTamperAndTrailingBytes() {
        val bytes = codec.encode(fixture())
        rejects { codec.decode(bytes.copyOf(bytes.size - 1)) }
        rejects { codec.decode(bytes + byteArrayOf(0)) }
        rejects { codec.decode(bytes.clone().also { ByteBuffer.wrap(it).putInt(4, 11) }) }
        rejects { codec.decode(bytes.clone().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) }) }
        rejects { codec.decode(bytes.clone().also { ByteBuffer.wrap(it).putInt(12, -1) }) }
        rejects { codec.decode(bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }) }
        rejects { codec.decode(bytes.clone().also { it[16] = (it[16].toInt() xor 1).toByte() }) }
    }

    @Test
    fun authenticatedFaroBak2RejectsWrongKeyTamperAndCrossVersion() {
        val value = fixture()
        val archive = crypto.sealWithHouseholdConsumption15(value, secret)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(value),
            crypto.openWithHouseholdConsumption15(archive, secret))
        val authenticationFailure = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED)
        assertEquals(authenticationFailure,
            crypto.openWithHouseholdConsumption15(archive, "wrong test key".toCharArray()))
        assertEquals(authenticationFailure, crypto.openWithHouseholdConsumption15(
            archive.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }, secret))
        val legacy = crypto.sealWithHouseholdReservations14(value.base, secret)
        val wrongType = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD)
        assertEquals(wrongType, crypto.openWithHouseholdConsumption15(legacy, secret))
        assertEquals(wrongType, crypto.openWithHouseholdReservations14(archive, secret))
        assertEquals(FiveStoreAuthenticatedPayload.Opened(value.base),
            crypto.openWithHouseholdReservations14(legacy, secret))
    }

    @Test
    fun rejectsMoreThan8192EventsWithoutAllocatingHugeLedger() {
        val value = fixture()
        val base = value.consumptions[0]
        val tooMany = List(8_193) { i -> base.copy(
            eventId = "consume-" + i, intentKey = "intent-" + i,
            consumptionRef = "consume-" + i, revision = i + 6L,
        ) }
        rejects { codec.encode(value.copy(consumptions = tooMany)) }
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private fun fixture(): FaroV5Household15Fbs2Snapshot {
        val reserve = Household14ReservationBackupRow(
            "reserve-1", "reserve-intent", Household14ReservationEventType.RESERVE,
            "reserve-1", "yerba", "UNIT", 2_000, "local-human", 24, 4,
        )
        val release = reserve.copy(
            eventId = "release-1", intentKey = "release-intent",
            type = Household14ReservationEventType.RELEASE,
            quantityThousandths = 1_000, recordedAtMs = 25, revision = 5,
        )
        val consume = Household15ConsumptionBackupRow(
            "consume-1", "consume-intent", Household15ConsumptionEventType.CONSUME,
            "consume-1", "yerba", "UNIT", 1_000, "local-human", "explicit-use",
            null, 26, 26, 6,
        )
        val undo = consume.copy(
            eventId = "consume-undo-1", intentKey = "consume-undo-intent",
            type = Household15ConsumptionEventType.CONSUME_UNDO,
            recordedAtMs = 27, occurredAtMs = 27, revision = 7,
        )
        return FaroV5Household15Fbs2Snapshot(
            FaroV5Household14Fbs2Snapshot(historicalStock(), listOf(reserve, release)),
            listOf(consume, undo),
        )
    }

    private fun historicalStock(): FaroV5Household13Fbs2Snapshot {
        val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("consume-source")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("consume-stage"), "fbs2-v12-test", 1),
        )
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("consume-fixture-finance"), stage, origin, "a".repeat(64), 2L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val previousArchive = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
            MaintenanceOperationId("consume-fixture-capture"), BackupSetId("consume-backup"),
            stage, origin, "5.0-dev", 3L, secret,
        )
        val older = crypto.openWithReceipts(previousArchive, secret)
        val auxiliary = when (older) {
            is FiveStoreAuthenticatedPayload.Opened -> older.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Fixture rejected: " + older.reason)
        }
        val household = FaroV5Household10Fbs2Snapshot(auxiliary, Household10BackupSection(0, emptyList()))
        val receipts = Household11ReceiptBackupSection(3, listOf(receipt(1, 7_000), receipt(2, 3_000)))
        val purchase = Household12PurchaseBackupRow(
            "purchase", "yerba", 10_000, "UNIT", "local-human", 1, "MANUAL_EXPLICIT", 1,
        )
        val undo = Household13CompensationBackupRow(
            "undo-r01", "undo-intent", "r01", "purchase", 7_000, "UNIT", "local-human", 23, 3,
        )
        return FaroV5Household13Fbs2Snapshot(
            FaroV5Household11Fbs2Snapshot(household, receipts),
            listOf(purchase), listOf(undo),
        )
    }

    private fun receipt(revision: Long, amount: Int) = Household11ReceiptBackupRow(
        "r0" + revision, "i0" + revision, "purchase", "yerba", amount, "UNIT", 0,
        20 + revision, revision,
    )
}
