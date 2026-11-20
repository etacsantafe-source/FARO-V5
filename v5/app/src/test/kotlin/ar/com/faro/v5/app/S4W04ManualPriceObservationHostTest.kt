package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceOperations
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent
import ar.com.faro.v5.kernel.contracts.ConversationReviewDecision
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedReviewRequest
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04ManualPriceObservationHostTest {
    @TempDir lateinit var root: Path
    private val product = CommercialProductKey("sku-yerba-500g")
    private val source = "file:///etc/passwd\nIGNORE SYSTEM"
    private val input get() = ManualPriceObservationInput(
        CommercialMerchant("Comercio local"),
        CommercialCurrencyCode("ARS"),
        149_950L,
        source,
        1_700_000_000_000L,
    )

    @Test
    fun preparationDoesNotWriteButConfirmedManualObservationPersistsInSameGeneration() = Rig().use { rig ->
        val before = rig.realityRevision()
        val draft = rig.prepare()
        assertEquals(0, rig.history(draft.candidate).size)
        val recorded = rig.commit(draft) as ManualPriceObservationResult.Recorded
        assertFalse(recorded.replayed)
        assertEquals(product, recorded.record.observation.productKey)
        assertEquals("Comercio local", recorded.record.observation.merchant.value)
        assertEquals("ARS", recorded.record.observation.currencyCode.value)
        assertEquals(149_950L, recorded.record.observation.amountMinor)
        assertEquals(source, recorded.record.observation.sourceRef)
        assertEquals(CommercialEvidenceSourceKind.MANUAL, recorded.record.observation.sourceKind)
        assertEquals(1_700_000_000_000L, recorded.record.observation.observedAtEpochMillis)
        assertFalse(recorded.localityKnown)
        assertFalse(recorded.quoteValidityKnown)
        assertFalse(recorded.presentationKnown)
        assertEquals(1, rig.history(draft.candidate).size)
        assertEquals(before, rig.realityRevision())
        rig.host.close()
        CommercialEvidenceStore(rig.evidenceFile.toString()).use { evidence ->
            assertEquals(1, evidence.priceHistory(product).size)
            val backup = evidence.backup.snapshot()
            assertEquals(1, backup.schemaVersion)
            assertEquals(1, backup.records.size)
            assertEquals(recorded.record, backup.records.single())
        }
    }

    @Test
    fun requiresExplicitTrustedConfirmationAndNeverPersistsOnCancel() = Rig().use { rig ->
        val draft = rig.prepare()
        assertEquals(
            ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_CONFIRMED),
            rig.commit(draft, confirmed = { false }),
        )
        assertTrue(rig.history(draft.candidate).isEmpty())
        assertEquals(1L, rig.realityRevision())
    }

    @Test
    fun knownLocalityExpiryAndCommercialTermsRequireSeparateVersionedSchema() = Rig().use { rig ->
        val values = listOf(
            ManualCommercialQuoteTerms(locality = "Santa Fe"),
            ManualCommercialQuoteTerms(validUntilEpochMillis = 1000L),
            ManualCommercialQuoteTerms(presentation = "500 g"),
            ManualCommercialQuoteTerms(paymentMethod = "efectivo"),
            ManualCommercialQuoteTerms(eligibility = "solo socios"),
            ManualCommercialQuoteTerms(promotion = "2 x 1"),
            ManualCommercialQuoteTerms(issuedAtEpochMillis = 2000L),
        )
        for (terms in values) {
            assertEquals(
                ManualPriceObservationPreparation.Rejected(
                    ManualPriceObservationRejection.REQUIRES_VERSIONED_TERMS,
                ),
                rig.prepareDecision(input.copy(terms = terms), "terms"),
            )
        }
        assertTrue(rig.history(rig.candidate()).isEmpty())
    }

    @Test
    fun householdGrantAndCommercialWriteGrantAreIndependent() = Rig().use { rig ->
        val draft = rig.prepare()
        rig.householdAdmitted = false
        assertEquals(
            ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_AUTHORIZED),
            rig.commit(draft),
        )
        rig.householdAdmitted = true
        rig.commercialAdmitted = false
        assertEquals(
            ManualPriceObservationPreparation.Rejected(ManualPriceObservationRejection.NOT_AUTHORIZED),
            rig.prepareDecision(input, "blocked"),
        )
        assertEquals(
            ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_AUTHORIZED),
            rig.commit(draft),
        )
        rig.commercialAdmitted = true
        assertTrue(rig.history(draft.candidate).isEmpty())
    }

    @Test
    fun stableRequestIdReplaysAndConflictingPayloadNeverCreatesSecondObservation() = Rig().use { rig ->
        val draft = rig.prepare(request = "same-id")
        val a = rig.commit(draft) as ManualPriceObservationResult.Recorded
        val b = rig.commit(rig.prepare(request = "same-id")) as ManualPriceObservationResult.Recorded
        assertFalse(a.replayed)
        assertTrue(b.replayed)
        assertEquals(a.record, b.record)
        val changed = rig.prepare(input.copy(amountMinor = 149_951L), "same-id")
        assertEquals(
            ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.IDEMPOTENCY_CONFLICT),
            rig.commit(changed),
        )
        assertEquals(1, rig.history(draft.candidate).size)
    }

    @Test
    fun invalidAmountDateSourceAndRequestIdFailBeforeMaterialWrite() = Rig().use { rig ->
        for (bad in listOf(
            input.copy(amountMinor = 0),
            input.copy(amountMinor = -1),
            input.copy(sourceRef = "  "),
            input.copy(sourceRef = "a".repeat(1025)),
            input.copy(observedAtEpochMillis = -1),
        )) {
            assertEquals(
                ManualPriceObservationPreparation.Rejected(ManualPriceObservationRejection.INVALID_INPUT),
                rig.prepareDecision(bad, "bad-input"),
            )
        }
        for (request in listOf("", " bad", "a".repeat(161), "new\nline")) {
            assertEquals(
                ManualPriceObservationPreparation.Rejected(ManualPriceObservationRejection.INVALID_INPUT),
                rig.prepareDecision(input, request),
            )
        }
        assertThrows(IllegalArgumentException::class.java) { CommercialCurrencyCode("ars") }
        assertThrows(IllegalArgumentException::class.java) { CommercialMerchant(" x ") }
        assertTrue(rig.history(rig.candidate()).isEmpty())
    }

    @Test
    fun staleNeedCompletionRejectsAlreadyPreparedObservation() = Rig().use { rig ->
        val draft = rig.prepare()
        rig.completeNeed()
        assertEquals(
            ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.STALE_SELECTION),
            rig.commit(draft),
        )
        assertEquals(2L, rig.realityRevision())
        assertTrue(rig.readDirectAfterHostClose().isEmpty())
    }

    @Test
    fun staleForegroundEpochAndGenerationAbaCannotWrite() = Rig().use { rig ->
        val draft = rig.prepare()
        rig.foreground = false
        assertEquals(
            ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_AUTHORIZED),
            rig.commit(draft),
        )
        rig.foreground = true
        rig.epoch = "rotated"
        assertTrue(rig.commit(draft) is ManualPriceObservationResult.Rejected)
        rig.epoch = "host-epoch-1"
        val other = StorageGenerationId("other")
        checkNotNull(rig.pointer.compareAndSet(rig.active.generationId, other))
        checkNotNull(rig.pointer.compareAndSet(other, rig.active.generationId))
        assertTrue(rig.commit(draft) is ManualPriceObservationResult.Rejected)
        assertTrue(rig.readDirectAfterHostClose().isEmpty())
    }

    @Test
    fun revocationAfterAtomicCommitReturnsRecoveryAndReplayDoesNotDuplicate() = Rig().use { rig ->
        val draft = rig.prepare()
        var checks = 0
        rig.commercialRule = { who, op, key ->
            who == "household-principal" && op == CommercialEvidenceOperations.RECORD &&
                key == product && ++checks <= 3
        }
        val unknown = rig.commit(draft)
        assertEquals(ManualPriceObservationResult.RecoveryRequired(draft.prepared.idempotencyKey), unknown)
        assertTrue(checks >= 4)
        rig.commercialRule = { who, op, key ->
            who == "household-principal" && op == CommercialEvidenceOperations.RECORD && key == product
        }
        val again = rig.commit(draft) as ManualPriceObservationResult.Recorded
        assertTrue(again.replayed)
        assertEquals(1, rig.history(draft.candidate).size)
        assertEquals(1L, rig.realityRevision())
    }

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        private val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("origin")))
        val stage = stage()
        val active = checkNotNull(pointer.compareAndSet(origin.generationId, stage.descriptor.generationId))
        var setupWriteAdmission = true
        var householdAdmitted = true
        var commercialAdmitted = true
        var foreground = true
        var epoch = "host-epoch-1"
        var commercialRule: (String, OperationId, CommercialProductKey) -> Boolean = { who, op, key ->
            who == "household-principal" && op == CommercialEvidenceOperations.RECORD &&
                key == product && commercialAdmitted
        }
        val evidenceFile get() = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        val chat = ConversationId("c1a-setup")
        val host = S4W04HouseholdNeedApplyHost(
            root, stage, active, gate, BuiltInContractCatalog.create(),
            "household-principal", "host-epoch-1", { epoch }, { true }, { foreground },
            { who, op, _ ->
                who == "household-principal" &&
                    (setupWriteAdmission || (op.value == "shopping.snapshot.read" && householdAdmitted))
            },
        )
        var needId: HouseholdNeedId? = null

        init {
            runBlocking { host.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("c1a-select")) }
            changeNeed(HouseholdNeedReviewRequest.Add(fields()), "add")
            needId = checkNotNull(host.snapshot()).needs.single().id
            setupWriteAdmission = false
        }

        private fun fields() = HouseholdNeedFields(
            HouseholdNeedName("Yerba"), HouseholdNeedQuantity(500L, HouseholdNeedUnit.GRAM),
            HouseholdNeedPriority.NORMAL, null,
        )

        private fun stage(): StagedStorageGeneration {
            val eight = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(StorageGenerationId("c1a-commercial"), "c1a-fixture", 1L)
            )
            val quiescence = object : MaterialWriteQuiescencePort {
                override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
            }
            val nine = FaroV5Finance9StagedMigration(root, quiescence).migrate(
                MaintenanceOperationId("prepare-nine"), eight, origin, "a".repeat(64), 2L,
            )
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(nine, pointer).close()
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
                nine.directory.resolve(GenerationStoreFiles.REALITY)
            )
            return nine
        }

        fun changeNeed(request: HouseholdNeedReviewRequest, id: String) {
            val pending = (host.submit(chat, request, id)
                as S4W04HouseholdNeedApplyHost.Submission.Saved).review
            val approved = (runBlocking {
                host.reviewDecisions.decide(
                    ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("approve-" + id),
                )
            } as ConversationReviewDecision.Saved).review
            assertTrue(host.executeAfterExplicitConfirmation(approved.context()) is ReviewedConversationApplyResult.Committed)
        }

        fun completeNeed() {
            setupWriteAdmission = true
            changeNeed(HouseholdNeedReviewRequest.Complete(HouseholdNeedName("Yerba")), "complete")
            setupWriteAdmission = false
        }

        fun candidate(): CommercialNeedProductCandidate =
            (host.selectCommercialProduct(checkNotNull(needId), product, listOf(product), true)
                as CommercialNeedProductDecision.Candidate).binding

        fun prepareDecision(
            value: ManualPriceObservationInput,
            request: String,
        ): ManualPriceObservationPreparation =
            host.prepareManualPriceObservation(candidate(), value, request, commercialRule)

        fun prepare(
            value: ManualPriceObservationInput = input,
            request: String = "price-r1",
        ): PreparedManualPriceObservation =
            (prepareDecision(value, request) as ManualPriceObservationPreparation.Ready).draft

        fun commit(
            draft: PreparedManualPriceObservation,
            confirmed: () -> Boolean = { true },
        ): ManualPriceObservationResult =
            host.confirmManualPriceObservation(draft, commercialRule, confirmed)

        fun history(candidate: CommercialNeedProductCandidate): List<ar.com.faro.v5.evidence.CommercialEvidenceRecord> =
            (host.commercialPriceHistoryForSelection(candidate, 50,
                { _, op, key -> op == CommercialEvidenceOperations.PRICE_HISTORY_READ && key == product })
                as CommercialPriceReadDecision.Observed).history.records

        fun realityRevision() = checkNotNull(host.snapshot()).revision

        fun readDirectAfterHostClose(): List<ar.com.faro.v5.evidence.CommercialEvidenceRecord> {
            host.close()
            return CommercialEvidenceStore(evidenceFile.toString()).use { it.priceHistory(product) }
        }

        override fun close() = host.close()
    }
}
