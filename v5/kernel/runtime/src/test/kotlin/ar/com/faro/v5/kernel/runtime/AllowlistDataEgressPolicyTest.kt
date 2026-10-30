package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.ContextPack
import ar.com.faro.v5.kernel.contracts.DataEgressDecision
import ar.com.faro.v5.kernel.contracts.DataEgressPolicyConfig
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AllowlistDataEgressPolicyTest {
    @Test
    fun `remote egress allows only explicitly allowlisted sensitivities`() {
        val policy = AllowlistDataEgressPolicy(
            DataEgressPolicyConfig(
                remoteAllowed = setOf(
                    EgressSensitivity.PUBLIC,
                    EgressSensitivity.PRIVATE,
                ),
                onDeviceAllowed = setOf(
                    EgressSensitivity.PUBLIC,
                    EgressSensitivity.PRIVATE,
                    EgressSensitivity.RESTRICTED,
                ),
            )
        )
        val pack = ContextPack(
            fragments = listOf(
                fragment("request", EgressSensitivity.PRIVATE),
                fragment("public", EgressSensitivity.PUBLIC),
            ),
            omittedFragmentIds = emptyList(),
        )

        assertSame(
            DataEgressDecision.Allowed,
            policy.evaluate(pack, DataEgressTarget.REMOTE_MODEL),
        )
    }

    @Test
    fun `unclassified context fails closed`() {
        val policy = AllowlistDataEgressPolicy(
            DataEgressPolicyConfig(
                remoteAllowed = setOf(EgressSensitivity.PUBLIC),
                onDeviceAllowed = setOf(EgressSensitivity.PUBLIC),
            )
        )
        val pack = ContextPack(
            fragments = listOf(fragment("unknown", EgressSensitivity.UNCLASSIFIED)),
            omittedFragmentIds = emptyList(),
        )

        val blocked = assertInstanceOf(
            DataEgressDecision.Blocked::class.java,
            policy.evaluate(pack, DataEgressTarget.REMOTE_MODEL),
        )
        assertEquals(
            listOf(ContextFragmentId("unknown")),
            blocked.fragmentIds,
        )
    }

    @Test
    fun `mixed remote pack identifies every blocked fragment deterministically`() {
        val policy = AllowlistDataEgressPolicy(
            DataEgressPolicyConfig(
                remoteAllowed = setOf(EgressSensitivity.PUBLIC),
                onDeviceAllowed = setOf(EgressSensitivity.PUBLIC),
            )
        )
        val pack = ContextPack(
            fragments = listOf(
                fragment("z-secret", EgressSensitivity.SECRET),
                fragment("allowed", EgressSensitivity.PUBLIC),
                fragment("a-private", EgressSensitivity.PRIVATE),
            ),
            omittedFragmentIds = emptyList(),
        )

        val blocked = assertInstanceOf(
            DataEgressDecision.Blocked::class.java,
            policy.evaluate(pack, DataEgressTarget.REMOTE_MODEL),
        )
        assertEquals(
            listOf(
                ContextFragmentId("a-private"),
                ContextFragmentId("z-secret"),
            ),
            blocked.fragmentIds,
        )
    }

    @Test
    fun `on-device policy is separate but secret still fails closed`() {
        val policy = AllowlistDataEgressPolicy(
            DataEgressPolicyConfig(
                remoteAllowed = emptySet(),
                onDeviceAllowed = setOf(EgressSensitivity.RESTRICTED),
            )
        )
        val allowed = ContextPack(
            fragments = listOf(fragment("local", EgressSensitivity.RESTRICTED)),
            omittedFragmentIds = emptyList(),
        )
        val secret = ContextPack(
            fragments = listOf(fragment("secret", EgressSensitivity.SECRET)),
            omittedFragmentIds = emptyList(),
        )

        assertSame(
            DataEgressDecision.Allowed,
            policy.evaluate(allowed, DataEgressTarget.ON_DEVICE_MODEL),
        )
        assertInstanceOf(
            DataEgressDecision.Blocked::class.java,
            policy.evaluate(secret, DataEgressTarget.ON_DEVICE_MODEL),
        )
    }

    private fun fragment(
        id: String,
        sensitivity: EgressSensitivity,
    ): ContextFragment =
        ContextFragment(
            id = ContextFragmentId(id),
            contextClass = ContextClass.CURRENT_REQUEST,
            payload = CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = id.encodeToByteArray(),
            ),
            estimatedTokens = 4,
            egressSensitivity = sensitivity,
        )
}
