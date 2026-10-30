package ar.com.faro.v5.maintenance

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path

@JvmInline
value class ResetPlanFingerprint(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}"))) {
            "reset plan fingerprint must be lowercase SHA-256"
        }
    }
}

enum class ResetInventoryDisposition {
    DELETE,
    PRESERVE,
}

enum class ResetInventoryTarget {
    STORAGE_GENERATION_AUTHORITY,
    VAULT_SECRET_ENTRIES,
    OS_WORK_ALARM_NOTIFICATION_PROJECTIONS,
    MAINTENANCE_TRANSIENT_ARTIFACTS,
    OBSOLETE_STORAGE_GENERATIONS,
    APP_INSTALLATION,
    OS_PERMISSION_DECISIONS,
    USER_MANAGED_NOTIFICATION_CHANNEL_SETTINGS,
    ACTIVE_RESET_BOOTSTRAP_STATE,
}

data class ResetInventoryDecision(
    val target: ResetInventoryTarget,
    val disposition: ResetInventoryDisposition,
)

object ResetInventoryPolicy {
    val FULL_RESET: List<ResetInventoryDecision> =
        listOf(
            ResetInventoryDecision(
                ResetInventoryTarget.STORAGE_GENERATION_AUTHORITY,
                ResetInventoryDisposition.DELETE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.VAULT_SECRET_ENTRIES,
                ResetInventoryDisposition.DELETE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.OS_WORK_ALARM_NOTIFICATION_PROJECTIONS,
                ResetInventoryDisposition.DELETE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.MAINTENANCE_TRANSIENT_ARTIFACTS,
                ResetInventoryDisposition.DELETE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.OBSOLETE_STORAGE_GENERATIONS,
                ResetInventoryDisposition.DELETE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.APP_INSTALLATION,
                ResetInventoryDisposition.PRESERVE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.OS_PERMISSION_DECISIONS,
                ResetInventoryDisposition.PRESERVE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.USER_MANAGED_NOTIFICATION_CHANNEL_SETTINGS,
                ResetInventoryDisposition.PRESERVE,
            ),
            ResetInventoryDecision(
                ResetInventoryTarget.ACTIVE_RESET_BOOTSTRAP_STATE,
                ResetInventoryDisposition.PRESERVE,
            ),
        ).sortedBy { it.target.name }
}

data class ResetPlan(
    val sourceGenerationId: StorageGenerationId,
    val targetGenerationId: StorageGenerationId,
    val schemaSetFingerprint: String,
    val generatedAtEpochMillis: Long,
    val inventory: List<ResetInventoryDecision>,
    val planFingerprint: ResetPlanFingerprint,
) {
    init {
        require(sourceGenerationId != targetGenerationId) {
            "reset target generation must differ from source generation"
        }
        require(schemaSetFingerprint.isNotBlank() && schemaSetFingerprint.length <= 256) {
            "reset schema fingerprint must be non-blank and bounded"
        }
        require(generatedAtEpochMillis >= 0L)
        require(inventory == inventory.sortedBy { it.target.name }) {
            "reset inventory must be in canonical order"
        }
        require(inventory.map { it.target }.distinct().size == inventory.size) {
            "reset inventory cannot contain duplicate targets"
        }
        require(inventory == ResetInventoryPolicy.FULL_RESET) {
            "reset inventory must match the reviewed full-reset policy"
        }
    }
}

object ResetPlanBuilder {
    fun fullReset(
        sourceGenerationId: StorageGenerationId,
        targetGenerationId: StorageGenerationId,
        schemaSetFingerprint: String,
        generatedAtEpochMillis: Long,
    ): ResetPlan {
        require(sourceGenerationId != targetGenerationId)
        require(schemaSetFingerprint.isNotBlank() && schemaSetFingerprint.length <= 256)
        require(generatedAtEpochMillis >= 0L)

        val inventory = ResetInventoryPolicy.FULL_RESET
        val fingerprint =
            fingerprint(
                sourceGenerationId = sourceGenerationId,
                targetGenerationId = targetGenerationId,
                schemaSetFingerprint = schemaSetFingerprint,
                generatedAtEpochMillis = generatedAtEpochMillis,
                inventory = inventory,
            )
        return ResetPlan(
            sourceGenerationId = sourceGenerationId,
            targetGenerationId = targetGenerationId,
            schemaSetFingerprint = schemaSetFingerprint,
            generatedAtEpochMillis = generatedAtEpochMillis,
            inventory = inventory,
            planFingerprint = fingerprint,
        )
    }

    fun matches(plan: ResetPlan): Boolean =
        plan.planFingerprint ==
            fingerprint(
                sourceGenerationId = plan.sourceGenerationId,
                targetGenerationId = plan.targetGenerationId,
                schemaSetFingerprint = plan.schemaSetFingerprint,
                generatedAtEpochMillis = plan.generatedAtEpochMillis,
                inventory = plan.inventory,
            )

    private fun fingerprint(
        sourceGenerationId: StorageGenerationId,
        targetGenerationId: StorageGenerationId,
        schemaSetFingerprint: String,
        generatedAtEpochMillis: Long,
        inventory: List<ResetInventoryDecision>,
    ): ResetPlanFingerprint {
        val hash =
            CanonicalHashBuilder
                .domain("faro-v5-reset-plan-v1")
                .string("sourceGenerationId", sourceGenerationId.value)
                .string("targetGenerationId", targetGenerationId.value)
                .string("schemaSetFingerprint", schemaSetFingerprint)
                .string("generatedAtEpochMillis", generatedAtEpochMillis.toString())
        inventory.forEachIndexed { index, item ->
            hash
                .string("inventory.$index.target", item.target.name)
                .string("inventory.$index.disposition", item.disposition.name)
        }
        return ResetPlanFingerprint(hash.sha256Hex())
    }
}

data class ResetApproval(
    val planFingerprint: ResetPlanFingerprint,
    val approvedAtEpochMillis: Long,
) {
    init {
        require(approvedAtEpochMillis >= 0L)
    }
}

data class ResetExecutionRequest(
    val operationId: MaintenanceOperationId,
    val plan: ResetPlan,
    val approval: ResetApproval,
) {
    init {
        require(approval.approvedAtEpochMillis >= plan.generatedAtEpochMillis) {
            "reset approval cannot predate the reviewed plan"
        }
    }
}

data class ResetAttemptRecord(
    val operationId: MaintenanceOperationId,
    val sourceGenerationId: StorageGenerationId,
    val targetGenerationId: StorageGenerationId,
    val schemaSetFingerprint: String,
    val planGeneratedAtEpochMillis: Long,
    val planFingerprint: ResetPlanFingerprint,
    val approvedAtEpochMillis: Long,
    val vaultReconciled: Boolean = false,
    val osReconciled: Boolean = false,
    val obsoleteGenerationCleaned: Boolean = false,
    val completed: Boolean = false,
) {
    init {
        require(sourceGenerationId != targetGenerationId)
        require(schemaSetFingerprint.isNotBlank() && schemaSetFingerprint.length <= 256)
        require(planGeneratedAtEpochMillis >= 0L)
        require(approvedAtEpochMillis >= planGeneratedAtEpochMillis)
        require(!completed || (vaultReconciled && osReconciled && obsoleteGenerationCleaned)) {
            "completed reset attempt requires all cleanup steps"
        }
        require(planFingerprint == plan().planFingerprint && ResetPlanBuilder.matches(plan())) {
            "reset attempt plan metadata/fingerprint mismatch"
        }
    }

    fun plan(): ResetPlan =
        ResetPlanBuilder.fullReset(
            sourceGenerationId = sourceGenerationId,
            targetGenerationId = targetGenerationId,
            schemaSetFingerprint = schemaSetFingerprint,
            generatedAtEpochMillis = planGeneratedAtEpochMillis,
        )

    fun matches(request: ResetExecutionRequest): Boolean =
        operationId == request.operationId &&
            plan() == request.plan &&
            planFingerprint == request.approval.planFingerprint &&
            approvedAtEpochMillis == request.approval.approvedAtEpochMillis
}

interface ResetAttemptStore {
    fun read(operationId: MaintenanceOperationId): ResetAttemptRecord?

    fun create(record: ResetAttemptRecord)

    fun compareAndSet(
        expected: ResetAttemptRecord,
        updated: ResetAttemptRecord,
    ): Boolean

    fun pending(): List<ResetAttemptRecord>
}

class FileResetAttemptStore(
    root: Path,
) : ResetAttemptStore {
    private val lock = Any()
    private val directory =
        root.toAbsolutePath().normalize()
            .resolve("bootstrap")
            .resolve("reset-attempts")
    private val records = DurableRecordDirectory(directory)

    override fun read(operationId: MaintenanceOperationId): ResetAttemptRecord? =
        synchronized(lock) {
            readPersisted(operationId)?.record
        }

    override fun create(record: ResetAttemptRecord) {
        synchronized(lock) {
            check(records.readLatest(prefix(record.operationId)) == null) {
                "reset attempt already exists"
            }
            records.publish(
                prefix = prefix(record.operationId),
                sequence = 1L,
                bytes = ResetAttemptCodec.encode(1L, record),
            )
        }
    }

    override fun compareAndSet(
        expected: ResetAttemptRecord,
        updated: ResetAttemptRecord,
    ): Boolean =
        synchronized(lock) {
            requireLegalMutation(expected, updated)
            val current = readPersisted(expected.operationId) ?: return@synchronized false
            if (current.record != expected) {
                return@synchronized false
            }
            val nextSequence = Math.addExact(current.sequence, 1L)
            records.publish(
                prefix = prefix(expected.operationId),
                sequence = nextSequence,
                bytes = ResetAttemptCodec.encode(nextSequence, updated),
            )
            true
        }

    override fun pending(): List<ResetAttemptRecord> =
        synchronized(lock) {
            if (!Files.exists(directory)) return@synchronized emptyList()
            val prefixes =
                Files.newDirectoryStream(directory).use { entries ->
                    entries.mapNotNull { path ->
                        ATTEMPT_FILE.matchEntire(path.fileName.toString())
                            ?.groupValues
                            ?.get(1)
                    }.toSet()
                }
            prefixes.map { prefix ->
                val durable =
                    checkNotNull(records.readLatest(prefix)) {
                        "reset attempt disappeared during scan"
                    }
                val persisted = ResetAttemptCodec.decode(durable.bytes)
                check(persisted.sequence == durable.sequence) {
                    "reset attempt sequence mismatch"
                }
                check(prefix(persisted.record.operationId) == prefix) {
                    "reset attempt path identity mismatch"
                }
                persisted.record
            }
                .filterNot { it.completed }
                .sortedBy { it.operationId.value }
        }

    private fun readPersisted(
        operationId: MaintenanceOperationId,
    ): PersistedResetAttempt? {
        val durable = records.readLatest(prefix(operationId)) ?: return null
        val persisted = ResetAttemptCodec.decode(durable.bytes)
        check(persisted.sequence == durable.sequence) {
            "reset attempt sequence mismatch"
        }
        check(persisted.record.operationId == operationId) {
            "reset attempt operation identity mismatch"
        }
        return persisted
    }

    private fun requireLegalMutation(
        expected: ResetAttemptRecord,
        updated: ResetAttemptRecord,
    ) {
        require(
            expected.copy(
                vaultReconciled = updated.vaultReconciled,
                osReconciled = updated.osReconciled,
                obsoleteGenerationCleaned = updated.obsoleteGenerationCleaned,
                completed = updated.completed,
            ) == updated
        ) {
            "reset attempt plan/approval identity is immutable"
        }
        require(!expected.completed) {
            "completed reset attempt is immutable"
        }
        require(!expected.vaultReconciled || updated.vaultReconciled) {
            "reset Vault cleanup cannot regress"
        }
        require(!expected.osReconciled || updated.osReconciled) {
            "reset OS cleanup cannot regress"
        }
        require(!expected.obsoleteGenerationCleaned || updated.obsoleteGenerationCleaned) {
            "reset generation cleanup cannot regress"
        }
        require(!updated.completed || (
            updated.vaultReconciled &&
                updated.osReconciled &&
                updated.obsoleteGenerationCleaned
        ))
    }

    private fun prefix(operationId: MaintenanceOperationId): String =
        "reset-" + MaintenancePathIdentity.sha256Hex(operationId.value)

    private companion object {
        val ATTEMPT_FILE = Regex("^(reset-[0-9a-f]{64})-[0-9]{20}\\.bin$")
    }
}

fun interface ResetTargetGenerationPort {
    /**
     * Materialize and verify an empty/preserved target generation.
     *
     * The implementation must be idempotent because recovery may call it again before switch.
     */
    fun materializeAndVerify(
        plan: ResetPlan,
        staged: StagedStorageGeneration,
    ): Boolean
}

fun interface ResetVaultReconciliationPort {
    /** Must be idempotent. Secret values never cross this boundary. */
    fun reconcileAfterReset(plan: ResetPlan)
}

fun interface ResetOsReconciliationPort {
    /** Must be idempotent. OS scheduler/notification state is projection, never Truth. */
    fun reconcileAfterReset(plan: ResetPlan)
}

enum class ResetBlockReason {
    INVALID_PLAN,
    APPROVAL_MISMATCH,
    ACTIVE_GENERATION_CHANGED,
    BOOTSTRAP_CONFLICT,
    TARGET_VERIFICATION_FAILED,
    ABORTED_PRE_SWITCH,
}

enum class ResetCleanupStep {
    RUNTIME_ACTIVATION,
    VAULT,
    OS_PROJECTIONS,
    OBSOLETE_GENERATION,
}

sealed interface ResetExecutionResult {
    data class Completed(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId,
        val recovered: Boolean,
    ) : ResetExecutionResult

    data class Blocked(
        val operationId: MaintenanceOperationId,
        val reason: ResetBlockReason,
    ) : ResetExecutionResult

    data class CleanupPending(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId,
        val failedStep: ResetCleanupStep,
        val failureClass: String,
    ) : ResetExecutionResult
}

class ResetLifecycleCoordinator(
    private val journal: BootstrapJournalPort,
    private val activePointer: ActiveGenerationPointerPort,
    private val generationLayout: FileStorageGenerationLayout,
    private val quiescence: MaterialWriteQuiescencePort,
    private val targetGeneration: ResetTargetGenerationPort,
    private val runtime: GenerationRuntimeActivationPort,
    private val attempts: ResetAttemptStore,
    private val vault: ResetVaultReconciliationPort,
    private val os: ResetOsReconciliationPort,
    private val generationSwitch: GenerationSwitchCoordinator =
        GenerationSwitchCoordinator(journal, activePointer, generationLayout),
) {
    fun execute(request: ResetExecutionRequest): ResetExecutionResult {
        if (!ResetPlanBuilder.matches(request.plan)) {
            return ResetExecutionResult.Blocked(
                request.operationId,
                ResetBlockReason.INVALID_PLAN,
            )
        }
        if (request.approval.planFingerprint != request.plan.planFingerprint) {
            return ResetExecutionResult.Blocked(
                request.operationId,
                ResetBlockReason.APPROVAL_MISMATCH,
            )
        }

        val attempt = beginAttempt(request)
        return quiescence.withMaterialWritesQuiesced(request.operationId) {
            continueAttempt(attempt, recovered = false)
        }
    }

    fun recover(operationId: MaintenanceOperationId): ResetExecutionResult {
        val attempt =
            attempts.read(operationId)
                ?: return ResetExecutionResult.Blocked(
                    operationId,
                    ResetBlockReason.BOOTSTRAP_CONFLICT,
                )
        return quiescence.withMaterialWritesQuiesced(operationId) {
            continueAttempt(attempt, recovered = true)
        }
    }

    fun recoverPending(): List<ResetExecutionResult> =
        attempts.pending()
            .filter { attempt ->
                journal.read(attempt.operationId)?.phase !=
                    BootstrapPhase.ABORTED_PRE_SWITCH
            }
            .map { attempt ->
                recover(attempt.operationId)
            }

    private fun beginAttempt(request: ResetExecutionRequest): ResetAttemptRecord {
        val existing = attempts.read(request.operationId)
        if (existing != null) {
            check(existing.matches(request)) {
                "reset operation id already belongs to a different reviewed plan"
            }
            return existing
        }

        val created =
            ResetAttemptRecord(
                operationId = request.operationId,
                sourceGenerationId = request.plan.sourceGenerationId,
                targetGenerationId = request.plan.targetGenerationId,
                schemaSetFingerprint = request.plan.schemaSetFingerprint,
                planGeneratedAtEpochMillis = request.plan.generatedAtEpochMillis,
                planFingerprint = request.plan.planFingerprint,
                approvedAtEpochMillis = request.approval.approvedAtEpochMillis,
            )
        attempts.create(created)
        return created
    }

    private fun continueAttempt(
        initialAttempt: ResetAttemptRecord,
        recovered: Boolean,
    ): ResetExecutionResult {
        var attempt =
            checkNotNull(attempts.read(initialAttempt.operationId)) {
                "reset attempt disappeared"
            }
        val plan = attempt.plan()

        var bootstrap = ensureBootstrap(attempt)
            ?: return ResetExecutionResult.Blocked(
                attempt.operationId,
                ResetBlockReason.BOOTSTRAP_CONFLICT,
            )

        if (bootstrap.phase == BootstrapPhase.ABORTED_PRE_SWITCH) {
            return ResetExecutionResult.Blocked(
                attempt.operationId,
                ResetBlockReason.ABORTED_PRE_SWITCH,
            )
        }

        if (
            bootstrap.phase in setOf(
                BootstrapPhase.PREPARING,
                BootstrapPhase.STAGED,
                BootstrapPhase.VALIDATED,
            )
        ) {
            val pointer = activePointer.read()
            if (pointer?.generationId != attempt.sourceGenerationId) {
                return ResetExecutionResult.Blocked(
                    attempt.operationId,
                    ResetBlockReason.ACTIVE_GENERATION_CHANGED,
                )
            }

            val staged =
                generationLayout.stage(
                    StorageGenerationDescriptor(
                        generationId = attempt.targetGenerationId,
                        schemaSetFingerprint = attempt.schemaSetFingerprint,
                        createdAtEpochMillis = attempt.planGeneratedAtEpochMillis,
                    )
                )

            val verified =
                targetGeneration.materializeAndVerify(plan, staged)
            if (!verified) {
                abortPreSwitch(bootstrap)
                return ResetExecutionResult.Blocked(
                    attempt.operationId,
                    ResetBlockReason.TARGET_VERIFICATION_FAILED,
                )
            }

            bootstrap = advancePreSwitchToSwitchPrepared(bootstrap)
        }

        bootstrap =
            if (bootstrap.phase == BootstrapPhase.SWITCH_PREPARED) {
                generationLayout.requireStaged(attempt.targetGenerationId)
                val recoveredSwitch = generationSwitch.recover(attempt.operationId)
                if (recoveredSwitch.phase == BootstrapPhase.SWITCH_PREPARED) {
                    generationSwitch.switch(attempt.operationId)
                } else {
                    recoveredSwitch
                }
            } else {
                generationSwitch.recover(attempt.operationId)
            }

        if (bootstrap.phase == BootstrapPhase.SWITCHED) {
            val runtimeHealthy =
                try {
                    runtime.activateAndVerify(
                        MaintenanceOperationKind.RESET,
                        attempt.targetGenerationId,
                    )
                } catch (failure: Exception) {
                    return cleanupPending(
                        attempt,
                        ResetCleanupStep.RUNTIME_ACTIVATION,
                        failure,
                    )
                }
            if (!runtimeHealthy) {
                return cleanupPending(
                    attempt,
                    ResetCleanupStep.RUNTIME_ACTIVATION,
                    IllegalStateException("reset target runtime health failed"),
                )
            }
            bootstrap = transition(bootstrap, BootstrapPhase.HEALTH_VERIFIED)
        }
        if (bootstrap.phase == BootstrapPhase.HEALTH_VERIFIED) {
            bootstrap = transition(bootstrap, BootstrapPhase.CLEANUP_PENDING)
        }

        check(
            bootstrap.phase == BootstrapPhase.CLEANUP_PENDING ||
                bootstrap.phase == BootstrapPhase.COMPLETED
        ) {
            "reset post-switch lifecycle did not reach cleanup"
        }
        check(activePointer.read()?.generationId == attempt.targetGenerationId) {
            "reset cleanup requires target generation to remain active"
        }

        if (bootstrap.phase == BootstrapPhase.CLEANUP_PENDING) {
            if (!attempt.vaultReconciled) {
                try {
                    vault.reconcileAfterReset(plan)
                } catch (failure: Exception) {
                    return cleanupPending(attempt, ResetCleanupStep.VAULT, failure)
                }
                attempt = updateAttempt(attempt, attempt.copy(vaultReconciled = true))
            }

            if (!attempt.osReconciled) {
                try {
                    os.reconcileAfterReset(plan)
                } catch (failure: Exception) {
                    return cleanupPending(attempt, ResetCleanupStep.OS_PROJECTIONS, failure)
                }
                attempt = updateAttempt(attempt, attempt.copy(osReconciled = true))
            }

            if (!attempt.obsoleteGenerationCleaned) {
                try {
                    check(activePointer.read()?.generationId == attempt.targetGenerationId) {
                        "old generation cleanup lost target authority"
                    }
                    generationLayout.deleteGeneration(attempt.sourceGenerationId)
                } catch (failure: Exception) {
                    return cleanupPending(
                        attempt,
                        ResetCleanupStep.OBSOLETE_GENERATION,
                        failure,
                    )
                }
                attempt =
                    updateAttempt(
                        attempt,
                        attempt.copy(obsoleteGenerationCleaned = true),
                    )
            }

            if (!bootstrap.materialWritesAccepted) {
                bootstrap = generationSwitch.markMaterialWritesAccepted(attempt.operationId)
            }
            bootstrap = transition(bootstrap, BootstrapPhase.COMPLETED)
        }

        if (bootstrap.phase == BootstrapPhase.COMPLETED && !bootstrap.materialWritesAccepted) {
            check(
                attempt.vaultReconciled &&
                    attempt.osReconciled &&
                    attempt.obsoleteGenerationCleaned
            ) {
                "completed reset without cleanup proof cannot accept material writes"
            }
            bootstrap = generationSwitch.markMaterialWritesAccepted(attempt.operationId)
        }
        check(bootstrap.phase == BootstrapPhase.COMPLETED && bootstrap.materialWritesAccepted) {
            "reset completion must include the material-write fence"
        }

        if (!attempt.completed) {
            attempt = updateAttempt(attempt, attempt.copy(completed = true))
        }

        return ResetExecutionResult.Completed(
            operationId = attempt.operationId,
            targetGenerationId = attempt.targetGenerationId,
            recovered = recovered,
        )
    }

    private fun ensureBootstrap(
        attempt: ResetAttemptRecord,
    ): BootstrapJournalEntry? {
        val existing = journal.read(attempt.operationId)
        if (existing != null) {
            return if (
                existing.kind == MaintenanceOperationKind.RESET &&
                    existing.previousGenerationId == attempt.sourceGenerationId &&
                    existing.targetGenerationId == attempt.targetGenerationId
            ) {
                existing
            } else {
                null
            }
        }

        if (activePointer.read()?.generationId != attempt.sourceGenerationId) {
            return null
        }
        val created =
            BootstrapJournalEntry(
                operationId = attempt.operationId,
                kind = MaintenanceOperationKind.RESET,
                previousGenerationId = attempt.sourceGenerationId,
                targetGenerationId = attempt.targetGenerationId,
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(created)
        return created
    }

    private fun advancePreSwitchToSwitchPrepared(
        initial: BootstrapJournalEntry,
    ): BootstrapJournalEntry {
        var current = initial
        val targets =
            listOf(
                BootstrapPhase.STAGED,
                BootstrapPhase.VALIDATED,
                BootstrapPhase.SWITCH_PREPARED,
            )
        for (next in targets) {
            if (current.phase == next) {
                continue
            }
            if (current.phase.orderForReset() < next.orderForReset()) {
                current = transition(current, next)
            }
        }
        return current
    }

    private fun abortPreSwitch(entry: BootstrapJournalEntry) {
        if (!entry.phase.isResetPreSwitch()) return
        val aborted =
            BootstrapTransitionPolicy.transition(
                entry,
                BootstrapPhase.ABORTED_PRE_SWITCH,
            )
        if (!journal.compareAndSet(entry, aborted)) {
            val observed = journal.read(entry.operationId)
            check(observed == aborted || observed?.phase == BootstrapPhase.ABORTED_PRE_SWITCH) {
                "reset bootstrap changed while aborting pre-switch"
            }
        }
    }

    private fun transition(
        current: BootstrapJournalEntry,
        next: BootstrapPhase,
    ): BootstrapJournalEntry {
        val updated = BootstrapTransitionPolicy.transition(current, next)
        if (!journal.compareAndSet(current, updated)) {
            val observed =
                checkNotNull(journal.read(current.operationId)) {
                    "reset bootstrap disappeared during transition"
                }
            check(observed == updated) {
                "reset bootstrap changed unexpectedly during transition"
            }
        }
        return updated
    }

    private fun updateAttempt(
        current: ResetAttemptRecord,
        updated: ResetAttemptRecord,
    ): ResetAttemptRecord {
        if (!attempts.compareAndSet(current, updated)) {
            val observed =
                checkNotNull(attempts.read(current.operationId)) {
                    "reset attempt disappeared during update"
                }
            check(observed == updated) {
                "reset attempt changed unexpectedly"
            }
        }
        return updated
    }

    private fun cleanupPending(
        attempt: ResetAttemptRecord,
        step: ResetCleanupStep,
        failure: Throwable,
    ): ResetExecutionResult.CleanupPending =
        ResetExecutionResult.CleanupPending(
            operationId = attempt.operationId,
            targetGenerationId = attempt.targetGenerationId,
            failedStep = step,
            failureClass = failure::class.java.simpleName.ifBlank { "Throwable" },
        )
}

private fun BootstrapPhase.isResetPreSwitch(): Boolean =
    this in setOf(
        BootstrapPhase.PREPARING,
        BootstrapPhase.STAGED,
        BootstrapPhase.VALIDATED,
        BootstrapPhase.SWITCH_PREPARED,
    )

private fun BootstrapPhase.orderForReset(): Int =
    when (this) {
        BootstrapPhase.PREPARING -> 0
        BootstrapPhase.STAGED -> 1
        BootstrapPhase.VALIDATED -> 2
        BootstrapPhase.SWITCH_PREPARED -> 3
        BootstrapPhase.SWITCHED -> 4
        BootstrapPhase.HEALTH_VERIFIED -> 5
        BootstrapPhase.CLEANUP_PENDING -> 6
        BootstrapPhase.COMPLETED -> 7
        BootstrapPhase.ABORTED_PRE_SWITCH -> 8
        BootstrapPhase.ROLLBACK_PREPARED -> 9
        BootstrapPhase.ROLLED_BACK_POST_SWITCH -> 10
        BootstrapPhase.ROLLBACK_VERIFIED -> 11
    }

private object ResetAttemptCodec {
    fun encode(
        sequence: Long,
        record: ResetAttemptRecord,
    ): ByteArray =
        MaintenanceRecordEnvelope.encode(
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeLong(sequence)
                    output.writeUTF(record.operationId.value)
                    output.writeUTF(record.sourceGenerationId.value)
                    output.writeUTF(record.targetGenerationId.value)
                    output.writeUTF(record.schemaSetFingerprint)
                    output.writeLong(record.planGeneratedAtEpochMillis)
                    output.writeUTF(record.planFingerprint.value)
                    output.writeLong(record.approvedAtEpochMillis)
                    output.writeBoolean(record.vaultReconciled)
                    output.writeBoolean(record.osReconciled)
                    output.writeBoolean(record.obsoleteGenerationCleaned)
                    output.writeBoolean(record.completed)
                }
                buffer.toByteArray()
            }
        )

    fun decode(record: ByteArray): PersistedResetAttempt =
        DataInputStream(
            ByteArrayInputStream(MaintenanceRecordEnvelope.decode(record))
        ).use { input ->
            val sequence = input.readLong()
            check(sequence > 0L) {
                "reset attempt sequence must be positive"
            }
            val decoded =
                ResetAttemptRecord(
                    operationId = MaintenanceOperationId(input.readUTF()),
                    sourceGenerationId = StorageGenerationId(input.readUTF()),
                    targetGenerationId = StorageGenerationId(input.readUTF()),
                    schemaSetFingerprint = input.readUTF(),
                    planGeneratedAtEpochMillis = input.readLong(),
                    planFingerprint = ResetPlanFingerprint(input.readUTF()),
                    approvedAtEpochMillis = input.readLong(),
                    vaultReconciled = input.readBoolean(),
                    osReconciled = input.readBoolean(),
                    obsoleteGenerationCleaned = input.readBoolean(),
                    completed = input.readBoolean(),
                )
            check(input.available() == 0) {
                "reset attempt payload has trailing bytes"
            }
            PersistedResetAttempt(sequence, decoded)
        }
}

private data class PersistedResetAttempt(
    val sequence: Long,
    val record: ResetAttemptRecord,
)
