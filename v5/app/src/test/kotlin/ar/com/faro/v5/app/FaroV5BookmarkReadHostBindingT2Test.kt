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
class FaroV5BookmarkReadHostBindingT2Test {
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
            auxiliary = AuxiliaryDeliveryStore(path)
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
        fun host(access: FaroV5LocalInstallReadAuthority? = authority) = FaroV5BookmarkReadHostBinding(
            pinned, pointer, workspace, auxiliary, principal, "host-a",
            { hostEpoch }, { hostReady }, access,
        )
        override fun close() {
            authority.close()
            runCatching { auxiliary.close() }
            runCatching { workspace.close() }
        }
    }

    @Test fun missingPolicyAndEachIndependentGrantDenyInsteadOfEmpty() {
        Case("deny").use { f ->
            f.host(null).use { assertNull(runBlocking { it.readAuthorized() }) }
            f.host().use { assertNull(runBlocking { it.readAuthorized() }) }
            assertTrue(f.grant(setOf(FaroV5LocalReadDomain.WORKSPACE_MESSAGE)))
            f.host().use { assertNull(runBlocking { it.readAuthorized() }) }
            f.authority.revokeAll()
            assertTrue(f.grant(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA)))
            f.host().use { assertNull(runBlocking { it.readAuthorized() }) }
        }
    }

    @Test fun sameOwnerReadReturnsPrivateMetadataAndCannotAcquireMutationPort() {
        Case("real").use { f ->
            assertTrue(f.grant())
            val before = f.auxiliary.backup.snapshot()
            val workspaceBefore = f.workspace.backup.snapshot()
            f.host().use { host ->
                val page = requireNotNull(runBlocking { host.readAuthorized() })
                assertEquals(1, page.items.size)
                assertEquals(f.chat, page.items.single().conversationId)
                assertEquals(f.message, page.items.single().messageId)
                assertTrue(page.items.single().favorite)
                assertEquals("Carpeta local", page.folders.single().name)
                assertFalse(page.hasMore)
                assertFalse(page.toString().contains(f.token))
                assertFalse(page.toString().contains("private source body"))
                assertTrue(host.isPageCurrent(page))
            }
            val permissive = object : BookmarkAuthorityGate {
                override fun isScopeActive(generationId: String, authorityToken: String) = true
                override fun isTargetAvailable(target: BookmarkTarget) = true
                override fun isMutationActive(generationId: String, authorityToken: String) = true
            }
            val reader = f.auxiliary.bookmarkReaderForTrustedHost(permissive)
            assertFalse(reader is BookmarkMutationPort)
            assertEquals(BookmarkMutationResult.Unavailable, runBlocking {
                f.auxiliary.bookmarks.mutate(BookmarkMutationRequest(
                    f.pinned.generationId.value, f.token, 2, IdempotencyKey("never-write"),
                    BookmarkMutation.FavoriteSet(BookmarkId("saved"), false)))
            })
            assertEquals(before, f.auxiliary.backup.snapshot())
            assertEquals(workspaceBefore, f.workspace.backup.snapshot())
        }
    }

    @Test fun delayedPageCannotPublishAfterRevokeRegrantOrPause() {
        Case("publish").use { f ->
            f.grant()
            f.host().use { host ->
                val old = requireNotNull(runBlocking { host.readAuthorized() })
                f.authority.revoke(FaroV5LocalReadDomain.BOOKMARK_METADATA)
                assertFalse(host.isPageCurrent(old))
                f.grant(setOf(FaroV5LocalReadDomain.BOOKMARK_METADATA))
                assertFalse(host.isPageCurrent(old))
                val current = requireNotNull(runBlocking { host.readAuthorized() })
                assertTrue(host.isPageCurrent(current))
                f.foreground = false
                assertFalse(host.isPageCurrent(current))
                f.foreground = true
                assertNull(runBlocking { host.readAuthorized() })
                f.grant()
                assertFalse(host.isPageCurrent(current))
            }
        }
    }

    @Test fun fullPointerAbaHostDeathAndClosedBindingNeverResurrectPage() {
        Case("aba").use { f ->
            f.grant()
            f.host().use { host ->
                val page = requireNotNull(runBlocking { host.readAuthorized() })
                val other = StorageGenerationId("other")
                requireNotNull(f.pointer.compareAndSet(f.pinned.generationId, other))
                requireNotNull(f.pointer.compareAndSet(other, f.pinned.generationId))
                assertFalse(host.isPageCurrent(page))
                assertNull(runBlocking { host.readAuthorized() })
            }
        }
        Case("host").use { f ->
            f.grant()
            val host = f.host()
            val page = requireNotNull(runBlocking { host.readAuthorized() })
            f.hostEpoch = "host-b"
            assertFalse(host.isPageCurrent(page))
            assertNull(runBlocking { host.readAuthorized() })
            host.close()
            f.hostEpoch = "host-a"
            assertNull(runBlocking { host.readAuthorized() })
        }
    }

    @Test fun absentCanonicalTargetAndClosingEitherRealOwnerDenyWholePage() {
        Case("missing", missingTarget = true).use { f ->
            f.grant()
            f.host().use { assertNull(runBlocking { it.readAuthorized() }) }
        }
        Case("workspace-close").use { f ->
            f.grant()
            f.host().use { host ->
                assertNotNull(runBlocking { host.readAuthorized() })
                f.workspace.close()
                assertNull(runBlocking { host.readAuthorized() })
            }
        }
        Case("auxiliary-close").use { f ->
            f.grant()
            f.host().use { host ->
                assertNotNull(runBlocking { host.readAuthorized() })
                f.auxiliary.close()
                assertNull(runBlocking { host.readAuthorized() })
            }
        }
    }

    @Test fun revocationDuringOwnerLookupDiscardsResultWithoutAnyWrite() {
        Case("mid-read").use { f ->
            f.grant()
            val before = f.auxiliary.backup.snapshot()
            var checks = 0
            f.onForeground = {
                // Two source epochs are admitted initially, then revocation happens
                // while the owner gate revalidates the same canonical lookup.
                if (++checks == 4) f.authority.revoke(FaroV5LocalReadDomain.BOOKMARK_METADATA)
                true
            }
            f.host().use { assertNull(runBlocking { it.readAuthorized() }) }
            assertTrue(checks >= 4)
            assertEquals(before, f.auxiliary.backup.snapshot())
        }
    }
}
