package ar.com.faro.v5.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class FaroV5StartupReadinessGateTest {
    @Test
    void constructionIsInitializingAndFailClosed() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);

        assertEquals(
                FaroV5StartupReadiness.State.INITIALIZING,
                readiness.snapshot().getState());
        assertFalse(writes.isMaterialWriteAdmissionOpen());
        assertThrows(
                IllegalStateException.class,
                () -> writes.supplyMaterialWrite(() -> Boolean.TRUE));
    }

    @Test
    void maintenanceOnlyKeepsWritesClosedAndExposesTypedReason() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);

        readiness.maintenanceOnly(
                FaroV5StartupReadiness.MaintenanceReason.RESET_RECOVERY_FAILED);

        assertEquals(
                FaroV5StartupReadiness.State.MAINTENANCE_ONLY,
                readiness.snapshot().getState());
        assertEquals(
                FaroV5StartupReadiness.MaintenanceReason.RESET_RECOVERY_FAILED,
                readiness.snapshot().getMaintenanceReason());
        assertFalse(writes.isMaterialWriteAdmissionOpen());
    }

    @Test
    void onlyLocalReadyOpensMaterialWritesAndRetryClosesThemAgain() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);

        readiness.localReady();

        assertTrue(readiness.snapshot().isLocalReady());
        assertTrue(writes.isMaterialWriteAdmissionOpen());
        assertEquals(
                Boolean.TRUE,
                writes.supplyMaterialWrite(() -> Boolean.TRUE));

        readiness.beginRecovery();

        assertEquals(
                FaroV5StartupReadiness.State.INITIALIZING,
                readiness.snapshot().getState());
        assertFalse(writes.isMaterialWriteAdmissionOpen());
    }

    @Test
    void listenersObserveTypedTransitionsAndReadyOnlyAfterWritesOpen() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);
        List<FaroV5StartupReadiness.State> seen = new ArrayList<>();
        AtomicBoolean writesOpenWhenReadyObserved = new AtomicBoolean(false);

        FaroV5StartupReadinessListener listener =
                state -> {
                    seen.add(state.getState());
                    if (state.isLocalReady()) {
                        writesOpenWhenReadyObserved.set(
                                writes.isMaterialWriteAdmissionOpen());
                    }
                };
        readiness.addListener(listener);

        readiness.maintenanceOnly(
                FaroV5StartupReadiness.MaintenanceReason.ACTIVE_GENERATION_HEALTH_FAILED);
        readiness.beginRecovery();
        readiness.localReady();

        assertEquals(
                List.of(
                        FaroV5StartupReadiness.State.MAINTENANCE_ONLY,
                        FaroV5StartupReadiness.State.INITIALIZING,
                        FaroV5StartupReadiness.State.LOCAL_READY),
                seen);
        assertTrue(writesOpenWhenReadyObserved.get());

        readiness.removeListener(listener);
        readiness.beginRecovery();
        assertEquals(3, seen.size());
    }

    @Test
    void realStartupPhasesStayFailClosedAndObservable() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);
        List<FaroV5StartupReadiness.StartupPhase> seen = new ArrayList<>();

        readiness.addListener(
                state -> {
                    if (state.getState() == FaroV5StartupReadiness.State.INITIALIZING) {
                        seen.add(state.getStartupPhase());
                    }
                });

        for (FaroV5StartupReadiness.StartupPhase phase
                : FaroV5StartupReadiness.StartupPhase.values()) {
            readiness.initializingPhase(phase);
            assertEquals(phase, readiness.snapshot().getStartupPhase());
            assertFalse(writes.isMaterialWriteAdmissionOpen());
        }

        assertEquals(
                List.of(FaroV5StartupReadiness.StartupPhase.values()),
                seen);
    }

    @Test
    void recoveryMeaningRemainsFailClosedPresentationMetadata() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);

        readiness.initializingRecovery(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                FaroV5StartupReadiness.RecoveryKind.RESTORE);

        assertEquals(
                FaroV5StartupReadiness.RecoveryKind.RESTORE,
                readiness.snapshot().getRecoveryKind());
        assertEquals(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                readiness.snapshot().getStartupPhase());
        assertFalse(writes.isMaterialWriteAdmissionOpen());
    }

    @Test
    void maintenanceFailureMayKeepRecoveryMeaningWithoutOpeningWrites() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);

        readiness.maintenanceOnly(
                FaroV5StartupReadiness.MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED,
                FaroV5StartupReadiness.RecoveryKind.RESTORE);

        assertEquals(
                FaroV5StartupReadiness.RecoveryKind.RESTORE,
                readiness.snapshot().getRecoveryKind());
        assertEquals(
                FaroV5StartupReadiness.State.MAINTENANCE_ONLY,
                readiness.snapshot().getState());
        assertFalse(writes.isMaterialWriteAdmissionOpen());
    }

    @Test
    void brokenPresentationObserverCannotBreakRecovery() {
        SingleProcessMaterialWriteGate writes =
                new SingleProcessMaterialWriteGate();
        FaroV5StartupReadinessGate readiness =
                new FaroV5StartupReadinessGate(writes);
        readiness.addListener(
                ignored -> {
                    throw new IllegalStateException("presentation failure");
                });

        readiness.localReady();

        assertTrue(readiness.snapshot().isLocalReady());
        assertTrue(writes.isMaterialWriteAdmissionOpen());
    }
}
