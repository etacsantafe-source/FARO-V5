package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationComposerPort
import ar.com.faro.v5.kernel.contracts.ConversationDraftSaveRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftSendRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationDraftWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/**
 * The Composer writes ONLY through the actual Workspace owner selected by
 * DeliveryGraph. Shared write admission covers pointer validation AND the
 * whole SQLite transaction, so Maintenance cannot race an old UI callback.
 *
 * Shipping V1 has no Workspace and never constructs this session; V2 stays OFF.
 */
class FaroV5ActiveChatComposerSession(
    private val generationId: String,
    private val workspace: ConversationComposerPort,
    private val materialWriteGate: MaterialWriteGate,
    private val activeGenerationId: Supplier<String>,
    private val startupReady: BooleanSupplier,
) {
    init { require(generationId.isNotBlank()) }

    private fun requireCurrent() {
        check(startupReady.getAsBoolean() && activeGenerationId.get() == generationId) {
            "Composer authority is stale or maintenance recovery has not completed"
        }
    }

    @JvmField
    val port: ConversationComposerPort = object : ConversationComposerPort {
        override suspend fun read(conversationId: ConversationId): ConversationDraftSnapshot? {
            requireCurrent()
            val draft = workspace.read(conversationId)
            requireCurrent()
            return draft
        }

        override suspend fun save(request: ConversationDraftSaveRequest): ConversationDraftWriteResult =
            materialWriteGate.withMaterialWrite {
                requireCurrent()
                val result = runBlocking { workspace.save(request) }
                requireCurrent()
                result
            }

        override suspend fun sendDraft(request: ConversationDraftSendRequest): ConversationTimelineWriteResult =
            materialWriteGate.withMaterialWrite {
                requireCurrent()
                val result = runBlocking { workspace.sendDraft(request) }
                requireCurrent()
                result
            }
    }
}
