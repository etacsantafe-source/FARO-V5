package ar.com.faro.v5.control.search;

import java.util.Objects;

/**
 * Fresh, owner-authorized material. Never construct this from an AppSearch hit:
 * an index is not a permission oracle or a source of truth.
 */
public final class SearchCanonicalDocument {
    public enum Kind { CONVERSATION, OBJECTIVE, MATTER, NOTICE, FINANCE_ENTRY, HISTORY_EVENT }
    private final SearchProjectionKey key;
    private final Kind kind;
    private final String title;
    private final String body;
    private final String sourceStamp;
    private final long asOfEpochMillis;

    public SearchCanonicalDocument(SearchProjectionKey key, Kind kind,
            String title, String body, String sourceStamp, long asOfEpochMillis) {
        this.key = Objects.requireNonNull(key, "key");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = SearchProjectionBounds.indexText(title);
        this.body = Objects.requireNonNull(body, "body");
        if (body.length() > SearchProjectionBounds.MAX_INDEX_TEXT_CHARS) {
            throw new IllegalArgumentException("canonical body exceeds search bound");
        }
        this.sourceStamp = SearchProjectionBounds.sourceStamp(sourceStamp);
        if (asOfEpochMillis < 0) throw new IllegalArgumentException("asOf must be nonnegative");
        this.asOfEpochMillis = asOfEpochMillis;
    }

    public SearchProjectionKey getKey() { return key; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getSourceStamp() { return sourceStamp; }
    public long getAsOfEpochMillis() { return asOfEpochMillis; }
}
