package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceId
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
import java.nio.ByteBuffer
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.SemanticBackupSetIntegrity
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5FiveStoreBackupCaptureT2Test {
    @TempDir lateinit var temp: Path

    private class Lease(
        val reject: Boolean = false,
    ) : MaterialWriteQuiescencePort {
        var calls = 0
        var released = 0
        var active = false

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            calls += 1
            if (reject) error("global write quiescence unavailable")
            check(!active) { "nested/non-global quiescence not allowed" }
            active = true
            try {
                return block()
            } finally {
                active = false
                released += 1
            }
        }
    }

    private fun openFive(name: String): FaroV5GenerationStoreGraph {
        val staged = FileStorageGenerationLayout(temp.resolve(name)).stageWorkspaceV2(
            StorageGenerationDescriptor(
                generationId = StorageGenerationId("generation-$name"),
                schemaSetFingerprint = "five-store-capture-native",
                createdAtEpochMillis = 1234,
            )
        )
        val dir = staged.directory
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        return FaroV5GenerationStoreGraph.openValidatedV2(staged)
    }

    /**
     * Test-only stage. This never activates a real app generation.
     * Create legacy Commercial first, then explicitly migrate it to Evidence2
     * and seal WAL before the five-store read-only preflight.
     */
    private fun stageFiveCommercialEvidence2(
        name: String,
        typed: Boolean = true,
    ): StagedStorageGeneration {
        val root = temp.resolve(name)
        val staged = FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(
                generationId = StorageGenerationId("generation-$name"),
                schemaSetFingerprint = "five-store-evidence2-staged-only",
                createdAtEpochMillis = 1234L,
            )
        )
        val dir = staged.directory
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        val commercialPath = dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()
        CommercialEvidenceStore(
            commercialPath,
            clock = { 150L },
            evidenceIdFactory = { CommercialEvidenceId("evidence-legacy") },
        ).use { legacy ->
            legacy.record(
                CommercialPriceObservation(
                    productKey = CommercialProductKey("rice-1kg"),
                    merchant = CommercialMerchant("local-market"),
                    currencyCode = CommercialCurrencyCode("ARS"),
                    amountMinor = 12300L,
                    sourceKind = CommercialEvidenceSourceKind.MANUAL,
                    sourceRef = "manual:legacy-ticket",
                    observedAtEpochMillis = 100L,
                ),
                IdempotencyKey("legacy-key"),
                PayloadFingerprint("a".repeat(64)),
            )
        }
        if (typed) {
            CommercialEvidenceStore(
                commercialPath,
                clock = { 250L },
                evidenceIdFactory = { CommercialEvidenceId("evidence-typed") },
                stagedTypedTermsV2 = true,
                stagedSemanticBackupV2 = true,
            ).use { evidence2 ->
                evidence2.recordWithTypedTerms(
                    CommercialPriceObservation(
                        productKey = CommercialProductKey("rice-1kg"),
                        merchant = CommercialMerchant("local-market"),
                        currencyCode = CommercialCurrencyCode("ARS"),
                        amountMinor = 11900L,
                        sourceKind = CommercialEvidenceSourceKind.TOOL,
                        sourceRef = "literal:store-flyer",
                        observedAtEpochMillis = 200L,
                    ),
                    CommercialEvidenceTypedTerms(
                        localityKind = CommercialLocalityKind.KNOWN,
                        localityCode = "AR-SF",
                        issuedAtEpochMillis = 190L,
                        validFromEpochMillis = 195L,
                        validUntilEpochMillis = 280L,
                        presentationQuantityThousandths = 1000L,
                        presentationUnit = CommercialPresentationUnit.GRAM,
                        packCount = 1,
                        paymentMethod = CommercialPaymentMethod.DEBIT,
                        eligibility = CommercialEligibility.GENERAL,
                        conditions = "only with membership",
                        promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
                        promotionValue = 1200L,
                        promotionSourceRef = "literal:flyer",
                    ),
                    IdempotencyKey("typed-key"),
                    PayloadFingerprint("b".repeat(64)),
                )
                assertTrue(evidence2.maintenance.inspect().integrityHealthy)
            }
        }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        return staged
    }

    private class SharedGateLease(
        private val gate: SingleProcessMaterialWriteGate,
        private val reject: Boolean = false,
    ) : MaterialWriteQuiescencePort {
        var calls = 0
        var released = 0
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = gate.withQuiescedMaterialWrites {
            calls += 1
            if (reject) error("staged global write quiescence denied")
            try { block() } finally { released += 1 }
        }
    }

    @Test fun evidence2StagedFiveStoreCaptureFbs2RoundtripRetainsTypedAndLegacy() {
        val staged = stageFiveCommercialEvidence2("e2-capture")
        val gate = SingleProcessMaterialWriteGate()
        FaroV5GenerationStoreGraph.openValidatedV2Evidence2StagedDev(staged, gate).use { graph ->
            val lease = SharedGateLease(gate)
            val set = FaroV5FiveStoreBackupCapture(graph, lease).capture(
                operationId = MaintenanceOperationId("e2-capture"),
                backupSetId = BackupSetId("e2-backup"),
                productVersion = "5-evidence2-staged",
                createdAtEpochMillis = 1234L,
            )
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
            assertTrue(graph.verifyHealthy())
            assertEquals(2, graph.commercialEvidence.maintenance.inspect().schemaVersion)
            assertEquals(2, set.fourStore.commercialEvidence.schemaVersion)
            assertEquals(graph.commercialEvidence.backup.snapshot(),
                set.fourStore.commercialEvidence)
            val typed = checkNotNull(set.fourStore.commercialEvidence.stagedTyped)
            assertEquals(listOf("evidence-legacy", "evidence-typed"),
                typed.rows.map { it.record.evidenceId.value })
            assertNull(typed.rows[0].terms)
            assertEquals(CommercialEvidenceTypedTerms.UNKNOWN, typed.rows[0].effectiveTerms)
            assertEquals("AR-SF", typed.rows[1].terms?.localityCode)
            assertEquals("literal:store-flyer", typed.rows[1].record.observation.sourceRef)
            assertEquals("ARS", typed.rows[1].record.observation.currencyCode.value)
            val bytes = FaroV5FiveStoreSemanticCodec.encode(set)
            assertEquals(set, FaroV5FiveStoreSemanticCodec.decode(bytes))
            assertTrue(SemanticBackupSetIntegrity.matches(set.fourStore))
            val tampered = set.copy(fourStore = set.fourStore.copy(
                commercialEvidence = set.fourStore.commercialEvidence.copy(
                    stagedTyped = typed.copy(rows = typed.rows.mapIndexed { index, row ->
                        if (index == 1) row.copy(
                            terms = checkNotNull(row.terms).copy(validUntilEpochMillis = 290L)
                        ) else row
                    }),
                ),
            ))
            assertFalse(SemanticBackupSetIntegrity.matches(tampered.fourStore))
            assertThrows(IllegalStateException::class.java) {
                FaroV5FiveStoreSemanticCodec.encode(tampered)
            }
        }
    }

    @Test fun legacyV2GraphRejectsSealedSchema2BeforeOpeningOwners() {
        val staged = stageFiveCommercialEvidence2("e2-legacy-reject")
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged).use { }
        }
        assertFalse(Files.exists(staged.directory.resolve(
            GenerationStoreFiles.COMMERCIAL_EVIDENCE + "-wal")))
        assertFalse(Files.exists(staged.directory.resolve(
            GenerationStoreFiles.COMMERCIAL_EVIDENCE + "-shm")))
    }

    @Test fun explicitEvidence2StageFactoryRejectsLegacySchema1WithoutMigration() {
        val staged = stageFiveCommercialEvidence2("e2-reject-v1", typed = false)
        val gate = SingleProcessMaterialWriteGate()
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Evidence2StagedDev(staged, gate).use { }
        }
        FaroV5GenerationStoreGraph.openValidatedV2(staged).use { legacy ->
            assertEquals(1, legacy.commercialEvidence.maintenance.inspect().schemaVersion)
            assertTrue(legacy.verifyHealthy())
        }
    }

    @Test fun explicitEvidence2StageFactoryRejectsSimulatedActivePointer() {
        val name = "e2-active-deny"
        val staged = stageFiveCommercialEvidence2(name)
        val pointer = FileActiveGenerationPointer(temp.resolve(name))
        val active = pointer.compareAndSet(null, staged.descriptor.generationId)
        assertEquals(staged.descriptor.generationId, active?.generationId)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Evidence2StagedDev(
                staged, SingleProcessMaterialWriteGate(),
            ).use { }
        }
    }

    @Test fun stagedEvidence2QuiescenceDenyAndHotWalFailClosed() {
        val staged = stageFiveCommercialEvidence2("e2-quiescence")
        val gate = SingleProcessMaterialWriteGate()
        FaroV5GenerationStoreGraph.openValidatedV2Evidence2StagedDev(staged, gate).use { graph ->
            val before = graph.commercialEvidence.stagedTypedBackup.snapshot()
            val lease = SharedGateLease(gate, reject = true)
            assertThrows(IllegalStateException::class.java) {
                capture(graph, Lease(reject = true))
            }
            assertThrows(IllegalStateException::class.java) {
                FaroV5FiveStoreBackupCapture(graph, lease).capture(
                    operationId = MaintenanceOperationId("e2-rejected"),
                    backupSetId = BackupSetId("e2-rejected-backup"),
                    productVersion = "5-e2-rejected",
                    createdAtEpochMillis = 1234L,
                )
            }
            assertEquals(1, lease.calls)
            assertEquals(0, lease.released)
            assertEquals(before, graph.commercialEvidence.stagedTypedBackup.snapshot())
            assertTrue(graph.verifyHealthy())
        }
        Files.writeString(
            staged.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE + "-wal"),
            "uncheckpointed",
        )
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2Evidence2StagedDev(
                staged, SingleProcessMaterialWriteGate(),
            ).use { }
        }
    }

    private fun capture(
        graph: FaroV5GenerationStoreGraph,
        lease: Lease,
    ): FaroV5FiveStoreSemanticSet =
        FaroV5FiveStoreBackupCapture(graph, lease).capture(
            operationId = MaintenanceOperationId("five-store-capture-test"),
            backupSetId = BackupSetId("five-store-capture-backup"),
            productVersion = "5.0-capture-native",
            createdAtEpochMillis = 54321L,
        )

    @Test fun oneLeaseCapturesAllFiveOwnersFromExactGraphGeneration() {
        openFive("complete").use { graph ->
            val lease = Lease()
            val set = capture(graph, lease)
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
            assertFalse(lease.active)
            assertEquals(graph.generationId, set.fourStore.manifest.sourceGenerationId.value)
            assertEquals(graph.reality.backup.snapshot(), set.fourStore.reality)
            assertEquals(graph.commercialEvidence.backup.snapshot(), set.fourStore.commercialEvidence)
            assertEquals(graph.webEvidence.backup.snapshot(), set.fourStore.webEvidence)
            assertEquals(graph.auxiliary.backup.snapshot(), set.fourStore.auxiliary)
            assertEquals(graph.workspace!!.backup.snapshot(), set.workspace)
            // Separate semantic gate is already certified; this checks integration only.
            assertEquals(set, FaroV5FiveStoreSemanticCodec.decode(
                FaroV5FiveStoreSemanticCodec.encode(set),
            ))
        }
    }

    @Test fun failedGlobalQuiescenceDoesNotProducePartialAuthorityOrMutateStores() {
        openFive("unavailable").use { graph ->
            val before = graph.workspace!!.backup.snapshot()
            val lease = Lease(reject = true)
            assertThrows(IllegalStateException::class.java) {
                capture(graph, lease)
            }
            assertEquals(1, lease.calls)
            assertEquals(0, lease.released)
            assertFalse(lease.active)
            assertEquals(before, graph.workspace!!.backup.snapshot())
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun legacyFourStoreGraphCannotSilentlyExportIncompleteFiveStoreSet() {
        val dir = temp.resolve("legacy")
        Files.createDirectories(dir)
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }

        FaroV5GenerationStoreGraph.open(dir, "legacy-four").use { graph ->
            assertNull(graph.workspace)
            val lease = Lease()
            assertThrows(IllegalStateException::class.java) {
                capture(graph, lease)
            }
            assertEquals(0, lease.calls)
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun realObjectiveAndMatterOwnersCaptureInOneLeaseWithoutChangingFbs1() {
        openFive("g4-real").use { graph ->
            graph.reality.run(TransactionId("tx-g4-capture-real")) { scope ->
                graph.reality.objectivePersistence.upsert(
                    ObjectiveRecord(
                        ObjectiveId.of("goal-one"),
                        ObjectiveTitle.of("Reserva de emergencia"),
                        ObjectiveDetail.of("Monto mensual"),
                        ObjectiveStatus.ACTIVE, 100L,
                    ), scope,
                )
                graph.reality.matterPersistence.upsert(
                    MatterRecord(
                        MatterId.of("matter-one"),
                        MatterTitle.of("Revisar factura"),
                        MatterDetail.of("Vencimiento de servicio"),
                        MatterStatus.WAITING, 200L, 100L,
                    ), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertThrows(IllegalStateException::class.java) {
                graph.reality.backup.snapshot()
            }
            assertThrows(IllegalStateException::class.java) {
                graph.reality.semanticMigration.snapshot()
            }
            val lease = Lease()
            val set = capture(graph, lease)
            val expected = graph.reality.domainBackup.snapshot()
            assertEquals(expected, set.g4Domains)
            assertEquals(1L, expected.objectives.revision)
            assertEquals(1L, expected.matters.revision)
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
            assertFalse(lease.active)
            assertEquals(2, ByteBuffer.wrap(FaroV5FiveStoreSemanticCodec.encode(set)).getInt(4))
            assertEquals(set, FaroV5FiveStoreSemanticCodec.decode(
                FaroV5FiveStoreSemanticCodec.encode(set),
            ))
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun nonzeroG4RevisionWithoutRowsCannotFallBackToV1() {
        openFive("g4-revision").use { graph ->
            val original = graph.reality.domainBackup.snapshot()
            graph.reality.domainBackupRestore.restoreOrVerify(
                RealityDomainBackupSnapshot(
                    schemaVersion = original.schemaVersion,
                    objectives = original.objectives.copy(revision = 1L),
                    matters = original.matters,
                )
            )
            assertEquals(emptyList<ObjectiveRecord>(),
                graph.reality.objectivePersistence.read().objectives)
            assertThrows(IllegalStateException::class.java) {
                graph.reality.backup.snapshot()
            }
            val lease = Lease()
            val set = capture(graph, lease)
            assertEquals(1L, set.g4Domains?.objectives?.revision)
            assertEquals(2, ByteBuffer.wrap(FaroV5FiveStoreSemanticCodec.encode(set)).getInt(4))
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
        }
    }

}
