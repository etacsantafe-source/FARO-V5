package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PromptRegistryContractTest {
    @Test
    fun `prompt version must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            PromptVersion(0)
        }
    }

    @Test
    fun `prompt fingerprint changes when content changes under same ref`() {
        val ref = PromptRef(
            promptId = PromptId("conversation.system"),
            version = PromptVersion(1),
        )
        val first = PromptArtifact(ref, payload("first"))
        val second = PromptArtifact(ref, payload("second"))

        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(ref, first.ref)
    }

    private fun payload(text: String): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            formatId = "text/plain",
            bytes = text.encodeToByteArray(),
        )
}
