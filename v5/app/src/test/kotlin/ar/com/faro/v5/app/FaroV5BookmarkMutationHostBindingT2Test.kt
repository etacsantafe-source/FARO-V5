package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Real SQLite owners; sandbox/foreground facts simulated, not native Android gesture proof. */
class FaroV5BookmarkMutationHostBindingT2Test {
    @TempDir lateinit var root: Path
    private inner class Case(label: String, missingTarget: Boolean = false) : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root.resolve(label + "-pointer"))
        val pinned = requireNotNull(pointer.compareAndSet(null, StorageGenerationId("gen-" + label)))
        val workspace = ConversationWorkspaceStore(root.resolve(label + "-workspace.db").toString())
        val chat = ConversationId("chat-" + label)
        val message = ConversationMessageId("message-" + label)
        var foreground = true
        var hostReady = true
        var hostEpoch: String? = "host-a"
        var onForeground: () -> Boolean = { foreground }
        var afterWrite: () -> Unit = {}
        val token get() = workspace.bookmarkReadAdmission().authorityToken
        val authority: FaroV5LocalInstallReadAuthority
        val principal: String
        val auxiliary: AuxiliaryDeliveryStore
        init {
            runBlocking {
                workspace.createAndSelect(chat, 1, IdempotencyKey("create-" + label))
                val before = requireNotNull(workspace.readTimeline(chat))
                assertTrue(workspace.writeTimeline(ConversationTimelineWriteRequest(
                    chat, before.authorityToken, before.revision, IdempotencyKey("message-" + label),
                    ConversationTimelineIntent.AcceptUser(ConversationMessage(
                        message, chat, ConversationTurnId("turn-" + label),
                        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                        "private source body " + label, 1,
                    )),
                )) is ConversationTimelineWriteResult.Saved)
            }
            val path = root.resolve(label + "-auxiliary.db").toString()
            // Fixture-only seed writer, closed BEFORE the actual default-deny host owner opens.
            val seed = object : BookmarkAuthorityGate {
                override fun isScopeActive(generationId: String, authorityToken: String) = true
                override fun isTargetAvailable(target: BookmarkTarget) = true
                override fun isMutationActive(generationId: String, authorityToken: String) = true
            }
            AuxiliaryDeliveryStore(path, bookmarkAuthorityGate = seed).use { setup ->
                runBlocking {
                    assertEquals(BookmarkMutationResult.Saved(1, false), setup.bookmarks.mutate(
                        BookmarkMutationRequest(pinned.generationId.value, token, 0, IdempotencyKey("folder-" + label),
                            BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("folder"), "Carpeta local")))))
                    assertEquals(BookmarkMutationResult.Saved(2, false), setup.bookmarks.mutate(
                        BookmarkMutationRequest(pinned.generationId.value, token, 1, IdempotencyKey("saved-" + label),
                            BookmarkMutation.Add(BookmarkMetadata(BookmarkId("saved"),
                                BookmarkTarget(chat, if (missingTarget) ConversationMessageId("missing") else message,
                                    pinned.generationId.value, token), BookmarkFolderId("folder"), true)))))
                }
            }
            auxiliary = AuxiliaryDeliveryStore(path, materialWriteGate = object : MaterialWriteGate {
                override fun <T> withMaterialWrite(block: () -> T): T {
                    val result = block()
                    afterWrite() // Deterministic owner-return boundary AFTER the SQL COMMIT.
                    return result
                }
                override fun <T> withQuiescedMaterialWrites(block: () -> T): T = block()
            })
            val sandbox = FaroV5AndroidSandboxPrincipal(
                expectedApplicationId = "ar.com.faro.alpha.v2coredev",
                pinned = pinned, activePointer = pointer,
                expectedHostEpoch = "host-a", hostEpochOrNull = { hostEpoch },
                facts = AndroidSandboxFactsReadPort {
                    AndroidSandboxProcessFacts(10200, 10200, true, false, false, "ar.com.faro.alpha.v2coredev")
                },
            )
            authority = FaroV5LocalInstallReadAuthority(
                sandbox, pointer, { hostReady }, { onForeground() },
            )
            principal = requireNotNull(authority.technicalPrincipalOrNull())
        }
        fun grant(domains: Set<FaroV5LocalReadDomain> = setOf(
            FaroV5LocalReadDomain.BOOKMARK_METADATA, FaroV5LocalReadDomain.WORKSPACE_MESSAGE,
        )) = authority.grantAfterTrustedForegroundReadAction(domains)
        val mutationAuthority by lazy {
            FaroV5LocalInstallBookmarkMutationAuthority(FaroV5AndroidSandboxPrincipal(
                expectedApplicationId = "ar.com.faro.alpha.v2coredev", pinned = pinned, activePointer = pointer,
                expectedHostEpoch = "host-a", hostEpochOrNull = { hostEpoch }, facts = AndroidSandboxFactsReadPort {
                    AndroidSandboxProcessFacts(10200, 10200, true, false, false, "ar.com.faro.alpha.v2coredev")
                }), pointer, { hostReady }, { onForeground() })
        }
        val mutationPrincipal get() = requireNotNull(mutationAuthority.technicalPrincipalOrNull())
        fun target() = BookmarkTarget(chat, message, pinned.generationId.value, token)
        fun request() = BookmarkMutationRequest(pinned.generationId.value, token, 2,
            IdempotencyKey("favorite"), BookmarkMutation.FavoriteSet(BookmarkId("saved"), false))
        fun consent(r: BookmarkMutationRequest = request(), t: BookmarkTarget? = target()) =
            mutationAuthority.grantAfterTrustedForegroundMutationAction(r, t)
        fun host(access: FaroV5LocalInstallBookmarkMutationAuthority? = mutationAuthority) = FaroV5BookmarkMutationHostBinding(
            pinned, pointer, workspace, auxiliary, mutationPrincipal, "host-a", { hostEpoch }, { hostReady }, access)
        override fun close() {
            authority.close()
            runCatching { auxiliary.close() }
            runCatching { workspace.close() }
        }
    }

    @Test fun absentGrantAndReadOnlyConsentCannotWrite() {
        Case("deny").use { f ->
            val r = f.request(); val t = f.target(); val before = f.auxiliary.backup.snapshot()
            f.grant()
            f.host(null).use { assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { it.mutateAuthorized(r, t) }) }
            f.host().use { assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { it.mutateAuthorized(r, t) }) }
            assertEquals(before, f.auxiliary.backup.snapshot())
        }
    }
    @Test fun exactConsentUsesRealOwnerReceiptAndReplayAndCannotMutateOtherId() {
        Case("real").use { f ->
            val r = f.request(); val t = f.target(); val workspaceBefore = f.workspace.backup.snapshot()
            assertTrue(f.consent(r,t))
            f.host().use { host ->
                val saved = assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java, runBlocking { host.mutateAuthorized(r,t) })
                assertEquals(3L, saved.revision); assertFalse(saved.replayed); assertTrue(host.isResultCurrent(saved))
                val replay = assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java, runBlocking { host.mutateAuthorized(r,t) })
                assertTrue(replay.replayed); assertFalse(host.isResultCurrent(saved))
                assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { host.mutateAuthorized(
                    r.copy(mutation = BookmarkMutation.FavoriteSet(BookmarkId("foreign"),true)),t) })
                assertEquals(workspaceBefore, f.workspace.backup.snapshot())
                f.mutationAuthority.revokeAll()
                assertFalse(host.isResultCurrent(replay))
                assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { host.mutateAuthorized(r,t) })
                assertTrue(f.consent(r,t))
                assertFalse(host.isResultCurrent(replay))
                assertTrue(assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java,
                    runBlocking { host.mutateAuthorized(r,t) }).replayed)
            }
        }
    }
    @Test fun fakeConsentSourceIsNotAcceptedForPersistedId() {
        Case("foreign").use { f ->
            val r = f.request(); val t = f.target().copy(messageId = ConversationMessageId("other"))
            assertTrue(f.consent(r,t))
            val before = f.auxiliary.backup.snapshot()
            f.host().use { assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { it.mutateAuthorized(r,t) }) }
            assertEquals(before, f.auxiliary.backup.snapshot())
        }
    }
    @Test fun delayedSuccessIsRejectedAfterWorkspaceChangePauseHostCloseOrRegrant() {
        Case("publish").use { f ->
            val r = f.request(); val t = f.target(); f.consent(r,t)
            val host = f.host()
            val saved = assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java, runBlocking { host.mutateAuthorized(r,t) })
            assertTrue(host.isResultCurrent(saved))
            f.foreground = false
            assertFalse(host.isResultCurrent(saved))
            f.foreground = true
            assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { host.mutateAuthorized(r,t) })
            f.consent(r,t)
            assertFalse(host.isResultCurrent(saved))
            host.close()
            assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { host.mutateAuthorized(r,t) })
        }
        Case("workspace-revision").use { f ->
            val r = f.request(); val t = f.target(); f.consent(r,t)
            f.host().use { host ->
                val saved = assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java, runBlocking { host.mutateAuthorized(r,t) })
                runBlocking { f.workspace.createAndSelect(ConversationId("new-chat"), 3, IdempotencyKey("new-chat")) }
                assertFalse(host.isResultCurrent(saved))
            }
        }
    }
    @Test fun revokedDuringMutationRollsBackWithoutAnnouncingSuccess() {
        Case("revoke").use { f ->
            val r = f.request(); val t = f.target(); f.consent(r,t)
            val host = f.host(); val before = f.auxiliary.backup.snapshot()
            var checks = 0
            f.onForeground = { if (++checks == 5) f.mutationAuthority.revokeAll(); true }
            val result = runBlocking { host.mutateAuthorized(r,t) }
            assertFalse(result is FaroV5BookmarkMutationOutcome.Saved)
            assertEquals(before, f.auxiliary.backup.snapshot()); host.close()
        }
    }
    @Test fun postCommitRevokeIsUnknownAndFreshConsentReconcilesWithOneReceipt() {
        Case("after-commit").use { f ->
            val r = f.request(); val t = f.target(); f.consent(r,t)
            f.host().use { host ->
                f.afterWrite = { f.mutationAuthority.revokeAll() }
                assertEquals(FaroV5BookmarkMutationOutcome.UnknownReconcile, runBlocking { host.mutateAuthorized(r,t) })
                val committed = f.auxiliary.backup.snapshot()
                assertEquals(3L, committed.bookmarks!!.scope!!.revision)
                assertEquals(3, committed.bookmarks!!.receipts.size)
                f.afterWrite = {}
                assertTrue(f.consent(r,t))
                val replay = assertInstanceOf(FaroV5BookmarkMutationOutcome.Saved::class.java,
                    runBlocking { host.mutateAuthorized(r,t) })
                assertTrue(replay.replayed); assertEquals(3L,replay.revision)
                assertEquals(committed,f.auxiliary.backup.snapshot())
            }
        }
    }
    @Test fun closedOwnersAndAbaCannotPublishOrWrite() {
        Case("aux-close").use { f ->
            val r = f.request(); val t = f.target(); f.consent(r,t)
            f.host().use { host ->
                f.auxiliary.close()
                assertFalse(runBlocking { host.mutateAuthorized(r,t) } is FaroV5BookmarkMutationOutcome.Saved)
            }
        }
        Case("aba").use { f ->
            val r = f.request(); val t = f.target(); f.consent(r,t)
            f.host().use { host ->
                val other = StorageGenerationId("other")
                f.pointer.compareAndSet(f.pinned.generationId,other); f.pointer.compareAndSet(other,f.pinned.generationId)
                assertEquals(FaroV5BookmarkMutationOutcome.Unavailable, runBlocking { host.mutateAuthorized(r,t) })
            }
        }
    }
}
