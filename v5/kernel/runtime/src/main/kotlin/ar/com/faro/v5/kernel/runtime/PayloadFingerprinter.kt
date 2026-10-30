package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint

interface CanonicalInputEncoder<I : Any> {
    val formatId: String
    fun encode(input: I): ByteArray
}

object PayloadFingerprinter {
    fun <I : Any> canonicalize(
        input: I,
        encoder: CanonicalInputEncoder<I>,
    ): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            formatId = encoder.formatId,
            bytes = encoder.encode(input),
        )

    fun <I : Any> fingerprint(
        input: I,
        encoder: CanonicalInputEncoder<I>,
    ): PayloadFingerprint =
        canonicalize(input, encoder).fingerprint
}
