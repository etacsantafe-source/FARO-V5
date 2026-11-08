package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * NEW S3.A5 Reality8 DEV host admission tests only. Earlier 6/6+5/5+5/5+4/4
 * staging and cutover suites are not rerun.
 */
class FaroV5V2Planning8DevHostTest {
    @TempDir lateinit var root: Path
    private val oldId = StorageGenerationId("v2-core-dev-workspace-1")
    private val newId = StorageGenerationId("v2-core-dev-planning8-1")
    private val upgrade = MaintenanceOperationId("v2-core-dev-planning8-upgrade-1")
    private val pointer get() = FileActiveGenerationPointer(root)
    private val journal get() = FileBootstrapJournal(root)
    private val fence get() = FileGenerationReplayFence(root)
    private fun startOld() = FaroV5V2CoreDevAuthority(root).open()
    private fun promoter(fault: () -> Unit = {}) =
        FaroV5V2Planning8DevHost(root, SingleProcessMaterialWriteGate(), fault)

    @Test fun ordinaryChatOpenNeverStartsPlanning8OrGrantsNewPointer() {
        startOld().use { first ->
            assertEquals(oldId, pointer.read()!!.generationId)
            assertNull(journal.read(upgrade))
            assertFalse(Files.exists(root.resolve("dev-planning8-origin-v1.bin")))
            assertTrue(runBlocking { first.workspaceReadPort.readSnapshot() }
                .conversations.isEmpty())
        }
        startOld().use { reopened ->
            assertEquals(oldId, pointer.read()!!.generationId)
            assertNull(journal.read(upgrade))
            assertTrue(runBlocking { reopened.workspaceReadPort.readSnapshot() }
                .conversations.isEmpty())
        }
    }

    @Test fun explicitUpgradeUsesSameCanonicalJournalAndPreservesReality7Bytes() {
        val oldSession = startOld()
        val oldPort = oldSession.workspaceReadPort
        val oldChat = ConversationId("old7-chat-not-migrated")
        assertTrue(runBlocking {
            oldSession.selectionPort.createAndSelect(
                oldChat, 1000L, IdempotencyKey("old7-preserved-init"),
            )
        } is ConversationSelectionResult.Saved)
        val previous = checkNotNull(pointer.read())
        val oldFile = FileStorageGenerationLayout(root).requireStaged(oldId)
            .directory.resolve(GenerationStoreFiles.REALITY)
        val bytes = Files.readAllBytes(oldFile)
        oldSession.close()
        FaroV5V2CoreDevAuthority(root)
            .upgradePlanning8AfterExplicitForegroundRequest { true }.use { active ->
                val now = checkNotNull(pointer.read())
                assertEquals(newId, now.generationId)
                assertEquals(previous.sequence + 1L, now.sequence)
                assertEquals(BootstrapPhase.COMPLETED, journal.read(upgrade)!!.phase)
                assertTrue(journal.read(upgrade)!!.materialWritesAccepted)
                assertTrue(fence.isInstalled(MaintenanceOperationKind.RESTORE, newId))
                assertArrayEquals(bytes, Files.readAllBytes(oldFile))
                assertTrue(runBlocking { active.workspaceReadPort.readSnapshot() }
                    .conversations.isEmpty(), "fresh Reality8 must not imply migration")
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { oldPort.readSnapshot() }
                }
                FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost(
                    root, newId.value, now.sequence, SingleProcessMaterialWriteGate(), true,
                ).use { graph ->
                    assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
                    assertNotNull(graph.workspace)
                    assertTrue(graph.verifyHealthy())
                }
            }
    }

    @Test fun restartedAuthorityOpensExactEightWithoutNewCutover() {
        startOld().use { }
        FaroV5V2CoreDevAuthority(root)
            .upgradePlanning8AfterExplicitForegroundRequest { true }.use { }
        val exact = checkNotNull(pointer.read())
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            assertEquals(exact, pointer.read())
            assertTrue(runBlocking { reopened.workspaceReadPort.readSnapshot() }
                .conversations.isEmpty())
        }
        assertEquals(BootstrapPhase.COMPLETED, journal.read(upgrade)!!.phase)
    }

    @Test fun stagedEightRestartRecoversOnlyPreviouslyAuthorizedJournal() {
        startOld().use { }
        val before = checkNotNull(pointer.read())
        val crashed = promoter { error("injected crash after durable VALIDATED") }
        assertThrows(IllegalStateException::class.java) {
            crashed.promoteAfterExplicitForegroundRequest { true }
        }
        assertEquals(before, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(upgrade)!!.phase)
        assertFalse(fence.isInstalled(MaintenanceOperationKind.RESTORE, newId))
        FaroV5V2CoreDevAuthority(root).open().use { restored ->
            assertEquals(newId, pointer.read()!!.generationId)
            assertEquals(before.sequence + 1L, pointer.read()!!.sequence)
            assertTrue(runBlocking { restored.workspaceReadPort.readSnapshot() }
                .conversations.isEmpty())
        }
        assertEquals(BootstrapPhase.COMPLETED, journal.read(upgrade)!!.phase)
    }

    @Test fun corruptedOriginFailsClosedWithoutSwitchingJournal() {
        startOld().use { }
        val before = checkNotNull(pointer.read())
        assertThrows(IllegalStateException::class.java) {
            promoter { error("inject after VALIDATED") }
                .promoteAfterExplicitForegroundRequest { true }
        }
        Files.write(root.resolve("dev-planning8-origin-v1.bin"),
            "tampered origin".toByteArray())
        assertThrows(Exception::class.java) {
            FaroV5V2CoreDevAuthority(root).open()
        }
        assertEquals(before, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(upgrade)!!.phase)
        assertFalse(fence.isInstalled(MaintenanceOperationKind.RESTORE, newId))
    }

    @Test fun abaPointerWithOldIdCannotReuseDurableOriginSequence() {
        startOld().use { }
        val before = checkNotNull(pointer.read())
        assertThrows(IllegalStateException::class.java) {
            promoter { error("inject after VALIDATED") }
                .promoteAfterExplicitForegroundRequest { true }
        }
        val foreign = StorageGenerationId("dev-planning8-foreign-aba")
        checkNotNull(pointer.compareAndSet(oldId, foreign))
        checkNotNull(pointer.compareAndSet(foreign, oldId))
        assertNotEquals(before, pointer.read())
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(root).open() }
        assertEquals(BootstrapPhase.VALIDATED, journal.read(upgrade)!!.phase)
        assertFalse(fence.isInstalled(MaintenanceOperationKind.RESTORE, newId))
    }

    @Test fun backgroundOrMissingOldAuthorityCannotInitiateUpgrade() {
        startOld().use { }
        val before = checkNotNull(pointer.read())
        assertThrows(IllegalStateException::class.java) {
            promoter().promoteAfterExplicitForegroundRequest { false }
        }
        assertEquals(before, pointer.read())
        assertNull(journal.read(upgrade))
        assertFalse(Files.exists(root.resolve("dev-planning8-origin-v1.bin")))
        val absentRoot = root.resolve("never-initialized")
        Files.createDirectory(absentRoot)
        assertThrows(Exception::class.java) {
            FaroV5V2Planning8DevHost(absentRoot, SingleProcessMaterialWriteGate())
                .promoteAfterExplicitForegroundRequest { true }
        }
    }

    @Test fun foreignPreparingJournalIsNeverAutoadopted() {
        startOld().use { }
        val before = checkNotNull(pointer.read())
        journal.create(BootstrapJournalEntry(
            upgrade, MaintenanceOperationKind.RESTORE, oldId, newId,
            BootstrapPhase.PREPARING,
        ))
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(root).open() }
        assertEquals(before, pointer.read())
        assertEquals(BootstrapPhase.PREPARING, journal.read(upgrade)!!.phase)
        assertFalse(fence.isInstalled(MaintenanceOperationKind.RESTORE, newId))
    }
}
