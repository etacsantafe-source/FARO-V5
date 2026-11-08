package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * S3.A5 NEW INTEGRATION: cross-owner sessions after real DEV Reality7->8 cutover.
 * No duplicated stage/cutover tests, no phone, network, synthetic assistant or Apply.
 */
class FaroV5S3A5PostCutoverIntegrationTest {
    @TempDir lateinit var root: Path
    private val seven = StorageGenerationId("v2-core-dev-workspace-1")
    private val eight = StorageGenerationId("v2-core-dev-planning8-1")
    private val op = MaintenanceOperationId("v2-core-dev-planning8-upgrade-1")
    private val pointer get() = FileActiveGenerationPointer(root)

    private fun promoteToEight() {
        FaroV5V2CoreDevAuthority(root).open().use { }
        FaroV5V2CoreDevAuthority(root)
            .upgradePlanning8AfterExplicitForegroundRequest { true }.use { }
        assertEquals(eight, pointer.read()!!.generationId)
    }

    private fun sendUserMessage(
        session: FaroV5V2CoreDevAuthority.Session,
        id: String,
        body: String,
    ) = runBlocking {
        val chat = ConversationId(id)
        assertTrue(session.selectionPort.createAndSelect(
            chat, 1000L, IdempotencyKey("$id-create"),
        ) is ConversationSelectionResult.Saved)
        val draft = checkNotNull(session.composerPort.read(chat))
        val saved = session.composerPort.save(ConversationDraftSaveRequest(
            chat, draft.authorityToken, draft.revision, body,
            IdempotencyKey("$id-draft"),
        )) as ConversationDraftWriteResult.Saved
        val result = session.composerPort.sendDraft(ConversationDraftSendRequest(
            ConversationDraftSendCandidate(
                chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                saved.snapshot.text,
            ),
            ConversationMessageId("$id-msg"), ConversationTurnId("$id-turn"),
            1001L, IdempotencyKey("$id-send"),
        ))
        assertTrue(result is ConversationTimelineWriteResult.Saved)
        chat
    }

    @Test fun fourRealOwnerSearchAndDurableChatWorkTogetherOnlyAfterFreshEight() = runBlocking {
        promoteToEight()
        val active = checkNotNull(pointer.read())
        val staged = FileStorageGenerationLayout(root).requireStaged(eight)
        RealityStore(staged.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { reality ->
            assertEquals(8, reality.maintenance.inspect().schemaVersion)
            reality.run(TransactionId("a5-integrated-eight-local-fixture")) { scope ->
                val account = FinanceAccountId.of("private-a5-account")
                reality.financePersistence.createAccount(FinanceAccountCreate(
                    account, FinanceAccountDisplayName.of("Cuenta A5"),
                    FinanceCurrencyCode.of("ARS"), FinanceAccountNature.BANK,
                ), scope)
                reality.financePersistence.recordEntry(FinanceEntryRecord(
                    entryId = FinanceEntryId.of("private-a5-entry"),
                    accountId = account,
                    amountMinor = 987654L,
                    kind = FinanceEntryKind.EXPENSE,
                    concept = FinanceEntryConcept.of("A5integrado Finanzas"),
                    category = FinanceEntryCategory.of("Pruebas"),
                    effectiveAtEpochMillis = 1000L, recordedAtEpochMillis = 2000L,
                ), scope)
                reality.objectivePersistence.upsert(ObjectiveRecord(
                    ObjectiveId.of("private-a5-objective"),
                    ObjectiveTitle.of("A5integrado Objetivos"),
                    ObjectiveDetail.of("Objetivo local Reality8"),
                    ObjectiveStatus.ACTIVE, 3000L,
                ), scope)
                reality.matterPersistence.upsert(MatterRecord(
                    MatterId.of("private-a5-matter"),
                    MatterTitle.of("A5integrado Asuntos"),
                    MatterDetail.of("Asunto local Reality8"),
                    MatterStatus.WAITING, 4000L, 3500L,
                ), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
        val chat = ConversationId("a5-integrated-eight-chat")
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            sendUserMessage(session, chat.value, "Mensaje A5integrado Chat local")
            val page = session.searchReadPort.search("A5integrado")
            assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
            assertEquals(4, page.hits.size)
            val domains = page.hits.filter { it.namespace in setOf(
                FaroV5FinanceSearchReadSource.NAMESPACE,
                FaroV5ObjectivesSearchReadSource.NAMESPACE,
                FaroV5MattersSearchReadSource.NAMESPACE,
            ) }
            assertEquals(3, domains.size)
            assertEquals(3, domains.map { it.namespace }.toSet().size)
            domains.forEach { hit ->
                assertEquals(FaroV5SearchOpenResult.CONFIRMED,
                    session.searchReadPort.openAuthorized(hit))
                val target = checkNotNull(session.searchReadPort.takeConfirmedDomainTarget())
                assertEquals(hit.canonicalSource, target.canonicalSource)
                assertFalse(target.summary.contains("987654"))
                assertFalse(target.summary.contains("private-a5-account"))
                assertNull(session.searchReadPort.takeConfirmedDomainTarget())
            }
        }
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            assertEquals(active, pointer.read(), "ordinary reopening must not cutover again")
            val result = reopened.searchReadPort.search("A5integrado")
            assertEquals(FaroV5SearchCoverage.COMPLETE, result.coverage)
            assertEquals(4, result.hits.size)
            assertEquals(1, runBlocking {
                reopened.timelineReadPort.readTimeline(chat)
            }!!.messages.size)
        }
    }

    @Test fun oldSevenSearchHitAndContentCannotAppearInFreshEight() = runBlocking {
        val old = FaroV5V2CoreDevAuthority(root).open()
        val chat = sendUserMessage(old, "a5-private-old-chat", "Old7OnlyPrivateA5")
        val oldPort = old.searchReadPort
        val oldPage = oldPort.search("Old7OnlyPrivateA5")
        assertEquals(FaroV5SearchCoverage.COMPLETE, oldPage.coverage)
        assertEquals(1, oldPage.hits.size)
        val oldHit = oldPage.hits.single()
        val before = checkNotNull(pointer.read())
        val oldDb = FileStorageGenerationLayout(root).requireStaged(seven)
            .directory.resolve(GenerationStoreFiles.WORKSPACE)
        val originalBytes = Files.readAllBytes(oldDb)
        old.close()
        FaroV5V2CoreDevAuthority(root)
            .upgradePlanning8AfterExplicitForegroundRequest { true }.use { newSession ->
                assertEquals(before.sequence + 1L, pointer.read()!!.sequence)
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                    oldPort.openAuthorized(oldHit))
                assertEquals(FaroV5SearchCoverage.UNAVAILABLE,
                    oldPort.search("Old7OnlyPrivateA5").coverage)
                val newPage = newSession.searchReadPort.search("Old7OnlyPrivateA5")
                assertEquals(FaroV5SearchCoverage.COMPLETE, newPage.coverage)
                assertTrue(newPage.hits.isEmpty())
                assertNull(runBlocking { newSession.timelineReadPort.readTimeline(chat) })
                assertArrayEquals(originalBytes, Files.readAllBytes(oldDb),
                    "cutover must not modify old7 workspace bytes")
            }
    }

    @Test fun committedEightCorruptionFailsClosedWithoutRollbackOrRebuild() {
        promoteToEight()
        val active = checkNotNull(pointer.read())
        val path = FileStorageGenerationLayout(root).requireStaged(eight)
            .directory.resolve(GenerationStoreFiles.REALITY)
        val damaged = byteArrayOf(0x11, 0x13, 0x17, 0x19, 0x23)
        Files.write(path, damaged)
        assertThrows(Exception::class.java) { FaroV5V2CoreDevAuthority(root).open() }
        assertEquals(active, pointer.read(), "a completed pointer cannot roll back silently")
        assertArrayEquals(damaged, Files.readAllBytes(path),
            "corrupted active8 must not be auto-rebuilt")
        assertEquals(BootstrapPhase.COMPLETED, FileBootstrapJournal(root).read(op)!!.phase)
        assertTrue(FileGenerationReplayFence(root).isInstalled(
            MaintenanceOperationKind.RESTORE, eight))
    }

    @Test fun duplicateUpgradeIsDeniedWithoutChangingCommittedEight() {
        promoteToEight()
        val original = checkNotNull(pointer.read())
        val entry = checkNotNull(FileBootstrapJournal(root).read(op))
        assertThrows(IllegalStateException::class.java) {
            FaroV5V2CoreDevAuthority(root)
                .upgradePlanning8AfterExplicitForegroundRequest { true }
        }
        assertEquals(original, pointer.read())
        assertEquals(entry, FileBootstrapJournal(root).read(op))
        FaroV5V2CoreDevAuthority(root).open().use {
            assertEquals(original, pointer.read())
        }
    }

    @Test fun pointerAbaOfActiveEightRevokesChatSearchAndOldHit() = runBlocking {
        promoteToEight()
        val before = checkNotNull(pointer.read())
        val session = FaroV5V2CoreDevAuthority(root).open()
        val chat = sendUserMessage(session, "a5-eight-aba-chat", "A5EightAbaToken")
        val oldWorkspace = session.workspaceReadPort
        val oldSearch = session.searchReadPort
        val hit = oldSearch.search("A5EightAbaToken").hits.single()
        val foreign = StorageGenerationId("a5-eight-aba-foreign")
        try {
            checkNotNull(pointer.compareAndSet(eight, foreign))
            checkNotNull(pointer.compareAndSet(foreign, eight))
            assertNotEquals(before, pointer.read())
            assertThrows(IllegalStateException::class.java) {
                runBlocking { oldWorkspace.readSnapshot() }
            }
            assertEquals(FaroV5SearchCoverage.UNAVAILABLE,
                oldSearch.search("A5EightAbaToken").coverage)
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                oldSearch.openAuthorized(hit))
            assertThrows(Exception::class.java) {
                FaroV5V2CoreDevAuthority(root).open()
            }
        } finally {
            session.close()
        }
    }
}
