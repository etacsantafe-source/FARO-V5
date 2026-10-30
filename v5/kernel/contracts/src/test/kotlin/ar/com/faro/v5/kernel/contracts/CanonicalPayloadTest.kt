package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CanonicalPayloadTest {
    @Test
    fun payloadCopiesSourceBytesOnIngress() {
        val source = "amount=100".toByteArray()
        val payload = CanonicalPayload.fromEncoded("finance-entry-v1", source)
        val fingerprint = payload.fingerprint

        source[0] = 'X'.code.toByte()

        assertEquals("amount=100", String(payload.copyBytes(), Charsets.UTF_8))
        assertEquals(fingerprint, payload.fingerprint)
    }

    @Test
    fun payloadReturnsDefensiveCopiesOnEgress() {
        val payload = CanonicalPayload.fromEncoded(
            "finance-entry-v1",
            "amount=100".toByteArray(),
        )
        val exported = payload.copyBytes()

        exported[0] = 'X'.code.toByte()

        assertEquals("amount=100", String(payload.copyBytes(), Charsets.UTF_8))
    }

    @Test
    fun payloadOwnsDeterministicFingerprint() {
        val first = CanonicalPayload.fromEncoded(
            "finance-entry-v1",
            "amount=100".toByteArray(),
        )
        val second = CanonicalPayload.fromEncoded(
            "finance-entry-v1",
            "amount=100".toByteArray(),
        )

        assertEquals(first, second)
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(
            "6e2370a1da9e3ee0dc7adcf576956cfbaddedb377ce6d64c32519a8645d8d920",
            first.fingerprint.value,
        )
    }

    @Test
    fun formatRevisionChangesPayloadFingerprint() {
        val first = CanonicalPayload.fromEncoded(
            "finance-entry-v1",
            "amount=100".toByteArray(),
        )
        val changed = CanonicalPayload.fromEncoded(
            "finance-entry-v2",
            "amount=100".toByteArray(),
        )

        assertNotEquals(first.fingerprint, changed.fingerprint)
    }

    @Test
    fun toStringNeverExposesCanonicalPayloadBytes() {
        val payload = CanonicalPayload.fromEncoded(
            "secret-v1",
            "super-secret-value".toByteArray(),
        )

        assertFalse(payload.toString().contains("super-secret-value"))
        assertEquals(
            "CanonicalPayload(formatId=secret-v1, size=18)",
            payload.toString(),
        )
    }
}
