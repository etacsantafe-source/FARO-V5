package ar.com.faro.v5.evidence

import java.util.UUID

/**
 * F2C1B1B1A: DEV-only Evidence2 OWNER read-scope fence independent from
 * Household10. The owner source MUST be the actual already-leased typed
 * Evidence2 port when composed. No renderer, model, Intent or UID may supply
 * a replacement policy. This is NOT a production Android/user ACL.
 */
class CommercialEvidence2DevReadScopeOwner(
    principal: String,
    generationId: String,
    pointerSequence: Long,
    hostEpoch: String,
    private val currentIdentity: () -> Identity?,
    private val ownerReady: () -> Boolean,
    private val source: CommercialEvidenceStagedTypedPortabilityPort,
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
        val product: CommercialProductKey,
        val watermark: String,
        val allRecords: List<PortableCommercialTypedObservation>,
        val selectedRecords: List<PortableCommercialTypedObservation>,
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

    private fun realTypedSnapshot(): CommercialEvidenceStagedTypedPortabilitySnapshot? =
        runCatching { source.snapshot() }.getOrNull()?.takeIf {
            it.schemaVersion == 2 && it.sourceWatermark.matches(Regex("[0-9a-f]{64}")) &&
                it.records.size <= CommercialEvidencePortabilitySnapshot.MAX_RECORDS
        }

    /** No null/legacy terms, no fuzzy matching, no inferred current price. */
    fun issueAfterTrustedOwnerDecision(
        selectedProduct: CommercialProductKey,
        trustedAdmission: () -> Boolean,
    ): String? = synchronized(monitor) {
        if (issued != null || !current() || !safeTrusted(trustedAdmission)) {
            revokeLocked()
            return@synchronized null
        }
        val snapshot = realTypedSnapshot() ?: run {
            revokeLocked()
            return@synchronized null
        }
        val selected = snapshot.records.filter {
            it.observation.productKey == selectedProduct.value
        }
        if (selected.isEmpty() || selected.any { it.terms == null }) {
            revokeLocked()
            return@synchronized null
        }
        val grant = Grant(
            selectedProduct, snapshot.sourceWatermark,
            snapshot.records.toList(), selected.toList(),
            UUID.randomUUID().toString(), trustedAdmission,
        )
        issued = grant
        if (!admittedLocked(grant)) {
            revokeLocked()
            null
        } else grant.nonce
    }

    fun admitted(selectedProduct: CommercialProductKey, nonce: String): Boolean =
        synchronized(monitor) {
            val grant = issued ?: return@synchronized false
            if (selectedProduct != grant.product || nonce != grant.nonce ||
                !admittedLocked(grant)
            ) {
                revokeLocked()
                return@synchronized false
            }
            true
        }

    private fun admittedLocked(grant: Grant): Boolean {
        if (!current() || !safeTrusted(grant.trustedAdmission)) return false
        val snapshot = realTypedSnapshot() ?: return false
        val selected = snapshot.records.filter {
            it.observation.productKey == grant.product.value
        }
        return snapshot.sourceWatermark == grant.watermark &&
            snapshot.records == grant.allRecords &&
            selected.isNotEmpty() && selected == grant.selectedRecords &&
            selected.all { it.terms != null } &&
            current() && safeTrusted(grant.trustedAdmission)
    }

    fun isRevoked(): Boolean = synchronized(monitor) { closed }
    private fun revokeLocked() {
        closed = true
        issued = null
    }
    override fun close() = synchronized(monitor) { revokeLocked() }
}
