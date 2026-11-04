package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** v2CoreDev ONLY: genuine active 5-owner graph, no OS dispatch, no model or Apply. */
class FaroV5V2CoreDevAuthorityTest {
    @TempDir lateinit var root: Path

    @Test fun firstOpenPublishesJournalFenceAndFiveOwnerPointer() {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val expected = StorageGenerationId("v2-core-dev-workspace-1")
            assertEquals(expected, FileActiveGenerationPointer(root).read()?.generationId)
            val entry = FileBootstrapJournal(root).read(
                MaintenanceOperationId("v2-core-dev-first-activation-1"),
            )
            assertEquals(BootstrapPhase.COMPLETED, entry?.phase)
            assertTrue(entry!!.materialWritesAccepted)
            assertTrue(FileGenerationReplayFence(root).isInstalled(MaintenanceOperationKind.RESTORE, expected))
            assertEquals(0L, runBlocking { session.workspaceReadPort.readSnapshot() }.revision)
            assertThrows(IllegalStateException::class.java) {
                FaroV5ActiveGenerationGraphOwner.open(root, expected.value, SingleProcessMaterialWriteGate(), false)
            }
        }
    }

    @Test fun localSelectionSurvivesRestartAndStalePortFailsClosed() {
        val session = FaroV5V2CoreDevAuthority(root).open()
        val chat = ConversationId("v2-core-dev-test-chat")
        assertTrue(runBlocking {
            session.selectionPort.createAndSelect(chat, 1000L, IdempotencyKey("v2-core-first-chat"))
        } is ConversationSelectionResult.Saved)
        assertEquals(chat, runBlocking { session.workspaceReadPort.readSnapshot() }.activeConversationId)
        val staleRead = session.workspaceReadPort
        session.close()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { staleRead.readSnapshot() }
        }
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            assertEquals(chat, runBlocking { reopened.workspaceReadPort.readSnapshot() }.activeConversationId)
        }
    }

    @Test fun corruptActiveWorkspaceIsDeniedRatherThanReinitialized() {
        FaroV5V2CoreDevAuthority(root).open().use { }
        val stage = FileStorageGenerationLayout(root).requireStaged(
            StorageGenerationId("v2-core-dev-workspace-1"),
        )
        val sqlite = stage.directory.resolve(GenerationStoreFiles.WORKSPACE)
        Files.write(sqlite, byteArrayOf(1, 2, 3))
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(root).open() }
        assertEquals(3L, Files.size(sqlite))
    }
}
