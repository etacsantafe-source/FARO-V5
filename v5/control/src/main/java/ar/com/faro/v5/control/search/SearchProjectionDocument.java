package ar.com.faro.v5.control.search;

/**
 * Bounded material handed to a rebuildable search index.
 *
 * <p>This object is not Domain Truth and is intentionally unsuitable for backup or owner recovery.
 */
public final class SearchProjectionDocument {
    private final SearchProjectionKey key;
    private final String indexText;
    private final String sourceStamp;

    public SearchProjectionDocument(
            SearchProjectionKey key,
            String indexText,
            String sourceStamp) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        this.key = key;
        this.indexText = SearchProjectionBounds.indexText(indexText);
        this.sourceStamp = SearchProjectionBounds.sourceStamp(sourceStamp);
    }

    public SearchProjectionKey getKey() {
        return key;
    }

    public String getIndexText() {
        return indexText;
    }

    public String getSourceStamp() {
        return sourceStamp;
    }
}
