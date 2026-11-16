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
import ar.com.faro.v5.store.reality.RealityFinance9SectionRestoreDisposition
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M0C2B1: authenticated FINANCE9 section only, never a five-store restore. */
class FaroV5Finance9AuthenticatedSectionRestoreTest {
    @TempDir lateinit var root: Path
    private val password = "finance9-isolated-stage-section-test".toCharArray()

    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block() // Test seam, NOT interprocess exclusion.
    }

    private fun pointer(): ActiveGenerationPointer =
        checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            null, StorageGenerationId("m0c2b1-active-historic"),
        ))

    private fun descriptor(id: String) = StorageGenerationDescriptor(
        StorageGenerationId("m0c2b1-" + id), "m0c2b1-isolated-restore-fixture", 100L,
    )

    private fun stagedEight(name: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root)
            .stageFreshPlanning8AndVerify(descriptor(name))

    private fun stagedNine(name: String, active: ActiveGenerationPointer): StagedStorageGeneration {
        val stage = stagedEight(name)
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("m0c2b1-upgrade-" + name),
            stage, active, "a".repeat(64), 1000L,
        )
        return stage
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

    private fun read(stage: StagedStorageGeneration, active: ActiveGenerationPointer) =
        FaroV5Finance9SealedSnapshotCapture(root, lease).capture(
            MaintenanceOperationId("m0c2b1-capture-" + stage.descriptor.generationId.value),
            stage, active,
        )

    private data class Archive(val encrypted: ByteArray, val finance: Finance9SectionSnapshot)

    /**
     * Source has a REAL Planning8 four-store envelope and a derived REAL
     * stage9 Finance snapshot. The 4-store content is not restored by M0C2B1.
     */
    private fun source(
        active: ActiveGenerationPointer,
        id: String,
        addBudget: Boolean = false,
        addAccount: Boolean = false,
    ): Archive {
        val stage = stagedEight("source-" + id)
        if (addAccount) write(stage,
            "INSERT INTO finance_account VALUES ('source-cash','Caja','ARS','CASH')")
        val legacy = FaroV5GenerationStoreGraph
            .openValidatedV2PlanningStage8(stage).use { graph ->
                FaroV5FiveStoreBackupCapture(graph, lease).capture(
                    MaintenanceOperationId("m0c2b1-source-capture"),
                    BackupSetId("m0c2b1-backup-" + id),
                    "5.0-dev", 3000L,
                )
            }
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("m0c2b1-source-upgrade-" + id),
            stage, active, "b".repeat(64), 2000L,
        )
        if (addBudget) {
            write(stage,
                "INSERT INTO finance_budget_limit VALUES (" +
                    "'2026-11','Alimentos','ARS',200000,1,2000,'HUMAN')")
            write(stage,
                "INSERT INTO finance_debt VALUES (" +
                    "'debt-one','Reparación','Taller','ARS',50000,1,2000,2000)")
        }
        val finance = read(stage, active)
        return Archive(
            FaroV5FiveStorePortableBackupCrypto().seal(
                legacy.copy(finance9 = finance), password,
            ),
            finance,
        )
    }

    private fun restore(
        target: StagedStorageGeneration,
        active: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray = password,
        owner: FaroV5Finance9AuthenticatedSectionRestore =
            FaroV5Finance9AuthenticatedSectionRestore(root, lease),
    ): RealityFinance9SectionRestoreDisposition =
        owner.restoreFinanceSectionOnly(
            MaintenanceOperationId("m0c2b1-restore-" + target.descriptor.generationId.value),
            target, active, archive, passphrase,
        )

    @Test fun authenticatedV4RestoresRealBudgetAndDebtOnSealedStageNine() {
        val active = pointer()
        val source = source(active, "budget", addBudget = true)
        val target = stagedNine("target-budget", active)
        assertEquals(
            RealityFinance9SectionRestoreDisposition.RESTORED,
            restore(target, active, source.encrypted),
        )
        assertEquals(source.finance, read(target, active))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun exactArchiveReplayIsVerifiedWithoutDuplicateOrReapply() {
        val active = pointer()
        val source = source(active, "replay", addBudget = true)
        val target = stagedNine("target-replay", active)
        assertEquals(RealityFinance9SectionRestoreDisposition.RESTORED,
            restore(target, active, source.encrypted))
        assertEquals(RealityFinance9SectionRestoreDisposition.ALREADY_IDENTICAL,
            restore(target, active, source.encrypted))
        assertEquals(source.finance, read(target, active))
    }

    @Test fun incorrectPasswordOrTamperedArchiveNeverWritesRows() {
        val active = pointer()
        val source = source(active, "bad-auth", addBudget = true)
        val target = stagedNine("target-bad-auth", active)
        val before = read(target, active)
        assertThrows(IllegalStateException::class.java) {
            restore(target, active, source.encrypted, "totally-wrong-passphrase".toCharArray())
        }
        val corrupted = source.encrypted.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertThrows(IllegalStateException::class.java) {
            restore(target, active, corrupted)
        }
        assertEquals(before, read(target, active))
    }

    @Test fun v3ArchiveOrMissingFinance9SectionIsRejected() {
        val active = pointer()
        val source = source(active, "v3")
        val set = (FaroV5FiveStorePortableBackupCrypto()
            .open(source.encrypted, password) as FiveStorePortableBackupOpenResult.Opened)
            .backupSet
        val oldArchive = FaroV5FiveStorePortableBackupCrypto()
            .seal(set.copy(finance9 = null), password)
        val target = stagedNine("target-v3", active)
        val before = read(target, active)
        assertThrows(IllegalStateException::class.java) {
            restore(target, active, oldArchive)
        }
        assertEquals(before, read(target, active))
    }

    @Test fun foreignNonfreshMoneyOrPersistedAclRejectsOverwrite() {
        val active = pointer()
        val source = source(active, "nonfresh", addBudget = true)
        val target = stagedNine("target-nonfresh", active)
        write(target,
            "INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Otros','ARS',12000,1,2000,'HUMAN')")
        val before = read(target, active)
        assertThrows(IllegalStateException::class.java) {
            restore(target, active, source.encrypted)
        }
        assertEquals(before, read(target, active))

        val acl = stagedNine("target-acl", active)
        write(acl, "INSERT INTO finance_acl_policy VALUES (" +
            "'" + "f".repeat(64) + "','stage-acl',1," +
            "'finance.entry.record','account-one',1,0,20000)")
        val aclBefore = read(acl, active)
        assertThrows(IllegalStateException::class.java) {
            restore(acl, active, source.encrypted)
        }
        assertEquals(aclBefore, read(acl, active))
    }

    @Test fun foreignAccountDependencyRollsBackAllFinance9Inserts() {
        val active = pointer()
        val source = source(active, "fk-account", addAccount = true)
        val target = stagedNine("target-fk", active)
        val before = read(target, active)
        assertTrue(source.finance.tables.single {
            it.table == "finance_opening_confirmation"
        }.rows.isNotEmpty())
        assertThrows(Exception::class.java) {
            restore(target, active, source.encrypted)
        }
        assertEquals(before, read(target, active))
    }

    @Test fun activeTargetAbaOrSidecarCannotReturnVerifiedStage() {
        val active = pointer()
        val source = source(active, "fences", addBudget = true)
        val target = stagedNine("target-active", active)
        val switched = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            active.generationId, target.descriptor.generationId,
        ))
        assertThrows(IllegalStateException::class.java) {
            restore(target, switched, source.encrypted)
        }
        // A separate root/pointer sequence is not needed to prove the
        // expectedActive ABA: switch from target to another generation.
        val targetAba = stagedEight("target-aba")
        // The stage is not converted under an active-target pointer:
        // first switch to a distinct stable active generation.
        val newActive = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            switched.generationId, StorageGenerationId("m0c2b1-active-2"),
        ))
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("m0c2b1-aba-upgrade"),
            targetAba, newActive, "d".repeat(64), 3000L,
        )
        val injected = FaroV5Finance9AuthenticatedSectionRestore(root, lease) {
            checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
                newActive.generationId, StorageGenerationId("m0c2b1-active-3"),
            ))
        }
        assertThrows(IllegalStateException::class.java) {
            restore(targetAba, newActive, source.encrypted, owner = injected)
        }
        assertFalse(FileActiveGenerationPointer(root).read() == newActive)

        val stable = checkNotNull(FileActiveGenerationPointer(root).read())
        val sidecar = stagedNine("target-sidecar", stable)
        val side = Files.writeString(
            sidecar.directory.resolve("reality.db-wal"), "untrusted",
        )
        assertThrows(IllegalStateException::class.java) {
            restore(sidecar, stable, source.encrypted)
        }
        assertTrue(Files.exists(side))
    }
}
