package ar.com.faro.v5.platform.android.reset;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import androidx.work.Operation;
import androidx.work.WorkManager;
import ar.com.faro.v5.maintenance.ResetOsReconciliationPort;
import ar.com.faro.v5.maintenance.ResetPlan;
import ar.com.faro.v5.platform.android.notification.AndroidNotificationDeliverySink;
import ar.com.faro.v5.platform.android.work.AndroidWorkManagerScheduler;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Full-reset reconciliation for OS projections currently reachable from the V5 app.
 *
 * <p>It cancels FARO-tagged WorkManager work and FARO delivery notifications. It deliberately
 * preserves the notification channel and OS permission decisions. Exact-alarm projection is not
 * part of the current app composition and therefore has no live reset debt in this baseline.
 */
public final class AndroidResetOsReconciliation
        implements ResetOsReconciliationPort {
    private final Context appContext;

    public AndroidResetOsReconciliation(Context context) {
        this.appContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public void reconcileAfterReset(ResetPlan plan) {
        Objects.requireNonNull(plan, "plan");
        cancelFaroWorkAndAwait();
        cancelFaroNotifications();
    }

    private void cancelFaroWorkAndAwait() {
        Operation cancellation =
                WorkManager.getInstance(appContext)
                        .cancelAllWorkByTag(AndroidWorkManagerScheduler.ROOT_TAG);
        try {
            cancellation.getResult().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FARO WorkManager reset interrupted", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("FARO WorkManager reset failed", failure);
        }
    }

    private void cancelFaroNotifications() {
        NotificationManager manager =
                Objects.requireNonNull(
                        appContext.getSystemService(NotificationManager.class),
                        "NotificationManager");
        try {
            for (StatusBarNotification active : manager.getActiveNotifications()) {
                if (AndroidNotificationDeliverySink.isFaroDeliveryTag(active.getTag())) {
                    manager.cancel(active.getTag(), active.getId());
                }
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException("FARO notification reset failed", failure);
        }
    }
}
