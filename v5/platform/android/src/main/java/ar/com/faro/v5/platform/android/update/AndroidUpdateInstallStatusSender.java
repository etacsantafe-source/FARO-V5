package ar.com.faro.v5.platform.android.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import java.util.Objects;

/**
 * Explicit callback IntentSender for PackageInstaller.Session.commit.
 *
 * <p>Target SDK 35+ rejects immutable status PendingIntents because PackageInstaller must add
 * status extras. FARO therefore uses mutable only where required, while pinning the PendingIntent
 * to a non-exported explicit receiver.
 */
public final class AndroidUpdateInstallStatusSender
        implements AndroidUpdateInstallStatusSenderPort {
    private final Context applicationContext;

    public AndroidUpdateInstallStatusSender(Context context) {
        this.applicationContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public IntentSender create(String expectedInstallId, int sessionId) {
        Objects.requireNonNull(expectedInstallId, "expectedInstallId");
        if (expectedInstallId.trim().isEmpty() || !expectedInstallId.equals(expectedInstallId.trim())) {
            throw new IllegalArgumentException("expectedInstallId must be canonical");
        }
        if (sessionId <= 0) {
            throw new IllegalArgumentException("sessionId must be positive");
        }

        Intent callback =
                new Intent(applicationContext, FaroUpdateInstallStatusReceiver.class)
                        .setAction(FaroUpdateInstallStatusReceiver.ACTION_INSTALL_STATUS)
                        .setPackage(applicationContext.getPackageName())
                        .putExtra(
                                FaroUpdateInstallStatusReceiver.EXTRA_EXPECTED_INSTALL_ID,
                                expectedInstallId)
                        .putExtra(
                                FaroUpdateInstallStatusReceiver.EXTRA_EXPECTED_SESSION_ID,
                                sessionId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        return PendingIntent.getBroadcast(
                        applicationContext,
                        sessionId,
                        callback,
                        flags)
                .getIntentSender();
    }
}
