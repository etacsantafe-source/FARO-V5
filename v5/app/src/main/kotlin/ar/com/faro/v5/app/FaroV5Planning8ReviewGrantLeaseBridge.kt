package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort

/**
 * N033C2B4 scoped bridge: canonical user-approved Review -> exact CREATE
 * operation/object -> existing in-process grant lease.
 *
 * NOT a source of policy, principal, ApplyPlan, receipt or production ACL.
 * The future trusted host must supply independently verified policy and put
 * the FULL synchronous Reality transaction, including COMMIT/ROLLBACK,
 * inside [action]. Cross-process and external-policy revocation remain STOP.
 */
internal class FaroV5Planning8ReviewGrantLeaseBridge(
    private val canonicalReview: FaroV5Planning8ApprovedObjectGate,
    private val grants: FaroV5Planning8ObjectGrantOwner,
    private val activePointer: ActiveGenerationPointerPort,
    private val hostReady: () -> Boolean,
) {
    fun <T> withReviewedGrantLease(
        context: ConversationPlanReviewContext,
        expectedScope: Planning8ObjectScope,
        expectedNonce: String,
        action: () -> T,
    ): Planning8GrantLeaseResult<T> {
        val pinned = runCatching { activePointer.read() }.getOrNull()
            ?: return Planning8GrantLeaseResult.Denied
        val initiallyValid = runCatching {
            hostReady() &&
                canonicalReview.currentApprovedObjectScopeOrNull(context) == expectedScope &&
                activePointer.read() == pinned
        }.getOrDefault(false)
        if (!initiallyValid) return Planning8GrantLeaseResult.Denied

        // The owner holds its same monitor for the WHOLE synchronous callback.
        // In particular, a concurrently confirmed revoke cannot overtake it.
        // A second canonical check prevents Discard, ABA or scope substitution
        // between the initial preview and actual entry into the owner lease.
        val leased = grants.withCurrentGrantLease(
            scope = expectedScope, pointer = pinned, expectedNonce = expectedNonce,
        ) {
            val stillValid = runCatching {
                hostReady() &&
                    activePointer.read() == pinned &&
                    canonicalReview.currentApprovedObjectScopeOrNull(context) == expectedScope
            }.getOrDefault(false)
            if (!stillValid) Planning8GrantLeaseResult.Denied
            else Planning8GrantLeaseResult.Admitted(action())
        }
        return when (leased) {
            is Planning8GrantLeaseResult.Admitted -> leased.value
            Planning8GrantLeaseResult.Denied -> Planning8GrantLeaseResult.Denied
        }
        // Never report a post-action deny: action may have COMMITTED Reality.
    }
}
