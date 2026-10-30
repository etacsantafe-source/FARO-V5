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
import ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DeterministicModelRoutePlannerTest {
    @Test
    fun `planner preserves profile order among eligible bindings`() {
        val profile = profile("preferred", "alternate", "local")
        val snapshots = listOf(
            snapshot("local"),
            snapshot("alternate"),
            snapshot("preferred"),
        )

        val plan = DeterministicModelRoutePlanner.plan(
            profile = profile,
            snapshots = snapshots,
            requirements = ModelRequirements(
                requiredCapabilities = setOf(ModelCapability.TEXT),
                minimumContextTokens = 8_000,
            ),
            contextPack = pack(EgressSensitivity.PUBLIC),
            egressPolicy = DataEgressPolicy { _, _ -> DataEgressDecision.Allowed },
        )

        assertEquals(
            listOf("preferred", "alternate", "local"),
            plan.eligibleBindings.map { it.bindingId.value },
        )
        assertEquals(emptyList<Any>(), plan.rejections)
    }

    @Test
    fun `planner rejects missing inactive incompatible and small-context bindings explicitly`() {
        val profile = profile("missing", "inactive", "capability", "small")
        val snapshots = listOf(
            snapshot("inactive", lifecycle = ModelLifecycle.DEPRECATED),
            snapshot("capability", capabilities = setOf(ModelCapability.TEXT)),
            snapshot("small", contextLimit = 4_000),
        )

        val plan = DeterministicModelRoutePlanner.plan(
            profile = profile,
            snapshots = snapshots,
            requirements = ModelRequirements(
                requiredCapabilities = setOf(
                    ModelCapability.TEXT,
                    ModelCapability.STRUCTURED_OUTPUT,
                ),
                minimumContextTokens = 8_000,
            ),
            contextPack = pack(EgressSensitivity.PUBLIC),
            egressPolicy = DataEgressPolicy { _, _ -> DataEgressDecision.Allowed },
        )

        assertEquals(
            listOf(
                ModelRouteRejectionReason.SNAPSHOT_MISSING,
                ModelRouteRejectionReason.LIFECYCLE_NOT_ACTIVE,
                ModelRouteRejectionReason.CAPABILITY_MISMATCH,
                ModelRouteRejectionReason.CONTEXT_LIMIT_TOO_SMALL,
            ),
            plan.rejections.map { it.reason },
        )
        assertEquals(emptyList<Any>(), plan.eligibleBindings)
    }

    @Test
    fun `planner evaluates egress by binding target and keeps blocked fragment evidence`() {
        val profile = ModelProfile(
            ref = ModelProfileRef(
                profileId = ModelProfileId("conversation"),
                version = ModelProfileVersion(1),
            ),
            orderedBindings = listOf(
                ModelProfileBinding(
                    bindingId = ModelBindingId("remote"),
                    target = DataEgressTarget.REMOTE_MODEL,
                ),
                ModelProfileBinding(
                    bindingId = ModelBindingId("local"),
                    target = DataEgressTarget.ON_DEVICE_MODEL,
                ),
            ),
        )
        val context = pack(EgressSensitivity.RESTRICTED)
        val policy = DataEgressPolicy { currentPack, target ->
            if (target == DataEgressTarget.REMOTE_MODEL) {
                DataEgressDecision.Blocked(
                    currentPack.fragments.map { it.id }
                )
            } else {
                DataEgressDecision.Allowed
            }
        }

        val plan = DeterministicModelRoutePlanner.plan(
            profile = profile,
            snapshots = listOf(snapshot("remote"), snapshot("local")),
            requirements = ModelRequirements(
                requiredCapabilities = setOf(ModelCapability.TEXT),
            ),
            contextPack = context,
            egressPolicy = policy,
        )

        assertEquals(listOf("local"), plan.eligibleBindings.map { it.bindingId.value })
        assertEquals(
            ModelRouteRejectionReason.EGRESS_BLOCKED,
            plan.rejections.single().reason,
        )
        assertEquals(
            listOf(ContextFragmentId("context")),
            plan.rejections.single().blockedFragmentIds,
        )
    }

    @Test
    fun `duplicate capability snapshots fail closed`() {
        val duplicate = snapshot("same")

        assertThrows(IllegalArgumentException::class.java) {
            DeterministicModelRoutePlanner.plan(
                profile = profile("same"),
                snapshots = listOf(duplicate, duplicate),
                requirements = ModelRequirements(
                    requiredCapabilities = setOf(ModelCapability.TEXT),
                ),
                contextPack = pack(EgressSensitivity.PUBLIC),
                egressPolicy = DataEgressPolicy { _, _ -> DataEgressDecision.Allowed },
            )
        }
    }

    private fun profile(vararg ids: String): ModelProfile =
        ModelProfile(
            ref = ModelProfileRef(
                profileId = ModelProfileId("conversation"),
                version = ModelProfileVersion(1),
            ),
            orderedBindings = ids.mapIndexed { index, id ->
                ModelProfileBinding(
                    bindingId = ModelBindingId(id),
                    target = if (index == ids.lastIndex && id == "local") {
                        DataEgressTarget.ON_DEVICE_MODEL
                    } else {
                        DataEgressTarget.REMOTE_MODEL
                    },
                )
            },
        )

    private fun snapshot(
        id: String,
        lifecycle: ModelLifecycle = ModelLifecycle.ACTIVE,
        capabilities: Set<ModelCapability> = setOf(
            ModelCapability.TEXT,
            ModelCapability.STRUCTURED_OUTPUT,
        ),
        contextLimit: Int? = 32_000,
    ): ProviderCapabilitySnapshot =
        ProviderCapabilitySnapshot(
            bindingId = ModelBindingId(id),
            lifecycle = lifecycle,
            capabilities = capabilities,
            contextLimitTokens = contextLimit,
        )

    private fun pack(
        sensitivity: EgressSensitivity,
    ): ContextPack =
        ContextPack(
            fragments = listOf(
                ContextFragment(
                    id = ContextFragmentId("context"),
                    contextClass = ContextClass.CURRENT_REQUEST,
                    payload = CanonicalPayload.fromEncoded(
                        formatId = "text/plain",
                        bytes = "context".encodeToByteArray(),
                    ),
                    estimatedTokens = 4,
                    egressSensitivity = sensitivity,
                )
            ),
            omittedFragmentIds = emptyList(),
        )
}
