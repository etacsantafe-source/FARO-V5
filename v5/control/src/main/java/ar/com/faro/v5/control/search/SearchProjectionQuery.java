package ar.com.faro.v5.control.search;

/**
 * Fail-closed query against exactly one authorized projection namespace.
 *
 * <p>Federation, owner authorization and full N-018 query semantics remain outside this low-level
 * projection boundary.
 */
public final class SearchProjectionQuery {
    private final String namespace;
    private final String queryText;
    private final int limit;

    public SearchProjectionQuery(String namespace, String queryText, int limit) {
        this.namespace = SearchProjectionBounds.namespace(namespace);
        this.queryText = SearchProjectionBounds.queryText(queryText);
        this.limit = SearchProjectionBounds.resultLimit(limit);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getQueryText() {
        return queryText;
    }

    public int getLimit() {
        return limit;
    }
}
