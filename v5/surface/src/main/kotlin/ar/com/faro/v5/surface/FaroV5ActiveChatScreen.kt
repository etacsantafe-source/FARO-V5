package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort
import ar.com.faro.v5.kernel.contracts.ConversationApplyHostOutcome
import ar.com.faro.v5.kernel.contracts.ConversationReviewDecision
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort
import ar.com.faro.v5.kernel.contracts.ConversationDraftEditorState
import ar.com.faro.v5.kernel.contracts.ConversationDraftPolicy
import ar.com.faro.v5.kernel.contracts.ConversationDraftSendRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Each conversation/generation-bound port set owns a distinct Compose lifetime.
 * A previous screen's IO callbacks must not update the new conversation editor,
 * review state or timeline after the user switches conversations or authority.
 * Disposal cancels rememberCoroutineScope and LaunchedEffect work together.
 * Canonical Workspace and generation gates still own all durable mutations.
 */
@Composable
fun FaroV5ActiveChatScreen(
    activeConversationId: ConversationId?,
    timelineReadPort: ConversationTimelineReadPort,
    composerPort: ConversationComposerPort?,
    reviewPort: ConversationReviewPort? = null,
    applyRequestPort: ConversationApplyRequestPort? = null,
) {
    key(activeConversationId, timelineReadPort, composerPort, reviewPort, applyRequestPort) {
        FaroV5ActiveChatScreenSession(
            activeConversationId = activeConversationId,
            timelineReadPort = timelineReadPort,
            composerPort = composerPort,
            reviewPort = reviewPort,
            applyRequestPort = applyRequestPort,
        )
    }
}

/**
 * Real Chat → authoritative Workspace path. Composer never holds a second
 * authority, and the user message appears only after the owner acknowledges
 * the atomic send. No synthetic FARO reply, model call or Apply side effect.
 */
@Composable
private fun FaroV5ActiveChatScreenSession(
    activeConversationId: ConversationId?,
    timelineReadPort: ConversationTimelineReadPort,
    composerPort: ConversationComposerPort?,
    reviewPort: ConversationReviewPort? = null,
    applyRequestPort: ConversationApplyRequestPort? = null,
) {
    val scope = rememberCoroutineScope()
    var editor by remember(activeConversationId, composerPort) {
        mutableStateOf<ConversationDraftEditorState?>(null)
    }
    var loading by remember(activeConversationId, composerPort) { mutableStateOf(composerPort != null) }
    var busy by remember(activeConversationId, composerPort) { mutableStateOf(false) }
    var uncertain by remember(activeConversationId, composerPort) { mutableStateOf(false) }
    var message by remember(activeConversationId, composerPort) { mutableStateOf<String?>(null) }
    var timelineVersion by remember(activeConversationId, composerPort) { mutableStateOf(0L) }
    var reviewVersion by remember(activeConversationId, reviewPort) { mutableStateOf(0L) }
    // Serialize Review commands; a double tap cannot mint a second idempotency key.
    var reviewBusy by remember(activeConversationId, reviewPort) { mutableStateOf(false) }
    var applyBusy by remember(activeConversationId, applyRequestPort) { mutableStateOf(false) }
    var applyUncertain by remember(activeConversationId, applyRequestPort) { mutableStateOf(false) }
    var canonicalReviews by remember(activeConversationId, reviewPort) {
        mutableStateOf<List<ConversationPlanReviewSnapshot>>(emptyList())
    }

    LaunchedEffect(activeConversationId, reviewPort, reviewVersion) {
        if (activeConversationId == null || reviewPort == null) {
            canonicalReviews = emptyList()
        } else {
            try {
                canonicalReviews = withContext(Dispatchers.IO) {
                    reviewPort.reviews(activeConversationId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                canonicalReviews = emptyList()
                message = "No se pudo recuperar la revisión del plan."
            }
        }
    }

    LaunchedEffect(activeConversationId, composerPort) {
        if (activeConversationId != null && composerPort != null) {
            try {
                val snapshot = withContext(Dispatchers.IO) { composerPort.read(activeConversationId) }
                editor = snapshot?.let(::ConversationDraftEditorState)
                message = if (snapshot == null) "La conversación ya no está disponible." else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "No se pudo recuperar el borrador desde Workspace."
            } finally {
                loading = false
            }
        } else {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            FaroV5ActiveChatTimeline(
                activeConversationId = activeConversationId,
                timelineReadPort = timelineReadPort,
                refreshRevision = timelineVersion,
            )
        }
        if (activeConversationId != null && reviewPort != null) {
            canonicalReviews.forEach { review ->
                FaroV5PlanReview(
                    review = review,
                    reviewExpanded = true,
                    onConfirm = { context ->
                        if (!reviewBusy && !applyBusy && !applyUncertain) {
                            reviewBusy = true
                            scope.launch {
                                try {
                                    val decided = withContext(Dispatchers.IO) {
                                        reviewPort.decide(
                                            ConversationPlanReviewIntent.Approve(context),
                                            IdempotencyKey(UUID.randomUUID().toString()),
                                        )
                                    }
                                    if (decided is ConversationReviewDecision.Saved) {
                                        message = "Propuesta confirmada. Todavía no se aplicaron cambios."
                                    } else message = "La propuesta cambió. Volvé a revisarla."
                                    reviewVersion++
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    message = "No se pudo confirmar la propuesta."
                                    reviewVersion++
                                } finally {
                                    reviewBusy = false
                                }
                            }
                        }
                    },
                    onDiscard = { context ->
                        if (!reviewBusy && !applyBusy && !applyUncertain) {
                            reviewBusy = true
                            scope.launch {
                                try {
                                    val decided = withContext(Dispatchers.IO) {
                                        reviewPort.decide(
                                            ConversationPlanReviewIntent.Discard(context),
                                            IdempotencyKey(UUID.randomUUID().toString()),
                                        )
                                    }
                                    if (decided is ConversationReviewDecision.Saved) {
                                        message = "Propuesta descartada."
                                    } else message = "La propuesta cambió. Volvé a revisarla."
                                    reviewVersion++
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    message = "No se pudo descartar la propuesta."
                                    reviewVersion++
                                } finally {
                                    reviewBusy = false
                                }
                            }
                        }
                    },
                    // Only a certified V2 host can expose this port. Never trust UI as authority.
                    onRequestApply = applyRequestPort?.takeIf { !reviewBusy && !applyBusy && !applyUncertain }?.let { requestPort ->
                        { context ->
                            if (!reviewBusy && !applyBusy && !applyUncertain) {
                                applyBusy = true
                                scope.launch {
                                    try {
                                        when (withContext(Dispatchers.IO) { requestPort.request(context) }) {
                                            is ConversationApplyHostOutcome.Committed -> {
                                                message = "Cambios aplicados y registrados."
                                                reviewVersion++
                                                timelineVersion++
                                            }
                                            is ConversationApplyHostOutcome.Denied -> {
                                                message = "No se aplicaron cambios. Volvé a revisar la propuesta."
                                                reviewVersion++
                                            }
                                            ConversationApplyHostOutcome.RecoveryRequired -> {
                                                applyUncertain = true
                                                message = "Resultado pendiente de verificar. Reabrí FARO antes de reintentar."
                                                reviewVersion++
                                            }
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        applyUncertain = true
                                        message = "No se pudo verificar la aplicación. Reabrí FARO antes de reintentar."
                                        reviewVersion++
                                    } finally {
                                        applyBusy = false
                                    }
                                }
                            }
                            Unit
                        }
                    },
                )
            }
        }
        if (activeConversationId != null && composerPort != null) {
            if (loading) {
                Text("Recuperando borrador…", modifier = Modifier.padding(FaroV5Spacing.small))
            }
            val currentEditor = editor
            if (currentEditor != null) {
                FaroV5TextComposer(
                    editor = currentEditor,
                    onTextChanged = { text ->
                        if (!busy && !uncertain) {
                            editor = editor?.let { ConversationDraftPolicy.edit(it, text) }
                        }
                    },
                    onSend = null,
                    onSubmitEditor = { submitted ->
                        if (!busy && !uncertain && activeConversationId == submitted.persisted.conversationId) {
                            scope.launch {
                                busy = true
                                message = null
                                try {
                                    // Recheck canonical revision before saving. An outdated
                                    // editor is never silently rebased after a restore.
                                    val latest = withContext(Dispatchers.IO) {
                                        composerPort.read(activeConversationId)
                                    } ?: error("Workspace conversation is no longer available")
                                    if (latest != submitted.persisted) {
                                        editor = ConversationDraftPolicy.reconcile(submitted, latest)
                                        message = "El borrador cambió. Revisalo antes de enviar."
                                    } else {
                                        val save = ConversationDraftPolicy.saveRequest(
                                            submitted, IdempotencyKey(UUID.randomUUID().toString()),
                                        )
                                        val acknowledged = if (save == null) submitted.persisted else
                                            when (val result = withContext(Dispatchers.IO) { composerPort.save(save) }) {
                                                is ConversationDraftWriteResult.Saved -> result.snapshot
                                                is ConversationDraftWriteResult.Stale -> {
                                                    editor = ConversationDraftPolicy.reconcile(submitted, result.current)
                                                    message = "El borrador cambió. Revisalo antes de enviar."
                                                    null
                                                }
                                                else -> {
                                                    message = "Workspace rechazó guardar el borrador."
                                                    null
                                                }
                                            }
                                        if (acknowledged != null) {
                                            val committed = ConversationDraftPolicy.sendCandidate(
                                                ConversationDraftEditorState(acknowledged)
                                            )
                                            if (committed != null) {
                                                val request = ConversationDraftSendRequest(
                                                    candidate = committed,
                                                    messageId = ConversationMessageId(UUID.randomUUID().toString()),
                                                    turnId = ConversationTurnId(UUID.randomUUID().toString()),
                                                    createdAtEpochMillis = System.currentTimeMillis(),
                                                    idempotencyKey = IdempotencyKey(UUID.randomUUID().toString()),
                                                )
                                                when (withContext(Dispatchers.IO) { composerPort.sendDraft(request) }) {
                                                    is ConversationTimelineWriteResult.Saved -> {
                                                        val cleared = withContext(Dispatchers.IO) {
                                                            composerPort.read(activeConversationId)
                                                        }
                                                        editor = cleared?.let(::ConversationDraftEditorState)
                                                        timelineVersion++
                                                    }
                                                    is ConversationTimelineWriteResult.Stale -> {
                                                        message = "El borrador quedó desactualizado. Revisalo."
                                                        editor = editor?.copy(conflict = true)
                                                    }
                                                    else -> {
                                                        message = "No se pudo confirmar el envío; revisá el chat."
                                                        uncertain = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // The owner may have committed before callback failed.
                                    // Fail closed; do not issue another user message blindly.
                                    message = "No se pudo verificar el envío. Reabrí el chat antes de reintentar."
                                    uncertain = true
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onReviewConflict = {
                        val pending = editor
                        if (pending != null && !busy) scope.launch {
                            try {
                                val remote = withContext(Dispatchers.IO) {
                                    composerPort.read(pending.persisted.conversationId)
                                } ?: return@launch
                                editor = ConversationDraftPolicy.resolveAfterReview(
                                    pending, remote, keepLocalText = true,
                                )
                                message = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                message = "No se pudo revisar el borrador."
                            }
                        }
                    },
                    saving = busy || uncertain,
                )
            }
            if (message != null) {
                Text(message!!, modifier = Modifier.padding(FaroV5Spacing.small))
            }
        }
    }
}
