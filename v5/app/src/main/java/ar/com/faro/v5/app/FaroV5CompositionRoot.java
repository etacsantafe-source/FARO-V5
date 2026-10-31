package ar.com.faro.v5.app;

import android.content.Context;
import androidx.work.WorkManager;
import ar.com.faro.v5.control.OnboardingControlFacade;
import ar.com.faro.v5.control.OnboardingMutationOutcome;
import ar.com.faro.v5.control.OnboardingSnapshot;
import ar.com.faro.v5.delivery.api.NotificationDeliveryDispatcher;
import ar.com.faro.v5.delivery.api.NotificationDeliveryReconciler;
import ar.com.faro.v5.delivery.api.NotificationDeliveryReprojector;
import ar.com.faro.v5.delivery.api.NotificationDeliveryScheduledWorkHandler;
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate;
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort;
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort;
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort;
import ar.com.faro.v5.kernel.contracts.ConversationAssistantTurnDispatchPort;
import ar.com.faro.v5.kernel.contracts.ConversationMessage;
import ar.com.faro.v5.kernel.contracts.ModelGateway;
import ar.com.faro.v5.kernel.contracts.ModelProfileId;
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort;
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort;
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext;
import ar.com.faro.v5.kernel.contracts.ConversationApplyHostOutcome;
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort;
import ar.com.faro.v5.maintenance.StagedStorageGeneration;
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionPort;
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionResult;
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate;
import ar.com.faro.v5.maintenance.ActiveGenerationStorageLocator;
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph;
import ar.com.faro.v5.maintenance.GenerationActivationResult;
import ar.com.faro.v5.maintenance.MaintenanceOperationKind;
import ar.com.faro.v5.maintenance.ResetBlockReason;
import ar.com.faro.v5.maintenance.ResetExecutionRequest;
import ar.com.faro.v5.maintenance.ResetExecutionResult;
import ar.com.faro.v5.platform.android.AndroidPlatformAdapterFactory;
import ar.com.faro.v5.platform.android.alarm.FaroExactAlarmExecutionBridge;
import ar.com.faro.v5.platform.android.notification.AndroidNotificationDeliverySink;
import ar.com.faro.v5.platform.android.update.AndroidUpdateForegroundStatePort;
import ar.com.faro.v5.platform.android.work.AndroidScheduledDeliveryWorkProjection;
import ar.com.faro.v5.platform.android.work.AndroidWorkManagerScheduler;
import ar.com.faro.v5.platform.android.work.FaroWorkerFactory;
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

/**
 * Terminal application composition for the P3 Delivery path.
 *
 * <p>This class wires concrete implementations but owns no domain semantics or durable truth.
 * Store construction stays lazy so WorkManager configuration remains lightweight.
 */
public final class FaroV5CompositionRoot {
    private final Context appContext;
    private final SingleProcessMaterialWriteGate materialWriteGate;
    private final ScheduledWorkExecutionPort executionPort;
    private final FaroWorkerFactory workerFactory;
    private final Path storageRoot;
    private volatile ActiveGenerationStorageLocator generationStorage;
    private volatile FaroV5MaintenanceRuntime maintenanceRuntime;
    private volatile FaroV5UpdateRuntime updateRuntime;
    private final AndroidPlatformAdapterFactory androidPlatformAdapters;
    private final FaroV5StartupReadinessGate startupReadinessGate;
    private final Object maintenanceAdmissionLock = new Object();

    private volatile DeliveryGraph deliveryGraph;
    private volatile TrustedAssistantRuntime trustedAssistantRuntime;
    private volatile OnboardingGraph onboardingGraph;
    private volatile boolean maintenanceRecoveryComplete = false;
    private volatile StartupReprojectionState startupReprojectionState =
            StartupReprojectionState.NOT_STARTED;

    public FaroV5CompositionRoot(Context context) {
        this.appContext = context.getApplicationContext();
        this.storageRoot =
                new File(appContext.getNoBackupFilesDir(), "faro-v5-storage").toPath();
        this.materialWriteGate = new SingleProcessMaterialWriteGate();
        this.startupReadinessGate =
                new FaroV5StartupReadinessGate(materialWriteGate);
        this.executionPort =
                (workKind, semanticId) -> {
                    if (!maintenanceRecoveryComplete) {
                        return ScheduledWorkExecutionResult.RETRY;
                    }
                    return materialWriteGate.supplyMaterialWrite(
                            () -> deliveryGraph().handler.execute(workKind, semanticId));
                };
        this.workerFactory = new FaroWorkerFactory(executionPort);
        this.androidPlatformAdapters =
                new AndroidPlatformAdapterFactory(appContext);
    }

    /**
     * Trusted application-bootstrap injection only, before any DeliveryGraph is
     * opened. No UI, chat message or provider response may install credentials,
     * choose a model profile or grant egress through this method.
     * Shipping V1 and uncertified V2 never expose an assistant dispatch port.
     */
    synchronized void configureTrustedAssistantRuntime(
            ModelGateway gateway,
            Supplier<ModelProfileId> profile,
            Predicate<ConversationMessage> egressAdmission) {
        if (deliveryGraph != null) {
            throw new IllegalStateException(
                    "trusted assistant configuration must precede graph construction");
        }
        trustedAssistantRuntime = new TrustedAssistantRuntime(
                Objects.requireNonNull(gateway, "gateway"),
                Objects.requireNonNull(profile, "profile"),
                Objects.requireNonNull(egressAdmission, "egressAdmission"));
    }

    /** Staged V2 host wiring only; no active pointer, Apply or cutover. */
    FaroV5GenerationStoreGraph openQuarantinedV2WithHostGate(
            StagedStorageGeneration staged) {
        return FaroV5QuarantinedV2GraphAdmission.canonical(
                materialWriteGate,
                () -> generationStorage().activeGenerationDirectory()).open(staged);
    }

    /**
     * C125 F1: bounded Android host binding for a NON-ACTIVE V2 candidate.
     * It exposes the real five-store Workspace read authority to the shell,
     * but neither installs the shell nor enables runtime Apply/OS effects.
     */
    FaroV5V2WorkspaceHostBinding openQuarantinedV2WorkspaceHost(
            StagedStorageGeneration staged) {
        return FaroV5V2WorkspaceHostBinding.openQuarantined(
                staged, materialWriteGate,
                () -> generationStorage().activeGenerationDirectory());
    }

    public FaroWorkerFactory getWorkerFactory() {
        return workerFactory;
    }

    public AndroidPlatformAdapterFactory getAndroidPlatformAdapters() {
        return androidPlatformAdapters;
    }

    public MaterialWriteGate getMaterialWriteGate() {
        return materialWriteGate;
    }

    public StartupReprojectionState getStartupReprojectionState() {
        return startupReprojectionState;
    }

    public FaroV5StartupReadiness getStartupReadiness() {
        return startupReadinessGate.snapshot();
    }

    public void addStartupReadinessListener(
            FaroV5StartupReadinessListener listener) {
        startupReadinessGate.addListener(listener);
    }

    public void removeStartupReadinessListener(
            FaroV5StartupReadinessListener listener) {
        startupReadinessGate.removeListener(listener);
    }

    public void installUpdateProcessBridges(AndroidUpdateForegroundStatePort foregroundState) {
        updateRuntime().installProcessBridges(foregroundState);
    }

    public void installProcessBridges() {
        FaroExactAlarmExecutionBridge.install(executionPort);
    }

    public FaroV5UpdateRuntime getUpdateRuntime() {
        return updateRuntime();
    }

    private ActiveGenerationStorageLocator generationStorage() {
        ActiveGenerationStorageLocator current = generationStorage;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = generationStorage;
            if (current == null) {
                current = new ActiveGenerationStorageLocator(storageRoot);
                generationStorage = current;
            }
            return current;
        }
    }

    // S2 F3 non-exported consent surface; shares the canonical maintenance owner.
    // It cannot bypass the V2 certification gate or startup recovery.
    FaroV5MaintenanceRuntime fiveStoreSafConsentRuntime() {
        return maintenanceRuntime();
    }

    private FaroV5MaintenanceRuntime maintenanceRuntime() {
        FaroV5MaintenanceRuntime current = maintenanceRuntime;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = maintenanceRuntime;
            if (current == null) {
                current = new FaroV5MaintenanceRuntime(appContext, this);
                maintenanceRuntime = current;
            }
            return current;
        }
    }

    private FaroV5UpdateRuntime updateRuntime() {
        FaroV5UpdateRuntime current = updateRuntime;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = updateRuntime;
            if (current == null) {
                current =
                        new FaroV5UpdateRuntime(
                                appContext,
                                storageRoot,
                                R.drawable.ic_faro_notification);
                updateRuntime = current;
            }
            return current;
        }
    }

    public FaroV5StartupReadiness recoverPendingMaintenanceState(
            AndroidUpdateForegroundStatePort foregroundState) {
        requireMaintenanceRecovery();

        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.PREPARING_STORAGE,
                FaroV5StartupReadiness.MaintenanceReason.INITIAL_STORAGE_BOOTSTRAP_FAILED,
                () ->
                        generationStorage().ensureInitialGenerationAndGetDirectory(
                                "initial-generation-v1",
                                "faro-v5-storage-schema-v1"))) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.INITIALIZING_MAINTENANCE,
                FaroV5StartupReadiness.MaintenanceReason.MAINTENANCE_RUNTIME_INITIALIZATION_FAILED,
                () -> maintenanceRuntime())) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_EXTERNAL_EFFECTS,
                FaroV5StartupReadiness.MaintenanceReason.EXTERNAL_EFFECT_RECOVERY_FAILED,
                () -> maintenanceRuntime().reconcileInterruptedExternalEffects())) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_UPDATE,
                FaroV5StartupReadiness.MaintenanceReason.UPDATE_RUNTIME_INITIALIZATION_FAILED,
                () -> updateRuntime())) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_UPDATE,
                FaroV5StartupReadiness.MaintenanceReason.UPDATE_BRIDGE_INITIALIZATION_FAILED,
                () -> installUpdateProcessBridges(foregroundState))) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_UPDATE,
                FaroV5StartupReadiness.MaintenanceReason.UPDATE_RECONCILIATION_FAILED,
                () -> updateRuntime().reconcilePending())) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupRecoveryStep(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_RESET,
                FaroV5StartupReadiness.MaintenanceReason.RESET_RECOVERY_FAILED,
                () -> maintenanceRuntime().recoverPendingResetsOrThrow())) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupRecoveryStep(
                FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION,
                FaroV5StartupReadiness.MaintenanceReason.GENERATION_ACTIVATION_RECOVERY_FAILED,
                () -> maintenanceRuntime().recoverPendingActivationsOrThrow())) {
            return startupReadinessGate.snapshot();
        }
        if (!recoverStartupStep(
                FaroV5StartupReadiness.StartupPhase.VERIFYING_LOCAL_DATA,
                FaroV5StartupReadiness.MaintenanceReason.ACTIVE_GENERATION_HEALTH_FAILED,
                this::requireActiveGenerationHealthy)) {
            return startupReadinessGate.snapshot();
        }

        completeStartupRecovery();
        return startupReadinessGate.snapshot();
    }

    private boolean recoverStartupStep(
            FaroV5StartupReadiness.StartupPhase phase,
            FaroV5StartupReadiness.MaintenanceReason reason,
            Runnable recoveryStep) {
        startupReadinessGate.initializingPhase(phase);
        return executeStartupRecoveryStep(
                reason,
                FaroV5StartupReadiness.RecoveryKind.GENERIC,
                recoveryStep);
    }

    private boolean recoverStartupRecoveryStep(
            FaroV5StartupReadiness.StartupPhase phase,
            FaroV5StartupReadiness.MaintenanceReason reason,
            Runnable recoveryStep) {
        FaroV5StartupReadiness.RecoveryKind recoveryKind =
                recoveryKindFor(phase);
        startupReadinessGate.initializingRecovery(
                phase,
                recoveryKind);
        return executeStartupRecoveryStep(
                reason,
                recoveryKind,
                recoveryStep);
    }

    private FaroV5StartupReadiness.RecoveryKind recoveryKindFor(
            FaroV5StartupReadiness.StartupPhase phase) {
        final Set<MaintenanceOperationKind> pendingKinds;
        try {
            pendingKinds = maintenanceRuntime().pendingBootstrapKinds();
        } catch (RuntimeException presentationDetailFailure) {
            return FaroV5StartupReadiness.RecoveryKind.GENERIC;
        }

        if (phase == FaroV5StartupReadiness.StartupPhase.RECOVERING_RESET) {
            return pendingKinds.contains(MaintenanceOperationKind.RESET)
                    ? FaroV5StartupReadiness.RecoveryKind.RESET
                    : FaroV5StartupReadiness.RecoveryKind.GENERIC;
        }
        if (phase != FaroV5StartupReadiness.StartupPhase.RECOVERING_GENERATION) {
            return FaroV5StartupReadiness.RecoveryKind.GENERIC;
        }

        boolean migration = pendingKinds.contains(MaintenanceOperationKind.MIGRATION);
        boolean restore = pendingKinds.contains(MaintenanceOperationKind.RESTORE);
        if (migration && restore) {
            return FaroV5StartupReadiness.RecoveryKind.MULTIPLE;
        }
        if (migration) {
            return FaroV5StartupReadiness.RecoveryKind.MIGRATION;
        }
        if (restore) {
            return FaroV5StartupReadiness.RecoveryKind.RESTORE;
        }
        return FaroV5StartupReadiness.RecoveryKind.GENERIC;
    }

    private boolean executeStartupRecoveryStep(
            FaroV5StartupReadiness.MaintenanceReason reason,
            FaroV5StartupReadiness.RecoveryKind recoveryKind,
            Runnable recoveryStep) {
        try {
            recoveryStep.run();
            return true;
        } catch (Exception failure) {
            startupReprojectionState = StartupReprojectionState.DEGRADED;
            startupReadinessGate.maintenanceOnly(
                    reason,
                    recoveryKind);
            return false;
        }
    }

    private void requireActiveGenerationHealthy() {
        String activeGenerationId = generationStorage().activeGenerationId();
        checkStartupCondition(
                activateAndVerifyGeneration(activeGenerationId),
                "active StorageGeneration failed runtime health verification");
    }

    private static void checkStartupCondition(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public ResetExecutionResult executeMaintenanceReset(ResetExecutionRequest request) {
        beginInteractiveMaintenanceOperation();

        ResetExecutionResult result = maintenanceRuntime().executeReset(request);
        if (resetResultAllowsMaterialWrites(result)) {
            completeMaintenanceOperation();
        }
        return result;
    }

    public GenerationActivationResult activateValidatedMaintenanceGeneration(String operationId) {
        beginInteractiveMaintenanceOperation();

        GenerationActivationResult result = maintenanceRuntime().activateValidated(operationId);
        if (activationResultAllowsMaterialWrites(result)) {
            completeMaintenanceOperation();
        }
        return result;
    }

    private void beginInteractiveMaintenanceOperation() {
        synchronized (maintenanceAdmissionLock) {
            if (!maintenanceRecoveryComplete) {
                throw new IllegalStateException(
                        "another Maintenance operation or recovery debt is still active");
            }
            maintenanceRecoveryComplete = false;
        }
        materialWriteGate.closeMaterialWriteAdmission();
    }

    private void requireMaintenanceRecovery() {
        synchronized (maintenanceAdmissionLock) {
            maintenanceRecoveryComplete = false;
        }
        startupReprojectionState = StartupReprojectionState.NOT_STARTED;
        startupReadinessGate.beginRecovery();
    }

    private void completeStartupRecovery() {
        startupReadinessGate.localReady();
        synchronized (maintenanceAdmissionLock) {
            maintenanceRecoveryComplete = true;
        }
    }

    private void completeMaintenanceOperation() {
        materialWriteGate.openMaterialWriteAdmission();
        synchronized (maintenanceAdmissionLock) {
            maintenanceRecoveryComplete = true;
        }
    }

    private static boolean resetResultAllowsMaterialWrites(ResetExecutionResult result) {
        if (result instanceof ResetExecutionResult.Completed) {
            return true;
        }
        if (!(result instanceof ResetExecutionResult.Blocked)) {
            return false;
        }
        ResetBlockReason reason = ((ResetExecutionResult.Blocked) result).getReason();
        return reason == ResetBlockReason.INVALID_PLAN
                || reason == ResetBlockReason.APPROVAL_MISMATCH
                || reason == ResetBlockReason.TARGET_VERIFICATION_FAILED
                || reason == ResetBlockReason.ABORTED_PRE_SWITCH;
    }

    private static boolean activationResultAllowsMaterialWrites(
            GenerationActivationResult result) {
        return result instanceof GenerationActivationResult.Activated
                || result instanceof GenerationActivationResult.RolledBack;
    }

    public void reprojectStartup() {
        startupReprojectionState = StartupReprojectionState.RUNNING;
        try {
            AndroidWorkManagerScheduler scheduler =
                    new AndroidWorkManagerScheduler(WorkManager.getInstance(appContext));
            AndroidScheduledDeliveryWorkProjection workProjection =
                    new AndroidScheduledDeliveryWorkProjection(scheduler);
            NotificationDeliveryReprojector reprojector =
                    new NotificationDeliveryReprojector(
                            deliveryGraph().store,
                            workProjection);
            reprojector.reproject(NotificationDeliveryReprojector.DEFAULT_SCAN_LIMIT);
            startupReprojectionState = StartupReprojectionState.PASS;
        } catch (RuntimeException failure) {
            startupReprojectionState = StartupReprojectionState.DEGRADED;
        }
    }

    public boolean activateAndVerifyGeneration(String expectedGenerationId) {
        synchronized (this) {
            if (!generationStorage().activeGenerationId().equals(expectedGenerationId)) {
                return false;
            }
            DeliveryGraph current = deliveryGraph;
            if (current != null) {
                current.close();
                deliveryGraph = null;
            }
            OnboardingGraph currentOnboarding = onboardingGraph;
            if (currentOnboarding != null) {
                currentOnboarding.control.close();
                onboardingGraph = null;
            }
            try {
                DeliveryGraph reopened = deliveryGraph();
                return reopened.generationId.equals(expectedGenerationId)
                        && reopened.stores.verifyHealthy();
            } catch (RuntimeException failure) {
                return false;
            }
        }
    }

    /**
     * Real active-generation Workspace read channel for the already-mounted shell.
     * V1 intentionally has no fifth owner and returns null. No staged V2 may
     * enter this path; activation still requires a separately certified switch.
     */
    public ConversationWorkspaceReadPort readActiveWorkspacePort() {
        if (!maintenanceRecoveryComplete) return null;
        FaroV5ActiveChatReadSession session = deliveryGraph().chatReadSession;
        return session == null ? null : session.workspaceReadPort;
    }

    public ConversationTimelineReadPort readActiveTimelinePort() {
        if (!maintenanceRecoveryComplete) return null;
        FaroV5ActiveChatReadSession session = deliveryGraph().chatReadSession;
        return session == null ? null : session.timelineReadPort;
    }

    /** Real generation-bound Composer mutations. Null for shipping V1 (V2 OFF). */
    public ConversationComposerPort activeComposerPort() {
        if (!maintenanceRecoveryComplete) return null;
        FaroV5ActiveChatComposerSession session = deliveryGraph().chatComposerSession;
        return session == null ? null : session.port;
    }

    public ConversationReviewPort activeReviewPort() {
        if (!maintenanceRecoveryComplete) return null;
        FaroV5ActiveChatReviewSession session = deliveryGraph().chatReviewSession;
        return session == null ? null : session.port;
    }

    public ConversationWorkspaceSelectionPort activeWorkspaceSelectionPort() {
        if (!maintenanceRecoveryComplete) return null;
        FaroV5ActiveChatSelectionSession session = deliveryGraph().chatSelectionSession;
        return session == null ? null : session.port;
    }

    /**
     * No assistant model calls for shipping V1 or uncertified V2. A future
     * trusted bootstrap must supply the policy-bound ModelGateway/profile.
     */
    public ConversationAssistantTurnDispatchPort activeAssistantTurnPort() {
        if (!maintenanceRecoveryComplete || !FaroV5HostCertification.V2_ACTIVE_ENABLED)
            return null;
        DeliveryGraph graph = deliveryGraph();
        return graph.chatAssistantDispatch;
    }

    /** No user-facing Apply before the COMPLETE F1-F4 V2 certification. */
    public ConversationApplyRequestPort activeApplyRequestPort() {
        if (!maintenanceRecoveryComplete || !FaroV5HostCertification.V2_ACTIVE_ENABLED) return null;
        FaroV5ActiveReviewedApplySession session = deliveryGraph().chatApplySession;
        return session == null ? null : session.port;
    }

    /** Context-only reviewed Apply; disabled for shipping V1 and pending recovery. */
    public ConversationApplyHostOutcome applyActiveReviewedPlan(
            ConversationPlanReviewContext context) {
        if (!maintenanceRecoveryComplete || !FaroV5HostCertification.V2_ACTIVE_ENABLED) return null;
        FaroV5ActiveReviewedApplySession session = deliveryGraph().chatApplySession;
        return session == null ? null : session.executeAfterHumanRequest(context);
    }

    public String activeGenerationId() {
        return generationStorage().activeGenerationId();
    }

    public OnboardingSnapshot readOnboardingState() {
        return onboardingGraph().control.readBlocking();
    }

    public OnboardingMutationOutcome deferOnboarding(
            long expectedRevision,
            String idempotencyKey) {
        // Same app-owned admission gate as Delivery and Maintenance.
        // Acquire BEFORE opening the Onboarding graph, so quiescence cannot
        // deadlock with a store monitor or generation activation.
        return materialWriteGate.supplyMaterialWrite(
                () -> onboardingGraph().control.deferBlocking(
                        expectedRevision,
                        idempotencyKey));
    }

    public OnboardingMutationOutcome completeOnboarding(
            long expectedRevision,
            String idempotencyKey) {
        return materialWriteGate.supplyMaterialWrite(
                () -> onboardingGraph().control.completeBlocking(
                        expectedRevision,
                        idempotencyKey));
    }

    private OnboardingGraph onboardingGraph() {
        String activeGenerationId = generationStorage().activeGenerationId();
        OnboardingGraph current = onboardingGraph;
        if (current != null && current.generationId.equals(activeGenerationId)) {
            return current;
        }
        synchronized (this) {
            activeGenerationId = generationStorage().activeGenerationId();
            current = onboardingGraph;
            if (current != null && !current.generationId.equals(activeGenerationId)) {
                current.control.close();
                current = null;
                onboardingGraph = null;
            }
            if (current == null) {
                File onboardingFile =
                        generationStorage()
                                .activeGenerationDirectory()
                                .resolve("onboarding.preferences_pb")
                                .toFile();
                current =
                        new OnboardingGraph(
                                activeGenerationId,
                                new OnboardingControlFacade(
                                        onboardingFile));
                onboardingGraph = current;
            }
            return current;
        }
    }

    private DeliveryGraph deliveryGraph() {
        String activeGenerationId = generationStorage().activeGenerationId();
        DeliveryGraph current = deliveryGraph;
        if (current != null && current.generationId.equals(activeGenerationId)) {
            return current;
        }
        synchronized (this) {
            activeGenerationId = generationStorage().activeGenerationId();
            current = deliveryGraph;
            if (current != null && !current.generationId.equals(activeGenerationId)) {
                current.close();
                current = null;
                deliveryGraph = null;
            }
            if (current == null) {
                // Version-aware runtime selection, but the production host
                // STILL denies V2 activation until F1-F4 are certified.
                FaroV5GenerationStoreGraph stores =
                        FaroV5ActiveGenerationGraphOwner.open(
                                storageRoot,
                                activeGenerationId,
                                materialWriteGate,
                                FaroV5HostCertification.V2_ACTIVE_ENABLED);
                AuxiliaryDeliveryStore store = stores.getAuxiliary();
                AndroidNotificationDeliverySink sink =
                        new AndroidNotificationDeliverySink(
                                appContext,
                                R.drawable.ic_faro_notification);
                NotificationDeliveryDispatcher dispatcher =
                        new NotificationDeliveryDispatcher(store, sink);
                NotificationDeliveryReconciler reconciler =
                        new NotificationDeliveryReconciler(store, sink);
                NotificationDeliveryScheduledWorkHandler handler =
                        new NotificationDeliveryScheduledWorkHandler(
                                store,
                                dispatcher,
                                reconciler);
                FaroV5ActiveChatReadSession chatReadSession =
                        stores.getWorkspace() == null ? null
                                : new FaroV5ActiveChatReadSession(
                                        activeGenerationId,
                                        stores.getWorkspace(),
                                        stores.getWorkspace(),
                                        () -> generationStorage().activeGenerationId(),
                                        () -> maintenanceRecoveryComplete);
                FaroV5ActiveChatComposerSession chatComposerSession =
                        stores.getWorkspace() == null ? null
                                : new FaroV5ActiveChatComposerSession(
                                        activeGenerationId,
                                        stores.getWorkspace(),
                                        materialWriteGate,
                                        () -> generationStorage().activeGenerationId(),
                                        () -> maintenanceRecoveryComplete);
                FaroV5ActiveChatReviewSession chatReviewSession =
                        stores.getWorkspace() == null ? null
                                : new FaroV5ActiveChatReviewSession(
                                        activeGenerationId,
                                        stores.getWorkspace(),
                                        materialWriteGate,
                                        () -> generationStorage().activeGenerationId(),
                                        () -> maintenanceRecoveryComplete);
                FaroV5ActiveChatSelectionSession chatSelectionSession =
                        stores.getWorkspace() == null ? null
                                : new FaroV5ActiveChatSelectionSession(
                                        activeGenerationId, stores.getWorkspace(),
                                        materialWriteGate,
                                        () -> generationStorage().activeGenerationId(),
                                        () -> maintenanceRecoveryComplete);
                FaroV5ActiveReviewedApplySession chatApplySession =
                        stores.getWorkspace() == null ? null
                                : new FaroV5ActiveReviewedApplySession(
                                        activeGenerationId, stores.getWorkspace(),
                                        stores.getReality(), materialWriteGate,
                                        () -> generationStorage().activeGenerationId(),
                                        () -> maintenanceRecoveryComplete);
                FaroV5HostAcceptedTurnDispatchPort assistantDispatch = null;
                TrustedAssistantRuntime assistantConfig = trustedAssistantRuntime;
                if (stores.getWorkspace() != null &&
                        FaroV5HostCertification.V2_ACTIVE_ENABLED &&
                        assistantConfig != null) {
                    final String selectedGeneration = activeGenerationId;
                    BooleanSupplier admitted = () ->
                            maintenanceRecoveryComplete &&
                            FaroV5HostCertification.V2_ACTIVE_ENABLED &&
                            generationStorage().activeGenerationId()
                                    .equals(selectedGeneration);
                    FaroV5ActiveChatReasoningSession reasoning =
                            new FaroV5ActiveChatReasoningSession(
                                    selectedGeneration, stores.getWorkspace(),
                                    materialWriteGate,
                                    () -> generationStorage().activeGenerationId(),
                                    () -> maintenanceRecoveryComplete,
                                    assistantConfig.gateway,
                                    message -> assistantConfig.egressAdmission.test(message));
                    // Dormant until the full V2 activation gate is certified.
                    // Only a COMPLETED durable FARO reply may propose a trusted,
                    // locally fingerprinted review; the model never gains Apply.
                    FaroV5ModelReviewBridge modelReview =
                            new FaroV5ModelReviewBridge(
                                    selectedGeneration, stores.getWorkspace(),
                                    stores.getReality(), materialWriteGate,
                                    () -> generationStorage().activeGenerationId(),
                                    admitted);
                    assistantDispatch = new FaroV5HostAcceptedTurnDispatchPort(
                            new FaroV5HostAssistantTurnDispatcher(
                                    reasoning,
                                    Executors.newSingleThreadExecutor(
                                            task -> new Thread(task, "faro-v5-assistant")),
                                    admitted,
                                    modelReview.callback()),
                            assistantConfig.profile, admitted);
                }
                current =
                        new DeliveryGraph(
                                activeGenerationId,
                                stores,
                                store,
                                handler,
                                chatReadSession,
                                chatComposerSession,
                                chatReviewSession,
                                chatSelectionSession,
                                chatApplySession,
                                assistantDispatch);
                deliveryGraph = current;
            }
            return current;
        }
    }

    public enum StartupReprojectionState {
        NOT_STARTED,
        RUNNING,
        PASS,
        DEGRADED
    }

    private static final class OnboardingGraph {
        final String generationId;
        final OnboardingControlFacade control;

        OnboardingGraph(
                String generationId,
                OnboardingControlFacade control) {
            this.generationId = generationId;
            this.control = control;
        }
    }

    private static final class TrustedAssistantRuntime {
        final ModelGateway gateway;
        final Supplier<ModelProfileId> profile;
        final Predicate<ConversationMessage> egressAdmission;

        TrustedAssistantRuntime(
                ModelGateway gateway,
                Supplier<ModelProfileId> profile,
                Predicate<ConversationMessage> egressAdmission) {
            this.gateway = gateway;
            this.profile = profile;
            this.egressAdmission = egressAdmission;
        }
    }

    private static final class DeliveryGraph {
        final String generationId;
        final FaroV5GenerationStoreGraph stores;
        final AuxiliaryDeliveryStore store;
        final NotificationDeliveryScheduledWorkHandler handler;
        final FaroV5ActiveChatReadSession chatReadSession;
        final FaroV5ActiveChatComposerSession chatComposerSession;
        final FaroV5ActiveChatReviewSession chatReviewSession;
        final FaroV5ActiveChatSelectionSession chatSelectionSession;
        final FaroV5ActiveReviewedApplySession chatApplySession;
        final FaroV5HostAcceptedTurnDispatchPort chatAssistantDispatch;

        DeliveryGraph(
                String generationId,
                FaroV5GenerationStoreGraph stores,
                AuxiliaryDeliveryStore store,
                NotificationDeliveryScheduledWorkHandler handler,
                FaroV5ActiveChatReadSession chatReadSession,
                FaroV5ActiveChatComposerSession chatComposerSession,
                FaroV5ActiveChatReviewSession chatReviewSession,
                FaroV5ActiveChatSelectionSession chatSelectionSession,
                FaroV5ActiveReviewedApplySession chatApplySession,
                FaroV5HostAcceptedTurnDispatchPort chatAssistantDispatch) {
            this.generationId = generationId;
            this.stores = stores;
            this.store = store;
            this.handler = handler;
            this.chatReadSession = chatReadSession;
            this.chatComposerSession = chatComposerSession;
            this.chatReviewSession = chatReviewSession;
            this.chatSelectionSession = chatSelectionSession;
            this.chatApplySession = chatApplySession;
            this.chatAssistantDispatch = chatAssistantDispatch;
        }

        void close() {
            try {
                if (chatAssistantDispatch != null) chatAssistantDispatch.close();
            } finally {
                stores.close();
            }
        }
    }
}
