package ar.com.faro.v5.platform.android.update;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import ar.com.faro.v5.platform.android.R;
import java.util.Objects;

/**
 * Conservative pre-handoff readiness check.
 *
 * <p>FARO refuses to start an update unless Android permits package-install requests and a
 * notification can carry a future PackageInstaller user-action request while FARO is background.
 */
public final class AndroidUpdateInstallReadiness {
    public static final String CHANNEL_ID = "faro_updates_v1";

    public enum BlockReason {
        NONE,
        INSTALL_SOURCE_NOT_ALLOWED,
        NOTIFICATION_PERMISSION_DENIED,
        NOTIFICATIONS_DISABLED
    }

    private final Context context;
    private final PackageManager packageManager;
    private final NotificationManager notificationManager;

    public AndroidUpdateInstallReadiness(Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            throw new IllegalStateException("NotificationManager unavailable");
        }
    }

    public BlockReason check() {
        ensureChannel();

        if (!packageManager.canRequestPackageInstalls()) {
            return BlockReason.INSTALL_SOURCE_NOT_ALLOWED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return BlockReason.NOTIFICATION_PERMISSION_DENIED;
        }
        if (!notificationManager.areNotificationsEnabled()) {
            return BlockReason.NOTIFICATIONS_DISABLED;
        }
        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
        if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return BlockReason.NOTIFICATIONS_DISABLED;
        }
        return BlockReason.NONE;
    }

    public boolean canNotifyUserAction() {
        BlockReason reason = check();
        return reason == BlockReason.NONE;
    }

    public Intent installSourceSettingsIntent() {
        return new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public Intent notificationSettingsIntent() {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    void ensureChannel() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.faro_update_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.faro_update_channel_description));
        notificationManager.createNotificationChannel(channel);
    }
}
