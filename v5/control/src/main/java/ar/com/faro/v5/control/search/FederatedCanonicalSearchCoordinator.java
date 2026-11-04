package ar.com.faro.v5.control.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * S3.A2 multi-owner search. A registered source MUST wrap a real authorized
 * canonical owner; the aggregator itself has no store, ACL or index authority.
 * A partially accessible search never claims that no matching data exists.
 *
 * V4.4 multi-domain parity: bounded round-robin protects rare domains from
 * starvation without comparing unrelated index scores or inventing matches.
 */
public final class FederatedCanonicalSearchCoordinator {
    public static final int MAX_SOURCES = 8;

    public static final class Source {
        private final String namespace;
        private final CanonicalSearchCoordinator coordinator;

        public Source(String namespace, CanonicalSearchCoordinator coordinator) {
            this.namespace = SearchProjectionBounds.namespace(namespace);
            this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        }
        public String getNamespace() { return namespace; }
    }

    public static final class Result {
        private final List<CanonicalSearchCoordinator.Hit> hits;
        private final Map<String, CanonicalSearchCoordinator.Coverage> coverageBySource;
        private final CanonicalSearchCoordinator.Coverage coverage;
        private final boolean indexDegraded;

        private Result(List<CanonicalSearchCoordinator.Hit> hits,
                Map<String, CanonicalSearchCoordinator.Coverage> coverageBySource,
                CanonicalSearchCoordinator.Coverage coverage,
                boolean indexDegraded) {
            this.hits = List.copyOf(hits);
            this.coverageBySource =
                    Collections.unmodifiableMap(new LinkedHashMap<>(coverageBySource));
            this.coverage = coverage;
            this.indexDegraded = indexDegraded;
        }

        public List<CanonicalSearchCoordinator.Hit> getHits() { return hits; }
        public Map<String, CanonicalSearchCoordinator.Coverage> getCoverageBySource() {
            return coverageBySource;
        }
        public CanonicalSearchCoordinator.Coverage getCoverage() { return coverage; }
        public boolean isIndexDegraded() { return indexDegraded; }
    }

    private final List<Source> sources;

    public FederatedCanonicalSearchCoordinator(List<Source> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty() || sources.size() > MAX_SOURCES)
            throw new IllegalArgumentException("federated sources must be 1..8");
        Set<String> names = new HashSet<>();
        for (Source source : sources) {
            Objects.requireNonNull(source, "source");
            if (!names.add(source.namespace))
                throw new IllegalArgumentException("duplicate canonical namespace");
        }
        this.sources = List.copyOf(sources);
    }

    public Result search(String query, int limit) {
        if (limit < 1 || limit > SearchProjectionBounds.MAX_RESULTS)
            throw new IllegalArgumentException("result limit must be 1..50");

        Map<String, CanonicalSearchCoordinator.Coverage> coverage = new LinkedHashMap<>();
        List<List<CanonicalSearchCoordinator.Hit>> perSource = new ArrayList<>();
        boolean degraded = false;
        int readable = 0;

        for (Source source : sources) {
            CanonicalSearchCoordinator.Result response;
            try {
                response = source.coordinator.search(new SearchProjectionQuery(
                        source.namespace, query, SearchProjectionBounds.MAX_RESULTS));
            } catch (RuntimeException failure) {
                // An owner-specific failure must not erase other authorized hits.
                response = null;
            }

            CanonicalSearchCoordinator.Coverage level =
                    response == null ? CanonicalSearchCoordinator.Coverage.UNAVAILABLE
                            : response.getCoverage();
            coverage.put(source.namespace, level);
            if (level == CanonicalSearchCoordinator.Coverage.UNAVAILABLE) {
                perSource.add(List.of());
                degraded = true;
            } else {
                readable++;
                degraded |= response.isIndexDegraded();
                // CanonicalSearchCoordinator guarantees only owner-authorized hits.
                perSource.add(response.getHits());
            }
        }

        CanonicalSearchCoordinator.Coverage overall;
        if (readable == 0) {
            overall = CanonicalSearchCoordinator.Coverage.UNAVAILABLE;
        } else if (coverage.values().stream().allMatch(
                level -> level == CanonicalSearchCoordinator.Coverage.COMPLETE)) {
            overall = CanonicalSearchCoordinator.Coverage.COMPLETE;
        } else {
            overall = CanonicalSearchCoordinator.Coverage.PARTIAL;
        }

        List<CanonicalSearchCoordinator.Hit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int round = 0; hits.size() < limit; round++) {
            boolean found = false;
            for (List<CanonicalSearchCoordinator.Hit> rows : perSource) {
                if (round >= rows.size()) continue;
                found = true;
                CanonicalSearchCoordinator.Hit hit = rows.get(round);
                String key = hit.getKey().getNamespace() + "\u0000" +
                        hit.getKey().getDocumentId();
                if (seen.add(key)) hits.add(hit);
                if (hits.size() == limit) break;
            }
            if (!found) break;
        }
        return new Result(hits, coverage, overall, degraded);
    }
}
