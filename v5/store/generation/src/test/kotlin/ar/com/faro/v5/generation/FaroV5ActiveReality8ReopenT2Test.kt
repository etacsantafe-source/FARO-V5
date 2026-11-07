package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** N033C1 native-only fenced active Reality8 reopen. NEVER a shipping host grant. */
class FaroV5ActiveReality8ReopenT2Test {
    @TempDir lateinit var root: Path
    private val shared = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }

    private fun fresh(id: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "n033c1-v2-staged", 2026L)
        )

    private fun planning8(id: String): StagedStorageGeneration = fresh(id).also { stage ->
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(stage).use { graph ->
            assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
            assertTrue(graph.verifyHealthy())
        }
    }

    private fun activate(stage: StagedStorageGeneration): Long =
        checkNotNull(FileActiveGenerationPointer(root)
            .compareAndSet(null, stage.descriptor.generationId)).sequence

    private fun reopen(stage: StagedStorageGeneration, seq: Long) =
        FaroV5GenerationStoreGraph.reopenVerifiedActivePlanning8(
            root, stage, seq, verifier, shared,
        )

    @Test fun withoutDurablePointerPlanning8CannotOpenAsActive() {
        val stage = planning8("c1-no-pointer")
        assertNull(FileActiveGenerationPointer(root).read())
        assertThrows(IllegalStateException::class.java) { reopen(stage, 1L).use { } }
        assertThrows(IllegalArgumentException::class.java) { reopen(stage, 0L).use { } }
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(stage).use {
            assertEquals(8, it.reality.maintenance.inspect().schemaVersion)
        }
    }

    @Test fun exactPointerAndSchema8ReopenFiveHealthyOwnersWithoutActivation() {
        val stage = planning8("c1-canonical")
        val sequence = activate(stage)
        reopen(stage, sequence).use { graph ->
            assertEquals(stage.descriptor.generationId.value, graph.generationId)
            assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
            assertNotNull(graph.workspace)
            assertEquals(0L, graph.reality.planningBackup.snapshot().reminders.revision)
            assertEquals(0L, graph.reality.planningBackup.snapshot().monthlyPlan.revision)
            assertTrue(graph.verifyHealthy())
        }
        assertEquals(sequence, FileActiveGenerationPointer(root).read()?.sequence)
    }

    @Test fun schemaSevenFailsBeforeAnyActivePlanningMigration() {
        val stage = fresh("c1-schema7")
        val realityFile = stage.directory.resolve(GenerationStoreFiles.REALITY)
        val before = Files.readAllBytes(realityFile)
        val sequence = activate(stage)
        assertThrows(IllegalStateException::class.java) { reopen(stage, sequence).use { } }
        assertTrue(before.contentEquals(Files.readAllBytes(realityFile)))
        RealityStore(realityFile.toString()).use { assertEquals(7, it.maintenance.inspect().schemaVersion) }
        assertEquals(sequence, FileActiveGenerationPointer(root).read()?.sequence)
    }

    @Test fun switchingAwayAndBackMakesOldPointerSequenceInvalid() {
        val stage = planning8("c1-aba")
        val pointer = FileActiveGenerationPointer(root)
        val original = activate(stage)
        val other = StorageGenerationId("c1-foreign-generation")
        assertNotNull(pointer.compareAndSet(stage.descriptor.generationId, other))
        assertNotNull(pointer.compareAndSet(other, stage.descriptor.generationId))
        assertEquals(stage.descriptor.generationId, pointer.read()?.generationId)
        assertTrue(checkNotNull(pointer.read()).sequence > original)
        assertThrows(IllegalStateException::class.java) { reopen(stage, original).use { } }
        reopen(stage, checkNotNull(pointer.read()).sequence).use {
            assertEquals(8, it.reality.maintenance.inspect().schemaVersion)
        }
    }

    @Test fun missingPlanningTableAndNoncanonicalDirectoryFailClosed() {
        val stage = planning8("c1-missing-table")
        val realityFile = stage.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(realityFile.toString(), SQLITE_OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE monthly_plan_item")
        }
        val sequence = activate(stage)
        assertThrows(IllegalStateException::class.java) { reopen(stage, sequence).use { } }
        assertFalse(Files.notExists(realityFile))
        assertThrows(IllegalStateException::class.java) {
            reopen(stage.copy(directory = root.resolve("forged-staged-path")), sequence).use { }
        }
    }

    @Test fun legacyActiveOpenerStillRejectsSchema8AndStagedOpenerRejectsActive() {
        val stage = planning8("c1-legacy-fence")
        val sequence = activate(stage)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(stage, verifier, shared).use { }
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(stage).use { }
        }
        reopen(stage, sequence).use { assertTrue(it.verifyHealthy()) }
    }


    @Test fun hostReadinessBooleanAcceptsOnlyHealthyExactActiveReality8() {
        val stage = planning8("c2b2-host-ready")
        val pinned = activate(stage)
        val pointerBefore = FileActiveGenerationPointer(root).read()
        assertTrue(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, stage, pinned, verifier, shared,
        ))
        assertEquals(pointerBefore, FileActiveGenerationPointer(root).read())
        assertFalse(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, stage, pinned + 1L, verifier, shared,
        ))
    }

    @Test fun hostReadinessRejectsSchemaSevenAndStaleAbaWithoutMigration() {
        val legacy = fresh("c2b2-host-schema7")
        val before = Files.readAllBytes(legacy.directory.resolve(GenerationStoreFiles.REALITY))
        val first = activate(legacy)
        assertFalse(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, legacy, first, verifier, shared,
        ))
        assertTrue(before.contentEquals(
            Files.readAllBytes(legacy.directory.resolve(GenerationStoreFiles.REALITY))
        ))
        val foreign = StorageGenerationId("c2b2-foreign")
        val pointer = FileActiveGenerationPointer(root)
        assertNotNull(pointer.compareAndSet(legacy.descriptor.generationId, foreign))
        assertNotNull(pointer.compareAndSet(foreign, legacy.descriptor.generationId))
        assertFalse(FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
            root, legacy, first, verifier, shared,
        ))
    }
}
