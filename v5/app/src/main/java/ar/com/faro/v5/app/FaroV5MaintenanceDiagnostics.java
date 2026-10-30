package ar.com.faro.v5.app;

import java.util.Objects;

/** Local/redacted startup diagnostic context for progressive disclosure. */
final class FaroV5MaintenanceDiagnostics {
    private FaroV5MaintenanceDiagnostics() {}

    static String from(FaroV5StartupReadiness readiness) {
        Objects.requireNonNull(readiness, "readiness");
        StringBuilder detail = new StringBuilder();
        detail.append("state=").append(readiness.getState().name());
        if (readiness.getMaintenanceReason() != null) {
            detail.append("\nreason=")
                    .append(readiness.getMaintenanceReason().name());
        }
        detail.append("\nrecovery=")
                .append(readiness.getRecoveryKind().name());
        if (readiness.getStartupPhase() != null) {
            detail.append("\nphase=")
                    .append(readiness.getStartupPhase().name());
        }
        return detail.toString();
    }
}
