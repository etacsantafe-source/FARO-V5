package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.BookmarkId
import ar.com.faro.v5.kernel.contracts.BookmarkFolder
import ar.com.faro.v5.kernel.contracts.BookmarkFolderId
import ar.com.faro.v5.kernel.contracts.BookmarkLifecycle
import ar.com.faro.v5.kernel.contracts.BookmarkMetadata
import ar.com.faro.v5.kernel.contracts.BookmarkMutation
import ar.com.faro.v5.kernel.contracts.BookmarkMutationPort
import ar.com.faro.v5.kernel.contracts.BookmarkMutationRequest
import ar.com.faro.v5.kernel.contracts.BookmarkMutationResult
import ar.com.faro.v5.kernel.contracts.BookmarkPolicy
import ar.com.faro.v5.kernel.contracts.BookmarkReadPage
import ar.com.faro.v5.kernel.contracts.BookmarkReadPort
import ar.com.faro.v5.kernel.contracts.BookmarkReadRequest
import ar.com.faro.v5.kernel.contracts.BookmarkReadResult
import ar.com.faro.v5.kernel.contracts.BookmarkRejection
import ar.com.faro.v5.kernel.contracts.BookmarkTarget
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The host must bind this to actual N-010 Workspace + generation/ACL fences.
 * No authority instance is supplied by default. Tests may inject a fake,
 * but the shipping V2 host must not mount it until an independent B7 audit.
 */
interface BookmarkAuthorityGate {
    fun isScopeActive(generationId: String, authorityToken: String): Boolean
    fun isTargetAvailable(target: BookmarkTarget): Boolean
}

/** One connection/lock with N-018 Auxiliary: metadata+revision+receipt are atomic. */
class AuxiliaryBookmarkStore internal constructor(
    private val db: SQLiteConnection,
    private val ownerLock: Any,
    private val writeGate: MaterialWriteGate?,
    private val authority: BookmarkAuthorityGate?,
    private val ownerIsOpen: () -> Boolean,
) : BookmarkReadPort, BookmarkMutationPort {
    private data class Scope(val generation: String, val digest: String, val revision: Long)
    private data class Receipt(val fingerprint: String, val revision: Long)

    /** Separate local export admission; reuses the exact operational scope/target fences. */
    internal fun authorizedPortabilitySnapshot(generationId: String, authorityToken: String): BookmarkBackupSnapshot? =
        synchronized(ownerLock) {
            BookmarkPolicy.requireScope(generationId, authorityToken)
            if (!ownerIsOpen() || !scopeAllowed(generationId, authorityToken)) return@synchronized null
            val before = scope()
            if (before != null && !sameScope(before, generationId, authorityToken)) return@synchronized null
            val stored = AuxiliaryBookmarkBackupStorage.read(db)
            val visible = stored.items.filter { it.lifecycle == BookmarkLifecycle.ACTIVE }
            if (visible.any { !targetAllowed(BookmarkTarget(it.conversationId, it.messageId,
                    it.generationId, authorityToken)) } || !ownerIsOpen() ||
                !scopeAllowed(generationId, authorityToken) || before != scope()
            ) return@synchronized null
            // Removed references and replay receipts are not open portability data.
            stored.copy(items = visible, receipts = emptyList())
        }

    override suspend fun read(request: BookmarkReadRequest): BookmarkReadResult =
        synchronized(ownerLock) {
            if (!ownerIsOpen() || !scopeAllowed(request.generationId, request.authorityToken)) {
                return@synchronized BookmarkReadResult.Unavailable
            }
            val current = scope()
            if (current != null && !sameScope(current, request.generationId, request.authorityToken)) {
                return@synchronized BookmarkReadResult.Stale
            }
            val bookmarks = mutableListOf<BookmarkMetadata>()
            var query = """
                SELECT bookmark_id, conversation_id, message_id,
                       generation_id, folder_id, favorite, lifecycle, revision
                FROM bookmark_item WHERE lifecycle = 'ACTIVE'
            """.trimIndent()
            val cursor = request.cursor
            if (cursor != null) query += " AND bookmark_id > ?"
            query += " ORDER BY bookmark_id LIMIT ${request.limit + 1}"
            db.prepare(query).use { statement ->
                cursor?.let { statement.bindText(1, it.value) }
                while (statement.step()) {
                    val item = BookmarkMetadata(
                        id = BookmarkId(statement.getText(0)),
                        target = BookmarkTarget(
                            conversationId = ConversationId(statement.getText(1)),
                            messageId = ConversationMessageId(statement.getText(2)),
                            generationId = statement.getText(3),
                            authorityToken = request.authorityToken,
                        ),
                        folderId =
                            if (statement.isNull(4)) null else BookmarkFolderId(statement.getText(4)),
                        favorite = statement.getLong(5) == 1L,
                        lifecycle = BookmarkLifecycle.valueOf(statement.getText(6)),
                        revision = statement.getLong(7),
                    )
                    bookmarks.add(item)
                }
            }
            val visible = bookmarks.take(request.limit)
            if (visible.any { !targetAllowed(it.target) } ||
                !scopeAllowed(request.generationId, request.authorityToken)
            ) {
                return@synchronized BookmarkReadResult.Unavailable
            }
            val folders = mutableListOf<BookmarkFolder>()
            db.prepare("SELECT folder_id,name,revision FROM bookmark_folder ORDER BY folder_id").use { st ->
                while (st.step()) {
                    folders += BookmarkFolder(
                        BookmarkFolderId(st.getText(0)), st.getText(1), st.getLong(2)
                    )
                }
            }
            BookmarkReadResult.Available(
                BookmarkReadPage(visible, folders, hasMore = bookmarks.size > request.limit)
            )
        }

    override suspend fun mutate(request: BookmarkMutationRequest): BookmarkMutationResult {
        val operation = { synchronized(ownerLock) { mutateLocked(request) } }
        return writeGate?.withMaterialWrite(operation) ?: operation()
    }

    private fun mutateLocked(request: BookmarkMutationRequest): BookmarkMutationResult {
        val mutation = request.mutation
        if (!ownerIsOpen() || !scopeAllowed(request.generationId, request.authorityToken)) {
            return BookmarkMutationResult.Unavailable
        }
        if (mutation is BookmarkMutation.Add &&
            !targetAllowed(mutation.bookmark.target)
        ) {
            return BookmarkMutationResult.Unavailable
        }
        db.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            fun abort(result: BookmarkMutationResult): BookmarkMutationResult {
                db.execSQL("ROLLBACK TRANSACTION")
                return result
            }
            val current = scope()
            if (current != null &&
                !sameScope(current, request.generationId, request.authorityToken)
            ) return abort(BookmarkMutationResult.IdentityConflict)
            val fingerprint = fingerprint(request)
            val previous = receipt(request.idempotencyKey.value)
            if (previous != null) {
                return abort(
                    if (previous.fingerprint == fingerprint) {
                        BookmarkMutationResult.Saved(previous.revision, replayed = true)
                    } else {
                        BookmarkMutationResult.IdentityConflict
                    }
                )
            }
            val revision = current?.revision ?: 0L
            if (request.expectedRevision != revision) {
                return abort(BookmarkMutationResult.Stale(revision))
            }
            if (current == null) {
                db.prepare(
                    "INSERT INTO bookmark_scope(scope_id,generation_id,authority_digest,revision)" +
                        " VALUES(1,?,?,0)"
                ).use { st ->
                    st.bindText(1, request.generationId)
                    st.bindText(2, sha256(listOf(request.authorityToken)))
                    st.step()
                }
            }
            val rejection = applyMutation(mutation)
            if (rejection != null) return abort(BookmarkMutationResult.Rejected(rejection))
            if (!ownerIsOpen() || !scopeAllowed(request.generationId, request.authorityToken) ||
                (mutation is BookmarkMutation.Add && !targetAllowed(mutation.bookmark.target))
            ) return abort(BookmarkMutationResult.Unavailable)
            val next = revision + 1
            db.prepare("UPDATE bookmark_scope SET revision=? WHERE scope_id=1 AND revision=?").use { st ->
                st.bindLong(1, next)
                st.bindLong(2, revision)
                st.step()
            }
            db.prepare(
                "INSERT INTO bookmark_receipt(idempotency_key,request_hash,result_revision)" +
                    " VALUES(?,?,?)"
            ).use { st ->
                st.bindText(1, request.idempotencyKey.value)
                st.bindText(2, fingerprint)
                st.bindLong(3, next)
                st.step()
            }
            db.execSQL("COMMIT TRANSACTION")
            return BookmarkMutationResult.Saved(next, replayed = false)
        } catch (failure: Throwable) {
            runCatching { db.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    }

    private fun applyMutation(mutation: BookmarkMutation): BookmarkRejection? {
        return when (mutation) {
            is BookmarkMutation.Add -> {
                val b = mutation.bookmark
                if (exists("SELECT 1 FROM bookmark_item WHERE bookmark_id=?", b.id.value) ||
                    exists(
                        "SELECT 1 FROM bookmark_item WHERE generation_id=? AND " +
                            "conversation_id=? AND message_id=?",
                        b.target.generationId,b.target.conversationId.value,b.target.messageId.value
                    )
                ) return BookmarkRejection.ALREADY_EXISTS
                if (count("SELECT COUNT(*) FROM bookmark_item") >= BookmarkPolicy.MAX_BOOKMARKS)
                    return BookmarkRejection.QUOTA_EXCEEDED
                val folderId = b.folderId
                if (folderId != null &&
                    !exists("SELECT 1 FROM bookmark_folder WHERE folder_id=?", folderId.value)
                ) return BookmarkRejection.NOT_FOUND
                db.prepare(
                    "INSERT INTO bookmark_item(bookmark_id,generation_id,conversation_id," +
                        "message_id,folder_id,favorite,lifecycle,revision) VALUES(?,?,?,?,?,?,?,?)"
                ).use { st ->
                    st.bindText(1, b.id.value)
                    st.bindText(2, b.target.generationId)
                    st.bindText(3, b.target.conversationId.value)
                    st.bindText(4, b.target.messageId.value)
                    if (folderId == null) st.bindNull(5) else st.bindText(5, folderId.value)
                    st.bindLong(6, if (b.favorite) 1 else 0)
                    st.bindText(7, b.lifecycle.name)
                    st.bindLong(8, b.revision)
                    st.step()
                }
                null
            }
            is BookmarkMutation.FolderCreate -> {
                val folder = mutation.folder
                if (exists("SELECT 1 FROM bookmark_folder WHERE folder_id=?",folder.id.value))
                    return BookmarkRejection.ALREADY_EXISTS
                if (count("SELECT COUNT(*) FROM bookmark_folder") >= BookmarkPolicy.MAX_FOLDERS)
                    return BookmarkRejection.QUOTA_EXCEEDED
                db.prepare("INSERT INTO bookmark_folder(folder_id,name,revision) VALUES(?,?,?)").use { st ->
                    st.bindText(1,folder.id.value)
                    st.bindText(2,folder.name)
                    st.bindLong(3,folder.revision)
                    st.step()
                }
                null
            }
            is BookmarkMutation.FolderRename -> {
                if (!exists("SELECT 1 FROM bookmark_folder WHERE folder_id=?",mutation.id.value))
                    return BookmarkRejection.NOT_FOUND
                db.prepare(
                    "UPDATE bookmark_folder SET name=?,revision=revision+1 WHERE folder_id=?"
                ).use { st ->
                    st.bindText(1,mutation.name)
                    st.bindText(2,mutation.id.value)
                    st.step()
                }
                null
            }
            is BookmarkMutation.FolderDelete -> {
                if (!exists("SELECT 1 FROM bookmark_folder WHERE folder_id=?",mutation.id.value))
                    return BookmarkRejection.NOT_FOUND
                if (exists("SELECT 1 FROM bookmark_item WHERE folder_id=?",mutation.id.value))
                    return BookmarkRejection.FOLDER_NOT_EMPTY
                db.prepare("DELETE FROM bookmark_folder WHERE folder_id=?").use { st ->
                    st.bindText(1,mutation.id.value)
                    st.step()
                }
                null
            }
            is BookmarkMutation.FolderAssign -> {
                val folderId = mutation.folderId
                if (!exists("SELECT 1 FROM bookmark_item WHERE bookmark_id=?",mutation.id.value))
                    return BookmarkRejection.NOT_FOUND
                if (folderId != null &&
                    !exists("SELECT 1 FROM bookmark_folder WHERE folder_id=?", folderId.value)
                ) return BookmarkRejection.NOT_FOUND
                db.prepare(
                    "UPDATE bookmark_item SET folder_id=?,revision=revision+1 WHERE bookmark_id=?"
                ).use { st ->
                    if (folderId == null) st.bindNull(1)
                    else st.bindText(1, folderId.value)
                    st.bindText(2,mutation.id.value)
                    st.step()
                }
                null
            }
            is BookmarkMutation.FavoriteSet -> {
                if (!exists("SELECT 1 FROM bookmark_item WHERE bookmark_id=?",mutation.id.value))
                    return BookmarkRejection.NOT_FOUND
                db.prepare(
                    "UPDATE bookmark_item SET favorite=?,revision=revision+1 WHERE bookmark_id=?"
                ).use { st ->
                    st.bindLong(1,if (mutation.favorite) 1 else 0)
                    st.bindText(2,mutation.id.value)
                    st.step()
                }
                null
            }
            is BookmarkMutation.LifecycleSet -> {
                if (!exists("SELECT 1 FROM bookmark_item WHERE bookmark_id=?",mutation.id.value))
                    return BookmarkRejection.NOT_FOUND
                db.prepare(
                    "UPDATE bookmark_item SET lifecycle=?,revision=revision+1 WHERE bookmark_id=?"
                ).use { st ->
                    st.bindText(1,mutation.lifecycle.name)
                    st.bindText(2,mutation.id.value)
                    st.step()
                }
                null
            }
        }
    }

    private fun scope(): Scope? =
        db.prepare("SELECT generation_id,authority_digest,revision FROM bookmark_scope WHERE scope_id=1")
            .use { st -> if (st.step()) Scope(st.getText(0),st.getText(1),st.getLong(2)) else null }

    private fun sameScope(current: Scope, generation: String, token: String): Boolean =
        current.generation == generation && current.digest == sha256(listOf(token))

    private fun receipt(key: String): Receipt? =
        db.prepare("SELECT request_hash,result_revision FROM bookmark_receipt WHERE idempotency_key=?")
            .use { st ->
                st.bindText(1,key)
                if (st.step()) Receipt(st.getText(0),st.getLong(1)) else null
            }

    private fun exists(sql: String,vararg binds: String): Boolean =
        db.prepare(sql).use { st ->
            binds.forEachIndexed { i,value -> st.bindText(i+1,value) }
            st.step()
        }

    private fun count(sql: String): Int =
        db.prepare(sql).use { st -> check(st.step());st.getLong(0).toInt() }

    private fun scopeAllowed(generation: String, token: String): Boolean =
        runCatching { authority?.isScopeActive(generation,token) == true }.getOrDefault(false)

    private fun targetAllowed(target: BookmarkTarget): Boolean =
        runCatching { authority?.isTargetAvailable(target) == true }.getOrDefault(false)

    private fun fingerprint(request: BookmarkMutationRequest): String {
        val base = listOf(
            request.generationId, sha256(listOf(request.authorityToken)),
            request.expectedRevision.toString(), request.idempotencyKey.value
        )
        val extra = when (val intent = request.mutation) {
            is BookmarkMutation.Add -> listOf(
                "ADD", intent.bookmark.id.value, intent.bookmark.target.conversationId.value,
                intent.bookmark.target.messageId.value, intent.bookmark.target.generationId,
                sha256(listOf(intent.bookmark.target.authorityToken)),
                intent.bookmark.folderId?.value ?: "",intent.bookmark.favorite.toString(),
                intent.bookmark.lifecycle.name,intent.bookmark.revision.toString()
            )
            is BookmarkMutation.LifecycleSet -> listOf("LIFECYCLE",intent.id.value,intent.lifecycle.name)
            is BookmarkMutation.FavoriteSet -> listOf("FAVORITE",intent.id.value,intent.favorite.toString())
            is BookmarkMutation.FolderCreate -> listOf(
                "FOLDER_CREATE",intent.folder.id.value,intent.folder.name,intent.folder.revision.toString()
            )
            is BookmarkMutation.FolderRename -> listOf("FOLDER_RENAME",intent.id.value,intent.name)
            is BookmarkMutation.FolderDelete -> listOf("FOLDER_DELETE",intent.id.value)
            is BookmarkMutation.FolderAssign -> listOf(
                "FOLDER_ASSIGN",intent.id.value,intent.folderId?.value ?: ""
            )
        }
        return sha256(base+extra)
    }

    private fun sha256(parts: List<String>): String {
        val hash = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(StandardCharsets.UTF_8)
            hash.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            hash.update(bytes)
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
