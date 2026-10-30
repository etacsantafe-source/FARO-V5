package ar.com.faro.v5.app;

import java.util.Objects;

/**
 * Process-local startup readiness snapshot for the S0 bootstrap/maintenance surface.
 *
 * <p>This is presentation/runtime state only. It owns no domain truth and persists nothing.
 */
public final class FaroV5StartupReadiness {
    public enum State {
        INITIALIZING,
        LOCAL_READY,
        MAINTENANCE_ONLY
    }

    /** Real startup/recovery step currently being executed. Never a synthetic percentage. */
    public enum StartupPhase {
        PREPARING_STORAGE,
        INITIALIZING_MAINTENANCE,
        RECOVERING_EXTERNAL_EFFECTS,
        RECOVERING_UPDATE,
        RECOVERING_RESET,
        RECOVERING_GENERATION,
        VERIFYING_LOCAL_DATA
    }

    /** Human-facing meaning derived from canonical maintenance journal state. */
    public enum RecoveryKind {
        GENERIC,
        MIGRATION,
        RESTORE,
        RESET,
        MULTIPLE
    }

    public enum MaintenanceReason {
        INITIAL_STORAGE_BOOTSTRAP_FAILED,
        MAINTENANCE_RUNTIME_INITIALIZATION_FAILED,
        EXTERNAL_EFFECT_RECOVERY_FAILED,
        UPDATE_RUNTIME_INITIALIZATION_FAILED,
        UPDATE_BRIDGE_INITIALIZATION_FAILED,
        UPDATE_RECONCILIATION_FAILED,
        RESET_RECOVERY_FAILED,
        GENERATION_ACTIVATION_RECOVERY_FAILED,
        ACTIVE_GENERATION_HEALTH_FAILED
    }

    private static final FaroV5StartupReadiness INITIALIZING =
            new FaroV5StartupReadiness(
                    State.INITIALIZING,
                    null,
                    StartupPhase.PREPARING_STORAGE,
                    RecoveryKind.GENERIC);
    private static final FaroV5StartupReadiness LOCAL_READY =
            new FaroV5StartupReadiness(
                    State.LOCAL_READY,
                    null,
                    null,
                    RecoveryKind.GENERIC);

    private final State state;
    private final MaintenanceReason maintenanceReason;
    private final StartupPhase startupPhase;
    private final RecoveryKind recoveryKind;

    private FaroV5StartupReadiness(
            State state,
            MaintenanceReason maintenanceReason,
            StartupPhase startupPhase,
            RecoveryKind recoveryKind) {
        this.state = Objects.requireNonNull(state, "state");
        this.recoveryKind = Objects.requireNonNull(recoveryKind, "recoveryKind");
        if (state == State.MAINTENANCE_ONLY) {
            this.maintenanceReason =
                    Objects.requireNonNull(
                            maintenanceReason,
                            "maintenanceReason");
            if (startupPhase != null) {
                throw new IllegalArgumentException(
                        "startup phase is valid only for INITIALIZING");
            }
            this.startupPhase = null;
            if (recoveryKind != RecoveryKind.GENERIC
                    && maintenanceReason != MaintenanceReason.RESET_RECOVERY_FAILED
                    && maintenanceReason != MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED) {
                throw new IllegalArgumentException(
                        "recovery kind is valid only for recovery-related maintenance");
            }
        } else if (state == State.INITIALIZING) {
            if (maintenanceReason != null) {
                throw new IllegalArgumentException(
                        "maintenance reason is valid only for MAINTENANCE_ONLY");
            }
            this.maintenanceReason = null;
            this.startupPhase =
                    Objects.requireNonNull(startupPhase, "startupPhase");
            if (recoveryKind != RecoveryKind.GENERIC
                    && startupPhase != StartupPhase.RECOVERING_RESET
                    && startupPhase != StartupPhase.RECOVERING_GENERATION) {
                throw new IllegalArgumentException(
                        "recovery kind is valid only for recovery phases");
            }
        } else {
            if (maintenanceReason != null
                    || startupPhase != null
                    || recoveryKind != RecoveryKind.GENERIC) {
                throw new IllegalArgumentException(
                        "LOCAL_READY cannot carry maintenance reason or startup phase");
            }
            this.maintenanceReason = null;
            this.startupPhase = null;
        }
    }

    public static FaroV5StartupReadiness initializing() {
        return INITIALIZING;
    }

    public static FaroV5StartupReadiness initializing(
            StartupPhase phase) {
        Objects.requireNonNull(phase, "phase");
        if (phase == StartupPhase.PREPARING_STORAGE) {
            return INITIALIZING;
        }
        return new FaroV5StartupReadiness(
                State.INITIALIZING,
                null,
                phase,
                RecoveryKind.GENERIC);
    }

    public static FaroV5StartupReadiness initializing(
            StartupPhase phase,
            RecoveryKind recoveryKind) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(recoveryKind, "recoveryKind");
        if (phase == StartupPhase.PREPARING_STORAGE
                && recoveryKind == RecoveryKind.GENERIC) {
            return INITIALIZING;
        }
        return new FaroV5StartupReadiness(
                State.INITIALIZING,
                null,
                phase,
                recoveryKind);
    }

    public static FaroV5StartupReadiness localReady() {
        return LOCAL_READY;
    }

    public static FaroV5StartupReadiness maintenanceOnly(
            MaintenanceReason reason) {
        return maintenanceOnly(reason, RecoveryKind.GENERIC);
    }

    public static FaroV5StartupReadiness maintenanceOnly(
            MaintenanceReason reason,
            RecoveryKind recoveryKind) {
        return new FaroV5StartupReadiness(
                State.MAINTENANCE_ONLY,
                reason,
                null,
                Objects.requireNonNull(recoveryKind, "recoveryKind"));
    }

    public State getState() {
        return state;
    }

    public MaintenanceReason getMaintenanceReason() {
        return maintenanceReason;
    }

    public StartupPhase getStartupPhase() {
        return startupPhase;
    }

    public RecoveryKind getRecoveryKind() {
        return recoveryKind;
    }

    public boolean isLocalReady() {
        return state == State.LOCAL_READY;
    }
}
