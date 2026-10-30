package ar.com.faro.v5.control.search;

import java.util.List;

/**
 * Optional low-level index projection.
 *
 * <p>All methods are blocking and must be called off the Android main thread by the outer runtime.
 * The projection can be deleted at any time and rebuilt exclusively from canonical read sources.
 */
public interface RebuildableSearchProjectionPort extends AutoCloseable {
    void upsert(SearchProjectionDocument document) throws SearchProjectionException;

    void remove(SearchProjectionKey key) throws SearchProjectionException;

    void clear() throws SearchProjectionException;

    List<SearchProjectionHit> search(SearchProjectionQuery query) throws SearchProjectionException;

    @Override
    void close();
}
