package ar.com.faro.v5.kernel.contracts

/** S3.A3 typed Guardados contracts only. These IDs are not registered in D-229 yet. */
object BookmarkOperationIds {
    val read = OperationId("search.bookmark.read")
    val add = OperationId("search.bookmark.add")
    val lifecycleSet = OperationId("search.bookmark.lifecycle.set")
    val favoriteSet = OperationId("search.bookmark.favorite.set")
    val folderCreate = OperationId("search.bookmark.folder.create")
    val folderRename = OperationId("search.bookmark.folder.rename")
    val folderDelete = OperationId("search.bookmark.folder.delete")
    val folderAssign = OperationId("search.bookmark.folder.assign")

    val proposedProfiles: Map<OperationId, ExecutionProfile> =
        linkedMapOf(
            read to ExecutionProfile.READ_ONLY,
            add to ExecutionProfile.AUXILIARY_MUTATION,
            lifecycleSet to ExecutionProfile.AUXILIARY_MUTATION,
            favoriteSet to ExecutionProfile.AUXILIARY_MUTATION,
            folderCreate to ExecutionProfile.AUXILIARY_MUTATION,
            folderRename to ExecutionProfile.AUXILIARY_MUTATION,
            folderDelete to ExecutionProfile.AUXILIARY_MUTATION,
            folderAssign to ExecutionProfile.AUXILIARY_MUTATION,
        )
}

object BookmarkPolicy {
    const val MAX_FOLDERS = 32
    const val MAX_BOOKMARKS = 1024
    const val MAX_FOLDER_NAME = 80
    const val MAX_READ_PAGE = 50

    fun requireOpaque(value: String, what: String, maxLength: Int = 128) {
        require(value.isNotBlank() && value.length <= maxLength &&
            value.none { it.code < 32 || it.code == 127 }) { "invalid $what" }
    }

    fun requireFolderName(name: String) {
        require(name.isNotBlank() && name == name.trim() &&
            name.length <= MAX_FOLDER_NAME &&
            name.none { it.code < 32 || it.code == 127 }) {
            "invalid bookmark folder name"
        }
    }

    fun requireScope(generationId: String, authorityToken: String) {
        requireOpaque(generationId, "bookmark generation")
        requireOpaque(authorityToken, "bookmark authority", 160)
    }
}

@JvmInline
value class BookmarkId(val value: String) {
    init { BookmarkPolicy.requireOpaque(value, "bookmark id") }
}

@JvmInline
value class BookmarkFolderId(val value: String) {
    init { BookmarkPolicy.requireOpaque(value, "bookmark folder id") }
}

/**
 * Metadata points to the Workspace owner N-010; it NEVER contains message text.
 * Persistence and every dereference must revalidate generation, token, ACL,
 * archive/lifecycle state and current canonical message in that owner.
 */
data class BookmarkTarget(
    val conversationId: ConversationId,
    val messageId: ConversationMessageId,
    val generationId: String,
    val authorityToken: String,
) {
    init { BookmarkPolicy.requireScope(generationId, authorityToken) }

    override fun toString(): String =
        "BookmarkTarget(conversationId=$conversationId, messageId=$messageId, scope=REDACTED)"
}

enum class BookmarkLifecycle { ACTIVE, REMOVED }

data class BookmarkMetadata(
    val id: BookmarkId,
    val target: BookmarkTarget,
    val folderId: BookmarkFolderId? = null,
    val favorite: Boolean = false,
    val lifecycle: BookmarkLifecycle = BookmarkLifecycle.ACTIVE,
    val revision: Long = 1,
) {
    init { require(revision > 0) }
}

data class BookmarkFolder(
    val id: BookmarkFolderId,
    val name: String,
    val revision: Long = 1,
) {
    init {
        BookmarkPolicy.requireFolderName(name)
        require(revision > 0)
    }
}

/** A page is metadata only; an empty authorized page is not owner-unavailable. */
data class BookmarkReadRequest(
    val generationId: String,
    val authorityToken: String,
    val cursor: BookmarkId? = null,
    val limit: Int = BookmarkPolicy.MAX_READ_PAGE,
) {
    init {
        BookmarkPolicy.requireScope(generationId, authorityToken)
        require(limit in 1..BookmarkPolicy.MAX_READ_PAGE)
    }

    override fun toString(): String = "BookmarkReadRequest(scope=REDACTED, limit=$limit)"
}

data class BookmarkReadPage(
    val bookmarks: List<BookmarkMetadata>,
    val folders: List<BookmarkFolder>,
    val hasMore: Boolean,
) {
    init {
        require(bookmarks.size <= BookmarkPolicy.MAX_READ_PAGE)
        require(folders.size <= BookmarkPolicy.MAX_FOLDERS)
        require(bookmarks.map { it.id }.distinct().size == bookmarks.size)
        require(folders.map { it.id }.distinct().size == folders.size)
    }
}

sealed interface BookmarkReadResult {
    data class Available(val page: BookmarkReadPage) : BookmarkReadResult
    data object Stale : BookmarkReadResult
    data object Unavailable : BookmarkReadResult
}

/** Seven auxiliary mutations. No Reality receipt or message deletion is implied. */
sealed interface BookmarkMutation {
    data class Add(val bookmark: BookmarkMetadata) : BookmarkMutation {
        init { require(bookmark.lifecycle == BookmarkLifecycle.ACTIVE) }
    }
    data class LifecycleSet(val id: BookmarkId, val lifecycle: BookmarkLifecycle) : BookmarkMutation
    data class FavoriteSet(val id: BookmarkId, val favorite: Boolean) : BookmarkMutation
    data class FolderCreate(val folder: BookmarkFolder) : BookmarkMutation
    data class FolderRename(val id: BookmarkFolderId, val name: String) : BookmarkMutation {
        init { BookmarkPolicy.requireFolderName(name) }
    }
    /** The Auxiliary owner must reject this if the folder is non-empty. */
    data class FolderDelete(val id: BookmarkFolderId) : BookmarkMutation
    /** Null folder means an explicit move to the root, not a cascading delete. */
    data class FolderAssign(val id: BookmarkId, val folderId: BookmarkFolderId?) : BookmarkMutation
}

data class BookmarkMutationRequest(
    val generationId: String,
    val authorityToken: String,
    val expectedRevision: Long,
    val idempotencyKey: IdempotencyKey,
    val mutation: BookmarkMutation,
) {
    init {
        BookmarkPolicy.requireScope(generationId, authorityToken)
        require(expectedRevision >= 0)
        BookmarkPolicy.requireOpaque(idempotencyKey.value, "bookmark idempotency key")
        if (mutation is BookmarkMutation.Add) {
            require(mutation.bookmark.target.generationId == generationId &&
                mutation.bookmark.target.authorityToken == authorityToken) {
                "bookmark target must match current write authority"
            }
        }
    }

    override fun toString(): String =
        "BookmarkMutationRequest(scope=REDACTED, expectedRevision=$expectedRevision)"
}

enum class BookmarkRejection {
    TARGET_UNAVAILABLE, NOT_FOUND, ALREADY_EXISTS, FOLDER_NOT_EMPTY,
    QUOTA_EXCEEDED, INVALID_NAME
}

sealed interface BookmarkMutationResult {
    data class Saved(val revision: Long, val replayed: Boolean) : BookmarkMutationResult {
        init { require(revision > 0) }
    }
    data class Stale(val currentRevision: Long) : BookmarkMutationResult {
        init { require(currentRevision >= 0) }
    }
    data object Unavailable : BookmarkMutationResult
    data object IdentityConflict : BookmarkMutationResult
    data class Rejected(val reason: BookmarkRejection) : BookmarkMutationResult
}

/** Future N-018 owner implementation must fail closed on generation/ACL loss. */
fun interface BookmarkReadPort {
    suspend fun read(request: BookmarkReadRequest): BookmarkReadResult
}

/** Writes will commit metadata and auxiliary receipt atomically, never via Reality. */
fun interface BookmarkMutationPort {
    suspend fun mutate(request: BookmarkMutationRequest): BookmarkMutationResult
}
