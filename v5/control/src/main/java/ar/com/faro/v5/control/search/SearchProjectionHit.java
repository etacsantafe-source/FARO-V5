package ar.com.faro.v5.control.search;

/**
 * Locator returned by the rebuildable index.
 *
 * <p>Callers must re-read the current canonical owner/read projection before presenting or acting
 * on a hit; indexed text is deliberately not returned.
 */
public final class SearchProjectionHit {
    private final SearchProjectionKey key;
    private final String sourceStamp;
    private final double rankingSignal;

    public SearchProjectionHit(
            SearchProjectionKey key,
            String sourceStamp,
            double rankingSignal) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (!Double.isFinite(rankingSignal)) {
            throw new IllegalArgumentException("rankingSignal must be finite");
        }
        this.key = key;
        this.sourceStamp = SearchProjectionBounds.sourceStamp(sourceStamp);
        this.rankingSignal = rankingSignal;
    }

    public SearchProjectionKey getKey() {
        return key;
    }

    public String getSourceStamp() {
        return sourceStamp;
    }

    public double getRankingSignal() {
        return rankingSignal;
    }
}
