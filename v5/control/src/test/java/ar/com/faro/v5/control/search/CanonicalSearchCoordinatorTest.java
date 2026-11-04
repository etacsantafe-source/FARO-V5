package ar.com.faro.v5.control.search;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CanonicalSearchCoordinatorTest {
    private static final String SPACE = "workspace.conversations";
    private static final SearchAuthorityStamp A1 = new SearchAuthorityStamp("g1", "acl-3", 4L);
    private static final SearchAuthorityStamp A2 = new SearchAuthorityStamp("g2", "acl-4", 5L);

    private static SearchCanonicalDocument doc(String id, String title, String body,
            String stamp, long asOf) {
        return new SearchCanonicalDocument(
                new SearchProjectionKey(SPACE, id),
                SearchCanonicalDocument.Kind.CONVERSATION, title, body, stamp, asOf);
    }

    private static class Source implements SearchCanonicalReadPort {
        SearchAuthorityStamp current = A1;
        SearchCanonicalSnapshot snapshot;
        int calls;
        boolean changeAfterRead;
        Source(boolean complete, SearchCanonicalDocument... records) {
            snapshot = new SearchCanonicalSnapshot(A1, List.of(records), complete);
        }
        @Override public SearchAuthorityStamp authorityStamp() {
            calls++;
            if (changeAfterRead && calls > 1) return A2;
            return current;
        }
        @Override public SearchCanonicalSnapshot readAuthorized(String space, int maximum) {
            assertEquals(SPACE, space);
            assertEquals(500, maximum);
            return snapshot;
        }
    }

    private static RebuildableSearchProjectionPort brokenIndex() {
        return new RebuildableSearchProjectionPort() {
            @Override public void upsert(SearchProjectionDocument d) {}
            @Override public void remove(SearchProjectionKey k) {}
            @Override public void clear() {}
            @Override public List<SearchProjectionHit> search(SearchProjectionQuery q)
                    throws SearchProjectionException {
                throw new SearchProjectionException(SearchProjectionException.Kind.UNAVAILABLE, "offline");
            }
            @Override public void close() {}
        };
    }

    @Test void accentsAndTitleBeforeBodyAreDeterministic() {
        Source source = new Source(true,
                doc("b", "Comidas", "Café de la mañana", "rev1", 900L),
                doc("z", "Cafe", "", "rev1", 100L),
                doc("a", "CAFÉ", "", "rev1", 100L));
        var result = new CanonicalSearchCoordinator(source, null)
                .search(new SearchProjectionQuery(SPACE, "cafe", 5));
        assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.getCoverage());
        assertTrue(result.isIndexDegraded());
        assertEquals(List.of("a", "z", "b"),
                result.getHits().stream().map(h -> h.getKey().getDocumentId()).toList());
        assertEquals(CanonicalSearchCoordinator.MatchField.TITLE, result.getHits().get(0).getMatchField());
    }

    @Test void staleAndUnauthorizedIndexHitsNeverLeak() {
        Source source = new Source(true, doc("live", "Caja", "Banco", "rev2", 5));
        RebuildableSearchProjectionPort malicious = new RebuildableSearchProjectionPort() {
            @Override public void upsert(SearchProjectionDocument d) {}
            @Override public void remove(SearchProjectionKey k) {}
            @Override public void clear() {}
            @Override public void close() {}
            @Override public List<SearchProjectionHit> search(SearchProjectionQuery q) {
                return List.of(
                        new SearchProjectionHit(new SearchProjectionKey(SPACE, "deleted"), "rev10", 500),
                        new SearchProjectionHit(new SearchProjectionKey(SPACE, "live"), "rev1", 999));
            }
        };
        var result = new CanonicalSearchCoordinator(source, malicious)
                .search(new SearchProjectionQuery(SPACE, "caja", 50));
        assertEquals(1, result.getHits().size());
        assertEquals("Caja", result.getHits().get(0).getTitle());
        assertEquals("live", result.getHits().get(0).getKey().getDocumentId());
        assertFalse(result.isIndexDegraded());
    }

    @Test void indexFailureDegradesToCompleteAuthorizedLocalSearch() {
        Source source = new Source(true, doc("real", "Gestión del mes", "", "rev1", 88));
        var result = new CanonicalSearchCoordinator(source, brokenIndex())
                .search(new SearchProjectionQuery(SPACE, "gestion", 10));
        assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.getCoverage());
        assertTrue(result.isIndexDegraded());
        assertEquals("real", result.getHits().get(0).getKey().getDocumentId());
    }

    @Test void partialOwnerCoverageCannotClaimNoResults() {
        Source source = new Source(false, doc("a", "No coincide", "", "r", 3));
        var result = new CanonicalSearchCoordinator(source, null)
                .search(new SearchProjectionQuery(SPACE, "ausente", 10));
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.getCoverage());
        assertTrue(result.getHits().isEmpty());
    }

    @Test void generationOrAclChangeDuringReadFailsClosed() {
        Source source = new Source(true, doc("sensitive", "Privado", "", "r", 99));
        source.changeAfterRead = true;
        var result = new CanonicalSearchCoordinator(source, null)
                .search(new SearchProjectionQuery(SPACE, "privado", 10));
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE, result.getCoverage());
        assertTrue(result.getHits().isEmpty());
    }

    @Test void unavailableOrInvalidOwnerFailsClosed() {
        Source absent = new Source(true);
        absent.current = null;
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
                new CanonicalSearchCoordinator(absent, null)
                .search(new SearchProjectionQuery(SPACE, "algo", 5)).getCoverage());

        Source duplicate = new Source(true,
                doc("same", "Uno", "", "1", 10), doc("same", "Dos", "", "1", 20));
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
                new CanonicalSearchCoordinator(duplicate, null)
                .search(new SearchProjectionQuery(SPACE, "uno", 5)).getCoverage());
    }
}
