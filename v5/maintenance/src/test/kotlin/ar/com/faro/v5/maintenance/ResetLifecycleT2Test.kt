package ar.com.faro.v5.maintenance

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ResetLifecycleT2Test {
    @Test
    fun `full reset plan is exact canonical delete preserve inventory`() {
        val plan =
            ResetPlanBuilder.fullReset(
                sourceGenerationId = StorageGenerationId("generation-old"),
                targetGenerationId = StorageGenerationId("generation-reset"),
                schemaSetFingerprint = "schema-v1",
                generatedAtEpochMillis = 100L,
            )

        assertEquals(ResetInventoryPolicy.FULL_RESET, plan.inventory)
        assertTrue(ResetPlanBuilder.matches(plan))
        assertEquals(
            ResetInventoryDisposition.DELETE,
            plan.inventory.single {
                it.target == ResetInventoryTarget.STORAGE_GENERATION_AUTHORITY
            }.disposition,
        )
        assertEquals(
            ResetInventoryDisposition.PRESERVE,
            plan.inventory.single {
                it.target == ResetInventoryTarget.OS_PERMISSION_DECISIONS
            }.disposition,
        )

        val changedTarget =
            ResetPlanBuilder.fullReset(
                sourceGenerationId = StorageGenerationId("generation-old"),
                targetGenerationId = StorageGenerationId("generation-reset-2"),
                schemaSetFingerprint = "schema-v1",
                generatedAtEpochMillis = 100L,
            )
        assertNotEquals(plan.planFingerprint, changedTarget.planFingerprint)
    }

    @Test
    fun `approval mismatch blocks before durable reset attempt`(
        @TempDir tempDir: Path,
    ) {
        val fixture = fixture(tempDir)
        val wrongApproval =
            ResetApproval(
                planFingerprint = ResetPlanFingerprint("f".repeat(64)),
                approvedAtEpochMillis = 101L,
            )

        val result =
            fixture.coordinator.execute(
                ResetExecutionRequest(
                    operationId = fixture.operationId,
                    plan = fixture.plan,
                    approval = wrongApproval,
                )
            )

        assertEquals(
            ResetExecutionResult.Blocked(
                fixture.operationId,
                ResetBlockReason.APPROVAL_MISMATCH,
            ),
            result,
        )
        assertEquals(fixture.source, fixture.pointer.read()!!.generationId)
        assertEquals(null, fixture.attempts.read(fixture.operationId))
        assertEquals(null, fixture.journal.read(fixture.operationId))
    }

    @Test
    fun `normal reset switches to verified generation then cleans Vault OS and old generation`(
        @TempDir tempDir: Path,
    ) {
        val vault = CountingVault()
        val os = CountingOs()
        val target = CountingTarget(verified = true)
        val fixture = fixture(tempDir, target = target, vault = vault, os = os)

        val result = fixture.coordinator.execute(fixture.request())

        assertEquals(
            ResetExecutionResult.Completed(
                operationId = fixture.operationId,
                targetGenerationId = fixture.target,
                recovered = false,
            ),
            result,
        )
        assertEquals(fixture.target, fixture.pointer.read()!!.generationId)
        assertEquals(1, target.calls)
        assertEquals(1, vault.calls)
        assertEquals(1, os.calls)

        val bootstrap = fixture.journal.read(fixture.operationId)!!
        assertEquals(BootstrapPhase.COMPLETED, bootstrap.phase)
        assertTrue(bootstrap.materialWritesAccepted)
        assertFalse(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(bootstrap))

        val attempt = fixture.attempts.read(fixture.operationId)!!
        assertTrue(attempt.vaultReconciled)
        assertTrue(attempt.osReconciled)
        assertTrue(attempt.obsoleteGenerationCleaned)
        assertTrue(attempt.completed)

        assertThrows(IllegalStateException::class.java) {
            fixture.layout.requireStaged(fixture.source)
        }
        assertEquals(
            fixture.target,
            fixture.layout.requireStaged(fixture.target).descriptor.generationId,
        )
        assertEquals(1, fixture.quiescence.entries)
        assertEquals(1, fixture.quiescence.exits)
    }

    @Test
    fun `target verification failure aborts pre switch and leaves old authority active`(
        @TempDir tempDir: Path,
    ) {
        val fixture = fixture(tempDir, target = CountingTarget(verified = false))

        val result = fixture.coordinator.execute(fixture.request())

        assertEquals(
            ResetExecutionResult.Blocked(
                fixture.operationId,
                ResetBlockReason.TARGET_VERIFICATION_FAILED,
            ),
            result,
        )
        assertEquals(fixture.source, fixture.pointer.read()!!.generationId)
        assertEquals(
            BootstrapPhase.ABORTED_PRE_SWITCH,
            fixture.journal.read(fixture.operationId)!!.phase,
        )
        assertEquals(
            fixture.source,
            fixture.layout.requireStaged(fixture.source).descriptor.generationId,
        )
        val attempt = fixture.attempts.read(fixture.operationId)!!
        assertFalse(attempt.completed)
        assertFalse(attempt.vaultReconciled)
        assertFalse(attempt.osReconciled)
        assertTrue(fixture.attempts.pending().isNotEmpty())
        assertTrue(fixture.coordinator.recoverPending().isEmpty())
    }

    @Test
    fun `crash after pointer publish recovers reset without reactivating old generation`(
        @TempDir tempDir: Path,
    ) {
        val source = StorageGenerationId("generation-old")
        val targetId = StorageGenerationId("generation-reset")
        val operationId = MaintenanceOperationId("reset-pointer-crash")
        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(descriptor(source, 10L))
        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(null, source)
        val journal = FileBootstrapJournal(tempDir)
        val attempts = FileResetAttemptStore(tempDir)
        val quiescence = CountingQuiescence()
        val target = CountingTarget(true)
        val runtime = CountingRuntime()
        val vault = CountingVault()
        val os = CountingOs()
        val plan = ResetPlanBuilder.fullReset(source, targetId, "schema-v1", 20L)

        val crashingSwitch =
            GenerationSwitchCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = layout,
                faultInjector = { point ->
                    if (
                        point ==
                        GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
                    ) {
                        error("simulated process death")
                    }
                },
            )
        val crashing =
            ResetLifecycleCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = layout,
                quiescence = quiescence,
                targetGeneration = target,
                runtime = runtime,
                attempts = attempts,
                vault = vault,
                os = os,
                generationSwitch = crashingSwitch,
            )

        assertThrows(IllegalStateException::class.java) {
            crashing.execute(
                ResetExecutionRequest(
                    operationId = operationId,
                    plan = plan,
                    approval = ResetApproval(plan.planFingerprint, 21L),
                )
            )
        }

        assertEquals(targetId, pointer.read()!!.generationId)
        val interrupted = journal.read(operationId)!!
        assertEquals(BootstrapPhase.SWITCH_PREPARED, interrupted.phase)
        assertFalse(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(interrupted))

        val recovered =
            ResetLifecycleCoordinator(
                journal = FileBootstrapJournal(tempDir),
                activePointer = FileActiveGenerationPointer(tempDir),
                generationLayout = FileStorageGenerationLayout(tempDir),
                quiescence = quiescence,
                targetGeneration = target,
                runtime = runtime,
                attempts = FileResetAttemptStore(tempDir),
                vault = vault,
                os = os,
            ).recover(operationId)

        assertEquals(
            ResetExecutionResult.Completed(operationId, targetId, recovered = true),
            recovered,
        )
        assertEquals(targetId, FileActiveGenerationPointer(tempDir).read()!!.generationId)
        assertThrows(IllegalStateException::class.java) {
            FileStorageGenerationLayout(tempDir).requireStaged(source)
        }
        assertEquals(BootstrapPhase.COMPLETED, FileBootstrapJournal(tempDir).read(operationId)!!.phase)
    }

    @Test
    fun `post switch runtime activation failure keeps target active and delays cleanup`(
        @TempDir tempDir: Path,
    ) {
        val runtime = CountingRuntime(failuresRemaining = 1)
        val vault = CountingVault()
        val os = CountingOs()
        val fixture =
            fixture(
                tempDir = tempDir,
                runtime = runtime,
                vault = vault,
                os = os,
            )

        val first = fixture.coordinator.execute(fixture.request())

        assertTrue(first is ResetExecutionResult.CleanupPending)
        first as ResetExecutionResult.CleanupPending
        assertEquals(ResetCleanupStep.RUNTIME_ACTIVATION, first.failedStep)
        assertEquals(fixture.target, fixture.pointer.read()!!.generationId)
        assertEquals(
            BootstrapPhase.SWITCHED,
            fixture.journal.read(fixture.operationId)!!.phase,
        )
        assertEquals(
            fixture.source,
            fixture.layout.requireStaged(fixture.source).descriptor.generationId,
        )
        assertEquals(1, runtime.calls)
        assertEquals(0, vault.calls)
        assertEquals(0, os.calls)

        val recovered = fixture.coordinator.recover(fixture.operationId)

        assertEquals(
            ResetExecutionResult.Completed(
                fixture.operationId,
                fixture.target,
                recovered = true,
            ),
            recovered,
        )
        assertEquals(2, runtime.calls)
        assertThrows(IllegalStateException::class.java) {
            fixture.layout.requireStaged(fixture.source)
        }
    }

    @Test
    fun `post switch OS cleanup failure keeps new authority active and recovery resumes idempotently`(
        @TempDir tempDir: Path,
    ) {
        val vault = CountingVault()
        val failingOs = CountingOs(failuresRemaining = 1)
        val fixture = fixture(tempDir, vault = vault, os = failingOs)

        val first = fixture.coordinator.execute(fixture.request())

        assertTrue(first is ResetExecutionResult.CleanupPending)
        first as ResetExecutionResult.CleanupPending
        assertEquals(ResetCleanupStep.OS_PROJECTIONS, first.failedStep)
        assertEquals(fixture.target, fixture.pointer.read()!!.generationId)
        val bootstrap = fixture.journal.read(fixture.operationId)!!
        assertEquals(BootstrapPhase.CLEANUP_PENDING, bootstrap.phase)
        assertFalse(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(bootstrap))
        assertEquals(
            fixture.source,
            fixture.layout.requireStaged(fixture.source).descriptor.generationId,
        )

        val afterFirst = fixture.attempts.read(fixture.operationId)!!
        assertTrue(afterFirst.vaultReconciled)
        assertFalse(afterFirst.osReconciled)
        assertFalse(afterFirst.obsoleteGenerationCleaned)
        assertFalse(afterFirst.completed)
        assertEquals(1, vault.calls)
        assertEquals(1, failingOs.calls)

        val recovered = fixture.coordinator.recover(fixture.operationId)

        assertEquals(
            ResetExecutionResult.Completed(
                fixture.operationId,
                fixture.target,
                recovered = true,
            ),
            recovered,
        )
        assertEquals(1, vault.calls)
        assertEquals(2, failingOs.calls)
        assertThrows(IllegalStateException::class.java) {
            fixture.layout.requireStaged(fixture.source)
        }
        assertTrue(fixture.attempts.read(fixture.operationId)!!.completed)
    }

    @Test
    fun `pending attempt scan can drive startup cleanup recovery`(
        @TempDir tempDir: Path,
    ) {
        val fixture =
            fixture(
                tempDir,
                os = CountingOs(failuresRemaining = 1),
            )
        val first = fixture.coordinator.execute(fixture.request())
        assertTrue(first is ResetExecutionResult.CleanupPending)

        val recovered = fixture.coordinator.recoverPending()

        assertEquals(1, recovered.size)
        assertTrue(recovered.single() is ResetExecutionResult.Completed)
        assertTrue(fixture.attempts.pending().isEmpty())
    }

    @Test
    fun `completed reset without cleanup proof cannot accept material writes`(
        @TempDir tempDir: Path,
    ) {
        val source = StorageGenerationId("generation-old")
        val target = StorageGenerationId("generation-reset")
        val operationId = MaintenanceOperationId("reset-completed-unfenced")
        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(descriptor(source, 10L))
        layout.stage(descriptor(target, 20L))
        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(null, target)
        val journal = FileBootstrapJournal(tempDir)
        journal.create(
            BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.RESET,
                previousGenerationId = source,
                targetGenerationId = target,
                phase = BootstrapPhase.COMPLETED,
                materialWritesAccepted = false,
            )
        )
        val plan = ResetPlanBuilder.fullReset(source, target, "schema-v1", 20L)
        val attempts = FileResetAttemptStore(tempDir)
        attempts.create(
            ResetAttemptRecord(
                operationId = operationId,
                sourceGenerationId = source,
                targetGenerationId = target,
                schemaSetFingerprint = "schema-v1",
                planGeneratedAtEpochMillis = 20L,
                planFingerprint = plan.planFingerprint,
                approvedAtEpochMillis = 21L,
            )
        )
        val coordinator =
            ResetLifecycleCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = layout,
                quiescence = CountingQuiescence(),
                targetGeneration = CountingTarget(true),
                runtime = CountingRuntime(),
                attempts = attempts,
                vault = CountingVault(),
                os = CountingOs(),
            )

        assertThrows(IllegalStateException::class.java) {
            coordinator.recover(operationId)
        }
        assertFalse(journal.read(operationId)!!.materialWritesAccepted)
    }

    @Test
    fun `reset previous generation is never eligible for automatic rollback`() {
        val resetEntry =
            BootstrapJournalEntry(
                operationId = MaintenanceOperationId("reset-no-rollback"),
                kind = MaintenanceOperationKind.RESET,
                previousGenerationId = StorageGenerationId("old"),
                targetGenerationId = StorageGenerationId("new"),
                phase = BootstrapPhase.SWITCHED,
                materialWritesAccepted = false,
            )
        val restoreEntry =
            resetEntry.copy(
                operationId = MaintenanceOperationId("restore-can-rollback"),
                kind = MaintenanceOperationKind.RESTORE,
            )

        assertFalse(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(resetEntry))
        assertTrue(BootstrapTransitionPolicy.previousGenerationMayBeReactivated(restoreEntry))
    }

    private fun fixture(
        tempDir: Path,
        target: CountingTarget = CountingTarget(true),
        runtime: CountingRuntime = CountingRuntime(),
        vault: CountingVault = CountingVault(),
        os: CountingOs = CountingOs(),
    ): Fixture {
        val source = StorageGenerationId("generation-old")
        val targetId = StorageGenerationId("generation-reset")
        val operationId = MaintenanceOperationId("reset-main")
        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(descriptor(source, 10L))
        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(null, source)
        val journal = FileBootstrapJournal(tempDir)
        val attempts = FileResetAttemptStore(tempDir)
        val quiescence = CountingQuiescence()
        val plan = ResetPlanBuilder.fullReset(source, targetId, "schema-v1", 20L)

        val coordinator =
            ResetLifecycleCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = layout,
                quiescence = quiescence,
                targetGeneration = target,
                runtime = runtime,
                attempts = attempts,
                vault = vault,
                os = os,
            )
        return Fixture(
            source = source,
            target = targetId,
            operationId = operationId,
            layout = layout,
            pointer = pointer,
            journal = journal,
            attempts = attempts,
            quiescence = quiescence,
            targetPort = target,
            runtime = runtime,
            vault = vault,
            os = os,
            plan = plan,
            coordinator = coordinator,
        )
    }

    private fun descriptor(
        generationId: StorageGenerationId,
        createdAt: Long,
    ): StorageGenerationDescriptor =
        StorageGenerationDescriptor(
            generationId = generationId,
            schemaSetFingerprint = "schema-v1",
            createdAtEpochMillis = createdAt,
        )

    private data class Fixture(
        val source: StorageGenerationId,
        val target: StorageGenerationId,
        val operationId: MaintenanceOperationId,
        val layout: FileStorageGenerationLayout,
        val pointer: FileActiveGenerationPointer,
        val journal: FileBootstrapJournal,
        val attempts: FileResetAttemptStore,
        val quiescence: CountingQuiescence,
        val targetPort: CountingTarget,
        val runtime: CountingRuntime,
        val vault: CountingVault,
        val os: CountingOs,
        val plan: ResetPlan,
        val coordinator: ResetLifecycleCoordinator,
    ) {
        fun request(): ResetExecutionRequest =
            ResetExecutionRequest(
                operationId = operationId,
                plan = plan,
                approval =
                    ResetApproval(
                        planFingerprint = plan.planFingerprint,
                        approvedAtEpochMillis = plan.generatedAtEpochMillis + 1L,
                    ),
            )
    }

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

    private class CountingTarget(
        private val verified: Boolean,
    ) : ResetTargetGenerationPort {
        var calls = 0
            private set

        override fun materializeAndVerify(
            plan: ResetPlan,
            staged: StagedStorageGeneration,
        ): Boolean {
            calls += 1
            assertEquals(plan.targetGenerationId, staged.descriptor.generationId)
            assertEquals(plan.schemaSetFingerprint, staged.descriptor.schemaSetFingerprint)
            return verified
        }
    }

    private class CountingRuntime(
        var failuresRemaining: Int = 0,
    ) : GenerationRuntimeActivationPort {
        var calls = 0
            private set

        override fun activateAndVerify(
            kind: MaintenanceOperationKind,
            targetGenerationId: StorageGenerationId,
        ): Boolean {
            calls += 1
            assertEquals(MaintenanceOperationKind.RESET, kind)
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                return false
            }
            return true
        }
    }

    private class CountingVault(
        var failuresRemaining: Int = 0,
    ) : ResetVaultReconciliationPort {
        var calls = 0
            private set

        override fun reconcileAfterReset(plan: ResetPlan) {
            calls += 1
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                error("simulated Vault cleanup failure")
            }
        }
    }

    private class CountingOs(
        var failuresRemaining: Int = 0,
    ) : ResetOsReconciliationPort {
        var calls = 0
            private set

        override fun reconcileAfterReset(plan: ResetPlan) {
            calls += 1
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                error("simulated OS cleanup failure")
            }
        }
    }
}
