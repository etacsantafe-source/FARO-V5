package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.profile.api.ProfileReadApi
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import ar.com.faro.v5.store.auxiliary.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BookmarkPortabilityTest {
    @TempDir lateinit var root: Path
    private val scope = PortabilityScope.of(PortabilitySection.BOOKMARKS)
    private fun snapshot(favorite: Boolean = false) = BookmarkPortabilitySnapshot(
        3, (if (favorite) "b" else "a").repeat(64),
        listOf(BookmarkFolder(BookmarkFolderId("f1"), "Ideas \"Ñ\"")),
        listOf(PortableBookmarkRecord("b1", "gen-a", "chat", "msg", "f1", favorite, 1)))
    private fun reader(port: BookmarkPortabilityPort? = null) = PortabilitySourceReader(
        ProfileReadApi { error("profile outside scope") },
        FinanceSnapshotReadApi { error("finance outside scope") },
        CommercialEvidencePortabilityPort { error("commercial outside scope") },
        WebEvidencePortabilityPort { error("web outside scope") },
        AuxiliaryPortabilityPort { AuxiliaryPortabilitySnapshot(3, "c".repeat(64), emptyList()) },
        port,
    )
    private fun request(preview: PortabilityPreview) = PortabilityExportRequest(
        EffectId("bookmark-export"), preview,
        PortabilityExportApproval(preview.exportPlanFingerprint, 1), 2)

    @Test fun bookmarksRequireExplicitScopeAndCannotLeakIntoNoticeOrLegacyAll() {
        assertFalse(PortabilitySection.BOOKMARKS in PortabilityScope.all().sections)
        assertEquals(5, PortabilityScope.all().sections.size)
        var bookmarkReads = 0
        val source = reader(BookmarkPortabilityPort { bookmarkReads++; snapshot() })
            .read(PortabilityScope.of(PortabilitySection.NOTICES))
        assertEquals(0, bookmarkReads)
        assertNull(source.bookmarks)
        assertNotNull(source.notices)
    }

    @Test fun approvedExplicitExportIsValidRestrictedJsonWithoutInternalFields() {
        val reader = reader(BookmarkPortabilityPort { snapshot() })
        val preview = PortabilityPreviewService(reader).preview(scope)
        assertEquals(PortabilitySensitivity.RESTRICTED, preview.sections.single().sensitivity)
        assertEquals(2L, preview.sections.single().itemCount)
        assertTrue(PortabilityWarning.BOOKMARK_METADATA_INCLUDED in preview.warnings)
        val store = FilePortabilityArtifactStore(root.resolve("staging"))
        val result = PortabilityBundlePreparer(reader, store).prepare(request(preview))
        val artifact = assertInstanceOf(PortabilityPreparationResult.Prepared::class.java, result).artifact
        val output = ByteArrayOutputStream()
        store.copyTo(artifact, output)
        val text = output.toString(Charsets.UTF_8)
        val json = Json.parseToJsonElement(text).jsonObject
        val sections = json.getValue("sections").jsonObject
        assertEquals(setOf("bookmarks"), sections.keys)
        val bookmarks = sections.getValue("bookmarks").jsonObject
        assertEquals("msg", bookmarks.getValue("records").jsonArray.single().jsonObject.getValue("messageId").jsonPrimitive.content)
        assertEquals("Ideas \"Ñ\"", bookmarks.getValue("folders").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertTrue(json.getValue("manifest").jsonObject.getValue("notice").jsonPrimitive.content.contains("NOT RESTORABLE"))
        for (field in listOf("authorityToken", "authorityDigest", "requestHash", "idempotencyKey", "messageText")) {
            assertFalse(text.contains(field), field)
        }
        assertTrue(store.verify(artifact))
    }

    @Test fun metadataChangedAfterApprovalBlocksBeforeCreatingArtifact() {
        var current = snapshot()
        val reader = reader(BookmarkPortabilityPort { current })
        val preview = PortabilityPreviewService(reader).preview(scope)
        current = snapshot(favorite = true)
        val directory = root.resolve("stale")
        val result = PortabilityBundlePreparer(reader, FilePortabilityArtifactStore(directory)).prepare(request(preview))
        assertEquals(PortabilityPreparationBlockReason.SOURCE_CHANGED,
            assertInstanceOf(PortabilityPreparationResult.Blocked::class.java, result).reason)
        Files.list(directory).use { assertEquals(0L, it.count()) }
    }

    @Test fun missingBookmarkBindingIsUnavailableRatherThanZero() {
        assertThrows(IllegalStateException::class.java) { PortabilityPreviewService(reader()).preview(scope) }
    }
}
