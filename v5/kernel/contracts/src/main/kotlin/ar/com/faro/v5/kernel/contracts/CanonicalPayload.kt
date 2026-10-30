package ar.com.faro.v5.kernel.contracts

/**
 * Immutable canonical payload frozen by Preparation.
 *
 * The backing bytes are copied on ingress and egress. Fingerprint is derived
 * from this exact snapshot, so payload bytes and identity cannot diverge.
 */
class CanonicalPayload private constructor(
    val formatId: String,
    bytes: ByteArray,
) {
    private val bytesSnapshot: ByteArray = bytes.copyOf()

    val size: Int
        get() = bytesSnapshot.size

    val fingerprint: PayloadFingerprint =
        PayloadFingerprint(
            CanonicalHashBuilder.domain("prepared-input")
                .string("formatId", formatId)
                .bytes("payload", bytesSnapshot)
                .sha256Hex()
        )

    fun copyBytes(): ByteArray = bytesSnapshot.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CanonicalPayload &&
            formatId == other.formatId &&
            bytesSnapshot.contentEquals(other.bytesSnapshot)

    override fun hashCode(): Int =
        31 * formatId.hashCode() + bytesSnapshot.contentHashCode()

    override fun toString(): String =
        "CanonicalPayload(formatId=" + formatId + ", size=" + size + ")"

    companion object {
        fun fromEncoded(
            formatId: String,
            bytes: ByteArray,
        ): CanonicalPayload {
            require(formatId.isNotBlank()) {
                "canonical input format id must not be blank"
            }
            return CanonicalPayload(
                formatId = formatId,
                bytes = bytes,
            )
        }
    }
}
