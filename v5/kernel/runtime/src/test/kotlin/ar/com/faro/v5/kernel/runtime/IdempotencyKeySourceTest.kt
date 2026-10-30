package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class IdempotencyKeySourceTest {
    @Test
    fun `new semantic intentions may receive distinct opaque keys`() {
        val source = SequencedKeySource()

        val first = source.newKey()
        val second = source.newKey()

        assertNotEquals(first, second)
        assertEquals(IdempotencyKey("intent-1"), first)
        assertEquals(IdempotencyKey("intent-2"), second)
    }

    @Test
    fun `retry identity is reused from prepared state rather than regenerated`() {
        val source = SequencedKeySource()
        val preparedKey = source.newKey()

        val retryKey = preparedKey

        assertEquals(preparedKey, retryKey)
        assertEquals(1, source.calls)
    }

    private class SequencedKeySource : IdempotencyKeySource {
        var calls: Int = 0
            private set

        override fun newKey(): IdempotencyKey {
            calls += 1
            return IdempotencyKey("intent-" + calls)
        }
    }
}
