package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * D1B1A: only an INFORMATIONAL observation of the existing ACTIVE Finance owner.
 *
 * A future D1B1B Android owner must supply the read port from its SINGLE open
 * FaroV5V2CoreDevAuthority.Session, and real Application-backed sandbox facts.
 * Callbacks are injectable for JVM tests and ARE NOT trusted by themselves.
 * This result is NOT a UI display lease, a Finance9 provenance guarantee,
 * a human identity, or a Finance Apply authorization.
 *
 * No Activity/CompositionRoot creates this class; it opens no SQLite store,
 * creates no owner, calls no biometric API, caches no Finance data and writes
 * nothing. Security-lock default OFF does not remove internal owner fencing.
 */
internal data class S4W01D1B1AActiveFinanceObservation(
    val generationId: String,
    val pointerSequence: Long,
    val ownerRevision: Long,
    val source: FinanceSnapshot,
) {
    companion object {
        const val SOURCE_KIND: String = "ACTIVE_FINANCE_SNAPSHOT_NOT_FINANCE9"
    }
}

/**
 * Short-lived, fail-closed, read-only seam for the certified ACTIVE owner.
 * A caller can forge any constructor callback. The future W01 Android host
 * MUST bind only to real Application/UID facts + its own active owner session.
 */
internal class S4W01D1B1AActiveFinanceSnapshotRead(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedApplicationUid: Int,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val ownerReady: () -> Boolean,
    private val foregroundReady: () -> Boolean,
    private val sessionAclEpochOrNull: () -> String?,
    private val sandboxEvidenceOrNull: () -> AndroidSandboxTechnicalEvidence?,
    private val owner: FinanceSnapshotReadApi,
) : AutoCloseable {
    companion object {
        const val DEV_APPLICATION_ID: String = "ar.com.faro.alpha.w01financedev"
        private const val MAX_ACCOUNTS = 100
        private const val MAX_ENTRIES = 5_000
    }

    private data class FenceSample(
        val aclEpoch: String,
        val evidence: AndroidSandboxTechnicalEvidence,
    )

    private val readLock = Any()
    private val closed = AtomicBoolean(false)

    init {
        require(expectedApplicationUid > 0)
        require(expectedHostEpoch.isNotBlank() &&
            expectedHostEpoch == expectedHostEpoch.trim())
        require(pinned.sequence > 0L && pinned.generationId.value.isNotBlank())
    }

    private fun sampleOrNull(): FenceSample? = runCatching {
        if (closed.get() || !ownerReady() || !foregroundReady() ||
            hostEpochOrNull() != expectedHostEpoch ||
            pointer.read() != pinned
        ) return@runCatching null

        val acl = sessionAclEpochOrNull()
            ?.takeIf { it.isNotBlank() && it == it.trim() }
            ?: return@runCatching null
        val sandbox = sandboxEvidenceOrNull() ?: return@runCatching null
        if (sandbox.applicationId != DEV_APPLICATION_ID ||
            sandbox.applicationUid != expectedApplicationUid ||
            sandbox.pinnedGeneration != pinned ||
            sandbox.sessionNonce.isBlank()
        ) return@runCatching null

        if (closed.get() || !ownerReady() || !foregroundReady() ||
            hostEpochOrNull() != expectedHostEpoch ||
            pointer.read() != pinned ||
            sessionAclEpochOrNull() != acl
        ) null else FenceSample(acl, sandbox)
    }.getOrNull()

    private fun deny(): S4W01D1B1AActiveFinanceObservation? {
        closed.set(true) // even if the original ACL/host/pointer later reappears
        return null
    }

    /**
     * Two complete canonical Finance snapshots, not an invented Finance9 SQL
     * read. No account is disclosed if an owner/sandbox/lifecycle check fails.
     * Callers may only treat the result as a DEV observation; UI mounting is
     * explicitly OUT OF SCOPE and requires a separate trusted-host review.
     */
    fun observedForDevOnly(): S4W01D1B1AActiveFinanceObservation? =
        synchronized(readLock) {
            val before = sampleOrNull() ?: return@synchronized deny()
            val first = runCatching { owner.read() }.getOrNull()
                ?: return@synchronized deny()
            if (sampleOrNull() != before || closed.get()) return@synchronized deny()

            val second = runCatching { owner.read() }.getOrNull()
                ?: return@synchronized deny()
            if (first != second ||
                first.revision != second.revision ||
                first.accounts.size > MAX_ACCOUNTS ||
                first.entries.size > MAX_ENTRIES
            ) return@synchronized deny()

            // Even a syntactically valid FinanceSnapshot may overflow a ledger.
            // No summation across currencies; each account stays in its own unit.
            if (!runCatching {
                first.accounts.forEach { first.ledgerMinor(it.accountId) }
            }.isSuccess) return@synchronized deny()

            if (sampleOrNull() != before || closed.get()) return@synchronized deny()
            S4W01D1B1AActiveFinanceObservation(
                generationId = pinned.generationId.value,
                pointerSequence = pinned.sequence,
                ownerRevision = first.revision,
                source = first,
            ).takeUnless { closed.get() }
        }

    fun isClosed(): Boolean = closed.get()

    override fun close() {
        // Non-blocking cancellation even while the read thread is in SQL.
        closed.set(true)
    }
}
