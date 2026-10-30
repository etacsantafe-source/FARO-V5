package ar.com.faro.v5.app;

/** Process-local presentation observer for startup readiness. */
@FunctionalInterface
public interface FaroV5StartupReadinessListener {
    void onStartupReadinessChanged(FaroV5StartupReadiness readiness);
}
