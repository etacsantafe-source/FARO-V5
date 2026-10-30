package ar.com.faro.v5.kernel.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PayloadFingerprinterTest {
    private data class AmountInput(val amount: String)

    @Test
    fun sameTypedSemanticInputAndFormatProduceSameCanonicalPayload() {
        val first = PayloadFingerprinter.canonicalize(AmountInput("100"), encoder())
        val second = PayloadFingerprinter.canonicalize(AmountInput("100"), encoder())

        assertEquals(first, second)
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(
            "6e2370a1da9e3ee0dc7adcf576956cfbaddedb377ce6d64c32519a8645d8d920",
            first.fingerprint.value,
        )
    }

    @Test
    fun materialInputChangeProducesDifferentFingerprint() {
        assertNotEquals(
            PayloadFingerprinter.fingerprint(AmountInput("100"), encoder()),
            PayloadFingerprinter.fingerprint(AmountInput("101"), encoder()),
        )
    }

    @Test
    fun canonicalFormatRevisionChangesFingerprint() {
        assertNotEquals(
            PayloadFingerprinter.fingerprint(
                AmountInput("100"),
                encoder("finance-entry-v1"),
            ),
            PayloadFingerprinter.fingerprint(
                AmountInput("100"),
                encoder("finance-entry-v2"),
            ),
        )
    }

    @Test
    fun blankCanonicalFormatFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            PayloadFingerprinter.canonicalize(
                AmountInput("100"),
                object : CanonicalInputEncoder<AmountInput> {
                    override val formatId = " "
                    override fun encode(input: AmountInput): ByteArray =
                        input.amount.toByteArray()
                },
            )
        }
    }

    @Test
    fun runtimeDoesNotUseInputToStringAsCanonicalOracle() {
        class NoString(val value: String) {
            override fun toString(): String = error("toString must not be used")
        }

        val encoder = object : CanonicalInputEncoder<NoString> {
            override val formatId = "nostring-v1"
            override fun encode(input: NoString): ByteArray =
                input.value.toByteArray()
        }

        val payload = PayloadFingerprinter.canonicalize(
            NoString("stable"),
            encoder,
        )

        assertEquals(64, payload.fingerprint.value.length)
    }

    private fun encoder(
        format: String = "finance-entry-v1",
    ) = object : CanonicalInputEncoder<AmountInput> {
        override val formatId = format
        override fun encode(input: AmountInput): ByteArray =
            ("amount=" + input.amount).toByteArray()
    }
}
