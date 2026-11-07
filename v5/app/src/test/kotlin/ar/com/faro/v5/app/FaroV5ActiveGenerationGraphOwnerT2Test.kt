package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Denials must happen BEFORE SQLite opens: Android JVM does not load native SQLite. */
class FaroV5ActiveGenerationGraphOwnerT2Test {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private fun descriptor(id: String) =
        StorageGenerationDescriptor(StorageGenerationId(id), "c127-authority", 1L)

    @Test fun uncertifiedV2IsDeniedWithoutTouchingDatabases() {
        val staged = layout.stageWorkspaceV2(descriptor("host-v2"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            Files.createFile(staged.directory.resolve(file))
        }
        checkNotNull(pointer.compareAndSet(null, StorageGenerationId("host-v2")))
        assertThrows(IllegalStateException::class.java) {
            FaroV5ActiveGenerationGraphOwner.open(root, "host-v2", gate, false)
        }
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            assertEquals(0L, Files.size(staged.directory.resolve(file)))
            assertFalse(Files.exists(staged.directory.resolve(file + "-wal")))
        }
    }

    @Test fun requestedGenerationCannotDisagreeWithActivePointer() {
        layout.stage(descriptor("host-old"))
        layout.stage(descriptor("host-forged"))
        checkNotNull(pointer.compareAndSet(null, StorageGenerationId("host-old")))
        assertThrows(IllegalStateException::class.java) {
            FaroV5ActiveGenerationGraphOwner.open(root, "host-forged", gate, false)
        }
        assertEquals(StorageGenerationId("host-old"), pointer.read()!!.generationId)
    }

    @Test fun incompleteCertifiedCandidateFailsBeforeInitializingSqlite() {
        val staged = layout.stageWorkspaceV2(descriptor("host-v2-incomplete"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            Files.createFile(staged.directory.resolve(file))
        }
        checkNotNull(pointer.compareAndSet(null, StorageGenerationId("host-v2-incomplete")))
        assertThrows(IllegalStateException::class.java) {
            FaroV5ActiveGenerationGraphOwner.open(root, "host-v2-incomplete", gate, true)
        }
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            assertEquals(0L, Files.size(staged.directory.resolve(file)))
        }
    }
}
