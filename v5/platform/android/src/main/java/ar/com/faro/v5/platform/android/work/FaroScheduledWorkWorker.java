package ar.com.faro.v5.platform.android.work;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionPort;
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionResult;
import java.util.Objects;

/**
 * Thin WorkManager trigger that delegates IDs to current FARO composition.
 *
 * <p>No domain snapshot or store is owned by this worker. The injected port must re-read current
 * FARO authority. Only an explicit RETRY result opts into WorkManager retry/backoff.
 */
public final class FaroScheduledWorkWorker extends Worker {
    private final ScheduledWorkExecutionPort executionPort;

    FaroScheduledWorkWorker(
            Context appContext,
            WorkerParameters workerParameters,
            ScheduledWorkExecutionPort executionPort) {
        super(appContext, workerParameters);
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort");
    }

    @Override
    public Result doWork() {
        String workKind = getInputData().getString(AndroidWorkManagerScheduler.INPUT_WORK_KIND);
        String semanticId = getInputData().getString(AndroidWorkManagerScheduler.INPUT_SEMANTIC_ID);

        try {
            workKind = AndroidWorkManagerScheduler.requireOpaqueId("workKind", workKind);
            semanticId = AndroidWorkManagerScheduler.requireOpaqueId("semanticId", semanticId);
        } catch (IllegalArgumentException invalidIdentity) {
            return Result.failure();
        }

        final ScheduledWorkExecutionResult outcome;
        try {
            outcome = executionPort.execute(workKind, semanticId);
        } catch (RuntimeException unexpectedFailure) {
            return Result.failure();
        }

        if (outcome == null) {
            return Result.failure();
        }
        switch (outcome) {
            case SUCCESS:
                return Result.success();
            case RETRY:
                return Result.retry();
            case FAILURE:
            default:
                return Result.failure();
        }
    }
}
