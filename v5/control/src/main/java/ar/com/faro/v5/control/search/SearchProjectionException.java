package ar.com.faro.v5.control.search;

/** Typed failure of an optional rebuildable search projection. */
public final class SearchProjectionException extends Exception {
    public enum Kind {
        UNAVAILABLE,
        OPERATION_FAILED,
        CLOSED
    }

    private final Kind kind;

    public SearchProjectionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public SearchProjectionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
