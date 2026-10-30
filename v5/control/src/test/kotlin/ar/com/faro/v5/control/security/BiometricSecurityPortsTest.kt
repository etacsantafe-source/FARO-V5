package ar.com.faro.v5.control.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BiometricSecurityPortsTest {
    @Test
    fun promptCopyIsTrimmedAndBounded() {
        val prompt = BiometricPromptSpec("  Desbloquear FARO  ", "  Confirmá para continuar  ", "  Usar PIN  ")
        assertEquals("Desbloquear FARO", prompt.title())
        assertEquals("Confirmá para continuar", prompt.subtitle())
        assertEquals("Usar PIN", prompt.negativeActionLabel())
    }

    @Test
    fun titleAndNegativeActionCannotBeBlank() {
        assertThrows(IllegalArgumentException::class.java) { BiometricPromptSpec("   ", "", "Usar PIN") }
        assertThrows(IllegalArgumentException::class.java) { BiometricPromptSpec("Desbloquear FARO", "", "   ") }
    }

    @Test
    fun promptCopyFailsClosedWhenBoundsAreExceeded() {
        assertThrows(IllegalArgumentException::class.java) {
            BiometricPromptSpec("x".repeat(BiometricPromptSpec.MAX_TITLE_CHARS + 1), "", "Usar PIN")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BiometricPromptSpec("Desbloquear FARO", "x".repeat(BiometricPromptSpec.MAX_SUBTITLE_CHARS + 1), "Usar PIN")
        }
    }

    @Test
    fun noOpAttemptIsSafeToCancel() {
        BiometricReauthenticationPort.Attempt.noOp().cancel()
    }
}
