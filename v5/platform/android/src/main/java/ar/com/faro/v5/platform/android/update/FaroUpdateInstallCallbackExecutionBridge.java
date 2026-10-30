package ar.com.faro.v5.platform.android.update;

import ar.com.faro.v5.maintenance.UpdateInstallCallbackPort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local bridge for PackageInstaller callbacks.
 *
 * <p>It owns no durable state. Maintenance remains authority; the Application/CompositionRoot
 * installs the canonical callback port in P5.A8.
 */
public final class FaroUpdateInstallCallbackExecutionBridge {
    private static final AtomicReference<UpdateInstallCallbackPort> PORT =
            new AtomicReference<>();

    private FaroUpdateInstallCallbackExecutionBridge() {}

    public static void install(UpdateInstallCallbackPort port) {
        PORT.set(Objects.requireNonNull(port, "port"));
    }

    public static void clear(UpdateInstallCallbackPort expectedPort) {
        PORT.compareAndSet(Objects.requireNonNull(expectedPort, "expectedPort"), null);
    }

    public static boolean isInstalled() {
        return PORT.get() != null;
    }

    static UpdateInstallCallbackPort currentOrNull() {
        return PORT.get();
    }
}
