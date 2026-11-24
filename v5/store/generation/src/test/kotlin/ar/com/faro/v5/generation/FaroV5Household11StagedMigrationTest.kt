package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11StagedMigrationTest {
    @TempDir lateinit var root: Path

    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())

    @Test
    fun upgradesOnlyFiveStoreCopyWithEmptyReceiptsAndUnchangedPointer() {
        val (source, target, origin) = stages()
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(source.directory.resolve(it))
        }
        val result = migrate(target, origin)

        assertEquals(target, result)
        assertEquals(origin, pointer.read())
        assertEquals(10L, version(source))
        assertEquals(11L, version(target))
        assertEquals(4L, version(target, GenerationStoreFiles.AUXILIARY))
        assertEquals(0L, scalar(target, "SELECT count(*) FROM household_stock_receipt"))
        assertEquals(0L, scalar(target, "SELECT revision FROM household_stock_receipt_state"))
        assertEquals(0L, scalar(target, "SELECT count(*) FROM household_need"))
        assertTrue(FileBootstrapJournal(root).pending().isEmpty())
        for ((file, bytes) in before) {
            assertArrayEquals(bytes, Files.readAllBytes(source.directory.resolve(file)))
            if (file != GenerationStoreFiles.REALITY) {
                assertArrayEquals(bytes, Files.readAllBytes(target.directory.resolve(file)))
            }
        }
    }

    @Test
    fun rejectsActiveAndForgedTargetDirectory() {
        val (source, target, origin) = stages()
        assertThrows(IllegalStateException::class.java) { migrate(source, origin) }
        assertThrows(IllegalStateException::class.java) {
            migrate(target.copy(directory = root.resolve("forged")), origin)
        }
        assertEquals(10L, version(source))
        assertEquals(10L, version(target))
    }

    @Test
    fun rejectsDifferentCopyAndHardLinkToSource() {
        val (source, target, origin) = stages()
        val targetReality = target.directory.resolve(GenerationStoreFiles.REALITY)
        val sourceReality = source.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(targetReality.toString()).use {
            it.execSQL("INSERT INTO finance_account VALUES ('different','Efectivo','ARS','CASH')")
        }
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(10L, version(target))

        Files.delete(targetReality)
        Files.createLink(targetReality, sourceReality)
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(10L, version(source))
    }

    @Test
    fun rejectsUnknownRealitySchemaAndAuxiliaryThree() {
        val (_, oldTarget, oldOrigin) = stages(id = "aux3", auxiliary4 = false)
        assertThrows(IllegalStateException::class.java) { migrate(oldTarget, oldOrigin) }
        assertEquals(10L, version(oldTarget))

        val (source, target, origin) = stages(id = "version99")
        for (stage in listOf(source, target)) {
            BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
                it.execSQL("PRAGMA user_version = 99")
            }
        }
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(99L, version(target))
    }

    @Test
    fun rejectsSealedSidecarAndRealitySymlink() {
        val (source, target, origin) = stages()
        val sidecar = target.directory.resolve(GenerationStoreFiles.REALITY + "-wal")
        Files.createSymbolicLink(sidecar, target.directory.resolve("missing"))
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        Files.delete(sidecar)

        val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
        Files.delete(reality)
        Files.createSymbolicLink(reality, source.directory.resolve(GenerationStoreFiles.REALITY))
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(10L, version(source))
    }

    @Test
    fun pointerAbaAfterSqlQuarantinesElevenWithoutActivation() {
        val (source, target, origin) = stages()
        assertThrows(IllegalStateException::class.java) {
            migrate(target, origin) {
                val other = StorageGenerationId("temporary-household11-active")
                checkNotNull(pointer.compareAndSet(origin.generationId, other))
                checkNotNull(pointer.compareAndSet(other, origin.generationId))
            }
        }
        val advanced = checkNotNull(pointer.read())
        assertEquals(origin.generationId, advanced.generationId)
        assertNotEquals(origin.sequence, advanced.sequence)
        assertEquals(10L, version(source))
        assertEquals(11L, version(target))
        assertThrows(IllegalStateException::class.java) { migrate(target, advanced) }
    }

    @Test
    fun revokedExclusiveClosedHostLeaseStopsBeforeSchemaChange() {
        val (_, target, origin) = stages()
        var checks = 0
        val migration = FaroV5Household11StagedMigration(root, gate, {
            check(++checks == 1) { "exclusive host lease revoked" }
        })
        assertThrows(IllegalStateException::class.java) {
            migration.migrate(MaintenanceOperationId("household11-revoked"), target, origin)
        }
        assertEquals(10L, version(target))
        assertEquals(origin, pointer.read())
    }

    @Test
    fun injectedCrashAfterSqlLeavesOnlyQuarantinedStageEleven() {
        val (source, target, origin) = stages()
        assertThrows(IllegalStateException::class.java) {
            migrate(target, origin) { error("injected crash after SQL before admission") }
        }
        assertEquals(10L, version(source))
        assertEquals(11L, version(target))
        assertEquals(origin, pointer.read())
        assertTrue(FileBootstrapJournal(root).pending().isEmpty())
    }

    private data class StagePair(
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val origin: ActiveGenerationPointer,
    )

    private fun stages(id: String = "household11", auxiliary4: Boolean = true): StagePair {
        val old = pointer.read() ?: checkNotNull(
            pointer.compareAndSet(null, StorageGenerationId("historical-active")),
        )
        val source = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(descriptor("$id-source"))
        FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("prepare-$id"), source, old, "a".repeat(64), 1L,
        )
        if (auxiliary4) {
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).close()
        }
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY),
        )
        val origin = checkNotNull(pointer.compareAndSet(old.generationId, source.descriptor.generationId))
        val target = FileStorageGenerationLayout(root).stageWorkspaceV2(descriptor("$id-target"))
        for (file in GenerationStoreFiles.WORKSPACE_V2) {
            Files.copy(source.directory.resolve(file), target.directory.resolve(file))
        }
        return StagePair(source, target, origin)
    }

    private fun descriptor(id: String) =
        StorageGenerationDescriptor(StorageGenerationId(id), "household11-stage-test", 1L)

    private fun migrate(
        stage: StagedStorageGeneration,
        active: ActiveGenerationPointer,
        after: () -> Unit = {},
    ) = FaroV5Household11StagedMigration(root, gate, {}, after).migrate(
        MaintenanceOperationId("migrate-household11"), stage, active,
    )

    private fun version(stage: StagedStorageGeneration, file: String = GenerationStoreFiles.REALITY): Long =
        BundledSQLiteDriver().open(stage.directory.resolve(file).toString()).use { db ->
            db.prepare("PRAGMA user_version").use { stmt ->
                check(stmt.step())
                stmt.getLong(0)
            }
        }

    private fun scalar(stage: StagedStorageGeneration, sql: String): Long =
        BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
            db.prepare(sql).use { stmt ->
                check(stmt.step())
                stmt.getLong(0)
            }
        }
}
