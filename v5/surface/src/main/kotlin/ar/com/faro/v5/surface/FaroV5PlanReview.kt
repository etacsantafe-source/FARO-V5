package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState

/**
 * Read-only A4 review surface; NOT mounted in app until the canonical durable
 * Workspace plan owner and Reality receipt bridge are certified.
 * Every callback is a request. The owner MUST re-read identity+revision and
 * run the existing ApplyPreflight/Retry/Receipt gates before any effect.
 */
@Composable
fun FaroV5PlanReview(
    review: ConversationPlanReviewSnapshot,
    reviewExpanded: Boolean,
    modifier: Modifier = Modifier,
    onOpenReview: (() -> Unit)? = null,
    onConfirm: ((ConversationPlanReviewContext) -> Unit)? = null,
    onRequestApply: ((ConversationPlanReviewContext) -> Unit)? = null,
    onDiscard: ((ConversationPlanReviewContext) -> Unit)? = null,
    activeApplyVerified: Boolean = false,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.padding(FaroV5Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
        ) {
            Text("Cambios propuestos", style = MaterialTheme.typography.titleMedium)
            Text(faroV5ReviewStateLabel(review.state, activeApplyVerified),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reviewExpanded) {
                Text(review.reviewText, style = MaterialTheme.typography.bodyMedium)
                Text("Nada cambia en tus datos hasta que confirmes y solicites aplicar.",
                    style = MaterialTheme.typography.bodySmall)
            }
            when (review.state) {
                ConversationPlanReviewState.PENDING -> {
                    if (!reviewExpanded && onOpenReview != null) {
                        TextButton(onClick = onOpenReview) { Text("Revisar cambios") }
                    }
                    if (reviewExpanded && onConfirm != null) {
                        TextButton(onClick = { onConfirm(review.context()) }) {
                            Text("Confirmar propuesta")
                        }
                    }
                    if (reviewExpanded && onDiscard != null) {
                        TextButton(onClick = { onDiscard(review.context()) }) {
                            Text("Descartar")
                        }
                    }
                }
                ConversationPlanReviewState.APPROVED -> {
                    if (onRequestApply != null && reviewExpanded) {
                        TextButton(onClick = { onRequestApply(review.context()) }) {
                            Text("Aplicar cambios")
                        }
                    } else if (!reviewExpanded && onOpenReview != null) {
                        TextButton(onClick = onOpenReview) { Text("Ver propuesta aprobada") }
                    }
                    if (onDiscard != null) {
                        TextButton(onClick = { onDiscard(review.context()) }) {
                            Text("Descartar")
                        }
                    }
                }
                ConversationPlanReviewState.APPLYING,
                ConversationPlanReviewState.RECOVERING_UNKNOWN -> {
                    // Reconciliation-only for an uncertain effect: trusted host re-reads
                    // Reality receipt; it must never blindly execute a second Apply.
                    if (onRequestApply != null && reviewExpanded) {
                        TextButton(onClick = { onRequestApply(review.context()) }) {
                            Text("Verificar resultado")
                        }
                    }
                }
                else -> Unit // Terminal outcomes are read-only.
            }
        }
    }
}

/** Processing state is not trusted after process death without a live attempt. */
internal fun faroV5ReviewStateLabel(
    state: ConversationPlanReviewState,
    activeApplyVerified: Boolean = false,
): String = when (state) {
    ConversationPlanReviewState.PENDING -> "Pendiente de revisión"
    ConversationPlanReviewState.APPROVED -> "Confirmado. Todavía no se aplicó."
    ConversationPlanReviewState.APPLYING -> if (activeApplyVerified) "Aplicando cambios…"
        else "Estado de aplicación pendiente de verificar"
    ConversationPlanReviewState.RECOVERING_UNKNOWN -> "Verificando si los cambios se aplicaron"
    ConversationPlanReviewState.REJECTED -> "No se aplicaron los cambios"
    ConversationPlanReviewState.COMMITTED -> "Aplicado y registrado"
    ConversationPlanReviewState.DISCARDED -> "Propuesta descartada"
}
