package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Sources are separately authorized. A technical Android UID is NOT a human login. */
internal enum class FaroV5LocalReadDomain {
    REALITY_RECEIPT, WORKSPACE_MESSAGE, MATTER_DUE, AUXILIARY_NOTICE,
    REMINDER_OCCURRENCE, MONTHLY_PLAN_ITEM, BOOKMARK_METADATA,
}

/**
 * Memory-only, per-installation READ authority CORE; not yet mounted by shipping.
 *
 * The future trusted Activity must call grantAfterTrustedForegroundReadAction
 * ONLY from a real user gesture for exactly the relevant screen sources.
 * A host/model/Intent passing a boolean is NOT evidence of that gesture.
 *
 * The caller must construct the sandbox with AndroidPlatformSandboxFactsReader
 * from the trusted Application and the canonical private-root pointer.
 * This core neither creates a user account nor authorizes Apply, Search,
 * a background worker, network egress, or a globally complete count.
 */
internal class FaroV5LocalInstallReadAuthority(
    private val sandbox: FaroV5AndroidSandboxPrincipal,
    private val activePointer: ActiveGenerationPointerPort,
    private val hostReady: () -> Boolean,
    private val foregroundReady: () -> Boolean,
) : AutoCloseable {
    private val monitor = Any()
    private val originalEvidence = sandbox.currentEvidenceOrNull()
    private val originalPointer = originalEvidence?.pinnedGeneration
    private val principal = originalEvidence?.let { "local-install:" + it.sessionNonce }
    private val grants = mutableMapOf<FaroV5LocalReadDomain, String>()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    private var poisoned = originalEvidence == null
    private var closed = false

    /** Emission is a UI refresh hint, NOT a replacement for post-SQL epoch checks. */
    val invalidations: Flow<Unit> get() = changes

    private fun revokeLocked(permanent: Boolean) {
        val hadGrants = grants.isNotEmpty()
        grants.clear()
        if (permanent) poisoned = true
        if (hadGrants || permanent) changes.tryEmit(Unit)
    }

    /** A->B->A, host death or sandbox mismatch permanently kills this instance. */
    private fun hostCurrentLocked(): Boolean {
        if (closed || poisoned) return false
        val valid = runCatching {
            val baseline = originalEvidence ?: return@runCatching false
            val pinned = originalPointer ?: return@runCatching false
            hostReady() && activePointer.read() == pinned &&
                sandbox.currentEvidenceOrNull() == baseline &&
                activePointer.read() == pinned && hostReady()
        }.getOrDefault(false)
        if (!valid) revokeLocked(permanent = true)
        return valid
    }

    /** Leaving foreground drops all grants; coming back needs a NEW explicit action. */
    private fun foregroundCurrentLocked(): Boolean {
        val foreground = runCatching { foregroundReady() }.getOrDefault(false)
        if (!foreground) revokeLocked(permanent = false)
        return foreground
    }

    /** Technical session label only; not authenticated identity or authorization. */
    fun technicalPrincipalOrNull(): String? = synchronized(monitor) {
        if (hostCurrentLocked()) principal else null
    }

    /**
     * Host-only entrypoint: no default grants, not safe to call from Compose
     * composition/startup, model output, intents, or background notifications.
     */
    fun grantAfterTrustedForegroundReadAction(
        domains: Set<FaroV5LocalReadDomain>,
    ): Boolean = synchronized(monitor) {
        if (domains.isEmpty() || !hostCurrentLocked() ||
            !foregroundCurrentLocked()
        ) return@synchronized false
        for (domain in domains) grants[domain] = UUID.randomUUID().toString()
        changes.tryEmit(Unit)
        true
    }

    fun epochOrNull(
        principalId: String,
        domain: FaroV5LocalReadDomain,
        pinned: ActiveGenerationPointer,
    ): String? = synchronized(monitor) {
        if (!hostCurrentLocked() || !foregroundCurrentLocked() ||
            principalId != principal || pinned != originalPointer
        ) return@synchronized null
        grants[domain]
    }

    fun historyReadPolicy(): N015TrustedHistoryReadPolicyPort =
        N015TrustedHistoryReadPolicyPort { principalId, domain, pinned ->
            val source = when (domain) {
                FaroV5ActivityDomain.REALITY_RECEIPT ->
                    FaroV5LocalReadDomain.REALITY_RECEIPT
                FaroV5ActivityDomain.WORKSPACE_MESSAGE ->
                    FaroV5LocalReadDomain.WORKSPACE_MESSAGE
            }
            epochOrNull(principalId, source, pinned)
        }

    fun planningDueReadPolicy(): Planning8TrustedDueReadPolicyPort =
        Planning8TrustedDueReadPolicyPort { principalId, domain, pinned ->
            val source = when (domain) {
                FaroV5PlanningDueDomain.REMINDER_OCCURRENCE ->
                    FaroV5LocalReadDomain.REMINDER_OCCURRENCE
                FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM ->
                    FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM
            }
            epochOrNull(principalId, source, pinned)
        }

    fun matterDueEpochOrNull(
        principalId: String, pinned: ActiveGenerationPointer,
    ): String? = epochOrNull(principalId, FaroV5LocalReadDomain.MATTER_DUE, pinned)

    /** Revoke only a selected source; a later action mints a DIFFERENT epoch. */
    fun revoke(domain: FaroV5LocalReadDomain) {
        synchronized(monitor) {
            if (grants.remove(domain) != null) changes.tryEmit(Unit)
        }
    }

    fun revokeAll() {
        synchronized(monitor) { revokeLocked(permanent = false) }
    }

    override fun close() {
        synchronized(monitor) {
            if (!closed) {
                closed = true
                revokeLocked(permanent = true)
                sandbox.close()
            }
        }
    }
}
