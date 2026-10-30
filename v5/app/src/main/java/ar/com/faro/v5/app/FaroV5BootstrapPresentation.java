package ar.com.faro.v5.app;

import java.util.Objects;

/** Pure presentation mapping for the bounded S0 bootstrap surface. */
final class FaroV5BootstrapPresentation {
    private FaroV5BootstrapPresentation() {}

    static Model from(FaroV5StartupReadiness readiness) {
        Objects.requireNonNull(readiness, "readiness");
        switch (readiness.getState()) {
            case INITIALIZING:
                return new Model(
                        R.string.bootstrap_initializing_title,
                        startupBody(
                                readiness.getStartupPhase(),
                                readiness.getRecoveryKind()),
                        R.string.bootstrap_initializing_supporting,
                        true,
                        false);
            case LOCAL_READY:
                return new Model(
                        R.string.bootstrap_ready_title,
                        R.string.bootstrap_ready_body,
                        0,
                        false,
                        false);
            case MAINTENANCE_ONLY:
                return new Model(
                        R.string.bootstrap_maintenance_title,
                        maintenanceBody(
                                readiness.getMaintenanceReason(),
                                readiness.getRecoveryKind()),
                        R.string.bootstrap_maintenance_supporting,
                        false,
                        true);
            default:
                throw new IllegalStateException(
                        "Unhandled startup readiness state: " + readiness.getState());
        }
    }

    private static int startupBody(
            FaroV5StartupReadiness.StartupPhase phase,
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(recoveryKind, "recoveryKind");
        if (phase == FaroV5StartupReadiness.StartupPhase.RECOVERING_RESET
                || phase == FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION) {
            int recoveryBody = recoveryBody(recoveryKind);
            if (recoveryBody != 0) {
                return recoveryBody;
            }
        }
        switch (phase) {
            case PREPARING_STORAGE:
                return R.string.bootstrap_phase_storage;
            case INITIALIZING_MAINTENANCE:
                return R.string.bootstrap_phase_maintenance;
            case RECOVERING_EXTERNAL_EFFECTS:
                return R.string.bootstrap_phase_external_effects;
            case RECOVERING_UPDATE:
                return R.string.bootstrap_phase_update;
            case RECOVERING_RESET:
                return R.string.bootstrap_phase_reset;
            case RECOVERING_GENERATION:
                return R.string.bootstrap_phase_generation;
            case VERIFYING_LOCAL_DATA:
                return R.string.bootstrap_phase_health;
            default:
                throw new IllegalStateException(
                        "Unhandled startup phase: " + phase);
        }
    }


    private static int recoveryBody(
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        switch (recoveryKind) {
            case MIGRATION:
                return R.string.bootstrap_recovery_migration;
            case RESTORE:
                return R.string.bootstrap_recovery_restore;
            case RESET:
                return R.string.bootstrap_recovery_reset;
            case MULTIPLE:
                return R.string.bootstrap_recovery_multiple;
            case GENERIC:
                return 0;
            default:
                throw new IllegalStateException(
                        "Unhandled recovery kind: " + recoveryKind);
        }
    }


    private static int recoveryFailureBody(
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        switch (recoveryKind) {
            case MIGRATION:
                return R.string.bootstrap_maintenance_migration;
            case RESTORE:
                return R.string.bootstrap_maintenance_restore;
            case RESET:
                return R.string.bootstrap_maintenance_reset_specific;
            case MULTIPLE:
                return R.string.bootstrap_maintenance_multiple;
            case GENERIC:
                return 0;
            default:
                throw new IllegalStateException(
                        "Unhandled recovery kind: " + recoveryKind);
        }
    }

    private static int maintenanceBody(
            FaroV5StartupReadiness.MaintenanceReason reason,
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(recoveryKind, "recoveryKind");
        if (reason == FaroV5StartupReadiness.MaintenanceReason.RESET_RECOVERY_FAILED
                || reason == FaroV5StartupReadiness.MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED) {
            int recoveryFailureBody = recoveryFailureBody(recoveryKind);
            if (recoveryFailureBody != 0) {
                return recoveryFailureBody;
            }
        }
        switch (reason) {
            case INITIAL_STORAGE_BOOTSTRAP_FAILED:
                return R.string.bootstrap_maintenance_storage;
            case MAINTENANCE_RUNTIME_INITIALIZATION_FAILED:
                return R.string.bootstrap_maintenance_runtime;
            case EXTERNAL_EFFECT_RECOVERY_FAILED:
                return R.string.bootstrap_maintenance_external_recovery;
            case UPDATE_RUNTIME_INITIALIZATION_FAILED:
            case UPDATE_BRIDGE_INITIALIZATION_FAILED:
            case UPDATE_RECONCILIATION_FAILED:
                return R.string.bootstrap_maintenance_update;
            case RESET_RECOVERY_FAILED:
                return R.string.bootstrap_maintenance_reset;
            case GENERATION_ACTIVATION_RECOVERY_FAILED:
                return R.string.bootstrap_maintenance_generation;
            case ACTIVE_GENERATION_HEALTH_FAILED:
                return R.string.bootstrap_maintenance_health;
            default:
                throw new IllegalStateException(
                        "Unhandled maintenance reason: " + reason);
        }
    }

    static final class Model {
        private final int titleResId;
        private final int bodyResId;
        private final int supportingResId;
        private final boolean showProgress;
        private final boolean showMaintenanceActions;

        Model(
                int titleResId,
                int bodyResId,
                int supportingResId,
                boolean showProgress,
                boolean showMaintenanceActions) {
            this.titleResId = titleResId;
            this.bodyResId = bodyResId;
            this.supportingResId = supportingResId;
            this.showProgress = showProgress;
            this.showMaintenanceActions = showMaintenanceActions;
        }

        int getTitleResId() { return titleResId; }
        int getBodyResId() { return bodyResId; }
        int getSupportingResId() { return supportingResId; }
        boolean shouldShowProgress() { return showProgress; }
        boolean shouldShowMaintenanceActions() { return showMaintenanceActions; }
    }
}
