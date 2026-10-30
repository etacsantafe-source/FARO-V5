package ar.com.faro.v5.platform.android.notification;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import ar.com.faro.v5.delivery.api.DeliveryDesiredAction;
import ar.com.faro.v5.delivery.spi.NotificationDeliveryObserver;
import ar.com.faro.v5.delivery.spi.NotificationDeliverySink;
import ar.com.faro.v5.delivery.spi.NotificationProjectionObservation;
import ar.com.faro.v5.delivery.spi.NotificationProjectionRequest;
import ar.com.faro.v5.delivery.spi.NotificationSinkOutcome;
import ar.com.faro.v5.platform.android.R;
import java.util.Objects;

/**
 * Android OS projection for one already-durable FARO DeliveryIntent.
 *
 * <p>This adapter never owns Notice state and never interprets OS posting/removal as semantic
 * seen/dismissed/resolved state.
 */
public final class AndroidNotificationDeliverySink
        implements NotificationDeliverySink, NotificationDeliveryObserver {
    public static final String CHANNEL_ID = "faro_notices_v1";
    private static final String TAG_PREFIX = "faro-v5-delivery:";
    private static final int NOTIFICATION_ID = 1001;

    private final Context context;
    private final NotificationManager notificationManager;
    private final int smallIconResId;

    public AndroidNotificationDeliverySink(
            Context context,
            int smallIconResId) {
        Context applicationContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
        if (smallIconResId == 0) {
            throw new IllegalArgumentException("smallIconResId must be a valid resource id");
        }
        NotificationManager manager =
                applicationContext.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("NotificationManager unavailable");
        }
        this.context = applicationContext;
        this.notificationManager = manager;
        this.smallIconResId = smallIconResId;
    }

    @Override
    public NotificationSinkOutcome dispatch(NotificationProjectionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getDesiredAction() == DeliveryDesiredAction.CANCEL) {
            return cancel(request);
        }
        return upsert(request);
    }

    @Override
    public NotificationProjectionObservation observe(String deliveryIdentity) {
        String expectedTag = stableTag(deliveryIdentity);
        try {
            for (StatusBarNotification active : notificationManager.getActiveNotifications()) {
                if (active.getId() == NOTIFICATION_ID
                        && expectedTag.equals(active.getTag())) {
                    return NotificationProjectionObservation.ACTIVE;
                }
            }
            return NotificationProjectionObservation.NOT_ACTIVE;
        } catch (RuntimeException platformFailure) {
            return NotificationProjectionObservation.UNKNOWN;
        }
    }

    private NotificationSinkOutcome upsert(NotificationProjectionRequest request) {
        ensureChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            return NotificationSinkOutcome.BLOCKED_PERMISSION;
        }
        if (!notificationManager.areNotificationsEnabled()) {
            return NotificationSinkOutcome.BLOCKED_PERMISSION;
        }

        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
        if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            return NotificationSinkOutcome.BLOCKED_PERMISSION;
        }

        String body =
                request.getSensitive()
                        ? context.getString(R.string.faro_notification_redacted)
                        : request.getSummary();

        Notification.Builder builder =
                new Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(smallIconResId)
                        .setContentTitle(context.getString(R.string.faro_notification_title))
                        .setContentText(body)
                        .setCategory(Notification.CATEGORY_REMINDER)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(false)
                        .setVisibility(Notification.VISIBILITY_PRIVATE);

        if (request.getSensitive()) {
            Notification publicVersion =
                    new Notification.Builder(context, CHANNEL_ID)
                            .setSmallIcon(smallIconResId)
                            .setContentTitle(context.getString(R.string.faro_notification_title))
                            .setContentText(context.getString(R.string.faro_notification_redacted))
                            .setOnlyAlertOnce(true)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .build();
            builder.setPublicVersion(publicVersion);
        }

        try {
            notificationManager.notify(
                    stableTag(request.getDeliveryIdentity()),
                    NOTIFICATION_ID,
                    builder.build());
            return NotificationSinkOutcome.POSTED_TO_OS;
        } catch (SecurityException denied) {
            return NotificationSinkOutcome.BLOCKED_PERMISSION;
        } catch (RuntimeException platformFailure) {
            return NotificationSinkOutcome.FAILED;
        }
    }

    private NotificationSinkOutcome cancel(NotificationProjectionRequest request) {
        try {
            notificationManager.cancel(
                    stableTag(request.getDeliveryIdentity()),
                    NOTIFICATION_ID);
            return NotificationSinkOutcome.CANCELLED;
        } catch (RuntimeException platformFailure) {
            return NotificationSinkOutcome.FAILED;
        }
    }

    private void ensureChannel() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.faro_notice_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.faro_notice_channel_description));
        notificationManager.createNotificationChannel(channel);
    }

    public static boolean isFaroDeliveryTag(String tag) {
        return tag != null && tag.startsWith(TAG_PREFIX);
    }

    public static String stableTag(String deliveryIdentity) {
        if (deliveryIdentity == null || deliveryIdentity.isEmpty()) {
            throw new IllegalArgumentException("deliveryIdentity must not be empty");
        }
        return TAG_PREFIX + deliveryIdentity;
    }
}
