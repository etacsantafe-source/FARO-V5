package ar.com.faro.v5.platform.android.alarm;

import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionPort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local bridge installed by the future V5 Application/CompositionRoot.
 *
 * <p>This bridge is not durable state and never owns domain authority. The exact-alarm receiver
 * remains inert until real application composition installs the canonical execution port.
 */
public final class FaroExactAlarmExecutionBridge {
    private static final AtomicReference<ScheduledWorkExecutionPort> PORT =
            new AtomicReference<>();

    private FaroExactAlarmExecutionBridge() {}

    public static void install(ScheduledWorkExecutionPort port) {
        PORT.set(Objects.requireNonNull(port, "port"));
    }

    public static void clear(ScheduledWorkExecutionPort expectedPort) {
        PORT.compareAndSet(Objects.requireNonNull(expectedPort, "expectedPort"), null);
    }

    public static boolean isInstalled() {
        return PORT.get() != null;
    }

    static ScheduledWorkExecutionPort currentOrNull() {
        return PORT.get();
    }
}
