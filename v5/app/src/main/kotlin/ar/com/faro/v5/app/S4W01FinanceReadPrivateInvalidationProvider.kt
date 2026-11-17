package ar.com.faro.v5.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * DEV-only URI anchor for ContentResolver invalidation. No Finance data,
 * credentials, account values, SQL access or writable IPC endpoint.
 * Not registered in shipping / S3 manifests.
 */
class S4W01FinanceReadPrivateInvalidationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw SecurityException("Finance DEV invalidation has no read endpoint")

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw SecurityException("Finance DEV invalidation has no write endpoint")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException("Finance DEV invalidation has no write endpoint")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw SecurityException("Finance DEV invalidation has no write endpoint")
}
