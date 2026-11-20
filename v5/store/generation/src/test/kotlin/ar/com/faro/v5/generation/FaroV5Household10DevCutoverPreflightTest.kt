package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10DevCutoverPreflightTest {
    @TempDir lateinit var root: Path

    private val operation = MaintenanceOperationId("household-preflight")
    private val pointer get() = FileActiveGenerationPointer(root)
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
    }

    private fun owner(probe: () -> Unit = {}) = FaroV5Household10DevCutoverPreflight(root, lease, probe)

    private fun origin() = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("finance-nine-origin")))

    private fun stage(origin: ActiveGenerationPointer, ten: Boolean = true): StagedStorageGeneration {
        val prepared = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("household-target"), "household-preflight", 100L),
        )
        val stage = FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("finance-prepare"), prepared, origin, "a".repeat(64), 1_000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        if (ten) {
            val file = stage.directory.resolve(GenerationStoreFiles.REALITY)
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(file)
            BundledSQLiteDriver().open(file.toString()).use { db ->
                db.execSQL("UPDATE household_need_state SET revision=5")
                db.execSQL("INSERT INTO household_need VALUES ('yerba','Yerba',500,'GRAM','NORMAL','OPEN',4,NULL)")
            }
        }
        return stage
    }

    private fun journal(origin: ActiveGenerationPointer, target: StagedStorageGeneration, id: MaintenanceOperationId = operation) {
        val record = BootstrapJournalEntry(
            id, MaintenanceOperationKind.RESTORE, origin.generationId, target.descriptor.generationId, BootstrapPhase.PREPARED,
        )
        val journal = FileBootstrapJournal(root)
        journal.create(record)
        assertTrue(journal.compareAndSet(record, BootstrapTransitionPolicy.transition(record, BootstrapPhase.VALIDATED)))
    }

    @Test fun admitsSealedStageWithoutChangingFilesOrPointer() {
        val origin = origin()
        val stage = stage(origin)
        journal(origin, stage)
        val files = GenerationStoreFiles.WORKSPACE_V2.map { stage.directory.resolve(it) }
        val before = files.map { Files.readAllBytes(it) }
        assertTrue(owner().inspect(operation, stage, origin))
        files.zip(before).forEach { (file, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(file)) }
        assertEquals(origin, pointer.read())
    }

    @Test fun refusesNineAndIncompatibleCompanions() {
        val origin = origin()
        val stage = stage(origin, ten = false)
        journal(origin, stage)
        assertFalse(owner().inspect(operation, stage, origin))
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(stage.directory.resolve(GenerationStoreFiles.REALITY))
        BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { db ->
            db.execSQL("PRAGMA user_version=3")
        }
        assertFalse(owner().inspect(operation, stage, origin))
    }

    @Test fun refusesPointerAbaAndJournalForDifferentOperation() {
        val origin = origin()
        val stage = stage(origin)
        journal(origin, stage, MaintenanceOperationId("unrelated"))
        assertFalse(owner().inspect(operation, stage, origin))
        val other = StorageGenerationId("other")
        checkNotNull(pointer.compareAndSet(origin.generationId, other))
        checkNotNull(pointer.compareAndSet(other, origin.generationId))
        assertFalse(owner().inspect(MaintenanceOperationId("unrelated"), stage, origin))
    }

    @Test fun refusesLinksAndDanglingSidecar() {
        val origin = origin()
        val stage = stage(origin)
        journal(origin, stage)
        val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
        val alias = Files.createLink(root.resolve("linked.db"), reality)
        assertFalse(owner().inspect(operation, stage, origin))
        Files.delete(alias)
        assertFalse(owner().inspect(operation, stage.copy(directory = root), origin))
        val sidecar = reality.resolveSibling(reality.fileName.toString() + "-wal")
        Files.createSymbolicLink(sidecar, root.resolve("missing"))
        assertFalse(owner().inspect(operation, stage, origin))
        Files.delete(sidecar)
    }

    @Test fun refusesReplayFenceAndClosedLease() {
        val origin = origin()
        val stage = stage(origin)
        journal(origin, stage)
        assertFalse(owner { error("lease closed") }.inspect(operation, stage, origin))
        FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, stage.descriptor.generationId)
        assertFalse(owner().inspect(operation, stage, origin))
    }

    @Test fun opensOnlyTheSelectedDevGenerationWithAnExactPointer() {
        val origin = origin()
        val stage = stage(origin)
        journal(origin, stage)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(stage, origin).close()
        }
        val selected = checkNotNull(pointer.compareAndSet(origin.generationId, stage.descriptor.generationId))
        assertTrue(owner().verifyAlreadyActive(operation, stage, selected))
        assertFalse(owner().verifyAlreadyActive(operation, stage, selected.copy(sequence = selected.sequence + 1)))
        assertFalse(owner().inspect(operation, stage, selected))
    }
}
