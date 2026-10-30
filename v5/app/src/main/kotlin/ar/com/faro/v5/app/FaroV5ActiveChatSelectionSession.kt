package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/** Same active generation and material gate as Composer and human Review. */
class FaroV5ActiveChatSelectionSession(
    private val generationId: String,
    private val workspace: ConversationWorkspaceSelectionPort,
    private val gate: MaterialWriteGate,
    private val pointer: Supplier<String>,
    private val ready: BooleanSupplier,
) {
    init { require(generationId.isNotBlank()) }
    private fun requireCurrent() {
        check(ready.getAsBoolean() && pointer.get() == generationId) {
            "Workspace selection is stale or recovery is incomplete"
        }
    }
    @JvmField val port: ConversationWorkspaceSelectionPort = object : ConversationWorkspaceSelectionPort {
        override suspend fun createAndSelect(id: ConversationId, atMillis: Long, key: IdempotencyKey) =
            gate.withMaterialWrite {
                requireCurrent()
                val result = runBlocking { workspace.createAndSelect(id, atMillis, key) }
                requireCurrent()
                result
            }
        override suspend fun select(id: ConversationId, key: IdempotencyKey) =
            gate.withMaterialWrite {
                requireCurrent()
                val result = runBlocking { workspace.select(id, key) }
                requireCurrent()
                result
            }
    }
}
