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
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10ArchiveCoordinatorTest {
    @TempDir lateinit var root: Path

    private val passphrase = "aux4-archive-fixture-only".toCharArray()
    private val pointer get() = FileActiveGenerationPointer(root)
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    private fun origin(): ActiveGenerationPointer =
        checkNotNull(pointer.compareAndSet(null, StorageGenerationId("archive-origin")))

    private fun stage(name: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val prepared = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "receipt-archive", 100L),
        )
        return FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("migrate-" + name), prepared, origin, "a".repeat(64), 1000L,
        )
    }

    private fun source(origin: ActiveGenerationPointer, needs: Boolean = true): StagedStorageGeneration {
        val source = stage("archive-source", origin)
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO matter_record(matter_id,title,detail,status,due_at_epoch_millis,updated_at_epoch_millis) " +
                "VALUES('matter-source','Asunto de prueba','Detalle','OPEN',NULL,1000)")
            db.execSQL("UPDATE matter_state SET revision=1 WHERE singleton_id=1")
            db.execSQL("INSERT INTO reminder_rule VALUES('source-rule','Aviso','UTC','2026-11-19'," +
                "'08:00','ONCE',1,'matter-source',1000)")
            db.execSQL("UPDATE reminder_state SET revision=1 WHERE singleton_id=1")
            db.execSQL("INSERT INTO finance_budget_limit VALUES('2026-11','Comida','ARS',300000,1,1000,'HUMAN')")
        }
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { db ->
            db.execSQL("INSERT INTO bookmark_scope(scope_id,generation_id,authority_digest,revision) " +
                "VALUES(1,'workspace-ref','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',1)")
            db.execSQL("INSERT INTO bookmark_folder(folder_id,name,revision) VALUES('ideas','Ideas',1)")
        }
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).use { owner ->
            val notice = owner.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:source-occ"),
                SourceRef.of("reminder:source-rule"),
                OccurrenceRef.of("source-occ"),
                NoticeMateriality.NOTICE,
                NoticeSensitivity.NORMAL,
                NoticeSummary.of("Aviso"),
                DestinationRef.of("faro:avisos.local"),
            )) { true }.notice
            owner.mutateReminderNoticeAttentionWithReceipt(
                "source-click", notice.noticeId, 1L, NoticeAttention.SEEN,
            ) { true }
        }
        val reality = source.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        BundledSQLiteDriver().open(reality.toString()).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=8")
            if (needs) {
                db.execSQL("INSERT INTO household_need VALUES ('b','Leche',1000,'LITER','NORMAL','OPEN',6,NULL)")
                db.execSQL("INSERT INTO household_need VALUES ('a','Yerba',500,'GRAM','HIGH','COMPLETED',7,20000)")
            }
        }
        return source
    }

    private fun capture(source: StagedStorageGeneration, origin: ActiveGenerationPointer, probe: () -> Unit = {}) =
        FaroV5Household10ArchiveCoordinator(root, lease, probe).captureAuthenticated(
            MaintenanceOperationId("capture-household"), BackupSetId("backup-household"), source, origin,
            "5.0-dev", 2000L, passphrase,
        )

    private fun open(bytes: ByteArray): FaroV5Household10Fbs2Snapshot =
        when (val result = FaroV5FiveStorePortableBackupCrypto().openWithHousehold(bytes, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> result.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Unexpected rejected archive: " + result.reason)
        }

    @Test fun capturesFiveRealStoresReceiptsFinanceAndHouseholdWithoutChangingSource() {
        val origin = origin()
        val source = source(origin)
        val files = GenerationStoreFiles.WORKSPACE_V2.map { source.directory.resolve(it) }
        val before = files.map { Files.readAllBytes(it) }
        val archive = capture(source, origin)
        val snapshot = open(archive)
        assertEquals(origin, pointer.read())
        files.zip(before).forEach { (file, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(file)) }
        assertEquals(1, snapshot.base.receipts.size)
        assertEquals(1, checkNotNull(snapshot.base.base.g4Domains).matters.matters.size)
        assertEquals(1, checkNotNull(snapshot.base.base.planningDomains).reminders.rules.size)
        assertEquals(1, checkNotNull(snapshot.base.base.finance9).tables.single {
            it.table == "finance_budget_limit"
        }.rows.size)
        assertEquals(8L, snapshot.household.revision)
        assertEquals(listOf("a", "b"), snapshot.household.needs.map { it.id.value })
        assertEquals(20_000L, snapshot.household.needs.first().dueDate?.toEpochDay())
        assertTrue(FaroV5FiveStorePortableBackupCrypto().openWithReceipts(archive, passphrase)
            is FiveStoreAuthenticatedPayload.Rejected)
    }

    @Test fun preservesRevisedEmptyHousehold() {
        val origin = origin()
        val snapshot = open(capture(source(origin, needs = false), origin))
        assertEquals(Household10BackupSection(8, emptyList()), snapshot.household)
        assertEquals(1, snapshot.base.receipts.size)
    }

    @Test fun refusesActiveSourceAndStaleExpectedPointer() {
        val origin = origin()
        val source = source(origin)
        val next = checkNotNull(pointer.compareAndSet(origin.generationId, source.descriptor.generationId))
        assertThrows(IllegalStateException::class.java) { capture(source, origin) }
        assertThrows(IllegalStateException::class.java) { capture(source, next) }
    }

    @Test fun refusesSchemaNineHardlinksAndDanglingSidecars() {
        val origin = origin()
        assertThrows(IllegalStateException::class.java) { capture(stage("still-nine", origin), origin) }
        val source = source(origin)
        val reality = source.directory.resolve(GenerationStoreFiles.REALITY)
        val hardlink = Files.createLink(root.resolve("alias.db"), reality)
        assertThrows(IllegalStateException::class.java) { capture(source, origin) }
        Files.delete(hardlink)
        val dangling = Files.createSymbolicLink(reality.resolveSibling(reality.fileName.toString() + "-wal"), root.resolve("missing"))
        assertThrows(IllegalStateException::class.java) { capture(source, origin) }
        Files.delete(dangling)
    }

    @Test fun refusesWrongCompanionSchemaAndLostLease() {
        val origin = origin()
        val source = source(origin)
        assertThrows(IllegalStateException::class.java) { capture(source, origin) { error("lease unavailable") } }
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.WORKSPACE).toString()).use {
            it.execSQL("PRAGMA user_version = 3")
        }
        assertThrows(IllegalStateException::class.java) { capture(source, origin) }
    }
}
