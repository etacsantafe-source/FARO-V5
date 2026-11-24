package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceBackupPort
import ar.com.faro.v5.evidence.CommercialEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedBackupSnapshot
import ar.com.faro.v5.evidence.WebEvidenceBackupPort
import ar.com.faro.v5.evidence.WebEvidenceBackupSnapshot
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import ar.com.faro.v5.store.reality.RealityBackupPort
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11Fbs2CodecTest {
    @TempDir lateinit var root: Path

    private val codec = FaroV5Household11Fbs2Codec
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "Local test passphrase W05-A1B2A".toCharArray()

    @Test
    fun v8EmptyReceiptsAreExplicitAndCanonical() {
        val snap = snapshot()
        val bytes = codec.encode(snap)
        assertEquals(8, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(snap, codec.decode(bytes))
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)))
        assertEquals(0L, codec.decode(bytes).receipts.revision)
    }

    @Test
    fun partialReceiptsKeepAllIdsAndNeverSynthesizeStock() {
        val snap = snapshot().copy(receipts = Household11ReceiptBackupSection(
            2L, listOf(row("r-01", "i-01", 7_000, 1L), row("r-02", "i-02", 3_000, 2L)),
        ))
        val recovered = codec.decode(codec.encode(snap))
        assertEquals(snap, recovered)
        assertEquals(listOf(7_000, 3_000), recovered.receipts.receipts.map { it.quantityThousandths })
        assertEquals(snap.base.household, recovered.base.household)
    }

    @Test
    fun nestedLegacyV6HasByteExactPreservationAndNewVersion() {
        val snap = snapshot()
        val inner = FaroV5Household10Fbs2Codec.encode(snap.base)
        val archive = codec.encode(snap)
        val baseLen = ByteBuffer.wrap(archive).getInt(8)
        assertArrayEquals(inner, archive.copyOfRange(80, 80 + baseLen))
        assertArrayEquals(inner, FaroV5Household10Fbs2Codec.encode(snap.base))
    }

    @Test
    fun nestedTypedEvidenceV7RemainsLossless() {
        val typed = snapshot().let { it.copy(base = withEvidence2(it.base)) }
        val inner = FaroV5Household10Fbs2Codec.encodeEvidence2(typed.base)
        assertEquals(7, ByteBuffer.wrap(inner).getInt(4))
        val bytes = codec.encode(typed)
        assertEquals(typed, codec.decode(bytes))
        assertArrayEquals(inner, bytes.copyOfRange(80, 80 + ByteBuffer.wrap(bytes).getInt(8)))
    }

    @Test
    fun oldReadersAndNewReaderNeverCrossAcceptVersion() {
        val old = snapshot()
        val v6 = FaroV5Household10Fbs2Codec.encode(old.base)
        val v7 = FaroV5Household10Fbs2Codec.encodeEvidence2(withEvidence2(old.base))
        val v8 = codec.encode(old)
        for (historical in listOf(v6, v7)) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(historical) }
        }
        assertThrows(IllegalArgumentException::class.java) { FaroV5Household10Fbs2Codec.decode(v8) }
        assertThrows(IllegalArgumentException::class.java) { FaroV5Household10Fbs2Codec.decodeEvidence2(v8) }
        assertThrows(IllegalArgumentException::class.java) { FaroV5Auxiliary4Fbs2Codec.decode(v8) }
    }

    @Test
    fun sectionTamperRejectedEvenIfOnlyOneByteChanges() {
        val bytes = codec.encode(snapshot())
        val headerTamper = bytes.clone().also { it[16] = (it[16].toInt() xor 1).toByte() }
        val baseTamper = bytes.clone().also { it[80] = (it[80].toInt() xor 1).toByte() }
        val receiptsTamper = bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (invalid in listOf(headerTamper, baseTamper, receiptsTamper)) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(invalid) }
        }
    }

    @Test
    fun forgedVersionLengthsAndTrailingBytesFailClosed() {
        val bytes = codec.encode(snapshot())
        val invalid = listOf(
            bytes.clone().also { ByteBuffer.wrap(it).putInt(0, 0) },
            bytes.clone().also { ByteBuffer.wrap(it).putInt(4, 7) },
            bytes.clone().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) },
            bytes.clone().also { ByteBuffer.wrap(it).putInt(12, 0) },
            bytes.clone().also { ByteBuffer.wrap(it).putInt(12, -1) },
            bytes.copyOf(bytes.size - 1),
            bytes + 0.toByte(),
        )
        for (corrupt in invalid) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(corrupt) }
        }
    }

    @Test
    fun invalidDuplicateAndOutOfRevisionReceiptSectionsCannotSeal() {
        val base = snapshot().base
        val invalid = listOf(
            Household11ReceiptBackupSection(1, listOf(row("r", "i", 1_000, 1), row("r", "j", 2_000, 1))),
            Household11ReceiptBackupSection(2, listOf(row("a", "same", 1_000, 1), row("b", "same", 2_000, 2))),
            Household11ReceiptBackupSection(1, listOf(row("a", "i", 1_000, 2))),
            Household11ReceiptBackupSection(1, listOf(row("a", "i", 0, 1))),
        )
        for (section in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                codec.encode(FaroV5Household11Fbs2Snapshot(base, section))
            }
        }
    }

    @Test
    fun authenticatedRoundTripAndVersionIsolation() {
        val snap = snapshot()
        val archive = crypto.sealWithHouseholdReceipts11(snap, passphrase)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(snap),
            crypto.openWithHouseholdReceipts11(archive, passphrase))
        val invalid = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD)
        assertEquals(invalid, crypto.openWithHousehold(archive, passphrase))
        assertEquals(invalid, crypto.openWithHouseholdEvidence2(archive, passphrase))
        val oldArchive = crypto.sealWithHousehold(snap.base, passphrase)
        assertEquals(invalid, crypto.openWithHouseholdReceipts11(oldArchive, passphrase))
    }

    @Test
    fun authenticatedWrongPassphraseTagAndHeaderTamperFailClosed() {
        val snap = snapshot()
        val archive = crypto.sealWithHouseholdReceipts11(snap, passphrase)
        val badAuth = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED)
        assertEquals(badAuth,
            crypto.openWithHouseholdReceipts11(archive, "different secret phrase for test".toCharArray()))
        val tag = archive.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(badAuth, crypto.openWithHouseholdReceipts11(tag, passphrase))
        val header = archive.clone().also { it[0] = 0 }
        assertEquals(FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.INVALID_ENVELOPE),
            crypto.openWithHouseholdReceipts11(header, passphrase))
    }

    private fun row(id: String, intent: String, quantity: Int, revision: Long) =
        Household11ReceiptBackupRow(
            receiptId = id, intentKey = intent, purchaseRef = "purchase-A",
            stockItemRef = "yerba", quantityThousandths = quantity, unit = "UNIT",
            receivedEpochDay = 0L, recordedAtMs = 1_000L, revision = revision,
        )

    private fun snapshot(): FaroV5Household11Fbs2Snapshot {
        val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("archive-origin")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("archive-source"), "household-fbs2", 100L),
        )
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("migrate-archive-source"), stage, origin, "a".repeat(64), 1000L,
        )
        BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("INSERT INTO finance_budget_limit VALUES('2026-11','Comida','ARS',300000,1,1000,'HUMAN')")
        }
        val previousArchive = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
            MaintenanceOperationId("capture-archive-source"), BackupSetId("household-archive"),
            stage, origin, "5.0-dev", 1001L, passphrase,
        )
        val base = when (val opened = crypto.openWithReceipts(previousArchive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Legacy fixture rejected: " + opened.reason)
        }
        val household = Household10BackupSection(5, listOf(HouseholdNeed(
            HouseholdNeedId("need-1"), HouseholdNeedName("Yerba"),
            HouseholdNeedQuantity(1500, HouseholdNeedUnit.KILOGRAM),
            HouseholdNeedPriority.HIGH, HouseholdNeedStatus.OPEN, 2, LocalDate.of(2026, 11, 25),
        )))
        return FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(base, household),
            Household11ReceiptBackupSection(0L, emptyList()),
        )
    }

    private fun withEvidence2(existing: FaroV5Household10Fbs2Snapshot): FaroV5Household10Fbs2Snapshot {
        val original = existing.base.base.fourStore
        val typed = CommercialEvidenceBackupSnapshot(
            schemaVersion = 2, records = emptyList(),
            stagedTyped = CommercialEvidenceStagedTypedBackupSnapshot(2, emptyList()),
        )
        val captured = SemanticBackupCaptureCoordinator(
            quiescence = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate()),
            reality = object : RealityBackupPort {
                override fun snapshot(): RealityBackupSnapshot = original.reality
                override fun restoreOrVerify(snapshot: RealityBackupSnapshot): RealityBackupSnapshot =
                    error("snapshot only")
            },
            commercialEvidence = object : CommercialEvidenceBackupPort {
                override fun snapshot(): CommercialEvidenceBackupSnapshot = typed
                override fun restoreOrVerify(snapshot: CommercialEvidenceBackupSnapshot): CommercialEvidenceBackupSnapshot =
                    error("snapshot only")
            },
            webEvidence = object : WebEvidenceBackupPort {
                override fun snapshot(): WebEvidenceBackupSnapshot = original.webEvidence
                override fun restoreOrVerify(snapshot: WebEvidenceBackupSnapshot): WebEvidenceBackupSnapshot =
                    error("snapshot only")
            },
            auxiliary = object : AuxiliaryBackupPort {
                override fun snapshot(): AuxiliaryBackupSnapshot = original.auxiliary
                override fun restoreOrVerify(snapshot: AuxiliaryBackupSnapshot): AuxiliaryBackupSnapshot =
                    error("snapshot only")
            },
        ).capture(MaintenanceOperationId("w05a1b2a-typed"), original.manifest.backupSetId,
            original.manifest.sourceGenerationId, original.manifest.productVersion,
            original.manifest.createdAtEpochMillis)
        return existing.copy(base = existing.base.copy(
            base = existing.base.base.copy(fourStore = captured),
        ))
    }
}
