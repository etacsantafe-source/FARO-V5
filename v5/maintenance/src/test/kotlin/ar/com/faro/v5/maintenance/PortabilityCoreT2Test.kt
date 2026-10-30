package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidencePortabilityPort
import ar.com.faro.v5.evidence.CommercialEvidencePortabilitySnapshot
import ar.com.faro.v5.evidence.PortableCommercialObservation
import ar.com.faro.v5.evidence.PortableWebFetchRecord
import ar.com.faro.v5.evidence.PortableWebSearchHit
import ar.com.faro.v5.evidence.PortableWebSearchRecord
import ar.com.faro.v5.evidence.WebEvidencePortabilityPort
import ar.com.faro.v5.evidence.WebEvidencePortabilitySnapshot
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceEntryCategory
import ar.com.faro.v5.owner.finance.api.FinanceEntryConcept
import ar.com.faro.v5.owner.finance.api.FinanceEntryId
import ar.com.faro.v5.owner.finance.api.FinanceEntryKind
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName
import ar.com.faro.v5.owner.profile.api.ProfileFieldProvenance
import ar.com.faro.v5.owner.profile.api.ProfileReadApi
import ar.com.faro.v5.owner.profile.api.ProfileSnapshot
import ar.com.faro.v5.store.auxiliary.AuxiliaryPortabilityPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryPortabilitySnapshot
import ar.com.faro.v5.store.auxiliary.PortableNoticeRecord
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PortabilityCoreT2Test {
    @Test
    fun `preview reads only selected source and performs no artifact write`() {
        val profileReads = AtomicInteger(0)
        val financeReads = AtomicInteger(0)
        val commercialReads = AtomicInteger(0)
        val webReads = AtomicInteger(0)
        val noticeReads = AtomicInteger(0)

        val sourceReader =
            PortabilitySourceReader(
                profile =
                    ProfileReadApi {
                        profileReads.incrementAndGet()
                        profile()
                    },
                finance =
                    FinanceSnapshotReadApi {
                        financeReads.incrementAndGet()
                        finance()
                    },
                commercialEvidence =
                    CommercialEvidencePortabilityPort {
                        commercialReads.incrementAndGet()
                        commercial()
                    },
                webEvidence =
                    WebEvidencePortabilityPort {
                        webReads.incrementAndGet()
                        web()
                    },
                notices =
                    AuxiliaryPortabilityPort {
                        noticeReads.incrementAndGet()
                        notices()
                    },
            )

        val preview =
            PortabilityPreviewService(sourceReader)
                .preview(PortabilityScope.of(PortabilitySection.PROFILE))

        assertEquals(1, profileReads.get())
        assertEquals(0, financeReads.get())
        assertEquals(0, commercialReads.get())
        assertEquals(0, webReads.get())
        assertEquals(0, noticeReads.get())
        assertEquals(listOf(PortabilitySection.PROFILE), preview.scope.sections)
        assertEquals(
            listOf(
                PortabilityWarning.NOT_A_BACKUP,
                PortabilityWarning.OPEN_ARTIFACT,
                PortabilityWarning.PERSONAL_DATA_INCLUDED,
            ).sortedBy { it.name },
            preview.warnings,
        )
        assertTrue("vault_secrets" in preview.exclusions)
        assertTrue("delivery_intents" in preview.exclusions)
    }

    @Test
    fun `approved export stages valid open JSON with digests and no backup semantics`(
        @TempDir tempDir: Path,
    ) {
        val sourceReader = sourceReader()
        val preview =
            PortabilityPreviewService(sourceReader)
                .preview(PortabilityScope.all())
        val artifactStore =
            FilePortabilityArtifactStore(tempDir.resolve("portability-staging"))
        val result =
            PortabilityBundlePreparer(sourceReader, artifactStore)
                .prepare(
                    PortabilityExportRequest(
                        effectId = EffectId("export-effect-a7"),
                        preview = preview,
                        approval =
                            PortabilityExportApproval(
                                preview.exportPlanFingerprint,
                                approvedAtEpochMillis = 1_000L,
                            ),
                        generatedAtEpochMillis = 1_100L,
                    )
                )

        val prepared =
            assertInstanceOf(
                PortabilityPreparationResult.Prepared::class.java,
                result,
            )
        val artifact = prepared.artifact
        assertTrue(artifactStore.verify(artifact))

        val copied = ByteArrayOutputStream()
        assertEquals(artifact.byteCount, artifactStore.copyTo(artifact, copied))
        val bytes = copied.toByteArray()
        val text = bytes.decodeToString()
        val root = Json.parseToJsonElement(text).jsonObject
        val manifest = root.getValue("manifest").jsonObject

        assertEquals(
            PortabilityPreview.FORMAT_PROFILE,
            manifest.getValue("formatProfile").jsonPrimitive.content,
        )
        assertEquals(
            "NOT A FARO BACKUP / NOT RESTORABLE",
            manifest.getValue("notice").jsonPrimitive.content,
        )
        assertEquals(5, manifest.getValue("sectionMetadata").jsonArray.size)
        assertTrue(text.contains("Diego \\\"Ñ\\\""))
        assertTrue(text.contains("milk"))
        assertTrue(text.contains("sensitive notice"))
        assertFalse(text.contains("providerRequestId"))
        assertFalse(text.contains("providerUsage"))
        assertFalse(text.contains("idempotencyKey"))
        assertFalse(text.contains("payloadFingerprint"))
        assertFalse(text.contains("deliveryIntent"))
        assertTrue(
            manifest.getValue("exclusions")
                .jsonArray
                .any { it.jsonPrimitive.content == "vault_secrets" }
        )

        val backupOpen =
            PortableBackupArchiveCrypto()
                .open(bytes, "correct horse battery staple".toCharArray())
        val rejected =
            assertInstanceOf(
                PortableBackupOpenResult.Rejected::class.java,
                backupOpen,
            )
        assertEquals(PortableBackupOpenFailure.INVALID_ENVELOPE, rejected.failure)

        assertTrue(artifactStore.delete(artifact))
        assertFalse(artifactStore.verify(artifact))
    }

    @Test
    fun `section digest covers exact JSON section bytes`(
        @TempDir tempDir: Path,
    ) {
        val sourceReader = sourceReader()
        val preview =
            PortabilityPreviewService(sourceReader)
                .preview(PortabilityScope.of(PortabilitySection.PROFILE))
        val store = FilePortabilityArtifactStore(tempDir.resolve("staging"))
        val prepared =
            assertInstanceOf(
                PortabilityPreparationResult.Prepared::class.java,
                PortabilityBundlePreparer(sourceReader, store).prepare(
                    request(preview, "effect-profile-only")
                ),
            ).artifact
        val output = ByteArrayOutputStream()
        store.copyTo(prepared, output)
        val text = output.toString(Charsets.UTF_8)

        val prefix = "{\"sections\":{\"profile\":"
        assertTrue(text.startsWith(prefix))
        val sectionStart = prefix.length
        val sectionEnd = text.indexOf("},\"manifest\":", startIndex = sectionStart)
        assertTrue(sectionEnd > sectionStart)
        val sectionJson = text.substring(sectionStart, sectionEnd)
        val sectionDigest =
            MessageDigest.getInstance("SHA-256")
                .digest(sectionJson.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        val parsed = Json.parseToJsonElement(text).jsonObject
        val metadata =
            parsed.getValue("manifest")
                .jsonObject
                .getValue("sectionMetadata")
                .jsonArray
                .single()
                .jsonObject
        assertEquals(sectionDigest, metadata.getValue("jsonSha256").jsonPrimitive.content)
        assertEquals(
            sectionJson.toByteArray(Charsets.UTF_8).size.toString(),
            metadata.getValue("jsonBytes").jsonPrimitive.content,
        )
    }

    @Test
    fun `source change after preview fails closed before staging`(
        @TempDir tempDir: Path,
    ) {
        var finance = finance(revision = 4L)
        val sourceReader =
            sourceReader(financeProvider = { finance })
        val preview =
            PortabilityPreviewService(sourceReader)
                .preview(PortabilityScope.of(PortabilitySection.FINANCE))
        finance = finance(revision = 5L)

        val staging = tempDir.resolve("staging")
        val result =
            PortabilityBundlePreparer(
                sourceReader,
                FilePortabilityArtifactStore(staging),
            ).prepare(request(preview, "source-changed"))

        val blocked =
            assertInstanceOf(
                PortabilityPreparationResult.Blocked::class.java,
                result,
            )
        assertEquals(PortabilityPreparationBlockReason.SOURCE_CHANGED, blocked.reason)
        Files.list(staging).use { files ->
            assertEquals(0L, files.count())
        }
    }

    @Test
    fun `approval mismatch blocks before source reread or staging`() {
        val profileReads = AtomicInteger(0)
        val sourceReader =
            PortabilitySourceReader(
                profile =
                    ProfileReadApi {
                        profileReads.incrementAndGet()
                        profile()
                    },
                finance = FinanceSnapshotReadApi { finance() },
                commercialEvidence = CommercialEvidencePortabilityPort { commercial() },
                webEvidence = WebEvidencePortabilityPort { web() },
                notices = AuxiliaryPortabilityPort { notices() },
            )
        val preview =
            PortabilityPreviewService(sourceReader)
                .preview(PortabilityScope.of(PortabilitySection.PROFILE))
        val stageCalls = AtomicInteger(0)
        val store =
            object : PortabilityArtifactStore {
                override fun stage(
                    effectId: EffectId,
                    planFingerprint: ExportPlanFingerprint,
                    generatedAtEpochMillis: Long,
                    writer: (OutputStream) -> Unit,
                ): PreparedPortabilityArtifact {
                    stageCalls.incrementAndGet()
                    error("must not stage")
                }

                override fun verify(artifact: PreparedPortabilityArtifact) = false
                override fun copyTo(
                    artifact: PreparedPortabilityArtifact,
                    output: OutputStream,
                ): Long = error("not used")
                override fun delete(artifact: PreparedPortabilityArtifact) = false
            }

        val result =
            PortabilityBundlePreparer(sourceReader, store).prepare(
                PortabilityExportRequest(
                    effectId = EffectId("mismatch"),
                    preview = preview,
                    approval =
                        PortabilityExportApproval(
                            ExportPlanFingerprint("f".repeat(64)),
                            approvedAtEpochMillis = 10L,
                        ),
                    generatedAtEpochMillis = 11L,
                )
            )
        val blocked =
            assertInstanceOf(PortabilityPreparationResult.Blocked::class.java, result)
        assertEquals(PortabilityPreparationBlockReason.APPROVAL_MISMATCH, blocked.reason)
        assertEquals(1, profileReads.get())
        assertEquals(0, stageCalls.get())
    }

    @Test
    fun `bounded staging deletes temp artifact on overflow`(
        @TempDir tempDir: Path,
    ) {
        val staging = tempDir.resolve("bounded")
        val store = FilePortabilityArtifactStore(staging, maxArtifactBytes = 32L)

        assertThrows(IllegalStateException::class.java) {
            store.stage(
                effectId = EffectId("bounded-effect"),
                planFingerprint = ExportPlanFingerprint("a".repeat(64)),
                generatedAtEpochMillis = 1L,
            ) { output ->
                output.write(ByteArray(33) { 1 })
            }
        }
        Files.list(staging).use { files ->
            assertEquals(0L, files.count())
        }
    }

    private fun request(
        preview: PortabilityPreview,
        effectId: String,
    ): PortabilityExportRequest =
        PortabilityExportRequest(
            effectId = EffectId(effectId),
            preview = preview,
            approval =
                PortabilityExportApproval(
                    preview.exportPlanFingerprint,
                    approvedAtEpochMillis = 100L,
                ),
            generatedAtEpochMillis = 101L,
        )

    private fun sourceReader(
        profileProvider: () -> ProfileSnapshot = { profile() },
        financeProvider: () -> FinanceSnapshot = { finance() },
        commercialProvider: () -> CommercialEvidencePortabilitySnapshot = { commercial() },
        webProvider: () -> WebEvidencePortabilitySnapshot = { web() },
        noticesProvider: () -> AuxiliaryPortabilitySnapshot = { notices() },
    ): PortabilitySourceReader =
        PortabilitySourceReader(
            profile = ProfileReadApi(profileProvider),
            finance = FinanceSnapshotReadApi(financeProvider),
            commercialEvidence = CommercialEvidencePortabilityPort(commercialProvider),
            webEvidence = WebEvidencePortabilityPort(webProvider),
            notices = AuxiliaryPortabilityPort(noticesProvider),
        )

    private fun profile(): ProfileSnapshot =
        ProfileSnapshot(
            displayName = DeclaredDisplayName.of("Diego \"Ñ\""),
            revision = 3L,
            provenance = ProfileFieldProvenance.DECLARED_BY_USER,
        )

    private fun finance(revision: Long = 4L): FinanceSnapshot {
        val accountId = FinanceAccountId.of("cash-ars")
        return FinanceSnapshot(
            accounts =
                listOf(
                    FinanceAccountSnapshot(
                        accountId = accountId,
                        displayName = FinanceAccountDisplayName.of("Cash"),
                        currency = FinanceCurrencyCode.of("ARS"),
                        nature = FinanceAccountNature.CASH,
                        openingBalanceMinor = 100_000L,
                    )
                ),
            revision = revision,
            entries =
                listOf(
                    FinanceEntrySnapshot(
                        entryId = FinanceEntryId.of("entry-a7"),
                        accountId = accountId,
                        amountMinor = 12_500L,
                        kind = FinanceEntryKind.EXPENSE,
                        currency = FinanceCurrencyCode.of("ARS"),
                        concept = FinanceEntryConcept.of("Groceries"),
                        category = FinanceEntryCategory.of("Food"),
                        effectiveAtEpochMillis = 100L,
                        recordedAtEpochMillis = 110L,
                        provenanceLabel = "USER",
                    )
                ),
        )
    }

    private fun commercial(): CommercialEvidencePortabilitySnapshot =
        CommercialEvidencePortabilitySnapshot(
            schemaVersion = 1,
            sourceWatermark = "a".repeat(64),
            records =
                listOf(
                    PortableCommercialObservation(
                        productKey = "milk",
                        merchant = "sample-market",
                        currencyCode = "ARS",
                        amountMinor = 2_000L,
                        sourceKind = "MANUAL",
                        sourceRef = "manual:ticket",
                        observedAtEpochMillis = 120L,
                        acquiredAtEpochMillis = 130L,
                    )
                ),
        )

    private fun web(): WebEvidencePortabilitySnapshot =
        WebEvidencePortabilitySnapshot(
            schemaVersion = 2,
            sourceWatermark = "b".repeat(64),
            fetchRecords =
                listOf(
                    PortableWebFetchRecord(
                        requestedUrl = "https://example.com/a",
                        finalUrl = "https://example.com/a",
                        method = "GET",
                        statusCode = 200,
                        mediaType = "text/plain",
                        redirectCount = 0,
                        acquiredAtEpochMillis = 140L,
                        contentSha256Hex = "d".repeat(64),
                        contentBytes = 12,
                    )
                ),
            searchRecords =
                listOf(
                    PortableWebSearchRecord(
                        query = "milk price",
                        maxResults = 1,
                        providerId = "sample-provider",
                        acquiredAtEpochMillis = 150L,
                        hits =
                            listOf(
                                PortableWebSearchHit(
                                    title = "Milk \"offer\"",
                                    url = "https://example.com/milk",
                                    snippet = "line one\nline two",
                                    score = 0.9,
                                )
                            ),
                    )
                ),
        )

    private fun notices(): AuxiliaryPortabilitySnapshot =
        AuxiliaryPortabilitySnapshot(
            schemaVersion = 2,
            sourceWatermark = "c".repeat(64),
            notices =
                listOf(
                    PortableNoticeRecord(
                        noticeKey = "notice-key-a7",
                        occurrenceRef = "occurrence:a7",
                        materiality = "NOTICE",
                        sensitivity = "SENSITIVE",
                        summary = "sensitive notice",
                        relevance = "OPEN",
                        attention = "UNSEEN",
                        revision = 1L,
                        resolutionReason = null,
                    )
                ),
        )
}
