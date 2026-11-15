package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Structural-only test: never calls Apply/Reality/Workspace or issues a grant. */
class S4W00D1AdmissionShapeTest {
    private class Fixture {
        val firstId = PlanNodeId("a-reminder")
        val secondId = PlanNodeId("b-monthly")
        private fun prepared(operation: String, key: String) = PreparedOperation(
            OperationContractDescriptor(
                OperationId(operation), CapabilityId("N-TEST"),
                ExecutorId("w00d1-test-only"), ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"), ContractFingerprint("w00d1-test-v1"),
                ContractExposure.BINDABLE,
            ),
            CanonicalPayload.fromEncoded("w00d1.fixture.v1", byteArrayOf(1)),
            DependencyStampSet.of(DependencyStamp(
                AuthorityBoundaryId("reality.test"),
                DependencyKey(key), DependencyStampValue("revision:0")
            )),
            IdempotencyKey(key),
        )
        val first = ApplyPlanNode(
            firstId, prepared("w00d1.fixture.reminder", "first"), emptySet()
        )
        val second = ApplyPlanNode(
            secondId, prepared("w00d1.fixture.monthly", "second"), setOf(firstId)
        )
        val plan = PreparedPlan.of(first, second)
        val requirements = listOf(
            S4W00D1ScopeRequirement(
                firstId, first.preparedOperation.contract.operationId,
                "reality.reminders", "reminder-1",
            ),
            S4W00D1ScopeRequirement(
                secondId, second.preparedOperation.contract.operationId,
                "reality.monthlyplan", "monthly-1",
            ),
        )
        val grants = requirements.mapIndexed { index, scope ->
            S4W00D1GrantObservation(
                scope, plan.planFingerprint, "generation-1",
                "trusted-host-principal", "owner-policy-epoch-${index + 1}",
                "distinct-lease-nonce-${index + 1}",
            )
        }
        var generation = "generation-1"
        var hostEpoch = "host-epoch-1"
        val current = requirements.zip(grants).toMap().toMutableMap()

        fun inspect(
            actualPlan: PreparedPlanView = plan,
            fingerprint: PlanFingerprint = plan.planFingerprint,
            scopes: List<S4W00D1ScopeRequirement> = requirements,
            presented: List<S4W00D1GrantObservation> = grants,
            principal: String = "trusted-host-principal",
            reader: (S4W00D1ScopeRequirement) -> S4W00D1GrantObservation? = { current[it] },
        ): Boolean = S4W00D1AdmissionShape.check(
            plan = actualPlan,
            reviewedFingerprint = fingerprint,
            requirements = scopes,
            presentedGrants = presented,
            pinnedGenerationId = "generation-1",
            expectedHostEpoch = "host-epoch-1",
            expectedPrincipalId = principal,
            currentGenerationOrNull = { generation },
            currentHostEpochOrNull = { hostEpoch },
            currentGrantOrNull = reader,
        )
    }

    @Test fun twoDistinctScopedObservationsMatchImmutablePlanWithoutGrantingApply() {
        assertTrue(Fixture().inspect())
    }

    @Test fun missingSecondObservationFailsClosed() {
        val f = Fixture()
        assertFalse(f.inspect(presented = f.grants.take(1)))
    }

    @Test fun crossObjectScopeSwapFailsClosed() {
        val f = Fixture()
        assertFalse(f.inspect(presented = listOf(
            f.grants[0], f.grants[1].copy(
                scope = f.grants[1].scope.copy(objectId = "reminder-1")
            ),
        )))
    }

    @Test fun stalePolicyEpochFailsClosed() {
        val f = Fixture()
        f.current[f.requirements[1]] = f.grants[1].copy(policyEpoch = "new-policy-epoch")
        assertFalse(f.inspect())
    }

    @Test fun staleGenerationFailsClosed() {
        val f = Fixture()
        f.generation = "restored-generation-2"
        assertFalse(f.inspect())
    }

    @Test fun hostLifecycleEpochDriftFailsClosed() {
        val f = Fixture()
        f.hostEpoch = "restarted-host-epoch"
        assertFalse(f.inspect())
    }

    @Test fun reusedNonceAcrossOwnersFailsClosed() {
        val f = Fixture()
        val duplicate = f.grants[1].copy(leaseNonce = f.grants[0].leaseNonce)
        f.current[f.requirements[1]] = duplicate
        assertFalse(f.inspect(presented = listOf(f.grants[0], duplicate)))
    }

    @Test fun reviewFingerprintMismatchFailsClosed() {
        val f = Fixture()
        assertFalse(f.inspect(fingerprint = PlanFingerprint("stale-review-fingerprint")))
    }

    @Test fun singleNodePlanCannotBePromotedToComposite() {
        val f = Fixture()
        assertFalse(f.inspect(actualPlan = PreparedPlan.of(f.first)))
    }

    @Test fun duplicateAuthorityCannotImpersonateSecondOwner() {
        val f = Fixture()
        val duplicated = f.requirements[1].copy(authorityId = f.requirements[0].authorityId)
        val secondGrant = f.grants[1].copy(scope = duplicated)
        assertFalse(f.inspect(
            scopes = listOf(f.requirements[0], duplicated),
            presented = listOf(f.grants[0], secondGrant),
        ))
    }

    @Test fun grantRotatingDuringValidationFailsClosed() {
        val f = Fixture()
        var calls = 0
        assertFalse(f.inspect(reader = { scope ->
            calls += 1
            val match = f.current[scope]
            if (calls >= 4 && scope == f.requirements[1])
                match?.copy(policyEpoch = "rotated-after-first-pass")
            else match
        }))
    }
}
