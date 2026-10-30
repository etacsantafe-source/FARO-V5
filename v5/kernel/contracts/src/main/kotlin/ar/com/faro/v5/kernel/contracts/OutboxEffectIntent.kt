package ar.com.faro.v5.kernel.contracts

/**
 * Durable external-effect intent created by a committed Reality transaction.
 *
 * The payload is already a canonical immutable byte envelope. Reality never
 * serializes arbitrary runtime objects, uses toString(), or Java serialization.
 * A later dispatcher resolves the payload format explicitly after commit.
 */
data class OutboxEffectIntent(
    val effectId: EffectId,
    val sourceTransactionId: TransactionId,
    val idempotencyKey: IdempotencyKey,
    val payload: CanonicalPayload,
)
