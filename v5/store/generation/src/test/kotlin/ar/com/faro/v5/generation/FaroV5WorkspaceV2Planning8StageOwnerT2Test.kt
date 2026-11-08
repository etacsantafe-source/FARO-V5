package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** A5: only new STAGED Reality8 preparation and pointer-invariance cases. */
class FaroV5WorkspaceV2Planning8StageOwnerT2Test {
    @TempDir lateinit var root: Path
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private fun descriptor(name: String) =
        StorageGenerationDescriptor(StorageGenerationId(name), "a5-stage8-frozen", 1L)
    private fun reality(id: StorageGenerationId) =
        layout.requireStaged(id).directory.resolve(GenerationStoreFiles.REALITY)

    @Test fun freshPlanning8PreparesFiveRealStoresWithoutPublishingPointer() {
        val staged = FaroV5WorkspaceV2StageOwner(root)
            .stageFreshPlanning8AndVerify(descriptor("a5-stage8-fresh"))
        assertNull(pointer.read())
        FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged).use {
            assertNotNull(it.workspace)
            assertEquals(8, it.reality.maintenance.inspect().schemaVersion)
            assertTrue(it.verifyHealthy())
        }
        assertNull(pointer.read())
    }

    @Test fun freshStagedEightNeverMutatesExistingActiveSevenGeneration() {
        val owner = FaroV5WorkspaceV2StageOwner(root)
        val active = owner.stageFreshAndVerify(descriptor("a5-existing-active7"))
        val oldFile = reality(active.descriptor.generationId)
        val original = Files.readAllBytes(oldFile)
        val pinned = checkNotNull(pointer.compareAndSet(null, active.descriptor.generationId))
        val next = owner.stageFreshPlanning8AndVerify(descriptor("a5-new-staged8"))
        assertEquals(8, RealityStore(reality(next.descriptor.generationId).toString(),
            planningStage8 = true).use { it.maintenance.inspect().schemaVersion })
        assertArrayEquals(original, Files.readAllBytes(oldFile))
        assertEquals(pinned, pointer.read())
    }

    @Test fun cannotStagePlanningEightOverActiveTarget() {
        val owner = FaroV5WorkspaceV2StageOwner(root)
        val active = owner.stageFreshAndVerify(descriptor("a5-stage-target-active"))
        val file = reality(active.descriptor.generationId)
        val original = Files.readAllBytes(file)
        val pinned = checkNotNull(pointer.compareAndSet(null, active.descriptor.generationId))
        assertThrows(IllegalStateException::class.java) {
            owner.stageFreshPlanning8AndVerify(active.descriptor)
        }
        assertArrayEquals(original, Files.readAllBytes(file))
        assertEquals(pinned, pointer.read())
    }

    @Test fun existingStagedTargetIsNotAdoptedOrReinitialized() {
        val owner = FaroV5WorkspaceV2StageOwner(root)
        val candidate = owner.stageFreshAndVerify(descriptor("a5-stage-already-used"))
        val file = reality(candidate.descriptor.generationId)
        val original = Files.readAllBytes(file)
        assertThrows(IllegalStateException::class.java) {
            owner.stageFreshPlanning8AndVerify(candidate.descriptor)
        }
        assertArrayEquals(original, Files.readAllBytes(file))
        assertNull(pointer.read())
    }

    @Test fun pointerDriftDuringStageDeniesBeforeSchemaEightMigration() {
        val old = StorageGenerationId("a5-stage8-old")
        val other = StorageGenerationId("a5-stage8-other")
        checkNotNull(pointer.compareAndSet(null, old))
        val owner = FaroV5WorkspaceV2StageOwner(root) { file ->
            if (file == GenerationStoreFiles.REALITY) {
                checkNotNull(pointer.compareAndSet(old, other))
            }
        }
        val requested = descriptor("a5-stage8-raced")
        assertThrows(IllegalStateException::class.java) {
            owner.stageFreshPlanning8AndVerify(requested)
        }
        assertEquals(other, pointer.read()?.generationId)
        // A failed attempt stays quarantined schema7. No automatic retry, wipe
        // or migration after the active pointer changed.
        val file = reality(requested.generationId)
        assertEquals(7, RealityStore(file.toString()).use {
            it.maintenance.inspect().schemaVersion
        })
    }
}
