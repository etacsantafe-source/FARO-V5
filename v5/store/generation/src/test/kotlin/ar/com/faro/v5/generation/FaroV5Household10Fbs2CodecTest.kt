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
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceBackupPort
import ar.com.faro.v5.evidence.CommercialEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceId
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedBackupRow
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.WebEvidenceBackupPort
import ar.com.faro.v5.evidence.WebEvidenceBackupSnapshot
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import ar.com.faro.v5.store.reality.RealityBackupPort
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10Fbs2CodecTest {
    @TempDir lateinit var root: Path

    private val codec = FaroV5Household10Fbs2Codec
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "Frase del archivo de prueba".toCharArray()

    @Test
    fun preservesFinanceAndReceiptDataAlongsideHousehold() {
        val (_, snapshot) = fixture()
        val bytes = codec.encode(snapshot)
        assertEquals(6, ByteBuffer.wrap(bytes).getInt(4))
        val restored = codec.decode(bytes)

        assertEquals(snapshot.household, restored.household)
        assertEquals(snapshot.base, restored.base)
        assertEquals(2L, restored.base.receipts.single().resultRevision)
        val finance = checkNotNull(restored.base.base.finance9)
        val budget = finance.tables.single { it.table == "finance_budget_limit" }
        assertEquals(listOf("2026-11", "Comida", "ARS", "300000", "1", "1000", "HUMAN"), budget.rows.single())
        assertArrayEquals(bytes, codec.encode(restored))
    }

    @Test
    fun roundTripsAnExplicitEmptyHouseholdSection() {
        val (_, snapshot) = fixture()
        val empty = snapshot.copy(household = Household10BackupSection(13, emptyList()))
        assertEquals(empty, codec.decode(codec.encode(empty)))
    }

    @Test
    fun rejectsMissingSectionsCorruptDigestsAndIncorrectLengths() {
        val (_, snapshot) = fixture()
        val bytes = codec.encode(snapshot)
        val emptyHousehold = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(12, 0) }
        val hugeBase = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) }
        val corruptBase = bytes.copyOf().also { it[80] = (it[80].toInt() xor 1).toByte() }
        val corruptHousehold = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        for (invalid in listOf(emptyHousehold, hugeBase, corruptBase, corruptHousehold,
            bytes.copyOf(bytes.size - 1), bytes + 0.toByte())) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(invalid) }
        }
    }

    @Test
    fun readersKeepVFiveAndVSixSeparateInPlaintextAndAuthenticatedArchives() {
        val (previousArchive, snapshot) = fixture()
        val legacyBytes = FaroV5Auxiliary4Fbs2Codec.encode(snapshot.base)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(legacyBytes) }
        assertThrows(IllegalArgumentException::class.java) {
            FaroV5Auxiliary4Fbs2Codec.decode(codec.encode(snapshot))
        }

        val updatedArchive = crypto.sealWithHousehold(snapshot, passphrase)
        val incompatible = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD,
        )
        assertEquals(FiveStoreAuthenticatedPayload.Opened(snapshot.base),
            crypto.openWithReceipts(previousArchive, passphrase))
        assertEquals(incompatible, crypto.openWithHousehold(previousArchive, passphrase))
        assertEquals(incompatible, crypto.openWithReceipts(updatedArchive, passphrase))
        assertEquals(FiveStoreAuthenticatedPayload.Opened(snapshot),
            crypto.openWithHousehold(updatedArchive, passphrase))
    }

    @Test
    fun authenticatedHouseholdRejectsWrongPassphraseAndTampering() {
        val (_, snapshot) = fixture()
        val archive = crypto.sealWithHousehold(snapshot, passphrase)
        val failure = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED,
        )
        assertEquals(failure, crypto.openWithHousehold(archive, "Otra frase local de prueba".toCharArray()))
        archive[archive.lastIndex] = (archive.last().toInt() xor 1).toByte()
        assertEquals(failure, crypto.openWithHousehold(archive, passphrase))
    }


    /** F2B2B01 directed tests only. Existing Household10 v6 tests remain unchanged. */
    @Test fun f2b2b01Evidence2RoundTripPreservesOriginalIdentityAndThreeTermStates() {
        val typed = withEvidence2(fixture().second)
        val bytes = codec.encodeEvidence2(typed)
        assertEquals(7, ByteBuffer.wrap(bytes).getInt(4))
        val restored = codec.decodeEvidence2(bytes)
        assertEquals(typed, restored)
        val rows = checkNotNull(restored.base.base.fourStore.commercialEvidence.stagedTyped).rows
        assertEquals(3, rows.size)
        assertEquals(null, rows[0].terms)
        assertEquals(CommercialEvidenceTypedTerms.UNKNOWN, rows[1].terms)
        assertEquals(CommercialPromotionKind.DISCOUNT_PERCENT, rows[2].terms?.promotionKind)
        assertEquals("USD", rows[1].record.observation.currencyCode.value)
        assertArrayEquals(bytes, codec.encodeEvidence2(restored))
    }

    @Test fun f2b2b02NeverCrossAcceptsV6AndV7OrWrongSchema() {
        val old = fixture().second
        val typed = withEvidence2(old)
        val six = codec.encode(old)
        val seven = codec.encodeEvidence2(typed)
        assertEquals(6, ByteBuffer.wrap(six).getInt(4))
        assertEquals(7, ByteBuffer.wrap(seven).getInt(4))
        assertThrows(IllegalArgumentException::class.java) { codec.encode(typed) }
        assertThrows(IllegalArgumentException::class.java) { codec.encodeEvidence2(old) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(seven) }
        assertThrows(IllegalArgumentException::class.java) { codec.decodeEvidence2(six) }
        assertArrayEquals(six, codec.encode(codec.decode(six)))
    }

    @Test fun f2b2b03RejectsCorruptDigestLengthsAndTrailingBytes() {
        val bytes = codec.encodeEvidence2(withEvidence2(fixture().second))
        val corrupt = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val huge = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) }
        val missing = bytes.copyOf(bytes.size - 1)
        val trailing = bytes + 0.toByte()
        val wrongVersion = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(4, 8) }
        for (invalid in listOf(corrupt, huge, missing, trailing, wrongVersion)) {
            assertThrows(IllegalArgumentException::class.java) { codec.decodeEvidence2(invalid) }
        }
    }

    @Test fun f2b2b04AuthenticatedTypedArchiveRoundTripAndLegacyIsolation() {
        val old = fixture().second
        val typed = withEvidence2(old)
        val archive = crypto.sealWithHouseholdEvidence2(typed, passphrase)
        assertEquals(FiveStoreAuthenticatedPayload.Opened(typed),
            crypto.openWithHouseholdEvidence2(archive, passphrase))
        val invalid = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD)
        assertEquals(invalid, crypto.openWithHousehold(archive, passphrase))
        val legacy = crypto.sealWithHousehold(old, passphrase)
        assertEquals(invalid, crypto.openWithHouseholdEvidence2(legacy, passphrase))
    }

    @Test fun f2b2b05AuthenticatedTypedArchiveRejectsTamperAndWrongPassword() {
        val typed = withEvidence2(fixture().second)
        val archive = crypto.sealWithHouseholdEvidence2(typed, passphrase)
        val invalid = FiveStoreAuthenticatedPayload.Rejected(
            FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED)
        assertEquals(invalid,
            crypto.openWithHouseholdEvidence2(archive, "different-local-passphrase".toCharArray()))
        archive[archive.lastIndex] = (archive.last().toInt() xor 1).toByte()
        assertEquals(invalid, crypto.openWithHouseholdEvidence2(archive, passphrase))
    }

    @Test fun f2b2b06EmptyTypedSectionIsExplicitAndDoesNotMutateV6() {
        val old = fixture().second
        val oldBytes = codec.encode(old)
        val typed = withEvidence2(old, includeRows = false)
        val seven = codec.encodeEvidence2(typed)
        assertEquals(emptyList<CommercialEvidenceStagedTypedBackupRow>(),
            checkNotNull(codec.decodeEvidence2(seven).base.base.fourStore.commercialEvidence.stagedTyped).rows)
        assertArrayEquals(seven, codec.encodeEvidence2(codec.decodeEvidence2(seven)))
        assertArrayEquals(oldBytes, codec.encode(old))
    }

    private fun withEvidence2(
        existing: FaroV5Household10Fbs2Snapshot,
        includeRows: Boolean = true,
    ): FaroV5Household10Fbs2Snapshot {
        val base = existing.base.base.fourStore
        fun record(id: String, currency: String): CommercialEvidenceRecord =
            CommercialEvidenceRecord(
                CommercialEvidenceId(id), IdempotencyKey("key-" + id),
                PayloadFingerprint("a".repeat(64)),
                CommercialPriceObservation(
                    CommercialProductKey("rice-1000"), CommercialMerchant("market"),
                    CommercialCurrencyCode(currency), 12_000L,
                    CommercialEvidenceSourceKind.MANUAL, "manual:" + id, 100L,
                ),
                acquiredAtEpochMillis = 220L,
            )
        val fullTerms = CommercialEvidenceTypedTerms(
            localityKind = CommercialLocalityKind.KNOWN,
            localityCode = "AR-SF", issuedAtEpochMillis = 100L,
            validFromEpochMillis = 110L, validUntilEpochMillis = 200L,
            presentationQuantityThousandths = 1_000L,
            presentationUnit = CommercialPresentationUnit.GRAM,
            packCount = 1, paymentMethod = CommercialPaymentMethod.DEBIT,
            eligibility = CommercialEligibility.GENERAL, conditions = "Solo socios",
            promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
            promotionValue = 1_200L, promotionSourceRef = "manual:flyer",
        )
        val rows = if (includeRows) listOf(
            CommercialEvidenceStagedTypedBackupRow(record("evidence-1", "ARS"), null),
            CommercialEvidenceStagedTypedBackupRow(record("evidence-2", "USD"), CommercialEvidenceTypedTerms.UNKNOWN),
            CommercialEvidenceStagedTypedBackupRow(record("evidence-3", "ARS"), fullTerms),
        ) else emptyList()
        val evidence2 = CommercialEvidenceBackupSnapshot(
            schemaVersion = 2, records = rows.map { it.record },
            stagedTyped = CommercialEvidenceStagedTypedBackupSnapshot(2, rows),
        )
        val snapshot = SemanticBackupCaptureCoordinator(
            quiescence = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate()),
            reality = object : RealityBackupPort {
                override fun snapshot(): RealityBackupSnapshot = base.reality
                override fun restoreOrVerify(snapshot: RealityBackupSnapshot): RealityBackupSnapshot =
                    error("test snapshot only")
            },
            commercialEvidence = object : CommercialEvidenceBackupPort {
                override fun snapshot(): CommercialEvidenceBackupSnapshot = evidence2
                override fun restoreOrVerify(snapshot: CommercialEvidenceBackupSnapshot): CommercialEvidenceBackupSnapshot =
                    error("test snapshot only")
            },
            webEvidence = object : WebEvidenceBackupPort {
                override fun snapshot(): WebEvidenceBackupSnapshot = base.webEvidence
                override fun restoreOrVerify(snapshot: WebEvidenceBackupSnapshot): WebEvidenceBackupSnapshot =
                    error("test snapshot only")
            },
            auxiliary = object : AuxiliaryBackupPort {
                override fun snapshot(): AuxiliaryBackupSnapshot = base.auxiliary
                override fun restoreOrVerify(snapshot: AuxiliaryBackupSnapshot): AuxiliaryBackupSnapshot =
                    error("test snapshot only")
            },
        ).capture(
            MaintenanceOperationId("f2b2b-typed-snapshot"),
            base.manifest.backupSetId, base.manifest.sourceGenerationId,
            base.manifest.productVersion, base.manifest.createdAtEpochMillis,
        )
        return existing.copy(base = existing.base.copy(
            base = existing.base.base.copy(fourStore = snapshot),
        ))
    }

    private fun fixture(): Pair<ByteArray, FaroV5Household10Fbs2Snapshot> {
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
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).use { owner ->
            val notice = owner.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:household-occ"),
                SourceRef.of("reminder:household-rule"),
                OccurrenceRef.of("household-occ"),
                NoticeMateriality.NOTICE,
                NoticeSensitivity.NORMAL,
                NoticeSummary.of("Aviso de prueba"),
                DestinationRef.of("faro:avisos.local"),
            )) { true }.notice
            owner.mutateReminderNoticeAttentionWithReceipt(
                "household-seen", notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true }
        }
        val previousArchive = FaroV5Auxiliary4ArchiveCoordinator(root, lease).captureAuthenticated(
            MaintenanceOperationId("capture-archive-source"), BackupSetId("household-archive"),
            stage, origin, "5.0-dev", 1001L, passphrase,
        )
        val base = when (val opened = crypto.openWithReceipts(previousArchive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Fixture archive rejected: " + opened.reason)
        }
        val household = Household10BackupSection(5, listOf(HouseholdNeed(
            HouseholdNeedId("need-1"), HouseholdNeedName("Yerba orgánica"),
            HouseholdNeedQuantity(1500, HouseholdNeedUnit.KILOGRAM),
            HouseholdNeedPriority.HIGH, HouseholdNeedStatus.OPEN, 2, LocalDate.of(2026, 11, 25),
        )))
        return previousArchive to FaroV5Household10Fbs2Snapshot(base, household)
    }
}
