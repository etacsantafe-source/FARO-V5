package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.Finance9TrustedStageGrant
import ar.com.faro.v5.store.reality.Finance9TrustedStageGrantIssueRequest
import ar.com.faro.v5.store.reality.RealityFinance9TrustedStageGrantIssuerPort
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Pure policy tests with SYNTHETIC evidence and a FAKE SQL port.
 * NEVER proof of AndroidKeyStore authentication, biometric human approval,
 * real SQLite durability, productive Finance, or Android device hardware.
 */
class S4W01M1C1B1CTrustedGrantAdmissionTest {
    @TempDir lateinit var root: Path

    private inner class Rig(label: String) {
        private val folder = root.resolve("trusted-stage-" + label)
        val pointer = FileActiveGenerationPointer(folder)
        val pinned = requireNotNull(pointer.compareAndSet(
            null, StorageGenerationId("generation-" + label),
        ))
        val appId = "ar.com.faro.alpha.v2coredev"
        val uid = 10123
        val scope = S4W01FinanceLocalConsentScope(
            "finance.account.create", "account-" + label,
            "a".repeat(64), "Confirmar cuenta ARS",
        )
        val evidence = S4W01FinanceLocalPresenceEvidence(
            "c".repeat(64), "d".repeat(64), scope, pinned, uid, appId, 9000L,
        )
        val match = S4W01M1C1AReviewPresenceMatch(
            "review-" + label, 1L, PlanFingerprint(scope.planFingerprintHex),
            scope.operationId, scope.objectScope, evidence.challengeDigestHex,
            evidence.signatureDigestHex, pinned, uid,
        )
        var epoch: String? = "host-epoch"
        var ready = true
        var now = 1000L
        var calls = 0
        var lastRequest: Finance9TrustedStageGrantIssueRequest? = null
        val principalHash = "b".repeat(64)
        val issuer = RealityFinance9TrustedStageGrantIssuerPort { r ->
            calls += 1
            lastRequest = r
            Finance9TrustedStageGrant(
                r.principalHash, r.generationId, r.pointerSequence,
                r.operationId, r.objectScope, r.grantId, 1L,
            )
        }
        fun gate(port: RealityFinance9TrustedStageGrantIssuerPort = issuer) =
            S4W01M1C1B1CTrustedGrantAdmission(
                pinned = pinned, pointer = pointer, expectedApplicationId = appId,
                expectedApplicationUid = uid, expectedHostEpoch = "host-epoch",
                hostEpochOrNull = { epoch }, hostReady = { ready },
                issuer = port, nowMs = { now },
            )
    }

    @Test fun i01HostBoundMatchingReviewCreatesOneStageRequest() {
        val f = Rig("valid")
        f.gate().use { gate ->
            val grant = requireNotNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
            assertEquals(f.principalHash, grant.principalHash)
            assertEquals(f.pinned.generationId.value, grant.generationId)
            assertEquals(f.pinned.sequence, grant.pointerSequence)
            assertEquals(1L, grant.expectedPolicyEpoch)
            val req = requireNotNull(f.lastRequest)
            assertEquals(f.scope.planFingerprintHex, req.fingerprintHex)
            assertEquals(f.match.operationId, req.operationId)
            assertEquals(f.match.objectScope, req.objectScope)
            assertEquals(1000L, req.issuedAtMs)
            assertEquals(9000L, req.expiresAtMs)
            assertTrue(req.grantId.startsWith("g-") && req.grantId.length == 66)
            assertTrue(req.intentKey.startsWith("i-") && req.intentKey.length == 66)
            assertEquals(1, f.calls)
        }
    }

    @Test fun i02SingleUseAndFailedFirstAttemptCannotReplay() {
        val f = Rig("replay")
        f.gate().use { gate ->
            assertNotNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
            assertEquals(1, f.calls)
        }
        f.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, null, f.match, f.principalHash,
            ))
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
            assertEquals(1, f.calls)
        }
    }

    @Test fun i03ForgedOrForeignReviewScopeCannotReachIssuer() {
        val f = Rig("scope")
        f.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence.copy(scope = f.scope.copy(objectScope = "other")),
                f.match, f.principalHash,
            ))
        }
        f.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence,
                f.match.copy(signatureDigestHex = "e".repeat(64)), f.principalHash,
            ))
        }
        f.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence,
                f.match.copy(planFingerprint = PlanFingerprint("f".repeat(64))),
                f.principalHash,
            ))
        }
        assertEquals(0, f.calls)
    }

    @Test fun i04PointerAbaOrRevokedHostEpochDeny() {
        val f = Rig("aba")
        val next = StorageGenerationId("foreign-aba")
        assertNotNull(f.pointer.compareAndSet(f.pinned.generationId, next))
        assertNotNull(f.pointer.compareAndSet(next, f.pinned.generationId))
        f.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
        }
        val f2 = Rig("revoked")
        f2.epoch = "other"
        f2.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f2.scope, f2.evidence, f2.match, f2.principalHash,
            ))
        }
        f2.ready = false
        f2.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f2.scope, f2.evidence, f2.match, f2.principalHash,
            ))
        }
        assertEquals(0, f.calls)
        assertEquals(0, f2.calls)
    }

    @Test fun i05ExpiredForeignUidOrBadPrincipalDeny() {
        val f = Rig("expired")
        f.now = f.evidence.expiresAtMs
        f.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
        }
        val f2 = Rig("foreign")
        f2.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f2.scope, f2.evidence.copy(applicationUid = f2.uid + 1),
                f2.match, f2.principalHash,
            ))
        }
        f2.gate().use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f2.scope, f2.evidence, f2.match, "invalid",
            ))
        }
        assertEquals(0, f.calls)
        assertEquals(0, f2.calls)
    }

    @Test fun i06IssuerErrorAndFalseReceiptFieldsNeverReturnStageGrant() {
        val f = Rig("issuerfail")
        f.gate(RealityFinance9TrustedStageGrantIssuerPort {
            throw IllegalStateException("synthetic issuer failure")
        }).use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
        }
        f.gate(RealityFinance9TrustedStageGrantIssuerPort { r ->
            Finance9TrustedStageGrant(
                r.principalHash, r.generationId, r.pointerSequence,
                r.operationId, "foreign-account", r.grantId, 1L,
            )
        }).use { gate ->
            assertNull(gate.issueAfterVerifiedHostCallback(
                f.scope, f.evidence, f.match, f.principalHash,
            ))
        }
    }
}
