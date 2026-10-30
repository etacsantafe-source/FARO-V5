package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class OutboxEffectIntentTest {
    @Test
    fun `outbox intent pins effect transaction idempotency and canonical payload`() {
        val payload = CanonicalPayload.fromEncoded(
            "notification-v1",
            "hello".toByteArray(),
        )
        val intent = OutboxEffectIntent(
            effectId = EffectId("effect-1"),
            sourceTransactionId = TransactionId("tx-1"),
            idempotencyKey = IdempotencyKey("delivery-1"),
            payload = payload,
        )

        assertEquals(EffectId("effect-1"), intent.effectId)
        assertEquals(TransactionId("tx-1"), intent.sourceTransactionId)
        assertEquals(IdempotencyKey("delivery-1"), intent.idempotencyKey)
        assertEquals("notification-v1", intent.payload.formatId)
        assertArrayEquals("hello".toByteArray(), intent.payload.copyBytes())
    }

    @Test
    fun `changing canonical payload changes outbox intent by value`() {
        val first = OutboxEffectIntent(
            effectId = EffectId("effect-1"),
            sourceTransactionId = TransactionId("tx-1"),
            idempotencyKey = IdempotencyKey("delivery-1"),
            payload = CanonicalPayload.fromEncoded(
                "notification-v1",
                "hello".toByteArray(),
            ),
        )
        val changed = first.copy(
            payload = CanonicalPayload.fromEncoded(
                "notification-v1",
                "changed".toByteArray(),
            ),
        )

        assertNotEquals(first, changed)
    }
}
