package ar.com.faro.v5.platform.android.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import ar.com.faro.v5.maintenance.UpdateFailure;
import ar.com.faro.v5.maintenance.UpdateFailureKind;
import ar.com.faro.v5.maintenance.UpdateInstallCallbackEvent;
import ar.com.faro.v5.maintenance.UpdateInstallCallbackKind;
import ar.com.faro.v5.maintenance.UpdateInstallCallbackPort;

/**
 * Thin, non-exported PackageInstaller status receiver.
 *
 * <p>It validates the explicit callback identity, maps platform status to typed Maintenance events,
 * and forwards any pending-user-action Intent to a process-local UI bridge. It never marks an
 * update INSTALLED; final Truth belongs to update.reconcile.
 */
public final class FaroUpdateInstallStatusReceiver extends BroadcastReceiver {
    static final String ACTION_INSTALL_STATUS =
            "ar.com.faro.v5.action.UPDATE_INSTALL_STATUS";
    static final String EXTRA_EXPECTED_INSTALL_ID =
            "faro.v5.update.expected_install_id";
    static final String EXTRA_EXPECTED_SESSION_ID =
            "faro.v5.update.expected_session_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return;
        }

        String expectedInstallId = intent.getStringExtra(EXTRA_EXPECTED_INSTALL_ID);
        int expectedSessionId = intent.getIntExtra(EXTRA_EXPECTED_SESSION_ID, -1);
        int callbackSessionId =
                intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        if (expectedInstallId == null
                || expectedInstallId.trim().isEmpty()
                || !expectedInstallId.equals(expectedInstallId.trim())
                || expectedSessionId <= 0
                || (callbackSessionId >= 0 && callbackSessionId != expectedSessionId)) {
            return;
        }

        UpdateInstallCallbackPort callbackPort =
                FaroUpdateInstallCallbackExecutionBridge.currentOrNull();
        if (callbackPort == null) {
            return;
        }

        int status =
                intent.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        Integer.MIN_VALUE);
        String sessionIdentity = Integer.toString(expectedSessionId);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                callbackPort.onCallback(
                        new UpdateInstallCallbackEvent(
                                expectedInstallId,
                                sessionIdentity,
                                UpdateInstallCallbackKind.PENDING_USER_ACTION,
                                null));
                Intent confirmation = confirmationIntent(intent);
                AndroidUpdateInstallUserActionPort userAction =
                        FaroUpdateInstallUserActionExecutionBridge.currentOrNull();
                boolean surfaced = false;
                if (confirmation != null && userAction != null) {
                    try {
                        surfaced =
                                userAction.handle(
                                        expectedInstallId,
                                        sessionIdentity,
                                        confirmation);
                    } catch (RuntimeException ignored) {
                        surfaced = false;
                    }
                }
                if (!surfaced) {
                    callbackPort.onCallback(
                            new UpdateInstallCallbackEvent(
                                    expectedInstallId,
                                    sessionIdentity,
                                    UpdateInstallCallbackKind.EFFECT_UNKNOWN,
                                    new UpdateFailure(
                                            UpdateFailureKind.EFFECT_UNKNOWN,
                                            "package_installer_user_action_not_deliverable")));
                }
                return;

            case PackageInstaller.STATUS_SUCCESS:
                callbackPort.onCallback(
                        new UpdateInstallCallbackEvent(
                                expectedInstallId,
                                sessionIdentity,
                                UpdateInstallCallbackKind.SUCCESS_REPORTED,
                                null));
                return;

            case PackageInstaller.STATUS_FAILURE:
            case PackageInstaller.STATUS_FAILURE_ABORTED:
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            case PackageInstaller.STATUS_FAILURE_INVALID:
            case PackageInstaller.STATUS_FAILURE_STORAGE:
            case PackageInstaller.STATUS_FAILURE_TIMEOUT:
                UpdateFailure failure =
                        new UpdateFailure(
                                UpdateFailureKind.INSTALL_REJECTED,
                                failureReason(status));
                callbackPort.onCallback(
                        new UpdateInstallCallbackEvent(
                                expectedInstallId,
                                sessionIdentity,
                                UpdateInstallCallbackKind.FAILURE_REPORTED,
                                failure));
                return;

            default:
                UpdateFailure unknown =
                        new UpdateFailure(
                                UpdateFailureKind.EFFECT_UNKNOWN,
                                "package_installer_unknown_status");
                callbackPort.onCallback(
                        new UpdateInstallCallbackEvent(
                                expectedInstallId,
                                sessionIdentity,
                                UpdateInstallCallbackKind.EFFECT_UNKNOWN,
                                unknown));
        }
    }

    @SuppressWarnings("deprecation")
    private static Intent confirmationIntent(Intent callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return callback.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }
        return callback.getParcelableExtra(Intent.EXTRA_INTENT);
    }

    private static String failureReason(int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "package_installer_status_failure_aborted";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "package_installer_status_failure_blocked";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "package_installer_status_failure_conflict";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "package_installer_status_failure_incompatible";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "package_installer_status_failure_invalid";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "package_installer_status_failure_storage";
            case PackageInstaller.STATUS_FAILURE_TIMEOUT:
                return "package_installer_status_failure_timeout";
            default:
                return "package_installer_status_failure";
        }
    }
}
