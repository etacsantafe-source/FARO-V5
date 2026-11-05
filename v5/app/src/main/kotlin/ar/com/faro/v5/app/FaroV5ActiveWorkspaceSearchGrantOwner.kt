package ar.com.faro.v5.app

import java.util.UUID
import java.util.function.BooleanSupplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Host-owned, session-scoped Search read grant for an ALREADY admitted active
 * Workspace graph. Not a UI flag, Workspace write token or durable account ACL.
 *
 * The host alone opens/revokes this grant. Every grant gets a fresh identity,
 * even after a revoke/regrant in the same generation; a process restart never
 * restores a grant. The Flow only asks collectors to re-read actual authority.
 *
 * This owner does NOT certify cross-process invalidation or authorize shipping
 * V2: those checks remain with the trusted host and its activation gate.
 */
class FaroV5ActiveWorkspaceSearchGrantOwner(
    private val scope: String,
    private val hostAdmitted: BooleanSupplier,
) : AutoCloseable {
    init { require(scope.isNotBlank()) { "Search grant must have an owner scope" } }

    private val lock = Any()
    private val stateVersion = MutableStateFlow(0L)
    private var grant: String? = null
    private var closed = false

    /** Replays an invalidation to late collectors; never conveys permission. */
    val invalidations: Flow<Unit> = stateVersion.map { Unit }

    private fun admitted(): Boolean =
        runCatching { hostAdmitted.getAsBoolean() }.getOrDefault(false)

    /** Only a verified active host lifecycle may call this, never Search/UI. */
    fun grantAfterVerifiedHostOpen(): String {
        check(admitted()) { "Search cannot grant without an admitted active Workspace host" }
        return synchronized(lock) {
            check(!closed && grant == null) { "Search grant is closed or still active" }
            check(admitted()) { "Search host authority changed during grant" }
            val next = Math.addExact(stateVersion.value, 1L)
            val issued = "${scope}:${next}:${UUID.randomUUID()}"
            grant = issued
            stateVersion.value = next
            issued
        }
    }

    /** The actual grant + host authority must still match on every read/open. */
    fun epochOrNull(): String? {
        val initially = synchronized(lock) { if (closed) null else grant } ?: return null
        if (!admitted()) {
            // Recovery denial is a REVOCATION, not a temporary suppression.
            // Otherwise false->true could resurrect the same ACL epoch.
            revoke()
            return null
        }
        return synchronized(lock) {
            if (closed || grant != initially) null
            else if (!admitted()) {
                grant = null
                stateVersion.value = Math.addExact(stateVersion.value, 1L)
                null
            } else initially
        }
    }

    /** Invalidates old hits, independent of any subscriber or UI lifecycle. */
    fun revoke() {
        synchronized(lock) {
            if (!closed && grant != null) {
                grant = null
                stateVersion.value = Math.addExact(stateVersion.value, 1L)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                grant = null
                stateVersion.value = Math.addExact(stateVersion.value, 1L)
            }
        }
    }
}
