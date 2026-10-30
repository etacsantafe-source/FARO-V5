package ar.com.faro.v5.control.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VoicePortsTest {
    @Test
    fun voiceRequestIsBoundedAndNormalizesLanguage() {
        val request = VoiceInputRequest("  Hablar con FARO  ", "es-ar", 500)
        assertEquals("Hablar con FARO", request.prompt())
        assertEquals("es-AR", request.languageTag())
        assertEquals(500, request.maxTranscriptChars())
    }

    @Test
    fun invalidVoiceRequestFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceInputRequest("x".repeat(VoiceInputRequest.MAX_PROMPT_CHARS + 1), null, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoiceInputRequest("", null, VoiceInputRequest.MAX_TRANSCRIPT_CHARS + 1)
        }
    }

    @Test
    fun transcriptResultDoesNotExposeTextForNonTranscriptStates() {
        val transcript = VoiceInputPort.Result.transcript("  hola FARO  ")
        assertEquals(VoiceInputPort.Kind.TRANSCRIPT, transcript.kind())
        assertEquals("hola FARO", transcript.transcript())

        val cancelled = VoiceInputPort.Result.cancelled()
        assertEquals(VoiceInputPort.Kind.CANCELLED, cancelled.kind())
        assertThrows(IllegalStateException::class.java) { cancelled.transcript() }
    }

    @Test
    fun readAloudRequestIsBoundedAndNormalizesLanguage() {
        val request = ReadAloudRequest("  texto para leer  ", "es-ar")
        assertEquals("texto para leer", request.text())
        assertEquals("es-AR", request.languageTag())
        assertThrows(IllegalArgumentException::class.java) {
            ReadAloudRequest("x".repeat(ReadAloudRequest.MAX_TEXT_CHARS + 1), "es-AR")
        }
    }
}
