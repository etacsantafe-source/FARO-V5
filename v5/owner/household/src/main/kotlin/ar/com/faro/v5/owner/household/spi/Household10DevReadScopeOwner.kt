package ar.com.faro.v5.owner.household.spi

import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import java.util.UUID

/**
 * F2C1B1B1A: an ephemeral Household OWNER read-scope fence for isolated DEV.
 *
 * A trusted composition MUST provide the REAL Household10 owner read source
 * from the already-selected, leased generation. The callable and host approval
 * can be faked in a unit test and DO NOT authenticate Android users or issue
 * production ACLs. This owner never exports its source or Household data.
 */
class Household10DevReadScopeOwner(
    principal: String,
    generationId: String,
    pointerSequence: Long,
    hostEpoch: String,
    private val currentIdentity: () -> Identity?,
    private val ownerReady: () -> Boolean,
    private val source: () -> HouseholdNeedsSnapshot?,
) : AutoCloseable {
    data class Identity(
        val principal: String,
        val generationId: String,
        val pointerSequence: Long,
        val hostEpoch: String,
    ) {
        init {
            fun valid(s: String) = s.length in 1..160 && s.isNotBlank() &&
                s == s.trim() && s.none(Char::isISOControl)
            require(valid(principal) && valid(generationId) && valid(hostEpoch))
            require(pointerSequence > 0L)
        }
    }

    private data class Grant(
        val needId: HouseholdNeedId,
        val row: HouseholdNeed,
        val revision: Long,
        val nonce: String,
        val trustedAdmission: () -> Boolean,
    )

    private val bound = Identity(principal, generationId, pointerSequence, hostEpoch)
    private val monitor = Any()
    private var closed = false
    private var issued: Grant? = null

    private fun safeTrusted(admission: () -> Boolean) =
        runCatching { admission() }.getOrDefault(false)

    private fun current() = runCatching {
        !closed && ownerReady() && currentIdentity() == bound &&
            ownerReady() && currentIdentity() == bound
    }.getOrDefault(false)

    private fun verifiedRow(id: HouseholdNeedId): Pair<Long, HouseholdNeed>? {
        val snapshot = runCatching { source() }.getOrNull() ?: return null
        if (snapshot.revision < 0L || snapshot.needs.size > 100_000) return null
        val matches = snapshot.needs.filter { it.id == id }
        val row = matches.singleOrNull() ?: return null
        if (row.status != HouseholdNeedStatus.OPEN ||
            row.revision < 0L || row.revision > snapshot.revision
        ) return null
        return snapshot.revision to row
    }

    /**
     * Not a free-standing grant factory: ownerReady + source must be bound by
     * the trusted DEV generation host, not an Activity/Intent/AI/renderer.
     */
    fun issueAfterTrustedOwnerDecision(
        selectedNeed: HouseholdNeedId,
        trustedAdmission: () -> Boolean,
    ): String? = synchronized(monitor) {
        if (issued != null || !current() || !safeTrusted(trustedAdmission)) {
            revokeLocked()
            return@synchronized null
        }
        val (revision, row) = verifiedRow(selectedNeed) ?: run {
            revokeLocked()
            return@synchronized null
        }
        val grant = Grant(selectedNeed, row, revision, UUID.randomUUID().toString(), trustedAdmission)
        issued = grant
        if (!admittedLocked(grant)) {
            revokeLocked()
            null
        } else grant.nonce
    }

    /** A mismatch or stale read poisons this exact session irreversibly. */
    fun admitted(selectedNeed: HouseholdNeedId, nonce: String): Boolean = synchronized(monitor) {
        val grant = issued ?: return@synchronized false
        if (selectedNeed != grant.needId || nonce != grant.nonce || !admittedLocked(grant)) {
            revokeLocked()
            return@synchronized false
        }
        true
    }

    private fun admittedLocked(grant: Grant): Boolean {
        if (!current() || !safeTrusted(grant.trustedAdmission)) return false
        val value = verifiedRow(grant.needId) ?: return false
        return value.first == grant.revision && value.second == grant.row &&
            current() && safeTrusted(grant.trustedAdmission)
    }

    fun isRevoked(): Boolean = synchronized(monitor) { closed }
    private fun revokeLocked() {
        closed = true
        issued = null
    }
    override fun close() = synchronized(monitor) { revokeLocked() }
}
