package ar.com.faro.v5.platform.android.search;

import android.content.Context;
import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchResult;
import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.app.PutDocumentsRequest;
import androidx.appsearch.app.RemoveByDocumentIdRequest;
import androidx.appsearch.app.SearchResult;
import androidx.appsearch.app.SearchResults;
import androidx.appsearch.app.SearchSpec;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.localstorage.LocalStorage;
import ar.com.faro.v5.control.search.RebuildableSearchProjectionPort;
import ar.com.faro.v5.control.search.SearchProjectionDocument;
import ar.com.faro.v5.control.search.SearchProjectionException;
import ar.com.faro.v5.control.search.SearchProjectionHit;
import ar.com.faro.v5.control.search.SearchProjectionKey;
import ar.com.faro.v5.control.search.SearchProjectionQuery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * App-private LocalStorage projection for bounded FARO full-text lookup.
 *
 * <p>This database is disposable platform state. It never stores SavedView definitions, never
 * publishes to System UI or other apps, and returns only opaque canonical locators plus the source
 * stamp supplied at indexing time.
 */
public final class AndroidAppSearchProjection implements RebuildableSearchProjectionPort {
    private static final String DATABASE_NAME = "faro-v5-search-projection";
    private static final String SCHEMA_TYPE = "FaroSearchProjectionDocument";
    private static final String PROPERTY_INDEX_TEXT = "indexText";
    private static final String PROPERTY_SOURCE_STAMP = "sourceStamp";

    private final Context appContext;
    private AppSearchSession session;
    private boolean closed;

    public AndroidAppSearchProjection(Context context) {
        this.appContext = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public synchronized void upsert(SearchProjectionDocument document)
            throws SearchProjectionException {
        Objects.requireNonNull(document, "document");
        GenericDocument genericDocument =
                new GenericDocument.Builder<>(
                                document.getKey().getNamespace(),
                                document.getKey().getDocumentId(),
                                SCHEMA_TYPE)
                        .setPropertyString(PROPERTY_INDEX_TEXT, document.getIndexText())
                        .setPropertyString(PROPERTY_SOURCE_STAMP, document.getSourceStamp())
                        .build();
        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder().addGenericDocuments(genericDocument).build();
        AppSearchBatchResult<String, Void> result =
                await(activeSession().putAsync(request), "upsert");
        if (!result.isSuccess()) {
            throw failed("upsert", result.getFailures().size());
        }
    }

    @Override
    public synchronized void remove(SearchProjectionKey key)
            throws SearchProjectionException {
        Objects.requireNonNull(key, "key");
        RemoveByDocumentIdRequest request =
                new RemoveByDocumentIdRequest.Builder(key.getNamespace())
                        .addIds(key.getDocumentId())
                        .build();
        AppSearchBatchResult<String, Void> result =
                await(activeSession().removeAsync(request), "remove");
        if (!result.isSuccess() && !onlyNotFound(result.getFailures())) {
            throw failed("remove", result.getFailures().size());
        }
    }

    @Override
    public synchronized void clear() throws SearchProjectionException {
        SearchSpec projectionOnly =
                new SearchSpec.Builder().addFilterSchemas(SCHEMA_TYPE).build();
        await(activeSession().removeAsync("", projectionOnly), "clear");
    }

    @Override
    public synchronized List<SearchProjectionHit> search(SearchProjectionQuery query)
            throws SearchProjectionException {
        Objects.requireNonNull(query, "query");
        SearchSpec spec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .setResultCountPerPage(query.getLimit())
                        .addFilterSchemas(SCHEMA_TYPE)
                        .addFilterNamespaces(query.getNamespace())
                        .build();

        SearchResults results = activeSession().search(query.getQueryText(), spec);
        try {
            List<SearchResult> page = await(results.getNextPageAsync(), "search");
            List<SearchProjectionHit> hits =
                    new ArrayList<>(Math.min(query.getLimit(), page.size()));
            for (SearchResult result : page) {
                if (hits.size() >= query.getLimit()) {
                    break;
                }
                GenericDocument document = result.getGenericDocument();
                String sourceStamp = document.getPropertyString(PROPERTY_SOURCE_STAMP);
                if (sourceStamp == null) {
                    throw new SearchProjectionException(
                            SearchProjectionException.Kind.OPERATION_FAILED,
                            "search projection document missing source stamp");
                }
                hits.add(
                        new SearchProjectionHit(
                                new SearchProjectionKey(
                                        document.getNamespace(),
                                        document.getId()),
                                sourceStamp,
                                result.getRankingSignal()));
            }
            return Collections.unmodifiableList(hits);
        } catch (IllegalArgumentException invalidProjectionDocument) {
            throw new SearchProjectionException(
                    SearchProjectionException.Kind.OPERATION_FAILED,
                    "search projection document failed validation",
                    invalidProjectionDocument);
        } finally {
            results.close();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private AppSearchSession activeSession() throws SearchProjectionException {
        if (closed) {
            throw new SearchProjectionException(
                    SearchProjectionException.Kind.CLOSED,
                    "search projection is closed");
        }
        if (session != null) {
            return session;
        }

        AppSearchSession created = null;
        try {
            created =
                    await(
                            LocalStorage.createSearchSessionAsync(
                                    new LocalStorage.SearchContext.Builder(
                                                    appContext,
                                                    DATABASE_NAME)
                                            .build()),
                            "open");
            created.setSchemaAsync(schemaRequest()).get();
            session = created;
            return session;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closeQuietly(created);
            throw new SearchProjectionException(
                    SearchProjectionException.Kind.UNAVAILABLE,
                    "search projection initialization interrupted",
                    interrupted);
        } catch (ExecutionException | RuntimeException initializationFailure) {
            closeQuietly(created);
            throw new SearchProjectionException(
                    SearchProjectionException.Kind.UNAVAILABLE,
                    "search projection initialization failed",
                    initializationFailure);
        }
    }

    private static SetSchemaRequest schemaRequest() {
        AppSearchSchema schema =
                new AppSearchSchema.Builder(SCHEMA_TYPE)
                        .addProperty(
                                new AppSearchSchema.StringPropertyConfig.Builder(
                                                PROPERTY_INDEX_TEXT)
                                        .setCardinality(
                                                AppSearchSchema.PropertyConfig
                                                        .CARDINALITY_REQUIRED)
                                        .setIndexingType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .INDEXING_TYPE_PREFIXES)
                                        .setTokenizerType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .TOKENIZER_TYPE_PLAIN)
                                        .build())
                        .addProperty(
                                new AppSearchSchema.StringPropertyConfig.Builder(
                                                PROPERTY_SOURCE_STAMP)
                                        .setCardinality(
                                                AppSearchSchema.PropertyConfig
                                                        .CARDINALITY_REQUIRED)
                                        .build())
                        .build();

        // The entire database is a disposable projection, so incompatible schema revisions may
        // safely wipe only this projection. Canonical owner/read sources remain untouched.
        return new SetSchemaRequest.Builder()
                .addSchemas(schema)
                .setForceOverride(true)
                .build();
    }

    private static boolean onlyNotFound(
            Map<String, AppSearchResult<Void>> failures) {
        if (failures.isEmpty()) {
            return true;
        }
        for (AppSearchResult<Void> failure : failures.values()) {
            if (failure.getResultCode() != AppSearchResult.RESULT_NOT_FOUND) {
                return false;
            }
        }
        return true;
    }

    private static SearchProjectionException failed(String operation, int failureCount) {
        return new SearchProjectionException(
                SearchProjectionException.Kind.OPERATION_FAILED,
                operation + " failed for " + failureCount + " projection item(s)");
    }

    private static <T> T await(Future<T> future, String operation)
            throws SearchProjectionException {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SearchProjectionException(
                    SearchProjectionException.Kind.UNAVAILABLE,
                    operation + " interrupted",
                    interrupted);
        } catch (ExecutionException failure) {
            throw new SearchProjectionException(
                    SearchProjectionException.Kind.OPERATION_FAILED,
                    operation + " failed",
                    failure.getCause());
        }
    }

    private static void closeQuietly(AppSearchSession created) {
        if (created != null) {
            created.close();
        }
    }
}
