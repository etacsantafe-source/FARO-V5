package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.ContextPack
import ar.com.faro.v5.kernel.contracts.DataEgressDecision
import ar.com.faro.v5.kernel.contracts.DataEgressPolicy
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.ModelLifecycle
import ar.com.faro.v5.kernel.contracts.ModelProfile
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion
import ar.com.faro.v5.kernel.contracts.ModelRequirements
import ar.com.faro.v5.kernel.contracts.ModelRouteRejectionReason
import ar.com.faro.v5.kernel.contracts.MonotonicClock
import ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FreshProviderCapabilityCacheTest {
    @Test
    fun `fresh snapshots remain eligible before ttl expiry`() {
        val clock = FakeClock(1_000L)
        val cache =
            FreshProviderCapabilityCache(
                clock = clock,
                ttlNanos = 100L,
            )
        cache.replace(listOf(snapshot("groq")))
        clock.value = 1_099L

        val plan =
            DeterministicModelRoutePlanner.plan(
                profile = profile("groq"),
                snapshots = cache.freshSnapshots(),
                requirements =
                    ModelRequirements(
                        requiredCapabilities = setOf(ModelCapability.TEXT),
                    ),
                contextPack = pack(),
                egressPolicy =
                    DataEgressPolicy { _, _ ->
                        DataEgressDecision.Allowed
                    },
            )

        assertEquals(listOf("groq"), plan.eligibleBindings.map { it.bindingId.value })
        assertEquals(emptyList<Any>(), plan.rejections)
    }

    @Test
    fun `expired snapshot disappears and router rejects it as missing`() {
        val clock = FakeClock(10L)
        val cache =
            FreshProviderCapabilityCache(
                clock = clock,
                ttlNanos = 50L,
            )
        cache.replace(listOf(snapshot("groq")))
        clock.value = 60L

        val plan =
            DeterministicModelRoutePlanner.plan(
                profile = profile("groq"),
                snapshots = cache.freshSnapshots(),
                requirements =
                    ModelRequirements(
                        requiredCapabilities = setOf(ModelCapability.TEXT),
                    ),
                contextPack = pack(),
                egressPolicy =
                    DataEgressPolicy { _, _ ->
                        DataEgressDecision.Allowed
                    },
            )

        assertEquals(emptyList<Any>(), plan.eligibleBindings)
        assertEquals(
            listOf(ModelRouteRejectionReason.SNAPSHOT_MISSING),
            plan.rejections.map { it.reason },
        )
    }

    @Test
    fun `clock regression fails closed instead of extending stale health`() {
        val clock = FakeClock(500L)
        val cache =
            FreshProviderCapabilityCache(
                clock = clock,
                ttlNanos = 100L,
            )
        cache.replace(listOf(snapshot("groq")))
        clock.value = 499L

        assertEquals(emptyList<ProviderCapabilitySnapshot>(), cache.freshSnapshots())
    }

    @Test
    fun `replace is atomic clears old observations and sorts output deterministically`() {
        val clock = FakeClock(1L)
        val cache =
            FreshProviderCapabilityCache(
                clock = clock,
                ttlNanos = 1_000L,
            )
        cache.replace(listOf(snapshot("old")))
        clock.value = 2L
        cache.replace(
            listOf(
                snapshot("z"),
                snapshot("a"),
            )
        )

        assertEquals(
            listOf("a", "z"),
            cache.freshSnapshots().map { it.bindingId.value },
        )
    }

    @Test
    fun `duplicate replace invalidation and clear are fail-safe`() {
        val clock = FakeClock(1L)
        val cache =
            FreshProviderCapabilityCache(
                clock = clock,
                ttlNanos = 1_000L,
            )
        val duplicate = snapshot("same")

        assertThrows(IllegalArgumentException::class.java) {
            cache.replace(listOf(duplicate, duplicate))
        }

        cache.replace(listOf(snapshot("one"), snapshot("two")))
        cache.invalidate(ModelBindingId("one"))
        assertEquals(
            listOf("two"),
            cache.freshSnapshots().map { it.bindingId.value },
        )

        cache.clear()
        assertEquals(emptyList<ProviderCapabilitySnapshot>(), cache.freshSnapshots())
    }

    private fun snapshot(id: String): ProviderCapabilitySnapshot =
        ProviderCapabilitySnapshot(
            bindingId = ModelBindingId(id),
            lifecycle = ModelLifecycle.ACTIVE,
            capabilities = setOf(ModelCapability.TEXT),
            contextLimitTokens = 32_000,
        )

    private fun profile(id: String): ModelProfile =
        ModelProfile(
            ref =
                ModelProfileRef(
                    profileId = ModelProfileId("conversation"),
                    version = ModelProfileVersion(1),
                ),
            orderedBindings =
                listOf(
                    ModelProfileBinding(
                        bindingId = ModelBindingId(id),
                        target = DataEgressTarget.REMOTE_MODEL,
                    )
                ),
        )

    private fun pack(): ContextPack =
        ContextPack(
            fragments =
                listOf(
                    ContextFragment(
                        id = ContextFragmentId("request"),
                        contextClass = ContextClass.CURRENT_REQUEST,
                        payload =
                            CanonicalPayload.fromEncoded(
                                formatId = "text/plain",
                                bytes = "hello".encodeToByteArray(),
                            ),
                        estimatedTokens = 2,
                        egressSensitivity = EgressSensitivity.PUBLIC,
                    )
                ),
            omittedFragmentIds = emptyList(),
        )

    private class FakeClock(
        var value: Long,
    ) : MonotonicClock {
        override fun readNanos(): Long = value
    }
}
