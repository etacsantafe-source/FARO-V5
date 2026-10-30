package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Native SQLite WAL evidence: STAGED immutable preflight is never used on ACTIVE WAL. */
class FaroV5ActiveWalRecoveryT2Test {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }

    private fun materializeFive() =
        FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(
                StorageGenerationId("c128-active-wal"), "c128-wal-schemas", 9L,
            )
        ).also { staged ->
            val dir = staged.directory
            RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
            CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
            WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
            AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
            ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        }

    @Test fun committedWorkspaceWalVisibleToActiveGraphWithoutWeakeningStagedPolicy() {
        val stage = materializeFive()
        val workspace = stage.directory.resolve(GenerationStoreFiles.WORKSPACE)
        BundledSQLiteDriver().open(workspace.toString(), SQLITE_OPEN_READWRITE).use { writer ->
            writer.prepare("PRAGMA journal_mode=WAL").use { assertTrue(it.step()) }
            writer.execSQL(
                """INSERT INTO workspace_conversation
                   (id, title, activity_ms, pinned, archived, pending_review)
                   VALUES ('wal-visible-chat', 'WAL committed chat', 1, 0, 0, 0)"""
            )
            assertTrue(Files.exists(stage.directory.resolve(GenerationStoreFiles.WORKSPACE + "-wal")))
            // A STAGED candidate must remain sealed and cannot ignore this WAL.
            assertThrows(IllegalStateException::class.java) {
                FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
                    stage, verifier, gate,
                ).use { }
            }
            // ACTIVE reopen must observe the committed WAL, not immutable=1's
            // stale base database, and must retain all five real store owners.
            FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
                stage, verifier, gate,
            ).use { graph ->
                assertTrue(graph.verifyHealthy())
                assertNotNull(graph.workspace)
                val chats = graph.workspace!!.snapshot().workspace.conversations
                assertTrue(chats.any { it.id == ConversationId("wal-visible-chat") })
            }
        }
    }

    @Test fun activeOpenRejectsSymlinkedWalBeforeOpeningAnyStore() {
        val stage = materializeFive()
        val workspace = stage.directory.resolve(GenerationStoreFiles.WORKSPACE)
        val symlink = stage.directory.resolve(GenerationStoreFiles.WORKSPACE + "-wal")
        val external = root.resolve("external-wal")
        Files.write(external, byteArrayOf(1, 2, 3))
        Files.createSymbolicLink(symlink, external)
        val previousBytes = Files.size(workspace)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
                stage, verifier, gate,
            ).use { }
        }
        assertEquals(previousBytes, Files.size(workspace))
        assertTrue(Files.isSymbolicLink(symlink))
    }
}
