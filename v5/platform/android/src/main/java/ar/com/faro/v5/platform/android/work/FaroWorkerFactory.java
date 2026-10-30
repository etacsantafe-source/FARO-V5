package ar.com.faro.v5.platform.android.work;

import android.content.Context;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionPort;
import java.util.Objects;

/**
 * Dependency injection bridge for FARO scheduled work.
 *
 * <p>Unrelated worker classes are deliberately declined so WorkManager can use its normal fallback.
 */
public final class FaroWorkerFactory extends WorkerFactory {
    private final ScheduledWorkExecutionPort executionPort;

    public FaroWorkerFactory(ScheduledWorkExecutionPort executionPort) {
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort");
    }

    @Override
    public ListenableWorker createWorker(
            Context appContext,
            String workerClassName,
            WorkerParameters workerParameters) {
        if (!FaroScheduledWorkWorker.class.getName().equals(workerClassName)) {
            return null;
        }
        return new FaroScheduledWorkWorker(appContext, workerParameters, executionPort);
    }
}
