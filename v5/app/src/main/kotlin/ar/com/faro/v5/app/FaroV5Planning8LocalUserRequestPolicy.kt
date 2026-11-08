package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.util.UUID

/**
 * A single, ephemeral, host-initiated user request for a local Planning8 CREATE.
 * This is not an Apply permit or receipt; it may only be presented to the
 * existing object grant owner by the future trusted Android host.
 */
internal data class Planning8LocalUserRequestTicket(
    val principalId: String,
    val scope: Planning8ObjectScope,
    val policyEpoch: String,
)

/**
 * A STRICT pre-Apply decision point. The only policy source is an explicit
 * request delivered by a trusted host after canonical human APPROVED Review
 * plus Android Application-backed sandbox evidence for this installation.
 *
 * Not registered by shipping or V2 CORE DEV. Does not run Apply or mint
 * a grant nonce. The host must still prove a coherent policy/revocation
 * and pointer fence THROUGH Reality8 COMMIT after BeginApply.
 *
 * Important: the host must call issueAfterTrustedHumanApplyRequest ONLY from
 * a genuine user-request event. A screen/model passing a context is not proof
 * that the event occurred; this class cannot authenticate human intent alone.
 */
internal class FaroV5Planning8LocalUserRequestPolicy(
    private val canonical: FaroV5Planning8ApprovedObjectGate,
    private val sandbox: FaroV5AndroidSandboxPrincipal,
    private val activePointer: ActiveGenerationPointerPort,
    private val hostReady: () -> Boolean,
) : Planning8TrustedObjectPolicyPort, AutoCloseable {
    private data class BoundApply(
        val context: ConversationPlanReviewContext,
        val identity: ApplyAttemptIdentity,
    )

    private data class Request(
        val context: ConversationPlanReviewContext,
        val pinned: ActiveGenerationPointer,
        val ticket: Planning8LocalUserRequestTicket,
        val boundApply: BoundApply? = null,
    )

    private val monitor = Any()
    private val originalEvidence: AndroidSandboxTechnicalEvidence? =
        sandbox.currentEvidenceOrNull()
    private val initialPrincipal: String? =
        originalEvidence?.let { "local-android:" + it.sessionNonce }
    private var request: Request? = null
    private var closed = false

    private fun hostAndSandboxCurrent(): Boolean = runCatching {
        val original = originalEvidence ?: return@runCatching false
        hostReady() && activePointer.read() == original.pinnedGeneration &&
            sandbox.currentEvidenceOrNull() == original
    }.getOrDefault(false)

    /** Technical principal identifier only. Not evidence of human identity. */
    fun principalIdOrNull(): String? = synchronized(monitor) {
        if (closed || !hostAndSandboxCurrent()) null else initialPrincipal
    }

    /**
     * Host-only entry after a direct user action. A canonical Review is
     * necessary but NOT enough: no ticket exists until this method is called.
     * One request per instance; no automatic reissue on retry or restart.
     */
    fun issueAfterTrustedHumanApplyRequest(
        context: ConversationPlanReviewContext,
    ): Planning8LocalUserRequestTicket? = synchronized(monitor) {
        if (closed || request != null || !hostAndSandboxCurrent()) return@synchronized null
        val principal = initialPrincipal ?: return@synchronized null
        val pinned = originalEvidence?.pinnedGeneration ?: return@synchronized null
        val scope = canonical.currentApprovedScopeForTrustedRequestOrNull(context)
            ?: return@synchronized null
        if (!hostAndSandboxCurrent()) return@synchronized null
        val ticket = Planning8LocalUserRequestTicket(
            principal, scope, UUID.randomUUID().toString(),
        )
        val candidate = Request(context, pinned, ticket)
        request = candidate
        // Do not issue even a dormant ticket after Review Discard or host loss.
        if (!hostAndSandboxCurrent() ||
            canonical.currentApprovedScopeForTrustedRequestOrNull(context) != scope
        ) {
            request = null
            return@synchronized null
        }
        ticket
    }

    /**
     * Promote exactly once only AFTER canonical Workspace BeginApply has
     * committed a PREPARED Reality attempt for the SAME original Review.
     * The trusted caller MUST hold the owner grant lease across this
     * promotion and subsequent Reality COMMIT: this method alone does not
     * fence cross-process revocation or permit running Apply.
     *
     * Do not invoke this method or policyEpochOrNull under Reality's mutex:
     * they read Workspace and would invert Workspace -> Reality lock order.
     */
    fun promoteAfterCanonicalBeginApply(
        applyingContext: ConversationPlanReviewContext,
        identity: ApplyAttemptIdentity,
    ): Boolean = synchronized(monitor) {
        val current = request ?: return@synchronized false
        if (closed || current.boundApply != null || !hostAndSandboxCurrent())
            return@synchronized false
        val prior = current.context
        if (identity.planFingerprint != prior.planFingerprint ||
            applyingContext.planFingerprint != prior.planFingerprint ||
            applyingContext.conversationId != prior.conversationId ||
            applyingContext.reviewId != prior.reviewId ||
            applyingContext.authorityToken != prior.authorityToken ||
            prior.expectedRevision == Long.MAX_VALUE ||
            applyingContext.expectedRevision != prior.expectedRevision + 1
        ) return@synchronized false
        if (canonical.currentApplyingScopeForTrustedTransitionOrNull(
                applyingContext, identity,
            ) != current.ticket.scope || !hostAndSandboxCurrent()
        ) return@synchronized false
        request = current.copy(boundApply = BoundApply(applyingContext, identity))
        true
    }

    /**
     * Called by FaroV5Planning8ObjectGrantOwner under its monitor, OUTSIDE
     * a Reality.run callback. Before BeginApply it requires APPROVED; after
     * promotion it requires matching APPLYING + transactionId. The policy
     * epoch never changes, so a pinned grant can survive the CAS transition.
     */
    override fun policyEpochOrNull(
        principalId: String,
        scope: Planning8ObjectScope,
        pointer: ActiveGenerationPointer,
    ): String? = synchronized(monitor) {
        val current = request ?: return@synchronized null
        if (closed || current.ticket.principalId != principalId ||
            current.ticket.scope != scope || current.pinned != pointer ||
            !hostAndSandboxCurrent()
        ) return@synchronized null
        val canonicalScope = current.boundApply?.let {
            canonical.currentApplyingScopeForTrustedTransitionOrNull(it.context, it.identity)
        } ?: if (current.boundApply == null) {
            canonical.currentApprovedScopeForTrustedRequestOrNull(current.context)
        } else null
        if (canonicalScope != scope || !hostAndSandboxCurrent()) null
        else current.ticket.policyEpoch
    }

    /** This owner never survives a process, host, backup or generation change. */
    override fun close() {
        synchronized(monitor) {
            closed = true
            request = null
        }
    }
}
