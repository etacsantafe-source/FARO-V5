package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import ar.com.faro.v5.kernel.contracts.ConversationDraftEditorState
import ar.com.faro.v5.kernel.contracts.ConversationDraftPolicy
import ar.com.faro.v5.kernel.contracts.ConversationDraftSendCandidate

/**
 * A2 bounded text composer. This controlled component never persists a draft
 * or submits a message on its own. Only an actual Workspace owner can provide
 * editor state and the S2.A3 delivery owner can enable onSend.
 *
 * The parent must key editor state by ConversationId and recover it from the
 * canonical Workspace after recreation. A saved Compose Bundle is not storage.
 */
@Composable
fun FaroV5TextComposer(
    editor: ConversationDraftEditorState,
    onTextChanged: (String) -> Unit,
    onSend: ((ConversationDraftSendCandidate) -> Unit)?,
    onSubmitEditor: ((ConversationDraftEditorState) -> Unit)? = null,
    onReviewConflict: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
) {
    val candidate = ConversationDraftPolicy.sendCandidate(editor)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.small),
    ) {
        OutlinedTextField(
            value = editor.text,
            onValueChange = { value ->
                if (value.length <= ConversationDraftPolicy.MAX_TEXT_CHARS) {
                    onTextChanged(value)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Preguntale a FARO") },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            enabled = !editor.conflict && !saving,
            isError = editor.conflict,
            supportingText = if (editor.conflict) {
                { Text("Este borrador cambió. Revisalo antes de continuar.") }
            } else null,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (editor.conflict && onReviewConflict != null) {
                FaroV5TextAction(
                    label = "Revisar borrador",
                    onClick = onReviewConflict,
                )
            }
            if (saving || editor.hasUnsavedChanges) {
                Text(
                    text = if (saving) "Guardando…" else "Cambios sin guardar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = FaroV5Spacing.small),
                )
            }
            if (onSubmitEditor != null) {
                FaroV5PrimaryAction(
                    label = "Enviar",
                    enabled = !editor.conflict && editor.text.isNotBlank() && !saving,
                    onClick = {
                        if (!editor.conflict && editor.text.isNotBlank() && !saving) {
                            // Host must acknowledge the text at canonical Workspace
                            // before invoking the owner-only atomic send.
                            onSubmitEditor(editor)
                        }
                    },
                )
            } else if (onSend != null) {
                FaroV5PrimaryAction(
                    label = "Enviar",
                    enabled = candidate != null && !saving,
                    onClick = {
                        val currentCandidate = ConversationDraftPolicy.sendCandidate(editor)
                        if (currentCandidate != null && !saving) onSend(currentCandidate)
                    },
                )
            }
        }
    }
}
