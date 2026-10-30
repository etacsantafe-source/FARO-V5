package ar.com.faro.v5.platform.android.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import ar.com.faro.v5.delivery.spi.WallClockAlarmIdentity;
import ar.com.faro.v5.delivery.spi.WallClockAlarmPrecision;
import ar.com.faro.v5.delivery.spi.WallClockAlarmProjectionPort;
import ar.com.faro.v5.delivery.spi.WallClockAlarmProjectionRequest;
import ar.com.faro.v5.delivery.spi.WallClockAlarmProjectionResult;
import java.util.Objects;

/**
 * Exact-only Android wall-clock projection.
 *
 * <p>AlarmManager is rebuildable platform state. This adapter never promotes INEXACT intent to an
 * exact alarm and never treats special-access state as FARO Truth.
 */
public final class AndroidExactAlarmProjection implements WallClockAlarmProjectionPort {
    private static final String URI_SCHEME = "faro-v5";
    private static final String URI_AUTHORITY = "exact-alarm";

    private final Context appContext;
    private final AlarmManager alarmManager;

    public AndroidExactAlarmProjection(Context context) {
        this.appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.alarmManager =
                Objects.requireNonNull(
                        appContext.getSystemService(AlarmManager.class), "AlarmManager");
    }

    @Override
    public WallClockAlarmProjectionResult schedule(WallClockAlarmProjectionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getPrecision() != WallClockAlarmPrecision.EXACT_REQUIRED) {
            return WallClockAlarmProjectionResult.INEXACT_NOT_HANDLED;
        }
        if (!FaroExactAlarmExecutionBridge.isInstalled()) {
            return WallClockAlarmProjectionResult.EXECUTION_BRIDGE_UNAVAILABLE;
        }
        if (!hasExactAlarmAccess()) {
            return WallClockAlarmProjectionResult.EXACT_ACCESS_REQUIRED;
        }

        PendingIntent pendingIntent =
                pendingIntent(request.getIdentity(), PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    request.getTriggerAtEpochMillis(),
                    pendingIntent);
            return WallClockAlarmProjectionResult.SCHEDULED;
        } catch (SecurityException revokedDuringSchedule) {
            return WallClockAlarmProjectionResult.EXACT_ACCESS_REQUIRED;
        }
    }

    @Override
    public WallClockAlarmProjectionResult cancel(WallClockAlarmIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        appContext,
                        0,
                        alarmIntent(identity),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
        return WallClockAlarmProjectionResult.CANCELLED;
    }

    private boolean hasExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return alarmManager.canScheduleExactAlarms();
    }

    private PendingIntent pendingIntent(WallClockAlarmIdentity identity, int creationFlag) {
        return PendingIntent.getBroadcast(
                appContext,
                0,
                alarmIntent(identity),
                creationFlag | PendingIntent.FLAG_IMMUTABLE);
    }

    private Intent alarmIntent(WallClockAlarmIdentity identity) {
        Uri identityUri =
                new Uri.Builder()
                        .scheme(URI_SCHEME)
                        .authority(URI_AUTHORITY)
                        .appendPath(identity.getWorkKind())
                        .appendPath(identity.getSemanticId())
                        .build();
        return new Intent(appContext, FaroExactAlarmReceiver.class)
                .setAction(FaroExactAlarmReceiver.ACTION_TRIGGER)
                .setData(identityUri)
                .putExtra(FaroExactAlarmReceiver.EXTRA_WORK_KIND, identity.getWorkKind())
                .putExtra(FaroExactAlarmReceiver.EXTRA_SEMANTIC_ID, identity.getSemanticId());
    }
}
