package ar.com.faro.v5.platform.android.cert;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Debug-only, non-exported provider with deterministic bounded content for P6 certification. */
public final class FaroV5P6CertificationProvider extends ContentProvider {
    public static final String SAMPLE_PATH = "sample.txt";
    public static final String SAMPLE_NAME = "faro-p6-cert.txt";
    public static final String SAMPLE_TEXT = "FARO V5 P6 certification content\n";
    private static final byte[] SAMPLE_BYTES = SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireSample(uri);
        return "text/plain";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        requireSample(uri);
        MatrixCursor cursor =
                new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {SAMPLE_NAME, SAMPLE_BYTES.length});
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireSample(uri);
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("P6 certification provider is read-only");
        }
        File file = new File(
                Objects.requireNonNull(getContext(), "context").getCacheDir(),
                "faro-v5-p6-cert-sample.txt");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(SAMPLE_BYTES);
            output.flush();
        } catch (IOException failure) {
            FileNotFoundException wrapped =
                    new FileNotFoundException("unable to materialize P6 certification content");
            wrapped.initCause(failure);
            throw wrapped;
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only certification provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only certification provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only certification provider");
    }

    private static void requireSample(Uri uri) {
        if (uri == null || !("/" + SAMPLE_PATH).equals(uri.getPath())) {
            throw new IllegalArgumentException("unknown P6 certification URI");
        }
    }
}
