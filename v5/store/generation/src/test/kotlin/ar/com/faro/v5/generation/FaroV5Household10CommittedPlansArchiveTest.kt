package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.*
import ar.com.faro.v5.owner.household.spi.HouseholdNeedChange
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.workspace.WorkspacePlanReviewCreateRequest
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10CommittedPlansArchiveTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = CanonicalPreparedPlanEnvelopeVerifier
    private val pointer get() = FileActiveGenerationPointer(root)
    private val chat = ConversationId("household-archive")
    private val passphrase = "directed-household-fbs2v6-test".toCharArray()
    private val name = HouseholdNeedName("Yerba")
    private val id = HouseholdNeedId("first-need")
    private val fields = HouseholdNeedFields(
        name, HouseholdNeedQuantity(500, HouseholdNeedUnit.GRAM), HouseholdNeedPriority.NORMAL, null,
    )

    @Test
    fun preservesNeedsReceiptAndCanonicalPreimageThroughAuthenticatedRestore() {
        val fixture = fixture()
        val before = sourceBytes(fixture)
        val target = stageNine("restore-target", fixture.pinned)
        withClosedLease { owned ->
            val coordinator = coordinator(owned)
            val archive = capture(coordinator, fixture)
            val result = coordinator.restoreAuthenticated(
                MaintenanceOperationId("restore-live"), target, fixture.pinned, archive, passphrase,
            )
            assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value), result)
            val expected = RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(fixture.source.realityFile())
            assertEquals(expected, RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(target.realityFile()))
            assertEquals(2L, expected.revision)
            assertEquals(750, expected.needs.single().quantityThousandths)

            FaroV5GenerationStoreGraph.openValidatedHousehold10ArchiveStage(target, fixture.pinned, verifier).use { restored ->
                val restoredWorkspace = checkNotNull(restored.workspace)
                val restoredReview = sync { restoredWorkspace.reviews(chat) }.single { it.reviewId == fixture.review.reviewId }
                assertEquals(ConversationPlanReviewState.COMMITTED, restoredReview.state)
                assertEquals(fixture.receipt, restoredReview.receipt)
                val committed = checkNotNull(restoredWorkspace.loadCommitted(restoredReview.context()))
                val plan = PreparedPlanEnvelopeCodec.decode(committed.envelope())
                assertEquals(fixture.review.planFingerprint, plan.planFingerprint)
                val payload = plan.applyPlan.nodesInTopologicalOrder().single().preparedOperation.payload
                val original = checkNotNull(HouseholdNeedReview.decode(payload))
                assertEquals(500L, original.before?.quantity?.thousandths)
                assertEquals(750L, original.after?.quantity?.thousandths)
                assertEquals(fixture.receipt, restored.reality.executionReceiptLookup.find(fixture.receipt.transactionId))
                assertEquals(ApplyAttemptJournalState.COMMITTED,
                    restored.reality.applyAttemptLookup.find(fixture.receipt.transactionId)?.state)
                assertEquals(0L, restored.reality.financePersistence.read().revision)
                assertEquals(0L, restored.reality.objectivePersistence.read().revision)
                assertEquals(0L, restored.reality.matterPersistence.read().revision)
            }
        }
        assertSourceUnchanged(fixture, before)
    }

    @Test
    fun failsClosedWithoutAnAdmittedVerifier() {
        val fixture = fixture()
        val before = sourceBytes(fixture)
        withClosedLease { owned ->
            assertThrows(IllegalStateException::class.java) { capture(coordinator(owned, null), fixture, "without-verifier") }
            val wrong = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
            assertThrows(IllegalStateException::class.java) { capture(coordinator(owned, wrong), fixture, "wrong-verifier") }
        }
        assertSourceUnchanged(fixture, before)
    }

    @Test
    fun rejectsMissingLeaseSidecarsAndPointerAbaBeforeAllocatingCopy() {
        val fixture = fixture()
        val unowned = coordinator({ error("root lease absent") })
        assertThrows(IllegalStateException::class.java) { capture(unowned, fixture) }
        withClosedLease { owned ->
            val coordinator = coordinator(owned)
            val sidecar = fixture.source.directory.resolve(GenerationStoreFiles.REALITY + "-wal")
            Files.write(sidecar, byteArrayOf(1))
            try {
                assertThrows(IllegalStateException::class.java) { capture(coordinator, fixture) }
            } finally {
                Files.delete(sidecar)
            }
            val other = StorageGenerationId("other")
            checkNotNull(pointer.compareAndSet(fixture.pinned.generationId, other))
            checkNotNull(pointer.compareAndSet(other, fixture.pinned.generationId))
            assertThrows(IllegalStateException::class.java) { capture(coordinator, fixture) }
            assertFalse(Files.exists(root.resolve("generations/active-copy")))
        }
    }

    @Test
    fun rejectsTamperedArchiveAndKeepsSourceUnchanged() {
        val fixture = fixture()
        val before = sourceBytes(fixture)
        val target = stageNine("restore-target", fixture.pinned)
        withClosedLease { owned ->
            val coordinator = coordinator(owned)
            val archive = capture(coordinator, fixture)
            archive[archive.lastIndex] = (archive.last().toInt() xor 1).toByte()
            val rejected = assertInstanceOf(FiveStoreStagedRestoreResult.Blocked::class.java,
                coordinator.restoreAuthenticated(MaintenanceOperationId("bad-restore"), target, fixture.pinned, archive, passphrase))
            assertEquals(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, rejected.reason)
        }
        assertSourceUnchanged(fixture, before)
    }

    private fun fixture(): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("origin")))
        val source = stageNine("active-household", origin)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).close()
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(source.realityFile())
        val pinned = checkNotNull(pointer.compareAndSet(origin.generationId, source.descriptor.generationId))
        return FaroV5GenerationStoreGraph.openActiveHousehold10DevForApplyHost(root, source, pinned, gate, verifier, { true })
            .use { graph ->
                val workspace = graph.workspace
                val store = graph.reality
                val needs = store.householdNeedsPersistenceForTrustedDev()
                store.run(TransactionId("seed-need")) {
                    needs.mutate(0, HouseholdNeedChange.Add(id, fields), it)
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
                val chosen = sync { workspace.createAndSelect(chat, 1L, IdempotencyKey("archive-chat")) }
                assertInstanceOf(ConversationSelectionResult.Saved::class.java, chosen)
                val timeline = checkNotNull(sync { workspace.readTimeline(chat) })
                val draft = (HouseholdNeedReview.propose(
                    HouseholdReviewGeneration(pinned.generationId.value, pinned.sequence),
                    needs.read(), HouseholdNeedReviewRequest.Edit(name,
                        fields.copy(quantity = HouseholdNeedQuantity(750, HouseholdNeedUnit.GRAM))),
                    "archive-edit",
                ) as HouseholdNeedReviewResult.Ready).review
                val descriptor = checkNotNull(BuiltInContractCatalog.create().current(draft.action.operationId))
                val prepared = PreparedOperation(descriptor, draft.payload, DependencyStampSet.empty(), IdempotencyKey("needs-edit"))
                val plan = PreparedPlan.of(ApplyPlanNode(PlanNodeId("need"), prepared, emptySet()))
                val pending = (workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                    chat, "w04:household:archive", timeline.authorityToken, timeline.revision, plan.planFingerprint,
                    draft.summary, PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("review-archive"),
                )) as WorkspacePlanReviewWriteResult.Saved).review
                val approved = (sync {
                    workspace.decide(ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("approve-archive"))
                } as ConversationReviewDecision.Saved).review
                val identity = store.applyAttemptIdentities.resolve(plan)
                val applying = (workspace.advance(
                    ConversationPlanReviewIntent.BeginApply(approved.context(), identity), IdempotencyKey("begin-archive"),
                    store.applyAttemptLookup, store.executionReceiptLookup,
                ) as ConversationReviewDecision.Saved).review
                val receipt = store.run(identity.transactionId) { scope ->
                    needs.mutate(draft.expectedRevision, draft.toPersistenceChange(), scope)
                    AtomicApplyTransactionDecision.Commit(store.commit(scope, identity, emptyList()))
                }
                val committed = (workspace.advance(
                    ConversationPlanReviewIntent.ObserveCommitted(applying.context(), receipt), IdempotencyKey("commit-archive"),
                    store.applyAttemptLookup, store.executionReceiptLookup,
                ) as ConversationReviewDecision.Saved).review
                Fixture(source, pinned, committed, receipt)
            }
    }

    private fun <T> sync(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(outcome: Result<T>) {
                result = outcome
            }
        })
        return checkNotNull(result).getOrThrow()
    }

    private fun stageNine(name: String, expected: ActiveGenerationPointer): StagedStorageGeneration {
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "archive-fixture", 1L),
        )
        return FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("migrate-$name"), stage, expected, "a".repeat(64), 2L,
        )
    }

    private fun coordinator(owned: () -> Unit, planVerifier: PreparedPlanEnvelopeVerificationPort? = verifier) =
        FaroV5Household10ArchiveCoordinator(root, MaterialWriteGateQuiescence(gate), owned, planVerifier = planVerifier)

    private fun capture(coordinator: FaroV5Household10ArchiveCoordinator, fixture: Fixture, copy: String = "active-copy") =
        coordinator.captureClosedActiveAuthenticated(
            MaintenanceOperationId("capture-$copy"), BackupSetId("backup-$copy"), fixture.pinned,
            StorageGenerationDescriptor(StorageGenerationId(copy), "archive-copy", 3L), "5.0-dev", 4L, passphrase,
        )

    private fun withClosedLease(block: (() -> Unit) -> Unit) {
        FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { file ->
            checkNotNull(file.tryLock()).use { lock -> block { check(lock.isValid) } }
        }
    }

    private fun StagedStorageGeneration.realityFile() = directory.resolve(GenerationStoreFiles.REALITY)

    private fun sourceBytes(fixture: Fixture) = GenerationStoreFiles.WORKSPACE_V2.map {
        Files.readAllBytes(fixture.source.directory.resolve(it))
    }

    private fun assertSourceUnchanged(fixture: Fixture, before: List<ByteArray>) {
        assertEquals(fixture.pinned, pointer.read())
        before.zip(sourceBytes(fixture)).forEach { (expected, actual) -> assertArrayEquals(expected, actual) }
    }

    private data class Fixture(
        val source: StagedStorageGeneration,
        val pinned: ActiveGenerationPointer,
        val review: ConversationPlanReviewSnapshot,
        val receipt: ExecutionReceipt,
    )
}
