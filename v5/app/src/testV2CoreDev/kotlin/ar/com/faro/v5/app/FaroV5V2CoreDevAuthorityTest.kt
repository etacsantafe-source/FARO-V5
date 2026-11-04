package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** v2CoreDev ONLY: genuine active 5-owner graph, no OS dispatch, no model or Apply. */
class FaroV5V2CoreDevAuthorityTest {
    @TempDir lateinit var root: Path

    /** Journal/crash fixture: intentionally constructs pre-switch state only in isolated @TempDir. */
    private fun interrupted(
        name: String, phase: BootstrapPhase, stage: Boolean, stores: Boolean,
    ): Path {
        val dir = root.resolve(name)
        val previous = StorageGenerationId("v2-core-dev-legacy-base-1")
        val target = StorageGenerationId("v2-core-dev-workspace-1")
        ActiveGenerationStorageLocator(dir).ensureInitialGenerationAndGetDirectory(
            previous.value, "v2-core-dev-recovery-base-1",
        )
        val operation = MaintenanceOperationId("v2-core-dev-first-activation-1")
        FileBootstrapJournal(dir).create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE, previous, target, phase,
        ))
        if (stage) {
            val staged = FileStorageGenerationLayout(dir).stageWorkspaceV2(
                StorageGenerationDescriptor(target, "v2-core-dev-five-store-schema-1", 1L),
            )
            if (stores) {
                RealityStore(staged.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { }
                CommercialEvidenceStore(staged.directory.resolve(
                    GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
                WebEvidenceStore(staged.directory.resolve(
                    GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
                AuxiliaryDeliveryStore(staged.directory.resolve(
                    GenerationStoreFiles.AUXILIARY).toString()).use { }
                ConversationWorkspaceStore(staged.directory.resolve(
                    GenerationStoreFiles.WORKSPACE).toString()).use { }
            }
        }
        return dir
    }

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
            assertTrue(runBlocking { session.workspaceReadPort.readSnapshot() }.conversations.isEmpty())
            assertThrows(IllegalStateException::class.java) {
                FaroV5ActiveGenerationGraphOwner.open(root, expected.value, SingleProcessMaterialWriteGate(), false)
            }
        }
        // PREPARING no stage; PREPARING immutable marker+descriptor only;
        // STAGED 5 healthy stores; VALIDATED 5 healthy stores and V1 pointer.
        val cases = listOf(
            Triple("preparing-new", BootstrapPhase.PREPARING, false),
            Triple("preparing-metadata", BootstrapPhase.PREPARING, true),
            Triple("staged-complete", BootstrapPhase.STAGED, true),
            Triple("validated-complete", BootstrapPhase.VALIDATED, true),
        )
        for ((name, phase, hasStage) in cases) {
            val dir = interrupted(name, phase, hasStage,
                phase != BootstrapPhase.PREPARING)
            FaroV5V2CoreDevAuthority(dir).open().use { session ->
                val target = StorageGenerationId("v2-core-dev-workspace-1")
                assertEquals(target, FileActiveGenerationPointer(dir).read()?.generationId, name)
                val complete = FileBootstrapJournal(dir).read(
                    MaintenanceOperationId("v2-core-dev-first-activation-1"),
                )
                assertEquals(BootstrapPhase.COMPLETED, complete?.phase, name)
                assertTrue(complete!!.materialWritesAccepted, name)
                assertTrue(FileGenerationReplayFence(dir).isInstalled(
                    MaintenanceOperationKind.RESTORE, target), name)
                assertTrue(runBlocking { session.workspaceReadPort.readSnapshot() }
                    .conversations.isEmpty(), name)
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

        val target = StorageGenerationId("v2-core-dev-workspace-1")
        val previous = StorageGenerationId("v2-core-dev-legacy-base-1")
        val partial = interrupted("preparing-partial-sqlite", BootstrapPhase.PREPARING, true, false)
        val partialStage = FileStorageGenerationLayout(partial).requireStaged(target)
        val stray = partialStage.directory.resolve(GenerationStoreFiles.REALITY)
        val bytes = byteArrayOf(7, 9, 11, 13)
        Files.write(stray, bytes)
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(partial).open() }
        assertArrayEquals(bytes, Files.readAllBytes(stray))
        assertEquals(previous, FileActiveGenerationPointer(partial).read()?.generationId)

        val incomplete = interrupted("staged-incomplete", BootstrapPhase.STAGED, true, false)
        val incompleteStage = FileStorageGenerationLayout(incomplete).requireStaged(target)
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(incomplete).open() }
        assertEquals(2L, Files.list(incompleteStage.directory).use { it.count() })
        assertEquals(previous, FileActiveGenerationPointer(incomplete).read()?.generationId)

        val foreign = root.resolve("foreign-operation")
        ActiveGenerationStorageLocator(foreign).ensureInitialGenerationAndGetDirectory(
            previous.value, "v2-core-dev-recovery-base-1")
        FileBootstrapJournal(foreign).create(BootstrapJournalEntry(
            MaintenanceOperationId("other-maintenance-operation-1"),
            MaintenanceOperationKind.RESTORE, previous, target, BootstrapPhase.PREPARING))
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(foreign).open() }
        assertEquals(previous, FileActiveGenerationPointer(foreign).read()?.generationId)

        // A VALIDATED journal with a pointer switched independently must not
        // be adopted as a completed recovery or material-write authority.
        val unexpectedPointer = interrupted(
            "validated-pointer-drift", BootstrapPhase.VALIDATED, true, true)
        checkNotNull(FileActiveGenerationPointer(unexpectedPointer).compareAndSet(
            previous, target))
        assertThrows(Exception::class.java) {
            FaroV5V2CoreDevAuthority(unexpectedPointer).open()
        }
        assertEquals(BootstrapPhase.VALIDATED,
            FileBootstrapJournal(unexpectedPointer).read(
                MaintenanceOperationId("v2-core-dev-first-activation-1"))?.phase)
        assertEquals(target, FileActiveGenerationPointer(unexpectedPointer).read()?.generationId)

        // Root symlinks are denied before bootstrap/generation directories are created.
        val symlinkTarget = Files.createDirectory(root.resolve("symlink-target"))
        val symlinkRoot = Files.createSymbolicLink(root.resolve("symlink-root"), symlinkTarget)
        assertThrows(Exception::class.java) {
            FaroV5V2CoreDevAuthority(symlinkRoot).open()
        }
        assertFalse(Files.exists(symlinkTarget.resolve("generations")))
    }
    @Test fun acceptedLocalUserMessageIsDurableWithoutSyntheticAssistant() {
        val chat = ConversationId("v2-core-message-chat")
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            assertTrue(runBlocking {
                session.selectionPort.createAndSelect(chat, 2000L, IdempotencyKey("m3-create"))
            } is ConversationSelectionResult.Saved)
            val draft = runBlocking { session.composerPort.read(chat) }!!
            val saved = runBlocking {
                session.composerPort.save(ConversationDraftSaveRequest(
                    chat, draft.authorityToken, draft.revision,
                    "Hola desde el Chat local", IdempotencyKey("m3-save"),
                ))
            } as ConversationDraftWriteResult.Saved
            val candidate = checkNotNull(
                ConversationDraftPolicy.sendCandidate(ConversationDraftEditorState(saved.snapshot))
            )
            val sent = runBlocking {
                session.composerPort.sendDraft(ConversationDraftSendRequest(
                    candidate, ConversationMessageId("m3-user-1"), ConversationTurnId("m3-turn-1"),
                    2001L, IdempotencyKey("m3-send"),
                ))
            }
            assertTrue(sent is ConversationTimelineWriteResult.Saved)
            val timeline = runBlocking { session.timelineReadPort.readTimeline(chat) }!!
            assertEquals(1, timeline.messages.size)
            assertEquals(ConversationMessageRole.USER, timeline.messages.single().role)
            assertEquals(ConversationMessageState.ACCEPTED, timeline.messages.single().state)
            assertEquals("Hola desde el Chat local", timeline.messages.single().text)
        }
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            val timeline = runBlocking { reopened.timelineReadPort.readTimeline(chat) }!!
            assertEquals(1, timeline.messages.size)
            assertEquals(ConversationMessageRole.USER, timeline.messages.single().role)
        }
    }


    @Test fun closedSessionRejectsSelectionAndComposerWithoutMutation() {
        val chat = ConversationId("m4-closed-session-chat")
        val session = FaroV5V2CoreDevAuthority(root).open()
        assertTrue(runBlocking {
            session.selectionPort.createAndSelect(chat, 3000L, IdempotencyKey("m4-init-chat"))
        } is ConversationSelectionResult.Saved)
        val initial = runBlocking { session.composerPort.read(chat) }!!
        val saved = runBlocking {
            session.composerPort.save(ConversationDraftSaveRequest(
                chat, initial.authorityToken, initial.revision,
                "Borrador local M4", IdempotencyKey("m4-first-save"),
            ))
        } as ConversationDraftWriteResult.Saved
        val sendCandidate = checkNotNull(
            ConversationDraftPolicy.sendCandidate(ConversationDraftEditorState(saved.snapshot))
        )
        val staleSelection = session.selectionPort
        val staleComposer = session.composerPort
        session.close()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                staleSelection.createAndSelect(
                    ConversationId("m4-forbidden-chat"), 3001L, IdempotencyKey("m4-stale-create"),
                )
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { staleSelection.select(chat, IdempotencyKey("m4-stale-select")) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                staleComposer.save(ConversationDraftSaveRequest(
                    chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                    "Mutacion prohibida", IdempotencyKey("m4-stale-save"),
                ))
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                staleComposer.sendDraft(ConversationDraftSendRequest(
                    sendCandidate, ConversationMessageId("m4-forbidden-message"),
                    ConversationTurnId("m4-forbidden-turn"), 3002L,
                    IdempotencyKey("m4-stale-send"),
                ))
            }
        }
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            val snapshot = runBlocking { reopened.workspaceReadPort.readSnapshot() }
            assertEquals(1, snapshot.conversations.size)
            assertEquals(chat, snapshot.activeConversationId)
            assertEquals(saved.snapshot, runBlocking { reopened.composerPort.read(chat) })
            assertTrue(runBlocking { reopened.timelineReadPort.readTimeline(chat) }!!.messages.isEmpty())
        }
    }

    @Test fun pointerDriftRejectsLocalWritesWithoutMutation() {
        val chat = ConversationId("m4-pointer-drift-chat")
        val session = FaroV5V2CoreDevAuthority(root).open()
        assertTrue(runBlocking {
            session.selectionPort.createAndSelect(chat, 4000L, IdempotencyKey("m4-drift-init"))
        } is ConversationSelectionResult.Saved)
        val draft = runBlocking { session.composerPort.read(chat) }!!
        val pointer = FileActiveGenerationPointer(root)
        val target = StorageGenerationId("v2-core-dev-workspace-1")
        val previous = StorageGenerationId("v2-core-dev-legacy-base-1")
        checkNotNull(pointer.compareAndSet(target, previous))
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { session.workspaceReadPort.readSnapshot() }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    session.selectionPort.createAndSelect(
                        ConversationId("m4-drift-forbidden"), 4001L,
                        IdempotencyKey("m4-drift-deny-selection"),
                    )
                }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    session.composerPort.save(ConversationDraftSaveRequest(
                        chat, draft.authorityToken, draft.revision,
                        "Mutacion prohibida", IdempotencyKey("m4-drift-deny-save"),
                    ))
                }
            }
            assertEquals(previous, pointer.read()?.generationId)
        } finally {
            session.close()
            checkNotNull(pointer.compareAndSet(previous, target))
        }
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            assertEquals(1, runBlocking { reopened.workspaceReadPort.readSnapshot() }.conversations.size)
            assertEquals(draft, runBlocking { reopened.composerPort.read(chat) })
            assertTrue(runBlocking { reopened.timelineReadPort.readTimeline(chat) }!!.messages.isEmpty())
        }
    }

    @Test fun isolatedDevSearchFindsCommittedMessageAndSelectsCanonicalChat() = runBlocking {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val chat = ConversationId("dev-search-expensas")
            assertTrue(session.selectionPort.createAndSelect(
                chat, 1000L, IdempotencyKey("dev-search-create"),
            ) is ConversationSelectionResult.Saved)
            val draft = requireNotNull(session.composerPort.read(chat))
            val saved = session.composerPort.save(ConversationDraftSaveRequest(
                chat, draft.authorityToken, draft.revision,
                "Pagar las expensas de noviembre", IdempotencyKey("dev-search-draft"),
            )) as ConversationDraftWriteResult.Saved
            assertTrue(session.composerPort.sendDraft(ConversationDraftSendRequest(
                ConversationDraftSendCandidate(
                    chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                    saved.snapshot.text,
                ),
                ConversationMessageId("dev-search-user"), ConversationTurnId("dev-search-turn"),
                1001L, IdempotencyKey("dev-search-send"),
            )) is ConversationTimelineWriteResult.Saved)
            val page = session.searchReadPort.search("EXPENSAS")
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchCoverage.COMPLETE, page.coverage)
            assertEquals(1, page.hits.size)
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchOpenResult.CONFIRMED,
                session.searchReadPort.openAuthorized(page.hits.single()))
            assertEquals(chat, session.workspaceReadPort.readSnapshot().activeConversationId)
        }
    }

    @Test fun closedDevSessionDeniesPublishedHitEvenAfterNewVerifiedGrant() = runBlocking {
        val first = FaroV5V2CoreDevAuthority(root).open()
        val chat = ConversationId("dev-search-stale-hit")
        assertTrue(first.selectionPort.createAndSelect(
            chat, 1000L, IdempotencyKey("dev-search-stale-create"),
        ) is ConversationSelectionResult.Saved)
        val oldPort = first.searchReadPort
        val oldHit = oldPort.search("nuevo").hits.single()
        first.close()
        assertEquals(ar.com.faro.v5.surface.FaroV5SearchCoverage.UNAVAILABLE,
            oldPort.search("nuevo").coverage)
        assertEquals(ar.com.faro.v5.surface.FaroV5SearchOpenResult.UNAVAILABLE,
            oldPort.openAuthorized(oldHit))
        FaroV5V2CoreDevAuthority(root).open().use { second ->
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchOpenResult.UNAVAILABLE,
                oldPort.openAuthorized(oldHit))
            val fresh = second.searchReadPort.search("nuevo")
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchCoverage.COMPLETE, fresh.coverage)
            assertEquals(1, fresh.hits.size)
            assertNotEquals(oldHit.issuanceId, fresh.hits.single().issuanceId)
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchOpenResult.CONFIRMED,
                second.searchReadPort.openAuthorized(fresh.hits.single()))
        }
    }

    @Test fun durablePointerSwitchRevokesDevSearchAndEmitsLiveInvalidation() = runBlocking {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val chat = ConversationId("dev-search-pointer")
            assertTrue(session.selectionPort.createAndSelect(
                chat, 1000L, IdempotencyKey("dev-search-pointer-create"),
            ) is ConversationSelectionResult.Saved)
            val hit = session.searchReadPort.search("nuevo").hits.single()
            val observed = mutableListOf<Unit>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                session.searchReadPort.invalidations.take(2).collect { observed.add(it) }
            }
            assertEquals(1, observed.size)
            val previous = StorageGenerationId("v2-core-dev-workspace-1")
            val switched = FileActiveGenerationPointer(root).compareAndSet(
                previous, StorageGenerationId("dev-search-test-stale-pointer"),
            )
            assertNotNull(switched)
            collector.join()
            assertEquals(2, observed.size)
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchCoverage.UNAVAILABLE,
                session.searchReadPort.search("nuevo").coverage)
            assertEquals(ar.com.faro.v5.surface.FaroV5SearchOpenResult.UNAVAILABLE,
                session.searchReadPort.openAuthorized(hit))
        }
    }
}
