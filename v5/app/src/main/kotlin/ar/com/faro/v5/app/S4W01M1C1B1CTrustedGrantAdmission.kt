package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.Finance9TrustedStageGrant
import ar.com.faro.v5.store.reality.Finance9TrustedStageGrantIssueRequest
import ar.com.faro.v5.store.reality.RealityFinance9TrustedStageGrantIssuerPort
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * M1C1B1C: single-use DEV/STAGED9 policy gate. THIS DOES NOT VERIFY A
 * BIOMETRIC SIGNATURE. Only S4W01FinanceAuthHost's CryptoObject callback
 * may call this gate in the DEV composition; JVM fakes verify policy only.
 * An attacker who can call Reality9 DEV issuer within this app UID is outside
 * this stage-only trust boundary: do NOT install it in shipping Android.
 */
internal class S4W01M1C1B1CTrustedGrantAdmission(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedApplicationId: String,
    private val expectedApplicationUid: Int,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val issuer: RealityFinance9TrustedStageGrantIssuerPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val mutex = Any()
    private var consumed = false
    private val hex64 = Regex("[0-9a-f]{64}")

    init {
        require(expectedApplicationId.length in 3..200 &&
            expectedApplicationId.contains('.') &&
            expectedApplicationId == expectedApplicationId.trim())
        require(expectedApplicationUid > 0)
        require(expectedHostEpoch.isNotBlank() && expectedHostEpoch == expectedHostEpoch.trim())
    }

    private fun current(): Boolean = runCatching {
        hostReady() && hostEpochOrNull() == expectedHostEpoch &&
            pointer.read() == pinned
    }.getOrDefault(false)

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    /**
     * Only invoked through the trusted Android host's private callback path.
     * The evidence/match objects can be SYNTHESIZED in tests; therefore a
     * successful JVM test is never a biometric or product authorization.
     */
    fun issueAfterVerifiedHostCallback(
        reviewScope: S4W01FinanceLocalConsentScope,
        presence: S4W01FinanceLocalPresenceEvidence?,
        match: S4W01M1C1AReviewPresenceMatch?,
        localKeystorePrincipalHash: String?,
    ): Finance9TrustedStageGrant? = synchronized(mutex) {
        if (consumed) return@synchronized null
        consumed = true
        val e = presence ?: return@synchronized null
        val m = match ?: return@synchronized null
        val principalHash = localKeystorePrincipalHash ?: return@synchronized null
        if (!current() || !hex64.matches(principalHash) ||
            e.scope != reviewScope || e.pointer != pinned ||
            e.applicationUid != expectedApplicationUid ||
            e.applicationId != expectedApplicationId ||
            m.pointer != pinned || m.applicationUid != expectedApplicationUid ||
            m.reviewId.isBlank() || m.reviewRevision < 0L ||
            m.operationId != reviewScope.operationId ||
            m.objectScope != reviewScope.objectScope ||
            m.planFingerprint.value != reviewScope.planFingerprintHex ||
            m.challengeDigestHex != e.challengeDigestHex ||
            m.signatureDigestHex != e.signatureDigestHex ||
            !hex64.matches(e.challengeDigestHex) ||
            !hex64.matches(e.signatureDigestHex) ||
            e.signatureDigestHex == e.challengeDigestHex
        ) return@synchronized null

        val now = runCatching { nowMs() }.getOrNull()
            ?: return@synchronized null
        if (now < 0L || e.expiresAtMs <= now ||
            e.expiresAtMs - now > 120_000L) return@synchronized null
        val deadline = runCatching { Math.addExact(now, 30_000L) }.getOrNull()
            ?: return@synchronized null
        val expires = minOf(e.expiresAtMs, deadline)
        if (expires <= now || !current()) return@synchronized null

        val intentKey = "i-" + hash(
            "FARO_W01_M1C1B1C_INTENT_V1:" + m.reviewId + ":" +
                m.reviewRevision + ":" + e.challengeDigestHex,
        )
        val grantId = "g-" + hash(
            "FARO_W01_M1C1B1C_GRANT_V1:" + intentKey + ":" +
                e.signatureDigestHex,
        )
        val request = Finance9TrustedStageGrantIssueRequest(
            principalHash = principalHash,
            generationId = pinned.generationId.value,
            pointerSequence = pinned.sequence,
            operationId = m.operationId,
            objectScope = m.objectScope,
            fingerprintHex = m.planFingerprint.value,
            intentKey = intentKey, grantId = grantId,
            issuedAtMs = now, expiresAtMs = expires,
        )
        val issued = runCatching {
            issuer.issueAfterExternallyVerifiedStageAdmission(request)
        }.getOrNull()
        if (!current() || issued == null ||
            issued.principalHash != request.principalHash ||
            issued.generationId != request.generationId ||
            issued.pointerSequence != request.pointerSequence ||
            issued.operationId != request.operationId ||
            issued.objectScope != request.objectScope ||
            issued.grantId != request.grantId ||
            issued.expectedPolicyEpoch <= 0L
        ) null else issued
    }

    override fun close() {
        synchronized(mutex) { consumed = true }
    }
}
