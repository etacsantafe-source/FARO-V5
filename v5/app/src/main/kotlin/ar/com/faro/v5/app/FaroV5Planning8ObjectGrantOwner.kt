package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.util.UUID

/**
 * Trusted host POLICY boundary. A non-null epoch means this principal may use
 * THIS operation on THIS object at THIS pinned generation. Epochs MUST change
 * upon revoke/regrant and never be restored from an older backup.
 *
 * There is NO production implementation yet. In particular, a Workspace
 * authorityToken, Search grant or healthy pointer is NOT a policy decision.
 */
internal fun interface Planning8TrustedObjectPolicyPort {
    fun policyEpochOrNull(
        principalId: String,
        scope: Planning8ObjectScope,
        pointer: ActiveGenerationPointer,
    ): String?
}

/** A scope-specific, synchronous admission result; never a stored Apply ticket. */
internal sealed interface Planning8GrantLeaseResult<out T> {
    data class Admitted<T>(val value: T) : Planning8GrantLeaseResult<T>
    data object Denied : Planning8GrantLeaseResult<Nothing>
}

/**
 * Session-only object grant owner for an ALREADY verified active Planning8 host.
 * Does NOT authenticate principalId, derive ownership, approve a Review, execute
 * Apply or retain a transaction lease. Only a trusted future host may supply the
 * principal, host lifecycle epoch and independent policy. Missing policy DENIES.
 *
 * This owner is intentionally NOT wired to shipping or a v2CoreDev runtime.
 */
internal class FaroV5Planning8ObjectGrantOwner(
    private val principalId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val hostLifecycleEpochOrNull: () -> String?,
    private val expectedHostEpoch: String,
    private val policy: Planning8TrustedObjectPolicyPort? = null,
) : Planning8ObjectAclReadPort, AutoCloseable {

    init {
        require(valid(principalId)) { "Planning8 principal must come from trusted host" }
        require(valid(expectedHostEpoch)) { "Planning8 host lifecycle epoch is required" }
    }

    private data class Grant(val nonce: String, val policyEpoch: String)

    private val monitor = Any()
    private val instanceId = UUID.randomUUID().toString()
    private var sequence = 0L
    private val grants = mutableMapOf<Planning8ObjectScope, Grant>()
    private var closed = false

    private fun valid(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value.length <= 160 &&
            value.none { it.code < 32 || it.code == 127 }

    private fun hostCurrent(): Boolean = runCatching {
        activePointer.read() == pinned &&
            hostLifecycleEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private fun policyEpoch(scope: Planning8ObjectScope): String? = runCatching {
        policy?.policyEpochOrNull(principalId, scope, pinned)?.takeIf(::valid)
    }.getOrNull()

    /**
     * Trusted host ONLY. A caller's object ID never grants permission by itself.
     * Rechecks the independent policy/host before AND after issuance.
     * Every successful issue changes the nonce even for the same object.
     */
    fun grantAfterTrustedPolicyCheck(scope: Planning8ObjectScope): String? =
        synchronized(monitor) {
            if (closed) return@synchronized null
            if (!hostCurrent()) {
                grants.clear()
                return@synchronized null
            }
            val first = policyEpoch(scope) ?: run {
                grants.remove(scope)
                return@synchronized null
            }
            if (!hostCurrent() || policyEpoch(scope) != first) {
                grants.remove(scope)
                return@synchronized null
            }
            val next = Math.addExact(sequence, 1L)
            val nonce = instanceId + ":" + next + ":" + UUID.randomUUID().toString()
            sequence = next
            val issued = Grant(nonce, first)
            grants[scope] = issued
            // A policy can change while the grant is being prepared.
            if (!hostCurrent() || policyEpoch(scope) != first ||
                grants[scope] != issued
            ) {
                grants.remove(scope)
                return@synchronized null
            }
            nonce
        }

    /**
     * Read-only port consumed by the existing approved-Review guard.
     * This is a fresh, scoped lookup, NOT an Apply permit that can be cached.
     */
    override fun epochOrNull(
        scope: Planning8ObjectScope,
        pointer: ActiveGenerationPointer,
    ): String? = synchronized(monitor) {
        if (closed) return@synchronized null
        val observed = grants[scope] ?: return@synchronized null
        if (pointer != pinned || !hostCurrent()) {
            grants.clear()
            return@synchronized null
        }
        val currentPolicy = policyEpoch(scope)
        if (currentPolicy != observed.policyEpoch || grants[scope] != observed ||
            !hostCurrent()
        ) {
            if (!hostCurrent()) grants.clear() else grants.remove(scope)
            return@synchronized null
        }
        observed.nonce
    }

    /**
     * Guard the entire synchronous transaction callback against revoke/revokeAll/close
     * issued THROUGH THIS owner. The caller must include Reality COMMIT/ROLLBACK
     * inside action and use the exact scoped nonce minted by a trusted policy.
     * An external policy change, pointer swap or another process is NOT fenced
     * by this in-memory monitor; production Planning8 ACL stays disabled.
     */
    fun <T> withCurrentGrantLease(
        scope: Planning8ObjectScope,
        pointer: ActiveGenerationPointer,
        expectedNonce: String,
        action: () -> T,
    ): Planning8GrantLeaseResult<T> = synchronized(monitor) {
        if (expectedNonce.isBlank() || epochOrNull(scope, pointer) != expectedNonce) {
            return@synchronized Planning8GrantLeaseResult.Denied
        }
        Planning8GrantLeaseResult.Admitted(action())
    }

    /** Explicit host revocation for ONE operation+object. */
    fun revoke(scope: Planning8ObjectScope) {
        synchronized(monitor) { grants.remove(scope) }
    }

    /** Called on recovery start, host stop or ownership-policy invalidation. */
    fun revokeAll() {
        synchronized(monitor) { grants.clear() }
    }

    /** Never restore grants from Workspace backup, SQLite or a previous process. */
    override fun close() {
        synchronized(monitor) {
            closed = true
            grants.clear()
        }
    }
}
