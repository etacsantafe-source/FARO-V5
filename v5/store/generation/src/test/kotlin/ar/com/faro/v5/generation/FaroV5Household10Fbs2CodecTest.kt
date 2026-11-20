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
