package ar.com.faro.v5.platform.android.security;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import ar.com.faro.v5.control.spi.SecretId;
import ar.com.faro.v5.control.spi.SecretStore;
import ar.com.faro.v5.control.spi.SecretValue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Device-local SecretStore backed by Android Keystore.
 *
 * Ciphertext is stored under noBackupFilesDir so device-bound key material is never paired
 * with restored ciphertext on another device. No plaintext fallback exists.
 */
public final class AndroidKeystoreSecretStore implements SecretStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALIAS_PREFIX = "faro_v5_secret_";
    private static final String STORE_DIR = "faro-v5-secrets";
    private static final int MAGIC = 0x46563553; // FV5S
    private static final int FORMAT_VERSION = 1;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_SECRET_BYTES = 16 * 1024;
    private static final int MIN_IV_BYTES = 12;
    private static final int MAX_IV_BYTES = 32;
    private static final int MAX_CIPHERTEXT_BYTES = MAX_SECRET_BYTES + 64;

    private final Context context;

    public AndroidKeystoreSecretStore(Context context) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
    }

    @Override
    public ReadResult read(SecretId id) {
        AtomicFile atomicFile = new AtomicFile(secretFile(id));
        byte[] iv = null;
        byte[] ciphertext = null;
        byte[] plaintext = null;
        try {
            EncodedSecret encoded = readEncoded(atomicFile);
            iv = encoded.iv;
            ciphertext = encoded.ciphertext;

            SecretKey key = existingKey(id);
            if (key == null) {
                return ReadResult.unavailable(UnavailableReason.KEY_UNAVAILABLE);
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            plaintext = cipher.doFinal(ciphertext);
            if (plaintext.length == 0 || plaintext.length > MAX_SECRET_BYTES) {
                return ReadResult.unavailable(UnavailableReason.INVALID_PAYLOAD);
            }
            return ReadResult.available(SecretValue.copyOf(plaintext));
        } catch (FileNotFoundException missing) {
            return ReadResult.missing();
        } catch (InvalidSecretPayload invalid) {
            return ReadResult.unavailable(UnavailableReason.INVALID_PAYLOAD);
        } catch (AEADBadTagException invalidCiphertext) {
            return ReadResult.unavailable(UnavailableReason.INVALID_PAYLOAD);
        } catch (GeneralSecurityException securityFailure) {
            return ReadResult.unavailable(UnavailableReason.CRYPTO_FAILURE);
        } catch (IOException ioFailure) {
            return ReadResult.unavailable(UnavailableReason.IO_FAILURE);
        } finally {
            wipe(iv);
            wipe(ciphertext);
            wipe(plaintext);
        }
    }

    @Override
    public MutationResult put(SecretId id, SecretValue value) {
        byte[] plaintext = value.copyBytes();
        byte[] iv = null;
        byte[] ciphertext = null;
        try {
            if (plaintext.length == 0 || plaintext.length > MAX_SECRET_BYTES) {
                return MutationResult.unavailable(UnavailableReason.INVALID_PAYLOAD);
            }

            File directory = secretDirectory();
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                return MutationResult.unavailable(UnavailableReason.IO_FAILURE);
            }

            SecretKey key = getOrCreateKey(id);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            iv = cipher.getIV();
            if (iv == null || iv.length < MIN_IV_BYTES || iv.length > MAX_IV_BYTES) {
                return MutationResult.unavailable(UnavailableReason.CRYPTO_FAILURE);
            }
            ciphertext = cipher.doFinal(plaintext);
            if (ciphertext.length == 0 || ciphertext.length > MAX_CIPHERTEXT_BYTES) {
                return MutationResult.unavailable(UnavailableReason.CRYPTO_FAILURE);
            }

            writeEncoded(new AtomicFile(secretFile(id)), iv, ciphertext);
            return MutationResult.stored();
        } catch (GeneralSecurityException securityFailure) {
            return MutationResult.unavailable(UnavailableReason.CRYPTO_FAILURE);
        } catch (IOException ioFailure) {
            return MutationResult.unavailable(UnavailableReason.IO_FAILURE);
        } finally {
            wipe(plaintext);
            wipe(iv);
            wipe(ciphertext);
        }
    }

    /**
     * Deletes every FARO V5 secret ciphertext and Android Keystore key.
     *
     * <p>This is intentionally broader than SecretStore.delete(id) and is used only by the
     * reviewed full-reset adapter. It is idempotent and confined to FARO's key alias prefix and
     * dedicated no-backup directory.
     */
    public void deleteAllFaroSecrets() {
        try {
            KeyStore keyStore = loadKeyStore();
            Enumeration<String> aliases = keyStore.aliases();
            List<String> faroAliases = new ArrayList<>();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                    faroAliases.add(alias);
                }
            }
            for (String alias : faroAliases) {
                keyStore.deleteEntry(alias);
            }

            File directory = secretDirectory();
            if (directory.exists()) {
                File[] files = directory.listFiles();
                if (files == null) {
                    throw new IOException("FARO secret directory cannot be listed");
                }
                for (File file : files) {
                    if (!file.isFile() || !file.delete()) {
                        throw new IOException("FARO secret artifact could not be deleted");
                    }
                }
                if (!directory.delete() && directory.exists()) {
                    throw new IOException("FARO secret directory could not be deleted");
                }
            }
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalStateException("FARO secret reset failed", failure);
        }
    }

    @Override
    public MutationResult delete(SecretId id) {
        AtomicFile atomicFile = new AtomicFile(secretFile(id));
        boolean hadCiphertext = atomicFile.getBaseFile().exists();
        try {
            KeyStore keyStore = loadKeyStore();
            String alias = keyAlias(id);
            boolean hadKey = keyStore.containsAlias(alias);
            if (hadKey) {
                keyStore.deleteEntry(alias);
            }
            atomicFile.delete();
            return hadCiphertext || hadKey
                    ? MutationResult.deleted()
                    : MutationResult.missing();
        } catch (GeneralSecurityException securityFailure) {
            return MutationResult.unavailable(UnavailableReason.CRYPTO_FAILURE);
        } catch (IOException ioFailure) {
            return MutationResult.unavailable(UnavailableReason.IO_FAILURE);
        }
    }

    private EncodedSecret readEncoded(AtomicFile file) throws IOException, InvalidSecretPayload {
        try (DataInputStream input =
                     new DataInputStream(new BufferedInputStream(file.openRead()))) {
            if (input.readInt() != MAGIC) {
                throw new InvalidSecretPayload();
            }
            if (input.readUnsignedByte() != FORMAT_VERSION) {
                throw new InvalidSecretPayload();
            }

            int ivLength = input.readUnsignedByte();
            int ciphertextLength = input.readInt();
            if (ivLength < MIN_IV_BYTES || ivLength > MAX_IV_BYTES) {
                throw new InvalidSecretPayload();
            }
            if (ciphertextLength <= 0 || ciphertextLength > MAX_CIPHERTEXT_BYTES) {
                throw new InvalidSecretPayload();
            }

            byte[] iv = new byte[ivLength];
            byte[] ciphertext = new byte[ciphertextLength];
            try {
                input.readFully(iv);
                input.readFully(ciphertext);
                if (input.read() != -1) {
                    throw new InvalidSecretPayload();
                }
                return new EncodedSecret(iv, ciphertext);
            } catch (IOException | InvalidSecretPayload failure) {
                wipe(iv);
                wipe(ciphertext);
                throw failure;
            }
        }
    }

    private void writeEncoded(AtomicFile file, byte[] iv, byte[] ciphertext) throws IOException {
        FileOutputStream raw = null;
        try {
            raw = file.startWrite();
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(raw));
            output.writeInt(MAGIC);
            output.writeByte(FORMAT_VERSION);
            output.writeByte(iv.length);
            output.writeInt(ciphertext.length);
            output.write(iv);
            output.write(ciphertext);
            output.flush();
            raw.getFD().sync();
            file.finishWrite(raw);
            raw = null;
        } finally {
            if (raw != null) {
                file.failWrite(raw);
            }
        }
    }

    private SecretKey existingKey(SecretId id) throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore();
        java.security.Key key = keyStore.getKey(keyAlias(id), null);
        return key instanceof SecretKey ? (SecretKey) key : null;
    }

    private SecretKey getOrCreateKey(SecretId id) throws GeneralSecurityException, IOException {
        SecretKey existing = existingKey(id);
        if (existing != null) {
            return existing;
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );
        generator.init(
                new KeyGenParameterSpec.Builder(
                        keyAlias(id),
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
        );
        return generator.generateKey();
    }

    private KeyStore loadKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    private File secretDirectory() {
        return new File(context.getNoBackupFilesDir(), STORE_DIR);
    }

    private File secretFile(SecretId id) {
        return new File(secretDirectory(), stableName(id) + ".bin");
    }

    private String keyAlias(SecretId id) {
        return KEY_ALIAS_PREFIX + stableName(id);
    }

    private String stableName(SecretId id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(id.value().getBytes(StandardCharsets.UTF_8));
            try {
                StringBuilder value = new StringBuilder(digest.length * 2);
                for (byte item : digest) {
                    value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
                }
                return value.toString();
            } finally {
                wipe(digest);
            }
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static final class EncodedSecret {
        private final byte[] iv;
        private final byte[] ciphertext;

        private EncodedSecret(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }

    private static final class InvalidSecretPayload extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
