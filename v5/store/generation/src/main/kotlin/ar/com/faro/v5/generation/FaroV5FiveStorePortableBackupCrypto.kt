package ar.com.faro.v5.generation

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

enum class FiveStorePortableBackupOpenFailure {
    INVALID_ENVELOPE,
    AUTHENTICATION_FAILED,
    INVALID_FIVE_STORE_PAYLOAD,
    CRYPTO_UNAVAILABLE,
}

sealed interface FiveStorePortableBackupOpenResult {
    data class Opened(val backupSet: FaroV5FiveStoreSemanticSet) : FiveStorePortableBackupOpenResult {
        override fun toString(): String = "FiveStorePortableBackupOpenResult.Opened"
    }

    data class Rejected(val reason: FiveStorePortableBackupOpenFailure) :
        FiveStorePortableBackupOpenResult
}

/**
 * FAROBAK2: standalone authenticated envelope exclusively for a bounded FBS2
 * five-store semantic plaintext. FAROBAK1 and FBS1 remain four-store/V1.
 *
 * AES-GCM authenticates ALL header fields (including version, sizes and KDF
 * parameters) and the complete FBS2 payload. This is only an encryption/open
 * primitive: it does NOT establish a quiescent cross-store snapshot, an
 * independent restore-generation identity, a replay fence, or safe activation.
 * Caller must not export or restore until those separate A6.2 gates pass.
 *
 * Passphrases belong to the caller. The implementation clears derived keys,
 * plaintext and intermediate ciphertext; no secret is persisted.
 */
class FaroV5FiveStorePortableBackupCrypto(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun seal(set: FaroV5FiveStoreSemanticSet, passphrase: CharArray): ByteArray {
        requirePassphrase(passphrase)
        val plaintext = FaroV5FiveStoreSemanticCodec.encode(set)
        try {
            val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
            val header = encodeHeader(plaintext.size, salt, nonce)
            try {
                val keyBytes = deriveKey(passphrase, salt)
                try {
                    val cipher = Cipher.getInstance(AEAD)
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(keyBytes, "AES"),
                        GCMParameterSpec(TAG_BITS, nonce),
                    )
                    cipher.updateAAD(header)
                    val ciphertext = cipher.doFinal(plaintext)
                    try {
                        check(ciphertext.size == plaintext.size + TAG_BYTES) {
                            "FAROBAK2 unexpected ciphertext length"
                        }
                        return ByteArray(header.size + ciphertext.size).also { archive ->
                            header.copyInto(archive)
                            ciphertext.copyInto(archive, header.size)
                        }
                    } finally {
                        ciphertext.fill(0)
                    }
                } finally {
                    keyBytes.fill(0)
                }
            } finally {
                salt.fill(0)
                nonce.fill(0)
                header.fill(0)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    fun open(archive: ByteArray, passphrase: CharArray): FiveStorePortableBackupOpenResult {
        requirePassphrase(passphrase)
        val parsed = try {
            parseHeader(archive)
        } catch (_: Exception) {
            return FiveStorePortableBackupOpenResult.Rejected(
                FiveStorePortableBackupOpenFailure.INVALID_ENVELOPE
            )
        }
        var plaintext: ByteArray? = null
        try {
            val key = try {
                deriveKey(passphrase, parsed.salt)
            } catch (_: GeneralSecurityException) {
                return FiveStorePortableBackupOpenResult.Rejected(
                    FiveStorePortableBackupOpenFailure.CRYPTO_UNAVAILABLE
                )
            }
            try {
                val cipher = Cipher.getInstance(AEAD)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(TAG_BITS, parsed.nonce),
                )
                cipher.updateAAD(parsed.header)
                val authenticated = cipher.doFinal(archive, HEADER_BYTES, parsed.ciphertextBytes)
                plaintext = authenticated
                check(authenticated.size == parsed.plaintextBytes) {
                    "FAROBAK2 authenticated plaintext length mismatch"
                }
            } catch (_: BadPaddingException) {
                return FiveStorePortableBackupOpenResult.Rejected(
                    FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED
                )
            } catch (_: GeneralSecurityException) {
                return FiveStorePortableBackupOpenResult.Rejected(
                    FiveStorePortableBackupOpenFailure.CRYPTO_UNAVAILABLE
                )
            } finally {
                key.fill(0)
            }
            return try {
                FiveStorePortableBackupOpenResult.Opened(
                    FaroV5FiveStoreSemanticCodec.decode(
                        plaintext ?: error("FAROBAK2 authenticated plaintext missing")
                    )
                )
            } catch (_: Exception) {
                FiveStorePortableBackupOpenResult.Rejected(
                    FiveStorePortableBackupOpenFailure.INVALID_FIVE_STORE_PAYLOAD
                )
            }
        } finally {
            plaintext?.fill(0)
            parsed.clear()
        }
    }

    private fun encodeHeader(
        plaintextBytes: Int,
        salt: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        require(plaintextBytes in 1..FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES)
        require(salt.size == SALT_BYTES && nonce.size == NONCE_BYTES)
        return ByteArrayOutputStream(HEADER_BYTES).use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeLong(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(KDF_ID)
                output.writeInt(AEAD_ID)
                output.writeInt(PBKDF2_ITERATIONS)
                output.writeInt(SALT_BYTES)
                output.writeInt(NONCE_BYTES)
                output.writeInt(plaintextBytes)
                output.write(salt)
                output.write(nonce)
            }
            buffer.toByteArray().also { check(it.size == HEADER_BYTES) }
        }
    }

    private fun parseHeader(archive: ByteArray): ParsedHeader {
        check(archive.size in MIN_ARCHIVE_BYTES..MAX_ARCHIVE_BYTES) {
            "FAROBAK2 size outside bound"
        }
        return DataInputStream(ByteArrayInputStream(archive)).use { input ->
            check(input.readLong() == MAGIC) { "FAROBAK2 magic mismatch" }
            check(input.readInt() == VERSION) { "FAROBAK2 version mismatch" }
            check(input.readInt() == KDF_ID) { "FAROBAK2 KDF mismatch" }
            check(input.readInt() == AEAD_ID) { "FAROBAK2 AEAD mismatch" }
            check(input.readInt() == PBKDF2_ITERATIONS) { "FAROBAK2 work factor mismatch" }
            check(input.readInt() == SALT_BYTES && input.readInt() == NONCE_BYTES) {
                "FAROBAK2 salt/nonce sizes mismatch"
            }
            val plaintextBytes = input.readInt()
            check(plaintextBytes in 1..FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES) {
                "FAROBAK2 declared plaintext outside bound"
            }
            check(archive.size == HEADER_BYTES + plaintextBytes + TAG_BYTES) {
                "FAROBAK2 archive length mismatch"
            }
            val salt = ByteArray(SALT_BYTES)
            val nonce = ByteArray(NONCE_BYTES)
            input.readFully(salt)
            input.readFully(nonce)
            ParsedHeader(
                salt = salt,
                nonce = nonce,
                header = archive.copyOfRange(0, HEADER_BYTES),
                plaintextBytes = plaintextBytes,
                ciphertextBytes = plaintextBytes + TAG_BYTES,
            )
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded.also {
                check(it.size == KEY_BYTES) { "FAROBAK2 key size mismatch" }
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun requirePassphrase(value: CharArray) {
        require(value.size in 12..1024 && value.any { !it.isWhitespace() } &&
            value.none { it == '\u0000' }) { "FAROBAK2 passphrase outside safety policy" }
    }

    private data class ParsedHeader(
        val salt: ByteArray,
        val nonce: ByteArray,
        val header: ByteArray,
        val plaintextBytes: Int,
        val ciphertextBytes: Int,
    ) {
        fun clear() {
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    companion object {
        private const val MAGIC = 0x4641524F42414B32L // FAROBAK2
        private const val VERSION = 2
        private const val KDF_ID = 1
        private const val AEAD_ID = 1
        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val AEAD = "AES/GCM/NoPadding"
        const val PBKDF2_ITERATIONS = 600_000
        private const val KEY_BITS = 256
        private const val KEY_BYTES = KEY_BITS / 8
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val TAG_BITS = 128
        private const val HEADER_BYTES = 8 + 7 * 4 + SALT_BYTES + NONCE_BYTES
        private const val MIN_ARCHIVE_BYTES = HEADER_BYTES + 1 + TAG_BYTES
        const val MAX_ARCHIVE_BYTES =
            HEADER_BYTES + FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES + TAG_BYTES
    }
}
