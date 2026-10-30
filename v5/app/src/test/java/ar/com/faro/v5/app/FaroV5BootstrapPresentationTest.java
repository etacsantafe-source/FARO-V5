package ar.com.faro.v5.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FaroV5BootstrapPresentationTest {
    @Test
    void everyInitializingPhaseIsHumanVisibleAndIndeterminate() {
        for (FaroV5StartupReadiness.StartupPhase phase
                : FaroV5StartupReadiness.StartupPhase.values()) {
            FaroV5BootstrapPresentation.Model model =
                    FaroV5BootstrapPresentation.from(
                            FaroV5StartupReadiness.initializing(phase));

            assertEquals(R.string.bootstrap_initializing_title, model.getTitleResId());
            assertNotEquals(0, model.getBodyResId());
            assertEquals(
                    R.string.bootstrap_initializing_supporting,
                    model.getSupportingResId());
            assertTrue(model.shouldShowProgress());
            assertFalse(model.shouldShowMaintenanceActions());
        }
    }

    @Test
    void canonicalRecoveryKindsBecomeHumanMessages() {
        assertEquals(
                R.string.bootstrap_recovery_migration,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.initializing(
                                        FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                                        FaroV5StartupReadiness.RecoveryKind.MIGRATION))
                        .getBodyResId());
        assertEquals(
                R.string.bootstrap_recovery_restore,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.initializing(
                                        FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                                        FaroV5StartupReadiness.RecoveryKind.RESTORE))
                        .getBodyResId());
        assertEquals(
                R.string.bootstrap_recovery_reset,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.initializing(
                                        FaroV5StartupReadiness.StartupPhase.RECOVERING_RESET,
                                        FaroV5StartupReadiness.RecoveryKind.RESET))
                        .getBodyResId());
        assertEquals(
                R.string.bootstrap_recovery_multiple,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.initializing(
                                        FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                                        FaroV5StartupReadiness.RecoveryKind.MULTIPLE))
                        .getBodyResId());
    }

    @Test
    void failedRecoveryKeepsHumanSpecificMeaning() {
        assertEquals(
                R.string.bootstrap_maintenance_restore,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.maintenanceOnly(
                                        FaroV5StartupReadiness.MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED,
                                        FaroV5StartupReadiness.RecoveryKind.RESTORE))
                        .getBodyResId());
        assertEquals(
                R.string.bootstrap_maintenance_migration,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.maintenanceOnly(
                                        FaroV5StartupReadiness.MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED,
                                        FaroV5StartupReadiness.RecoveryKind.MIGRATION))
                        .getBodyResId());
        assertEquals(
                R.string.bootstrap_maintenance_reset_specific,
                FaroV5BootstrapPresentation.from(
                                FaroV5StartupReadiness.maintenanceOnly(
                                        FaroV5StartupReadiness.MaintenanceReason.RESET_RECOVERY_FAILED,
                                        FaroV5StartupReadiness.RecoveryKind.RESET))
                        .getBodyResId());
    }

    @Test
    void localReadyIsAPlaceholderWithoutFakeProgress() {
        FaroV5BootstrapPresentation.Model model =
                FaroV5BootstrapPresentation.from(
                        FaroV5StartupReadiness.localReady());

        assertEquals(R.string.bootstrap_ready_title, model.getTitleResId());
        assertEquals(R.string.bootstrap_ready_body, model.getBodyResId());
        assertFalse(model.shouldShowProgress());
        assertFalse(model.shouldShowMaintenanceActions());
        assertEquals(0, model.getSupportingResId());
    }

    @Test
    void everyMaintenanceReasonIsHumanVisibleAndNeverShowsProgress() {
        for (FaroV5StartupReadiness.MaintenanceReason reason
                : FaroV5StartupReadiness.MaintenanceReason.values()) {
            FaroV5BootstrapPresentation.Model model =
                    FaroV5BootstrapPresentation.from(
                            FaroV5StartupReadiness.maintenanceOnly(reason));

            assertEquals(
                    R.string.bootstrap_maintenance_title,
                    model.getTitleResId());
            assertNotEquals(0, model.getBodyResId());
            assertEquals(
                    R.string.bootstrap_maintenance_supporting,
                    model.getSupportingResId());
            assertFalse(model.shouldShowProgress());
            assertTrue(model.shouldShowMaintenanceActions());
        }
    }
}
