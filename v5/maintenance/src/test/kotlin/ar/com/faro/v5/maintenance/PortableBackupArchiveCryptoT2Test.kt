package ar.com.faro.v5.maintenance

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceBackupPort
import ar.com.faro.v5.evidence.CommercialEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceId
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.EvidenceContentTrust
import ar.com.faro.v5.evidence.WebEvidenceBackupPort
import ar.com.faro.v5.evidence.WebEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.WebEvidenceId
import ar.com.faro.v5.evidence.WebEvidenceRecord
import ar.com.faro.v5.evidence.WebFetchMethod
import ar.com.faro.v5.evidence.WebProviderUsage
import ar.com.faro.v5.evidence.WebSearchEvidenceRecord
import ar.com.faro.v5.evidence.WebSearchHit
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import ar.com.faro.v5.store.reality.RealityBackupPort
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.store.reality.RealityCommittedReceiptBackup
import ar.com.faro.v5.store.reality.RealityMigrationAccount
import ar.com.faro.v5.store.reality.RealityMigrationEntry
import ar.com.faro.v5.store.reality.RealitySemanticMigrationData
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortableBackupArchiveCryptoT2Test {
    private val passphrase = "correct horse battery staple".toCharArray()

    @Test
    fun `semantic backup codec and crypto round trip preserve exact A5 set`() {
        val source = sampleBackupSet()
        val codecBytes = SemanticBackupSetCodec.encode(source)
        assertEquals(source, SemanticBackupSetCodec.decode(codecBytes))

        val crypto = PortableBackupArchiveCrypto()
        val archive = crypto.seal(source, passphrase)
        val metadata = crypto.inspectUntrusted(archive)
        requireNotNull(metadata)

        assertEquals(1, metadata.envelopeVersion)
        assertEquals("PBKDF2WithHmacSHA256", metadata.kdf)
        assertEquals("AES/GCM/NoPadding", metadata.aead)
        assertEquals(PortableBackupArchiveCrypto.DEFAULT_PBKDF2_ITERATIONS, metadata.iterations)
        assertEquals(codecBytes.size, metadata.plaintextBytes)

        val visible = String(archive, StandardCharsets.ISO_8859_1)
        assertFalse(visible.contains("Backup User"))
        assertFalse(visible.contains("backup-set-a6"))
        assertFalse(visible.contains("milk"))

        val opened =
            assertInstanceOf(
                PortableBackupOpenResult.Opened::class.java,
                crypto.open(archive, passphrase),
            )
        assertEquals(source, opened.backupSet)
        codecBytes.fill(0)
    }

    @Test
    fun `same backup and passphrase produce randomized archives that both open`() {
        val source = sampleBackupSet()
        val crypto = PortableBackupArchiveCrypto()

        val first = crypto.seal(source, passphrase)
        val second = crypto.seal(source, passphrase)

        assertFalse(first.contentEquals(second))
        assertEquals(
            source,
            assertInstanceOf(
                PortableBackupOpenResult.Opened::class.java,
                crypto.open(first, passphrase),
            ).backupSet,
        )
        assertEquals(
            source,
            assertInstanceOf(
                PortableBackupOpenResult.Opened::class.java,
                crypto.open(second, passphrase),
            ).backupSet,
        )
    }

    @Test
    fun `wrong passphrase and authenticated tamper converge to authentication failure`() {
        val crypto = PortableBackupArchiveCrypto()
        val archive = crypto.seal(sampleBackupSet(), passphrase)

        assertRejected(
            PortableBackupOpenFailure.AUTHENTICATION_FAILED,
            crypto.open(archive, "wrong horse battery staple".toCharArray()),
        )

        val ciphertextTampered = archive.copyOf()
        ciphertextTampered[ciphertextTampered.lastIndex] =
            (ciphertextTampered.last().toInt() xor 0x01).toByte()
        assertRejected(
            PortableBackupOpenFailure.AUTHENTICATION_FAILED,
            crypto.open(ciphertextTampered, passphrase),
        )

        val saltTampered = archive.copyOf()
        saltTampered[40] = (saltTampered[40].toInt() xor 0x01).toByte()
        assertRejected(
            PortableBackupOpenFailure.AUTHENTICATION_FAILED,
            crypto.open(saltTampered, passphrase),
        )

        val workFactorTampered = archive.copyOf()
        ByteBuffer.wrap(workFactorTampered).putInt(20, 600_001)
        assertRejected(
            PortableBackupOpenFailure.INVALID_ENVELOPE,
            crypto.open(workFactorTampered, passphrase),
        )
    }

    @Test
    fun `truncation trailing bytes and hostile work factor fail before restore`() {
        val crypto = PortableBackupArchiveCrypto()
        val archive = crypto.seal(sampleBackupSet(), passphrase)

        assertRejected(
            PortableBackupOpenFailure.INVALID_ENVELOPE,
            crypto.open(archive.copyOf(archive.size - 1), passphrase),
        )

        val trailing = archive + byteArrayOf(0x00)
        assertRejected(
            PortableBackupOpenFailure.INVALID_ENVELOPE,
            crypto.open(trailing, passphrase),
        )

        val hostileIterations = archive.copyOf()
        ByteBuffer.wrap(hostileIterations).putInt(20, 20_000_000)
        assertRejected(
            PortableBackupOpenFailure.INVALID_ENVELOPE,
            crypto.open(hostileIterations, passphrase),
        )

        val unsupportedVersion = archive.copyOf()
        ByteBuffer.wrap(unsupportedVersion).putInt(8, 2)
        assertNull(crypto.inspectUntrusted(unsupportedVersion))
        assertRejected(
            PortableBackupOpenFailure.INVALID_ENVELOPE,
            crypto.open(unsupportedVersion, passphrase),
        )
    }

    @Test
    fun `plaintext codec rejects corruption and trailing bytes even without crypto layer`() {
        val source = sampleBackupSet()
        val encoded = SemanticBackupSetCodec.encode(source)

        val trailing = encoded + byteArrayOf(0x01)
        assertThrows(IllegalStateException::class.java) {
            SemanticBackupSetCodec.decode(trailing)
        }

        val corrupt = encoded.copyOf()
        corrupt[corrupt.lastIndex] = (corrupt.last().toInt() xor 0x01).toByte()
        assertThrows(Throwable::class.java) {
            SemanticBackupSetCodec.decode(corrupt)
        }
    }

    @Test
    fun `passphrase is bounded and never accepted blank or short`() {
        val crypto = PortableBackupArchiveCrypto()
        val source = sampleBackupSet()

        assertThrows(IllegalArgumentException::class.java) {
            crypto.seal(source, "too-short".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            crypto.seal(source, "                ".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            crypto.seal(source, "valid-passphrase\u0000".toCharArray())
        }
    }

    private fun assertRejected(
        expected: PortableBackupOpenFailure,
        result: PortableBackupOpenResult,
    ) {
        val rejected =
            assertInstanceOf(
                PortableBackupOpenResult.Rejected::class.java,
                result,
            )
        assertEquals(expected, rejected.failure)
    }

    private fun sampleBackupSet(): SemanticBackupSet {
        val reality =
            RealityBackupSnapshot(
                schemaVersion = 5,
                profileRevision = 4L,
                financeRevision = 7L,
                domain =
                    RealitySemanticMigrationData(
                        declaredDisplayName = "Backup User",
                        accounts =
                            listOf(
                                RealityMigrationAccount(
                                    accountId = "cash-ars",
                                    displayName = "Cash",
                                    currencyCode = "ARS",
                                    nature = "CASH",
                                    openingBalanceMinor = 10_000L,
                                )
                            ),
                        entries =
                            listOf(
                                RealityMigrationEntry(
                                    entryId = "entry-a6",
                                    accountId = "cash-ars",
                                    amountMinor = 1_250L,
                                    kind = "EXPENSE",
                                    concept = "Groceries",
                                    category = "Food",
                                    effectiveAtEpochMillis = 100L,
                                    recordedAtEpochMillis = 110L,
                                    provenanceLabel = "USER",
                                )
                            ),
                    ),
                committedReceipts =
                    listOf(
                        RealityCommittedReceiptBackup(
                            receiptId = "receipt-a6",
                            transactionId = "tx-a6",
                            planFingerprint = "plan-a6",
                        )
                    ),
            )

        val commercial =
            CommercialEvidenceBackupSnapshot(
                schemaVersion = 1,
                records =
                    listOf(
                        CommercialEvidenceRecord(
                            evidenceId = CommercialEvidenceId("commercial-a6"),
                            idempotencyKey = IdempotencyKey("commercial-idem-a6"),
                            payloadFingerprint = PayloadFingerprint("commercial-payload-a6"),
                            observation =
                                CommercialPriceObservation(
                                    productKey = CommercialProductKey("milk"),
                                    merchant = CommercialMerchant("sample-market"),
                                    currencyCode = CommercialCurrencyCode("ARS"),
                                    amountMinor = 2_000L,
                                    sourceKind = CommercialEvidenceSourceKind.MANUAL,
                                    sourceRef = "manual:a6",
                                    observedAtEpochMillis = 120L,
                                ),
                            acquiredAtEpochMillis = 130L,
                        )
                    ),
            )

        val web =
            WebEvidenceBackupSnapshot(
                schemaVersion = 2,
                fetchRecords =
                    listOf(
                        WebEvidenceRecord(
                            evidenceId = WebEvidenceId("web-fetch-a6"),
                            idempotencyKey = IdempotencyKey("web-fetch-idem-a6"),
                            payloadFingerprint = PayloadFingerprint("web-fetch-payload-a6"),
                            requestedUrl = "https://example.com/product",
                            finalUrl = "https://example.com/product",
                            method = WebFetchMethod.GET,
                            statusCode = 200,
                            mediaType = "text/plain",
                            redirectCount = 0,
                            acquiredAtEpochMillis = 140L,
                            contentSha256Hex = "a".repeat(64),
                            contentBytes = 32,
                            policyVersion = "policy-a6",
                            fetcherVersion = "fetcher-a6",
                            trust = EvidenceContentTrust.UNTRUSTED_CONTENT,
                        )
                    ),
                searchRecords =
                    listOf(
                        WebSearchEvidenceRecord(
                            evidenceId = WebEvidenceId("web-search-a6"),
                            idempotencyKey = IdempotencyKey("web-search-idem-a6"),
                            payloadFingerprint = PayloadFingerprint("web-search-payload-a6"),
                            query = "milk price",
                            maxResults = 1,
                            sensitivity = EgressSensitivity.PUBLIC,
                            providerId = "sample-provider",
                            providerRequestId = "request-a6",
                            providerUsage = WebProviderUsage("request", 1L),
                            acquiredAtEpochMillis = 150L,
                            policyVersion = "search-policy-a6",
                            adapterVersion = "adapter-a6",
                            hits =
                                listOf(
                                    WebSearchHit(
                                        title = "Milk",
                                        url = "https://example.com/milk",
                                        snippet = "Sample result",
                                        score = 0.9,
                                    )
                                ),
                        )
                    ),
            )

        val auxiliary =
            AuxiliaryBackupSnapshot(
                schemaVersion = 2,
                notices =
                    listOf(
                        NoticeSnapshot(
                            noticeId = NoticeId.of("notice-a6"),
                            noticeKey = NoticeKey.of("notice-key-a6"),
                            sourceRef = SourceRef.of("watch:a6"),
                            occurrenceRef = OccurrenceRef.of("occurrence:a6"),
                            materiality = NoticeMateriality.NOTICE,
                            sensitivity = NoticeSensitivity.NORMAL,
                            summary = NoticeSummary.of("Backup notice A6"),
                            destinationRef = DestinationRef.of("chat:main"),
                            relevance = NoticeRelevance.OPEN,
                            attention = NoticeAttention.UNSEEN,
                            revision = 1L,
                        )
                    ),
            )

        return SemanticBackupCaptureCoordinator(
            quiescence =
                object : MaterialWriteQuiescencePort {
                    override fun <T> withMaterialWritesQuiesced(
                        operationId: MaintenanceOperationId,
                        block: () -> T,
                    ): T = block()
                },
            reality = fixedReality(reality),
            commercialEvidence = fixedCommercial(commercial),
            webEvidence = fixedWeb(web),
            auxiliary = fixedAuxiliary(auxiliary),
        ).capture(
            operationId = MaintenanceOperationId("backup-a6-capture"),
            backupSetId = BackupSetId("backup-set-a6"),
            sourceGenerationId = StorageGenerationId("generation-a6-source"),
            productVersion = "5-a6-test",
            createdAtEpochMillis = 1_000L,
        )
    }

    private fun fixedReality(snapshot: RealityBackupSnapshot): RealityBackupPort =
        object : RealityBackupPort {
            override fun snapshot(): RealityBackupSnapshot = snapshot
            override fun restoreOrVerify(snapshot: RealityBackupSnapshot): RealityBackupSnapshot =
                error("not used by capture fixture")
        }

    private fun fixedCommercial(
        snapshot: CommercialEvidenceBackupSnapshot,
    ): CommercialEvidenceBackupPort =
        object : CommercialEvidenceBackupPort {
            override fun snapshot(): CommercialEvidenceBackupSnapshot = snapshot
            override fun restoreOrVerify(
                snapshot: CommercialEvidenceBackupSnapshot,
            ): CommercialEvidenceBackupSnapshot = error("not used by capture fixture")
        }

    private fun fixedWeb(snapshot: WebEvidenceBackupSnapshot): WebEvidenceBackupPort =
        object : WebEvidenceBackupPort {
            override fun snapshot(): WebEvidenceBackupSnapshot = snapshot
            override fun restoreOrVerify(
                snapshot: WebEvidenceBackupSnapshot,
            ): WebEvidenceBackupSnapshot = error("not used by capture fixture")
        }

    private fun fixedAuxiliary(snapshot: AuxiliaryBackupSnapshot): AuxiliaryBackupPort =
        object : AuxiliaryBackupPort {
            override fun snapshot(): AuxiliaryBackupSnapshot = snapshot
            override fun restoreOrVerify(
                snapshot: AuxiliaryBackupSnapshot,
            ): AuxiliaryBackupSnapshot = error("not used by capture fixture")
        }
}
