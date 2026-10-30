package ar.com.faro.v5.platform.android.update;

import android.content.Context;
import android.content.pm.PackageInstaller;
import ar.com.faro.v5.maintenance.UpdateFailure;
import ar.com.faro.v5.maintenance.UpdateFailureKind;
import ar.com.faro.v5.maintenance.UpdateInstallObservationKind;
import ar.com.faro.v5.maintenance.UpdateInstallPlatformInterop;
import ar.com.faro.v5.maintenance.UpdateInstallSessionCleanupPort;
import ar.com.faro.v5.maintenance.UpdateInstallSessionInspectionPort;
import ar.com.faro.v5.maintenance.UpdateInstallSessionInspectionRequest;
import ar.com.faro.v5.maintenance.UpdateInstallSessionObservation;
import java.util.Objects;

/**
 * Reconciliation adapter limited to PackageInstaller sessions owned by FARO itself.
 *
 * <p>Session existence is evidence only. Final install truth comes from the installed package
 * identity adapter.
 */
public final class AndroidPackageInstallerSessionInspectionPort
        implements UpdateInstallSessionInspectionPort, UpdateInstallSessionCleanupPort {
    private final PackageInstaller packageInstaller;

    public AndroidPackageInstallerSessionInspectionPort(Context context) {
        Context applicationContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
        this.packageInstaller = applicationContext.getPackageManager().getPackageInstaller();
    }

    @Override
    public UpdateInstallSessionObservation inspect(UpdateInstallSessionInspectionRequest request) {
        Objects.requireNonNull(request, "request");
        int sessionId = parseSessionId(request.getPlatformSessionIdentity());
        PackageInstaller.SessionInfo owned = findOwnedSession(sessionId);
        if (owned == null) {
            return UpdateInstallPlatformInterop.sessionObservation(
                    request,
                    UpdateInstallObservationKind.SESSION_MISSING,
                    null);
        }

        String sessionPackage = owned.getAppPackageName();
        if (sessionPackage != null && !sessionPackage.equals(request.getPackageName())) {
            UpdateFailure failure =
                    new UpdateFailure(
                            UpdateFailureKind.EFFECT_UNKNOWN,
                            "package_installer_owned_session_package_mismatch");
            return UpdateInstallPlatformInterop.sessionObservation(
                    request,
                    UpdateInstallObservationKind.EFFECT_UNKNOWN,
                    failure);
        }

        return UpdateInstallPlatformInterop.sessionObservation(
                request,
                UpdateInstallObservationKind.SESSION_ACTIVE,
                null);
    }

    @Override
    public void abandon(UpdateInstallSessionInspectionRequest request) {
        Objects.requireNonNull(request, "request");
        int sessionId = parseSessionId(request.getPlatformSessionIdentity());
        PackageInstaller.SessionInfo owned = findOwnedSession(sessionId);
        if (owned == null) {
            return;
        }

        String sessionPackage = owned.getAppPackageName();
        if (sessionPackage != null && !sessionPackage.equals(request.getPackageName())) {
            throw new SecurityException("refusing to abandon mismatched owned install session");
        }
        packageInstaller.abandonSession(sessionId);
    }

    private PackageInstaller.SessionInfo findOwnedSession(int sessionId) {
        for (PackageInstaller.SessionInfo info : packageInstaller.getMySessions()) {
            if (info.getSessionId() == sessionId) {
                return info;
            }
        }
        return null;
    }

    private static int parseSessionId(String value) {
        final int sessionId;
        try {
            sessionId = Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            throw new IllegalStateException("invalid durable PackageInstaller session id", malformed);
        }
        if (sessionId < 0) {
            throw new IllegalStateException("invalid negative PackageInstaller session id");
        }
        return sessionId;
    }
}
