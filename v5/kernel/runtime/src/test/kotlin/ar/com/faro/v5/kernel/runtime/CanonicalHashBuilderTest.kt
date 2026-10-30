package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CanonicalHashBuilderTest {
    @Test
    fun sameExplicitFieldsProduceSameDigest() {
        val first = fixture("100")
        val second = fixture("100")

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun materialFieldChangeProducesNewDigest() {
        assertNotEquals(fixture("100"), fixture("101"))
    }

    @Test
    fun lengthFramingPreventsDelimiterAmbiguity() {
        val first = CanonicalHashBuilder.domain("plan")
            .string("left", "ab")
            .string("right", "c")
            .sha256Hex()

        val second = CanonicalHashBuilder.domain("plan")
            .string("left", "a")
            .string("right", "bc")
            .sha256Hex()

        assertNotEquals(first, second)
    }

    @Test
    fun domainSeparationProducesDifferentDigest() {
        val plan = CanonicalHashBuilder.domain("plan")
            .string("value", "same")
            .sha256Hex()
        val contract = CanonicalHashBuilder.domain("contract")
            .string("value", "same")
            .sha256Hex()

        assertNotEquals(plan, contract)
    }

    @Test
    fun goldenVectorPinsCanonicalFramingAndSha256() {
        val digest = CanonicalHashBuilder.domain("plan")
            .string("operationId", "finance.snapshot.read")
            .bytes("payload", """{"amount":"100"}""".toByteArray())
            .sha256Hex()

        assertEquals(
            "ac177e9e08c46b7e91472e77fc32d966572552a788dac7dbd00053e1952a4c7b",
            digest,
        )
    }

    private fun fixture(amount: String): String =
        CanonicalHashBuilder.domain("plan")
            .string("operationId", "finance.snapshot.read")
            .string("amount", amount)
            .sha256Hex()
}
