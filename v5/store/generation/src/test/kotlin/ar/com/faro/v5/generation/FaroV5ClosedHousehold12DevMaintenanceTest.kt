package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspaceBackupPlanReview
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5ClosedHousehold12DevMaintenanceTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val verifier = CanonicalPreparedPlanEnvelopeVerifier
    private val pointer get() = FileActiveGenerationPointer(root)
    private val key = "closed-sql12-dev-fixture".toCharArray()
    private val operation = MaintenanceOperationId("closed-sql12-to-sql13")
    private val rows = RealityHousehold12PurchaseReceiptRows(
        listOf(RealityHousehold12PurchaseRow("purchase", "yerba", 10_000, "UNIT", "local-human", 1L, "MANUAL_EXPLICIT", 1L)),
        RealityHousehold11ReceiptRows(2L, listOf(
            RealityHousehold11ReceiptRow("receipt-1", "intent-1", "purchase", "yerba", 7000, "UNIT", 0L, 2L, 1L),
            RealityHousehold11ReceiptRow("receipt-2", "intent-2", "purchase", "yerba", 3000, "UNIT", 0L, 3L, 2L),
        )),
    )

    private fun descriptor(id: String) = StorageGenerationDescriptor(StorageGenerationId(id), "closed-dev-fixture", 100L)

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(descriptor(id))
        val finance = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("finance-$id"), fresh, origin, "a".repeat(64), 100L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(finance, pointer).close()
        CommercialEvidenceStore(
            fileName = finance.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        return finance
    }

    private fun sql11(staged: StagedStorageGeneration): Path = staged.directory.resolve(GenerationStoreFiles.REALITY).also {
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(it)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(it)
    }

    private fun sql12(): ActiveGenerationPointer {
        val initial = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("fixture-bootstrap")))
        val old = stage("fixture-origin11", initial)
        sql11(old)
        val origin = checkNotNull(pointer.compareAndSet(initial.generationId, old.descriptor.generationId))
        val source = stage("fixture-source12", origin)
        val reality = sql11(source)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
        RealityHousehold12PurchaseRowsPort.restoreOrVerifyTrustedStagedTwelve(reality, rows)

        val prior = MaintenanceOperationId("fixture-select12")
        val target = stage("fixture-selected12", origin)
        val replay = FaroV5Household12V9StagedReplay(root, quiescence, {}, planVerifier = verifier)
        val archive = replay.captureAuthenticated(prior, source, origin, true, BackupSetId("fixture-v9"), "5.0-dev", 100, key)
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value),
            replay.restoreAuthenticatedV9(prior, target, origin, archive, key))
        FaroV5Household12Evidence2DevJournaledCutover.acquire(root, gate, { true }).use {
            assertEquals(FaroV5Household12Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND,
                it.authorize(prior, target, origin, archive, key) { true }.status)
            assertEquals(FaroV5Household12Evidence2DevJournaledCutover.Status.ACTIVE_TWELVE,
                it.start(prior, target, origin, archive, key) { true }.status)
        }
        return checkNotNull(pointer.read())
    }

    private fun persistCommittedReview(selected: ActiveGenerationPointer): WorkspaceBackupPlanReview {
        val active = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
        val chat = ConversationId("closed-fixture-history")
        val contract = checkNotNull(BuiltInContractCatalog.create().current(OperationId("stock.receive")))
        val plan = PreparedPlan.of(ApplyPlanNode(
            PlanNodeId("receive"),
            PreparedOperation(contract, CanonicalPayload.fromEncoded("fixture-v1", byteArrayOf(1)),
                DependencyStampSet.empty(), IdempotencyKey("fixture-review")),
            emptySet(),
        ))
        val bytes = PreparedPlanEnvelopeCodec.encode(plan)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val review = WorkspaceBackupPlanReview(
            "w05-fixture-review", chat, plan.planFingerprint, "Recepción confirmada", 1L,
            ConversationPlanReviewState.COMMITTED, Base64.getEncoder().encodeToString(bytes),
            hash, "fixture-transaction", "fixture-execution-receipt",
        )
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            active.directory.resolve(GenerationStoreFiles.WORKSPACE).toString(), verifier,
        ).use {
            it.backup.restoreOrVerify(it.backup.snapshot().copy(
                revision = 1L, activeConversationId = chat,
                conversations = listOf(ConversationWorkspaceEntry(chat, "Historial", 1L)),
                planReviews = listOf(review),
            ))
        }
        val fingerprint = plan.planFingerprint.value
        BundledSQLiteDriver().open(active.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("INSERT INTO apply_attempt(transaction_id, plan_fingerprint, state) " +
                "VALUES ('fixture-transaction', '$fingerprint', 'COMMITTED')")
            it.execSQL("INSERT INTO execution_receipt(receipt_id, transaction_id, plan_fingerprint) " +
                "VALUES ('fixture-execution-receipt', 'fixture-transaction', '$fingerprint')")
        }
        return review
    }

    private fun stages() = FaroV5ClosedHousehold12DevMaintenance.Stages(
        descriptor("sealed-copy12"), descriptor("bridge13"), descriptor("selected13"),
    )

    @Test
    fun archivesClosedSql12AndSelectsAuthenticatedSql13() {
        val origin = sql12()
        val expectedReview = persistCommittedReview(origin)
        val stages = stages()
        val selected = FaroV5ClosedHousehold12DevMaintenance.acquire(root, gate, verifier) { true }.use { service ->
            val prepared = service.prepare(operation, origin, stages, key) { true }
            assertEquals(origin, pointer.read())
            val journal = FileBootstrapJournal(root)
            assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
            assertNull(service.start(prepared, key) { false })
            assertEquals(origin, pointer.read())
            val active = checkNotNull(service.start(prepared, key) { true })
            assertEquals(ActiveGenerationPointer(origin.sequence + 1, stages.v10Destination.generationId), active)
            assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
            active
        }
        FaroV5ClosedHousehold12DevMaintenance.acquire(root, gate, verifier) { true }.use {
            assertEquals(selected, it.recover())
        }
        FaroV5Household13Evidence2DevJournaledCutover.reopenCompleted(root, selected, gate, verifier) { true }.use {
            val restored = checkNotNull(it.readHistory())
            assertEquals(rows, restored.historical)
            assertTrue(restored.compensations.isEmpty())
        }
        val active = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            active.directory.resolve(GenerationStoreFiles.WORKSPACE).toString(), verifier,
        ).use { assertEquals(expectedReview, it.backup.snapshot().planReviews.single()) }
    }

    @Test
    fun blocksLiveLeaseAndPreparedApplyBeforeSelecting() {
        val origin = sql12()
        FileChannel.open(root.resolve("finance9-dev-root.lock"), WRITE).use { channel ->
            channel.lock().use {
                assertThrows(java.nio.channels.OverlappingFileLockException::class.java) {
                    FaroV5ClosedHousehold12DevMaintenance.acquire(root, gate, verifier) { true }.close()
                }
            }
        }
        val active = FileStorageGenerationLayout(root).requireStaged(origin.generationId)
        BundledSQLiteDriver().open(active.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("INSERT INTO apply_attempt(transaction_id, plan_fingerprint, state) VALUES ('pending', 'fixture', 'PREPARED')")
        }
        FaroV5ClosedHousehold12DevMaintenance.acquire(root, gate, verifier) { true }.use { service ->
            assertThrows(IllegalStateException::class.java) {
                service.prepare(operation, origin, stages(), key) { true }
            }
        }
        assertNull(FileBootstrapJournal(root).read(operation))
        assertEquals(origin, pointer.read())
    }
}
