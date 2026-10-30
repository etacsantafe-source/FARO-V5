package ar.com.faro.v5.maintenance

enum class MaintenanceOperationKind {
    MIGRATION,
    RESTORE,
    RESET,
}

enum class BootstrapPhase {
    PREPARING,
    STAGED,
    VALIDATED,
    SWITCH_PREPARED,
    SWITCHED,
    HEALTH_VERIFIED,
    ROLLBACK_PREPARED,
    ROLLED_BACK_POST_SWITCH,
    ROLLBACK_VERIFIED,
    CLEANUP_PENDING,
    COMPLETED,
    ABORTED_PRE_SWITCH,
}

data class BootstrapJournalEntry(
    val operationId: MaintenanceOperationId,
    val kind: MaintenanceOperationKind,
    val previousGenerationId: StorageGenerationId?,
    val targetGenerationId: StorageGenerationId,
    val phase: BootstrapPhase,
    val materialWritesAccepted: Boolean = false,
) {
    init {
        require(previousGenerationId != targetGenerationId) {
            "bootstrap source and target generations must differ"
        }
        require(!materialWritesAccepted || phase.isPostSwitch()) {
            "material writes can be accepted only after generation switch"
        }
        require(!materialWritesAccepted || phase != BootstrapPhase.ABORTED_PRE_SWITCH) {
            "aborted pre-switch operation cannot accept material writes"
        }
    }
}

interface BootstrapJournalPort {
    fun read(operationId: MaintenanceOperationId): BootstrapJournalEntry?

    fun pending(): List<BootstrapJournalEntry>

    fun create(entry: BootstrapJournalEntry)

    fun compareAndSet(
        expected: BootstrapJournalEntry,
        updated: BootstrapJournalEntry,
    ): Boolean
}

object BootstrapTransitionPolicy {
    private val allowedTransitions: Map<BootstrapPhase, Set<BootstrapPhase>> =
        mapOf(
            BootstrapPhase.PREPARING to
                setOf(BootstrapPhase.STAGED, BootstrapPhase.ABORTED_PRE_SWITCH),
            BootstrapPhase.STAGED to
                setOf(BootstrapPhase.VALIDATED, BootstrapPhase.ABORTED_PRE_SWITCH),
            BootstrapPhase.VALIDATED to
                setOf(BootstrapPhase.SWITCH_PREPARED, BootstrapPhase.ABORTED_PRE_SWITCH),
            BootstrapPhase.SWITCH_PREPARED to
                setOf(BootstrapPhase.SWITCHED, BootstrapPhase.ABORTED_PRE_SWITCH),
            BootstrapPhase.SWITCHED to
                setOf(
                    BootstrapPhase.HEALTH_VERIFIED,
                    BootstrapPhase.CLEANUP_PENDING,
                    BootstrapPhase.ROLLBACK_PREPARED,
                ),
            BootstrapPhase.HEALTH_VERIFIED to
                setOf(
                    BootstrapPhase.CLEANUP_PENDING,
                    BootstrapPhase.COMPLETED,
                    BootstrapPhase.ROLLBACK_PREPARED,
                ),
            BootstrapPhase.ROLLBACK_PREPARED to
                setOf(BootstrapPhase.ROLLED_BACK_POST_SWITCH),
            BootstrapPhase.ROLLED_BACK_POST_SWITCH to
                setOf(BootstrapPhase.ROLLBACK_VERIFIED),
            BootstrapPhase.ROLLBACK_VERIFIED to emptySet(),
            BootstrapPhase.CLEANUP_PENDING to
                setOf(BootstrapPhase.COMPLETED),
            BootstrapPhase.COMPLETED to emptySet(),
            BootstrapPhase.ABORTED_PRE_SWITCH to emptySet(),
        )

    fun transition(
        entry: BootstrapJournalEntry,
        next: BootstrapPhase,
    ): BootstrapJournalEntry {
        require(next in allowedTransitions.getValue(entry.phase)) {
            "invalid bootstrap transition: ${entry.phase} -> $next"
        }
        return entry.copy(phase = next)
    }

    fun acceptMaterialWrites(
        entry: BootstrapJournalEntry,
    ): BootstrapJournalEntry {
        require(entry.phase.isPostSwitch()) {
            "material writes require an activated target generation"
        }
        require(entry.phase != BootstrapPhase.ABORTED_PRE_SWITCH) {
            "aborted bootstrap cannot accept material writes"
        }
        return entry.copy(materialWritesAccepted = true)
    }

    fun previousGenerationMayBeReactivated(
        entry: BootstrapJournalEntry,
    ): Boolean =
        entry.kind != MaintenanceOperationKind.RESET &&
            entry.previousGenerationId != null &&
            !entry.materialWritesAccepted &&
            entry.phase in setOf(
                BootstrapPhase.SWITCHED,
                BootstrapPhase.HEALTH_VERIFIED,
            )
}

private fun BootstrapPhase.isPostSwitch(): Boolean =
    this in setOf(
        BootstrapPhase.SWITCHED,
        BootstrapPhase.HEALTH_VERIFIED,
        BootstrapPhase.CLEANUP_PENDING,
        BootstrapPhase.COMPLETED,
    )
