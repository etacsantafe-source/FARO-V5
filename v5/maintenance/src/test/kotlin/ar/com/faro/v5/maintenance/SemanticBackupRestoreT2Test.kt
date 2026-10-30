package ar.com.faro.v5.maintenance

import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceId
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.EvidenceContentTrust
import ar.com.faro.v5.evidence.WebEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.WebEvidenceId
import ar.com.faro.v5.evidence.WebEvidenceRecord
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.evidence.WebFetchMethod
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.store.reality.RealityCommittedReceiptBackup
import ar.com.faro.v5.store.reality.RealityMigrationAccount
import ar.com.faro.v5.store.reality.RealityMigrationEntry
import ar.com.faro.v5.store.reality.RealitySemanticMigrationData
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SemanticBackupRestoreT2Test {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `capture is quiesced and restore validates semantic set while stripping Class D`() {
        val sourceRoot = tempDir.resolve("source")
        Files.createDirectories(sourceRoot)
        val quiescence = RecordingQuiescence()
        val backupSet =
            RealityStore(sourceRoot.resolve("reality.db").toString()).use { reality ->
                CommercialEvidenceStore(sourceRoot.resolve("commercial.db").toString()).use { commercial ->
                    WebEvidenceStore(sourceRoot.resolve("web.db").toString()).use { web ->
                        AuxiliaryDeliveryStore
                            .open(sourceRoot.resolve("auxiliary.db").toString())
                            .use { auxiliary ->
                                seedSource(reality, commercial, web, auxiliary)

                                assertEquals(1L, auxiliary.maintenance.inspect().deliveryIntentCount)
                                SemanticBackupCaptureCoordinator(
                                    quiescence = quiescence,
                                    reality = reality.backup,
                                    commercialEvidence = commercial.backup,
                                    webEvidence = web.backup,
                                    auxiliary = auxiliary.backup,
                                ).capture(
                                    operationId = MaintenanceOperationId("backup-capture-main"),
                                    backupSetId = BackupSetId("backup-main"),
                                    sourceGenerationId = StorageGenerationId("generation-source"),
                                    productVersion = "5-test",
                                    createdAtEpochMillis = 1_000L,
                                )
                            }
                    }
                }
            }

        assertEquals(1, quiescence.calls)
        assertEquals(BackupClassPolicy.decisions, backupSet.manifest.classDecisions)
        assertFalse(
            backupSet.manifest.classDecisions
                .single { it.contentClass == BackupContentClass.DELIVERY_INTENTS }
                .included
        )
        assertTrue(backupSet.manifest.restoreReplayFenceRequired)
        assertEquals(1, backupSet.auxiliary.notices.size)

        val target = openTarget("main-target", "restore-main")
        target.use { fixture ->
            val result = fixture.coordinator().restore(fixture.operationId, backupSet)
            val validated =
                assertInstanceOf(SemanticRestoreResult.Validated::class.java, result)

            assertEquals(fixture.targetGenerationId, validated.targetGenerationId)
            assertEquals(backupSet.manifest.backupSetId, validated.backupSetId)
            assertEquals(BootstrapPhase.VALIDATED, fixture.journal.read(fixture.operationId)?.phase)
            assertEquals(backupSet.reality, fixture.reality.backup.snapshot())
            assertEquals(backupSet.commercialEvidence, fixture.commercial.backup.snapshot())
            assertEquals(backupSet.webEvidence, fixture.web.backup.snapshot())
            assertEquals(backupSet.auxiliary, fixture.auxiliary.backup.snapshot())
            assertEquals(0L, fixture.reality.maintenance.inspect().outboxEffectCount)
            assertEquals(0L, fixture.auxiliary.maintenance.inspect().deliveryIntentCount)

            val replay =
                assertInstanceOf(
                    SemanticRestoreResult.Validated::class.java,
                    fixture.coordinator().restore(fixture.operationId, backupSet),
                )
            assertTrue(replay.recoveredFromExistingTarget)
            assertEquals(BootstrapPhase.VALIDATED, fixture.journal.read(fixture.operationId)?.phase)
        }
    }

    @Test
    fun `crash after Reality restore keeps journal staged and deterministic retry completes target`() {
        val backupSet = sampleBackupSet()
        val target = openTarget("crash-target", "restore-crash")

        target.use { fixture ->
            val crashing =
                fixture.coordinator { point ->
                    if (point == SemanticRestoreFaultPoint.AFTER_REALITY) {
                        throw SemanticRestoreInjectedCrash("injected process death")
                    }
                }

            assertThrows(SemanticRestoreInjectedCrash::class.java) {
                crashing.restore(fixture.operationId, backupSet)
            }
            assertEquals(BootstrapPhase.STAGED, fixture.journal.read(fixture.operationId)?.phase)
            assertEquals(backupSet.reality, fixture.reality.backup.snapshot())
            assertEquals(0L, fixture.commercial.maintenance.inspect().recordCount)

            val retry =
                assertInstanceOf(
                    SemanticRestoreResult.Validated::class.java,
                    fixture.coordinator().restore(fixture.operationId, backupSet),
                )
            assertTrue(retry.recoveredFromExistingTarget)
            assertEquals(BootstrapPhase.VALIDATED, fixture.journal.read(fixture.operationId)?.phase)
            assertEquals(backupSet.commercialEvidence, fixture.commercial.backup.snapshot())
            assertEquals(backupSet.webEvidence, fixture.web.backup.snapshot())
            assertEquals(backupSet.auxiliary, fixture.auxiliary.backup.snapshot())
        }
    }

    @Test
    fun `tampered manifest fails closed before touching staged target`() {
        val valid = sampleBackupSet()
        val tampered =
            valid.copy(
                manifest =
                    valid.manifest.copy(
                        setFingerprint = "0".repeat(64),
                    )
            )
        val target = openTarget("tamper-target", "restore-tamper")

        target.use { fixture ->
            val blocked =
                assertInstanceOf(
                    SemanticRestoreResult.Blocked::class.java,
                    fixture.coordinator().restore(fixture.operationId, tampered),
                )
            assertEquals(SemanticRestoreBlockReason.INVALID_BACKUP_SET, blocked.reason)
            assertEquals(
                SemanticRestoreTargetDisposition.NO_ACTIVATION,
                blocked.targetDisposition,
            )
            assertEquals(BootstrapPhase.STAGED, fixture.journal.read(fixture.operationId)?.phase)
            assertTrue(fixture.reality.maintenance.inspect().freshForSemanticMigration)
            assertEquals(0L, fixture.commercial.maintenance.inspect().recordCount)
            assertEquals(0, fixture.web.backup.snapshot().fetchRecords.size)
            assertEquals(0L, fixture.auxiliary.maintenance.inspect().noticeCount)
        }
    }

    private fun sampleBackupSet(): SemanticBackupSet {
        val reality = RealityStore(":memory:")
        val commercial = CommercialEvidenceStore(":memory:")
        val web = WebEvidenceStore(":memory:")
        val auxiliary = AuxiliaryDeliveryStore.open(":memory:")
        return try {
            seedSource(reality, commercial, web, auxiliary)
            SemanticBackupCaptureCoordinator(
                quiescence = RecordingQuiescence(),
                reality = reality.backup,
                commercialEvidence = commercial.backup,
                webEvidence = web.backup,
                auxiliary = auxiliary.backup,
            ).capture(
                operationId = MaintenanceOperationId("backup-sample"),
                backupSetId = BackupSetId("backup-sample"),
                sourceGenerationId = StorageGenerationId("generation-source"),
                productVersion = "5-test",
                createdAtEpochMillis = 2_000L,
            )
        } finally {
            auxiliary.close()
            web.close()
            commercial.close()
            reality.close()
        }
    }

    private fun seedSource(
        reality: RealityStore,
        commercial: CommercialEvidenceStore,
        web: WebEvidenceStore,
        auxiliary: AuxiliaryDeliveryStore,
    ) {
        reality.backup.restoreOrVerify(
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
                                    entryId = "expense-1",
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
                            receiptId = "receipt-backup-1",
                            transactionId = "tx-backup-1",
                            planFingerprint = "plan-backup-1",
                        )
                    ),
            )
        )

        commercial.backup.restoreOrVerify(
            CommercialEvidenceBackupSnapshot(
                schemaVersion = 1,
                records =
                    listOf(
                        CommercialEvidenceRecord(
                            evidenceId = CommercialEvidenceId("commercial-1"),
                            idempotencyKey = IdempotencyKey("commercial-idem-1"),
                            payloadFingerprint = PayloadFingerprint("commercial-payload-1"),
                            observation =
                                CommercialPriceObservation(
                                    productKey = CommercialProductKey("milk"),
                                    merchant = CommercialMerchant("market"),
                                    currencyCode = CommercialCurrencyCode("ARS"),
                                    amountMinor = 2_000L,
                                    sourceKind = CommercialEvidenceSourceKind.MANUAL,
                                    sourceRef = "manual:fixture",
                                    observedAtEpochMillis = 120L,
                                ),
                            acquiredAtEpochMillis = 130L,
                        )
                    ),
            )
        )

        web.backup.restoreOrVerify(
            WebEvidenceBackupSnapshot(
                schemaVersion = 2,
                fetchRecords =
                    listOf(
                        WebEvidenceRecord(
                            evidenceId = WebEvidenceId("web-1"),
                            idempotencyKey = IdempotencyKey("web-idem-1"),
                            payloadFingerprint = PayloadFingerprint("web-payload-1"),
                            requestedUrl = "https://example.com/a",
                            finalUrl = "https://example.com/a",
                            method = WebFetchMethod.GET,
                            statusCode = 200,
                            mediaType = "text/plain",
                            redirectCount = 0,
                            acquiredAtEpochMillis = 140L,
                            contentSha256Hex = "b".repeat(64),
                            contentBytes = 12,
                            policyVersion = "policy-1",
                            fetcherVersion = "fetcher-1",
                            trust = EvidenceContentTrust.UNTRUSTED_CONTENT,
                        )
                    ),
                searchRecords = emptyList(),
            )
        )

        auxiliary.route(
            candidate =
                NoticeCandidate(
                    noticeKey = NoticeKey.of("notice-key-1"),
                    sourceRef = SourceRef.of("watch:1"),
                    occurrenceRef = OccurrenceRef.of("occurrence:1"),
                    materiality = NoticeMateriality.NOTICE,
                    sensitivity = NoticeSensitivity.NORMAL,
                    summary = NoticeSummary.of("Backup notice"),
                    destinationRef = DestinationRef.of("chat:main"),
                ),
            newIds =
                NoticeMaterializationIds(
                    noticeId = NoticeId.of("notice-1"),
                    deliveryIntentId = DeliveryIntentId.of("delivery-1"),
                    effectId = EffectId("effect-delivery-1"),
                ),
        )
    }

    private fun openTarget(
        rootName: String,
        operationName: String,
    ): TargetFixture {
        val storageRoot = tempDir.resolve(rootName)
        val generationLayout = FileStorageGenerationLayout(storageRoot)
        val targetGenerationId = StorageGenerationId("generation-" + operationName)
        val staged =
            generationLayout.stage(
                StorageGenerationDescriptor(
                    generationId = targetGenerationId,
                    schemaSetFingerprint = "backup-target-schema-v1",
                    createdAtEpochMillis = 3_000L,
                )
            )
        val journal = FileBootstrapJournal(storageRoot)
        val operationId = MaintenanceOperationId(operationName)
        journal.create(
            BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = StorageGenerationId("generation-before"),
                targetGenerationId = targetGenerationId,
                phase = BootstrapPhase.STAGED,
            )
        )
        return TargetFixture(
            generationLayout = generationLayout,
            journal = journal,
            operationId = operationId,
            targetGenerationId = targetGenerationId,
            reality = RealityStore(staged.directory.resolve("reality.db").toString()),
            commercial =
                CommercialEvidenceStore(staged.directory.resolve("commercial.db").toString()),
            web = WebEvidenceStore(staged.directory.resolve("web.db").toString()),
            auxiliary =
                AuxiliaryDeliveryStore.open(staged.directory.resolve("auxiliary.db").toString()),
        )
    }

    private class RecordingQuiescence : MaterialWriteQuiescencePort {
        var calls: Int = 0
            private set

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            calls += 1
            return block()
        }
    }

    private data class TargetFixture(
        val generationLayout: FileStorageGenerationLayout,
        val journal: FileBootstrapJournal,
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId,
        val reality: RealityStore,
        val commercial: CommercialEvidenceStore,
        val web: WebEvidenceStore,
        val auxiliary: AuxiliaryDeliveryStore,
    ) : AutoCloseable {
        fun coordinator(
            faultInjector: (SemanticRestoreFaultPoint) -> Unit = {},
        ): SemanticBackupRestoreCoordinator =
            SemanticBackupRestoreCoordinator(
                journal = journal,
                generationLayout = generationLayout,
                reality = reality.backup,
                commercialEvidence = commercial.backup,
                webEvidence = web.backup,
                auxiliary = auxiliary.backup,
                faultInjector = faultInjector,
            )

        override fun close() {
            auxiliary.close()
            web.close()
            commercial.close()
            reality.close()
        }
    }
}
