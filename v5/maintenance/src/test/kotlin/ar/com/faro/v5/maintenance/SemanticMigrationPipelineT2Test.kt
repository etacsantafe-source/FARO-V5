package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceMaintenanceInspection
import ar.com.faro.v5.evidence.CommercialEvidenceMaintenancePort
import ar.com.faro.v5.evidence.CommercialEvidenceMigrationRecord
import ar.com.faro.v5.evidence.CommercialEvidenceSemanticMigrationPort
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.auxiliary.AuxiliaryMaintenanceInspection
import ar.com.faro.v5.store.auxiliary.AuxiliaryMaintenancePort
import ar.com.faro.v5.store.reality.RealityMigrationAccount
import ar.com.faro.v5.store.reality.RealityMigrationEntry
import ar.com.faro.v5.store.reality.RealitySemanticMigrationData
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SemanticMigrationPipelineT2Test {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `semantic migration validates staged generation preserves manifest survives reopen and replays`() {
        val storageRoot = tempDir.resolve("main-storage")
        val generationLayout = FileStorageGenerationLayout(storageRoot)
        val targetGenerationId = StorageGenerationId("generation-new")
        val staged =
            generationLayout.stage(
                StorageGenerationDescriptor(
                    generationId = targetGenerationId,
                    schemaSetFingerprint = "fixture-schema-set-v1",
                    createdAtEpochMillis = 1L,
                )
            )
        val realityPath = staged.directory.resolve("reality.db")
        val evidencePath = staged.directory.resolve("evidence.db")
        val auxiliaryPath = staged.directory.resolve("auxiliary.db")
        val journal = FileBootstrapJournal(storageRoot)
        val operationId = MaintenanceOperationId("migration-main")
        journal.create(stagedEntry(operationId, targetGenerationId))
        val source = sampleSource()

        RealityStore(realityPath.toString()).use { reality ->
            CommercialEvidenceStore(evidencePath.toString()).use { evidence ->
                AuxiliaryDeliveryStore.open(auxiliaryPath.toString()).use { auxiliary ->
                    val pipeline =
                        SemanticMigrationPipeline(
                            journal = journal,
                            generationLayout = generationLayout,
                            realityMaintenance = reality.maintenance,
                            realityMigration = reality.semanticMigration,
                            commercialMaintenance = evidence.maintenance,
                            commercialMigration = evidence.semanticMigration,
                            auxiliaryMaintenance = auxiliary.maintenance,
                        )

                    val result = pipeline.migrate(operationId, source)
                    val validated =
                        assertInstanceOf(
                            SemanticMigrationResult.Validated::class.java,
                            result,
                        )

                    assertEquals(operationId, validated.operationId)
                    assertEquals(targetGenerationId, validated.targetGenerationId)
                    assertFalse(validated.recoveredFromExistingTarget)
                    assertEquals(validated.sourceManifest, validated.targetManifest)
                    assertEquals(
                        listOf(
                            FinanceSemanticAggregate(
                                accountId = "cash-ars",
                                currencyCode = "ARS",
                                ledgerMinor = 750L,
                            )
                        ),
                        validated.targetManifest.financeAggregates,
                    )
                    assertEquals(
                        listOf(
                            FinanceCurrencySemanticAggregate(
                                currencyCode = "ARS",
                                ledgerMinor = 750L,
                            )
                        ),
                        validated.targetManifest.financeCurrencyAggregates,
                    )
                    assertEquals(
                        BootstrapPhase.VALIDATED,
                        journal.read(operationId)?.phase,
                    )
                    assertTrue(reality.maintenance.inspect().foreignKeysHealthy)
                    assertTrue(evidence.maintenance.inspect().foreignKeysHealthy)
                    assertTrue(auxiliary.maintenance.inspect().foreignKeysHealthy)
                    assertTrue(auxiliary.maintenance.inspect().freshForSemanticMigration)

                    val replay =
                        assertInstanceOf(
                            SemanticMigrationResult.Validated::class.java,
                            pipeline.migrate(operationId, source),
                        )
                    assertTrue(replay.recoveredFromExistingTarget)
                    assertEquals(validated.targetManifest, replay.targetManifest)
                    assertEquals(
                        BootstrapPhase.VALIDATED,
                        journal.read(operationId)?.phase,
                    )
                }
            }
        }

        RealityStore(realityPath.toString()).use { reopenedReality ->
            CommercialEvidenceStore(evidencePath.toString()).use { reopenedEvidence ->
                AuxiliaryDeliveryStore.open(auxiliaryPath.toString()).use { reopenedAuxiliary ->
                    val realitySnapshot = reopenedReality.semanticMigration.snapshot()
                    val evidenceSnapshot = reopenedEvidence.semanticMigration.snapshot()

                    assertEquals("Sample User", realitySnapshot.declaredDisplayName)
                    assertEquals(1, realitySnapshot.accounts.size)
                    assertEquals(1, realitySnapshot.entries.size)
                    assertEquals(1, evidenceSnapshot.size)
                    assertEquals(
                        "a".repeat(64),
                        evidenceSnapshot.single().sourceIdentityFingerprint,
                    )
                    assertTrue(reopenedReality.maintenance.inspect().integrityHealthy)
                    assertTrue(reopenedReality.maintenance.inspect().foreignKeysHealthy)
                    assertTrue(reopenedEvidence.maintenance.inspect().integrityHealthy)
                    assertTrue(reopenedEvidence.maintenance.inspect().foreignKeysHealthy)
                    assertTrue(reopenedAuxiliary.maintenance.inspect().freshForSemanticMigration)
                }
            }
        }
    }

    @Test
    fun `staged target written before crash is recovered by semantic parity and journal advance`() {
        val source = sampleSource()
        val storageRoot = tempDir.resolve("recovery-storage")
        val generationLayout = FileStorageGenerationLayout(storageRoot)
        val targetGenerationId = StorageGenerationId("generation-recovery")
        val staged =
            generationLayout.stage(
                StorageGenerationDescriptor(
                    generationId = targetGenerationId,
                    schemaSetFingerprint = "fixture-schema-set-v1",
                    createdAtEpochMillis = 2L,
                )
            )
        val journal = FileBootstrapJournal(storageRoot)
        val operationId = MaintenanceOperationId("migration-recovery")
        journal.create(stagedEntry(operationId, targetGenerationId))

        RealityStore(staged.directory.resolve("reality.db").toString()).use { reality ->
            CommercialEvidenceStore(staged.directory.resolve("evidence.db").toString()).use { evidence ->
                AuxiliaryDeliveryStore
                    .open(staged.directory.resolve("auxiliary.db").toString())
                    .use { auxiliary ->
                        reality.semanticMigration.importFresh(source.reality)
                        evidence.semanticMigration.importFresh(source.commercialEvidence)

                        val result =
                            SemanticMigrationPipeline(
                                journal = journal,
                                generationLayout = generationLayout,
                                realityMaintenance = reality.maintenance,
                                realityMigration = reality.semanticMigration,
                                commercialMaintenance = evidence.maintenance,
                                commercialMigration = evidence.semanticMigration,
                                auxiliaryMaintenance = auxiliary.maintenance,
                            ).migrate(operationId, source)

                        val validated =
                            assertInstanceOf(
                                SemanticMigrationResult.Validated::class.java,
                                result,
                            )
                        assertTrue(validated.recoveredFromExistingTarget)
                        assertEquals(
                            BootstrapPhase.VALIDATED,
                            journal.read(operationId)?.phase,
                        )
                        assertEquals(validated.sourceManifest, validated.targetManifest)
                    }
            }
        }
    }

    @Test
    fun `failure after Reality write aborts journal and requires discarding staged target`() {
        val storageRoot = tempDir.resolve("failure-storage")
        val generationLayout = FileStorageGenerationLayout(storageRoot)
        val targetGenerationId = StorageGenerationId("generation-failure")
        val staged =
            generationLayout.stage(
                StorageGenerationDescriptor(
                    generationId = targetGenerationId,
                    schemaSetFingerprint = "fixture-schema-set-v1",
                    createdAtEpochMillis = 3L,
                )
            )
        val journal = FileBootstrapJournal(storageRoot)
        val operationId = MaintenanceOperationId("migration-failure")
        journal.create(stagedEntry(operationId, targetGenerationId))

        RealityStore(staged.directory.resolve("reality.db").toString()).use { reality ->
            val failingCommercial =
                object : CommercialEvidenceSemanticMigrationPort {
                    override fun snapshot(): List<CommercialEvidenceMigrationRecord> =
                        emptyList()

                    override fun importFresh(
                        records: List<CommercialEvidenceMigrationRecord>,
                    ): List<CommercialEvidenceMigrationRecord> =
                        error("injected commercial migration failure")
                }

            val result =
                SemanticMigrationPipeline(
                    journal = journal,
                    generationLayout = generationLayout,
                    realityMaintenance = reality.maintenance,
                    realityMigration = reality.semanticMigration,
                    commercialMaintenance =
                        CommercialEvidenceMaintenancePort {
                            CommercialEvidenceMaintenanceInspection(
                                schemaVersion = 1,
                                integrityHealthy = true,
                                foreignKeysHealthy = true,
                                recordCount = 0L,
                            )
                        },
                    commercialMigration = failingCommercial,
                    auxiliaryMaintenance =
                        AuxiliaryMaintenancePort {
                            AuxiliaryMaintenanceInspection(
                                schemaVersion = 1,
                                integrityHealthy = true,
                                foreignKeysHealthy = true,
                                noticeCount = 0L,
                                deliveryIntentCount = 0L,
                            )
                        },
                ).migrate(operationId, sampleSource())

            val blocked =
                assertInstanceOf(
                    SemanticMigrationResult.Blocked::class.java,
                    result,
                )
            assertEquals(SemanticMigrationBlockReason.IMPORT_FAILED, blocked.reason)
            assertEquals(
                SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
                blocked.targetDisposition,
            )
            assertEquals(
                BootstrapPhase.ABORTED_PRE_SWITCH,
                journal.read(operationId)?.phase,
            )
            assertFalse(reality.maintenance.inspect().freshForSemanticMigration)
        }
    }

    @Test
    fun `journal cannot validate a generation that is not physically staged`() {
        val storageRoot = tempDir.resolve("missing-generation-storage")
        val generationLayout = FileStorageGenerationLayout(storageRoot)
        val journal = FileBootstrapJournal(storageRoot)
        val operationId = MaintenanceOperationId("migration-missing-generation")
        val targetGenerationId = StorageGenerationId("generation-missing")
        journal.create(stagedEntry(operationId, targetGenerationId))

        RealityStore(":memory:").use { reality ->
            CommercialEvidenceStore(":memory:").use { evidence ->
                AuxiliaryDeliveryStore.open(":memory:").use { auxiliary ->
                    val result =
                        SemanticMigrationPipeline(
                            journal = journal,
                            generationLayout = generationLayout,
                            realityMaintenance = reality.maintenance,
                            realityMigration = reality.semanticMigration,
                            commercialMaintenance = evidence.maintenance,
                            commercialMigration = evidence.semanticMigration,
                            auxiliaryMaintenance = auxiliary.maintenance,
                        ).migrate(operationId, sampleSource())

                    val blocked =
                        assertInstanceOf(
                            SemanticMigrationResult.Blocked::class.java,
                            result,
                        )
                    assertEquals(
                        SemanticMigrationBlockReason.TARGET_GENERATION_NOT_STAGED,
                        blocked.reason,
                    )
                    assertEquals(
                        SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
                        blocked.targetDisposition,
                    )
                    assertEquals(
                        BootstrapPhase.ABORTED_PRE_SWITCH,
                        journal.read(operationId)?.phase,
                    )
                }
            }
        }
    }

    @Test
    fun `partial crash target is never completed blindly and semantic mismatch aborts generation`() {
        val source = sampleSource()
        val storageRoot = tempDir.resolve("partial-storage")
        val generationLayout = FileStorageGenerationLayout(storageRoot)
        val targetGenerationId = StorageGenerationId("generation-partial")
        val staged =
            generationLayout.stage(
                StorageGenerationDescriptor(
                    generationId = targetGenerationId,
                    schemaSetFingerprint = "fixture-schema-set-v1",
                    createdAtEpochMillis = 4L,
                )
            )
        val journal = FileBootstrapJournal(storageRoot)
        val operationId = MaintenanceOperationId("migration-partial")
        journal.create(stagedEntry(operationId, targetGenerationId))

        RealityStore(staged.directory.resolve("reality.db").toString()).use { reality ->
            CommercialEvidenceStore(staged.directory.resolve("evidence.db").toString()).use { evidence ->
                AuxiliaryDeliveryStore
                    .open(staged.directory.resolve("auxiliary.db").toString())
                    .use { auxiliary ->
                        reality.semanticMigration.importFresh(source.reality)

                        val result =
                            SemanticMigrationPipeline(
                                journal = journal,
                                generationLayout = generationLayout,
                                realityMaintenance = reality.maintenance,
                                realityMigration = reality.semanticMigration,
                                commercialMaintenance = evidence.maintenance,
                                commercialMigration = evidence.semanticMigration,
                                auxiliaryMaintenance = auxiliary.maintenance,
                            ).migrate(operationId, source)

                        val blocked =
                            assertInstanceOf(
                                SemanticMigrationResult.Blocked::class.java,
                                result,
                            )
                        assertEquals(
                            SemanticMigrationBlockReason.SEMANTIC_MISMATCH,
                            blocked.reason,
                        )
                        assertEquals(
                            SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
                            blocked.targetDisposition,
                        )
                        assertEquals(
                            BootstrapPhase.ABORTED_PRE_SWITCH,
                            journal.read(operationId)?.phase,
                        )
                        assertFalse(reality.maintenance.inspect().freshForSemanticMigration)
                        assertTrue(evidence.maintenance.inspect().freshForSemanticMigration)
                    }
            }
        }
    }

    private fun stagedEntry(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
    ): BootstrapJournalEntry =
        BootstrapJournalEntry(
            operationId = operationId,
            kind = MaintenanceOperationKind.MIGRATION,
            previousGenerationId = StorageGenerationId("generation-old"),
            targetGenerationId = targetGenerationId,
            phase = BootstrapPhase.STAGED,
        )

    private fun sampleSource(): NormalizedSemanticMigrationSource =
        NormalizedSemanticMigrationSource(
            sourceSetFingerprint = "fixture-v4-normalized-set",
            reality =
                RealitySemanticMigrationData(
                    declaredDisplayName = "Sample User",
                    accounts =
                        listOf(
                            RealityMigrationAccount(
                                accountId = "cash-ars",
                                displayName = "Cash",
                                currencyCode = "ARS",
                                nature = "CASH",
                                openingBalanceMinor = 1000L,
                            )
                        ),
                    entries =
                        listOf(
                            RealityMigrationEntry(
                                entryId = "entry-1",
                                accountId = "cash-ars",
                                amountMinor = 250L,
                                kind = "EXPENSE",
                                concept = "Groceries",
                                category = "Food",
                                effectiveAtEpochMillis = 100L,
                                recordedAtEpochMillis = 200L,
                                provenanceLabel = "LEGACY_MIGRATION",
                            )
                        ),
                ),
            commercialEvidence =
                listOf(
                    CommercialEvidenceMigrationRecord(
                        sourceIdentityFingerprint = "a".repeat(64),
                        productKey = "milk",
                        merchant = "sample-merchant",
                        currencyCode = "ARS",
                        amountMinor = 1250L,
                        sourceRef = "legacy:v4:commercial:fixture",
                        observedAtEpochMillis = 150L,
                        acquiredAtEpochMillis = 250L,
                    )
                ),
        )
}
