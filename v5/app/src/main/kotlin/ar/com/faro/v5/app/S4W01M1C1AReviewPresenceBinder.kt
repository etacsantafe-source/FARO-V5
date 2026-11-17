package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.TrustedConversationApplyReviewPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort

/**
 * M1C1A read-only result after matching a canonical Workspace APPROVED Review
 * with local CryptoObject presence evidence provided by a trusted Android host.
 * Not a principal, a policy epoch, a durable grant, a receipt, or an Apply token.
 * Do not marshal this object across process, to Activity Intents, or backup.
 */
internal data class S4W01M1C1AReviewPresenceMatch(
    val reviewId: String,
    val reviewRevision: Long,
    val planFingerprint: PlanFingerprint,
    val operationId: String,
    val objectScope: String,
    val challengeDigestHex: String,
    val signatureDigestHex: String,
    val pointer: ActiveGenerationPointer,
    val applicationUid: Int,
)

/**
 * Single-use in-memory DEV-only bridge, never composed into a shipping Activity.
 *
 * Structural evidence cannot attest physical Keystore authentication by itself:
 * the caller MUST obtain its S4W01FinanceLocalPresenceEvidence only through the
 * trusted S4W01FinanceAuthHost CryptoObject callback. The JVM tests can forge
 * their test evidence and therefore NEVER certify biometric authorization.
 *
 * The canonical approved scope is resolved through a real Workspace reader.
 * This object NEVER reads or writes Finance ACL/Reality, executes Apply, or
 * grants authorization. M1C1B must still issue/consume a durable grant in
 * one SQLite BEGIN IMMEDIATE -> COMMIT with the actual receipt.
 */
internal class S4W01M1C1AReviewPresenceBinder(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val workspace: TrustedConversationApplyReviewPort,
    private val approved: S4W01B2FinanceApprovedScopeReadPort,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val expectedApplicationId: String,
    private val expectedApplicationUid: Int,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val mutex = Any()
    private var consumed = false

    init {
        require(expectedHostEpoch.isNotBlank() && expectedHostEpoch == expectedHostEpoch.trim())
        require(expectedApplicationId.length in 3..200 &&
            expectedApplicationId.contains('.') &&
            expectedApplicationId == expectedApplicationId.trim())
        require(expectedApplicationUid > 0)
    }

    private fun current(): Boolean = runCatching {
        hostReady() && hostEpochOrNull() == expectedHostEpoch &&
            pointer.read() == pinned
    }.getOrDefault(false)

    private val hex64 = Regex("[0-9a-f]{64}")

    /**
     * One attempt, regardless of success/failure. Neither a previous approval
     * nor the same signed challenge can pass through this instance again.
     * Cross-instance/cross-process replay is still a M1C1B SQL grant concern.
     */
    fun bindAfterTrustedPresence(
        context: ConversationPlanReviewContext,
        presence: S4W01FinanceLocalPresenceEvidence?,
    ): S4W01M1C1AReviewPresenceMatch? = synchronized(mutex) {
        if (consumed) return@synchronized null
        consumed = true
        if (presence == null || !current()) return@synchronized null

        val now = runCatching { nowMs() }.getOrNull()
            ?: return@synchronized null
        if (now < 0L || presence.expiresAtMs <= now ||
            presence.expiresAtMs - now > 120_000L ||
            presence.pointer != pinned ||
            presence.applicationUid != expectedApplicationUid ||
            presence.applicationId != expectedApplicationId ||
            !hex64.matches(presence.challengeDigestHex) ||
            !hex64.matches(presence.signatureDigestHex) ||
            presence.challengeDigestHex == presence.signatureDigestHex
        ) return@synchronized null

        val first = runCatching { workspace.load(context) }.getOrNull()
            ?: return@synchronized null
        val review = first.review
        if (review.context() != context ||
            review.state != ConversationPlanReviewState.APPROVED ||
            review.planFingerprint != context.planFingerprint ||
            review.reviewText.length !in 1..120 ||
            review.reviewText != review.reviewText.trim() ||
            review.reviewText.any { it == '\n' || it == '\r' || it.code < 32 }
        ) return@synchronized null

        val typed = runCatching { approved.read(context) }.getOrNull()
            ?: return@synchronized null
        if (typed.planFingerprint != review.planFingerprint ||
            typed.operationId.value != presence.scope.operationId ||
            typed.objectId != presence.scope.objectScope ||
            typed.planFingerprint.value != presence.scope.planFingerprintHex ||
            review.reviewText != presence.scope.reviewedSummary
        ) return@synchronized null

        // Detect stale Workspace revisions, envelope mutation, contract drift,
        // pointer ABA, OS lifecycle transitions and interrupted host.
        val second = runCatching { workspace.load(context) }.getOrNull()
            ?: return@synchronized null
        if (second.review != review ||
            !second.envelope().contentEquals(first.envelope()) ||
            runCatching { approved.read(context) }.getOrNull() != typed ||
            !current()
        ) return@synchronized null

        S4W01M1C1AReviewPresenceMatch(
            reviewId = review.reviewId,
            reviewRevision = review.revision,
            planFingerprint = typed.planFingerprint,
            operationId = typed.operationId.value,
            objectScope = typed.objectId,
            challengeDigestHex = presence.challengeDigestHex,
            signatureDigestHex = presence.signatureDigestHex,
            pointer = pinned,
            applicationUid = expectedApplicationUid,
        )
    }

    override fun close() {
        synchronized(mutex) { consumed = true }
    }
}
