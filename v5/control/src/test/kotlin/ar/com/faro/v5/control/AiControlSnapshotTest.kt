package ar.com.faro.v5.control

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiControlSnapshotTest {
    @Test
    fun `control snapshot keeps only opaque credential references`() {
        val snapshot = AiControlSnapshot(
            remoteReasoningEnabled = true,
            activeProfile = profile(),
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 100,
            ),
            credentialRefs = listOf(
                AiBindingCredentialRef(
                    bindingId = ModelBindingId("remote-fast"),
                    secretId = SecretId("ai.remote.primary"),
                )
            ),
            revision = 3,
        )

        assertEquals(
            SecretId("ai.remote.primary"),
            snapshot.credentialRefFor(ModelBindingId("remote-fast")),
        )
        assertNull(snapshot.credentialRefFor(ModelBindingId("local")))
        assertEquals(ModelProfileVersion(2), snapshot.activeProfile.version)
    }

    @Test
    fun `duplicate credential ref for same binding fails closed`() {
        val first = AiBindingCredentialRef(
            bindingId = ModelBindingId("remote-fast"),
            secretId = SecretId("ai.remote.primary"),
        )
        val second = AiBindingCredentialRef(
            bindingId = ModelBindingId("remote-fast"),
            secretId = SecretId("ai.remote.secondary"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiControlSnapshot(
                remoteReasoningEnabled = true,
                activeProfile = profile(),
                budget = AiMonetaryBudget.Disabled,
                credentialRefs = listOf(first, second),
                revision = 0,
            )
        }
    }

    @Test
    fun `control revision cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiControlSnapshot(
                remoteReasoningEnabled = false,
                activeProfile = profile(),
                budget = AiMonetaryBudget.Disabled,
                credentialRefs = emptyList(),
                revision = -1,
            )
        }
    }

    private fun profile(): ModelProfileRef =
        ModelProfileRef(
            profileId = ModelProfileId("conversation"),
            version = ModelProfileVersion(2),
        )
}
