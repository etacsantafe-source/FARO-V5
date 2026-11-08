package ar.com.faro.v5.app

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
    private data class Request(
        val context: ConversationPlanReviewContext,
        val pinned: ActiveGenerationPointer,
        val ticket: Planning8LocalUserRequestTicket,
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
     * Called by FaroV5Planning8ObjectGrantOwner under its monitor.
     * An APPROVED Review must remain current when issuing/checking a grant.
     * An APPLYING Review is deliberately NOT accepted here: transactional
     * lifetime admission still needs its own independently audited unit.
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
        val approvedScope = canonical.currentApprovedScopeForTrustedRequestOrNull(current.context)
        if (approvedScope != scope || !hostAndSandboxCurrent()) null
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
