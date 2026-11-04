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
        private final SearchCanonicalReadPort authority;
        private final CanonicalSearchCoordinator coordinator;

        public Source(String namespace, SearchCanonicalReadPort owner,
                RebuildableSearchProjectionPort index) {
            this.namespace = SearchProjectionBounds.namespace(namespace);
            this.authority = Objects.requireNonNull(owner, "owner");
            this.coordinator = new CanonicalSearchCoordinator(owner, index);
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
        List<SearchAuthorityStamp> acceptedAuthority = new ArrayList<>();
        boolean degraded = false;

        for (Source source : sources) {
            SearchAuthorityStamp stamp = safeStamp(source.authority);
            CanonicalSearchCoordinator.Result response;
            try {
                response = stamp == null ? null
                        : source.coordinator.search(new SearchProjectionQuery(
                                source.namespace, query, SearchProjectionBounds.MAX_RESULTS));
            } catch (RuntimeException failure) {
                // An owner-specific failure must not erase other authorized hits.
                response = null;
            }
            // The owner may revoke access between local coordinator exit and
            // the next owner. A changed authority NEVER contributes content.
            if (stamp == null || !stamp.equals(safeStamp(source.authority))) {
                response = null;
            }

            CanonicalSearchCoordinator.Coverage level =
                    response == null ? CanonicalSearchCoordinator.Coverage.UNAVAILABLE
                            : response.getCoverage();
            coverage.put(source.namespace, level);
            acceptedAuthority.add(level == CanonicalSearchCoordinator.Coverage.UNAVAILABLE
                    ? null : stamp);
            if (level == CanonicalSearchCoordinator.Coverage.UNAVAILABLE) {
                perSource.add(List.of());
                degraded = true;
            } else {
                degraded |= response.isIndexDegraded();
                // CanonicalSearchCoordinator guarantees only owner-authorized hits.
                perSource.add(response.getHits());
            }
        }

        // A later source can revoke an EARLIER source. Revalidate every
        // original full stamp after the entire federation has finished.
        for (int i = 0; i < sources.size(); i++) {
            SearchAuthorityStamp stamp = acceptedAuthority.get(i);
            if (stamp != null && !stamp.equals(safeStamp(sources.get(i).authority))) {
                coverage.put(sources.get(i).namespace,
                        CanonicalSearchCoordinator.Coverage.UNAVAILABLE);
                perSource.set(i, List.of());
                degraded = true;
            }
        }
        long readable = coverage.values().stream()
                .filter(level -> level != CanonicalSearchCoordinator.Coverage.UNAVAILABLE)
                .count();

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

    private static SearchAuthorityStamp safeStamp(SearchCanonicalReadPort owner) {
        try {
            return owner.authorityStamp();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
