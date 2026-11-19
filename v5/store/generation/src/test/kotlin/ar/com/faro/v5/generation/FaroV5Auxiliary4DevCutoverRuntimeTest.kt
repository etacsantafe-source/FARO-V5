package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Auxiliary4DevCutoverRuntimeTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private val pointer get() = FileActiveGenerationPointer(root)
    private val journal get() = FileBootstrapJournal(root)
    private val layout get() = FileStorageGenerationLayout(root)
    private val operation = MaintenanceOperationId("upgrade-auxiliary4")
    private val target = StorageGenerationDescriptor(StorageGenerationId("auxiliary4-target"), "aux4-dev", 200L)

    private fun acquire(
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        afterAuthorization: () -> Unit = {},
        targetHealthy: () -> Boolean = { true },
    ) = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, verifier, fault, afterAuthorization, targetHealthy)

    private fun seed(): ActiveGenerationPointer {
        val preceding = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("finance-preceding")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("finance9-auxiliary3"), "finance9-origin", 100L),
        )
        FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("prepare-origin"), stage, preceding, "a".repeat(64), 1000L,
        )
        AuxiliaryDeliveryStore(stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { owner ->
            owner.routeReminderLocalOnly(NoticeCandidate(
                NoticeKey.of("reminder:cutover-occ"), SourceRef.of("reminder:cutover-rule"),
                OccurrenceRef.of("cutover-occ"), NoticeMateriality.NOTICE, NoticeSensitivity.NORMAL,
                NoticeSummary.of("Aviso ya guardado"), DestinationRef.of("faro:avisos.local"),
            )) { true }
        }
        return checkNotNull(pointer.compareAndSet(preceding.generationId, stage.descriptor.generationId))
    }

    private fun fingerprints(id: StorageGenerationId): Map<String, List<Byte>> {
        val staged = layout.requireStaged(id)
        return GenerationStoreFiles.WORKSPACE_V2.associateWith {
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(staged.directory.resolve(it))).toList()
        }
    }

    @Test fun confirmsAndMigratesOnlyTheCopyAndReopensExactAuxiliary4() {
        val origin = seed()
        val before = fingerprints(origin.generationId)
        acquire().use { runtime ->
            val selected = runtime.startAuxiliary4(operation, target, origin, 2000L) { true }
            assertTrue(selected.auxiliary4)
            assertEquals(FaroV5Finance9DevCutoverRuntime.Status.ACTIVE_NINE, selected.status)
            assertEquals(ActiveGenerationPointer(origin.sequence + 1, target.generationId), selected.pointer)
            assertTrue(runtime.isCurrent(selected))
            assertEquals(before, fingerprints(origin.generationId))
        }
        acquire().use { runtime ->
            val selected = checkNotNull(runtime.recover())
            val graph = runtime.openCommittedFinance9(selected)
            assertTrue(graph.verifyHealthy())
            assertEquals(4, graph.auxiliary.maintenance.inspect().schemaVersion)
            assertEquals("Aviso ya guardado", graph.auxiliary.readNotices().single().summary.value)
            assertEquals(1L, graph.auxiliary.maintenance.inspect().deliveryIntentCount)
            assertTrue(FileGenerationReplayFence(root).isInstalled(MaintenanceOperationKind.RESTORE, target.generationId))
        }
        assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
    }

    @Test fun missingInitialConfirmationCreatesNoTicketOrStage() {
        val origin = seed()
        acquire().use { runtime ->
            assertThrows(IllegalStateException::class.java) {
                runtime.startAuxiliary4(operation, target, origin, 2000L) { false }
            }
            assertNull(runtime.recover())
        }
        assertEquals(origin, pointer.read())
        assertNull(journal.read(operation))
        assertFalse(Files.exists(root.resolve("generations").resolve(target.generationId.value)))
        assertFalse(Files.exists(root.resolve("bootstrap/auxiliary4-dev-cutover-v1.bin")))
    }

    @Test fun revokingConfirmationAfterCopyRetainsAndReopensAuxiliary3() {
        val origin = seed()
        var checks = 0
        acquire().use { runtime ->
            val selected = runtime.startAuxiliary4(operation, target, origin, 2000L) { ++checks == 1 }
            assertEquals(FaroV5Finance9DevCutoverRuntime.Status.ORIGIN_RETAINED, selected.status)
            assertEquals(origin, selected.pointer)
        }
        acquire().use { runtime ->
            val selected = checkNotNull(runtime.recover())
            assertTrue(runtime.isCurrent(selected))
            assertEquals(3, runtime.openCommittedFinance9(selected).auxiliary.maintenance.inspect().schemaVersion)
        }
        assertEquals(BootstrapPhase.ABORTED_PRE_SWITCH, journal.read(operation)?.phase)
    }

    @Test fun crashAfterPointerPublishRecoversSameSelectionWithoutSecondSwitch() {
        val origin = seed()
        acquire(fault = {
            if (it == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) {
                error("injected interruption")
            }
        }).use { runtime ->
            assertThrows(IllegalStateException::class.java) {
                runtime.startAuxiliary4(operation, target, origin, 2000L) { true }
            }
        }
        val publishedPointer = ActiveGenerationPointer(origin.sequence + 1, target.generationId)
        assertEquals(publishedPointer, pointer.read())
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(operation)?.phase)
        acquire().use { runtime ->
            assertEquals(publishedPointer, runtime.recover()?.pointer)
            assertEquals(publishedPointer, runtime.recover()?.pointer)
        }
        assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        assertTrue(journal.read(operation)?.materialWritesAccepted == true)
    }

    @Test fun failedTargetHealthRollsBackToOriginalAuxiliary3BeforeAcceptingWrites() {
        val origin = seed()
        acquire(targetHealthy = { false }).use { runtime ->
            val selected = runtime.startAuxiliary4(operation, target, origin, 2000L) { true }
            assertEquals(FaroV5Finance9DevCutoverRuntime.Status.ROLLED_BACK, selected.status)
            assertEquals(ActiveGenerationPointer(origin.sequence + 2, origin.generationId), selected.pointer)
            assertTrue(runtime.isCurrent(selected))
        }
        acquire().use { runtime ->
            val selected = checkNotNull(runtime.recover())
            val graph = runtime.openCommittedFinance9(selected)
            assertEquals(3, graph.auxiliary.maintenance.inspect().schemaVersion)
            assertEquals("Aviso ya guardado", graph.auxiliary.readNotices().single().summary.value)
        }
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(operation)?.phase)
        assertFalse(FileGenerationReplayFence(root).isInstalled(MaintenanceOperationKind.RESTORE, target.generationId))
    }

    @Test fun changingTheStagedContentAfterAuthorizationNeverMovesThePointer() {
        val origin = seed()
        acquire(afterAuthorization = {
            val stage = layout.requireStaged(target.generationId)
            BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use {
                it.execSQL("UPDATE delivery_notice SET summary='changed after authorization'")
            }
        }).use { runtime ->
            assertThrows(IllegalStateException::class.java) {
                runtime.startAuxiliary4(operation, target, origin, 2000L) { true }
            }
        }
        assertEquals(origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        acquire().use { runtime ->
            assertThrows(IllegalStateException::class.java) { runtime.recover() }
        }
        assertEquals(origin, pointer.read())
    }
}
