package ar.com.faro.v5.control.search;

/** Stable opaque identity of one rebuildable search projection document. */
public final class SearchProjectionKey {
    private final String namespace;
    private final String documentId;

    public SearchProjectionKey(String namespace, String documentId) {
        this.namespace = SearchProjectionBounds.namespace(namespace);
        this.documentId = SearchProjectionBounds.documentId(documentId);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getDocumentId() {
        return documentId;
    }
}
