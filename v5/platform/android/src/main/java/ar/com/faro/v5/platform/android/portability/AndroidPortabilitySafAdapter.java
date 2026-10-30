package ar.com.faro.v5.platform.android.portability;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import ar.com.faro.v5.maintenance.PortabilityExternalAttemptPhase;
import ar.com.faro.v5.maintenance.PortabilityExternalAttemptRecord;
import ar.com.faro.v5.maintenance.PortabilityExternalEffectCoordinator;
import ar.com.faro.v5.maintenance.PortabilityExternalEffectReceipt;
import ar.com.faro.v5.maintenance.PortabilityExternalWriteSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Android SAF sink for one A7 portability artifact.
 *
 * <p>The destination Uri is intentionally never handed to the maintenance journal and is never
 * persisted by this adapter. A process death after external write start therefore reconciles to
 * EFFECT_UNKNOWN instead of enabling a blind replay.
 */
public final class AndroidPortabilitySafAdapter {
    private static final int BUFFER_BYTES = 64 * 1024;

    private final PortabilityExternalEffectCoordinator coordinator;

    public AndroidPortabilitySafAdapter(PortabilityExternalEffectCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    /**
     * Begins the durable attempt before launching the user-selected document picker.
     *
     * <p>The caller should launch this Intent through the Activity Result API. Any state needed to
     * map the callback back to the FARO effect must be saved independently of the Activity instance.
     */
    public Intent createDocumentIntent(PortabilityExternalWriteSource source) {
        Objects.requireNonNull(source, "source");
        PortabilityExternalAttemptRecord attempt = coordinator.begin(source);
        if (attempt.getPhase() != PortabilityExternalAttemptPhase.AWAITING_DESTINATION) {
            throw new IllegalStateException(
                    "portability attempt is not awaiting a destination");
        }

        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(source.getMimeType())
                .putExtra(Intent.EXTRA_TITLE, source.getSuggestedFileName())
                .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    /** Picker dismissal is the only A8 cancellation classified as CANCELLED / no write. */
    public PortabilityExternalEffectReceipt pickerCancelled(
            PortabilityExternalWriteSource source) {
        return coordinator.pickerCancelled(Objects.requireNonNull(source, "source"));
    }

    /**
     * Copies the prepared private artifact to a user-selected SAF document and verifies it.
     *
     * <p>WRITE_STARTED is durable before ContentResolver can touch the external destination.
     * Once a provider descriptor has been acquired, any exception/cancellation whose exact external
     * outcome cannot be proven becomes EFFECT_UNKNOWN.
     */
    public PortabilityExternalEffectReceipt writeSelectedDocument(
            ContentResolver resolver,
            Uri destination,
            PortabilityExternalWriteSource source,
            CancellationSignal cancellationSignal) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");

        coordinator.markWriteStarted(source);
        boolean outputAcquired = false;

        try {
            cancellationSignal.throwIfCanceled();
            ParcelFileDescriptor descriptor =
                    resolver.openFileDescriptor(destination, "w", cancellationSignal);
            if (descriptor == null) {
                return coordinator.completeKnownFailureAfterStart(
                        source, "provider_open_returned_null");
            }
            outputAcquired = true;

            try (OutputStream output =
                    new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                long copied =
                        source.copyTo(
                                new CancellationCheckingOutputStream(
                                        output, cancellationSignal));
                if (copied != source.getByteCount()) {
                    return coordinator.completeUnknown(
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
                return coordinator.completeVerifiedSuccess(source);
            }
            if (verification == Verification.MISMATCH) {
                return coordinator.completeUnknown(
                        source, "external_verification_mismatch");
            }
            return coordinator.completeUnknown(
                    source, "external_verification_unavailable");
        } catch (OperationCanceledException cancelled) {
            if (outputAcquired) {
                return coordinator.completeUnknown(
                        source, "external_write_cancelled_after_open");
            }
            return coordinator.completeKnownFailureAfterStart(
                    source, "external_write_cancelled_before_open");
        } catch (FileNotFoundException | SecurityException denied) {
            if (outputAcquired) {
                return coordinator.completeUnknown(
                        source, "external_provider_access_lost_after_open");
            }
            return coordinator.completeKnownFailureAfterStart(
                    source, "external_provider_open_failed");
        } catch (IOException platformFailure) {
            if (outputAcquired) {
                return coordinator.completeUnknown(
                        source, "external_io_failure_after_open");
            }
            return coordinator.completeKnownFailureAfterStart(
                    source, "external_io_failure_before_open");
        } catch (RuntimeException platformFailure) {
            if (outputAcquired) {
                return coordinator.completeUnknown(
                        source, "external_runtime_failure_after_open");
            }
            return coordinator.completeKnownFailureAfterStart(
                    source, "external_runtime_failure_before_open");
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
            String actualSha256 = toHex(digest.digest());
            return expectedSha256.equals(actualSha256)
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
}
