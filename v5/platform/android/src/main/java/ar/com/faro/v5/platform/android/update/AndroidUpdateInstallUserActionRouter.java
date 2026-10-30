package ar.com.faro.v5.platform.android.update;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import ar.com.faro.v5.platform.android.R;
import java.util.Objects;

/**
 * Routes PackageInstaller user approval without background-activity guessing.
 *
 * <p>Foreground FARO may launch the system confirmation immediately. Background FARO always posts
 * a notification whose immutable PendingIntent launches the exact system-provided confirmation.
 */
public final class AndroidUpdateInstallUserActionRouter
        implements AndroidUpdateInstallUserActionPort {
    private static final String TAG_PREFIX = "faro-v5-update:";

    private final Context context;
    private final PackageManager packageManager;
    private final NotificationManager notificationManager;
    private final AndroidUpdateInstallReadiness readiness;
    private final AndroidUpdateForegroundStatePort foregroundState;
    private final int smallIconResId;

    public AndroidUpdateInstallUserActionRouter(
            Context context,
            AndroidUpdateInstallReadiness readiness,
            AndroidUpdateForegroundStatePort foregroundState,
            int smallIconResId) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.foregroundState = Objects.requireNonNull(foregroundState, "foregroundState");
        if (smallIconResId == 0) {
            throw new IllegalArgumentException("smallIconResId must be a valid resource id");
        }
        this.smallIconResId = smallIconResId;
        this.packageManager = this.context.getPackageManager();
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            throw new IllegalStateException("NotificationManager unavailable");
        }
    }

    @Override
    public boolean handle(
            String expectedInstallId,
            String platformSessionIdentity,
            Intent confirmationIntent) {
        if (expectedInstallId == null
                || expectedInstallId.trim().isEmpty()
                || !expectedInstallId.equals(expectedInstallId.trim())
                || platformSessionIdentity == null
                || platformSessionIdentity.trim().isEmpty()
                || !platformSessionIdentity.equals(platformSessionIdentity.trim())
                || confirmationIntent == null) {
            return false;
        }

        int sessionId;
        try {
            sessionId = Integer.parseInt(platformSessionIdentity);
        } catch (NumberFormatException malformed) {
            return false;
        }
        if (sessionId <= 0) {
            return false;
        }

        Intent confirmation = trustedSystemConfirmation(confirmationIntent);
        if (confirmation == null) {
            return false;
        }

        if (foregroundState.isForeground()) {
            try {
                context.startActivity(
                        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (RuntimeException blocked) {
                return false;
            }
        }

        if (!readiness.canNotifyUserAction()) {
            return false;
        }

        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent contentIntent =
                    PendingIntent.getActivity(
                            context,
                            sessionId,
                            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            flags);

            Notification notification =
                    new Notification.Builder(context, AndroidUpdateInstallReadiness.CHANNEL_ID)
                            .setSmallIcon(smallIconResId)
                            .setContentTitle(context.getString(R.string.faro_update_action_title))
                            .setContentText(context.getString(R.string.faro_update_action_body))
                            .setCategory(Notification.CATEGORY_SYSTEM)
                            .setVisibility(Notification.VISIBILITY_PRIVATE)
                            .setAutoCancel(true)
                            .setContentIntent(contentIntent)
                            .build();

            notificationManager.notify(
                    TAG_PREFIX + expectedInstallId,
                    sessionId,
                    notification);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private Intent trustedSystemConfirmation(Intent rawConfirmation) {
        Intent confirmation = new Intent(rawConfirmation);
        ResolveInfo resolved = packageManager.resolveActivity(confirmation, 0);
        if (resolved == null
                || resolved.activityInfo == null
                || resolved.activityInfo.applicationInfo == null
                || resolved.activityInfo.packageName == null
                || resolved.activityInfo.name == null) {
            return null;
        }
        ApplicationInfo app = resolved.activityInfo.applicationInfo;
        int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        if ((app.flags & systemFlags) == 0) {
            return null;
        }
        confirmation.setComponent(
                new ComponentName(
                        resolved.activityInfo.packageName,
                        resolved.activityInfo.name));
        return confirmation;
    }
}
