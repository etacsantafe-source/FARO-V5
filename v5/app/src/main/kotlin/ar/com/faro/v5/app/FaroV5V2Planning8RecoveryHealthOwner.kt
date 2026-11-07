package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Path

/**
 * N033C2B3: health-only evidence for an ALREADY active Planning8 generation.
 * Neither this owner nor Verified grants an ACL, Review, Apply, activation,
 * replay-fence, or a persistent mutable Reality graph to Android/Chat.
 * The historical schema7 V2RuntimeHealthOwner.canonical remains unchanged.
 */
internal fun interface Planning8RecoveryReadinessProbe {
    fun isReady(staged: StagedStorageGeneration): Boolean
}

internal class FaroV5V2Planning8RecoveryHealthOwner(
    private val layout: FileStorageGenerationLayout,
    private val journal: BootstrapJournalPort,
    private val pointer: ActiveGenerationPointerPort,
    private val quiescence: MaterialWriteQuiescencePort,
    private val probe: Planning8RecoveryReadinessProbe,
) {
    fun verify(id: MaintenanceOperationId): V2RuntimeHealth = runCatching {
        quiescence.withMaterialWritesQuiesced(id) {
            val entry = journal.read(id)
                ?: return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked
            val pinned = pointer.read()
                ?: return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked
            if (entry.kind != MaintenanceOperationKind.RESTORE ||
                entry.previousGenerationId == null ||
                entry.phase !in setOf(
                    BootstrapPhase.SWITCHED,
                    BootstrapPhase.HEALTH_VERIFIED,
                ) ||
                entry.materialWritesAccepted ||
                pinned.generationId != entry.targetGenerationId ||
                journal.pending().any { it.operationId != id }
            ) return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked

            val staged = layout.requireStaged(entry.targetGenerationId)
            if (ExistingGenerationFormatReader.requireAndRead(staged) !=
                    GenerationStoreFormat.WORKSPACE_V2 ||
                pointer.read() != pinned
            ) return@withMaterialWritesQuiesced V2RuntimeHealth.Blocked

            // The canonical probe opens/verifies/closes all five real stores.
            // It exposes a one-shot Boolean, never a retained graph/capability.
            if (!probe.isReady(staged) ||
                pointer.read() != pinned ||
                journal.read(id) != entry ||
                journal.pending().any { it.operationId != id }
            ) V2RuntimeHealth.Blocked
            else V2RuntimeHealth.Verified(entry.targetGenerationId.value)
        }
    }.getOrDefault(V2RuntimeHealth.Blocked)

    companion object {
        /** Not bound to production. Explicit certificate and isolated call only. */
        fun canonical(
            storageRoot: Path,
            layout: FileStorageGenerationLayout,
            journal: BootstrapJournalPort,
            pointer: ActiveGenerationPointerPort,
            quiescence: MaterialWriteQuiescencePort,
            gate: MaterialWriteGate,
            v2HostCertified: Boolean,
        ) = FaroV5V2Planning8RecoveryHealthOwner(
            layout, journal, pointer, quiescence,
            Planning8RecoveryReadinessProbe { staged ->
                FaroV5ActiveGenerationGraphOwner.isActivePlanning8ReadyForCertifiedHost(
                    storageRoot,
                    staged.descriptor.generationId.value,
                    gate,
                    v2HostCertified,
                )
            },
        )
    }
}
