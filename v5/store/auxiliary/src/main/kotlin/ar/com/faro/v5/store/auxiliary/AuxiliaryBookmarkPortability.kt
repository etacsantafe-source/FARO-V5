package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.kernel.contracts.*

/** Open, non-restorable metadata. Deliberately no token, digest, receipt or message text. */
data class PortableBookmarkRecord(
    val id: String,
    val generationId: String,
    val conversationId: String,
    val messageId: String,
    val folderId: String?,
    val favorite: Boolean,
    val revision: Long,
) {
    init {
        BookmarkId(id)
        BookmarkPolicy.requireOpaque(generationId, "portable generation")
        ConversationId(conversationId)
        ConversationMessageId(messageId)
        folderId?.let(::BookmarkFolderId)
        require(revision > 0)
    }
}

data class BookmarkPortabilitySnapshot(
    val schemaVersion: Int,
    val sourceWatermark: String,
    val folders: List<BookmarkFolder>,
    val records: List<PortableBookmarkRecord>,
) {
    init {
        require(schemaVersion >= 0 && sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        require(folders.size <= BookmarkPolicy.MAX_FOLDERS && records.size <= BookmarkPolicy.MAX_BOOKMARKS)
        require(folders == folders.sortedBy { it.id.value } && records == records.sortedBy { it.id })
        require(folders.map { it.id }.distinct().size == folders.size)
        require(records.map { it.id }.distinct().size == records.size)
        require(records.all { it.folderId == null || folders.any { f -> f.id.value == it.folderId } })
    }
}

fun interface BookmarkPortabilityPort {
    /** Missing/revoked authority throws; it is never a verified empty projection. */
    fun snapshot(): BookmarkPortabilitySnapshot
}

internal object BookmarkPortabilityProjection {
    fun from(schemaVersion: Int, authorized: BookmarkBackupSnapshot): BookmarkPortabilitySnapshot {
        val folders = authorized.folders.toList()
        val records = authorized.items.map {
            PortableBookmarkRecord(it.id.value, it.generationId, it.conversationId.value,
                it.messageId.value, it.folderId?.value, it.favorite, it.revision)
        }
        val h = CanonicalHashBuilder.domain("faro-bookmark-portability-v1")
            .string("schema", schemaVersion.toString()).string("folders", folders.size.toString())
            .string("records", records.size.toString())
        folders.forEachIndexed { i, f -> h.string("f.$i.id", f.id.value).string("f.$i.name", f.name)
            .string("f.$i.revision", f.revision.toString()) }
        records.forEachIndexed { i, b -> h.string("b.$i.id", b.id).string("b.$i.generation", b.generationId)
            .string("b.$i.chat", b.conversationId).string("b.$i.message", b.messageId)
            .string("b.$i.folder.present", (b.folderId != null).toString())
            .string("b.$i.folder", b.folderId ?: "").string("b.$i.favorite", b.favorite.toString())
            .string("b.$i.revision", b.revision.toString()) }
        return BookmarkPortabilitySnapshot(schemaVersion, h.sha256Hex(), folders, records)
    }
}
