package ar.com.faro.v5.workspace

/** Canonical owner facts only. Not a user grant and never carries message text/title. */
data class WorkspaceBookmarkReadAdmission(
    val authorityToken: String,
    val ownerRevision: Long,
    val targetAvailable: Boolean?,
) {
    init {
        require(authorityToken.isNotBlank() && authorityToken.length <= 160)
        require(ownerRevision >= 0)
    }
    override fun toString(): String =
        "WorkspaceBookmarkReadAdmission(revision=$ownerRevision, targetAvailable=$targetAvailable, authority=REDACTED)"
}
