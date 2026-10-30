package ar.com.faro.v5.evidence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded, provider-neutral attachment ingress.
 *
 * External content remains untrusted. Source locators are represented only by a SHA-256 fingerprint;
 * raw Android/content URIs never become Evidence authority.
 */
public final class AttachmentIngress {
    public static final int DEFAULT_MAX_FILE_BYTES = 12 * 1024 * 1024;
    public static final int DEFAULT_MAX_ATTACHMENTS_PER_TURN = 4;
    public static final int DEFAULT_MAX_TOTAL_BYTES = 24 * 1024 * 1024;
    public static final int MAX_DISPLAY_NAME_CHARS = 240;
    public static final int MAX_MEDIA_TYPE_CHARS = 127;

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MEDIA_TYPE =
            Pattern.compile("[a-z0-9!#$&^_.+\\-]+/[a-z0-9!#$&^_.+\\-]+");

    private AttachmentIngress() {}

    public interface Source {
        String sourceFingerprintSha256();
        Metadata metadata() throws IOException;
        InputStream openStream() throws IOException;
    }

    public static final class Metadata {
        private final String displayName;
        private final String mediaType;
        private final Long declaredSizeBytes;

        public Metadata(String displayName, String mediaType, Long declaredSizeBytes) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
            this.declaredSizeBytes = declaredSizeBytes;
        }

        public String displayName() { return displayName; }
        public String mediaType() { return mediaType; }
        public Long declaredSizeBytes() { return declaredSizeBytes; }
    }

    public static final class Policy {
        private final int maxFileBytes;
        private final int maxAttachmentsPerTurn;
        private final int maxTotalBytes;
        private final Set<String> exactMediaTypes;
        private final Set<String> mediaTypePrefixes;

        public Policy(
                int maxFileBytes,
                int maxAttachmentsPerTurn,
                int maxTotalBytes,
                Set<String> exactMediaTypes,
                Set<String> mediaTypePrefixes) {
            if (maxFileBytes <= 0 || maxFileBytes > DEFAULT_MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("maxFileBytes must be positive and bounded");
            }
            if (maxAttachmentsPerTurn <= 0 || maxAttachmentsPerTurn > 16) {
                throw new IllegalArgumentException("maxAttachmentsPerTurn must be positive and bounded");
            }
            if (maxTotalBytes < maxFileBytes || maxTotalBytes > 64 * 1024 * 1024) {
                throw new IllegalArgumentException("maxTotalBytes must cover a file and remain bounded");
            }
            this.maxFileBytes = maxFileBytes;
            this.maxAttachmentsPerTurn = maxAttachmentsPerTurn;
            this.maxTotalBytes = maxTotalBytes;
            this.exactMediaTypes = normalizeExactTypes(exactMediaTypes);
            this.mediaTypePrefixes = normalizePrefixes(mediaTypePrefixes);
            if (this.exactMediaTypes.isEmpty() && this.mediaTypePrefixes.isEmpty()) {
                throw new IllegalArgumentException("at least one media type must be allowed");
            }
        }

        public static Policy defaultPolicy() {
            return new Policy(
                    DEFAULT_MAX_FILE_BYTES,
                    DEFAULT_MAX_ATTACHMENTS_PER_TURN,
                    DEFAULT_MAX_TOTAL_BYTES,
                    Set.of("application/pdf", "application/json", "text/csv"),
                    Set.of("image/", "text/"));
        }

        public int maxFileBytes() { return maxFileBytes; }
        public int maxAttachmentsPerTurn() { return maxAttachmentsPerTurn; }
        public int maxTotalBytes() { return maxTotalBytes; }

        public boolean acceptsMediaType(String mediaType) {
            String normalized = normalizeMediaType(mediaType);
            if (normalized == null) return false;
            if (exactMediaTypes.contains(normalized)) return true;
            for (String prefix : mediaTypePrefixes) {
                if (normalized.startsWith(prefix)) return true;
            }
            return false;
        }

        private static Set<String> normalizeExactTypes(Set<String> values) {
            Objects.requireNonNull(values, "exactMediaTypes");
            Set<String> result = new LinkedHashSet<>();
            for (String value : values) {
                String normalized = normalizeMediaType(value);
                if (normalized == null) {
                    throw new IllegalArgumentException("invalid exact media type");
                }
                result.add(normalized);
            }
            return Set.copyOf(result);
        }

        private static Set<String> normalizePrefixes(Set<String> values) {
            Objects.requireNonNull(values, "mediaTypePrefixes");
            Set<String> result = new LinkedHashSet<>();
            for (String value : values) {
                Objects.requireNonNull(value, "media type prefix");
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.endsWith("/") ||
                        normalized.length() < 2 ||
                        normalized.length() > MAX_MEDIA_TYPE_CHARS ||
                        normalized.indexOf(';') >= 0 ||
                        hasControlCharacters(normalized)) {
                    throw new IllegalArgumentException("invalid media type prefix");
                }
                result.add(normalized);
            }
            return Set.copyOf(result);
        }
    }

    public enum FailureKind {
        INVALID_SOURCE,
        INVALID_METADATA,
        CONTENT_TYPE_REJECTED,
        EMPTY,
        TOO_LARGE,
        TOO_MANY_ATTACHMENTS,
        TOTAL_TOO_LARGE,
        UNREADABLE
    }

    public static final class Result {
        private final EphemeralAttachment attachment;
        private final FailureKind failureKind;

        private Result(EphemeralAttachment attachment, FailureKind failureKind) {
            this.attachment = attachment;
            this.failureKind = failureKind;
        }

        public static Result success(EphemeralAttachment attachment) {
            return new Result(Objects.requireNonNull(attachment, "attachment"), null);
        }

        public static Result failure(FailureKind failureKind) {
            return new Result(null, Objects.requireNonNull(failureKind, "failureKind"));
        }

        public boolean isSuccess() { return attachment != null; }

        public EphemeralAttachment attachment() {
            if (attachment == null) throw new IllegalStateException("attachment ingress did not succeed");
            return attachment;
        }

        public FailureKind failureKind() {
            if (failureKind == null) throw new IllegalStateException("attachment ingress did not fail");
            return failureKind;
        }
    }

    public static final class EphemeralAttachment {
        private final String displayName;
        private final String mediaType;
        private final int sizeBytes;
        private final String contentSha256Hex;
        private final String sourceFingerprintSha256;
        private byte[] payload;
        private boolean wiped;

        private EphemeralAttachment(
                String displayName,
                String mediaType,
                byte[] payload,
                String contentSha256Hex,
                String sourceFingerprintSha256) {
            this.displayName = displayName;
            this.mediaType = mediaType;
            this.sizeBytes = payload.length;
            this.payload = payload;
            this.contentSha256Hex = contentSha256Hex;
            this.sourceFingerprintSha256 = sourceFingerprintSha256;
        }

        public String displayName() { return displayName; }
        public String mediaType() { return mediaType; }
        public int sizeBytes() { return sizeBytes; }
        public String contentSha256Hex() { return contentSha256Hex; }
        public String sourceFingerprintSha256() { return sourceFingerprintSha256; }
        public boolean isImage() { return mediaType.startsWith("image/"); }

        public synchronized byte[] copyBytes() {
            if (wiped) throw new IllegalStateException("attachment payload was wiped");
            return payload.clone();
        }

        public synchronized void wipe() {
            if (!wiped) {
                Arrays.fill(payload, (byte) 0);
                payload = new byte[0];
                wiped = true;
            }
        }

        public synchronized boolean isWiped() { return wiped; }

        @Override
        public String toString() {
            return "EphemeralAttachment(mediaType=" + mediaType +
                    ", sizeBytes=" + sizeBytes +
                    ", contentSha256Hex=" + contentSha256Hex +
                    ", sourceFingerprintSha256=" + sourceFingerprintSha256 +
                    ", wiped=" + wiped + ")";
        }
    }

    public static Result readEphemeral(Source source, Policy policy) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(policy, "policy");

        final String sourceFingerprint;
        final Metadata metadata;
        try {
            sourceFingerprint = normalizeFingerprint(source.sourceFingerprintSha256());
            metadata = Objects.requireNonNull(source.metadata(), "metadata");
        } catch (IOException | RuntimeException failure) {
            return Result.failure(FailureKind.UNREADABLE);
        }
        if (sourceFingerprint == null) return Result.failure(FailureKind.INVALID_SOURCE);

        String displayName = normalizeDisplayName(metadata.displayName());
        String mediaType = normalizeMediaType(metadata.mediaType());
        if (displayName == null || mediaType == null) {
            return Result.failure(FailureKind.INVALID_METADATA);
        }
        if (!policy.acceptsMediaType(mediaType)) {
            return Result.failure(FailureKind.CONTENT_TYPE_REJECTED);
        }

        Long declaredSize = metadata.declaredSizeBytes();
        if (declaredSize != null) {
            if (declaredSize < 0) return Result.failure(FailureKind.INVALID_METADATA);
            if (declaredSize == 0) return Result.failure(FailureKind.EMPTY);
            if (declaredSize > policy.maxFileBytes()) return Result.failure(FailureKind.TOO_LARGE);
        }

        byte[] buffer = new byte[16 * 1024];
        WipeableByteArrayOutputStream output = new WipeableByteArrayOutputStream();
        try (InputStream input = source.openStream()) {
            if (input == null) return Result.failure(FailureKind.UNREADABLE);
            int total = 0;
            while (true) {
                int count = input.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                if (total > policy.maxFileBytes() - count) {
                    return Result.failure(FailureKind.TOO_LARGE);
                }
                output.write(buffer, 0, count);
                total += count;
            }
            if (total == 0) return Result.failure(FailureKind.EMPTY);
            if (declaredSize != null && declaredSize.longValue() != total) {
                return Result.failure(FailureKind.INVALID_METADATA);
            }
            byte[] payload = output.toByteArray();
            String contentDigest = sha256Hex(payload);
            return Result.success(new EphemeralAttachment(
                    displayName,
                    mediaType,
                    payload,
                    contentDigest,
                    sourceFingerprint));
        } catch (IOException | RuntimeException failure) {
            return Result.failure(FailureKind.UNREADABLE);
        } finally {
            Arrays.fill(buffer, (byte) 0);
            output.wipe();
        }
    }

    public static FailureKind validateBatch(
            java.util.List<EphemeralAttachment> attachments,
            Policy policy) {
        Objects.requireNonNull(attachments, "attachments");
        Objects.requireNonNull(policy, "policy");
        if (attachments.size() > policy.maxAttachmentsPerTurn()) {
            return FailureKind.TOO_MANY_ATTACHMENTS;
        }
        long total = 0;
        for (EphemeralAttachment attachment : attachments) {
            Objects.requireNonNull(attachment, "attachment");
            total += attachment.sizeBytes();
            if (total > policy.maxTotalBytes()) {
                return FailureKind.TOTAL_TOO_LARGE;
            }
        }
        return null;
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() ||
                normalized.length() > MAX_DISPLAY_NAME_CHARS ||
                hasControlCharacters(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String normalizeMediaType(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() ||
                normalized.length() > MAX_MEDIA_TYPE_CHARS ||
                normalized.indexOf(';') >= 0 ||
                hasControlCharacters(normalized) ||
                !MEDIA_TYPE.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private static String normalizeFingerprint(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SHA256_HEX.matcher(normalized).matches() ? normalized : null;
    }

    private static boolean hasControlCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c < 0x20 || c == 0x7f) return true;
        }
        return false;
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            Arrays.fill(digest, (byte) 0);
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static final class WipeableByteArrayOutputStream extends ByteArrayOutputStream {
        void wipe() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }
}
