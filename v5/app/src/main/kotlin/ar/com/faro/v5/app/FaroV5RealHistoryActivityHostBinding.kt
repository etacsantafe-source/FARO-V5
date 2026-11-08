package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5ActivityEntry
import ar.com.faro.v5.surface.FaroV5ActivityEventKind
import ar.com.faro.v5.surface.FaroV5ActivityPage
import ar.com.faro.v5.surface.FaroV5ActivityReadPort
import ar.com.faro.v5.surface.FaroV5ActivitySource
import ar.com.faro.v5.surface.FaroV5ActivityTimeProvenance
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Independent per-source READ authority, not a Search or Apply grant. */
internal fun interface N015TrustedHistoryReadPolicyPort {
    fun epochOrNull(
        principalId: String,
        source: FaroV5ActivityDomain,
        pointer: ActiveGenerationPointer,
    ): String?
}

/**
 * N015 isolated host connection to two REAL canonical SQLite owners.
 * No Activity, CompositionRoot or shipping wiring: the future host must
 * supply a legitimately authenticated policy and owner invalidations.
 *
 * A technical principal is not proof of human identity. Null policy DENIES.
 * The existing N015 federator owns cursor/merge/revocation semantics.
 */
internal class FaroV5RealHistoryActivityHostBinding(
    private val generationId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    reality: RealityStore,
    workspace: ConversationWorkspaceStore,
    private val principalId: String,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val readPolicy: N015TrustedHistoryReadPolicyPort? = null,
    override val invalidations: Flow<Unit>,
) : FaroV5ActivityReadPort, AutoCloseable {
    @Volatile private var closed = false

    init {
        require(generationId.isNotBlank() && pinned.generationId.value == generationId)
        require(principalId.isNotBlank() && principalId == principalId.trim() &&
            principalId.length <= 160 && principalId.none { it.code < 32 || it.code == 127 })
        require(expectedHostEpoch.isNotBlank())
        check(activePointer.read() == pinned) { "N015 host cannot bind stale generation" }
    }

    private fun hostCurrent(): Boolean = runCatching {
        !closed && hostReady() && activePointer.read() == pinned &&
            hostEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private fun policyEpoch(source: FaroV5ActivityDomain): String? = runCatching {
        if (!hostCurrent()) return@runCatching null
        val epoch = readPolicy?.epochOrNull(principalId, source, pinned)
            ?.takeIf { it.isNotBlank() && it == it.trim() && it.length <= 160 }
        if (hostCurrent()) epoch else null
    }.getOrNull()

    private val source = FaroV5FederatedHistoryReadSource(
        expectedGenerationId = generationId,
        reality = reality.timedReceiptHistory,
        workspace = FaroV5WorkspaceHistoryReadPort { query ->
            workspace.readHistoryPage(query)
        },
        currentGenerationId = { activePointer.read()?.generationId?.value },
        pointerCurrent = ::hostCurrent,
        pointerEpoch = {
            if (hostCurrent()) pinned.generationId.value + ":" + pinned.sequence else null
        },
        realityAclEpoch = { policyEpoch(FaroV5ActivityDomain.REALITY_RECEIPT) },
        workspaceAclEpoch = { policyEpoch(FaroV5ActivityDomain.WORKSPACE_MESSAGE) },
        realityReadAdmitted = {
            policyEpoch(FaroV5ActivityDomain.REALITY_RECEIPT) != null
        },
        workspaceReadAdmitted = {
            policyEpoch(FaroV5ActivityDomain.WORKSPACE_MESSAGE) != null
        },
    )

    /**
     * No cursor is exported to Surface in this bounded first-page bridge.
     * Event content/ID are NEVER exposed as presentation text, and exact
     * source provenance must match or the WHOLE read is denied.
     */
    override suspend fun readActivity(): FaroV5ActivityPage? {
        val page = try {
            source.readAuthorized(limit = 50) ?: return null
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }

        val mapped = try {
            page.items.map { item ->
                when (item.domain) {
                    FaroV5ActivityDomain.REALITY_RECEIPT -> {
                        require(item.eventKind == "APPLY_COMMITTED" &&
                            item.timeProvenance ==
                                "reality.receipt.commit_recorded_at_epoch_millis")
                        FaroV5ActivityEntry(
                            source = FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT,
                            opaqueId = item.opaqueId,
                            kind = FaroV5ActivityEventKind.APPLY_COMMITTED,
                            timestampEpochMillis = item.timestampEpochMillis,
                            timeProvenance =
                                FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED,
                        )
                    }
                    FaroV5ActivityDomain.WORKSPACE_MESSAGE -> {
                        require(item.timeProvenance == "workspace.message.created_ms")
                        val kind = when (item.eventKind) {
                            "USER_ACCEPTED" -> FaroV5ActivityEventKind.WORKSPACE_USER_MESSAGE
                            "FARO_COMPLETED" -> FaroV5ActivityEventKind.WORKSPACE_ASSISTANT_MESSAGE
                            else -> error("unknown canonical Workspace event")
                        }
                        FaroV5ActivityEntry(
                            source = FaroV5ActivitySource.WORKSPACE_MESSAGE,
                            opaqueId = item.opaqueId,
                            kind = kind,
                            timestampEpochMillis = item.timestampEpochMillis,
                            timeProvenance =
                                FaroV5ActivityTimeProvenance.WORKSPACE_PRODUCER_DECLARED,
                        )
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        if (!hostCurrent() ||
            policyEpoch(FaroV5ActivityDomain.REALITY_RECEIPT) == null ||
            policyEpoch(FaroV5ActivityDomain.WORKSPACE_MESSAGE) == null
        ) return null
        // The federator rechecks exact epochs in SQL-to-DTO stage; after
        // mapping, deny if either independent admission has gone missing.
        return FaroV5ActivityPage(
            entries = mapped,
            completeWithinSources = page.completeWithinTwoSources,
            hasUndatedLegacyReceipts = page.legacyUndatedReality,
        )
    }

    override fun close() {
        closed = true
    }
}
