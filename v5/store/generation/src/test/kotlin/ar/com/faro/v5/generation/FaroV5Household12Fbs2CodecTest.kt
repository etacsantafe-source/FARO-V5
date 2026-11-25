package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
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
import java.security.MessageDigest
import java.nio.file.Path
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household12Fbs2CodecTest {
    @TempDir lateinit var root: Path

    private val codec = FaroV5Household12Fbs2Codec
    private val previous = FaroV5Household11Fbs2Codec
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "Local test passphrase W05-A1C0B1".toCharArray()
    private val invalidPayload = FiveStoreAuthenticatedPayload.Rejected(
        FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD,
    )

    @Test
    fun evidence2PurchaseAndTwoPartialReceiptsRoundTrip() {
        val base = receiptSnapshot().let { it.copy(base = withEvidence2(it.base)) }
            .copy(receipts = Household11ReceiptBackupSection(2L, listOf(
                row("r-01", "i-01", 7_000, 1L),
                row("r-02", "i-02", 3_000, 2L),
            )))
        val snapshot = FaroV5Household12Fbs2Snapshot(base, listOf(purchase()))
        val bytes = codec.encode(snapshot)
        assertEquals(9, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(snapshot, codec.decode(bytes))
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)))
        assertEquals(FiveStoreAuthenticatedPayload.Opened(snapshot),
            crypto.openWithHouseholdPurchases12(crypto.sealWithHouseholdPurchases12(snapshot, passphrase), passphrase))
    }

    @Test
    fun legacyReceiptWithNoPurchaseRemainsUnlinked() {
        val base = receiptSnapshot().copy(
            receipts = Household11ReceiptBackupSection(1L, listOf(row("r-01", "i-01", 7_000, 1L))),
        )
        val decoded = codec.decode(codec.encode(FaroV5Household12Fbs2Snapshot(base, emptyList())))
        assertEquals(emptyList<Household12PurchaseBackupRow>(), decoded.purchases)
        assertEquals(base, decoded.base)
    }

    @Test
    fun evidence1AndEvidence2KeepByteExactEmbeddedV8() {
        val evidence1 = receiptSnapshot()
        for (base in listOf(evidence1, evidence1.copy(base = withEvidence2(evidence1.base)))) {
            val old = previous.encode(base)
            val current = codec.encode(FaroV5Household12Fbs2Snapshot(base, emptyList()))
            assertArrayEquals(old, current.copyOfRange(80, 80 + ByteBuffer.wrap(current).getInt(8)))
            assertEquals(base, previous.decode(old))
            rejects { codec.decode(old) }
            rejects { previous.decode(current) }
        }
    }

    @Test
    fun invalidPurchaseFieldsAndNoncanonicalReferencesAreRejected() {
        val base = receiptSnapshot()
        val p = purchase()
        val invalidRows = listOf(
            p.copy(purchaseRef = " purchase-A"), p.copy(purchaseRef = ""),
            p.copy(stockItemRef = "bad\u0000ref"), p.copy(confirmedBy = "a".repeat(129)),
            p.copy(confirmedBy = "\uD800"), p.copy(revision = 0),
            p.copy(quantityThousandths = 0), p.copy(quantityThousandths = Int.MIN_VALUE),
            p.copy(confirmedAtMs = -1), p.copy(provenance = "CART"),
            p.copy(unit = "BOX"),
        )
        for (invalid in invalidRows.map { listOf(it) } +
            listOf(listOf(p, p), listOf(p.copy(purchaseRef = "purchase-Z"), p))) {
            rejects { codec.encode(FaroV5Household12Fbs2Snapshot(base, invalid)) }
        }
        val valid = codec.encode(FaroV5Household12Fbs2Snapshot(base, listOf(p)))
        rejects { codec.decode(changePurchaseBytes(valid) { it[6] = 0x80.toByte() }) }
    }

    @Test
    fun linkedReceiptsMustMatchUnitItemAndAggregateQuantity() {
        val base = receiptSnapshot()
        val receive = row("r-01", "i-01", 7_000, 1L)
        val second = row("r-02", "i-02", 3_001, 2L)
        val invalid = listOf(
            listOf(receive.copy(stockItemRef = "otro")),
            listOf(receive.copy(unit = "PACKAGE")),
            listOf(receive, second),
            listOf(receive.copy(quantityThousandths = Int.MAX_VALUE), second),
        )
        for (rows in invalid) {
            val snapshot = FaroV5Household12Fbs2Snapshot(
                base.copy(receipts = Household11ReceiptBackupSection(2L, rows)), listOf(purchase()),
            )
            rejects { codec.encode(snapshot) }
        }
        val valid = FaroV5Household12Fbs2Snapshot(
            base.copy(receipts = Household11ReceiptBackupSection(1L, listOf(receive))), listOf(purchase()),
        )
        val stored = codec.encode(valid)
        val quantityOffset = 4 + 2 + purchase().purchaseRef.length + 2 + purchase().stockItemRef.length
        rejects { codec.decode(changePurchaseBytes(stored) { ByteBuffer.wrap(it).putInt(quantityOffset, 6_999) }) }
    }

    @Test
    fun malformedLengthsDigestsVersionsAndBudgetsFailClosed() {
        val base = receiptSnapshot()
        val valid = codec.encode(FaroV5Household12Fbs2Snapshot(base, emptyList()))
        for (bytes in listOf(
            valid.copyOf(79), valid.copyOf(valid.size - 1), valid + 0.toByte(),
            valid.clone().also { ByteBuffer.wrap(it).putInt(4, 10) },
            valid.clone().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) },
            valid.clone().also { ByteBuffer.wrap(it).putInt(12, -1) },
            valid.clone().also { it[16] = (it[16].toInt() xor 1).toByte() },
            valid.clone().also { it[it.lastIndex] = 1 },
            ByteArray(FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES + 1),
            changePurchaseBytes(valid) { ByteBuffer.wrap(it).putInt(8_193) },
            changePurchaseBytes(valid + 0.toByte(), appendByte = true) {},
        )) {
            rejects { codec.decode(bytes) }
        }
        rejects {
            codec.encode(FaroV5Household12Fbs2Snapshot(base, List(8_193) { purchase() }))
        }
    }

    @Test
    fun wrongPassphraseTagSaltNonceAndEnvelopeHeaderAreRejected() {
        val snapshot = FaroV5Household12Fbs2Snapshot(receiptSnapshot(), listOf(purchase()))
        val archive = crypto.sealWithHouseholdPurchases12(snapshot, passphrase)
        val badAuth = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
        )
        assertEquals(badAuth, crypto.openWithHouseholdPurchases12(archive, "different local passphrase".toCharArray()))
        for (index in listOf(36, 52, archive.lastIndex)) {
            val tampered = archive.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertEquals(badAuth, crypto.openWithHouseholdPurchases12(tampered, passphrase))
        }
        val invalidHeader = archive.clone().also { it[0] = 0 }
        assertEquals(FiveStoreAuthenticatedPayload.Rejected(FiveStorePortableBackupOpenFailure.INVALID_ENVELOPE),
            crypto.openWithHouseholdPurchases12(invalidHeader, passphrase))
    }

    @Test
    fun authenticatedOpeningOnlyReturnsProvenanceAndPreservesBase() {
        val base = receiptSnapshot()
        val stored = FaroV5Household12Fbs2Snapshot(base, listOf(purchase()))
        val archive = crypto.sealWithHouseholdPurchases12(stored, passphrase)
        val opened = crypto.openWithHouseholdPurchases12(archive, passphrase)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(stored), opened)
        assertEquals(0L, base.receipts.revision)
        assertEquals(invalidPayload, crypto.openWithHouseholdReceipts11(archive, passphrase))
    }

    @Test
    fun canonicalOrderAndUtf8SurviveRepeatedDecoding() {
        val p = purchase().copy(confirmedBy = "Andrés 🛒")
        val snapshot = FaroV5Household12Fbs2Snapshot(
            receiptSnapshot(), listOf(p, p.copy(purchaseRef = "purchase-B", quantityThousandths = Int.MAX_VALUE)),
        )
        val bytes = codec.encode(snapshot)
        assertEquals(snapshot, codec.decode(bytes))
        assertArrayEquals(bytes, codec.encode(codec.decode(codec.encode(codec.decode(bytes)))))
    }

    @Test
    fun authenticatedHistoricalV8StillOpensThroughItsOwnReader() {
        val base = receiptSnapshot()
        val old = crypto.sealWithHouseholdReceipts11(base, passphrase)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(base), crypto.openWithHouseholdReceipts11(old, passphrase))
        assertEquals(invalidPayload, crypto.openWithHouseholdPurchases12(old, passphrase))
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private fun changePurchaseBytes(
        original: ByteArray,
        appendByte: Boolean = false,
        change: (ByteArray) -> Unit,
    ): ByteArray = original.clone().also { bytes ->
        val header = ByteBuffer.wrap(bytes)
        val offset = 80 + header.getInt(8)
        val section = bytes.copyOfRange(offset, bytes.size)
        change(section)
        if (appendByte) header.putInt(12, section.size)
        MessageDigest.getInstance("SHA-256").digest(section).copyInto(bytes, 48)
        section.copyInto(bytes, offset)
    }

    private fun purchase() = Household12PurchaseBackupRow(
        purchaseRef = "purchase-A", stockItemRef = "yerba", quantityThousandths = 10_000,
        unit = "UNIT", confirmedBy = "local-user", confirmedAtMs = 1_000L,
        provenance = "MANUAL_EXPLICIT", revision = 1L,
    )

    private fun row(id: String, intent: String, quantity: Int, revision: Long) =
        Household11ReceiptBackupRow(
            receiptId = id, intentKey = intent, purchaseRef = "purchase-A",
            stockItemRef = "yerba", quantityThousandths = quantity, unit = "UNIT",
            receivedEpochDay = 0L, recordedAtMs = 1_000L, revision = revision,
        )

    private fun receiptSnapshot(): FaroV5Household11Fbs2Snapshot {
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
        // The FBS2v5 capture requires a real AUXILIARY4 staged schema.
        // Reuse the original bounded local Notice receipt test transition.
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).use { owner ->
            val notice = owner.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:w05-a1c0b1-occ"),
                SourceRef.of("reminder:w05-a1c0b1-rule"),
                OccurrenceRef.of("w05-a1c0b1-occ"),
                NoticeMateriality.NOTICE,
                NoticeSensitivity.NORMAL,
                NoticeSummary.of("Aviso local de prueba"),
                DestinationRef.of("faro:avisos.local"),
            )) { true }.notice
            owner.mutateReminderNoticeAttentionWithReceipt(
                "w05-a1c0b1-notice-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true }
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
        ).capture(MaintenanceOperationId("w05a1c0b1-typed"), original.manifest.backupSetId,
            original.manifest.sourceGenerationId, original.manifest.productVersion,
            original.manifest.createdAtEpochMillis)
        return existing.copy(base = existing.base.copy(
            base = existing.base.base.copy(fourStore = captured),
        ))
    }
}
