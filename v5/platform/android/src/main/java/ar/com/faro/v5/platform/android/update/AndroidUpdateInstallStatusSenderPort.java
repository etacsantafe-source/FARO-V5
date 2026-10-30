package ar.com.faro.v5.platform.android.update;

import android.content.IntentSender;

/**
 * Supplies the explicit PackageInstaller status callback destination.
 *
 * <p>P5.A6 owns the concrete receiver/PendingIntent bridge. For targetSdk 35+, the IntentSender
 * passed to PackageInstaller.Session.commit must not come from an immutable PendingIntent.
 */
public interface AndroidUpdateInstallStatusSenderPort {
    IntentSender create(String expectedInstallId, int sessionId);
}
