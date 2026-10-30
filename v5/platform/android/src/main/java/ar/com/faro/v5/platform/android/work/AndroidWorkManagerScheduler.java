package ar.com.faro.v5.platform.android.work;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Android scheduling projection for durable FARO work.
 *
 * <p>This adapter is deliberately not a source of truth. WorkManager receives only opaque
 * identifiers; workers must re-read current FARO authority when they execute.
 */
public final class AndroidWorkManagerScheduler {
    public static final String INPUT_SEMANTIC_ID = "faro.v5.semantic_id";
    public static final String INPUT_WORK_KIND = "faro.v5.work_kind";

    public static final String ROOT_TAG = "faro-v5";
    private static final String UNIQUE_PREFIX = "faro-v5/";
    private static final long MIN_PERIODIC_MINUTES = 15L;
    private static final Pattern OPAQUE_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final WorkManager workManager;

    public AndroidWorkManagerScheduler(WorkManager workManager) {
        this.workManager = Objects.requireNonNull(workManager, "workManager");
    }

    /**
     * Ensures at most one pending/running one-shot projection for the same semantic identity.
     *
     * <p>KEEP prevents a duplicate enqueue from replacing work already in flight.
     */
    public Operation ensureOneTime(
            String workKind,
            String semanticId,
            Class<? extends ListenableWorker> workerClass) {
        String normalizedKind = requireOpaqueId("workKind", workKind);
        String normalizedSemanticId = requireOpaqueId("semanticId", semanticId);
        Objects.requireNonNull(workerClass, "workerClass");

        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(workerClass)
                        .setInputData(identityInput(normalizedKind, normalizedSemanticId))
                        .addTag(ROOT_TAG)
                        .addTag(kindTag(normalizedKind))
                        .build();

        return workManager.enqueueUniqueWork(
                uniqueWorkName(normalizedKind, normalizedSemanticId),
                ExistingWorkPolicy.KEEP,
                request);
    }

    /**
     * Creates or non-destructively updates periodic work for the same semantic identity.
     *
     * <p>UPDATE preserves the existing enqueue history and does not cancel a currently running
     * worker merely because its periodic projection is refreshed.
     */
    public Operation upsertPeriodic(
            String workKind,
            String semanticId,
            long repeatIntervalMinutes,
            Class<? extends ListenableWorker> workerClass) {
        String normalizedKind = requireOpaqueId("workKind", workKind);
        String normalizedSemanticId = requireOpaqueId("semanticId", semanticId);
        Objects.requireNonNull(workerClass, "workerClass");
        if (repeatIntervalMinutes < MIN_PERIODIC_MINUTES) {
            throw new IllegalArgumentException(
                    "repeatIntervalMinutes must be >= " + MIN_PERIODIC_MINUTES);
        }

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                                workerClass,
                                repeatIntervalMinutes,
                                TimeUnit.MINUTES)
                        .setInputData(identityInput(normalizedKind, normalizedSemanticId))
                        .addTag(ROOT_TAG)
                        .addTag(kindTag(normalizedKind))
                        .build();

        return workManager.enqueueUniquePeriodicWork(
                uniqueWorkName(normalizedKind, normalizedSemanticId),
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    public Operation cancel(String workKind, String semanticId) {
        return workManager.cancelUniqueWork(uniqueWorkName(workKind, semanticId));
    }

    public static String uniqueWorkName(String workKind, String semanticId) {
        return UNIQUE_PREFIX
                + requireOpaqueId("workKind", workKind)
                + "/"
                + requireOpaqueId("semanticId", semanticId);
    }

    public static Data identityInput(String workKind, String semanticId) {
        return new Data.Builder()
                .putString(INPUT_WORK_KIND, requireOpaqueId("workKind", workKind))
                .putString(INPUT_SEMANTIC_ID, requireOpaqueId("semanticId", semanticId))
                .build();
    }

    private static String kindTag(String workKind) {
        return "faro-v5-kind:" + workKind;
    }

    static String requireOpaqueId(String label, String value) {
        if (value == null || !OPAQUE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    label + " must be an opaque ASCII identifier (1..128 chars)");
        }
        return value;
    }
}
