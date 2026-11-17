package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceRead
import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceReadPort
import ar.com.faro.v5.store.reality.RealityFinance9ReadPolicyObservationRequest

/**
 * W01-B3B1B1B0: DEV/STAGED9 double-observation consistency fence, NOT read authority.
 *
 * principalHashOrNull and expectedPolicyEpoch are untrusted INJECTABLE test
 * inputs. Neither they nor a matching SQL result authenticate Android users.
 * This class does NOT implement FinanceReadPort, mount UI, cache balances,
 * listen to OS-process changes, or guarantee freshness after the last SELECT.
 * A later trusted Keystore/UID host and revocation-aware UI owner are REQUIRED.
 */
internal class S4W01B3B1B1B0ObservedReadFence(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val principalHashOrNull: () -> String?,
    private val expectedPolicyEpoch: Long,
    private val source: RealityFinance9ObservedSourceReadPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val mutex = Any()
    private var closed = false
    private val hashPattern = Regex("[0-9a-f]{64}")

    init {
        require(expectedHostEpoch.isNotBlank() &&
            expectedHostEpoch == expectedHostEpoch.trim())
        require(expectedPolicyEpoch > 0L)
    }

    private fun stillPinned(): Boolean = !closed && runCatching {
        hostReady() && hostEpochOrNull() == expectedHostEpoch &&
            pointer.read() == pinned
    }.getOrDefault(false)

    private fun samplePrincipal(): String? =
        runCatching { principalHashOrNull() }.getOrNull()
            ?.takeIf(hashPattern::matches)

    private fun reject(): RealityFinance9ObservedSourceRead? {
        closed = true // any failed observation poisons this session permanently
        return null
    }

    private fun matches(
        observed: RealityFinance9ObservedSourceRead,
        req: RealityFinance9ReadPolicyObservationRequest,
    ): Boolean = observed.observedPolicy.generationId == req.generationId &&
        observed.observedPolicy.pointerSequence == req.pointerSequence &&
        observed.observedPolicy.policyEpoch == req.expectedPolicyEpoch

    /** A matching second observation is NOT a lease or authorization for UI. */
    fun observedForDevOnly(): RealityFinance9ObservedSourceRead? = synchronized(mutex) {
        if (!stillPinned()) return@synchronized reject()
        val hash = samplePrincipal() ?: return@synchronized reject()
        val beforeTime = runCatching { nowMs() }.getOrNull()
            ?.takeIf { it >= 0L } ?: return@synchronized reject()
        val firstReq = RealityFinance9ReadPolicyObservationRequest(
            principalHash = hash,
            generationId = pinned.generationId.value,
            pointerSequence = pinned.sequence,
            expectedPolicyEpoch = expectedPolicyEpoch,
            observedAtMs = beforeTime,
        )
        val before = runCatching { source.observe(firstReq) }.getOrNull()
            ?: return@synchronized reject()
        if (!matches(before, firstReq) || !stillPinned() ||
            samplePrincipal() != hash
        ) return@synchronized reject()

        val afterTime = runCatching { nowMs() }.getOrNull()
            ?.takeIf { it >= beforeTime } ?: return@synchronized reject()
        val afterReq = firstReq.copy(observedAtMs = afterTime)
        // A FRESH SQL snapshot is needed; never reconstruct from the first.
        val after = runCatching { source.observe(afterReq) }.getOrNull()
            ?: return@synchronized reject()
        if (after != before || !matches(after, afterReq) || !stillPinned() ||
            samplePrincipal() != hash
        ) return@synchronized reject()
        after // remains ONLY an observed DEV DTO, not trusted display data
    }

    override fun close() {
        synchronized(mutex) { closed = true }
    }
}
