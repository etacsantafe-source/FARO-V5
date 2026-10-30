package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class PortableBackupOpenFailure {
    INVALID_ENVELOPE,
    AUTHENTICATION_FAILED,
    INVALID_BACKUP_PAYLOAD,
    CRYPTO_UNAVAILABLE,
}

sealed interface PortableBackupOpenResult {
    data class Opened(
        val backupSet: SemanticBackupSet,
    ) : PortableBackupOpenResult

    data class Rejected(
        val failure: PortableBackupOpenFailure,
    ) : PortableBackupOpenResult
}

data class UntrustedPortableBackupEnvelopeMetadata(
    val envelopeVersion: Int,
    val kdf: String,
    val aead: String,
    val iterations: Int,
    val plaintextBytes: Int,
)

/**
 * Portable backup crypto boundary.
 *
 * The archive key is derived solely from the caller-provided passphrase and
 * archive salt. It is deliberately independent from Android Keystore so the
 * archive can move to another device. The semantic manifest/payload remains
 * inside the authenticated ciphertext.
 */
class PortableBackupArchiveCrypto(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun seal(
        backupSet: SemanticBackupSet,
        passphrase: CharArray,
    ): ByteArray {
        requirePassphrase(passphrase)
        val plaintext = SemanticBackupSetCodec.encode(backupSet)
        try {
            return encrypt(plaintext, passphrase)
        } finally {
            plaintext.fill(0)
        }
    }

    fun open(
        archive: ByteArray,
        passphrase: CharArray,
    ): PortableBackupOpenResult {
        requirePassphrase(passphrase)
        val parsed =
            try {
                parseEnvelope(archive)
            } catch (_: Exception) {
                return PortableBackupOpenResult.Rejected(
                    PortableBackupOpenFailure.INVALID_ENVELOPE
                )
            }

        val plaintext =
            try {
                decrypt(parsed, archive, passphrase)
            } catch (_: BadPaddingException) {
                return PortableBackupOpenResult.Rejected(
                    PortableBackupOpenFailure.AUTHENTICATION_FAILED
                )
            } catch (_: GeneralSecurityException) {
                return PortableBackupOpenResult.Rejected(
                    PortableBackupOpenFailure.CRYPTO_UNAVAILABLE
                )
            }

        return try {
            val decoded = SemanticBackupSetCodec.decode(plaintext)
            PortableBackupOpenResult.Opened(decoded)
        } catch (_: Exception) {
            PortableBackupOpenResult.Rejected(
                PortableBackupOpenFailure.INVALID_BACKUP_PAYLOAD
            )
        } finally {
            plaintext.fill(0)
        }
    }

    /**
     * Parses only the public envelope header. The returned metadata is
     * deliberately named UNTRUSTED because authenticity is known only after
     * successful open().
     */
    fun inspectUntrusted(archive: ByteArray): UntrustedPortableBackupEnvelopeMetadata? {
        val parsed =
            try {
                parseEnvelope(archive)
            } catch (_: Exception) {
                return null
            }
        return try {
            UntrustedPortableBackupEnvelopeMetadata(
                envelopeVersion = parsed.version,
                kdf = KDF_NAME,
                aead = AEAD_NAME,
                iterations = parsed.iterations,
                plaintextBytes = parsed.plaintextLength,
            )
        } finally {
            parsed.clear()
        }
    }

    private fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        require(plaintext.isNotEmpty() && plaintext.size <= SemanticBackupSetCodec.MAX_PLAINTEXT_BYTES) {
            "portable backup plaintext outside bound"
        }

        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val header =
            encodeHeader(
                iterations = DEFAULT_PBKDF2_ITERATIONS,
                salt = salt,
                nonce = nonce,
                plaintextLength = plaintext.size,
            )
        val keyBytes = deriveKey(passphrase, salt, DEFAULT_PBKDF2_ITERATIONS)
        try {
            val cipher = Cipher.getInstance(AEAD_NAME)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            check(ciphertext.size == plaintext.size + TAG_BYTES) {
                "portable backup AEAD output length mismatch"
            }

            return try {
                ByteArray(header.size + ciphertext.size).also { archive ->
                    header.copyInto(archive, destinationOffset = 0)
                    ciphertext.copyInto(archive, destinationOffset = header.size)
                }
            } finally {
                ciphertext.fill(0)
            }
        } finally {
            keyBytes.fill(0)
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    private fun decrypt(
        parsed: ParsedEnvelope,
        archive: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        var keyBytes: ByteArray? = null
        try {
            keyBytes = deriveKey(passphrase, parsed.salt, parsed.iterations)
            val cipher = Cipher.getInstance(AEAD_NAME)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, parsed.nonce),
            )
            cipher.updateAAD(parsed.header)
            val plaintext =
                cipher.doFinal(
                    archive,
                    parsed.ciphertextOffset,
                    parsed.ciphertextLength,
                )
            check(plaintext.size == parsed.plaintextLength) {
                "portable backup plaintext length mismatch"
            }
            return plaintext
        } finally {
            keyBytes?.fill(0)
            parsed.clear()
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory
                .getInstance(KDF_NAME)
                .generateSecret(spec)
                .encoded
                .also { key ->
                    check(key.size == KEY_BYTES) {
                        "portable backup derived key length mismatch"
                    }
                }
        } finally {
            spec.clearPassword()
        }
    }

    private fun parseEnvelope(archive: ByteArray): ParsedEnvelope {
        check(
            archive.size in
                MIN_ARCHIVE_BYTES..MAX_ARCHIVE_BYTES
        ) {
            "portable backup archive outside bound"
        }

        return DataInputStream(ByteArrayInputStream(archive)).use { input ->
            check(input.readLong() == MAGIC) { "portable backup magic mismatch" }
            val version = input.readInt()
            check(version == ENVELOPE_VERSION) {
                "unsupported portable backup envelope version"
            }
            check(input.readInt() == KDF_ID) { "unsupported portable backup KDF" }
            check(input.readInt() == AEAD_ID) { "unsupported portable backup AEAD" }

            val iterations = input.readInt()
            check(iterations == DEFAULT_PBKDF2_ITERATIONS) {
                "unsupported portable backup KDF work factor"
            }

            val saltLength = input.readInt()
            val nonceLength = input.readInt()
            val plaintextLength = input.readInt()
            check(saltLength == SALT_BYTES) { "portable backup salt length mismatch" }
            check(nonceLength == NONCE_BYTES) { "portable backup nonce length mismatch" }
            check(plaintextLength in 1..SemanticBackupSetCodec.MAX_PLAINTEXT_BYTES) {
                "portable backup plaintext length outside bound"
            }

            val expectedLength = HEADER_BYTES + plaintextLength + TAG_BYTES
            check(archive.size == expectedLength) {
                "portable backup archive length mismatch"
            }

            val salt = ByteArray(SALT_BYTES)
            val nonce = ByteArray(NONCE_BYTES)
            input.readFully(salt)
            input.readFully(nonce)

            val ciphertextLength = plaintextLength + TAG_BYTES
            check(input.available() == ciphertextLength) {
                "portable backup archive ciphertext length mismatch"
            }

            val header = archive.copyOfRange(0, HEADER_BYTES)
            ParsedEnvelope(
                version = version,
                iterations = iterations,
                plaintextLength = plaintextLength,
                salt = salt,
                nonce = nonce,
                header = header,
                ciphertextOffset = HEADER_BYTES,
                ciphertextLength = ciphertextLength,
            )
        }
    }

    private fun encodeHeader(
        iterations: Int,
        salt: ByteArray,
        nonce: ByteArray,
        plaintextLength: Int,
    ): ByteArray {
        require(iterations == DEFAULT_PBKDF2_ITERATIONS)
        require(salt.size == SALT_BYTES)
        require(nonce.size == NONCE_BYTES)
        require(plaintextLength in 1..SemanticBackupSetCodec.MAX_PLAINTEXT_BYTES)

        val header =
            ByteArrayOutputStream(HEADER_BYTES).use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(MAGIC)
                    output.writeInt(ENVELOPE_VERSION)
                    output.writeInt(KDF_ID)
                    output.writeInt(AEAD_ID)
                    output.writeInt(iterations)
                    output.writeInt(SALT_BYTES)
                    output.writeInt(NONCE_BYTES)
                    output.writeInt(plaintextLength)
                    output.write(salt)
                    output.write(nonce)
                }
                buffer.toByteArray()
            }
        check(header.size == HEADER_BYTES)
        return header
    }

    private fun requirePassphrase(passphrase: CharArray) {
        require(passphrase.size in MIN_PASSPHRASE_CHARS..MAX_PASSPHRASE_CHARS) {
            "portable backup passphrase length outside bound"
        }
        require(passphrase.any { !it.isWhitespace() }) {
            "portable backup passphrase must not be blank"
        }
        require(passphrase.none { it == '\u0000' }) {
            "portable backup passphrase must not contain NUL"
        }
    }

    private data class ParsedEnvelope(
        val version: Int,
        val iterations: Int,
        val plaintextLength: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val header: ByteArray,
        val ciphertextOffset: Int,
        val ciphertextLength: Int,
    ) {
        fun clear() {
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    companion object {
        private const val MAGIC: Long = 0x4641524F42414B31L // FAROBAK1
        private const val ENVELOPE_VERSION = 1
        private const val KDF_ID = 1
        private const val AEAD_ID = 1
        private const val KDF_NAME = "PBKDF2WithHmacSHA256"
        private const val AEAD_NAME = "AES/GCM/NoPadding"

        const val DEFAULT_PBKDF2_ITERATIONS = 600_000
        private const val KEY_BITS = 256
        private const val KEY_BYTES = KEY_BITS / 8
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val MIN_PASSPHRASE_CHARS = 12
        private const val MAX_PASSPHRASE_CHARS = 1024

        private const val HEADER_BYTES =
            8 + // magic
                (7 * 4) + // version, ids, iterations, lengths
                SALT_BYTES +
                NONCE_BYTES

        private const val MIN_ARCHIVE_BYTES = HEADER_BYTES + 1 + TAG_BYTES
        const val MAX_ARCHIVE_BYTES =
            HEADER_BYTES + SemanticBackupSetCodec.MAX_PLAINTEXT_BYTES + TAG_BYTES
    }
}
