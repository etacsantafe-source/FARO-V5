package ar.com.faro.v5.control.search;

import java.util.List;
import java.util.Objects;

/** The canonical owner MUST mark truncated/partial enumerations as incomplete. */
public final class SearchCanonicalSnapshot {
    private final SearchAuthorityStamp authority;
    private final List<SearchCanonicalDocument> documents;
    private final boolean complete;

    public SearchCanonicalSnapshot(SearchAuthorityStamp authority,
            List<SearchCanonicalDocument> documents, boolean complete) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        this.complete = complete;
    }

    public SearchAuthorityStamp getAuthority() { return authority; }
    public List<SearchCanonicalDocument> getDocuments() { return documents; }
    public boolean isComplete() { return complete; }
}
