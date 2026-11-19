package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionReceiptV4Result
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W03NoticeAttentionReceiptHostTest {
    @TempDir lateinit var root: Path

    private inner class Session : AutoCloseable {
        val gate = SingleProcessMaterialWriteGate()
        val pointer = FileActiveGenerationPointer(root)
        val owner = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, CanonicalPreparedPlanEnvelopeVerifier)
        val selected = try {
            val source = StorageGenerationId("host-planning8")
            FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
                StorageGenerationDescriptor(source, "planning", 1L),
            )
            val finance = owner.start(
                MaintenanceOperationId("host-finance9"),
                StorageGenerationDescriptor(StorageGenerationId("host-finance9"), "finance", 2L),
                checkNotNull(pointer.compareAndSet(null, source)), 3L,
            ) { true }
            owner.startAuxiliary4(
                MaintenanceOperationId("host-auxiliary4"),
                StorageGenerationDescriptor(StorageGenerationId("host-auxiliary4"), "auxiliary", 4L),
                finance.pointer, 5L,
            ) { true }
        } catch (failure: Throwable) {
            owner.close()
            throw failure
        }
        val graph = owner.openCommittedFinance9(selected)
        val notice = graph.auxiliary.routeReminderLocalOnly(NoticeCandidate(
            NoticeKey.of("reminder:host-occ"), SourceRef.of("reminder:host-rule"),
            OccurrenceRef.of("host-occ"), NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
            NoticeSummary.of("Aviso"), DestinationRef.of("faro:avisos.local"),
        )) { true }.notice
        var read = true
        var write = true
        var foreground = true
        var epoch: String? = "host-epoch"
        val host = S4W03NoticeAttentionCasHost(
            selected.pointer, pointer, graph.auxiliary, gate, "trusted-device",
            "host-epoch", { epoch }, { owner.isCurrent(selected) }, { foreground },
            Planning8TrustedObjectPolicyPort { _, scope, _ ->
                val allowed = when (scope.operationId) {
                    OperationId("notice.read") -> read
                    OperationId("notice.seen.set"), OperationId("notice.dismiss") -> write
                    else -> false
                }
                "scope-epoch".takeIf { allowed && scope.objectId == notice.noticeId.value }
            },
        )

        fun seen(key: String = "seen", revision: Long = 1L) =
            host.explicitClickWithReceipt(key, notice.noticeId, revision, NoticeAttention.SEEN)

        override fun close() {
            host.close()
            owner.close()
        }
    }

    @Test fun clickAndRetryRetainExactReceiptWithoutMutatingReminderOrMatter() = Session().use {
        val reminders = it.graph.reality.planningDueReadOwnersForTrustedHost().reminders.read()
        val matters = it.graph.reality.matterPersistence.read()
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Committed(2L, false), it.seen())
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Committed(2L, true), it.seen())
        assertEquals(2L, it.graph.auxiliary.readNotices().single().revision)
        assertEquals(reminders, it.graph.reality.planningDueReadOwnersForTrustedHost().reminders.read())
        assertEquals(matters, it.graph.reality.matterPersistence.read())
        assertTrue(it.graph.auxiliary.readNotificationProjectionCandidateIds(8).isEmpty())
    }

    @Test fun readWriteForegroundAndEpochAreIndependentRequirements() = Session().use {
        it.read = false
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied, it.seen())
        it.read = true
        it.write = false
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied, it.seen())
        it.write = true
        it.foreground = false
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied, it.seen())
        it.foreground = true
        it.epoch = null
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied, it.seen())
        assertEquals(1L, it.graph.auxiliary.readNotices().single().revision)
    }

    @Test fun wrongObjectStaleRevisionAndConflictingKeyFailWithoutNewRevision() = Session().use {
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied,
            it.host.explicitClickWithReceipt("foreign", NoticeId.of("some-other"), 1L, NoticeAttention.SEEN))
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Stale(1L), it.seen("wrong-revision", 9L))
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Committed(2L, false), it.seen())
        assertEquals(S4W03NoticeAttentionReceiptV4Result.IdentityConflict,
            it.host.explicitClickWithReceipt("seen", it.notice.noticeId, 2L, NoticeAttention.DISMISSED))
        assertEquals(2L, it.graph.auxiliary.readNotices().single().revision)
    }

    @Test fun changingPointerAwayAndBackRevokesEvenAnExistingReceiptReplay() = Session().use {
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Committed(2L, false), it.seen())
        val pinned = it.selected.pointer.generationId
        val other = StorageGenerationId("other")
        checkNotNull(it.pointer.compareAndSet(pinned, other))
        checkNotNull(it.pointer.compareAndSet(other, pinned))
        assertEquals(S4W03NoticeAttentionReceiptV4Result.Denied, it.seen())
        assertEquals(2L, it.graph.auxiliary.readNotices().single().revision)
    }
}
