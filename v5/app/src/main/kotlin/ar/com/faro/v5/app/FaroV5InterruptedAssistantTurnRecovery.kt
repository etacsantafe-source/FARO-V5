package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/** Shared exact restart-only reconciliation; never dispatches/replays a provider request. */
internal object FaroV5InterruptedAssistantTurnRecovery {
    fun reconcile(
        generation: String,
        workspace: ConversationWorkspaceStore,
        reasoning: FaroV5ActiveChatReasoningSession,
        profile: Supplier<ModelProfileId>,
        admitted: BooleanSupplier,
        exclusiveRestartRecovery: BooleanSupplier?,
    ): Int {
        var recovered = 0
        if (exclusiveRestartRecovery != null) {
            check(admitted.getAsBoolean() &&
                exclusiveRestartRecovery.getAsBoolean()) {
                "exclusive_restart_recovery_not_admitted"
            }
            val conversations = runBlocking { workspace.readSnapshot() }.conversations
            for (entry in conversations) {
                val timeline = runBlocking { workspace.readTimeline(entry.id) }
                    ?: continue
                for (reply in timeline.messages.filter {
                    it.role == ConversationMessageRole.FARO &&
                        it.state == ConversationMessageState.PROCESSING
                }) {
                    val user = timeline.messages.firstOrNull {
                        it.role == ConversationMessageRole.USER &&
                            it.turnId == reply.turnId &&
                            it.state == ConversationMessageState.ACCEPTED
                    } ?: error("orphaned_faro_without_accepted_user")
                    val recoveryIdentity = UUID.nameUUIDFromBytes(
                        ("f1-recovery:" + generation + ":" +
                            entry.id.value + ":" + reply.turnId.value)
                            .toByteArray(StandardCharsets.UTF_8),
                    )
                    val key = IdempotencyKey("f1-recovery-$recoveryIdentity")
                    val outcome = reasoning.settleInterruptedAfterRecovery(
                        FaroV5AssistantTurnRequest(
                            entry.id, user.id, reply.turnId, reply.id,
                            profile.get(), reply.createdAtEpochMillis, key, key,
                        ),
                        BooleanSupplier {
                            admitted.getAsBoolean() &&
                                exclusiveRestartRecovery.getAsBoolean()
                        },
                    )
                    check(outcome == FaroV5AssistantTurnOutcome.FAILED ||
                        outcome == FaroV5AssistantTurnOutcome.ALREADY_RECORDED) {
                        "interrupted_turn_recovery_incomplete:$outcome"
                    }
                    if (outcome == FaroV5AssistantTurnOutcome.FAILED) recovered++
                }
            }
        }
        return recovered
    }
}
