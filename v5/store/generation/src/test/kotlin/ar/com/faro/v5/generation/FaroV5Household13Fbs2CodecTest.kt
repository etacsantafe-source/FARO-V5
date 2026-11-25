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

class FaroV5Household13Fbs2CodecTest {
    @TempDir lateinit var root: Path

    private val codec = FaroV5Household13Fbs2Codec
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "Test-only FARO FBS2 v10".toCharArray()

    @Test
    fun roundTripPreservesOriginalAndCompensatedHistory() {
        val value = fixture()
        val encoded = codec.encode(value)
        assertEquals(10, ByteBuffer.wrap(encoded).getInt(4))
        assertEquals(value, codec.decode(encoded))
        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)))
        val archive = crypto.sealWithHouseholdCompensation13(value, passphrase)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(value),
            crypto.openWithHouseholdCompensation13(archive, passphrase))
    }

    @Test
    fun embedsStableV8AndPreservesV9BytesForLegacyHistory() {
        val value = fixture()
        val v10 = codec.encode(value)
        val size = ByteBuffer.wrap(v10).getInt(8)
        val header = 5 * Int.SIZE_BYTES + 3 * 32
        assertArrayEquals(FaroV5Household11Fbs2Codec.encode(value.base), v10.copyOfRange(header, header + size))
        val v9Value = historical(value)
        val original = FaroV5Household12Fbs2Codec.encode(v9Value)
        val sectionOffset = 4 * Int.SIZE_BYTES + 2 * 32 + ByteBuffer.wrap(original).getInt(8)
        val purchaseSectionSize = ByteBuffer.wrap(v10).getInt(12)
        assertArrayEquals(original.copyOfRange(sectionOffset, original.size),
            v10.copyOfRange(header + size, header + size + purchaseSectionSize))
        assertArrayEquals(original, FaroV5Household12Fbs2Codec.encode(FaroV5Household12Fbs2Codec.decode(original)))
    }

    @Test
    fun chronologicalNetMustStayWithinPurchaseEvenWhenFinalNetIsValid() {
        val value = fixture()
        val ledger = value.base.receipts
        val receivedBeforeUndo = ledger.receipts.map {
            if (it.receiptId == "r04") it.copy(revision = 3) else it
        }
        val lateUndo = value.compensations.single().copy(revision = 4)
        rejects {
            codec.encode(value.copy(base = value.base.copy(receipts = ledger.copy(receipts = receivedBeforeUndo)),
                compensations = listOf(lateUndo)))
        }
        assertEquals(value, codec.decode(codec.encode(value)))
    }

    @Test
    fun undoMustMatchTheOriginalReceiptAndConfirmedPrincipal() {
        val value = fixture()
        val row = value.compensations.single()
        for (bad in listOf(
            row.copy(receiptId = "missing"), row.copy(purchaseRef = "other"),
            row.copy(principal = "other"), row.copy(unit = "GRAM"),
            row.copy(quantityThousandths = 6_999), row.copy(recordedAtMs = 1),
            row.copy(revision = 1), row.copy(revision = 2), row.copy(revision = 5),
        )) {
            rejects { codec.encode(value.copy(compensations = listOf(bad))) }
        }
        rejects { codec.encode(value.copy(purchases = emptyList())) }
        val incompatible = value.base.receipts.receipts.map {
            if (it.receiptId == "r01") it.copy(stockItemRef = "different-item") else it
        }
        rejects {
            codec.encode(value.copy(base = value.base.copy(receipts =
                value.base.receipts.copy(receipts = incompatible))))
        }
    }

    @Test
    fun rejectsRepeatedUndoIntentsSourceAndNoncanonicalReferences() {
        val value = fixture()
        val row = value.compensations.single()
        for (bad in listOf(
            listOf(row, row),
            listOf(row, row.copy(compensationId = "z-undo", intentKey = "other-undo", revision = 5)),
            listOf(row, row.copy(compensationId = "z-undo", receiptId = "r02", quantityThousandths = 3_000, revision = 5)),
            listOf(row.copy(compensationId = " ")), listOf(row.copy(intentKey = "x".repeat(129))),
            listOf(row.copy(intentKey = "\ud800")), List(8_193) { row },
        )) {
            rejects {
                val ledger = value.base.receipts.copy(revision = 5)
                codec.encode(value.copy(base = value.base.copy(receipts = ledger), compensations = bad))
            }
        }
    }

    @Test
    fun rejectsTruncatedOversizedAndTamperedPlainSections() {
        val encoded = codec.encode(fixture())
        for (bad in listOf(
            encoded.copyOf(115), encoded.copyOf(encoded.size - 1), encoded + 0.toByte(),
            encoded.clone().also { ByteBuffer.wrap(it).putInt(4, 9) },
            encoded.clone().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) },
            encoded.clone().also { ByteBuffer.wrap(it).putInt(16, -1) },
            encoded.clone().also { it[20] = (it[20].toInt() xor 1).toByte() },
            encoded.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() },
        )) {
            rejects { codec.decode(bad) }
        }
    }

    @Test
    fun rejectsWrongSecretAndModificationOfAuthenticatedArchive() {
        val archive = crypto.sealWithHouseholdCompensation13(fixture(), passphrase)
        val rejected = FiveStoreAuthenticatedPayload.Rejected(FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED)
        assertEquals(rejected, crypto.openWithHouseholdCompensation13(archive, "Different test passphrase".toCharArray()))
        for (index in listOf(36, 52, archive.lastIndex)) {
            val bad = archive.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertEquals(rejected, crypto.openWithHouseholdCompensation13(bad, passphrase))
        }
    }

    @Test
    fun historicalReaderRetainsOldArchiveAndRejectsNewType() {
        val value = fixture()
        val historical = historical(value)
        val v9 = crypto.sealWithHouseholdPurchases12(historical, passphrase)
        val v10 = crypto.sealWithHouseholdCompensation13(value, passphrase)
        val rejected = FiveStoreAuthenticatedPayload.Rejected(FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(historical), crypto.openWithHouseholdPurchases12(v9, passphrase))
        assertEquals(rejected, crypto.openWithHouseholdCompensation13(v9, passphrase))
        assertEquals(rejected, crypto.openWithHouseholdPurchases12(v10, passphrase))
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private fun historical(value: FaroV5Household13Fbs2Snapshot) = FaroV5Household12Fbs2Snapshot(
        value.base.copy(receipts = Household11ReceiptBackupSection(2, value.base.receipts.receipts.take(2))),
        value.purchases,
    )

    private fun fixture(): FaroV5Household13Fbs2Snapshot {
        val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("compensation-source")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("compensation-stage"), "fbs2-v10-test", 1),
        )
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("compensation-fixture-finance"), stage, origin, "a".repeat(64), 2L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val previousArchive = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
            MaintenanceOperationId("compensation-fixture-capture"), BackupSetId("compensation-backup"),
            stage, origin, "5.0-dev", 3L, passphrase,
        )
        val older = crypto.openWithReceipts(previousArchive, passphrase)
        val auxiliary = when (older) {
            is FiveStoreAuthenticatedPayload.Opened -> older.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Historical fixture rejected: " + older.reason)
        }
        val household = FaroV5Household10Fbs2Snapshot(auxiliary, Household10BackupSection(0, emptyList()))
        val receipts = Household11ReceiptBackupSection(4, listOf(row(1, 7000), row(2, 3000), row(4, 7000)))
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
