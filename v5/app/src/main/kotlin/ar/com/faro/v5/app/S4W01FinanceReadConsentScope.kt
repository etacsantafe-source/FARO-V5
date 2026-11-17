package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.Finance9ReadPolicyIssueRecord
import ar.com.faro.v5.store.reality.Finance9TrustedReadPolicyIssueRequest
import ar.com.faro.v5.store.reality.RealityFinance9ReadPolicyObservationPort
import ar.com.faro.v5.store.reality.RealityFinance9TrustedReadPolicyIssuerPort
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/** DEV-only read-consent challenge. This is not a human identity or UI grant. */
internal data class S4W01FinanceReadChallenge(
    val digestHex: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(digestHex))
        require(issuedAtMs >= 0L && expiresAtMs > issuedAtMs &&
            expiresAtMs - issuedAtMs <= 120_000L)
    }
}

/**
 * W01-B3B1B1B1B0 DEV-only one-shot READ challenge gate.
 *
 * Injected JVM verifier, sandbox and issuer are forgeable seams, not Android
 * attestation. ONLY S4W01FinanceReadAuthHost's real CryptoObject callback may
 * call completeFromAuthenticatedCrypto in the staged Android composition.
 * No Flow, UI, Apply grant, account balances or product wiring are produced.
 * The next B3B1B1B1B1 MUST solve cross-process revoke/lifecycle separately.
 */
internal class S4W01FinanceReadConsentGate(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedApplicationId: String,
    private val expectedApplicationUid: Int,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val sandboxEvidence: () -> AndroidSandboxTechnicalEvidence?,
    private val principalHashOrNull: () -> String?,
    private val verifyTrustedSignature: (String, ByteArray) -> Boolean,
    private val issuer: RealityFinance9TrustedReadPolicyIssuerPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val nonce: () -> ByteArray = {
        ByteArray(32).also(SecureRandom()::nextBytes)
    },
) : AutoCloseable {
    private enum class State { NEW, PENDING, CONSUMED }
    private val mutex = Any()
    private var state = State.NEW
    private var pending: S4W01FinanceReadChallenge? = null
    private var sandboxAtBegin: AndroidSandboxTechnicalEvidence? = null
    private val shaPattern = Regex("[0-9a-f]{64}")

    init {
        require(expectedApplicationId.length in 3..200 &&
            expectedApplicationId.contains('.') &&
            expectedApplicationId == expectedApplicationId.trim())
        require(expectedApplicationUid > 0)
        require(expectedHostEpoch.isNotBlank() &&
            expectedHostEpoch == expectedHostEpoch.trim())
    }

    private fun current(): Boolean = runCatching {
        hostReady() && hostEpochOrNull() == expectedHostEpoch &&
            pointer.read() == pinned
    }.getOrDefault(false)

    private fun sample(): AndroidSandboxTechnicalEvidence? = runCatching {
        if (!current()) return@runCatching null
        val evidence = sandboxEvidence() ?: return@runCatching null
        if (evidence.applicationId != expectedApplicationId ||
            evidence.applicationUid != expectedApplicationUid ||
            evidence.pinnedGeneration != pinned || evidence.sessionNonce.isBlank()
        ) return@runCatching null
        if (current()) evidence else null
    }.getOrNull()

    /** Must be invoked after explicit READ consent, never automatically. */
    fun beginAfterExplicitReadRequest(): S4W01FinanceReadChallenge? =
        synchronized(mutex) {
            if (state != State.NEW) return@synchronized null
            state = State.CONSUMED // consume even a denied first request
            val sandbox = sample() ?: return@synchronized null
            val at = runCatching { nowMs() }.getOrNull()
                ?.takeIf { it >= 0L } ?: return@synchronized null
            val expires = runCatching { Math.addExact(at, 120_000L) }.getOrNull()
                ?: return@synchronized null
            val entropy = runCatching { nonce() }.getOrNull()
                ?: return@synchronized null
            if (entropy.size != 32 || entropy.all { it == 0.toByte() }) {
                entropy.fill(0)
                return@synchronized null
            }
            val bytes = ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { out ->
                    out.writeUTF("FARO_V5_W01_READ_CONSENT_DOMAIN_V1")
                    out.writeUTF(RealityFinance9ReadPolicyObservationPort.OPERATION_ID)
                    out.writeUTF(RealityFinance9ReadPolicyObservationPort.OBJECT_SCOPE)
                    out.writeUTF("Consultar mis cuentas y saldos")
                    out.writeUTF(expectedApplicationId)
                    out.writeInt(expectedApplicationUid)
                    out.writeUTF(sandbox.sessionNonce)
                    out.writeUTF(pinned.generationId.value)
                    out.writeLong(pinned.sequence)
                    out.writeUTF(expectedHostEpoch)
                    out.writeLong(at)
                    out.writeLong(expires)
                    out.writeInt(entropy.size)
                    out.write(entropy)
                }
                buffer.toByteArray()
            }
            entropy.fill(0)
            val challenge = S4W01FinanceReadChallenge(
                MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
                at, expires,
            )
            bytes.fill(0)
            if (sample() != sandbox) return@synchronized null
            pending = challenge
            sandboxAtBegin = sandbox
            state = State.PENDING
            challenge
        }

    /**
     * Only real Android host calls here with Signature.sign() from the exact
     * authenticated CryptoObject operation. Pure JVM callers can fake it:
     * these tests do not certify G15 or human consent.
     */
    fun completeFromAuthenticatedCrypto(
        challenge: S4W01FinanceReadChallenge,
        signature: ByteArray,
    ): Finance9ReadPolicyIssueRecord? = synchronized(mutex) {
        if (state != State.PENDING) return@synchronized null
        state = State.CONSUMED
        val expected = pending
        val oldSandbox = sandboxAtBegin
        pending = null
        sandboxAtBegin = null
        if (expected != challenge || oldSandbox == null ||
            signature.size !in 8..4096
        ) return@synchronized null
        val now = runCatching { nowMs() }.getOrNull()
            ?: return@synchronized null
        if (now < challenge.issuedAtMs || now >= challenge.expiresAtMs ||
            sample() != oldSandbox
        ) return@synchronized null
        val signatureVerified = runCatching {
            verifyTrustedSignature(challenge.digestHex, signature.copyOf())
        }.getOrDefault(false)
        if (!signatureVerified || sample() != oldSandbox) return@synchronized null
        val principalHash = runCatching { principalHashOrNull() }.getOrNull()
            ?.takeIf(shaPattern::matches) ?: return@synchronized null
        val policyDeadline = runCatching { Math.addExact(now, 30_000L) }
            .getOrNull() ?: return@synchronized null
        val expires = minOf(challenge.expiresAtMs, policyDeadline)
        if (expires <= now || sample() != oldSandbox) return@synchronized null
        val request = Finance9TrustedReadPolicyIssueRequest(
            principalHash = principalHash,
            generationId = pinned.generationId.value,
            pointerSequence = pinned.sequence,
            issuedAtMs = now,
            expiresAtMs = expires,
        )
        val record = runCatching {
            issuer.issueAfterExternallyVerifiedReadAdmission(request)
        }.getOrNull()
        if (record == null || record.principalHash != request.principalHash ||
            record.generationId != request.generationId ||
            record.pointerSequence != request.pointerSequence ||
            record.expiresAtMs != request.expiresAtMs ||
            record.policyEpoch <= 0L ||
            runCatching { principalHashOrNull() }.getOrNull() != principalHash ||
            sample() != oldSandbox
        ) null else record
    }

    override fun close() {
        synchronized(mutex) {
            state = State.CONSUMED
            pending = null
            sandboxAtBegin = null
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
}
