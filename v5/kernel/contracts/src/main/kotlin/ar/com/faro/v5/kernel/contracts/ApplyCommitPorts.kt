package ar.com.faro.v5.kernel.contracts

/**
 * Persists the durable control artifacts of a successful Reality Apply inside
 * the same transaction scope as owner mutations.
 *
 * A concrete Reality Store implementation must atomically:
 * - insert the immutable ExecutionReceipt,
 * - persist the supplied Reality Outbox intents,
 * - mark the matching ApplyAttempt COMMITTED,
 * - and make those changes visible only with the surrounding Reality commit.
 *
 * Physical SQLite crash-atomicity is a T2 conformance responsibility.
 */
interface ApplyCommitPort {
    fun commit(
        transactionScope: RealityTransactionScope,
        identity: ApplyAttemptIdentity,
        outboxIntents: List<OutboxEffectIntent>,
    ): ExecutionReceipt
}
