package ar.com.faro.v5.app;

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Couples startup readiness with material-write admission.
 *
 * <p>The safe ordering is deliberate:
 * admission closes before INITIALIZING/MAINTENANCE_ONLY is published. LOCAL_READY is stored before
 * admission opens, but presentation observers are notified only after writes are open. Therefore a
 * presentation callback never promotes unhealthy state or becomes authority for write admission.
 */
final class FaroV5StartupReadinessGate {
    private final SingleProcessMaterialWriteGate materialWriteGate;
    private final CopyOnWriteArraySet<FaroV5StartupReadinessListener> listeners =
            new CopyOnWriteArraySet<>();

    private volatile FaroV5StartupReadiness snapshot =
            FaroV5StartupReadiness.initializing();

    FaroV5StartupReadinessGate(
            SingleProcessMaterialWriteGate materialWriteGate) {
        this.materialWriteGate =
                Objects.requireNonNull(materialWriteGate, "materialWriteGate");
        this.materialWriteGate.closeMaterialWriteAdmission();
    }

    FaroV5StartupReadiness snapshot() {
        FaroV5StartupReadiness current = snapshot;
        if (current.isLocalReady()
                && !materialWriteGate.isMaterialWriteAdmissionOpen()) {
            return FaroV5StartupReadiness.initializing();
        }
        return current;
    }

    void addListener(FaroV5StartupReadinessListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    void removeListener(FaroV5StartupReadinessListener listener) {
        listeners.remove(Objects.requireNonNull(listener, "listener"));
    }

    void beginRecovery() {
        publishInitializing(
                FaroV5StartupReadiness.StartupPhase.PREPARING_STORAGE,
                FaroV5StartupReadiness.RecoveryKind.GENERIC);
    }

    void initializingPhase(
            FaroV5StartupReadiness.StartupPhase phase) {
        publishInitializing(
                Objects.requireNonNull(phase, "phase"),
                FaroV5StartupReadiness.RecoveryKind.GENERIC);
    }

    void initializingRecovery(
            FaroV5StartupReadiness.StartupPhase phase,
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        publishInitializing(
                Objects.requireNonNull(phase, "phase"),
                Objects.requireNonNull(recoveryKind, "recoveryKind"));
    }

    private void publishInitializing(
            FaroV5StartupReadiness.StartupPhase phase,
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        materialWriteGate.closeMaterialWriteAdmission();
        FaroV5StartupReadiness next =
                FaroV5StartupReadiness.initializing(phase, recoveryKind);
        snapshot = next;
        notifyListeners(next);
    }

    void maintenanceOnly(
            FaroV5StartupReadiness.MaintenanceReason reason) {
        maintenanceOnly(reason, FaroV5StartupReadiness.RecoveryKind.GENERIC);
    }

    void maintenanceOnly(
            FaroV5StartupReadiness.MaintenanceReason reason,
            FaroV5StartupReadiness.RecoveryKind recoveryKind) {
        materialWriteGate.closeMaterialWriteAdmission();
        FaroV5StartupReadiness next =
                FaroV5StartupReadiness.maintenanceOnly(
                        reason,
                        Objects.requireNonNull(recoveryKind, "recoveryKind"));
        snapshot = next;
        notifyListeners(next);
    }

    void localReady() {
        FaroV5StartupReadiness next = FaroV5StartupReadiness.localReady();
        snapshot = next;
        materialWriteGate.openMaterialWriteAdmission();
        notifyListeners(next);
    }

    private void notifyListeners(FaroV5StartupReadiness readiness) {
        for (FaroV5StartupReadinessListener listener : listeners) {
            try {
                listener.onStartupReadinessChanged(readiness);
            } catch (RuntimeException observerFailure) {
                listeners.remove(listener);
            }
        }
    }
}
