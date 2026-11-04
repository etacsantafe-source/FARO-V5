package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceProjector
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSnapshot
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5ShellNavigationContractTest {
    @Test
    fun rootsAreExactOrderedAndChatFirst() {
        assertEquals(
            listOf(
                FaroV5ShellDestination.CHAT,
                FaroV5ShellDestination.INICIO,
                FaroV5ShellDestination.FINANZAS,
                FaroV5ShellDestination.OBJETIVOS,
                FaroV5ShellDestination.ASUNTOS,
                FaroV5ShellDestination.SMART_COMPRA,
                FaroV5ShellDestination.AJUSTES,
            ),
            faroV5ShellRoots,
        )
        assertEquals(FaroV5ShellDestination.CHAT, faroV5ShellRoots.first())
        assertFalse(
            FaroV5ShellDestination.entries.any {
                it.name.equals("RADAR", ignoreCase = true)
            },
        )
    }

    @Test
    fun drawerKeepsSettingsAsRootButPlacesItInFooter() {
        assertEquals(
            listOf(
                FaroV5ShellDestination.INICIO,
                FaroV5ShellDestination.FINANZAS,
                FaroV5ShellDestination.OBJETIVOS,
                FaroV5ShellDestination.ASUNTOS,
                FaroV5ShellDestination.SMART_COMPRA,
            ),
            faroV5PrimaryDrawerRoots,
        )
        assertTrue(FaroV5ShellDestination.AJUSTES in faroV5ShellRoots)
        assertFalse(FaroV5ShellDestination.AJUSTES in faroV5PrimaryDrawerRoots)
        assertFalse(FaroV5ShellDestination.CHAT in faroV5PrimaryDrawerRoots)
    }

    @Test
    fun searchAndNoticesAreSecondaryNotRoots() {
        assertEquals(
            setOf(
                FaroV5ShellDestination.BUSCAR,
                FaroV5ShellDestination.GUARDADOS,
                FaroV5ShellDestination.ACTIVIDAD,
                FaroV5ShellDestination.AVISOS,
            ),
            faroV5SecondaryShellDestinations,
        )
        assertEquals(
            listOf(
                FaroV5ShellDestination.BUSCAR,
                FaroV5ShellDestination.GUARDADOS,
                FaroV5ShellDestination.ACTIVIDAD,
                FaroV5ShellDestination.AVISOS,
            ),
            faroV5DrawerSecondaryDestinations,
        )
        assertTrue(
            faroV5SecondaryShellDestinations.all {
                it.kind == FaroV5ShellDestinationKind.SECONDARY
            },
        )
        assertTrue(
            faroV5ShellRoots.all {
                it.kind == FaroV5ShellDestinationKind.ROOT
            },
        )
    }

    @Test
    fun rootNavigationReplacesRatherThanStacksRoots() {
        val current =
            listOf(
                FaroV5ShellDestination.CHAT,
                FaroV5ShellDestination.BUSCAR,
            )

        assertEquals(
            listOf(FaroV5ShellDestination.FINANZAS),
            faroV5NextShellStack(
                current = current,
                target = FaroV5ShellDestination.FINANZAS,
            ),
        )
    }

    @Test
    fun secondaryNavigationPreservesOneRootAndReplacesPriorSecondary() {
        val current =
            listOf(
                FaroV5ShellDestination.ASUNTOS,
                FaroV5ShellDestination.BUSCAR,
            )

        assertEquals(
            listOf(
                FaroV5ShellDestination.ASUNTOS,
                FaroV5ShellDestination.AVISOS,
            ),
            faroV5NextShellStack(
                current = current,
                target = FaroV5ShellDestination.AVISOS,
            ),
        )
    }

    @Test
    fun secondaryBackReturnsToRootAndRootBackIsStable() {
        assertEquals(
            listOf(FaroV5ShellDestination.SMART_COMPRA),
            faroV5BackShellStack(
                listOf(
                    FaroV5ShellDestination.SMART_COMPRA,
                    FaroV5ShellDestination.AVISOS,
                ),
            ),
        )
        assertEquals(
            listOf(FaroV5ShellDestination.CHAT),
            faroV5BackShellStack(
                listOf(FaroV5ShellDestination.CHAT),
            ),
        )
    }

    @Test
    fun malformedSecondaryOnlyStackFailsClosedToChatRoot() {
        assertEquals(
            listOf(
                FaroV5ShellDestination.CHAT,
                FaroV5ShellDestination.BUSCAR,
            ),
            faroV5NextShellStack(
                current = listOf(FaroV5ShellDestination.AVISOS),
                target = FaroV5ShellDestination.BUSCAR,
            ),
        )
        assertEquals(
            listOf(FaroV5ShellDestination.CHAT),
            faroV5BackShellStack(
                listOf(
                    FaroV5ShellDestination.AVISOS,
                    FaroV5ShellDestination.BUSCAR,
                ),
            ),
        )
    }
    @Test
    fun adaptiveDrawerPolicyUsesModalBelowExpandedAndPermanentFromExpanded() {
        assertEquals(
            FaroV5DrawerMode.MODAL,
            faroV5DrawerModeForWindowClassMinWidthDp(600),
        )
        assertEquals(
            FaroV5DrawerMode.PERMANENT,
            faroV5DrawerModeForWindowClassMinWidthDp(840),
        )
    }

    @Test
    fun savedPresentationStateRestoresValidContextAndFailsClosedWhenMalformed() {
        assertEquals(
            listOf(
                FaroV5ShellDestination.FINANZAS,
                FaroV5ShellDestination.BUSCAR,
            ),
            faroV5RestoreShellStack(listOf("FINANZAS", "BUSCAR")),
        )
        assertEquals(
            listOf(FaroV5ShellDestination.CHAT),
            faroV5RestoreShellStack(listOf("BUSCAR")),
        )
        assertEquals(
            listOf(FaroV5ShellDestination.CHAT),
            faroV5RestoreShellStack(listOf("INICIO", "FINANZAS")),
        )
        assertEquals(
            listOf(FaroV5ShellDestination.CHAT),
            faroV5RestoreShellStack(listOf("INICIO", "BUSCAR", "AVISOS")),
        )
    }


    @Test
    fun confirmedSearchCannotNavigateUsingCachedWorkspaceWithoutFreshRead() {
        val staleConversation = ConversationId("cached-chat-before-search")
        val selectedConversation = ConversationId("search-selected-chat")
        val entries = listOf(
            ConversationWorkspaceEntry(staleConversation, "Anterior", 50L),
            ConversationWorkspaceEntry(selectedConversation, "Actual", 100L),
        )
        val previouslyCached = FaroV5WorkspaceReadState.Ready(
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(entries, staleConversation)
            )
        )
        // The shell MUST discard this cached state before requesting a new
        // canonical snapshot, never reuse it after a Search CONFIRMED event.
        assertEquals(FaroV5ShellDestination.CHAT,
            faroV5SearchConfirmedDestination(previouslyCached))
        assertNull(faroV5SearchConfirmedDestination(FaroV5WorkspaceReadState.Loading))
        val reread = FaroV5WorkspaceReadState.Ready(
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(entries, selectedConversation)
            )
        )
        assertEquals(selectedConversation, reread.projection.activeConversationId)
        assertEquals(FaroV5ShellDestination.CHAT,
            faroV5SearchConfirmedDestination(reread))
    }

    @Test
    fun failedUnboundOrNoSelectionCannotNavigateAfterSearch() {
        assertNull(faroV5SearchConfirmedDestination(FaroV5WorkspaceReadState.Failed))
        assertNull(faroV5SearchConfirmedDestination(FaroV5WorkspaceReadState.Unbound))
        assertNull(faroV5SearchConfirmedDestination(FaroV5WorkspaceReadState.Loading))
        val noActive = FaroV5WorkspaceReadState.Ready(
            ConversationWorkspaceProjector.project(
                ConversationWorkspaceSnapshot(
                    listOf(ConversationWorkspaceEntry(ConversationId("only"), "Archivado", 10L, archived = true)),
                    ConversationId("only"),
                )
            )
        )
        assertNull(faroV5SearchConfirmedDestination(noActive))
    }

}
