package ar.com.faro.v5.control.search;

import java.util.Objects;

/** The authoritative generation + ACL epoch + owner revision of a canonical read. */
public final class SearchAuthorityStamp {
    private final String generationId;
    private final String aclEpoch;
    private final long ownerRevision;

    public SearchAuthorityStamp(String generationId, String aclEpoch, long ownerRevision) {
        this.generationId = SearchProjectionBounds.sourceStamp(generationId);
        this.aclEpoch = SearchProjectionBounds.sourceStamp(aclEpoch);
        if (ownerRevision < 0) throw new IllegalArgumentException("owner revision must be nonnegative");
        this.ownerRevision = ownerRevision;
    }

    public String getGenerationId() { return generationId; }
    public String getAclEpoch() { return aclEpoch; }
    public long getOwnerRevision() { return ownerRevision; }

    @Override public boolean equals(Object value) {
        if (!(value instanceof SearchAuthorityStamp other)) return false;
        return generationId.equals(other.generationId)
                && aclEpoch.equals(other.aclEpoch)
                && ownerRevision == other.ownerRevision;
    }

    @Override public int hashCode() {
        return Objects.hash(generationId, aclEpoch, ownerRevision);
    }
}
