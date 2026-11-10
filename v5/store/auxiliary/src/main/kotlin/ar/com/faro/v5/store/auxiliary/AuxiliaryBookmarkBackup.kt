package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.SQLiteConnection
import ar.com.faro.v5.kernel.contracts.*

/** Retained identity is data, never a grant of Workspace read authority. No clear token/text. */
data class BookmarkBackupScope(val generationId: String, val authorityDigest: String, val revision: Long) {
    init {
        BookmarkPolicy.requireOpaque(generationId, "backup generation")
        require(authorityDigest.matches(Regex("[0-9a-f]{64}")))
        require(revision >= 0)
    }
}

data class BookmarkBackupItem(
    val id: BookmarkId,
    val generationId: String,
    val conversationId: ConversationId,
    val messageId: ConversationMessageId,
    val folderId: BookmarkFolderId?,
    val favorite: Boolean,
    val lifecycle: BookmarkLifecycle,
    val revision: Long,
) {
    init {
        BookmarkPolicy.requireOpaque(generationId, "backup item generation")
        require(revision > 0)
    }
}

data class BookmarkBackupReceipt(val key: String, val requestHash: String, val resultRevision: Long) {
    init {
        BookmarkPolicy.requireOpaque(key, "backup receipt key")
        require(requestHash.matches(Regex("[0-9a-f]{64}")))
        require(resultRevision > 0)
    }
}

data class BookmarkBackupSnapshot(
    val scope: BookmarkBackupScope?,
    val folders: List<BookmarkFolder>,
    val items: List<BookmarkBackupItem>,
    val receipts: List<BookmarkBackupReceipt>,
) {
    init { validate() }

    fun validate() {
        require(folders.size <= BookmarkPolicy.MAX_FOLDERS && items.size <= BookmarkPolicy.MAX_BOOKMARKS)
        require(receipts.size <= MAX_RECEIPTS)
        require(folders == folders.sortedBy { it.id.value })
        require(items == items.sortedBy { it.id.value })
        require(receipts == receipts.sortedBy { it.key })
        require(folders.map { it.id }.distinct().size == folders.size)
        require(items.map { it.id }.distinct().size == items.size)
        require(items.map { it.generationId to (it.conversationId to it.messageId) }.distinct().size == items.size)
        require(receipts.map { it.key }.distinct().size == receipts.size)
        require(receipts.map { it.resultRevision }.distinct().size == receipts.size)
        val identity = scope
        if (identity == null) require(folders.isEmpty() && items.isEmpty() && receipts.isEmpty())
        else {
            require(items.all { it.generationId == identity.generationId })
            require(receipts.all { it.resultRevision <= identity.revision })
            require(items.all { it.folderId == null || folders.any { f -> f.id == it.folderId } })
        }
    }

    val recordCount: Long get() = (if (scope == null) 0L else 1L) + folders.size + items.size + receipts.size

    fun fingerprint(): String {
        validate()
        val h = CanonicalHashBuilder.domain("faro-bookmark-backup-v1")
            .string("scope.present", (scope != null).toString())
        scope?.let { h.string("generation", it.generationId).string("digest", it.authorityDigest)
            .string("revision", it.revision.toString()) }
        h.string("folders", folders.size.toString()).string("items", items.size.toString())
            .string("receipts", receipts.size.toString())
        folders.forEachIndexed { i, f -> h.string("f.$i.id", f.id.value).string("f.$i.name", f.name)
            .string("f.$i.revision", f.revision.toString()) }
        items.forEachIndexed { i, b ->
            h.string("b.$i.id", b.id.value).string("b.$i.generation", b.generationId)
                .string("b.$i.chat", b.conversationId.value).string("b.$i.message", b.messageId.value)
                .string("b.$i.folder.present", (b.folderId != null).toString())
                .string("b.$i.folder", b.folderId?.value ?: "").string("b.$i.favorite", b.favorite.toString())
                .string("b.$i.lifecycle", b.lifecycle.name).string("b.$i.revision", b.revision.toString())
        }
        receipts.forEachIndexed { i, r -> h.string("r.$i.key", r.key).string("r.$i.hash", r.requestHash)
            .string("r.$i.revision", r.resultRevision.toString()) }
        return h.sha256Hex()
    }

    companion object {
        const val MAX_RECEIPTS = 100_000
        val EMPTY = BookmarkBackupSnapshot(null, emptyList(), emptyList(), emptyList())
    }
}

/** Called only under the Auxiliary owner's lock; restore inside its transaction. */
internal object AuxiliaryBookmarkBackupStorage {
    fun read(db: SQLiteConnection): BookmarkBackupSnapshot {
        val scope = db.prepare("SELECT generation_id,authority_digest,revision FROM bookmark_scope WHERE scope_id=1")
            .use { if (it.step()) BookmarkBackupScope(it.getText(0), it.getText(1), it.getLong(2)) else null }
        val folders = mutableListOf<BookmarkFolder>()
        db.prepare("SELECT folder_id,name,revision FROM bookmark_folder ORDER BY folder_id LIMIT ${BookmarkPolicy.MAX_FOLDERS + 1}")
            .use { s -> while (s.step()) folders += BookmarkFolder(BookmarkFolderId(s.getText(0)), s.getText(1), s.getLong(2)) }
        val items = mutableListOf<BookmarkBackupItem>()
        db.prepare("SELECT bookmark_id,generation_id,conversation_id,message_id,folder_id,favorite,lifecycle,revision FROM bookmark_item ORDER BY bookmark_id LIMIT ${BookmarkPolicy.MAX_BOOKMARKS + 1}")
            .use { s -> while (s.step()) items += BookmarkBackupItem(
                BookmarkId(s.getText(0)), s.getText(1), ConversationId(s.getText(2)),
                ConversationMessageId(s.getText(3)), if (s.isNull(4)) null else BookmarkFolderId(s.getText(4)),
                s.getLong(5) == 1L, BookmarkLifecycle.valueOf(s.getText(6)), s.getLong(7)) }
        val receipts = mutableListOf<BookmarkBackupReceipt>()
        db.prepare("SELECT idempotency_key,request_hash,result_revision FROM bookmark_receipt ORDER BY idempotency_key LIMIT ${BookmarkBackupSnapshot.MAX_RECEIPTS + 1}")
            .use { s -> while (s.step()) receipts += BookmarkBackupReceipt(s.getText(0), s.getText(1), s.getLong(2)) }
        return BookmarkBackupSnapshot(scope, folders.sortedBy { it.id.value },
            items.sortedBy { it.id.value }, receipts.sortedBy { it.key })
    }

    fun restore(db: SQLiteConnection, snapshot: BookmarkBackupSnapshot) {
        snapshot.validate()
        fun insert(sql: String, vararg values: Any?) = db.prepare(sql).use { s ->
            values.forEachIndexed { i, v -> when (v) {
                null -> s.bindNull(i + 1)
                is Long -> s.bindLong(i + 1, v)
                is String -> s.bindText(i + 1, v)
                else -> error("unsupported backup binding")
            } }
            s.step()
        }
        snapshot.scope?.let { insert("INSERT INTO bookmark_scope(scope_id,generation_id,authority_digest,revision) VALUES(1,?,?,?)",
            it.generationId, it.authorityDigest, it.revision) }
        snapshot.folders.forEach { insert("INSERT INTO bookmark_folder(folder_id,name,revision) VALUES(?,?,?)", it.id.value, it.name, it.revision) }
        snapshot.items.forEach { insert("INSERT INTO bookmark_item(bookmark_id,generation_id,conversation_id,message_id,folder_id,favorite,lifecycle,revision) VALUES(?,?,?,?,?,?,?,?)",
            it.id.value, it.generationId, it.conversationId.value, it.messageId.value, it.folderId?.value,
            if (it.favorite) 1L else 0L, it.lifecycle.name, it.revision) }
        snapshot.receipts.forEach { insert("INSERT INTO bookmark_receipt(idempotency_key,request_hash,result_revision) VALUES(?,?,?)",
            it.key, it.requestHash, it.resultRevision) }
    }
}
