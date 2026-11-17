package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.Finance9ReadPolicyIssueRecord
import ar.com.faro.v5.store.reality.Finance9TrustedReadPolicyIssueRequest
import ar.com.faro.v5.store.reality.RealityFinance9TrustedReadPolicyIssuerPort
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * FAKE_SIGNER_NOT_G15: JVM Java-EC signer + synthetic Application UID,
 * sandbox and SQL issuer. Never evidence of Android BiometricPrompt or product
 * user consent. These are policy-unit tests only.
 */
class S4W01FinanceReadAuthPolicyTest {
    @TempDir lateinit var root: Path

    private inner class Rig(label: String) {
        val pointer = FileActiveGenerationPointer(root.resolve("read-" + label))
        val pinned = requireNotNull(pointer.compareAndSet(
            null, StorageGenerationId("generation-" + label),
        ))
        val appId = "ar.com.faro.alpha.v2coredev"
        val uid = 10123
        var epoch: String? = "host-read"
        var ready = true
        var now = 1_000L
        var evidence: AndroidSandboxTechnicalEvidence? =
            AndroidSandboxTechnicalEvidence("sandbox-" + label, appId, uid, pinned)
        var principalHash: String? = "b".repeat(64)
        var signerAllowed = true
        var entropy = ByteArray(32) { (it + 1).toByte() }
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
            .generateKeyPair()
        var calls = 0
        var lastRequest: Finance9TrustedReadPolicyIssueRequest? = null
        val issuer = RealityFinance9TrustedReadPolicyIssuerPort { request ->
            calls++
            lastRequest = request
            Finance9ReadPolicyIssueRecord(
                request.principalHash, request.generationId,
                request.pointerSequence, 1L, request.expiresAtMs,
            )
        }

        fun gate(port: RealityFinance9TrustedReadPolicyIssuerPort = issuer) =
            S4W01FinanceReadConsentGate(
                pinned = pinned, pointer = pointer,
                expectedApplicationId = appId, expectedApplicationUid = uid,
                expectedHostEpoch = "host-read",
                hostEpochOrNull = { epoch }, hostReady = { ready },
                sandboxEvidence = { evidence }, principalHashOrNull = { principalHash },
                verifyTrustedSignature = { digest, signature ->
                    signerAllowed && Signature.getInstance("SHA256withECDSA").run {
                        initVerify(pair.public)
                        update(digest.toByteArray(StandardCharsets.US_ASCII))
                        verify(signature)
                    }
                },
                issuer = port, nowMs = { now },
                nonce = { entropy.copyOf() },
            )

        fun sign(challenge: S4W01FinanceReadChallenge): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(challenge.digestHex.toByteArray(StandardCharsets.US_ASCII))
                sign()
            }
    }

    @Test fun r01FixedReadChallengeIsOneShotAndNotApplyReview() {
        val f = Rig("challenge")
        f.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            assertTrue(Regex("[0-9a-f]{64}").matches(c.digestHex))
            assertEquals(1_000L, c.issuedAtMs)
            assertEquals(121_000L, c.expiresAtMs)
            assertNull(gate.beginAfterExplicitReadRequest())
            assertEquals(0, f.calls)
        }
    }

    @Test fun r02JvmEcdsaIssuesOnlyShortReadPolicyRecord() {
        val f = Rig("signed")
        f.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            val issued = requireNotNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
            assertEquals(f.principalHash, issued.principalHash)
            assertEquals(f.pinned.generationId.value, issued.generationId)
            assertEquals(f.pinned.sequence, issued.pointerSequence)
            assertEquals(1L, issued.policyEpoch)
            val req = requireNotNull(f.lastRequest)
            assertEquals(1_000L, req.issuedAtMs)
            assertEquals(31_000L, req.expiresAtMs)
            assertEquals(31_000L, issued.expiresAtMs)
            assertEquals(1, f.calls)
            assertNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
    }

    @Test fun r03TamperedDigestBadSignatureAndReplayDeny() {
        val f = Rig("tampered")
        f.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            assertNull(gate.completeFromAuthenticatedCrypto(
                c.copy(digestHex = "a".repeat(64)), f.sign(c),
            ))
            assertNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
        val g = Rig("signature")
        g.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            assertNull(gate.completeFromAuthenticatedCrypto(c, byteArrayOf(1, 2)))
        }
        assertEquals(0, f.calls)
        assertEquals(0, g.calls)
    }

    @Test fun r04FullPointerAbaAndHostEpochDriftDeny() {
        val f = Rig("aba")
        f.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            val foreign = StorageGenerationId("foreign-read-generation")
            assertNotNull(f.pointer.compareAndSet(f.pinned.generationId, foreign))
            assertNotNull(f.pointer.compareAndSet(foreign, f.pinned.generationId))
            assertNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
        val other = Rig("epoch")
        other.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            other.epoch = "stale-host"
            assertNull(gate.completeFromAuthenticatedCrypto(c, other.sign(c)))
        }
        assertEquals(0, f.calls)
        assertEquals(0, other.calls)
    }

    @Test fun r05SandboxUidSessionAndForegroundDeny() {
        val f = Rig("uid")
        f.evidence = f.evidence?.copy(applicationUid = f.uid + 1)
        f.gate().use { assertNull(it.beginAfterExplicitReadRequest()) }
        val g = Rig("session")
        g.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            g.evidence = g.evidence?.copy(sessionNonce = "other-session")
            assertNull(gate.completeFromAuthenticatedCrypto(c, g.sign(c)))
        }
        val h = Rig("background")
        h.ready = false
        h.gate().use { assertNull(it.beginAfterExplicitReadRequest()) }
        assertEquals(0, f.calls)
        assertEquals(0, g.calls)
        assertEquals(0, h.calls)
    }

    @Test fun r06ExpiryRewoundClockAndWeakEntropyDeny() {
        val f = Rig("expired")
        f.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            f.now = c.expiresAtMs
            assertNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
        val g = Rig("rewound")
        g.gate().use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            g.now = c.issuedAtMs - 1L
            assertNull(gate.completeFromAuthenticatedCrypto(c, g.sign(c)))
        }
        val h = Rig("entropy")
        h.entropy = ByteArray(32)
        h.gate().use { assertNull(it.beginAfterExplicitReadRequest()) }
        assertEquals(0, f.calls)
        assertEquals(0, g.calls)
        assertEquals(0, h.calls)
    }

    @Test fun r07IssuerExceptionOrForeignResponseDeny() {
        val f = Rig("issuer-throws")
        f.gate(RealityFinance9TrustedReadPolicyIssuerPort {
            throw IllegalStateException("synthetic stage issuer closed")
        }).use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            assertNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
        val g = Rig("foreign-issuer")
        g.gate(RealityFinance9TrustedReadPolicyIssuerPort { request ->
            Finance9ReadPolicyIssueRecord(
                "c".repeat(64), request.generationId,
                request.pointerSequence, 3L, request.expiresAtMs,
            )
        }).use { gate ->
            val c = requireNotNull(gate.beginAfterExplicitReadRequest())
            assertNull(gate.completeFromAuthenticatedCrypto(c, g.sign(c)))
        }
        assertEquals(0, f.calls)
        assertEquals(0, g.calls)
    }

    @Test fun r08CloseAndUntrustedVerifierNeverIssue() {
        val f = Rig("close")
        val gate = f.gate()
        val c = requireNotNull(gate.beginAfterExplicitReadRequest())
        gate.close()
        assertNull(gate.completeFromAuthenticatedCrypto(c, f.sign(c)))
        assertEquals(0, f.calls)
        val g = Rig("verifier")
        g.signerAllowed = false
        g.gate().use { owner ->
            val challenge = requireNotNull(owner.beginAfterExplicitReadRequest())
            assertNull(owner.completeFromAuthenticatedCrypto(
                challenge, g.sign(challenge),
            ))
        }
        assertEquals(0, g.calls)
    }
}
