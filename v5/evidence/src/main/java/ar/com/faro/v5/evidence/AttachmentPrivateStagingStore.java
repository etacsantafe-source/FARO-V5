package ar.com.faro.v5.evidence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Private, bounded staging for attachment bytes that must outlive an external URI grant/process.
 *
 * The target name is derived only from the verified content digest; untrusted display names never
 * participate in filesystem paths.
 */
public final class AttachmentPrivateStagingStore {
    private final Path root;

    public AttachmentPrivateStagingStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public StagedAttachment stage(AttachmentIngress.EphemeralAttachment attachment) throws IOException {
        Objects.requireNonNull(attachment, "attachment");
        byte[] bytes = attachment.copyBytes();
        Path temp = null;
        try {
            if (bytes.length != attachment.sizeBytes()) {
                throw new IOException("attachment size changed before staging");
            }
            if (!AttachmentIngress.sha256Hex(bytes).equals(attachment.contentSha256Hex())) {
                throw new IOException("attachment digest changed before staging");
            }

            Files.createDirectories(root);
            Path target = root.resolve("attachment-" + attachment.contentSha256Hex() + ".bin").normalize();
            requireOwned(target);

            if (Files.exists(target)) {
                verifyExisting(target, attachment.contentSha256Hex(), attachment.sizeBytes());
                return StagedAttachment.from(attachment, target);
            }

            temp = root.resolve(".attachment-" + UUID.randomUUID() + ".part").normalize();
            requireOwned(temp);
            try (FileOutputStream output = new FileOutputStream(temp.toFile())) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }

            moveNoReplace(temp, target);
            temp = null;

            verifyExisting(target, attachment.contentSha256Hex(), attachment.sizeBytes());
            return StagedAttachment.from(attachment, target);
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (temp != null) Files.deleteIfExists(temp);
        }
    }

    public FileInputStream open(StagedAttachment staged) throws IOException {
        Objects.requireNonNull(staged, "staged");
        Path path = staged.privatePath().toAbsolutePath().normalize();
        requireOwned(path);
        verifyExisting(path, staged.contentSha256Hex(), staged.sizeBytes());
        return new FileInputStream(path.toFile());
    }

    public void delete(StagedAttachment staged) throws IOException {
        Objects.requireNonNull(staged, "staged");
        Path path = staged.privatePath().toAbsolutePath().normalize();
        requireOwned(path);
        Files.deleteIfExists(path);
    }

    private static void moveNoReplace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (java.nio.file.FileAlreadyExistsException raced) {
            Files.deleteIfExists(temp);
            return;
        } catch (AtomicMoveNotSupportedException noAtomicMove) {
            // Fall through to a non-atomic no-replace move on filesystems without atomic rename.
        }

        try {
            Files.move(temp, target);
        } catch (java.nio.file.FileAlreadyExistsException raced) {
            Files.deleteIfExists(temp);
        }
    }

    private void requireOwned(Path path) {
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("attachment staging path escapes private root");
        }
    }

    private static void verifyExisting(Path path, String expectedDigest, long expectedSize) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) != expectedSize) {
            throw new IOException("staged attachment size mismatch");
        }
        String digest = sha256File(path);
        if (!digest.equals(expectedDigest)) {
            throw new IOException("staged attachment digest mismatch");
        }
    }

    private static String sha256File(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
        byte[] buffer = new byte[16 * 1024];
        try (FileInputStream input = new FileInputStream(path.toFile())) {
            while (true) {
                int count = input.read(buffer);
                if (count < 0) break;
                if (count > 0) digest.update(buffer, 0, count);
            }
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
        byte[] bytes = digest.digest();
        try {
            StringBuilder result = new StringBuilder(64);
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static final class StagedAttachment {
        private final String displayName;
        private final String mediaType;
        private final long sizeBytes;
        private final String contentSha256Hex;
        private final String sourceFingerprintSha256;
        private final Path privatePath;

        private StagedAttachment(
                String displayName,
                String mediaType,
                long sizeBytes,
                String contentSha256Hex,
                String sourceFingerprintSha256,
                Path privatePath) {
            this.displayName = displayName;
            this.mediaType = mediaType;
            this.sizeBytes = sizeBytes;
            this.contentSha256Hex = contentSha256Hex;
            this.sourceFingerprintSha256 = sourceFingerprintSha256;
            this.privatePath = privatePath;
        }

        static StagedAttachment from(AttachmentIngress.EphemeralAttachment attachment, Path privatePath) {
            return new StagedAttachment(
                    attachment.displayName(),
                    attachment.mediaType(),
                    attachment.sizeBytes(),
                    attachment.contentSha256Hex(),
                    attachment.sourceFingerprintSha256(),
                    privatePath.toAbsolutePath().normalize());
        }

        public String displayName() { return displayName; }
        public String mediaType() { return mediaType; }
        public long sizeBytes() { return sizeBytes; }
        public String contentSha256Hex() { return contentSha256Hex; }
        public String sourceFingerprintSha256() { return sourceFingerprintSha256; }
        Path privatePath() { return privatePath; }

        public String stagingId() { return "sha256:" + contentSha256Hex; }

        @Override
        public String toString() {
            return "StagedAttachment(mediaType=" + mediaType +
                    ", sizeBytes=" + sizeBytes +
                    ", stagingId=" + stagingId() + ")";
        }
    }
}
