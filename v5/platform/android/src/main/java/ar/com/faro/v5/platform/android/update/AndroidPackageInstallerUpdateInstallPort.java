package ar.com.faro.v5.platform.android.update;

import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import ar.com.faro.v5.maintenance.ExpectedInstall;
import ar.com.faro.v5.maintenance.UpdateControlEntry;
import ar.com.faro.v5.maintenance.UpdateFailure;
import ar.com.faro.v5.maintenance.UpdateFailureKind;
import ar.com.faro.v5.maintenance.UpdateInstallControlCoordinator;
import ar.com.faro.v5.maintenance.UpdateInstallObservationKind;
import ar.com.faro.v5.maintenance.UpdateInstallPlatformInterop;
import ar.com.faro.v5.maintenance.UpdateInstallPlatformPayload;
import ar.com.faro.v5.maintenance.UpdateInstallPort;
import ar.com.faro.v5.maintenance.UpdateInstallSessionObservation;
import ar.com.faro.v5.maintenance.UpdateStagedArtifactReadPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * PackageInstaller.Session adapter for one already-verified FARO update artifact.
 *
 * <p>The durable ExpectedInstall exists before a platform session is created. The platform session
 * id is persisted before any OS handoff. The bytes written to PackageInstaller are re-verified from
 * the session itself before commit, closing the staging-to-session TOCTOU window.
 *
 * <p>This adapter does not interpret callbacks or declare installation success. P5.A6 owns callback
 * translation and P5.A7 owns reconciliation.
 */
public final class AndroidPackageInstallerUpdateInstallPort implements UpdateInstallPort {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final String APK_SESSION_NAME = "base.apk";

    private final PackageInstaller packageInstaller;
    private final UpdateStagedArtifactReadPort stagedArtifactReader;
    private final UpdateInstallControlCoordinator control;
    private final AndroidUpdateInstallStatusSenderPort statusSender;

    public AndroidPackageInstallerUpdateInstallPort(
            Context context,
            UpdateStagedArtifactReadPort stagedArtifactReader,
            UpdateInstallControlCoordinator control,
            AndroidUpdateInstallStatusSenderPort statusSender) {
        Context applicationContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
        this.packageInstaller = applicationContext.getPackageManager().getPackageInstaller();
        this.stagedArtifactReader =
                Objects.requireNonNull(stagedArtifactReader, "stagedArtifactReader");
        this.control = Objects.requireNonNull(control, "control");
        this.statusSender = Objects.requireNonNull(statusSender, "statusSender");
    }

    @Override
    public UpdateInstallSessionObservation commit(ExpectedInstall expected) {
        Objects.requireNonNull(expected, "expected");

        UpdateInstallPlatformPayload payload = UpdateInstallPlatformInterop.payload(expected);
        UpdateControlEntry prepared = control.prepare(expected);
        if (prepared.getPlatformSessionIdentity() != null) {
            throw new IllegalStateException(
                    "prepared update already owns a platform session; reconcile before retry");
        }

        final int sessionId;
        try {
            sessionId = packageInstaller.createSession(sessionParams(expected));
        } catch (IOException | RuntimeException failure) {
            UpdateFailure typed =
                    new UpdateFailure(
                            UpdateFailureKind.INSTALL_REJECTED,
                            "package_installer_session_create_failed");
            control.markFailed(prepared, typed);
            return failureObservation(expected, null, typed);
        }

        UpdateControlEntry bound;
        try {
            bound = control.bindPlatformSession(prepared, Integer.toString(sessionId));
        } catch (RuntimeException staleOrInvalid) {
            abandonQuietly(sessionId);
            throw staleOrInvalid;
        }

        PackageInstaller.Session session = null;
        try {
            session = packageInstaller.openSession(sessionId);
            writeAndVerifySession(session, expected);

            IntentSender receiver =
                    Objects.requireNonNull(
                            statusSender.create(payload.getExpectedInstallId(), sessionId),
                            "status sender returned null");

            UpdateControlEntry handoff = control.markOsHandoff(bound);
            try {
                session.commit(receiver);
                return UpdateInstallPlatformInterop.observation(
                        expected,
                        Integer.toString(sessionId),
                        UpdateInstallObservationKind.SESSION_ACTIVE,
                        null);
            } catch (IllegalArgumentException | SecurityException knownFailure) {
                UpdateFailure typed =
                        new UpdateFailure(
                                UpdateFailureKind.INSTALL_REJECTED,
                                "package_installer_commit_rejected");
                control.markFailed(handoff, typed);
                abandonQuietly(sessionId);
                return failureObservation(expected, Integer.toString(sessionId), typed);
            } catch (RuntimeException uncertain) {
                UpdateFailure typed =
                        new UpdateFailure(
                                UpdateFailureKind.EFFECT_UNKNOWN,
                                "package_installer_commit_outcome_unknown");
                control.markEffectUnknown(handoff, typed.getReason());
                return UpdateInstallPlatformInterop.observation(
                        expected,
                        Integer.toString(sessionId),
                        UpdateInstallObservationKind.EFFECT_UNKNOWN,
                        typed);
            }
        } catch (ArtifactMismatchException invalidArtifact) {
            abandonQuietly(sessionId);
            UpdateFailure typed =
                    new UpdateFailure(
                            UpdateFailureKind.ARTIFACT_INVALID,
                            "package_installer_session_artifact_mismatch");
            control.markFailed(bound, typed);
            return failureObservation(expected, Integer.toString(sessionId), typed);
        } catch (IOException | RuntimeException platformFailure) {
            abandonQuietly(sessionId);
            UpdateFailure typed =
                    new UpdateFailure(
                            UpdateFailureKind.INSTALL_REJECTED,
                            "package_installer_session_io_failed");
            control.markFailed(bound, typed);
            return failureObservation(expected, Integer.toString(sessionId), typed);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (RuntimeException ignored) {
                    // Session close is local cleanup; durable OS_HANDOFF/EFFECT_UNKNOWN state wins.
                }
            }
        }
    }

    private static PackageInstaller.SessionParams sessionParams(ExpectedInstall expected) {
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        UpdateInstallPlatformPayload payload = UpdateInstallPlatformInterop.payload(expected);
        params.setAppPackageName(payload.getPackageName());
        params.setSize(payload.getArtifactByteCount());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        return params;
    }

    private void writeAndVerifySession(
            PackageInstaller.Session session,
            ExpectedInstall expected)
            throws IOException {
        UpdateInstallPlatformPayload payload = UpdateInstallPlatformInterop.payload(expected);
        long expectedBytes = payload.getArtifactByteCount();
        String expectedSha256 = payload.getArtifactSha256Hex();

        MessageDigest sourceDigest = sha256();
        long copied = 0L;

        try (InputStream input = stagedArtifactReader.open(expected);
                OutputStream output = session.openWrite(APK_SESSION_NAME, 0L, expectedBytes)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                copied = Math.addExact(copied, read);
                if (copied > expectedBytes) {
                    throw new ArtifactMismatchException("staged artifact exceeds expected size");
                }
                sourceDigest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            if (copied != expectedBytes) {
                throw new ArtifactMismatchException("staged artifact is truncated");
            }
            if (!expectedSha256.equals(toHex(sourceDigest.digest()))) {
                throw new ArtifactMismatchException("staged artifact digest changed");
            }
            session.fsync(output);
        }

        verifySessionBytes(session, expectedBytes, expectedSha256);
    }

    private static void verifySessionBytes(
            PackageInstaller.Session session,
            long expectedBytes,
            String expectedSha256)
            throws IOException {
        MessageDigest digest = sha256();
        long count = 0L;

        try (InputStream input = session.openRead(APK_SESSION_NAME)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                count = Math.addExact(count, read);
                if (count > expectedBytes) {
                    throw new ArtifactMismatchException(
                            "PackageInstaller session exceeds expected size");
                }
                digest.update(buffer, 0, read);
            }
        }

        if (count != expectedBytes) {
            throw new ArtifactMismatchException("PackageInstaller session is truncated");
        }
        if (!expectedSha256.equals(toHex(digest.digest()))) {
            throw new ArtifactMismatchException("PackageInstaller session digest mismatch");
        }
    }

    private UpdateInstallSessionObservation failureObservation(
            ExpectedInstall expected,
            String platformSessionIdentity,
            UpdateFailure failure) {
        return UpdateInstallPlatformInterop.observation(
                expected,
                platformSessionIdentity,
                UpdateInstallObservationKind.FAILURE_REPORTED,
                failure);
    }

    private void abandonQuietly(int sessionId) {
        try {
            packageInstaller.abandonSession(sessionId);
        } catch (RuntimeException ignored) {
            // Reconciliation owns any session whose cleanup outcome cannot be proven here.
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
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

    private static final class ArtifactMismatchException extends IOException {
        private ArtifactMismatchException(String message) {
            super(message);
        }
    }
}
