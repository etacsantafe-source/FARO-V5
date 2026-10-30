package ar.com.faro.v5.platform.android.work;

import ar.com.faro.v5.delivery.spi.ScheduledDeliveryWorkProjectionPort;
import java.util.Objects;

/**
 * WorkManager-backed implementation of the Delivery scheduler projection boundary.
 *
 * <p>The generic FARO worker receives only stable work/semantic IDs and re-reads current durable
 * authority when it executes.
 */
public final class AndroidScheduledDeliveryWorkProjection
        implements ScheduledDeliveryWorkProjectionPort {
    private final AndroidWorkManagerScheduler scheduler;

    public AndroidScheduledDeliveryWorkProjection(AndroidWorkManagerScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void ensureOneTime(String workKind, String semanticId) {
        scheduler.ensureOneTime(workKind, semanticId, FaroScheduledWorkWorker.class);
    }
}
