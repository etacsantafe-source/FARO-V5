package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.PromptArtifact
import ar.com.faro.v5.kernel.contracts.PromptId
import ar.com.faro.v5.kernel.contracts.PromptRef
import ar.com.faro.v5.kernel.contracts.PromptVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImmutablePromptRegistryTest {
    @Test
    fun `registry resolves only exact pinned version`() {
        val v1 = artifact("conversation.system", 1, "v1")
        val v2 = artifact("conversation.system", 2, "v2")
        val registry = ImmutablePromptRegistry.of(listOf(v2, v1))

        assertEquals(v1, registry.resolve(v1.ref))
        assertEquals(v2, registry.resolve(v2.ref))
        assertNull(
            registry.resolve(
                PromptRef(
                    promptId = PromptId("conversation.system"),
                    version = PromptVersion(3),
                )
            )
        )
        assertEquals(
            listOf(PromptVersion(1), PromptVersion(2)),
            registry.versions(PromptId("conversation.system")),
        )
    }

    @Test
    fun `duplicate prompt ref fails closed even when content is equal`() {
        val artifact = artifact("conversation.system", 1, "same")

        assertThrows(IllegalArgumentException::class.java) {
            ImmutablePromptRegistry.of(listOf(artifact, artifact))
        }
    }

    @Test
    fun `registry snapshots source collection`() {
        val artifact = artifact("conversation.system", 1, "stable")
        val source = mutableListOf(artifact)
        val registry = ImmutablePromptRegistry.of(source)
        source.clear()

        assertEquals(artifact, registry.resolve(artifact.ref))
    }

    private fun artifact(
        id: String,
        version: Int,
        text: String,
    ): PromptArtifact =
        PromptArtifact(
            ref = PromptRef(
                promptId = PromptId(id),
                version = PromptVersion(version),
            ),
            content = CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = text.encodeToByteArray(),
            ),
        )
}
