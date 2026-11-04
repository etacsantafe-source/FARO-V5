package ar.com.faro.v5.control.search;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FederatedCanonicalSearchCoordinatorTest {
    private static final String WORKSPACE = "workspace.conversations";
    private static final String FINANCE = "finance.entries";

    private static SearchCanonicalDocument doc(String ns, String id, String title, long time) {
        return new SearchCanonicalDocument(
                new SearchProjectionKey(ns, id),
                ns.equals(WORKSPACE) ? SearchCanonicalDocument.Kind.CONVERSATION
                        : SearchCanonicalDocument.Kind.FINANCE_ENTRY,
                title, "", "revision-1", time);
    }

    private static FederatedCanonicalSearchCoordinator.Source source(String ns,
            boolean authorized, boolean complete, SearchCanonicalDocument... docs) {
        SearchAuthorityStamp stamp = new SearchAuthorityStamp("g1", "acl-1", 1L);
        SearchCanonicalReadPort port = new SearchCanonicalReadPort() {
            @Override public SearchAuthorityStamp authorityStamp() {
                return authorized ? stamp : null;
            }
            @Override public SearchCanonicalSnapshot readAuthorized(String namespace, int max) {
                assertEquals(ns, namespace);
                return new SearchCanonicalSnapshot(stamp, List.of(docs), complete);
            }
        };
        return new FederatedCanonicalSearchCoordinator.Source(
                ns, new CanonicalSearchCoordinator(port, null));
    }

    @Test void roundRobinPreservesFinanceEvenWhenWorkspaceHasManyMatches() {
        var coordinator = new FederatedCanonicalSearchCoordinator(List.of(
                source(WORKSPACE, true, true,
                        doc(WORKSPACE, "a", "pago enero", 300),
                        doc(WORKSPACE, "b", "pago febrero", 200),
                        doc(WORKSPACE, "c", "pago marzo", 100)),
                source(FINANCE, true, true,
                        doc(FINANCE, "entry-1", "pago alquiler", 50))));
        var result = coordinator.search("pago", 2);
        assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.getCoverage());
        assertEquals(List.of(WORKSPACE, FINANCE),
                result.getHits().stream().map(h -> h.getKey().getNamespace()).toList());
        assertEquals(2, result.getCoverageBySource().size());
    }

    @Test void oneUnavailableOwnerDoesNotEraseOtherResultsOrClaimGlobalAbsence() {
        var coordinator = new FederatedCanonicalSearchCoordinator(List.of(
                source(WORKSPACE, true, true),
                source(FINANCE, false, true, doc(FINANCE, "secret", "privado", 50))));
        var result = coordinator.search("privado", 5);
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.getCoverage());
        assertTrue(result.getHits().isEmpty());
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
                result.getCoverageBySource().get(FINANCE));
        assertTrue(result.isIndexDegraded());
    }

    @Test void allUnavailableIsNotAZeroResult() {
        var coordinator = new FederatedCanonicalSearchCoordinator(List.of(
                source(WORKSPACE, false, true),
                source(FINANCE, false, true)));
        var result = coordinator.search("algo", 5);
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE, result.getCoverage());
        assertEquals(0, result.getHits().size());
    }

    @Test void truncatedOwnerForcesPartialDespiteOtherCompleteOwner() {
        var coordinator = new FederatedCanonicalSearchCoordinator(List.of(
                source(WORKSPACE, true, false, doc(WORKSPACE, "one", "pago", 20)),
                source(FINANCE, true, true, doc(FINANCE, "two", "pago", 10))));
        var result = coordinator.search("pago", 10);
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.getCoverage());
        assertEquals(2, result.getHits().size());
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL,
                result.getCoverageBySource().get(WORKSPACE));
    }

    @Test void rejectsDuplicateUnregisteredOrOversizedSourceSets() {
        var one = source(WORKSPACE, true, true);
        assertThrows(IllegalArgumentException.class,
                () -> new FederatedCanonicalSearchCoordinator(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new FederatedCanonicalSearchCoordinator(List.of(one, one)));
        var coordinator = new FederatedCanonicalSearchCoordinator(List.of(one));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.search("pago", 51));
    }
}
