package ar.com.faro.v5.app;

import android.app.Application;
import androidx.work.Configuration;
import ar.com.faro.v5.control.OnboardingMutationOutcome;
import ar.com.faro.v5.control.OnboardingSnapshot;
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort;
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort;
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort;
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort;
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort;
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FaroV5Application extends Application implements Configuration.Provider {
    private final ExecutorService startupExecutor =
            Executors.newSingleThreadExecutor(
                    task -> new Thread(task, "faro-v5-startup-recovery"));
    private final AtomicBoolean startupRecoveryRunning = new AtomicBoolean(false);
    private final ExecutorService controlExecutor =
            Executors.newSingleThreadExecutor(
                    task -> new Thread(task, "faro-v5-control"));

    private volatile FaroV5CompositionRoot compositionRoot;
    private volatile FaroV5ActivityForegroundTracker foregroundTracker;

    private FaroV5CompositionRoot compositionRoot() {
        FaroV5CompositionRoot current = compositionRoot;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = compositionRoot;
            if (current == null) {
                current = new FaroV5CompositionRoot(this);
                compositionRoot = current;
            }
            return current;
        }
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(compositionRoot().getWorkerFactory())
                .build();
    }

    public ConversationWorkspaceReadPort readActiveWorkspacePort() {
        return compositionRoot().readActiveWorkspacePort();
    }

    public ConversationTimelineReadPort readActiveTimelinePort() {
        return compositionRoot().readActiveTimelinePort();
    }

    public ConversationComposerPort activeComposerPort() {
        return compositionRoot().activeComposerPort();
    }

    public ConversationWorkspaceSelectionPort activeWorkspaceSelectionPort() {
        return compositionRoot().activeWorkspaceSelectionPort();
    }

    public ConversationApplyRequestPort activeApplyRequestPort() {
        return compositionRoot().activeApplyRequestPort();
    }

    public ConversationReviewPort activeReviewPort() {
        return compositionRoot().activeReviewPort();
    }

    public FaroV5StartupReadiness getStartupReadiness() {
        return compositionRoot().getStartupReadiness();
    }

    public void addStartupReadinessListener(
            FaroV5StartupReadinessListener listener) {
        compositionRoot().addStartupReadinessListener(listener);
    }

    public void removeStartupReadinessListener(
            FaroV5StartupReadinessListener listener) {
        compositionRoot().removeStartupReadinessListener(listener);
    }

    public void readOnboardingState(
            Consumer<OnboardingSnapshot> onSuccess,
            Consumer<Exception> onFailure) {
        controlExecutor.execute(
                () -> {
                    try {
                        onSuccess.accept(compositionRoot().readOnboardingState());
                    } catch (Exception failure) {
                        onFailure.accept(failure);
                    }
                });
    }

    public void deferOnboarding(
            long expectedRevision,
            String idempotencyKey,
            Consumer<OnboardingMutationOutcome> onSuccess,
            Consumer<Exception> onFailure) {
        controlExecutor.execute(
                () -> {
                    try {
                        onSuccess.accept(
                                compositionRoot().deferOnboarding(
                                        expectedRevision,
                                        idempotencyKey));
                    } catch (Exception failure) {
                        onFailure.accept(failure);
                    }
                });
    }

    public void completeOnboarding(
            long expectedRevision,
            String idempotencyKey,
            Consumer<OnboardingMutationOutcome> onSuccess,
            Consumer<Exception> onFailure) {
        controlExecutor.execute(
                () -> {
                    try {
                        onSuccess.accept(
                                compositionRoot().completeOnboarding(
                                        expectedRevision,
                                        idempotencyKey));
                    } catch (Exception failure) {
                        onFailure.accept(failure);
                    }
                });
    }

    public boolean retryStartupRecovery() {
        if (getStartupReadiness().getState()
                != FaroV5StartupReadiness.State.MAINTENANCE_ONLY) {
            return false;
        }
        return scheduleStartupRecovery();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FaroV5ActivityForegroundTracker tracker =
                new FaroV5ActivityForegroundTracker();
        foregroundTracker = tracker;
        registerActivityLifecycleCallbacks(tracker);

        scheduleStartupRecovery();
    }

    private boolean scheduleStartupRecovery() {
        FaroV5ActivityForegroundTracker tracker = foregroundTracker;
        if (tracker == null || !startupRecoveryRunning.compareAndSet(false, true)) {
            return false;
        }

        startupExecutor.execute(
                () -> {
                    try {
                        FaroV5CompositionRoot root = compositionRoot();
                        FaroV5StartupReadiness readiness =
                                root.recoverPendingMaintenanceState(tracker);
                        if (!readiness.isLocalReady()) {
                            return;
                        }
                        root.installProcessBridges();
                        root.reprojectStartup();
                    } finally {
                        startupRecoveryRunning.set(false);
                    }
                });
        return true;
    }
}
