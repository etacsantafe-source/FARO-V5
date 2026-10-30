package ar.com.faro.v5.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FaroV5MaintenanceDiagnosticsTest {
    @Test
    void maintenanceDiagnosticsAreLocalTypedAndRedacted() {
        String detail =
                FaroV5MaintenanceDiagnostics.from(
                        FaroV5StartupReadiness.maintenanceOnly(
                                FaroV5StartupReadiness.MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED,
                                FaroV5StartupReadiness.RecoveryKind.RESTORE));

        assertTrue(detail.contains("state=MAINTENANCE_ONLY"));
        assertTrue(detail.contains("reason=GENERATION_ACTIVATION_RECOVERY_FAILED"));
        assertTrue(detail.contains("recovery=RESTORE"));
        assertFalse(detail.contains("http://"));
        assertFalse(detail.contains("https://"));
        assertFalse(detail.toLowerCase().contains("password"));
        assertFalse(detail.toLowerCase().contains("secret"));
        assertFalse(detail.toLowerCase().contains("token"));
    }

    @Test
    void initializingDiagnosticsMayIncludeOnlyCurrentTypedPhase() {
        String detail =
                FaroV5MaintenanceDiagnostics.from(
                        FaroV5StartupReadiness.initializing(
                                FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                                FaroV5StartupReadiness.RecoveryKind.MIGRATION));

        assertTrue(detail.contains("state=INITIALIZING"));
        assertTrue(detail.contains("phase=RECOVERING_GENERATION"));
        assertTrue(detail.contains("recovery=MIGRATION"));
    }
}
