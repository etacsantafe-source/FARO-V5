package ar.com.faro.v5.app

import ar.com.faro.v5.control.AiBindingCredentialRef
import ar.com.faro.v5.control.AiControlSnapshot
import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.kernel.contracts.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5TrustedModelGatewayFactoryTest {
    private val credential = SecretId("ai.groq.primary")

    @Test
    fun `remote disabled never builds a gateway or reads a secret`() {
        val reads = AtomicInteger()
        val factory = FaroV5TrustedModelGatewayFactory(
            { snapshot(false) },
            SecretReader { reads.incrementAndGet(); null },
        )
        assertNull(factory.create())
        assertEquals(0, reads.get())
    }

    @Test
    fun `missing credential reference never builds remote runtime`() {
        val factory = FaroV5TrustedModelGatewayFactory(
            { snapshot(true, withCredential = false) },
            SecretReader { null },
        )
        assertNull(factory.create())
    }

    @Test
    fun `live consent revocation rejects egress and model policy without credential read`() {
        val current = AtomicReference(snapshot(true))
        val reads = AtomicInteger()
        val configured = requireNotNull(FaroV5TrustedModelGatewayFactory(
            { current.get() },
            SecretReader { reads.incrementAndGet(); null },
        ).create())
        assertTrue(configured.egressAdmission.test(user()))
        current.set(snapshot(false))
        assertFalse(configured.egressAdmission.test(user()))
        val result = configured.gateway.reason(
            ReasoningRequest(
                ModelRequestId("revoked"), FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE.profileId,
                ModelRequirements(setOf(ModelCapability.TEXT)),
                CanonicalPayload.fromEncoded("text/plain", "hola".encodeToByteArray()),
            ),
            NeverCancelled,
        )
        val failed = assertInstanceOf(ReasoningResult.Failure::class.java, result)
        assertEquals(AiFailureKind.TERMINAL_REQUEST_ERROR, failed.failure.kind)
        assertEquals(0, reads.get())
    }

    @Test
    fun `unapproved profile cannot bootstrap even with remote enabled`() {
        val wrong = snapshot(true).copy(
            activeProfile = ModelProfileRef(ModelProfileId("other"), ModelProfileVersion(1)),
        )
        assertNull(FaroV5TrustedModelGatewayFactory(
            { wrong }, SecretReader { null },
        ).create())
    }

    private fun snapshot(enabled: Boolean, withCredential: Boolean = true) =
        AiControlSnapshot(
            remoteReasoningEnabled = enabled,
            activeProfile = FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE,
            budget = AiMonetaryBudget.Disabled,
            credentialRefs = if (withCredential) listOf(
                AiBindingCredentialRef(FaroV5TrustedModelGatewayFactory.BINDING_ID, credential)
            ) else emptyList(),
            revision = if (enabled) 1L else 2L,
        )

    private fun user() = ConversationMessage(
        ConversationMessageId("m"), ConversationId("c"), ConversationTurnId("t"),
        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
        "hola", 1L,
    )
}
