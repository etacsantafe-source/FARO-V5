package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** W01 M0C2B2B: DEV-only full FBS2v4, never ACTIVE/Android/ACL authority. */
class FaroV5Finance9FullArchiveCoordinatorTest {
    @TempDir lateinit var root: Path
    private val passphrase = "finance9-five-store-fixture-password".toCharArray()

    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block() // JVM seam, NOT a cross-process lease.
    }

    private fun active(): ActiveGenerationPointer =
        checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            null, StorageGenerationId("m0c2b2b-previous-active"),
        ))

    private fun stageEight(id: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(
                StorageGenerationId("m0c2b2b-" + id), "finance9-full-archive", 100L,
            ),
        )

    private fun stageNine(id: String, pointer: ActiveGenerationPointer): StagedStorageGeneration =
        stageEight(id).also { stage ->
            FaroV5Finance9StagedMigration(root, lease).migrate(
                MaintenanceOperationId("m0c2b2b-migrate-" + id),
                stage, pointer, "a".repeat(64), 1000L,
            )
        }

    private fun write(stage: StagedStorageGeneration, sql: String) {
        BundledSQLiteDriver().open(
            stage.directory.resolve("reality.db").toString(),
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(sql)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
    }

    private fun finance(stage: StagedStorageGeneration, active: ActiveGenerationPointer) =
        FaroV5Finance9SealedSnapshotCapture(root, lease).capture(
            MaintenanceOperationId("read-" + stage.descriptor.generationId.value),
            stage, active,
        )

    private fun coordinator() = FaroV5Finance9FullArchiveCoordinator(root, lease)

    private fun capture(
        stage: StagedStorageGeneration,
        active: ActiveGenerationPointer,
        backupId: String,
    ): ByteArray = coordinator().captureAuthenticated(
        MaintenanceOperationId("capture-" + backupId),
        BackupSetId("backup-" + backupId), stage, active,
        "5.0-dev", 3000L, passphrase,
    )

    private fun opened(archive: ByteArray): FaroV5FiveStoreSemanticSet =
        (FaroV5FiveStorePortableBackupCrypto().open(
            archive, passphrase,
        ) as FiveStorePortableBackupOpenResult.Opened).backupSet

    private data class Source(
        val stage: StagedStorageGeneration,
        val archive: ByteArray,
        val semantic: FaroV5FiveStoreSemanticSet,
    )

    private fun source(
        active: ActiveGenerationPointer,
        id: String,
        withAccount: Boolean = false,
    ): Source {
        val stage = stageEight("source-" + id)
        if (withAccount) {
            write(stage,
                "INSERT INTO finance_account VALUES ('cash-source','Caja','ARS','CASH')")
        }
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("source-migration-" + id),
            stage, active, "a".repeat(64), 1000L,
        )
        write(stage,
            "INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Comida','ARS',250000,1,1000,'HUMAN')")
        write(stage,
            "INSERT INTO finance_debt VALUES " +
                "('debt-one','Compra','Taller','ARS',50000,1,1000,1000)")
        val encrypted = capture(stage, active, id)
        return Source(stage, encrypted, opened(encrypted))
    }

    private fun restore(
        stage: StagedStorageGeneration,
        active: ActiveGenerationPointer,
        archive: ByteArray,
        operation: MaintenanceOperationId = MaintenanceOperationId("m0c2b2b-replay-operation"),
        coordinator: FaroV5Finance9FullArchiveCoordinator = coordinator(),
        password: CharArray = passphrase,
    ): FiveStoreStagedRestoreResult = coordinator.restoreAuthenticated(
        operation, stage, active, archive, password,
    )

    private fun restored(r: FiveStoreStagedRestoreResult) {
        assertTrue(r is FiveStoreStagedRestoreResult.StagedVerified, r.toString())
    }

    private fun blocked(r: FiveStoreStagedRestoreResult) {
        assertTrue(r is FiveStoreStagedRestoreResult.Blocked, r.toString())
    }

    @Test fun fullV4CaptureContainsAllFiveOwnersAndFinance9Data() {
        val active = active()
        val s = source(active, "capture", withAccount = true)
        assertEquals(s.stage.descriptor.generationId, s.semantic.fourStore.manifest.sourceGenerationId)
        assertEquals(9, s.semantic.finance9?.schemaVersion)
        assertEquals(FaroV5Finance9BackupCodec.tableNames(),
            s.semantic.finance9!!.tables.map { it.table })
        assertTrue(s.semantic.g4Domains != null)
        assertTrue(s.semantic.planningDomains != null)
        assertEquals(finance(s.stage, active), s.semantic.finance9)
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun captureExcludesFinanceAclPolicyAndGrantTables() {
        val active = active()
        val stage = stageNine("acl-source", active)
        write(stage,
            "INSERT INTO finance_acl_policy VALUES (" +
                "'" + "a".repeat(64) + "','m0c2b2b-acl-source',1," +
                "'finance.entry.record','account-1',1,0,10000)")
        val semantic = opened(capture(stage, active, "acl"))
        val names = semantic.finance9!!.tables.map { it.table }
        assertFalse(names.any { it.contains("acl") || it.contains("grant") })
        assertEquals(8, names.size)
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun fullRestoreReplaysLedgerG4PlanningWorkspaceAndFinance9WithoutCutover() {
        val active = active()
        val s = source(active, "complete", withAccount = true)
        val target = stageNine("target-complete", active)
        restored(restore(target, active, s.archive))
        assertEquals(s.semantic.finance9, finance(target, active))
        FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(target, active).use { graph ->
            assertEquals(s.semantic.fourStore.reality, graph.reality.fbs2Backup.snapshot().legacy)
            assertEquals(s.semantic.g4Domains, graph.reality.fbs2Backup.snapshot().g4Domains)
            assertEquals(s.semantic.planningDomains?.reminders,
                graph.reality.fbs2Backup.snapshot().planningDomains?.reminders)
            assertEquals(s.semantic.workspace, graph.workspace!!.backup.snapshot())
            assertEquals(s.semantic.fourStore.auxiliary, graph.auxiliary.backup.snapshot())
            assertEquals(s.semantic.fourStore.webEvidence, graph.webEvidence.backup.snapshot())
            assertEquals(s.semantic.fourStore.commercialEvidence,
                graph.commercialEvidence.backup.snapshot())
        }
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun exactArchiveReplayIsIdempotentAcrossStoreOwners() {
        val active = active()
        val s = source(active, "idempotent", withAccount = true)
        val target = stageNine("target-idempotent", active)
        restored(restore(target, active, s.archive))
        val financeFirst = finance(target, active)
        restored(restore(target, active, s.archive))
        assertEquals(financeFirst, finance(target, active))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun crashAfterRealityBaseCanReplayPinnedArchiveWithoutActivation() {
        val active = active()
        val s = source(active, "base-crash")
        val target = stageNine("target-base-crash", active)
        val interrupted = FaroV5Finance9FullArchiveCoordinator(root, lease, { section ->
            if (section == FiveStoreStagedRestoreSection.REALITY)
                error("injected crash before Evidence")
        }, {})
        blocked(restore(target, active, s.archive, coordinator = interrupted))
        assertEquals(active, FileActiveGenerationPointer(root).read())
        restored(restore(target, active, s.archive))
        assertEquals(s.semantic.finance9, finance(target, active))
    }

    @Test fun crashAfterFinanceCommitCanVerifyAndReplay() {
        val active = active()
        val s = source(active, "finance-crash")
        val target = stageNine("target-finance-crash", active)
        val interrupted = FaroV5Finance9FullArchiveCoordinator(
            root, lease, {}, { error("injected after Finance9 COMMIT") },
        )
        blocked(restore(target, active, s.archive, coordinator = interrupted))
        assertEquals(active, FileActiveGenerationPointer(root).read())
        restored(restore(target, active, s.archive))
        assertEquals(s.semantic.finance9, finance(target, active))
    }

    @Test fun archiveAndTargetSubstitutionDeniedByDurableIdentityPin() {
        val active = active()
        val s = source(active, "pin")
        val target = stageNine("target-pin", active)
        restored(restore(target, active, s.archive))
        val substituted = capture(s.stage, active, "pin-new-envelope")
        assertFalse(s.archive.contentEquals(substituted))
        blocked(restore(target, active, substituted))
        val foreign = stageNine("foreign-target", active)
        blocked(restore(foreign, active, s.archive))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun tamperWrongPassphraseAndV3ArchiveDeniedBeforeAnyRestore() {
        val active = active()
        val s = source(active, "authentication")
        val target = stageNine("target-authentication", active)
        blocked(restore(target, active, s.archive,
            password = "wrong-password-minimum-length".toCharArray()))
        val tampered = s.archive.copyOf()
        tampered[tampered.lastIndex] =
            (tampered.last().toInt() xor 1).toByte()
        blocked(restore(target, active, tampered))
        val stage8 = stageEight("old-version")
        val old = FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(stage8).use { graph ->
            FaroV5FiveStoreBackupCapture(graph, lease).capture(
                MaintenanceOperationId("old-capture"), BackupSetId("old-backup"),
                "5.0-dev", 3000L,
            )
        }
        val v3 = FaroV5FiveStorePortableBackupCrypto().seal(old, passphrase)
        blocked(restore(target, active, v3))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun foreignFinance9DataCannotBeOverwritten() {
        val active = active()
        val s = source(active, "foreign")
        val target = stageNine("target-foreign", active)
        write(target,
            "INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Vivienda','ARS',800000,1,1000,'HUMAN')")
        val before = finance(target, active)
        blocked(restore(target, active, s.archive))
        assertEquals(before, finance(target, active))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun activePointerAbaWrongDescriptorAndSidecarsAreRejected() {
        val active = active()
        val s = source(active, "identity")
        val target = stageNine("target-identity", active)
        blocked(restore(target.copy(directory = root.resolve("foreign-path")),
            active, s.archive))
        Files.writeString(target.directory.resolve("reality.db-wal"), "foreign")
        blocked(restore(target, active, s.archive))
        Files.delete(target.directory.resolve("reality.db-wal"))
        val changed = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            active.generationId, StorageGenerationId("changed-active-pointer"),
        ))
        assertNotEquals(active, changed)
        blocked(restore(target, active, s.archive))
        assertEquals(changed, FileActiveGenerationPointer(root).read())
    }
}
