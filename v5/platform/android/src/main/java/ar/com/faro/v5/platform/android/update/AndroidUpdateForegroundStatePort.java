package ar.com.faro.v5.platform.android.update;

/** Process-local observation only; it never owns update lifecycle state. */
@FunctionalInterface
public interface AndroidUpdateForegroundStatePort {
    boolean isForeground();
}
