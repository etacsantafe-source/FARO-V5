package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5G4DomainSemanticCodec
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.workspace.WorkspacePlanReviewWriteResult
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W02ObjectiveHistoryTest {
    @TempDir lateinit var root: Path

    private inner class Rig : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val runtime = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, CanonicalPreparedPlanEnvelopeVerifier)
        val selection = try {
            val start = StorageGenerationId("objective-planning-origin")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(start, "planning", 1L),
            )
            runtime.start(
                MaintenanceOperationId("objectives-cutover"),
                StorageGenerationDescriptor(StorageGenerationId("objectives-active"), "objectives", 2L),
                requireNotNull(pointer.compareAndSet(null, start)), 3L,
            ) { true }
        } catch (failure: Throwable) {
            runtime.close()
            throw failure
        }
        val graph = runtime.openCommittedFinance9(selection)
        val reality = graph.reality
        val workspace = requireNotNull(graph.workspace)
        val chat = ConversationId("objectives-chat")
        var foreground = true
        var policyAllows = true
        val host = S4W02ObjectiveReviewHost(
            selection.pointer, pointer, workspace, reality, gate, BuiltInContractCatalog.create(),
            "objective-test-principal", "epoch", { "epoch" }, { runtime.isCurrent(selection) }, { foreground },
            Planning8TrustedObjectPolicyPort { _, _, _ -> if (policyAllows) "allow-current" else null },
        )

        init { select(chat) }

        fun select(conversation: ConversationId) = runBlocking {
            assertTrue(workspace.createAndSelect(
                conversation, 5L, IdempotencyKey("select-" + conversation.value),
            ) is ConversationSelectionResult.Saved)
        }

        fun submit(
            key: String,
            operation: OperationId = ObjectiveReviewOperations.CREATE,
            revision: Long = reality.objectivePersistence.read().revision,
            title: String = "Practicar",
        ): ConversationPlanReviewSnapshot {
            val form = ObjectiveReviewedEdit(
                ObjectiveId.of("goal"), ObjectiveTitle.of(title), ObjectiveDetail.of("Cada semana"),
                ObjectiveMetric(12, ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.QUANTITY, "sesiones")),
                LocalDate.of(2027, 5, 1), ObjectivePriority.HIGH,
            )
            val result = host.submit(chat, operation, ObjectiveEditIntent(revision, 100 + revision, form), key)
            return (result as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun decide(intent: ConversationPlanReviewIntent, key: String): ConversationPlanReviewSnapshot =
            (runBlocking { workspace.decide(intent, IdempotencyKey(key)) } as ConversationReviewDecision.Saved).review

        fun approve(review: ConversationPlanReviewSnapshot): ConversationPlanReviewSnapshot =
            decide(ConversationPlanReviewIntent.Approve(review.context()), "approve-" + review.reviewId)

        fun apply(review: ConversationPlanReviewSnapshot) = host.executeAfterExplicitConfirmation(review.context())

        fun history(
            key: String,
            change: ObjectiveHistoryChange,
        ): ConversationPlanReviewSnapshot {
            val snapshot = reality.objectivePersistence.read()
            val record = snapshot.objectives.single()
            val intent = ObjectiveHistoryIntent(
                record.id, snapshot.revision,
                ObjectiveHistoryEntry(record.details.history.size + 1, record.updatedAtEpochMillis + 1, change),
            )
            return (host.submitHistory(chat, intent, key) as WorkspacePlanReviewWriteResult.Saved).review
        }

        fun create() {
            assertTrue(apply(approve(submit("create"))) is ConversationApplyHostOutcome.Committed)
        }

        override fun close() {
            host.close()
            runtime.close()
        }
    }


    private val readingUnit = ObjectiveMetricUnit(ObjectiveMetricUnit.Kind.QUANTITY, "sesiones")
    private val date = LocalDate.of(2027, 2, 3)

    @Test fun appliesProgressAndCompensatingCorrectionWithReceiptsAndRoundTrip() = Rig().use { rig ->
        rig.create()
        val report = ObjectiveProgressReport(date, "Avance observado", ObjectiveReading(3, readingUnit))
        val pending = rig.history("progress", ObjectiveHistoryChange.Progress(report))
        assertNull(rig.reality.objectivePersistence.read().objectives.single().confirmedProgress())
        val approved = rig.approve(pending)
        val progress = rig.apply(approved) as ConversationApplyHostOutcome.Committed
        assertTrue(rig.apply(approved) is ConversationApplyHostOutcome.Denied)
        val first = rig.reality.objectivePersistence.read().objectives.single()
        assertEquals(ObjectiveConfirmedProgress(3, 12), first.confirmedProgress())
        val change = ObjectiveHistoryChange.Correction(
            1, report.copy(reading = ObjectiveReading(4, readingUnit)), "Corregir el conteo",
        )
        val correction = rig.apply(rig.approve(rig.history("correction", change))) as ConversationApplyHostOutcome.Committed
        for (receipt in listOf(progress.receipt, correction.receipt)) {
            assertEquals(receipt, rig.reality.executionReceiptLookup.find(receipt.transactionId))
        }
        val updated = rig.reality.objectivePersistence.read().objectives.single()
        assertEquals(first.details.history.single(), updated.details.history.first())
        assertEquals(change, updated.details.history.last().change)
        assertEquals(ObjectiveConfirmedProgress(4, 12), updated.confirmedProgress())

        val archiveSnapshot = rig.reality.domainBackup.snapshot()
        val bytes = FaroV5G4DomainSemanticCodec.encode(archiveSnapshot)
        val decoded = FaroV5G4DomainSemanticCodec.decode(bytes)
        assertEquals(archiveSnapshot, decoded)
        RealityStore(":memory:").use { restored ->
            assertEquals(decoded, restored.domainBackupRestore.restoreOrVerify(decoded))
            assertEquals(updated, restored.objectivePersistence.read().objectives.single())
        }
    }

    @Test fun lifecycleIsExplicitAndEditsKeepTheJournal() {
        Rig().use { rig ->
            rig.create()
            val lifecycle = ObjectiveHistoryChange.Lifecycle(ObjectiveStatus.PLANNED, ObjectiveStatus.ACTIVE, "Arrancar")
            assertTrue(rig.apply(rig.approve(rig.history("start", lifecycle))) is ConversationApplyHostOutcome.Committed)
            val edit = rig.submit("edit", ObjectiveReviewOperations.UPDATE, title = "Práctica")
            assertTrue(rig.apply(rig.approve(edit)) is ConversationApplyHostOutcome.Committed)
            val result = rig.reality.objectivePersistence.read().objectives.single()
            assertEquals(ObjectiveStatus.ACTIVE, result.status)
            assertEquals(lifecycle, result.details.history.single().change)
            assertThrows(IllegalArgumentException::class.java) {
                ObjectiveHistoryChange.Lifecycle(ObjectiveStatus.ARCHIVED, ObjectiveStatus.PAUSED, "Inválido")
            }
        }
    }

    @Test fun staleOrRevokedProgressCannotAppendAnEvent() = Rig().use { rig ->
        rig.create()
        val change = ObjectiveHistoryChange.Progress(ObjectiveProgressReport(date, "Evidencia local"))
        val first = rig.approve(rig.history("first-report", change))
        val stale = rig.approve(rig.history("stale-report", change))
        rig.policyAllows = false
        assertTrue(rig.apply(first) is ConversationApplyHostOutcome.Denied)
        rig.policyAllows = true
        assertTrue(rig.apply(first) is ConversationApplyHostOutcome.Committed)
        assertTrue(rig.apply(stale) is ConversationApplyHostOutcome.Denied)
        val result = rig.reality.objectivePersistence.read().objectives.single()
        assertEquals(change, result.details.history.single().change)
        assertNull(result.confirmedProgress())
    }

    @Test fun rejectsDanglingCorrectionsAndSupportsQualitativeHistory() {
        val report = ObjectiveProgressReport(date, "Actividad realizada")
        val progress = ObjectiveHistoryEntry(1, 1L, ObjectiveHistoryChange.Progress(report))
        val record = ObjectiveRecord(
            ObjectiveId.of("goal"), ObjectiveTitle.of("Organización"), ObjectiveDetail.of(""),
            ObjectiveStatus.ACTIVE, 1, ObjectiveDetails(history = listOf(progress)),
        )
        assertNull(record.confirmedProgress())
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveDetails(history = listOf(
                progress, ObjectiveHistoryEntry(2, 2, ObjectiveHistoryChange.Correction(2, report, "Corrección")),
            ))
        }
    }
}
