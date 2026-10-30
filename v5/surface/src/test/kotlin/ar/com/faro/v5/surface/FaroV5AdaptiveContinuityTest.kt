package ar.com.faro.v5.surface

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FaroV5AdaptiveContinuityTest {
    @Test
    fun compactAndMediumUseModalDrawerWhileExpandedUsesPermanentDrawer() {
        assertEquals(
            FaroV5DrawerMode.MODAL,
            faroV5DrawerModeForWindowClassMinWidthDp(0),
        )
        assertEquals(
            FaroV5DrawerMode.MODAL,
            faroV5DrawerModeForWindowClassMinWidthDp(600),
        )
        assertEquals(
            FaroV5DrawerMode.PERMANENT,
            faroV5DrawerModeForWindowClassMinWidthDp(840),
        )
        assertEquals(
            FaroV5DrawerMode.PERMANENT,
            faroV5DrawerModeForWindowClassMinWidthDp(1200),
        )
        assertEquals(
            FaroV5DrawerMode.PERMANENT,
            faroV5DrawerModeForWindowClassMinWidthDp(1600),
        )
    }

    @Test
    fun validRootAndSecondaryRestoreExactly() {
        assertEquals(
            listOf(
                FaroV5ShellDestination.FINANZAS,
                FaroV5ShellDestination.BUSCAR,
            ),
            faroV5RestoreShellStack(
                listOf("FINANZAS", "BUSCAR"),
            ),
        )
        assertEquals(
            listOf(FaroV5ShellDestination.AJUSTES),
            faroV5RestoreShellStack(
                listOf("AJUSTES"),
            ),
        )
    }

    @Test
    fun emptyUnknownOrMalformedSavedStateFailsClosedToChat() {
        val chat = listOf(FaroV5ShellDestination.CHAT)

        assertEquals(chat, faroV5RestoreShellStack(emptyList()))
        assertEquals(chat, faroV5RestoreShellStack(listOf("NO_EXISTE")))
        assertEquals(
            chat,
            faroV5RestoreShellStack(
                listOf("BUSCAR"),
            ),
        )
        assertEquals(
            chat,
            faroV5RestoreShellStack(
                listOf("INICIO", "FINANZAS"),
            ),
        )
        assertEquals(
            chat,
            faroV5RestoreShellStack(
                listOf("INICIO", "BUSCAR", "AVISOS"),
            ),
        )
    }

    @Test
    fun restoredSecondaryBackStillReturnsToRestoredRoot() {
        val restored =
            faroV5RestoreShellStack(
                listOf("ASUNTOS", "AVISOS"),
            )

        assertEquals(
            listOf(FaroV5ShellDestination.ASUNTOS),
            faroV5BackShellStack(restored),
        )
    }

    @Test
    fun drawerModeIsDerivedOnlyFromWindowClassNotNavigationState() {
        val root =
            faroV5RestoreShellStack(
                listOf("SMART_COMPRA"),
            )

        assertEquals(
            listOf(FaroV5ShellDestination.SMART_COMPRA),
            root,
        )
        assertEquals(
            FaroV5DrawerMode.MODAL,
            faroV5DrawerModeForWindowClassMinWidthDp(600),
        )
        assertEquals(
            FaroV5DrawerMode.PERMANENT,
            faroV5DrawerModeForWindowClassMinWidthDp(840),
        )
    }
}
