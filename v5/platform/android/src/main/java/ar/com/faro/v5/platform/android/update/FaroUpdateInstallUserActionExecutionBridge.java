package ar.com.faro.v5.platform.android.update;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Process-local, non-authoritative bridge for pending PackageInstaller user action. */
public final class FaroUpdateInstallUserActionExecutionBridge {
    private static final AtomicReference<AndroidUpdateInstallUserActionPort> PORT =
            new AtomicReference<>();

    private FaroUpdateInstallUserActionExecutionBridge() {}

    public static void install(AndroidUpdateInstallUserActionPort port) {
        PORT.set(Objects.requireNonNull(port, "port"));
    }

    public static void clear(AndroidUpdateInstallUserActionPort expectedPort) {
        PORT.compareAndSet(Objects.requireNonNull(expectedPort, "expectedPort"), null);
    }

    public static boolean isInstalled() {
        return PORT.get() != null;
    }

    static AndroidUpdateInstallUserActionPort currentOrNull() {
        return PORT.get();
    }
}
