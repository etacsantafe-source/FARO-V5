package ar.com.faro.v5.generation

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionReceiptV4Result
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Auxiliary4RestoredNoticeRouteTest {
    @TempDir lateinit var root: Path

    private val candidate = NoticeCandidate(
        NoticeKey.of("reminder:restored-occ"), SourceRef.of("reminder:restored-rule"),
        OccurrenceRef.of("restored-occ"), NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
        NoticeSummary.of("Aviso local"), DestinationRef.of("faro:avisos.local"),
    )

    private fun stage(name: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "aux4-local-reprojection", 1L),
        )

    private fun open(staged: StagedStorageGeneration) =
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            staged, FileActiveGenerationPointer(root),
        )

    private fun restored(seed: (AuxiliaryDeliveryStore) -> Unit): StagedStorageGeneration {
        val snapshot = open(stage("projection-source")).use { owner ->
            seed(owner)
            owner.snapshotStagedReceiptBackup()
        }
        return stage("projection-target").also { target ->
            open(target).use { owner ->
                owner.restoreOrVerifyStagedReceiptBackup(snapshot)
                assertEquals(0L, owner.maintenance.inspect().deliveryIntentCount)
            }
        }
    }

    @Test fun reconciliationRepairsOnlyTheSuppressedIntentAndPreservesExistingReceipt() {
        val target = restored { owner ->
            val id = owner.routeReminderLocalOnly(candidate) { true }.notice.noticeId
            owner.mutateReminderNoticeAttentionWithReceipt("sent-seen", id, 1L, NoticeAttention.SEEN) { true }
        }
        open(target).use { owner ->
            val previous = owner.readNotices().single()
            val routed = owner.routeReminderLocalOnly(candidate) { true }
            assertFalse(routed.created)
            assertEquals(previous, routed.notice)
            assertEquals(DeliveryEffectState.SUPPRESSED_POLICY, routed.deliveryIntent.state)
            assertEquals(1L, owner.maintenance.inspect().deliveryIntentCount)
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, true),
                owner.mutateReminderNoticeAttentionWithReceipt(
                    "sent-seen", previous.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertTrue(owner.readNotificationProjectionCandidateIds(8).isEmpty())
        }
    }

    @Test fun anExplicitReceiptClickCanRepairMissingProjectionInTheSameTransaction() {
        val target = restored { it.routeReminderLocalOnly(candidate) { true } }
        open(target).use { owner ->
            val id = owner.readNotices().single().noticeId
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Denied,
                owner.mutateReminderNoticeAttentionWithReceipt("a-denied", id, 1L, NoticeAttention.SEEN) { false },
            )
            assertEquals(0L, owner.maintenance.inspect().deliveryIntentCount)
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.Committed(2L, false),
                owner.mutateReminderNoticeAttentionWithReceipt("restored-click", id, 1L, NoticeAttention.SEEN) { true },
            )
            assertEquals(1L, owner.maintenance.inspect().deliveryIntentCount)
            assertTrue(owner.readNotificationProjectionCandidateIds(8).isEmpty())
        }
        open(target).use { owner -> assertEquals(1, owner.readIsolatedStage4NoticeReceipts().size) }
    }

    @Test fun sourceResolutionRestoresACancelledProjectionWithoutChangingResolvedNotice() {
        val target = restored { owner ->
            val id = owner.routeReminderLocalOnly(candidate) { true }.notice.noticeId
            owner.resolveReminderLocalOnly(id) { true }
        }
        open(target).use { owner ->
            val previous = owner.readNotices().single()
            val resolved = assertInstanceOf(
                NoticeResolveResult.AlreadyResolved::class.java,
                owner.resolveReminderLocalOnly(previous.noticeId) { true },
            )
            assertEquals(previous, resolved.notice)
            assertEquals(DeliveryDesiredAction.CANCEL, resolved.deliveryIntent.desiredAction)
            assertEquals(DeliveryEffectState.SUPPRESSED_POLICY, resolved.deliveryIntent.state)
            assertFalse(owner.routeReminderLocalOnly(candidate) { true }.created)
            assertTrue(owner.readNotificationProjectionCandidateIds(8).isEmpty())
        }
    }

    @Test fun aForeignRestoredNoticeCannotAcquireALocalProjection() {
        val target = restored { owner ->
            owner.route(
                candidate.copy(destinationRef = DestinationRef.of("other:notice")),
                NoticeMaterializationIds(
                    NoticeId.of("foreign-restored"), DeliveryIntentId.of("foreign-intent"), EffectId("foreign-effect"),
                ),
            )
        }
        open(target).use { owner ->
            val previous = owner.readNotices().single()
            assertEquals(
                S4W03NoticeAttentionReceiptV4Result.NotFound,
                owner.mutateReminderNoticeAttentionWithReceipt(
                    "foreign-click", previous.noticeId, 1L, NoticeAttention.SEEN,
                ) { true },
            )
            assertEquals(NoticeResolveResult.NotFound, owner.resolveReminderLocalOnly(previous.noticeId) { true })
            assertEquals(previous, owner.readNotices().single())
            assertEquals(0L, owner.maintenance.inspect().deliveryIntentCount)
        }
    }
}
