package ar.com.faro.v5.surface

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
                FaroV5ShellDestination.CHAT,
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
    }

    @Test
    fun searchAndNoticesAreSecondaryNotRoots() {
        assertEquals(
            setOf(
                FaroV5ShellDestination.BUSCAR,
                FaroV5ShellDestination.AVISOS,
            ),
            faroV5SecondaryShellDestinations,
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

}
