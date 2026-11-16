package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.util.UUID

/** B2-B DEV: a scoped canonical reader is NOT an owner grant or Apply permit. */
internal fun interface S4W01B2FinanceApprovedScopeReadPort {
    fun read(context: ConversationPlanReviewContext): S4W01B2FinanceApprovedScope?
}

/** Host-owned independent policy source; null means DENY. */
internal fun interface S4W01B2FinancePolicyEpochPort {
    fun epochOrNull(
        principalId: String,
        scope: S4W01B2FinanceApprovedScope,
        pointer: ActiveGenerationPointer,
    ): String?
}

internal sealed interface S4W01B2FinanceLeaseResult<out T> {
    data class Admitted<T>(val value: T) : S4W01B2FinanceLeaseResult<T>
    data object Denied : S4W01B2FinanceLeaseResult<Nothing>
}

/**
 * DEV session-only Finance grant owner. Nothing is persisted or composed into
 * shipping Android. The monitor serializes ONLY revokes through this instance;
 * it does NOT fence cross-process or external policy revocation through SQL
 * COMMIT, authenticate humans, execute Apply or mint an execution receipt.
 */
internal class S4W01B2FinanceObjectGrantOwner(
    private val principalId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val hostLifecycleEpochOrNull: () -> String?,
    private val expectedHostEpoch: String,
    private val approvedScope: S4W01B2FinanceApprovedScopeReadPort,
    private val policy: S4W01B2FinancePolicyEpochPort? = null,
    private val hostReady: () -> Boolean,
) : AutoCloseable {
    private data class Grant(val nonce: String, val policyEpoch: String)

    private val monitor = Any()
    private val sessionId = UUID.randomUUID().toString()
    private val grants = mutableMapOf<S4W01B2FinanceApprovedScope, Grant>()
    private var sequence = 0L
    private var closed = false

    init {
        require(valid(principalId))
        require(valid(expectedHostEpoch))
    }

    private fun valid(value: String): Boolean =
        value.isNotBlank() && value.length <= 160 &&
            value == value.trim() &&
            value.none { it.code < 32 || it.code == 127 }

    private fun current(): Boolean = runCatching {
        !closed && hostReady() && activePointer.read() == pinned &&
            hostLifecycleEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private fun currentPolicyEpoch(scope: S4W01B2FinanceApprovedScope): String? =
        runCatching {
            policy?.epochOrNull(principalId, scope, pinned)?.takeIf(::valid)
        }.getOrNull()

    /** Still NOT a human Apply grant. Only a trusted DEV host may call it. */
    fun grantAfterTrustedPolicyCheck(
        context: ConversationPlanReviewContext,
        expectedScope: S4W01B2FinanceApprovedScope,
    ): String? = synchronized(monitor) {
        if (!current() || approvedScope.read(context) != expectedScope)
            return@synchronized null
        val epoch = currentPolicyEpoch(expectedScope)
            ?: return@synchronized null
        if (!current() || currentPolicyEpoch(expectedScope) != epoch ||
            approvedScope.read(context) != expectedScope
        ) return@synchronized null

        sequence = Math.addExact(sequence, 1L)
        val nonce = sessionId + ":" + sequence + ":" + UUID.randomUUID().toString()
        val issued = Grant(nonce, epoch)
        grants[expectedScope] = issued
        if (!current() || currentPolicyEpoch(expectedScope) != epoch ||
            approvedScope.read(context) != expectedScope ||
            grants[expectedScope] != issued
        ) {
            grants.remove(expectedScope)
            return@synchronized null
        }
        nonce
    }

    fun epochOrNull(
        scope: S4W01B2FinanceApprovedScope,
        pointer: ActiveGenerationPointer,
    ): String? = synchronized(monitor) {
        val issued = grants[scope] ?: return@synchronized null
        if (pointer != pinned || !current()) {
            grants.remove(scope)
            return@synchronized null
        }
        if (currentPolicyEpoch(scope) != issued.policyEpoch ||
            grants[scope] != issued || !current()
        ) {
            grants.remove(scope)
            return@synchronized null
        }
        issued.nonce
    }

    /** Synchronous in-process lease only. Never imply cross-process safety. */
    fun <T> withCurrentGrantLease(
        scope: S4W01B2FinanceApprovedScope,
        pointer: ActiveGenerationPointer,
        nonce: String,
        action: () -> T,
    ): S4W01B2FinanceLeaseResult<T> = synchronized(monitor) {
        if (!valid(nonce) || epochOrNull(scope, pointer) != nonce)
            return@synchronized S4W01B2FinanceLeaseResult.Denied
        S4W01B2FinanceLeaseResult.Admitted(action())
    }

    fun revoke(scope: S4W01B2FinanceApprovedScope) {
        synchronized(monitor) { grants.remove(scope) }
    }

    fun revokeAll() {
        synchronized(monitor) { grants.clear() }
    }

    override fun close() {
        synchronized(monitor) {
            closed = true
            grants.clear()
        }
    }
}
