package ar.com.faro.v5.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2 Android integration: real five-owner SQLite, but an EXCLUSIVE temporary
 * generation root. Never exercises the shipping V1 pointer, application data,
 * real OS projections, or V2_ACTIVE_ENABLED.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5F2IsolatedActivationRecoveryInstrumentationTest {
    private fun isolated(block: (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f2-isolated-")
        try {
            block(Fixture(directory))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class Fixture(val root: Path) {
        val layout = FileStorageGenerationLayout(root)
        val pointer = FileActiveGenerationPointer(root)
        val journal = FileBootstrapJournal(root)
        val gate = SingleProcessMaterialWriteGate()
        val quiescence = MaterialWriteGateQuiescence(gate)
        val previous = StorageGenerationId("f2-original-v1")
        val target = StorageGenerationId("f2-candidate-v2")
        val operation = MaintenanceOperationId("f2-restart-recovery")
        val stage: StagedStorageGeneration
        val replays = mutableListOf<String>()
        val projections = mutableListOf<String>()

        init {
            val old = layout.stage(StorageGenerationDescriptor(previous, "schema-v1", 1L))
            GenerationStoreFiles.LEGACY_V1.forEach {
                Files.write(old.directory.resolve(it), byteArrayOf(1))
            }
            checkNotNull(pointer.compareAndSet(null, previous))
            stage = layout.stageWorkspaceV2(
                StorageGenerationDescriptor(target, "schema-five-store-1", 2L),
            )
            RealityStore(stage.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { }
            CommercialEvidenceStore(stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
            WebEvidenceStore(stage.directory.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
            AuxiliaryDeliveryStore(stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
            ConversationWorkspaceStore(stage.directory.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }

            var current = BootstrapJournalEntry(
                operation, MaintenanceOperationKind.RESTORE, previous, target,
                BootstrapPhase.PREPARING,
            )
            journal.create(current)
            for (next in listOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED)) {
                val updated = BootstrapTransitionPolicy.transition(current, next)
                check(journal.compareAndSet(current, updated))
                current = updated
            }
            gate.closeMaterialWriteAdmission()
        }

        private val admission = FaroV5PreSwitchHostAdmission(
            layout = layout,
            activePointer = pointer,
            // This is a test-local supported-host simulation, NOT the shipping flag.
            v2HostCertified = true,
            verifyQuarantinedV2 = { candidate ->
                FaroV5QuarantinedV2GraphAdmission.canonical(
                    gate,
                    Supplier {
                        val active = checkNotNull(pointer.read()).generationId
                        layout.requireStaged(active).directory
                    },
                ).open(candidate).use { it.verifyHealthy() }
            },
        )

        fun coordinator(
            crashAfterPointer: Boolean = false,
            failTargetHealth: Boolean = false,
            crashAfterRollbackPointer: Boolean = false,
        ) =
            ValidatedGenerationActivationCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = layout,
                quiescence = quiescence,
                generationSwitch = GenerationSwitchCoordinator(
                    journal = journal,
                    activePointer = pointer,
                    generationLayout = layout,
                    faultInjector = { point ->
                        if (crashAfterPointer &&
                            point == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
                        ) error("synthetic process death immediately after pointer publication")
                        if (crashAfterRollbackPointer &&
                            point == GenerationSwitchFaultPoint.AFTER_ROLLBACK_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
                        ) error("synthetic process death immediately after rollback pointer publication")
                    },
                ),
                runtime = GenerationRuntimeActivationPort { _, generationId ->
                    if (generationId == previous) true else if (generationId != target || failTargetHealth) false
                    else runCatching {
                        FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
                            layout.requireStaged(generationId),
                            CanonicalPreparedPlanEnvelopeVerifier,
                            gate,
                        ).use { graph ->
                            graph.generationId == target.value && graph.verifyHealthy()
                        }
                    }.getOrDefault(false)
                },
                replayFence = GenerationReplayFencePort { _, generationId ->
                    check(generationId == target)
                    replays += generationId.value
                },
                projections = GenerationProjectionReconciliationPort { _, generationId ->
                    // Rollback must reproject the verified previous V1 authority.
                    check(generationId == target || generationId == previous)
                    projections += generationId.value
                },
                preSwitchAdmission = admission,
            )
    }

    @Test
    fun crashAfterPointerPublishRecoversFiveStoresAndDoesNotReplayTwice() = isolated { f ->
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        val failure = runCatching {
            f.coordinator(crashAfterPointer = true).activate(f.operation)
        }.exceptionOrNull()
        assertTrue("fault injection must interrupt activation", failure is IllegalStateException)
        assertEquals(f.target, checkNotNull(f.pointer.read()).generationId)
        assertEquals(BootstrapPhase.SWITCH_PREPARED, checkNotNull(f.journal.read(f.operation)).phase)
        assertFalse(f.gate.isMaterialWriteAdmissionOpen())
        assertTrue(f.replays.isEmpty())
        assertTrue(f.projections.isEmpty())

        // Reconstruct coordinator as a fresh runtime would after process death.
        val result = f.coordinator().recover(f.operation)
        assertEquals(
            GenerationActivationResult.Activated(
                f.operation, MaintenanceOperationKind.RESTORE, f.target, recovered = true,
            ),
            result,
        )
        assertEquals(f.target, checkNotNull(f.pointer.read()).generationId)
        val completed = checkNotNull(f.journal.read(f.operation))
        assertEquals(BootstrapPhase.COMPLETED, completed.phase)
        assertTrue(completed.materialWritesAccepted)
        assertEquals(listOf(f.target.value), f.replays)
        assertEquals(listOf(f.target.value), f.projections)
        assertFalse("runtime health alone must never open startup write admission",
            f.gate.isMaterialWriteAdmissionOpen())
        assertEquals(result, f.coordinator().recover(f.operation))
        assertEquals(1, f.replays.size)
        assertEquals(1, f.projections.size)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test
    fun missingWorkspaceBlocksActivationWithoutMovingV1PointerOrAdmittingWrites() = isolated { f ->
        val before = f.pointer.read()
        Files.delete(f.stage.directory.resolve(GenerationStoreFiles.WORKSPACE))
        val journalBefore = f.journal.read(f.operation)
        val result = f.coordinator().activate(f.operation)
        assertEquals(
            GenerationActivationResult.Blocked(
                f.operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
            ),
            result,
        )
        assertEquals(before, f.pointer.read())
        // The coordinator must not manufacture missing SQLite authority.
        assertFalse(Files.exists(f.stage.directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertEquals(journalBefore, f.journal.read(f.operation))
        assertTrue(f.replays.isEmpty())
        assertTrue(f.projections.isEmpty())
        assertFalse(f.gate.isMaterialWriteAdmissionOpen())
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test
    fun postSwitchHealthReadsActiveCommittedWalAndKeepsWriteGateClosed() = isolated { f ->
        val validated = checkNotNull(f.journal.read(f.operation))
        val prepared = BootstrapTransitionPolicy.transition(
            validated, BootstrapPhase.SWITCH_PREPARED,
        )
        assertTrue(f.journal.compareAndSet(validated, prepared))
        val switched = GenerationSwitchCoordinator(
            f.journal, f.pointer, f.layout,
        ).switch(f.operation)
        assertEquals(BootstrapPhase.SWITCHED, switched.phase)
        assertEquals(f.target, checkNotNull(f.pointer.read()).generationId)

        val workspace = f.stage.directory.resolve(GenerationStoreFiles.WORKSPACE)
        SQLiteDatabase.openDatabase(workspace.toString(), null, SQLiteDatabase.OPEN_READWRITE).use { writer ->
            assertTrue(writer.enableWriteAheadLogging())
            writer.execSQL(
                """INSERT INTO workspace_conversation
                   (id, title, activity_ms, pinned, archived, pending_review)
                   VALUES ('f2-wal-visible', 'F2 committed WAL', 1, 0, 0, 0)"""
            )
            assertTrue(Files.exists(f.stage.directory.resolve(
                GenerationStoreFiles.WORKSPACE + "-wal",
            )))
            val health = FaroV5V2RecoveryRuntimeHealthOwner.canonical(
                f.layout, f.journal, f.pointer, f.quiescence, f.gate,
            )
            assertEquals(V2RuntimeHealth.Verified(f.target.value),
                health.verify(f.operation))
            assertEquals(switched, f.journal.read(f.operation))
            assertEquals(f.target, checkNotNull(f.pointer.read()).generationId)
            assertFalse("post-switch health cannot admit material writes",
                f.gate.isMaterialWriteAdmissionOpen())
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        }
    }

    @Test
    fun targetHealthFailureRollsBackRealFiveStorePointerBeforeAnyWriteAdmission() = isolated { f ->
        val result = f.coordinator(failTargetHealth = true).activate(f.operation)
        assertTrue("runtime health failure must roll back", result is GenerationActivationResult.RolledBack)
        val rolled = result as GenerationActivationResult.RolledBack
        assertEquals(GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED, rolled.failedStep)
        assertEquals(f.previous, rolled.restoredGenerationId)
        assertEquals(f.previous, checkNotNull(f.pointer.read()).generationId)
        val journal = checkNotNull(f.journal.read(f.operation))
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.phase)
        assertFalse(journal.materialWritesAccepted)
        assertTrue("target replay must never run", f.replays.isEmpty())
        assertEquals(listOf(f.previous.value), f.projections)
        assertFalse(f.gate.isMaterialWriteAdmissionOpen())
        assertEquals(rolled.restoredGenerationId,
            (f.coordinator().recover(f.operation) as GenerationActivationResult.RolledBack)
                .restoredGenerationId)
        assertEquals(listOf(f.previous.value), f.projections)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test
    fun crashAfterRollbackPointerPublicationRecoversWithoutTargetReplay() = isolated { f ->
        val failure = runCatching {
            f.coordinator(
                failTargetHealth = true,
                crashAfterRollbackPointer = true,
            ).activate(f.operation)
        }.exceptionOrNull()
        assertTrue("rollback fault must interrupt activation", failure is IllegalStateException)
        assertEquals(f.previous, checkNotNull(f.pointer.read()).generationId)
        assertEquals(BootstrapPhase.ROLLBACK_PREPARED,
            checkNotNull(f.journal.read(f.operation)).phase)
        assertFalse(f.gate.isMaterialWriteAdmissionOpen())
        assertTrue(f.replays.isEmpty())

        val recovered = f.coordinator().recover(f.operation)
        assertTrue(recovered is GenerationActivationResult.RolledBack)
        assertEquals(f.previous,
            (recovered as GenerationActivationResult.RolledBack).restoredGenerationId)
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED,
            checkNotNull(f.journal.read(f.operation)).phase)
        assertEquals(f.previous, checkNotNull(f.pointer.read()).generationId)
        assertEquals(listOf(f.previous.value), f.projections)
        assertTrue(f.replays.isEmpty())
        assertFalse(f.gate.isMaterialWriteAdmissionOpen())
        assertEquals(f.previous,
            (f.coordinator().recover(f.operation) as GenerationActivationResult.RolledBack)
                .restoredGenerationId)
        assertEquals(1, f.projections.size)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
}
