package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * JVM POLICY tests with a fake ECDSA key. NOT a biometric/device/Keystore
 * certification, nor an Apply grant / cross-process COMMIT test.
 */
class S4W01FinanceLocalPrincipalTest {
    @TempDir lateinit var root: Path

    private val scope = S4W01FinanceLocalConsentScope(
        "finance.entry.record", "entry-local-test", "a".repeat(64),
        "Registrar gasto revisado en ARS",
    )

    private inner class Fixture {
        private val caseRoot = java.nio.file.Files.createDirectories(
            root.resolve("m1b-case-" + java.util.UUID.randomUUID()),
        )
        val pointer = FileActiveGenerationPointer(caseRoot)
        val generation = StorageGenerationId("w01-m1b-staged")
        val pinned = checkNotNull(pointer.compareAndSet(null, generation))
        val packageId = "ar.com.faro.alpha.v2coredev"
        var hostEpoch: String? = "host-local-epoch"
        var now: Long = 1000L
        var evidence: AndroidSandboxTechnicalEvidence? =
            AndroidSandboxTechnicalEvidence(
                sessionNonce = "sandbox-" + java.util.UUID.randomUUID(),
                applicationId = packageId,
                applicationUid = 10123,
                pinnedGeneration = pinned,
            )
        var signerAllowed = true
        private val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
        var entropy = ByteArray(32) { (it + 1).toByte() }

        fun controller() = S4W01FinanceLocalPrincipal(
            expectedApplicationId = packageId,
            pinned = pinned,
            pointer = pointer,
            expectedHostEpoch = "host-local-epoch",
            hostEpoch = { hostEpoch },
            sandboxEvidence = { evidence },
            nowMs = { now },
            nonce = { entropy.copyOf() },
            verifyTrustedSignature = { digest, signature ->
                signerAllowed && Signature.getInstance("SHA256withECDSA").run {
                    initVerify(pair.public)
                    update(digest.toByteArray(StandardCharsets.US_ASCII))
                    verify(signature)
                }
            },
        )

        fun sign(challenge: S4W01FinanceLocalChallenge): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(challenge.digestHex.toByteArray(StandardCharsets.US_ASCII))
                sign()
            }
    }

    @Test fun canonicalReviewedScopeAndSandboxProduceOneFreshChallengeOnly() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            assertTrue(Regex("[0-9a-f]{64}").matches(c.digestHex))
            assertEquals(scope, c.scope)
            assertEquals(1000L, c.issuedAtMs)
            assertEquals(121000L, c.expiresAtMs)
            assertNull(owner.beginAfterLocalHumanRequest(scope))
        }
    }

    @Test fun signedOneShotProofReturnsReadOnlyEvidenceNeverAnApplyGrant() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            val signed = f.sign(c) // JVM ECDSA ONLY, NOT AndroidKeyStore!
            val proof = requireNotNull(owner.completeFromAuthenticatedCrypto(c, signed))
            assertEquals(c.digestHex, proof.challengeDigestHex)
            assertEquals(scope.planFingerprintHex, proof.scope.planFingerprintHex)
            assertEquals(f.pinned, proof.pointer)
            assertEquals(f.packageId, proof.applicationId)
            assertEquals(10123, proof.applicationUid)
            assertTrue(Regex("[0-9a-f]{64}").matches(proof.signatureDigestHex))
            assertNull(owner.completeFromAuthenticatedCrypto(c, signed))
        }
    }

    @Test fun fakeOrUntrustedSignatureIsDeniedAndCannotRetry() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            val bad = f.sign(c).copyOf().apply {
                this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
            }
            assertNull(owner.completeFromAuthenticatedCrypto(c, bad))
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
    }

    @Test fun foreignOperationSummaryOrChallengeCannotBeSwapped() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            val changed = c.copy(scope = scope.copy(
                objectScope = "foreign-entry", reviewedSummary = "Otro gasto",
            ))
            assertNull(owner.completeFromAuthenticatedCrypto(changed, f.sign(c)))
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
        val f2 = Fixture()
        f2.entropy = ByteArray(32) { (it + 2).toByte() }
        f2.controller().use { other ->
            val unique = requireNotNull(other.beginAfterLocalHumanRequest(scope))
            assertNull(other.completeFromAuthenticatedCrypto(
                unique.copy(digestHex = "0".repeat(64)), f2.sign(unique),
            ))
        }
    }

    @Test fun generationPointerAbaInvalidatesOldSignature() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            val next = StorageGenerationId("w01-m1b-other")
            assertNotNull(f.pointer.compareAndSet(f.generation, next))
            val repointed = requireNotNull(f.pointer.compareAndSet(next, f.generation))
            assertTrue(repointed.sequence > f.pinned.sequence)
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
    }

    @Test fun hostLifecycleEpochDriftInvalidatesOldChallengePermanently() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            f.hostEpoch = "revoked-host-epoch"
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
            f.hostEpoch = "host-local-epoch"
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
    }

    @Test fun fakeSandboxMissingOrChangedAppUidNeverAuthenticates() {
        val f = Fixture()
        f.evidence = f.evidence?.copy(applicationUid = 0)
        f.controller().use { assertNull(it.beginAfterLocalHumanRequest(scope)) }
        f.evidence = f.evidence?.copy(applicationUid = 10123)
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            f.evidence = f.evidence?.copy(sessionNonce = "tampered-session")
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
    }

    @Test fun expiredOrRewoundClockCannotConfirmProof() {
        val f = Fixture()
        f.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            f.now = c.expiresAtMs
            assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        }
        val other = Fixture()
        other.controller().use { owner ->
            val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
            other.now = c.issuedAtMs - 1
            assertNull(owner.completeFromAuthenticatedCrypto(c, other.sign(c)))
        }
    }

    @Test fun closeMalformedScopeOrCryptoVerifierFailureFailClosed() {
        val f = Fixture()
        assertThrows(IllegalArgumentException::class.java) {
            S4W01FinanceLocalConsentScope(
                "finance.entry.record", "one", "bad", "Any",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            scope.copy(reviewedSummary = "Unapproved\nintent")
        }
        val owner = f.controller()
        val c = requireNotNull(owner.beginAfterLocalHumanRequest(scope))
        owner.close()
        assertNull(owner.completeFromAuthenticatedCrypto(c, f.sign(c)))
        f.signerAllowed = false
        f.controller().use { fresh ->
            val next = requireNotNull(fresh.beginAfterLocalHumanRequest(scope))
            assertNull(fresh.completeFromAuthenticatedCrypto(next, f.sign(next)))
        }
    }
}
