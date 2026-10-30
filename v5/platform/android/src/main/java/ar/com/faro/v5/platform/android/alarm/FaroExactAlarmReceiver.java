package ar.com.faro.v5.platform.android.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import ar.com.faro.v5.delivery.spi.WallClockAlarmIdentity;
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionPort;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin, non-exported exact-alarm trigger.
 *
 * <p>The PendingIntent carries only opaque scheduling IDs. Current FARO authority is re-read by the
 * injected execution port; the receiver never opens stores, owners, Runtime, or Apply directly.
 */
public final class FaroExactAlarmReceiver extends BroadcastReceiver {
    static final String ACTION_TRIGGER = "ar.com.faro.v5.action.EXACT_ALARM_TRIGGER";
    static final String EXTRA_WORK_KIND = "faro.v5.work_kind";
    static final String EXTRA_SEMANTIC_ID = "faro.v5.semantic_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TRIGGER.equals(intent.getAction())) {
            return;
        }

        String workKind = intent.getStringExtra(EXTRA_WORK_KIND);
        String semanticId = intent.getStringExtra(EXTRA_SEMANTIC_ID);
        final WallClockAlarmIdentity identity;
        try {
            identity = new WallClockAlarmIdentity(workKind, semanticId);
        } catch (IllegalArgumentException | NullPointerException invalidIdentity) {
            return;
        }

        ScheduledWorkExecutionPort port = FaroExactAlarmExecutionBridge.currentOrNull();
        if (port == null) {
            return;
        }

        PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(
                () -> {
                    try {
                        port.execute(identity.getWorkKind(), identity.getSemanticId());
                    } finally {
                        pendingResult.finish();
                        executor.shutdown();
                    }
                });
    }
}
