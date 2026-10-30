package ar.com.faro.v5.platform.android.ingress;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import ar.com.faro.v5.kernel.contracts.ConversationAttachmentIngressPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A5 Android user-picker ingress only. The result never carries a provider URI
 * or sends data anywhere. An A6 caller must bind a pending picker to a specific
 * conversation, run ConversationAttachmentIngressPolicy and a separate egress gate.
 *
 * Short-lived in-memory payloads are wiped on close. No persisted grant, library,
 * network transfer, Workspace write or Reality write is performed here.
 */
public final class AndroidConversationAttachmentReader {
    private static final int READ_CHUNK = 16 * 1024;
    private static final String DEFAULT_NAME = "adjunto";

    private AndroidConversationAttachmentReader() {}

    public static List<EphemeralFile> readSelected(Context context, List<Uri> uris)
            throws IOException {
        Objects.requireNonNull(context, "context");
        if (uris == null || uris.isEmpty()
                || uris.size() > ConversationAttachmentIngressPolicy.MAX_FILES) {
            throw new IOException("Cantidad de adjuntos no admitida");
        }
        List<EphemeralFile> opened = new ArrayList<>();
        long total = 0L;
        Set<String> hashes = new HashSet<>();
        try {
            for (Uri uri : uris) {
                EphemeralFile file = readOne(context.getContentResolver(), uri);
                if (!hashes.add(file.sha256Hex())) {
                    file.close();
                    throw new IOException("El archivo ya fue seleccionado");
                }
                opened.add(file);
                total += file.sizeBytes();
                if (total > ConversationAttachmentIngressPolicy.MAX_TOTAL_BYTES) {
                    throw new IOException("Los adjuntos superan el máximo conjunto");
                }
            }
            return Collections.unmodifiableList(new ArrayList<>(opened));
        } catch (IOException | RuntimeException failure) {
            for (EphemeralFile file : opened) file.close();
            throw failure;
        }
    }

    private static EphemeralFile readOne(ContentResolver resolver, Uri uri) throws IOException {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            throw new IOException("Seleccioná el archivo desde Android");
        }
        String mime = resolver.getType(uri);
        if (mime == null) throw new IOException("Formato de archivo no identificado");
        mime = mime.toLowerCase(Locale.ROOT);
        if (!supported(mime)) throw new IOException("Formato de archivo no admitido");

        String name = DEFAULT_NAME;
        long providerSize = -1L;
        try (Cursor cursor = resolver.query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cleanName(cursor.getString(nameIndex));
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    providerSize = cursor.getLong(sizeIndex);
                }
            }
        }
        if (providerSize > ConversationAttachmentIngressPolicy.MAX_FILE_BYTES) {
            throw new IOException("El archivo supera el tamaño admitido");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException absent) {
            throw new IOException("No se pudo verificar el archivo", absent);
        }

        byte[] payload;
        WipeableOutput buffer = new WipeableOutput();
        byte[] chunk = new byte[READ_CHUNK];
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("No se pudo abrir el adjunto");
            long size = 0L;
            int zeroReads = 0;
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (count == 0) {
                    if (++zeroReads > 3) throw new IOException("El proveedor no respondió");
                    continue;
                }
                zeroReads = 0;
                size += count;
                if (size > ConversationAttachmentIngressPolicy.MAX_FILE_BYTES) {
                    throw new IOException("El archivo supera el tamaño admitido");
                }
                digest.update(chunk, 0, count);
                buffer.write(chunk, 0, count);
            }
            if (size == 0L) throw new IOException("El archivo está vacío");
            payload = buffer.toByteArray();
        } finally {
            Arrays.fill(chunk, (byte) 0);
            buffer.wipe();
        }

        if (!signatureMatches(mime, payload)) {
            Arrays.fill(payload, (byte) 0);
            throw new IOException("El contenido no coincide con el formato");
        }
        String hash = toHex(digest.digest());
        return new EphemeralFile(name, mime, payload, hash);
    }

    private static String cleanName(String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT_NAME;
        String name = value.trim();
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < name.length() && safe.length() < 240; i++) {
            char c = name.charAt(i);
            if (c >= 32 && c != 127) safe.append(c);
        }
        return safe.length() == 0 ? DEFAULT_NAME : safe.toString();
    }

    private static boolean supported(String mime) {
        switch (mime) {
            case "image/jpeg":
            case "image/png":
            case "image/webp":
            case "image/heic":
            case "application/pdf":
            case "application/json":
            case "text/plain":
            case "text/markdown":
            case "text/csv":
                return true;
            default:
                return false;
        }
    }

    private static boolean signatureMatches(String mime, byte[] bytes) {
        if ("application/pdf".equals(mime)) {
            return starts(bytes, new int[]{0x25, 0x50, 0x44, 0x46, 0x2d});
        }
        if ("image/png".equals(mime)) {
            return starts(bytes, new int[]{137, 80, 78, 71, 13, 10, 26, 10});
        }
        if ("image/jpeg".equals(mime)) {
            return starts(bytes, new int[]{255, 216, 255});
        }
        if ("image/webp".equals(mime)) {
            return bytes.length >= 12 && textAt(bytes, 0, "RIFF")
                    && textAt(bytes, 8, "WEBP");
        }
        if ("image/heic".equals(mime)) {
            return bytes.length >= 12 && textAt(bytes, 4, "ftyp")
                    && (textAt(bytes, 8, "heic") || textAt(bytes, 8, "heix")
                    || textAt(bytes, 8, "hevc") || textAt(bytes, 8, "hevx"));
        }
        for (byte value : bytes) {
            if (value == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException malformed) {
            return false;
        }
    }

    private static boolean starts(byte[] bytes, int[] expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xff) != expected[i]) return false;
        }
        return true;
    }

    private static boolean textAt(byte[] bytes, int offset, String text) {
        if (bytes.length < offset + text.length()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (bytes[offset + i] != (byte) text.charAt(i)) return false;
        }
        return true;
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] & 0xff) >>> 4];
            result[i * 2 + 1] = digits[bytes[i] & 0x0f];
        }
        return new String(result);
    }

    private static final class WipeableOutput extends ByteArrayOutputStream {
        void wipe() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }

    public static final class EphemeralFile implements AutoCloseable {
        private final String displayName;
        private final String mimeType;
        private final String sha256Hex;
        private byte[] ownedBytes;

        private EphemeralFile(String name, String mime, byte[] bytes, String hash) {
            this.displayName = name;
            this.mimeType = mime;
            this.ownedBytes = bytes;
            this.sha256Hex = hash;
        }

        public String displayName() { return displayName; }
        public String mimeType() { return mimeType; }
        public String sha256Hex() { return sha256Hex; }
        public synchronized int sizeBytes() {
            return ownedBytes == null ? 0 : ownedBytes.length;
        }

        /** Read-only view into short-lived memory; invalid after close(). */
        public synchronized ByteBuffer readOnlyBytes() {
            if (ownedBytes == null) throw new IllegalStateException("Archivo descartado");
            return ByteBuffer.wrap(ownedBytes).asReadOnlyBuffer();
        }

        @Override public synchronized void close() {
            if (ownedBytes != null) Arrays.fill(ownedBytes, (byte) 0);
            ownedBytes = null;
        }
    }
}
