package ar.com.faro.v5.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Isolated debug-only local fixture; same UID as target app, never exported or in release. */
class FaroV5A5TestContentProvider : ContentProvider() {
    companion object {
        const val FILE_NAME = "faro-a5-synthetic.txt"
        const val TEXT = "FARO A5 local fixture - no personal data"
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "text/plain"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(arrayOf(
        OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE,
    )).apply {
        addRow(arrayOf<Any?>(FILE_NAME, TEXT.toByteArray(Charsets.UTF_8).size))
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "Fixture is read-only" }
        val file = File(requireNotNull(context).cacheDir, FILE_NAME)
        file.writeText(TEXT, Charsets.UTF_8)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")
}
