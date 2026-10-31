package ar.com.faro.v5.platform.android.backup;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import ar.com.faro.v5.maintenance.BackupExternalAttemptPhase;
import ar.com.faro.v5.maintenance.BackupExternalAttemptRecord;
import ar.com.faro.v5.maintenance.BackupExternalEffectCoordinator;
import ar.com.faro.v5.maintenance.BackupExternalEffectReceipt;
import ar.com.faro.v5.maintenance.PortableBackupArchiveStore;
import ar.com.faro.v5.maintenance.PreparedBackupExternalWriteSource;
import ar.com.faro.v5.maintenance.StagedPortableBackupArchive;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SAF ingress/egress for encrypted FARO backup archives.
 *
 * <p>Uris are one-shot transport handles only. They are never persisted by this adapter or by the
 * Maintenance journal. Restore ingress is copied into private bounded staging before crypto/open.
 */
public final class AndroidBackupSafAdapter {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final String BACKUP_MIME = "application/octet-stream";

    private final BackupExternalEffectCoordinator exportCoordinator;

    public AndroidBackupSafAdapter(BackupExternalEffectCoordinator exportCoordinator) {
        this.exportCoordinator = Objects.requireNonNull(exportCoordinator, "exportCoordinator");
    }

    public Intent createExportDocumentIntent(PreparedBackupExternalWriteSource source) {
        Objects.requireNonNull(source, "source");
        BackupExternalAttemptRecord attempt = exportCoordinator.begin(source);
        if (attempt.getPhase() != BackupExternalAttemptPhase.AWAITING_DESTINATION) {
            throw new IllegalStateException("backup export is not awaiting a destination");
        }

        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(source.getMimeType())
                .putExtra(Intent.EXTRA_TITLE, source.getSuggestedFileName())
                .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    public BackupExternalEffectReceipt exportPickerCancelled(
            PreparedBackupExternalWriteSource source) {
        return exportCoordinator.pickerCancelled(Objects.requireNonNull(source, "source"));
    }

    /** Fail closed before any provider access when consent or authority changed. */
    public BackupExternalEffectReceipt exportDeniedBeforeWrite(
            PreparedBackupExternalWriteSource source, String reason) {
        return exportCoordinator.failBeforeWrite(
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(reason, "reason"));
    }

    public BackupExternalEffectReceipt writeSelectedExportDocument(
            ContentResolver resolver,
            Uri destination,
            PreparedBackupExternalWriteSource source,
            CancellationSignal cancellationSignal) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");

        exportCoordinator.markWriteStarted(source);
        boolean outputAcquired = false;

        try {
            cancellationSignal.throwIfCanceled();
            ParcelFileDescriptor descriptor =
                    // Explicit truncation: SAF providers need not truncate for plain "w".
                    resolver.openFileDescriptor(destination, "wt", cancellationSignal);
            if (descriptor == null) {
                return exportCoordinator.completeKnownFailureAfterStart(
                        source, "provider_open_returned_null");
            }
            outputAcquired = true;

            try (OutputStream output =
                    new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                long copied =
                        source.copyTo(
                                new CancellationCheckingOutputStream(
                                        output,
                                        cancellationSignal));
                if (copied != source.getByteCount()) {
                    return exportCoordinator.completeUnknown(
                            source, "external_copy_length_mismatch");
                }
            }

            Verification verification =
                    verifyDestination(
                            resolver,
                            destination,
                            source.getByteCount(),
                            source.getSha256Hex(),
                            cancellationSignal);
            if (verification == Verification.MATCH) {
                return exportCoordinator.completeVerifiedSuccess(source);
            }
            if (verification == Verification.MISMATCH) {
                return exportCoordinator.completeUnknown(
                        source, "external_verification_mismatch");
            }
            return exportCoordinator.completeUnknown(
                    source, "external_verification_unavailable");
        } catch (OperationCanceledException cancelled) {
            if (outputAcquired) {
                return exportCoordinator.completeUnknown(
                        source, "external_write_cancelled_after_open");
            }
            return exportCoordinator.completeKnownFailureAfterStart(
                    source, "external_write_cancelled_before_open");
        } catch (FileNotFoundException | SecurityException denied) {
            if (outputAcquired) {
                return exportCoordinator.completeUnknown(
                        source, "external_provider_access_lost_after_open");
            }
            return exportCoordinator.completeKnownFailureAfterStart(
                    source, "external_provider_open_failed");
        } catch (IOException failure) {
            if (outputAcquired) {
                return exportCoordinator.completeUnknown(
                        source, "external_io_failure_after_open");
            }
            return exportCoordinator.completeKnownFailureAfterStart(
                    source, "external_io_failure_before_open");
        } catch (RuntimeException failure) {
            if (outputAcquired) {
                return exportCoordinator.completeUnknown(
                        source, "external_runtime_failure_after_open");
            }
            return exportCoordinator.completeKnownFailureAfterStart(
                    source, "external_runtime_failure_before_open");
        }
    }

    public Intent createRestoreDocumentIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(BACKUP_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    /**
     * Copies a selected archive into private bounded staging.
     *
     * <p>After this returns, the caller can drop the Uri grant and authenticate/decrypt only the
     * staged artifact through PortableBackupArchiveCrypto.
     */
    public StagedPortableBackupArchive stageSelectedRestoreArchive(
            ContentResolver resolver,
            Uri sourceUri,
            PortableBackupArchiveStore store,
            long stagedAtEpochMillis,
            CancellationSignal cancellationSignal)
            throws IOException {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(sourceUri, "sourceUri");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");

        cancellationSignal.throwIfCanceled();
        InputStream raw = resolver.openInputStream(sourceUri);
        if (raw == null) {
            throw new FileNotFoundException("backup provider returned null input stream");
        }

        try (InputStream input =
                new CancellationCheckingInputStream(raw, cancellationSignal)) {
            StagedPortableBackupArchive staged =
                    store.stageFrom(input, stagedAtEpochMillis);
            try {
                if (!store.verify(staged)) {
                    throw new IOException("staged backup archive failed verification");
                }
                return staged;
            } catch (IOException | RuntimeException invalid) {
                // An unverified ingress artifact is not pinned by a restore
                // journal and must not linger in private staging.
                try {
                    store.delete(staged);
                } catch (RuntimeException cleanup) {
                    invalid.addSuppressed(cleanup);
                }
                throw invalid;
            }
        }
    }

    private static Verification verifyDestination(
            ContentResolver resolver,
            Uri destination,
            long expectedBytes,
            String expectedSha256,
            CancellationSignal cancellationSignal) {
        try (InputStream input = resolver.openInputStream(destination)) {
            if (input == null) {
                return Verification.UNAVAILABLE;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_BYTES];
            long count = 0L;

            while (true) {
                cancellationSignal.throwIfCanceled();
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                count = Math.addExact(count, read);
                if (count > expectedBytes) {
                    return Verification.MISMATCH;
                }
                digest.update(buffer, 0, read);
            }

            if (count != expectedBytes) {
                return Verification.MISMATCH;
            }
            String actual = toHex(digest.digest());
            return expectedSha256.equals(actual)
                    ? Verification.MATCH
                    : Verification.MISMATCH;
        } catch (FileNotFoundException | SecurityException unavailable) {
            return Verification.UNAVAILABLE;
        } catch (IOException unavailable) {
            return Verification.UNAVAILABLE;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private enum Verification {
        MATCH,
        MISMATCH,
        UNAVAILABLE
    }

    private static final class CancellationCheckingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final CancellationSignal cancellationSignal;

        private CancellationCheckingOutputStream(
                OutputStream delegate,
                CancellationSignal cancellationSignal) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.cancellationSignal =
                    Objects.requireNonNull(cancellationSignal, "cancellationSignal");
        }

        @Override
        public void write(int value) throws IOException {
            cancellationSignal.throwIfCanceled();
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            cancellationSignal.throwIfCanceled();
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            cancellationSignal.throwIfCanceled();
            delegate.flush();
        }
    }

    private static final class CancellationCheckingInputStream extends FilterInputStream {
        private final CancellationSignal cancellationSignal;

        private CancellationCheckingInputStream(
                InputStream delegate,
                CancellationSignal cancellationSignal) {
            super(Objects.requireNonNull(delegate, "delegate"));
            this.cancellationSignal =
                    Objects.requireNonNull(cancellationSignal, "cancellationSignal");
        }

        @Override
        public int read() throws IOException {
            cancellationSignal.throwIfCanceled();
            return super.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            cancellationSignal.throwIfCanceled();
            return super.read(bytes, offset, length);
        }
    }
}
