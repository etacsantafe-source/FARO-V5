package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * A5: new host-level seam only. All five stores are real SQLite in a private
 * temporary generation, never the installed Android data directory.
 */
class FaroV5ActiveGenerationGraphOwnerPlanning8OpenT2Test {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val layout by lazy { FileStorageGenerationLayout(root) }

    private fun descriptor(id: String) =
        StorageGenerationDescriptor(StorageGenerationId(id), "s3-a5-planning8-host", 1L)

    private fun stage(id: String, planning8: Boolean): StorageGenerationId {
        val target = StorageGenerationId(id)
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(descriptor(id))
        if (planning8) {
            RealityStore(
                staged.directory.resolve(GenerationStoreFiles.REALITY).toString(),
                planningStage8 = true,
            ).use { assertEquals(8, it.maintenance.inspect().schemaVersion) }
        }
        return target
    }

    private fun open(id: String, sequence: Long, certified: Boolean = true) =
        FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost(
            root, id, sequence, gate, certified,
        )

    @Test fun activeSchemaEightOpensFiveRealSqliteOwnersAndRetainsPointer() {
        val id = stage("a5-active8", planning8 = true)
        val pinned = checkNotNull(pointer.compareAndSet(null, id))
        open(id.value, pinned.sequence).use { graph ->
            assertEquals(id.value, graph.generationId)
            assertNotNull(graph.workspace)
            assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
            assertTrue(graph.verifyHealthy())
            assertEquals(pinned, pointer.read())
        }
    }

    @Test fun uncertifiedHostDeniesBeforeOpeningAnySqliteFile() {
        val staged = layout.stageWorkspaceV2(descriptor("a5-denied"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            Files.createFile(staged.directory.resolve(file))
        }
        val pinned = checkNotNull(pointer.compareAndSet(null, staged.descriptor.generationId))
        assertThrows(IllegalStateException::class.java) {
            open(staged.descriptor.generationId.value, pinned.sequence, certified = false)
        }
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            assertEquals(0L, Files.size(staged.directory.resolve(file)))
            assertFalse(Files.exists(staged.directory.resolve("$file-wal")))
        }
    }

    @Test fun schemaSevenIsRejectedWithoutActiveMigration() {
        val id = stage("a5-schema7", planning8 = false)
        val file = layout.requireStaged(id).directory.resolve(GenerationStoreFiles.REALITY)
        val before = Files.readAllBytes(file)
        val pinned = checkNotNull(pointer.compareAndSet(null, id))
        assertThrows(IllegalStateException::class.java) { open(id.value, pinned.sequence) }
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals(7, RealityStore(file.toString()).use {
            it.maintenance.inspect().schemaVersion
        })
    }

    @Test fun wrongGenerationCannotOpenValidSchemaEight() {
        val id = stage("a5-wrong-id", planning8 = true)
        val pinned = checkNotNull(pointer.compareAndSet(null, id))
        assertThrows(IllegalStateException::class.java) {
            open("a5-foreign-id", pinned.sequence)
        }
        assertEquals(id, pointer.read()?.generationId)
    }

    @Test fun wrongPointerSequenceCannotOpenEvenWithCorrectId() {
        val id = stage("a5-wrong-sequence", planning8 = true)
        val pinned = checkNotNull(pointer.compareAndSet(null, id))
        assertThrows(IllegalStateException::class.java) {
            open(id.value, pinned.sequence + 1L)
        }
        assertEquals(pinned, pointer.read())
    }

    @Test fun abaAwayAndBackCannotReviveOldPinnedHost() {
        val id = stage("a5-aba-target", planning8 = true)
        val initial = checkNotNull(pointer.compareAndSet(null, id))
        val other = StorageGenerationId("a5-aba-other")
        checkNotNull(pointer.compareAndSet(id, other))
        val again = checkNotNull(pointer.compareAndSet(other, id))
        assertTrue(again.sequence > initial.sequence)
        assertThrows(IllegalStateException::class.java) {
            open(id.value, initial.sequence)
        }
        // A NEW host with a NEW observation is allowed to reopen verified data.
        open(id.value, again.sequence).use {
            assertEquals(8, it.reality.maintenance.inspect().schemaVersion)
        }
    }
}
