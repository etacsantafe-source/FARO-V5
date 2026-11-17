package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/** M1B: bounded human-readable Review context; not an Apply authorization. */
internal data class S4W01FinanceLocalConsentScope(
    val operationId: String,
    val objectScope: String,
    val planFingerprintHex: String,
    val reviewedSummary: String,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(planFingerprintHex))
        for (value in listOf(operationId, objectScope)) {
            require(value.length in 1..128 && value == value.trim() &&
                value.none { it.code < 32 || it.code == 127 })
        }
        require(reviewedSummary.length in 1..120 &&
            reviewedSummary == reviewedSummary.trim() &&
            reviewedSummary.none { it.code < 32 || it.code == 127 })
    }
}

internal data class S4W01FinanceLocalChallenge(
    val digestHex: String,
    val scope: S4W01FinanceLocalConsentScope,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
)

/** Ephemeral TECHNICAL evidence. Never a principal ID, ACL grant or Apply token. */
internal data class S4W01FinanceLocalPresenceEvidence(
    val challengeDigestHex: String,
    val signatureDigestHex: String,
    val scope: S4W01FinanceLocalConsentScope,
    val pointer: ActiveGenerationPointer,
    val applicationUid: Int,
    val applicationId: String,
    val expiresAtMs: Long,
)

/**
 * One-shot M1B in-memory challenge controller. The production signer MUST be
 * an AndroidKeyStore-per-use Signature unlocked by BiometricPrompt.CryptoObject.
 * Test fakes prove only policy; they NEVER attest hardware/Android biometrics.
 *
 * The verifier is injected by an INTERNAL trusted host and must verify against
 * the fixed AndroidKeyStore alias, never a public key supplied by the caller.
 * Nothing here touches SQL, Finance grants, receipts or Apply.
 */
internal class S4W01FinanceLocalPrincipal(
    private val expectedApplicationId: String,
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedHostEpoch: String,
    private val hostEpoch: () -> String?,
    private val sandboxEvidence: () -> AndroidSandboxTechnicalEvidence?,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val nonce: () -> ByteArray = {
        ByteArray(32).also(SecureRandom()::nextBytes)
    },
    private val verifyTrustedSignature: (String, ByteArray) -> Boolean,
) : AutoCloseable {
    private enum class State { NEW, PENDING, CONSUMED }
    private val lock = Any()
    private var state = State.NEW
    private var pending: S4W01FinanceLocalChallenge? = null
    private var sandboxAtStart: AndroidSandboxTechnicalEvidence? = null

    init {
        require(expectedApplicationId.length in 3..200 &&
            expectedApplicationId.contains('.') && expectedApplicationId == expectedApplicationId.trim())
        require(expectedHostEpoch.isNotBlank() && expectedHostEpoch == expectedHostEpoch.trim())
    }

    private fun sample(): AndroidSandboxTechnicalEvidence? = runCatching {
        if (pointer.read() != pinned || hostEpoch() != expectedHostEpoch) {
            return@runCatching null
        }
        val observed = sandboxEvidence() ?: return@runCatching null
        if (observed.applicationUid <= 0 ||
            observed.applicationId != expectedApplicationId ||
            observed.pinnedGeneration != pinned ||
            observed.sessionNonce.isBlank()
        ) return@runCatching null
        if (pointer.read() != pinned || hostEpoch() != expectedHostEpoch) null
        else observed
    }.getOrNull()

    fun beginAfterLocalHumanRequest(
        scope: S4W01FinanceLocalConsentScope,
    ): S4W01FinanceLocalChallenge? = synchronized(lock) {
        if (state != State.NEW) return@synchronized null
        // Permanently invalidate even if a first attempt cannot establish
        // the trusted sandbox. Never start a second prompt from this instance.
        state = State.CONSUMED
        val observed = sample() ?: return@synchronized null
        val issued = runCatching { nowMs() }.getOrNull()?.takeIf { it >= 0L }
            ?: return@synchronized null
        val deadline = runCatching { Math.addExact(issued, 120_000L) }.getOrNull()
            ?: return@synchronized null
        val entropy = runCatching { nonce() }.getOrNull()
            ?: return@synchronized null
        if (entropy.size != 32 || entropy.all { it == 0.toByte() }) return@synchronized null

        val payload = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF("FARO_W01_M1B_LOCAL_PRESENCE_CHALLENGE_V1")
                out.writeUTF(expectedApplicationId)
                out.writeInt(observed.applicationUid)
                out.writeUTF(observed.sessionNonce)
                out.writeUTF(pinned.generationId.value)
                out.writeLong(pinned.sequence)
                out.writeUTF(expectedHostEpoch)
                out.writeUTF(scope.operationId)
                out.writeUTF(scope.objectScope)
                out.writeUTF(scope.planFingerprintHex)
                out.writeUTF(scope.reviewedSummary)
                out.writeLong(issued)
                out.writeLong(deadline)
                out.writeInt(entropy.size)
                out.write(entropy)
            }
            buffer.toByteArray()
        }
        entropy.fill(0)
        val next = S4W01FinanceLocalChallenge(hex(sha256(payload)), scope, issued, deadline)
        payload.fill(0)
        if (sample() != observed) return@synchronized null
        sandboxAtStart = observed
        pending = next
        state = State.PENDING
        next
    }

    /**
     * Called ONLY by trusted Android host after CryptoObject.Signature.sign().
     * One attempt consumes the challenge even when signature/probe is invalid.
     */
    fun completeFromAuthenticatedCrypto(
        challenge: S4W01FinanceLocalChallenge,
        signature: ByteArray,
    ): S4W01FinanceLocalPresenceEvidence? = synchronized(lock) {
        if (state != State.PENDING) return@synchronized null
        state = State.CONSUMED
        val issued = pending
        val oldSandbox = sandboxAtStart
        pending = null
        sandboxAtStart = null
        if (issued != challenge || oldSandbox == null ||
            !Regex("[0-9a-f]{64}").matches(challenge.digestHex) ||
            signature.size !in 8..4096
        ) return@synchronized null
        val current = sample()
        val now = runCatching { nowMs() }.getOrNull()
        if (current != oldSandbox || now == null ||
            now < challenge.issuedAtMs || now >= challenge.expiresAtMs
        ) return@synchronized null
        if (!runCatching {
            verifyTrustedSignature(
                challenge.digestHex, signature.copyOf(),
            )
        }.getOrDefault(false)) return@synchronized null
        if (sample() != oldSandbox) return@synchronized null
        S4W01FinanceLocalPresenceEvidence(
            challengeDigestHex = challenge.digestHex,
            signatureDigestHex = hex(sha256(signature)),
            scope = challenge.scope,
            pointer = pinned,
            applicationUid = oldSandbox.applicationUid,
            applicationId = expectedApplicationId,
            expiresAtMs = challenge.expiresAtMs,
        )
    }

    override fun close() {
        synchronized(lock) {
            state = State.CONSUMED
            pending = null
            sandboxAtStart = null
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val n = b.toInt() and 255
            append(Character.forDigit(n ushr 4, 16))
            append(Character.forDigit(n and 15, 16))
        }
    }
}
