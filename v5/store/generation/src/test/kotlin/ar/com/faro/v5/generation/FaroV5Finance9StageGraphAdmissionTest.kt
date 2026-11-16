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
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M0C2B2A: finance9 five-store STAGED graph, no active/productive host. */
class FaroV5Finance9StageGraphAdmissionTest {
    @TempDir lateinit var root: Path
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    private fun pointer(): ActiveGenerationPointer =
        checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            null, StorageGenerationId("finance9-graph-historical-active"),
        ))

    private fun descriptor(id: String): StorageGenerationDescriptor =
        StorageGenerationDescriptor(
            StorageGenerationId(id), "finance9-stage-graph-test", 1000L,
        )

    private fun stageEight(id: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(descriptor(id))

    private fun stageNine(
        id: String,
        active: ActiveGenerationPointer,
    ): StagedStorageGeneration =
        stageEight(id).also { stage ->
            FaroV5Finance9StagedMigration(root, lease).migrate(
                MaintenanceOperationId("finance9-graph-" + id),
                stage, active, "a".repeat(64), 2000L,
            )
        }

    private fun version(stage: StagedStorageGeneration): Long =
        BundledSQLiteDriver().open(
            stage.directory.resolve("reality.db").toString(),
        ).use { db ->
            db.prepare("PRAGMA user_version").use {
                check(it.step())
                it.getLong(0)
            }
        }

    @Test fun sealedNineGraphReadsFiveOwnersAndExistingG4PlanningSnapshots() {
        val active = pointer()
        val staged = stageNine("target-graph-valid", active)
        FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(staged, active).use { graph ->
            assertEquals(staged.descriptor.generationId.value, graph.generationId)
            assertTrue(graph.verifyHealthy())
            assertNotNull(graph.workspace)
            assertEquals(9, graph.reality.maintenance.inspect().schemaVersion)
            val paired = graph.reality.fbs2Backup.snapshot()
            assertNotNull(paired.g4Domains)
            assertNotNull(paired.planningDomains)
            assertEquals(8, paired.planningDomains?.schemaVersion)
        }
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun historicalDefaultSevenAndPlanningEightOpenersRefuseFinanceNine() {
        val active = pointer()
        val staged = stageNine("target-old-openers", active)
        val db = staged.directory.resolve("reality.db").toString()
        assertThrows(IllegalStateException::class.java) {
            RealityStore(db).use { }
        }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(db, planningStage8 = true).use { }
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged).use { }
        }
        assertEquals(9L, version(staged))
    }

    @Test fun stagedEightNeverMigratesSilentlyFromFinanceNineGraphOpener() {
        val active = pointer()
        val staged = stageEight("target-eight-only")
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(staged, active).use { }
        }
        assertEquals(8L, version(staged))
        val graph = FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged)
        graph.use { assertEquals(8, it.reality.maintenance.inspect().schemaVersion) }
    }

    @Test fun forgedGenerationDescriptorAndDirectoryAreRejectedBeforeOpening() {
        val active = pointer()
        val staged = stageNine("target-forged", active)
        val forged = staged.copy(descriptor = descriptor("foreign-generation"))
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(forged, active).use { }
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(
                staged.copy(directory = root.resolve("foreign-folder")), active,
            ).use { }
        }
        assertEquals(9L, version(staged))
    }

    @Test fun activeTargetAndStalePointerSequenceCannotOpenFinanceNine() {
        val active = pointer()
        val staged = stageNine("target-active", active)
        val switched = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            active.generationId, staged.descriptor.generationId,
        ))
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(staged, switched).use { }
        }
        val changed = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            switched.generationId, StorageGenerationId("finance9-other-active"),
        ))
        assertFalse(active == changed)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(staged, active).use { }
        }
        assertEquals(9L, version(staged))
    }

    @Test fun uncheckpointedSidecarAndForeignFinanceColumnFailClosed() {
        val active = pointer()
        val stageWithWal = stageNine("target-sidecar", active)
        val sidecar = Files.writeString(
            stageWithWal.directory.resolve("reality.db-wal"), "untrusted",
        )
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(
                stageWithWal, active,
            ).use { }
        }
        assertTrue(Files.exists(sidecar))
        val extra = stageNine("target-extra-column", active)
        BundledSQLiteDriver().open(extra.directory.resolve("reality.db").toString()).use { db ->
            db.execSQL(
                "ALTER TABLE finance_budget_limit ADD COLUMN alien_column TEXT",
            )
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage(extra, active).use { }
        }
        assertEquals(9L, version(extra))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }
}
