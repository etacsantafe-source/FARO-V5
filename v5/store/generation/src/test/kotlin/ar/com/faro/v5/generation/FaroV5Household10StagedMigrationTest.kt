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
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10StagedMigrationTest {
    @TempDir lateinit var root: Path

    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())

    @Test
    fun migratesCanonicalFiveStoreCopyAndPreservesSourceAndOtherStores() {
        val (source, target, origin) = stages()
        val previous = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(source.directory.resolve(it))
        }

        val result = migrate(target, origin)

        assertEquals(target, result)
        assertEquals(origin, pointer.read())
        assertEquals(9L, version(source))
        assertEquals(10L, version(target))
        assertEquals(4L, version(target, GenerationStoreFiles.AUXILIARY))
        assertTrue(FileBootstrapJournal(root).pending().isEmpty())
        for ((file, bytes) in previous) {
            assertArrayEquals(bytes, Files.readAllBytes(source.directory.resolve(file)))
            if (file != GenerationStoreFiles.REALITY) {
                assertArrayEquals(bytes, Files.readAllBytes(target.directory.resolve(file)))
            }
        }
    }

    @Test
    fun rejectsActiveAndForgedTargets() {
        val (source, target, origin) = stages()
        assertThrows(IllegalStateException::class.java) { migrate(source, origin) }
        assertThrows(IllegalStateException::class.java) {
            migrate(target.copy(directory = root.resolve("forged")), origin)
        }
        assertEquals(9L, version(source))
        assertEquals(9L, version(target))
    }

    @Test
    fun rejectsAValidButDifferentCopyAndHardLinksToTheSource() {
        val (source, target, origin) = stages()
        val targetReality = target.directory.resolve(GenerationStoreFiles.REALITY)
        val sourceReality = source.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(targetReality.toString()).use {
            it.execSQL("INSERT INTO finance_account VALUES ('divergent','Efectivo','ARS','CASH')")
        }
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(9L, version(target))

        Files.delete(targetReality)
        Files.createLink(targetReality, sourceReality)
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(9L, version(source))
    }

    @Test
    fun rejectsAuxiliaryThreeAndUnknownRealityFormats() {
        val (_, olderTarget, olderOrigin) = stages(id = "aux-three", auxiliary4 = false)
        assertThrows(IllegalStateException::class.java) { migrate(olderTarget, olderOrigin) }
        assertEquals(9L, version(olderTarget))

        val (source, target, origin) = stages(id = "unknown-reality")
        for (stage in listOf(source, target)) {
            BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
                it.execSQL("PRAGMA user_version = 99")
            }
        }
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(99L, version(target))
    }

    @Test
    fun rejectsBrokenSidecarLinksAndStoreSymlinks() {
        val (source, target, origin) = stages()
        val sidecar = target.directory.resolve("reality.db-wal")
        Files.createSymbolicLink(sidecar, target.directory.resolve("unavailable"))
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        Files.delete(sidecar)

        val file = target.directory.resolve(GenerationStoreFiles.REALITY)
        Files.delete(file)
        Files.createSymbolicLink(file, source.directory.resolve(GenerationStoreFiles.REALITY))
        assertThrows(IllegalStateException::class.java) { migrate(target, origin) }
        assertEquals(9L, version(source))
    }

    @Test
    fun abortsOnPointerAbaAfterSqlAndLeavesTargetQuarantined() {
        val (source, target, origin) = stages()
        assertThrows(IllegalStateException::class.java) {
            migrate(target, origin) {
                val other = StorageGenerationId("temporary-active")
                checkNotNull(pointer.compareAndSet(origin.generationId, other))
                checkNotNull(pointer.compareAndSet(other, origin.generationId))
            }
        }
        val advanced = checkNotNull(pointer.read())
        assertEquals(origin.generationId, advanced.generationId)
        assertNotEquals(origin.sequence, advanced.sequence)
        assertEquals(9L, version(source))
        assertEquals(10L, version(target))
        assertTrue(FileBootstrapJournal(root).pending().isEmpty())
        assertThrows(IllegalStateException::class.java) { migrate(target, advanced) }
    }

    @Test
    fun revokedClosedHostLeaseStopsMigrationBeforeSql() {
        val (_, target, origin) = stages()
        var checks = 0
        val migration = FaroV5Household10StagedMigration(root, gate, {
            check(++checks == 1) { "closed-host lease revoked" }
        })
        assertThrows(IllegalStateException::class.java) {
            migration.migrate(MaintenanceOperationId("household-lease"), target, origin)
        }
        assertEquals(9L, version(target))
        assertEquals(origin, pointer.read())
    }

    private data class StagePair(
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val origin: ActiveGenerationPointer,
    )

    private fun stages(id: String = "household", auxiliary4: Boolean = true): StagePair {
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
        val origin = checkNotNull(pointer.compareAndSet(old.generationId, source.descriptor.generationId))
        val target = FileStorageGenerationLayout(root).stageWorkspaceV2(descriptor("$id-target"))
        for (file in GenerationStoreFiles.WORKSPACE_V2) {
            Files.copy(source.directory.resolve(file), target.directory.resolve(file))
        }
        return StagePair(source, target, origin)
    }

    private fun descriptor(id: String) =
        StorageGenerationDescriptor(StorageGenerationId(id), "household10-stage-test", 1L)

    private fun migrate(
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        after: () -> Unit = {},
    ) = FaroV5Household10StagedMigration(root, gate, {}, after).migrate(
        MaintenanceOperationId("migrate-household"), target, origin,
    )

    private fun version(stage: StagedStorageGeneration, file: String = GenerationStoreFiles.REALITY): Long =
        BundledSQLiteDriver().open(stage.directory.resolve(file).toString()).use { db ->
            db.prepare("PRAGMA user_version").use {
                check(it.step())
                it.getLong(0)
            }
        }
}
