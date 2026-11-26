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
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household14Fbs2CodecTest {
    @TempDir lateinit var root: Path

    private val codec = FaroV5Household14Fbs2Codec
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "Test-only FARO FBS2 v11".toCharArray()

    @Test
    fun roundTripEmbedsExactV10BytesAndPreservesPartialRelease() {
        val value = fixture()
        val bytes = codec.encode(value)
        assertEquals(11, ByteBuffer.wrap(bytes).getInt(4))
        val v10Size = ByteBuffer.wrap(bytes).getInt(8)
        assertArrayEquals(FaroV5Household13Fbs2Codec.encode(value.base), bytes.copyOfRange(80, 80 + v10Size))
        assertEquals(value, codec.decode(bytes))
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)))
        val empty = value.copy(reservations = emptyList())
        assertEquals(empty, codec.decode(codec.encode(empty)))
    }

    @Test
    fun rejectsOverbookingAndReleasesWithInvalidOriginOrBalance() {
        val value = fixture()
        val (reserve, release) = value.reservations
        val second = reserve.copy(eventId = "reserve-2", intentKey = "intent-2", reservationRef = "reserve-2", revision = 6)
        for (rows in listOf(
            listOf(reserve.copy(quantityThousandths = 3_001)), listOf(reserve, release, second.copy(quantityThousandths = 2_001)),
            listOf(reserve, release.copy(principal = "other")), listOf(reserve, release.copy(stockItemRef = "other")),
            listOf(reserve, release.copy(unit = "GRAM")), listOf(reserve, release.copy(reservationRef = "other")),
            listOf(reserve, release.copy(recordedAtMs = reserve.recordedAtMs - 1)), listOf(release),
            listOf(reserve, release, release.copy(eventId = "release-2", intentKey = "release-intent-2",
                quantityThousandths = 1_001, revision = 6)),
            listOf(reserve, release, second.copy(intentKey = reserve.intentKey)),
            listOf(reserve, release, second.copy(eventId = release.eventId)),
        )) {
            rejects { codec.encode(value.copy(reservations = rows)) }
        }
    }

    @Test
    fun rejectsUndoThatBreachesOutstandingReserveEvenIfLaterReleaseRestoresBalance() {
        val value = fixture()
        val ledger = value.base.base.receipts.copy(revision = 4)
        val undo = value.base.compensations.single().copy(revision = 4)
        val history = value.base.copy(base = value.base.base.copy(receipts = ledger), compensations = listOf(undo))
        val (reserve, release) = value.reservations
        rejects {
            codec.encode(value.copy(base = history, reservations = listOf(
                reserve.copy(revision = 3, quantityThousandths = 4_000), release,
            )))
        }
        val accepted = value.copy(base = history, reservations = listOf(reserve.copy(revision = 3), release))
        assertEquals(accepted, codec.decode(codec.encode(accepted)))
    }

    @Test
    fun rejectsMalformedSectionsReferencesAndRevisionCollisions() {
        val value = fixture()
        val (reserve, release) = value.reservations
        for (rows in listOf(
            listOf(reserve.copy(revision = 2)), listOf(reserve.copy(revision = 3)),
            listOf(reserve, release.copy(revision = reserve.revision)), listOf(release, reserve),
            listOf(reserve.copy(intentKey = "bad ref")), listOf(reserve.copy(intentKey = "\ud800")),
            listOf(reserve.copy(quantityThousandths = Int.MIN_VALUE)), List(8_193) { reserve },
        )) {
            rejects { codec.encode(value.copy(reservations = rows)) }
        }
        val valid = codec.encode(value)
        val offset = 80 + ByteBuffer.wrap(valid).getInt(8)
        val invalidUtf8 = valid.clone().also { bytes ->
            bytes[offset + 6] = 0xFF.toByte()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes.copyOfRange(offset, bytes.size))
            digest.copyInto(bytes, 48)
        }
        rejects { codec.decode(invalidUtf8) }
        rejects { codec.decode(valid.copyOf(valid.size - 1)) }
        rejects { codec.decode(valid.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }) }
        rejects { codec.decode(valid.clone().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) }) }
    }

    @Test
    fun wideTotalsDoNotWrapAndOverbookingStillFails() {
        val value = fixture()
        val purchase = value.base.purchases.single().copy(quantityThousandths = Int.MAX_VALUE)
        val other = purchase.copy(purchaseRef = "purchase-secondary", revision = 2)
        val receipts = listOf(row(1, Int.MAX_VALUE), row(2, Int.MAX_VALUE).copy(purchaseRef = other.purchaseRef))
        val previous = value.base.copy(
            base = value.base.base.copy(receipts = Household11ReceiptBackupSection(2, receipts)),
            purchases = listOf(purchase, other),
            compensations = emptyList(),
        )
        val reserve = value.reservations.first().copy(quantityThousandths = Int.MAX_VALUE, revision = 3)
        val second = reserve.copy(eventId = "reserve-2", intentKey = "intent-2", reservationRef = "reserve-2", revision = 4)
        val exact = value.copy(base = previous, reservations = listOf(reserve, second))
        assertEquals(exact, codec.decode(codec.encode(exact)))
        val extra = second.copy(eventId = "reserve-3", intentKey = "intent-3", reservationRef = "reserve-3",
            quantityThousandths = 1, revision = 5)
        rejects { codec.encode(exact.copy(reservations = exact.reservations + extra)) }
    }

    @Test
    fun existingEnvelopeAuthenticatesBeforeDecodingAndKeepsLegacyTypeIsolated() {
        val value = fixture()
        val archive = crypto.sealWithHouseholdReservations14(value, passphrase)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(value), crypto.openWithHouseholdReservations14(archive, passphrase))
        val authFailure = FiveStoreAuthenticatedPayload.Rejected(FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED)
        assertEquals(authFailure, crypto.openWithHouseholdReservations14(archive, "Wrong test v11 secret".toCharArray()))
        val tampered = archive.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(authFailure, crypto.openWithHouseholdReservations14(tampered, passphrase))

        val legacy = crypto.sealWithHouseholdCompensation13(value.base, passphrase)
        val invalidType = FiveStoreAuthenticatedPayload.Rejected(FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(value.base), crypto.openWithHouseholdCompensation13(legacy, passphrase))
        assertEquals(invalidType, crypto.openWithHouseholdReservations14(legacy, passphrase))
        assertEquals(invalidType, crypto.openWithHouseholdCompensation13(archive, passphrase))
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private fun fixture(): FaroV5Household14Fbs2Snapshot {
        val reserve = Household14ReservationBackupRow(
            "reserve-1", "reserve-intent", Household14ReservationEventType.RESERVE,
            "reserve-1", "yerba", "UNIT", 2_000, "local-human", 24, 4,
        )
        val release = reserve.copy(eventId = "release-1", intentKey = "release-intent",
            type = Household14ReservationEventType.RELEASE, quantityThousandths = 1_000, recordedAtMs = 25, revision = 5)
        return FaroV5Household14Fbs2Snapshot(historicalStock(), listOf(reserve, release))
    }

    private fun historicalStock(): FaroV5Household13Fbs2Snapshot {
        val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("reservation-source")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("reservation-stage"), "fbs2-v11-test", 1),
        )
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("reservation-fixture-finance"), stage, origin, "a".repeat(64), 2L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val previousArchive = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
            MaintenanceOperationId("reservation-fixture-capture"), BackupSetId("reservation-backup"),
            stage, origin, "5.0-dev", 3L, passphrase,
        )
        val older = crypto.openWithReceipts(previousArchive, passphrase)
        val auxiliary = when (older) {
            is FiveStoreAuthenticatedPayload.Opened -> older.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Historical fixture rejected: " + older.reason)
        }
        val household = FaroV5Household10Fbs2Snapshot(auxiliary, Household10BackupSection(0, emptyList()))
        val receipts = Household11ReceiptBackupSection(3, listOf(row(1, 7_000), row(2, 3_000)))
        val purchase = Household12PurchaseBackupRow(
            "purchase", "yerba", 10_000, "UNIT", "local-human", 1, "MANUAL_EXPLICIT", 1,
        )
        val compensation = Household13CompensationBackupRow(
            "undo-r01", "undo-intent", "r01", "purchase", 7_000, "UNIT", "local-human", 23, 3,
        )
        return FaroV5Household13Fbs2Snapshot(
            FaroV5Household11Fbs2Snapshot(household, receipts), listOf(purchase), listOf(compensation),
        )
    }

    private fun row(revision: Long, amount: Int) = Household11ReceiptBackupRow(
        "r0$revision", "i0$revision", "purchase", "yerba", amount, "UNIT", 0, 20 + revision, revision,
    )
}
