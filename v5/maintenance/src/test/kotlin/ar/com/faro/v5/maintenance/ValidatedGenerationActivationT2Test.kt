package ar.com.faro.v5.maintenance

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ValidatedGenerationActivationT2Test {
    @Test
    fun `validated restore activates target reconciles and only then accepts writes`(
        @TempDir tempDir: Path,
    ) {
        val fixture = fixture(tempDir, MaintenanceOperationKind.RESTORE)

        val result = fixture.coordinator.activate(fixture.operationId)

        assertEquals(
            GenerationActivationResult.Activated(
                operationId = fixture.operationId,
                kind = MaintenanceOperationKind.RESTORE,
                targetGenerationId = fixture.target,
                recovered = false,
            ),
            result,
        )
        assertEquals(fixture.target, fixture.pointer.read()!!.generationId)
        val journal = fixture.journal.read(fixture.operationId)!!
        assertEquals(BootstrapPhase.COMPLETED, journal.phase)
        assertTrue(journal.materialWritesAccepted)
        assertEquals(listOf(fixture.target), fixture.runtime.activations)
        assertEquals(listOf(fixture.target), fixture.replayFence.generations)
        assertEquals(listOf(fixture.target), fixture.projections.generations)
        assertEquals(1, fixture.quiescence.entries)
        assertEquals(1, fixture.quiescence.exits)
    }

    @Test
    fun `completed unfenced recovery revalidates replay and projections before accepting writes`(
        @TempDir tempDir: Path,
    ) {
        val base = base(tempDir, MaintenanceOperationKind.RESTORE)
        var current = base.journal.read(base.operationId)!!
        var updated = BootstrapTransitionPolicy.transition(current, BootstrapPhase.SWITCH_PREPARED)
        check(base.journal.compareAndSet(current, updated))
        current = updated

        val switch =
            GenerationSwitchCoordinator(
                base.journal,
                base.pointer,
                base.layout,
            )
        current = switch.switch(base.operationId)
        updated = BootstrapTransitionPolicy.transition(current, BootstrapPhase.HEALTH_VERIFIED)
        check(base.journal.compareAndSet(current, updated))
        current = updated
        updated = BootstrapTransitionPolicy.transition(current, BootstrapPhase.COMPLETED)
        check(base.journal.compareAndSet(current, updated))

        val runtime = RecordingRuntime()
        val replay = RecordingReplayFence()
        val projections = RecordingProjections()
        val recovered =
            coordinator(
                base = base,
                switch = switch,
                quiescence = CountingQuiescence(),
                runtime = runtime,
                replayFence = replay,
                projections = projections,
            ).recover(base.operationId)

        assertTrue(recovered is GenerationActivationResult.Activated)
        assertTrue(base.journal.read(base.operationId)!!.materialWritesAccepted)
        assertEquals(listOf(base.target), runtime.activations)
        assertEquals(listOf(base.target), replay.generations)
        assertEquals(listOf(base.target), projections.generations)
    }

    @Test
    fun `target runtime health failure rolls back pointer runtime and projections before writes`(
        @TempDir tempDir: Path,
    ) {
        val runtime = RecordingRuntime(failOn = setOf(StorageGenerationId("generation-new")))
        val fixture =
            fixture(
                tempDir = tempDir,
                kind = MaintenanceOperationKind.MIGRATION,
                runtime = runtime,
            )

        val result = fixture.coordinator.activate(fixture.operationId)

        assertTrue(result is GenerationActivationResult.RolledBack)
        result as GenerationActivationResult.RolledBack
        assertEquals(GenerationActivationBlockReason.RUNTIME_HEALTH_FAILED, result.failedStep)
        assertEquals(fixture.previous, result.restoredGenerationId)
        assertEquals(fixture.previous, fixture.pointer.read()!!.generationId)

        val journal = fixture.journal.read(fixture.operationId)!!
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.phase)
        assertFalse(journal.materialWritesAccepted)
        assertFalse(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(journal))

        assertEquals(
            listOf(fixture.target, fixture.previous),
            fixture.runtime.activations,
        )
        assertTrue(fixture.replayFence.generations.isEmpty())
        assertEquals(listOf(fixture.previous), fixture.projections.generations)
    }

    @Test
    fun `projection failure after replay fence still rolls back and reprojects previous generation`(
        @TempDir tempDir: Path,
    ) {
        val projections =
            RecordingProjections(
                failuresRemainingByGeneration =
                    mutableMapOf(StorageGenerationId("generation-new") to 1)
            )
        val fixture =
            fixture(
                tempDir = tempDir,
                kind = MaintenanceOperationKind.RESTORE,
                projections = projections,
            )

        val result = fixture.coordinator.activate(fixture.operationId)

        assertTrue(result is GenerationActivationResult.RolledBack)
        result as GenerationActivationResult.RolledBack
        assertEquals(
            GenerationActivationBlockReason.PROJECTION_RECONCILIATION_FAILED,
            result.failedStep,
        )
        assertEquals(fixture.previous, fixture.pointer.read()!!.generationId)
        assertEquals(listOf(fixture.target), fixture.replayFence.generations)
        assertEquals(
            listOf(fixture.target, fixture.previous),
            fixture.projections.generations,
        )
        assertEquals(
            listOf(fixture.target, fixture.previous),
            fixture.runtime.activations,
        )
    }

    @Test
    fun `crash after target pointer publish recovers switch and completes activation`(
        @TempDir tempDir: Path,
    ) {
        val base = base(tempDir, MaintenanceOperationKind.RESTORE)
        val crashingSwitch =
            GenerationSwitchCoordinator(
                journal = base.journal,
                activePointer = base.pointer,
                generationLayout = base.layout,
                faultInjector = { point ->
                    if (
                        point ==
                        GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
                    ) {
                        error("simulated switch crash")
                    }
                },
            )
        val runtime = RecordingRuntime()
        val replay = RecordingReplayFence()
        val projections = RecordingProjections()
        val quiescence = CountingQuiescence()
        val crashing =
            coordinator(
                base = base,
                switch = crashingSwitch,
                quiescence = quiescence,
                runtime = runtime,
                replayFence = replay,
                projections = projections,
            )

        assertThrows(IllegalStateException::class.java) {
            crashing.activate(base.operationId)
        }
        assertEquals(base.target, base.pointer.read()!!.generationId)
        assertEquals(
            BootstrapPhase.SWITCH_PREPARED,
            base.journal.read(base.operationId)!!.phase,
        )

        val recovered =
            coordinator(
                base = base,
                switch =
                    GenerationSwitchCoordinator(
                        base.journal,
                        base.pointer,
                        base.layout,
                    ),
                quiescence = quiescence,
                runtime = runtime,
                replayFence = replay,
                projections = projections,
            ).recover(base.operationId)

        assertEquals(
            GenerationActivationResult.Activated(
                operationId = base.operationId,
                kind = MaintenanceOperationKind.RESTORE,
                targetGenerationId = base.target,
                recovered = true,
            ),
            recovered,
        )
        assertEquals(BootstrapPhase.COMPLETED, base.journal.read(base.operationId)!!.phase)
        assertTrue(base.journal.read(base.operationId)!!.materialWritesAccepted)
    }

    @Test
    fun `crash after rollback pointer publish recovers rolled back state without target replay`(
        @TempDir tempDir: Path,
    ) {
        val base = base(tempDir, MaintenanceOperationKind.MIGRATION)
        val crashingSwitch =
            GenerationSwitchCoordinator(
                journal = base.journal,
                activePointer = base.pointer,
                generationLayout = base.layout,
                faultInjector = { point ->
                    if (
                        point ==
                        GenerationSwitchFaultPoint.AFTER_ROLLBACK_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
                    ) {
                        error("simulated rollback crash")
                    }
                },
            )
        val runtime = RecordingRuntime(failOn = setOf(base.target))
        val replay = RecordingReplayFence()
        val projections = RecordingProjections()
        val quiescence = CountingQuiescence()
        val crashing =
            coordinator(
                base = base,
                switch = crashingSwitch,
                quiescence = quiescence,
                runtime = runtime,
                replayFence = replay,
                projections = projections,
            )

        assertThrows(IllegalStateException::class.java) {
            crashing.activate(base.operationId)
        }
        assertEquals(base.previous, base.pointer.read()!!.generationId)
        assertEquals(
            BootstrapPhase.ROLLBACK_PREPARED,
            base.journal.read(base.operationId)!!.phase,
        )

        runtime.failOn = emptySet()
        val recovered =
            coordinator(
                base = base,
                switch =
                    GenerationSwitchCoordinator(
                        base.journal,
                        base.pointer,
                        base.layout,
                    ),
                quiescence = quiescence,
                runtime = runtime,
                replayFence = replay,
                projections = projections,
            ).recover(base.operationId)

        assertTrue(recovered is GenerationActivationResult.RolledBack)
        recovered as GenerationActivationResult.RolledBack
        assertEquals(base.previous, recovered.restoredGenerationId)
        assertEquals(GenerationActivationBlockReason.ROLLED_BACK, recovered.failedStep)
        assertEquals(
            BootstrapPhase.ROLLBACK_VERIFIED,
            base.journal.read(base.operationId)!!.phase,
        )
        assertFalse(base.journal.read(base.operationId)!!.materialWritesAccepted)
        assertEquals(base.previous, base.pointer.read()!!.generationId)
        assertTrue(replay.generations.isEmpty())
        assertEquals(listOf(base.previous), projections.generations)
    }

    @Test
    fun `rolled back journal stays pending until previous runtime and projections are verified`(
        @TempDir tempDir: Path,
    ) {
        val base = base(tempDir, MaintenanceOperationKind.MIGRATION)
        var current = base.journal.read(base.operationId)!!
        var updated =
            BootstrapTransitionPolicy.transition(
                current,
                BootstrapPhase.SWITCH_PREPARED,
            )
        check(base.journal.compareAndSet(current, updated))
        current = updated

        val generationSwitch =
            GenerationSwitchCoordinator(
                base.journal,
                base.pointer,
                base.layout,
            )
        current = generationSwitch.switch(base.operationId)
        assertEquals(BootstrapPhase.SWITCHED, current.phase)

        val rolledBack =
            generationSwitch.rollbackToPreviousIfEligible(base.operationId)
        assertEquals(BootstrapPhase.ROLLED_BACK_POST_SWITCH, rolledBack.phase)
        assertEquals(
            listOf(base.operationId),
            base.journal.pending().map { it.operationId },
        )

        val runtime = RecordingRuntime()
        val replay = RecordingReplayFence()
        val projections = RecordingProjections()
        val recovered =
            coordinator(
                base = base,
                switch = generationSwitch,
                quiescence = CountingQuiescence(),
                runtime = runtime,
                replayFence = replay,
                projections = projections,
            ).recoverPending()

        assertEquals(1, recovered.size)
        assertTrue(recovered.single() is GenerationActivationResult.RolledBack)
        assertEquals(
            BootstrapPhase.ROLLBACK_VERIFIED,
            base.journal.read(base.operationId)!!.phase,
        )
        assertTrue(base.journal.pending().isEmpty())
        assertEquals(listOf(base.previous), runtime.activations)
        assertEquals(listOf(base.previous), projections.generations)
        assertTrue(replay.generations.isEmpty())
    }

    @Test
    fun `reset bootstrap cannot enter generic restore migration activation`(
        @TempDir tempDir: Path,
    ) {
        val fixture = fixture(tempDir, MaintenanceOperationKind.RESET)

        val result = fixture.coordinator.activate(fixture.operationId)

        assertEquals(
            GenerationActivationResult.Blocked(
                fixture.operationId,
                GenerationActivationBlockReason.UNSUPPORTED_OPERATION_KIND,
            ),
            result,
        )
        assertEquals(fixture.previous, fixture.pointer.read()!!.generationId)
    }

    @Test
    fun `host downgrade after health verification rolls back V2 before write fence`(
        @TempDir tempDir: Path,
    ) {
        val base = base(tempDir, MaintenanceOperationKind.RESTORE)
        val switch = GenerationSwitchCoordinator(base.journal, base.pointer, base.layout)
        val validated = base.journal.read(base.operationId)!!
        val prepared = BootstrapTransitionPolicy.transition(validated, BootstrapPhase.SWITCH_PREPARED)
        check(base.journal.compareAndSet(validated, prepared))
        val switched = switch.switch(base.operationId)
        val verified = BootstrapTransitionPolicy.transition(switched, BootstrapPhase.HEALTH_VERIFIED)
        check(base.journal.compareAndSet(switched, verified))

        val runtime = RecordingRuntime()
        val replay = RecordingReplayFence()
        val projections = RecordingProjections()
        val result = coordinator(
            base, switch, CountingQuiescence(), runtime, replay, projections,
            admission = GenerationPreSwitchAdmissionPort { _, _ -> false },
        ).recover(base.operationId)

        assertTrue(result is GenerationActivationResult.RolledBack)
        assertEquals(
            GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
            (result as GenerationActivationResult.RolledBack).failedStep,
        )
        assertEquals(base.previous, result.restoredGenerationId)
        assertEquals(base.previous, base.pointer.read()!!.generationId)
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, base.journal.read(base.operationId)!!.phase)
        assertFalse(base.journal.read(base.operationId)!!.materialWritesAccepted)
        assertEquals(listOf(base.previous), runtime.activations)
        assertEquals(listOf(base.previous), projections.generations)
        assertTrue(replay.generations.isEmpty())
    }

    @Test
    fun `host downgrade after accepted writes fails closed and never rolls back`(
        @TempDir tempDir: Path,
    ) {
        val base = base(tempDir, MaintenanceOperationKind.RESTORE)
        val switch = GenerationSwitchCoordinator(base.journal, base.pointer, base.layout)
        val validated = base.journal.read(base.operationId)!!
        val prepared = BootstrapTransitionPolicy.transition(validated, BootstrapPhase.SWITCH_PREPARED)
        check(base.journal.compareAndSet(validated, prepared))
        val switched = switch.switch(base.operationId)
        val verified = BootstrapTransitionPolicy.transition(switched, BootstrapPhase.HEALTH_VERIFIED)
        check(base.journal.compareAndSet(switched, verified))
        val accepted = switch.markMaterialWritesAccepted(base.operationId)

        val runtime = RecordingRuntime()
        val replay = RecordingReplayFence()
        val projections = RecordingProjections()
        val result = coordinator(
            base, switch, CountingQuiescence(), runtime, replay, projections,
            admission = GenerationPreSwitchAdmissionPort { _, _ -> false },
        ).recover(base.operationId)

        assertEquals(
            GenerationActivationResult.Blocked(
                base.operationId, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
            ),
            result,
        )
        assertEquals(base.target, base.pointer.read()!!.generationId)
        assertEquals(accepted, base.journal.read(base.operationId))
        assertTrue(replay.generations.isEmpty())
        assertTrue(runtime.activations.isEmpty())
        assertTrue(projections.generations.isEmpty())
    }

    private fun fixture(
        tempDir: Path,
        kind: MaintenanceOperationKind,
        runtime: RecordingRuntime = RecordingRuntime(),
        replayFence: RecordingReplayFence = RecordingReplayFence(),
        projections: RecordingProjections = RecordingProjections(),
    ): Fixture {
        val base = base(tempDir, kind)
        val quiescence = CountingQuiescence()
        return Fixture(
            previous = base.previous,
            target = base.target,
            operationId = base.operationId,
            layout = base.layout,
            pointer = base.pointer,
            journal = base.journal,
            quiescence = quiescence,
            runtime = runtime,
            replayFence = replayFence,
            projections = projections,
            coordinator =
                coordinator(
                    base = base,
                    switch =
                        GenerationSwitchCoordinator(
                            base.journal,
                            base.pointer,
                            base.layout,
                        ),
                    quiescence = quiescence,
                    runtime = runtime,
                    replayFence = replayFence,
                    projections = projections,
                ),
        )
    }

    private fun base(
        tempDir: Path,
        kind: MaintenanceOperationKind,
    ): Base {
        val previous = StorageGenerationId("generation-old")
        val target = StorageGenerationId("generation-new")
        val operationId = MaintenanceOperationId("activate-${kind.name.lowercase()}")
        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(
            StorageGenerationDescriptor(
                generationId = previous,
                schemaSetFingerprint = "schema-v1",
                createdAtEpochMillis = 1L,
            )
        )
        layout.stage(
            StorageGenerationDescriptor(
                generationId = target,
                schemaSetFingerprint = "schema-v1",
                createdAtEpochMillis = 2L,
            )
        )
        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(null, previous)
        val journal = FileBootstrapJournal(tempDir)
        var entry =
            BootstrapJournalEntry(
                operationId = operationId,
                kind = kind,
                previousGenerationId = previous,
                targetGenerationId = target,
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(entry)
        for (next in listOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED)) {
            val updated = BootstrapTransitionPolicy.transition(entry, next)
            check(journal.compareAndSet(entry, updated))
            entry = updated
        }
        return Base(previous, target, operationId, layout, pointer, journal)
    }

    private fun coordinator(
        base: Base,
        switch: GenerationSwitchCoordinator,
        quiescence: CountingQuiescence,
        runtime: RecordingRuntime,
        replayFence: RecordingReplayFence,
        projections: RecordingProjections,
        admission: GenerationPreSwitchAdmissionPort =
            GenerationPreSwitchAdmissionPort { _, _ -> true },
    ): ValidatedGenerationActivationCoordinator =
        ValidatedGenerationActivationCoordinator(
            journal = base.journal,
            activePointer = base.pointer,
            generationLayout = base.layout,
            quiescence = quiescence,
            generationSwitch = switch,
            runtime = runtime,
            replayFence = replayFence,
            projections = projections,
            // Explicit test admission allows simulated supported or unsupported hosts.
            preSwitchAdmission = admission,
        )

    private data class Base(
        val previous: StorageGenerationId,
        val target: StorageGenerationId,
        val operationId: MaintenanceOperationId,
        val layout: FileStorageGenerationLayout,
        val pointer: FileActiveGenerationPointer,
        val journal: FileBootstrapJournal,
    )

    private data class Fixture(
        val previous: StorageGenerationId,
        val target: StorageGenerationId,
        val operationId: MaintenanceOperationId,
        val layout: FileStorageGenerationLayout,
        val pointer: FileActiveGenerationPointer,
        val journal: FileBootstrapJournal,
        val quiescence: CountingQuiescence,
        val runtime: RecordingRuntime,
        val replayFence: RecordingReplayFence,
        val projections: RecordingProjections,
        val coordinator: ValidatedGenerationActivationCoordinator,
    )

    private class CountingQuiescence : MaterialWriteQuiescencePort {
        var entries = 0
            private set
        var exits = 0
            private set

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            entries += 1
            return try {
                block()
            } finally {
                exits += 1
            }
        }
    }

    private class RecordingRuntime(
        var failOn: Set<StorageGenerationId> = emptySet(),
    ) : GenerationRuntimeActivationPort {
        val activations = mutableListOf<StorageGenerationId>()

        override fun activateAndVerify(
            kind: MaintenanceOperationKind,
            targetGenerationId: StorageGenerationId,
        ): Boolean {
            activations += targetGenerationId
            return targetGenerationId !in failOn
        }
    }

    private class RecordingReplayFence : GenerationReplayFencePort {
        val generations = mutableListOf<StorageGenerationId>()

        override fun install(
            kind: MaintenanceOperationKind,
            targetGenerationId: StorageGenerationId,
        ) {
            generations += targetGenerationId
        }
    }

    private class RecordingProjections(
        private val failuresRemainingByGeneration:
            MutableMap<StorageGenerationId, Int> = mutableMapOf(),
    ) : GenerationProjectionReconciliationPort {
        val generations = mutableListOf<StorageGenerationId>()

        override fun reconcile(
            kind: MaintenanceOperationKind,
            targetGenerationId: StorageGenerationId,
        ) {
            generations += targetGenerationId
            val remaining = failuresRemainingByGeneration[targetGenerationId] ?: 0
            if (remaining > 0) {
                failuresRemainingByGeneration[targetGenerationId] = remaining - 1
                error("simulated projection failure")
            }
        }
    }
}
