package ar.com.faro.v5.app

import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import ar.com.faro.v5.generation.FiveStoreJournalValidationResult
import ar.com.faro.v5.maintenance.BackupExternalWriteStatus
import java.util.UUID
import java.util.concurrent.Executors

/**
 * S2-only minimal, non-exported five-owner encrypted backup/restore consent
 * surface. Final Settings ownership belongs to S5. No automatic sharing,
 * pointer switching, V2 activation, or process-persistent URI/secret.
 *
 * A trusted internal caller must supply an existing restore operation and
 * target generation. This Activity never creates a restore journal.
 */
class FaroV5FiveStoreSafConsentActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RESTORE_OPERATION = "ar.com.faro.v5.f3.RESTORE_OPERATION"
        const val EXTRA_RESTORE_TARGET = "ar.com.faro.v5.f3.RESTORE_TARGET"
    }

    private sealed interface Pending {
        data class Export(val session: FiveStoreSafExportSession) : Pending
        data class Preview(val passphrase: CharArray) : Pending
        data class Restore(
            val operation: String,
            val target: String,
            val passphrase: CharArray,
        ) : Pending
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var pending: Pending? = null
    private var activeCancellation: CancellationSignal? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var passphrase: EditText
    private lateinit var exportConsent: CheckBox
    private lateinit var restoreConsent: CheckBox
    private lateinit var previewConsent: CheckBox
    private lateinit var share: CheckBox
    private lateinit var export: Button
    private lateinit var restore: Button
    private lateinit var preview: Button

    private val picker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pending
        pending = null
        if (request == null) {
            // Process/Activity recreation must never resume a stale secret or
            // write an external document without renewed explicit consent.
            showStatus("Selección interrumpida. Confirmá nuevamente.")
            setBusy(false)
            return@registerForActivityResult
        }
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        val stillConfirmed = when (request) {
            is Pending.Export -> exportConsent.isChecked
            is Pending.Restore -> restoreConsent.isChecked
            is Pending.Preview -> previewConsent.isChecked
        }
        val stillShared = share.isChecked
        // Consent is operation-specific and one-shot, even if SAF is cancelled.
        exportConsent.isChecked = false
        restoreConsent.isChecked = false
        previewConsent.isChecked = false
        share.isChecked = false
        val cancellation = CancellationSignal()
        activeCancellation = cancellation
        setBusy(true)
        worker.execute {
            val message = try {
                val runtime = (application as FaroV5Application)
                    .fiveStoreSafConsentRuntime()
                when (request) {
                    is Pending.Export -> {
                        val receipt = runtime.completeConsentedFiveStoreExportPicker(
                            request.session, contentResolver, uri, cancellation,
                            stillConfirmed, stillShared,
                        )
                        when (receipt.status) {
                            BackupExternalWriteStatus.SUCCESS ->
                                "Respaldo cifrado exportado y verificado."
                            BackupExternalWriteStatus.CANCELLED ->
                                "Exportación cancelada; no se compartió el respaldo."
                            BackupExternalWriteStatus.EFFECT_UNKNOWN ->
                                "Resultado externo incierto. No repitas la exportación sin revisar."
                            BackupExternalWriteStatus.FAILED ->
                                "Exportación bloqueada o fallida; no se confirmó el respaldo."
                        }
                    }
                    is Pending.Preview -> {
                        try {
                            if (!stillConfirmed || uri == null) {
                                "Verificación cancelada; no se restauraron datos."
                            } else if (runtime.verifyConsentedFiveStorePickedDocument(
                                    contentResolver, uri, System.currentTimeMillis(),
                                    cancellation, request.passphrase, true,
                                )) {
                                "Respaldo cifrado FAROBAK2 autenticado: cinco almacenes. No restaurado."
                            } else {
                                "El archivo o la contraseña no se validaron. No se restauraron datos."
                            }
                        } finally {
                            request.passphrase.fill('\u0000')
                        }
                    }
                    is Pending.Restore -> {
                        try {
                            if (!stillConfirmed || uri == null) {
                                "Restauración cancelada; V1 sigue activa."
                            } else {
                                val outcome = runtime.restoreConsentedFiveStorePickedDocument(
                                    request.operation, request.target, contentResolver, uri,
                                    System.currentTimeMillis(), cancellation,
                                    request.passphrase, true,
                                )
                                if (outcome is FiveStoreJournalValidationResult.Validated)
                                    "Archivo autenticado y validado en cuarentena. V1 sigue activa."
                                else "No se validó el respaldo; V1 sigue activa."
                            }
                        } finally {
                            request.passphrase.fill('\u0000')
                        }
                    }
                }
            } catch (_: Exception) {
                if (request is Pending.Restore) request.passphrase.fill('\u0000')
                if (request is Pending.Preview) request.passphrase.fill('\u0000')
                "Operación no disponible o bloqueada. V1 sigue activa."
            }
            runOnUiThread {
                if (activeCancellation === cancellation) activeCancellation = null
                if (!isDestroyed) {
                    showStatus(message)
                    setBusy(false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = "Respaldo cifrado de FARO · S2"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "Incluye cinco almacenes. No incluye credenciales. " +
                "Exportar comparte un archivo sólo al destino elegido. " +
                "Restaurar valida en cuarentena y no activa V2."
        })
        passphrase = EditText(this).apply {
            hint = "Contraseña del respaldo (mínimo 12 caracteres)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setSaveEnabled(false)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(passphrase)
        exportConsent = CheckBox(this).apply {
            text = "Confirmo exportar los cinco almacenes cifrados al destino elegido."
            isChecked = false
            setSaveEnabled(false)
        }
        column.addView(exportConsent)
        restoreConsent = CheckBox(this).apply {
            text = "Confirmo restaurar un archivo cifrado en cuarentena, sin activar V2."
            isChecked = false
            setSaveEnabled(false)
        }
        column.addView(restoreConsent)
        previewConsent = CheckBox(this).apply {
            text = "Confirmo verificar un respaldo cifrado sin restaurarlo."
            isChecked = false
            setSaveEnabled(false)
        }
        column.addView(previewConsent)
        share = CheckBox(this).apply {
            text = "Para exportar, acepto elegir un destino externo mediante Android."
            isChecked = false
            setSaveEnabled(false)
        }
        column.addView(share)
        export = Button(this).apply { text = "Exportar respaldo cifrado" }
        column.addView(export)
        restore = Button(this).apply { text = "Elegir archivo para restaurar en cuarentena" }
        column.addView(restore)
        preview = Button(this).apply { text = "Verificar archivo cifrado (sin restaurar)" }
        column.addView(preview)
        status = TextView(this).apply {
            text = if (savedInstanceState == null)
                "Esperando confirmación explícita."
            else "Sesión anterior descartada. Confirmá nuevamente."
        }
        column.addView(status)
        val scroll = ScrollView(this)
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        val operation = intent.getStringExtra(EXTRA_RESTORE_OPERATION)
        val target = intent.getStringExtra(EXTRA_RESTORE_TARGET)
        restore.isEnabled = !operation.isNullOrBlank() && !target.isNullOrBlank()
        export.setOnClickListener { startExport() }
        preview.setOnClickListener { startPreview() }
        restore.setOnClickListener {
            if (!operation.isNullOrBlank() && !target.isNullOrBlank())
                startRestore(operation, target)
        }
    }

    private fun takePassphrase(): CharArray? {
        // Avoid an immutable String copy of the user's backup secret.
        val editable = passphrase.text
        val chars = CharArray(editable.length) { index -> editable[index] }
        editable.clear()
        if (chars.size in 12..1024 &&
            chars.any { !it.isWhitespace() } &&
            chars.none { it == '\u0000' }
        ) return chars
        chars.fill('\u0000')
        // A rejected secret cannot leave a reusable export/restore/preview grant.
        exportConsent.isChecked = false
        restoreConsent.isChecked = false
        previewConsent.isChecked = false
        share.isChecked = false
        showStatus("La contraseña debe tener entre 12 y 1024 caracteres y contener texto.")
        return null
    }

    private fun startExport() {
        if (busy || !exportConsent.isChecked || !share.isChecked) {
            showStatus("Exportar requiere confirmar y aceptar el destino externo.")
            return
        }
        val chars = takePassphrase() ?: return
        setBusy(true)
        worker.execute {
            try {
                val runtime = (application as FaroV5Application)
                    .fiveStoreSafConsentRuntime()
                val id = UUID.randomUUID().toString()
                val session = runtime.prepareConsentedFiveStoreSafExport(
                    "f3-export-$id", "f3-backup-$id", "5.0.0-dev",
                    System.currentTimeMillis(), chars, true, true, "f3-saf-$id",
                )
                val document = runtime.createConsentedFiveStoreExportPicker(
                    session, true, true,
                )
                runOnUiThread {
                    if (!isDestroyed) {
                        pending = Pending.Export(session)
                        try {
                            picker.launch(document)
                        } catch (_: Exception) {
                            discardPendingPickerRequest()
                            exportConsent.isChecked = false
                            share.isChecked = false
                            showStatus("No se pudo abrir el selector. Exportación cancelada.")
                            setBusy(false)
                        }
                    } else {
                        // No external URI exists; close the prepared attempt.
                        runCatching { runtime.cancelConsentedFiveStoreExportPicker(session) }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isDestroyed) {
                        exportConsent.isChecked = false
                        share.isChecked = false
                        showStatus("Exportación no autorizada. V2 permanece desactivada.")
                        setBusy(false)
                    }
                }
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    private fun startPreview() {
        if (busy || !previewConsent.isChecked) {
            showStatus("Verificar requiere confirmación específica.")
            return
        }
        val chars = takePassphrase() ?: return
        setBusy(true)
        worker.execute {
            try {
                val document = (application as FaroV5Application)
                    .fiveStoreSafConsentRuntime()
                    .createConsentedFiveStorePreviewPicker(true)
                runOnUiThread {
                    if (!isDestroyed) {
                        pending = Pending.Preview(chars)
                        try {
                            picker.launch(document)
                        } catch (_: Exception) {
                            discardPendingPickerRequest()
                            previewConsent.isChecked = false
                            showStatus("No se pudo abrir el selector. No se restauraron datos.")
                            setBusy(false)
                        }
                    } else chars.fill('\u0000')
                }
            } catch (_: Exception) {
                chars.fill('\u0000')
                runOnUiThread {
                    if (!isDestroyed) {
                        previewConsent.isChecked = false
                        showStatus("Verificación no disponible. No se restauraron datos.")
                        setBusy(false)
                    }
                }
            }
        }
    }

    private fun startRestore(operation: String, target: String) {
        if (busy || !restoreConsent.isChecked) {
            showStatus("Restaurar requiere confirmación específica.")
            return
        }
        val chars = takePassphrase() ?: return
        setBusy(true)
        worker.execute {
            try {
                val document = (application as FaroV5Application)
                    .fiveStoreSafConsentRuntime()
                    .createConsentedFiveStoreRestorePicker(operation, target, true)
                runOnUiThread {
                    if (!isDestroyed) {
                        pending = Pending.Restore(operation, target, chars)
                        try {
                            picker.launch(document)
                        } catch (_: Exception) {
                            discardPendingPickerRequest()
                            restoreConsent.isChecked = false
                            showStatus("No se pudo abrir el selector. Restauración cancelada.")
                            setBusy(false)
                        }
                    } else chars.fill('\u0000')
                }
            } catch (_: Exception) {
                chars.fill('\u0000')
                runOnUiThread {
                    if (!isDestroyed) {
                        restoreConsent.isChecked = false
                        showStatus("No existe restauración autorizada para este archivo.")
                        setBusy(false)
                    }
                }
            }
        }
    }

    /** Drop a picker that never returned a URI, including rotation/destruction. */
    private fun discardPendingPickerRequest() {
        val request = pending
        pending = null
        when (request) {
            is Pending.Preview -> request.passphrase.fill('\u0000')
            is Pending.Restore -> request.passphrase.fill('\u0000')
            is Pending.Export -> runCatching {
                (application as FaroV5Application)
                    .fiveStoreSafConsentRuntime()
                    .cancelConsentedFiveStoreExportPicker(request.session)
            }
            null -> Unit
        }
    }

    private fun showStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun setBusy(active: Boolean) {
        busy = active
        export.isEnabled = !active
        exportConsent.isEnabled = !active
        restoreConsent.isEnabled = !active
        share.isEnabled = !active
        previewConsent.isEnabled = !active
        preview.isEnabled = !active
        restore.isEnabled = !active &&
            !intent.getStringExtra(EXTRA_RESTORE_OPERATION).isNullOrBlank() &&
            !intent.getStringExtra(EXTRA_RESTORE_TARGET).isNullOrBlank()
    }

    override fun onDestroy() {
        activeCancellation?.cancel()
        activeCancellation = null
        discardPendingPickerRequest()
        worker.shutdownNow()
        if (::passphrase.isInitialized) passphrase.text.clear()
        super.onDestroy()
    }
}
