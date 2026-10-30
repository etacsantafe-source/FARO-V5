package ar.com.faro.v5.kernel.contracts

@JvmInline
value class PromptVersion(val value: Int) {
    init {
        require(value > 0) {
            "prompt version must be positive"
        }
    }
}

data class PromptRef(
    val promptId: PromptId,
    val version: PromptVersion,
)

class PromptArtifact(
    val ref: PromptRef,
    val content: CanonicalPayload,
) {
    val fingerprint: PromptFingerprint =
        PromptFingerprint(
            CanonicalHashBuilder.domain("prompt-artifact")
                .string("promptId", ref.promptId.value)
                .string("version", ref.version.value.toString())
                .string("formatId", content.formatId)
                .bytes("content", content.copyBytes())
                .sha256Hex()
        )

    override fun equals(other: Any?): Boolean =
        other is PromptArtifact &&
            ref == other.ref &&
            content == other.content

    override fun hashCode(): Int =
        31 * ref.hashCode() + content.hashCode()

    override fun toString(): String =
        "PromptArtifact(ref=" + ref + ", fingerprint=" + fingerprint.value + ")"
}

interface PromptRegistry {
    fun resolve(ref: PromptRef): PromptArtifact?
    fun versions(promptId: PromptId): List<PromptVersion>
}
