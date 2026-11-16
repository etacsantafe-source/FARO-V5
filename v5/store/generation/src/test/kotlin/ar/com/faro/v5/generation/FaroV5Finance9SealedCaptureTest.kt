package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M0C2A: read data from a sealed Reality9 stage; NOT a five-store backup. */
class FaroV5Finance9SealedCaptureTest {
    @TempDir lateinit var root: Path

    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    private fun pointer(): ActiveGenerationPointer =
        checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            null, StorageGenerationId("m0c2a-historical-active"),
        ))

    private fun descriptor(id: String) = StorageGenerationDescriptor(
        StorageGenerationId(id), "m0c2a-sealed-fixture", 100L,
    )

    private fun stage(id: String, active: ActiveGenerationPointer): StagedStorageGeneration {
        val staged = FaroV5WorkspaceV2StageOwner(root)
            .stageFreshPlanning8AndVerify(descriptor(id))
        FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("m0c2a-upgrade-" + id),
            staged, active, "a".repeat(64), 1000L,
        )
        return staged
    }

    private fun capture(
        staged: StagedStorageGeneration,
        active: ActiveGenerationPointer,
        owner: FaroV5Finance9SealedSnapshotCapture =
            FaroV5Finance9SealedSnapshotCapture(root, lease),
    ): Finance9SectionSnapshot =
        owner.capture(
            MaintenanceOperationId("m0c2a-read-" + staged.descriptor.generationId.value),
            staged, active,
        )

    private fun write(staged: StagedStorageGeneration, sql: String) {
        BundledSQLiteDriver().open(
            staged.directory.resolve("reality.db").toString(),
        ).use { db ->
            db.execSQL(sql)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
    }

    @Test fun canonicalSealedNineReadsEightTablesWithoutPointerMutation() {
        val active = pointer()
        val target = stage("valid", active)
        val result = capture(target, active)
        assertEquals(9, result.schemaVersion)
        assertEquals(FaroV5Finance9BackupCodec.tableNames(), result.tables.map { it.table })
        assertEquals(1, result.tables.single {
            it.table == "finance_schema_migration_audit"
        }.rows.size)
        assertEquals(active, FileActiveGenerationPointer(root).read())
        val canonical = FaroV5Finance9BackupCodec.encode(result)
        assertArrayEquals(canonical, FaroV5Finance9BackupCodec.encode(
            FaroV5Finance9BackupCodec.decode(canonical),
        ))
    }

    @Test fun realBudgetDebtAndGrantRowsAreCapturedOnlyFromWhitelistedDataTables() {
        val active = pointer()
        val target = stage("populated", active)
        write(target,
            "INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Comida','ARS',250000,1,1000,'HUMAN')")
        write(target,
            "INSERT INTO finance_debt VALUES " +
                "('debt-1','Compra','Banco','ARS',50000,1,1000,1000)")
        write(target,
            "INSERT INTO finance_acl_policy VALUES (" +
                "'" + "a".repeat(64) + "','m0c2a-populated',1," +
                "'finance.entry.record','account-1',1,0,10000)")
        val snapshot = capture(target, active)
        assertEquals(
            listOf("2026-11","Comida","ARS","250000","1","1000","HUMAN"),
            snapshot.tables.single { it.table=="finance_budget_limit" }.rows.single(),
        )
        assertEquals("debt-1",
            snapshot.tables.single { it.table=="finance_debt" }.rows.single()[0])
        assertTrue(snapshot.tables.none {
            it.table.contains("acl") || it.table.contains("grant")
        })
        assertFalse(
            FaroV5Finance9BackupCodec.encode(snapshot).toString(Charsets.UTF_8)
                .contains("account-1"),
        )
    }

    @Test fun unknownLegacyOpeningIsNotConvertedIntoConfirmedZero() {
        val active = pointer()
        val target = stage("opening", active)
        write(target, "INSERT INTO finance_account VALUES ('acct-a','Caja','ARS','CASH')")
        write(target,
            "INSERT INTO finance_opening_confirmation VALUES " +
                "('acct-a',NULL,'LEGACY_UNVERIFIED',NULL,'MIGRATED_FROM_8')")
        val row = capture(target, active).tables.single {
            it.table == "finance_opening_confirmation"
        }.rows.single()
        assertEquals("acct-a",row[0])
        assertEquals(null,row[1])
        assertEquals("LEGACY_UNVERIFIED",row[2])
        assertEquals(null,row[3])
    }

    @Test fun activeTargetIsDeniedBeforeAnySealedSqliteRead() {
        val active = pointer()
        val target = stage("becomes-active", active)
        val switched = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            active.generationId, target.descriptor.generationId,
        ))
        assertThrows(IllegalStateException::class.java) {
            capture(target, switched)
        }
    }

    @Test fun forgedDescriptorAndUncheckpointedSidecarAreDenied() {
        val active = pointer()
        val target = stage("foreign", active)
        assertThrows(IllegalStateException::class.java) {
            capture(target.copy(directory = root.resolve("wrong")), active)
        }
        val sidecar = Files.writeString(
            target.directory.resolve("reality.db-wal"), "untrusted",
        )
        assertThrows(IllegalStateException::class.java) { capture(target, active) }
        assertTrue(Files.exists(sidecar))
    }

    @Test fun pointerAbaAfterSnapshotReadFailsBeforeReturningData() {
        val active = pointer()
        val target = stage("aba", active)
        val injected = FaroV5Finance9SealedSnapshotCapture(root, lease) {
            checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
                active.generationId, StorageGenerationId("m0c2a-foreign-active"),
            ))
        }
        assertThrows(IllegalStateException::class.java) {
            capture(target, active, injected)
        }
        assertFalse(FileActiveGenerationPointer(root).read() == active)
    }

    @Test fun UnknownVersionAndAddedColumnCannotBecomePartialBackup() {
        val active = pointer()
        val unknown = stage("unknown", active)
        write(unknown, "PRAGMA user_version=55")
        assertThrows(IllegalStateException::class.java) { capture(unknown, active) }
        val added = stage("added-col", active)
        write(added, "ALTER TABLE finance_budget_limit ADD COLUMN extras TEXT")
        assertThrows(IllegalStateException::class.java) { capture(added, active) }
    }
}
