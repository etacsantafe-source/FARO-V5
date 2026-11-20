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
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10DevCutoverRuntimeTest {
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
        FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(source, origin).use {
            val workspace = checkNotNull(it.workspace)
            workspace.backup.restoreOrVerify(workspace.backup.snapshot().copy(revision = 9L))
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

    private class Interruption : Error("injected interruption")
    private val operation = MaintenanceOperationId("activate-household")
    private val journal get() = FileBootstrapJournal(root)
    private val replay get() = FileGenerationReplayFence(root)

    private data class Scenario(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val archive: ByteArray,
        val originalDigest: String,
    )

    private fun scenario(): Scenario {
        val provisional = origin()
        val original = stage("original-finance", provisional)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(original, pointer).close()
        BundledSQLiteDriver().open(original.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("INSERT INTO finance_budget_limit VALUES('2026-10','Origen','ARS',7000,1,1000,'HUMAN')")
        }
        val pinned = checkNotNull(pointer.compareAndSet(provisional.generationId, original.descriptor.generationId))
        val source = source(pinned)
        return Scenario(pinned, source, stage("restore-target", pinned), capture(source, pinned),
            FaroV5Household10DevCutoverOwner.fingerprint(original))
    }

    private fun runtime(
        hostClosed: () -> Boolean = { true },
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        authorized: () -> Unit = {},
        fenced: () -> Unit = {},
        healthy: () -> Boolean = { true },
    ) = FaroV5Household10DevCutoverRuntime.acquire(
        root, SingleProcessMaterialWriteGate(), hostClosed, fault, authorized, fenced, healthy,
    )

    private fun FaroV5Household10DevCutoverRuntime.start(
        case: Scenario, confirmed: () -> Boolean = { true }, bytes: ByteArray = case.archive,
    ) = start(operation, case.target, case.origin, bytes, passphrase, confirmed)

    private fun assertActive(case: Scenario) {
        val expected = ActiveGenerationPointer(case.origin.sequence + 1, case.target.descriptor.generationId)
        assertEquals(expected, pointer.read())
        assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        assertTrue(hasFence(case))
        assertTrue(checkNotNull(journal.read(operation)).materialWritesAccepted)
    }

    private fun hasFence(case: Scenario): Boolean =
        replay.isInstalled(MaintenanceOperationKind.RESTORE, case.target.descriptor.generationId)

    private fun assertOriginalPreserved(case: Scenario) {
        val original = FileStorageGenerationLayout(root).requireStaged(case.origin.generationId)
        assertEquals(case.originalDigest, FaroV5Household10DevCutoverOwner.fingerprint(original))
    }

    @Test fun activatesRealArchiveAndReopensWithHouseholdFinanceAndReceipts() {
        val case = scenario()
        runtime().use { first ->
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, first.start(case).status)
            assertActive(case)
        }
        runtime().use { reopened ->
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, reopened.recover()?.status)
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, reopened.start(case).status)
        }
        assertActive(case)
        val file = case.target.directory.resolve(GenerationStoreFiles.REALITY)
        val household = RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(file)
        assertEquals(8L, household.revision)
        assertEquals(listOf("a", "b"), household.needs.map { it.id })
        val finance = RealityFinance9SealedReadPort.readTrustedSealedStage10(file)
        assertEquals("300000", finance.tables.single { it.table == "finance_budget_limit" }.rows.single()[3])
        BundledSQLiteDriver().open(case.target.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { db ->
            db.prepare("SELECT request_key FROM notice_attention_receipt").use {
                assertTrue(it.step())
                assertEquals("source-click", it.getText(0))
                assertFalse(it.step())
            }
        }
        assertOriginalPreserved(case)
    }

    @Test fun interruptionBeforeValidationKeepsOriginAndRecoversFromDurableAuthorization() {
        val case = scenario()
        runtime(authorized = { throw Interruption() }).use {
            assertThrows(Interruption::class.java) { it.start(case) }
        }
        assertEquals(case.origin, pointer.read())
        assertEquals(BootstrapPhase.PREPARING, journal.read(operation)?.phase)
        assertFalse(hasFence(case))
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, it.recover()?.status)
        }
        assertActive(case)
    }

    @Test fun interruptionAfterPointerPublishRecoversWithoutSecondCas() {
        val case = scenario()
        runtime(fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) throw Interruption()
        }).use {
            assertThrows(Interruption::class.java) { it.start(case) }
        }
        val published = checkNotNull(pointer.read())
        assertEquals(case.origin.sequence + 1, published.sequence)
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(operation)?.phase)
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, it.recover()?.status)
        }
        assertEquals(published, pointer.read())
        assertActive(case)
    }

    @Test fun interruptionAfterFenceIsIdempotentAndLaterCorruptionCannotRollBack() {
        val case = scenario()
        runtime(fenced = { throw Interruption() }).use {
            assertThrows(Interruption::class.java) { it.start(case) }
        }
        val published = pointer.read()
        assertTrue(hasFence(case))
        assertEquals(BootstrapPhase.HEALTH_VERIFIED, journal.read(operation)?.phase)
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, it.recover()?.status)
        }
        BundledSQLiteDriver().open(case.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("UPDATE household_need SET name='Alterado' WHERE need_id='a'")
        }
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED, it.recover()?.status)
        }
        assertEquals(published, pointer.read())
        assertTrue(hasFence(case))
        assertOriginalPreserved(case)
    }

    @Test fun failedHealthRollsBackToExactPreservedOriginBeforeFence() {
        val case = scenario()
        runtime(healthy = { false }).use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ROLLED_BACK, it.start(case).status)
        }
        assertEquals(ActiveGenerationPointer(case.origin.sequence + 2, case.origin.generationId), pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(operation)?.phase)
        assertFalse(hasFence(case))
        assertOriginalPreserved(case)
    }

    @Test fun recoveryCompletesInterruptedRollbackPointerPublication() {
        val case = scenario()
        runtime(healthy = { false }, fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_ROLLBACK_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) throw Interruption()
        }).use {
            assertThrows(Interruption::class.java) { it.start(case) }
        }
        val rolledBack = pointer.read()
        assertEquals(ActiveGenerationPointer(case.origin.sequence + 2, case.origin.generationId), rolledBack)
        assertEquals(BootstrapPhase.ROLLBACK_PREPARED, journal.read(operation)?.phase)
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ROLLED_BACK, it.recover()?.status)
        }
        assertEquals(rolledBack, pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(operation)?.phase)
        assertFalse(hasFence(case))
    }

    @Test fun rejectsAnotherLeaseAndDeniesAbsentOrRevokedForegroundGrant() {
        val case = scenario()
        runtime().use {
            assertThrows(IllegalStateException::class.java) { runtime().close() }
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED, it.start(case, { false }).status)
            assertNull(journal.read(operation))
            var confirmations = 0
            val revoked = it.start(case, { ++confirmations == 1 })
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ORIGIN_RETAINED, revoked.status)
        }
        assertEquals(case.origin, pointer.read())
        runtime().use {
            assertNull(it.recover())
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.ACTIVE_TEN, it.start(case).status)
        }
    }

    @Test fun rejectsArchiveSubstitutionAndPointerAbaAfterAuthorization() {
        val case = scenario()
        val alternate = capture(case.source, case.origin)
        runtime(authorized = { throw Interruption() }).use {
            assertThrows(Interruption::class.java) { it.start(case) }
        }
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED, it.start(case, bytes = alternate).status)
        }
        assertEquals(case.origin, pointer.read())
        val other = StorageGenerationId("aba-intermediate")
        checkNotNull(pointer.compareAndSet(case.origin.generationId, other))
        val changed = checkNotNull(pointer.compareAndSet(other, case.origin.generationId))
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED, it.recover()?.status)
        }
        assertEquals(changed, pointer.read())
        assertFalse(hasFence(case))
    }

    @Test fun changedTargetBeforeCasAndHostLossFailClosed() {
        val case = scenario()
        runtime(authorized = { throw Interruption() }).use {
            assertThrows(Interruption::class.java) { it.start(case) }
        }
        var isClosed = true
        runtime(hostClosed = { isClosed }).use {
            isClosed = false
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED, it.recover()?.status)
        }
        BundledSQLiteDriver().open(case.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("UPDATE household_need SET name='Alterado' WHERE need_id='a'")
        }
        runtime().use {
            assertEquals(FaroV5Household10DevCutoverRuntime.Status.BLOCKED, it.recover()?.status)
        }
        assertEquals(case.origin, pointer.read())
        assertFalse(hasFence(case))
    }
}
