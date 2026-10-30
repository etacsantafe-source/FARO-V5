package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** One generation owns both visible metadata and chat timeline; no stale reads after switch. */
class FaroV5ActiveChatReadSessionT2Test {
    private val active = AtomicReference("g-v2")
    private val ready = AtomicBoolean(true)
    private val workspaceSnapshot = ConversationWorkspaceSnapshot(emptyList(), null)
    private val timelineSnapshot = ConversationTimelineSnapshot(
        ConversationId("chat-a"), "authority-g-v2", 0, emptyList()
    )

    private fun session(
        workspaceReader: ConversationWorkspaceReadPort = ConversationWorkspaceReadPort { workspaceSnapshot },
    ): FaroV5ActiveChatReadSession =
        FaroV5ActiveChatReadSession(
            "g-v2", workspaceReader,
            object : ConversationTimelinePort {
                override suspend fun readTimeline(conversationId: ConversationId) =
                    timelineSnapshot.takeIf { it.conversationId == conversationId }
                override suspend fun writeTimeline(request: ConversationTimelineWriteRequest) =
                    ConversationTimelineWriteResult.Unavailable
            },
            Supplier { active.get() }, BooleanSupplier { ready.get() }
        )

    @Test fun currentGenerationSuppliesWorkspaceAndActualTimeline() = runBlocking {
        val session = session()
        assertEquals(workspaceSnapshot, session.workspaceReadPort.readSnapshot())
        assertEquals(timelineSnapshot, session.timelineReadPort.readTimeline(ConversationId("chat-a")))
        assertNull(session.timelineReadPort.readTimeline(ConversationId("missing")))
    }

    @Test fun pointerSwitchMakesBothPreviousPortsUnavailable() {
        val session = session()
        active.set("g-other")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { session.workspaceReadPort.readSnapshot() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { session.timelineReadPort.readTimeline(ConversationId("chat-a")) }
        }
    }

    @Test fun maintenanceRecoveryRevokesExistingActivityReads() {
        val session = session()
        ready.set(false)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { session.workspaceReadPort.readSnapshot() }
        }
    }

    @Test fun switchDuringSuspendCannotExposePreviousWorkspace() {
        val read = ConversationWorkspaceReadPort {
            active.set("g-next")
            workspaceSnapshot
        }
        val session = session(read)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { session.workspaceReadPort.readSnapshot() }
        }
    }
}
