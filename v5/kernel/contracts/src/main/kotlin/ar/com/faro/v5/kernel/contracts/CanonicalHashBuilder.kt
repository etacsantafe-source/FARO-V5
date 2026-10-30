package ar.com.faro.v5.kernel.contracts

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Versioned canonical byte framing shared by Preparation protocol values.
 *
 * Every token is UTF-8 or caller-supplied bytes prefixed by a 4-byte
 * big-endian length. Callers must choose field order explicitly.
 */
class CanonicalHashBuilder private constructor(
    domain: String,
) {
    private val output = ByteArrayOutputStream()

    init {
        require(domain.isNotBlank()) { "canonical hash domain must not be blank" }
        writeFrame(CANONICAL_VERSION.toByteArray(StandardCharsets.UTF_8))
        writeFrame(domain.toByteArray(StandardCharsets.UTF_8))
    }

    fun string(name: String, value: String): CanonicalHashBuilder =
        bytes(name, value.toByteArray(StandardCharsets.UTF_8))

    fun bytes(name: String, value: ByteArray): CanonicalHashBuilder {
        require(name.isNotBlank()) { "canonical field name must not be blank" }
        writeFrame(name.toByteArray(StandardCharsets.UTF_8))
        writeFrame(value)
        return this
    }

    fun sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(output.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun writeFrame(value: ByteArray) {
        val size = value.size
        output.write((size ushr 24) and 0xff)
        output.write((size ushr 16) and 0xff)
        output.write((size ushr 8) and 0xff)
        output.write(size and 0xff)
        output.write(value)
    }

    companion object {
        private const val CANONICAL_VERSION = "faro-canonical-v1"

        fun domain(domain: String): CanonicalHashBuilder =
            CanonicalHashBuilder(domain)
    }
}
