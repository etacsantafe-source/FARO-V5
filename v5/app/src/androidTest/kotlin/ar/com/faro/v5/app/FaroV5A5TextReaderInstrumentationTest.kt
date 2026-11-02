package ar.com.faro.v5.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.platform.android.ingress.AndroidConversationAttachmentReader
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A5 real Android ContentResolver path; synthetic text only, no model egress. */
@RunWith(AndroidJUnit4::class)
class FaroV5A5TextReaderInstrumentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private fun fileUri() = Uri.parse("content://${context.packageName}.a5test/text")

    @Test fun syntheticContentProviderReadIsBoundedAndWipedOnClose() {
        val files = AndroidConversationAttachmentReader.readSelected(
            context, listOf(fileUri()),
        )
        try {
            assertEquals(1, files.size)
            val file = files.single()
            assertEquals(FaroV5A5TestContentProvider.FILE_NAME, file.displayName())
            assertEquals("text/plain", file.mimeType())
            val bytes = ByteArray(file.sizeBytes())
            try {
                file.readOnlyBytes().get(bytes)
                assertEquals(FaroV5A5TestContentProvider.TEXT,
                    String(bytes, Charsets.UTF_8))
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                digest.fill(0)
                assertEquals(hex, file.sha256Hex())
            } finally { bytes.fill(0) }
            file.close()
            var rejected = false
            try { file.readOnlyBytes() } catch (_: IllegalStateException) {
                rejected = true
            }
            assertTrue("Closed ephemeral payload cannot be reused", rejected)
        } finally {
            files.forEach { it.close() }
            File(context.cacheDir, FaroV5A5TestContentProvider.FILE_NAME).delete()
        }
    }

    @Test fun duplicatePickedBytesFailClosedBeforeAnyDraftMutation() {
        var rejected = false
        try {
            AndroidConversationAttachmentReader.readSelected(
                context, listOf(fileUri(), fileUri()),
            ).forEach { it.close() }
        } catch (_: IOException) {
            rejected = true
        } finally {
            File(context.cacheDir, FaroV5A5TestContentProvider.FILE_NAME).delete()
        }
        assertTrue("Duplicate content must be rejected", rejected)
    }

    @Test fun nonContentUriIsRejectedWithoutOpeningProvider() {
        var rejected = false
        try {
            AndroidConversationAttachmentReader.readSelected(
                context, listOf(Uri.parse("file:///tmp/never-open-a5.txt")),
            ).forEach { it.close() }
        } catch (_: IOException) { rejected = true }
        assertTrue("Only Android content URI is admissible", rejected)
    }
}
