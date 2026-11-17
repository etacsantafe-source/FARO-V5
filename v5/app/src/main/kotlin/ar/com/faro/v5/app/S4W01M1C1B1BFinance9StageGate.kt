package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.ApplyTransactionFinalCommitGate
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.finance.api.FinanceOperations
import ar.com.faro.v5.store.reality.Finance9TrustedStageGrant
import ar.com.faro.v5.store.reality.RealityFinance9TrustedStageGrantPort

/**
 * DEV/STAGED9-only M1C1B1B wiring. NEVER registered in an Android Activity,
 * production CompositionRoot, Surface, Chat or as a product Apply authority.
 *
 * boundReview and grant are PRE-ADMITTED references. This bridge does NOT
 * authenticate the real Android biometric CryptoObject, verify signatures or
 * issue durable grants: test callers can synthesize both. A future trusted
 * Android host must establish that separate origin before mounting any Apply.
 */
internal class S4W01M1C1B1BFinance9StageGate(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val boundReview: S4W01M1C1AReviewPresenceMatch,
    private val grant: Finance9TrustedStageGrant,
    private val expectedApplicationUid: Int,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val realityPort: RealityFinance9TrustedStageGrantPort,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ApplyTransactionFinalCommitGate, AutoCloseable {
    private val mutex = Any()
    private var consumed = false
    private val hex64 = Regex("[0-9a-f]{64}")

    init {
        require(expectedApplicationUid > 0)
        require(expectedHostEpoch.isNotBlank() &&
            expectedHostEpoch == expectedHostEpoch.trim())
    }

    private fun current(): Boolean = runCatching {
        hostReady() && hostEpochOrNull() == expectedHostEpoch &&
            pointer.read() == pinned
    }.getOrDefault(false)

    override fun allowCommit(
        preparedPlan: PreparedPlanView,
        identity: ApplyAttemptIdentity,
        receipt: ExecutionReceipt,
        checkedNodes: List<ApplyPlanNode>,
        transactionScope: RealityTransactionScope,
    ): Boolean = synchronized(mutex) {
        if (consumed) return@synchronized false
        consumed = true
        if (!current() ||
            boundReview.pointer != pinned ||
            boundReview.applicationUid != expectedApplicationUid ||
            boundReview.reviewId.isBlank() ||
            boundReview.reviewRevision < 0L ||
            !hex64.matches(boundReview.challengeDigestHex) ||
            !hex64.matches(boundReview.signatureDigestHex) ||
            boundReview.challengeDigestHex == boundReview.signatureDigestHex ||
            grant.generationId != pinned.generationId.value ||
            grant.pointerSequence != pinned.sequence ||
            grant.operationId != boundReview.operationId ||
            grant.objectScope != boundReview.objectScope ||
            transactionScope.transactionId != identity.transactionId ||
            receipt.transactionId != identity.transactionId ||
            receipt.planFingerprint != identity.planFingerprint ||
            identity.planFingerprint != preparedPlan.planFingerprint ||
            boundReview.planFingerprint != preparedPlan.planFingerprint
        ) return@synchronized false

        val node = preparedPlan.applyPlan.nodesInTopologicalOrder().singleOrNull()
            ?: return@synchronized false
        if (checkedNodes.size != 1 || checkedNodes.single() != node ||
            node.dependsOn.isNotEmpty() ||
            node.preparedOperation.contract.capabilityId != FinanceOperations.CAPABILITY_ID ||
            node.preparedOperation.contract.canonicalExecutor != FinanceOperations.EXECUTOR_ID ||
            node.preparedOperation.contract.operationId.value != grant.operationId ||
            node.preparedOperation.contract.operationId !in
                setOf(FinanceOperations.ACCOUNT_CREATE, FinanceOperations.ENTRY_RECORD)
        ) return@synchronized false

        val now = nowMs()
        if (now < 0L || !current()) return@synchronized false
        // Deliberately do not catch: exceptions become RecoveryRequired in the
        // existing ApplyTransactionExecutor and force the SAME SQL rollback.
        val accepted = realityPort.consumeInsideFinalCommit(
            transactionScope, grant, identity, receipt, now,
        )
        accepted && current()
    }

    override fun close() {
        synchronized(mutex) { consumed = true }
    }
}
