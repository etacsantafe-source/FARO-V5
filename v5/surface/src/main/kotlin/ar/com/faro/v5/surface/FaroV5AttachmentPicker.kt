package ar.com.faro.v5.surface

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ar.com.faro.v5.kernel.contracts.ConversationId

/**
 * A5: launches only the Android user-controlled file picker.
 * No ContentResolver read, draft mutation, URI persistence or model dispatch
 * occurs here. Caller binds the returned frozen conversation/request identities
 * to the scoped platform reader and the pure ingress policy.
 *
 * A process-death callback with no live request is ignored: user must pick again.
 */
@Composable
fun FaroV5AttachmentPicker(
    conversationId: ConversationId?,
    newPickerRequestId: () -> String,
    onPicked: (ConversationId, String, List<Uri>) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean = true,
) {
    var pending by remember { mutableStateOf<Pair<ConversationId, String>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val origin = pending
        pending = null
        if (origin != null && uris.isNotEmpty()) {
            onPicked(origin.first, origin.second, uris.toList())
        }
    }

    TextButton(
        enabled = enabled && conversationId != null && pending == null,
        onClick = {
            val origin = conversationId ?: return@TextButton
            val requestId = newPickerRequestId()
            if (requestId.isBlank()) {
                onError("No se pudo preparar la selección de archivos.")
                return@TextButton
            }
            pending = origin to requestId
            try {
                launcher.launch(
                    arrayOf("image/*", "application/pdf", "text/*", "application/json"),
                )
            } catch (failure: RuntimeException) {
                pending = null
                onError("No se pudo abrir el selector de archivos.")
            }
        },
    ) {
        Text("Adjuntar")
    }
}
