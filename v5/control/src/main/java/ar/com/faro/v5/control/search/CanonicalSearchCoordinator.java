package ar.com.faro.v5.control.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * S3 A2 source-of-truth query. AppSearch is ONLY an optional ranking hint.
 * Canonical owner authorization and content are re-read on EVERY request;
 * index errors degrade to bounded local search, never an "empty" assertion.
 * Owner generation/ACL/revision changes fail closed, including mid-request.
 */
public final class CanonicalSearchCoordinator {
    public static final int MAX_CANONICAL_DOCUMENTS = 500;

    public enum Coverage { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum MatchField { TITLE, BODY }

    public static final class Hit {
        private final SearchProjectionKey key;
        private final SearchCanonicalDocument.Kind kind;
        private final String title;
        private final String sourceStamp;
        private final long asOfEpochMillis;
        private final MatchField matchField;
        private final double indexRank;

        private Hit(SearchCanonicalDocument document, MatchField field, double indexRank) {
            this.key = document.getKey();
            this.kind = document.getKind();
            this.title = document.getTitle();
            this.sourceStamp = document.getSourceStamp();
            this.asOfEpochMillis = document.getAsOfEpochMillis();
            this.matchField = field;
            this.indexRank = indexRank;
        }

        public SearchProjectionKey getKey() { return key; }
        public SearchCanonicalDocument.Kind getKind() { return kind; }
        public String getTitle() { return title; }
        public String getSourceStamp() { return sourceStamp; }
        public long getAsOfEpochMillis() { return asOfEpochMillis; }
        public MatchField getMatchField() { return matchField; }
        private double getIndexRank() { return indexRank; }
    }

    public static final class Result {
        private final List<Hit> hits;
        private final Coverage coverage;
        private final boolean indexDegraded;

        private Result(List<Hit> hits, Coverage coverage, boolean indexDegraded) {
            this.hits = List.copyOf(hits);
            this.coverage = coverage;
            this.indexDegraded = indexDegraded;
        }
        public List<Hit> getHits() { return hits; }
        public Coverage getCoverage() { return coverage; }
        public boolean isIndexDegraded() { return indexDegraded; }
    }

    private final SearchCanonicalReadPort owner;
    private final RebuildableSearchProjectionPort optionalIndex;

    public CanonicalSearchCoordinator(SearchCanonicalReadPort owner,
            RebuildableSearchProjectionPort optionalIndex) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.optionalIndex = optionalIndex;
    }

    public Result search(SearchProjectionQuery query) {
        Objects.requireNonNull(query, "query");
        try {
            SearchAuthorityStamp before = owner.authorityStamp();
            if (before == null) return unavailable();

            SearchCanonicalSnapshot snapshot =
                    owner.readAuthorized(query.getNamespace(), MAX_CANONICAL_DOCUMENTS);
            if (snapshot == null || !before.equals(snapshot.getAuthority())
                    || snapshot.getDocuments().size() > MAX_CANONICAL_DOCUMENTS) {
                return unavailable();
            }

            // The disposable index can *never* contribute content or authorization.
            Map<String, SearchProjectionHit> validHints = new HashMap<>();
            boolean indexDegraded = optionalIndex == null;
            if (optionalIndex != null) {
                try {
                    for (SearchProjectionHit hit : optionalIndex.search(query)) {
                        if (!hit.getKey().getNamespace().equals(query.getNamespace())) continue;
                        validHints.putIfAbsent(id(hit.getKey()), hit);
                    }
                } catch (SearchProjectionException | RuntimeException projectionFailure) {
                    indexDegraded = true;
                }
            }

            String normalizedQuery = normalized(query.getQueryText());
            if (normalizedQuery.isEmpty()) return unavailable();
            List<Hit> matches = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (SearchCanonicalDocument document : snapshot.getDocuments()) {
                if (document == null
                        || !document.getKey().getNamespace().equals(query.getNamespace())
                        || !seen.add(id(document.getKey()))) {
                    return unavailable();
                }
                MatchField match = null;
                if (normalized(document.getTitle()).contains(normalizedQuery)) {
                    match = MatchField.TITLE;
                } else if (normalized(document.getBody()).contains(normalizedQuery)) {
                    match = MatchField.BODY;
                }
                if (match == null) continue;
                SearchProjectionHit hint = validHints.get(id(document.getKey()));
                double signal = hint != null && hint.getSourceStamp().equals(document.getSourceStamp())
                        ? hint.getRankingSignal() : 0.0d;
                matches.add(new Hit(document, match, signal));
            }
            // The second check also detects generation switches or ACL revocations
            // during index lookup. Never leak previously observed canonical titles.
            if (!before.equals(owner.authorityStamp())) return unavailable();

            matches.sort(Comparator
                    .comparing((Hit hit) -> hit.getMatchField() != MatchField.TITLE)
                    .thenComparing(Comparator.comparingDouble(Hit::getIndexRank).reversed())
                    .thenComparing(Comparator.comparingLong(Hit::getAsOfEpochMillis).reversed())
                    .thenComparing(hit -> hit.getKey().getNamespace())
                    .thenComparing(hit -> hit.getKey().getDocumentId()));
            return new Result(
                    matches.subList(0, Math.min(query.getLimit(), matches.size())),
                    snapshot.isComplete() ? Coverage.COMPLETE : Coverage.PARTIAL,
                    indexDegraded);
        } catch (RuntimeException ownerFailure) {
            return unavailable();
        }
    }

    private static Result unavailable() {
        return new Result(List.of(), Coverage.UNAVAILABLE, true);
    }
    private static String id(SearchProjectionKey key) {
        return key.getNamespace() + "\u0000" + key.getDocumentId();
    }
    private static String normalized(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
}
