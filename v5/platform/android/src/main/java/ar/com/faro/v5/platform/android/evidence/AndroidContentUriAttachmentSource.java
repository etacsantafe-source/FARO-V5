package ar.com.faro.v5.platform.android.evidence;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import ar.com.faro.v5.evidence.AttachmentIngress;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * Ephemeral Android content:// bridge.
 *
 * It never takes a persistable grant and never exposes the raw Uri to Evidence. Durable work must
 * copy bounded bytes into FARO-owned private staging before relying on process/grant survival.
 */
public final class AndroidContentUriAttachmentSource implements AttachmentIngress.Source {
    private final ContentResolver resolver;
    private final Uri uri;
    private final String sourceFingerprintSha256;

    public AndroidContentUriAttachmentSource(Context context, Uri uri) {
        Objects.requireNonNull(context, "context");
        this.uri = Objects.requireNonNull(uri, "uri");
        if (!ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("attachment ingress requires content:// URI");
        }
        this.resolver = context.getApplicationContext().getContentResolver();
        this.sourceFingerprintSha256 = sha256Hex(uri.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String sourceFingerprintSha256() {
        return sourceFingerprintSha256;
    }

    @Override
    public AttachmentIngress.Metadata metadata() throws IOException {
        try {
            String mediaType = resolver.getType(uri);
            if (mediaType == null || mediaType.trim().isEmpty()) {
                mediaType = "application/octet-stream";
            }

            String displayName = "attachment";
            Long declaredSize = null;
            try (Cursor cursor = resolver.query(
                    uri,
                    new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE },
                    null,
                    null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        String candidate = cursor.getString(nameIndex);
                        if (candidate != null && !candidate.trim().isEmpty()) {
                            displayName = candidate;
                        }
                    }
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        declaredSize = cursor.getLong(sizeIndex);
                    }
                }
            }
            return new AttachmentIngress.Metadata(displayName, mediaType, declaredSize);
        } catch (RuntimeException failure) {
            throw new IOException("content URI metadata unavailable", failure);
        }
    }

    @Override
    public InputStream openStream() throws IOException {
        try {
            InputStream input = resolver.openInputStream(uri);
            if (input == null) throw new FileNotFoundException("content URI returned no stream");
            return input;
        } catch (SecurityException failure) {
            throw new IOException("content URI access denied", failure);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            java.util.Arrays.fill(digest, (byte) 0);
            java.util.Arrays.fill(bytes, (byte) 0);
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
