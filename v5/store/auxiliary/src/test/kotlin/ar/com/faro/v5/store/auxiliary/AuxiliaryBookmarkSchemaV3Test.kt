package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * S3.A3 schema-only migration tests. The operational bookmark owner is NOT
 * enabled and these tests deliberately do not claim ACL/CAS/backup readiness.
 */
class AuxiliaryBookmarkSchemaV3Test {
    @Test
    fun freshAuxiliaryCreatesFourBookmarkTablesWithoutMessageBody() = db { file ->
        AuxiliaryDeliveryStore(file).use { store ->
            assertEquals(3, store.schemaVersion())
            assertEquals("ok", store.integrityCheck())
        }
        BundledSQLiteDriver().open(file).use { sql ->
            val names = mutableSetOf<String>()
            sql.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name LIKE 'bookmark_%' ORDER BY name"
            ).use { s ->
                while (s.step()) names.add(s.getText(0))
            }
            assertEquals(
                setOf("bookmark_scope", "bookmark_folder", "bookmark_item", "bookmark_receipt"),
                names
            )
            val columns = mutableSetOf<String>()
            sql.prepare("PRAGMA table_info(bookmark_item)").use { s ->
                while (s.step()) columns.add(s.getText(1))
            }
            assertTrue(columns.containsAll(listOf(
                "generation_id", "conversation_id", "message_id", "folder_id", "favorite",
                "lifecycle", "revision"
            )))
            assertFalse(columns.contains("message_text"))
            assertFalse(columns.contains("content"))
            assertFalse(columns.contains("authority_token"))
        }
    }

    @Test
    fun versionTwoMigratesInPlacePreservingNoticeAndDeliveryIntent() = db { file ->
        BundledSQLiteDriver().open(file).use { sql ->
            sql.execSQL("PRAGMA foreign_keys = ON")
            sql.execSQL("""
                CREATE TABLE delivery_notice (
                    notice_id TEXT NOT NULL PRIMARY KEY,
                    notice_key TEXT NOT NULL, source_ref TEXT NOT NULL,
                    occurrence_ref TEXT NOT NULL, materiality TEXT NOT NULL,
                    sensitivity TEXT NOT NULL, summary TEXT NOT NULL,
                    destination_ref TEXT NOT NULL, relevance TEXT NOT NULL,
                    attention TEXT NOT NULL, revision INTEGER NOT NULL,
                    resolution_reason TEXT, UNIQUE(notice_key, occurrence_ref)
                )
            """.trimIndent())
            sql.execSQL("""
                CREATE TABLE delivery_intent (
                    delivery_intent_id TEXT NOT NULL PRIMARY KEY,
                    effect_id TEXT NOT NULL UNIQUE, notice_id TEXT NOT NULL UNIQUE,
                    sink TEXT NOT NULL, desired_action TEXT NOT NULL,
                    state TEXT NOT NULL, FOREIGN KEY(notice_id)
                    REFERENCES delivery_notice(notice_id) ON DELETE CASCADE
                )
            """.trimIndent())
            sql.execSQL("""
                INSERT INTO delivery_notice VALUES(
                    'n1','key1','source1','occ1','NOTICE','NORMAL',
                    'Aviso previo','destination1','OPEN','UNSEEN',1,NULL
                )
            """.trimIndent())
            sql.execSQL("""
                INSERT INTO delivery_intent VALUES(
                    'i1','effect1','n1','ANDROID_NOTIFICATION','UPSERT','PLANNED'
                )
            """.trimIndent())
            sql.execSQL("PRAGMA user_version = 2")
        }
        AuxiliaryDeliveryStore(file).use { store ->
            assertEquals(3, store.schemaVersion())
            assertEquals("n1", store.readNotices().single().noticeId.value)
            assertEquals(1, store.deliveryIntentCount())
            assertEquals("ok", store.integrityCheck())
        }
        AuxiliaryDeliveryStore(file).use { reopened ->
            assertEquals(3, reopened.schemaVersion())
            assertEquals(1, reopened.readNotices().size)
        }
    }

    @Test
    fun sqliteForeignKeyRestrictsNonEmptyFolderAndReceiptKeyIsUnique() = db { file ->
        AuxiliaryDeliveryStore(file).close()
        BundledSQLiteDriver().open(file).use { sql ->
            sql.execSQL("PRAGMA foreign_keys = ON")
            sql.execSQL(
                "INSERT INTO bookmark_scope VALUES(1, 'generation-1', " +
                    "'0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', 0)"
            )
            sql.execSQL("INSERT INTO bookmark_folder(folder_id, name, revision) " +
                "VALUES('folder-1','Trabajo',1)")
            sql.execSQL(
                "INSERT INTO bookmark_item(" +
                    "bookmark_id,generation_id,conversation_id,message_id,folder_id,revision" +
                    ") VALUES('b1','generation-1','chat1','msg1','folder-1',1)"
            )
            assertThrows(Exception::class.java) {
                sql.execSQL("DELETE FROM bookmark_folder WHERE folder_id='folder-1'")
            }
            sql.execSQL(
                "INSERT INTO bookmark_receipt(idempotency_key,request_hash,result_revision)" +
                    " VALUES('op1','0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',1)"
            )
            assertThrows(Exception::class.java) {
                sql.execSQL(
                    "INSERT INTO bookmark_receipt(idempotency_key,request_hash,result_revision)" +
                        " VALUES('op1','0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',2)"
                )
            }
            sql.prepare("PRAGMA foreign_key_check").use { s ->
                assertFalse(s.step(), "schema v3 must have no broken foreign keys")
            }
        }
    }

    @Test
    fun futureSchemaVersionStopsWithoutRewritingFile() = db { file ->
        BundledSQLiteDriver().open(file).use { sql ->
            sql.execSQL("PRAGMA user_version = 4")
        }
        assertThrows(IllegalStateException::class.java) {
            AuxiliaryDeliveryStore(file).close()
        }
        BundledSQLiteDriver().open(file).use { sql ->
            sql.prepare("PRAGMA user_version").use { s ->
                assertTrue(s.step())
                assertEquals(4, s.getLong(0).toInt())
            }
        }
    }

    @Test
    fun falselyLabeledV3WithoutRequiredTablesFailsClosed() = db { file ->
        BundledSQLiteDriver().open(file).use { sql ->
            sql.execSQL("PRAGMA user_version = 3")
        }
        assertThrows(IllegalStateException::class.java) {
            AuxiliaryDeliveryStore(file).close()
        }
    }

    private fun db(block: (String) -> Unit) {
        val dir = Files.createTempDirectory("faro-a3-aux-schema-v3")
        try {
            block(dir.resolve("auxiliary.db").toString())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
