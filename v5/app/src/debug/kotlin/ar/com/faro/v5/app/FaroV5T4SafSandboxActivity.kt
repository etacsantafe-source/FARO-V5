package ar.com.faro.v5.app

import android.app.Activity
import android.content.ContentResolver
import android.os.Bundle
import android.os.CancellationSignal
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.generation.*
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.platform.android.backup.AndroidBackupSafAdapter
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.function.Supplier

/**
 * T4-only debug harness for REAL Android DocumentsUI, strictly synthetic data.
 * NEVER reads the host application's active pointer, live Workspace or Reality.
 * The normal shipping SAF surface stays non-exported and V2 remains OFF.
 */
class FaroV5T4SafSandboxActivity : ComponentActivity() {
    private enum class Step { EXPORT, RESTORE }
    // One-shot proof of explicit consent at the moment Android's picker is launched.
    // Never depend on the CheckBox's potentially restored UI state after picker return.
    private data class PickerGrant(val step: Step, val consentAtLaunch: Boolean)
    private data class Ready(
        val source: PreparedBackupExternalWriteSource,
        val adapter: AndroidBackupSafAdapter,
    )
    /**
     * Survives Activity recreation while Android DocumentsUI is open, but NOT
     * process death. The latter must fail closed and demand fresh consent.
     * No credential or persistent authorization is written to a Bundle/disk.
     */
    private class RetainedPickerState : ViewModel() {
        var root: Path? = null
        var ready: Ready? = null
        var pending: PickerGrant? = null
    }
    private lateinit var retained: RetainedPickerState
    private val password = "synthetic-only-t4-manual-20261101"
    private val worker = Executors.newSingleThreadExecutor()
    private val testRoot: Path get() = checkNotNull(retained.root)
    private lateinit var status: TextView
    private lateinit var exportConsent: CheckBox
    private lateinit var restoreConsent: CheckBox
    private lateinit var exportButton: Button
    private lateinit var restoreButton: Button
    private var ready: Ready?
        get() = retained.ready
        set(value) { retained.ready = value }
    private var pending: PickerGrant?
        get() = retained.pending
        set(value) { retained.pending = value }

    private val picker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ticket = pending
        pending = null
        val step = ticket?.step
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        val granted = ticket?.consentAtLaunch == true
        exportConsent.isChecked = false
        restoreConsent.isChecked = false
        if (uri == null) {
            status.text = "CANCELADO: no se escribieron ni restauraron datos."
            busy(false)
            return@registerForActivityResult
        }
        if (!granted) {
            status.text = "ROJO: se perdió la sesión autorizada del selector (posible cierre de proceso). " +
                "No se escribió nada. Volvé a autorizar antes de repetir."
            ready = null
            busy(false)
            return@registerForActivityResult
        }
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            // Do not disclose document names, URI values or personal content in diagnostics.
            status.text = "ROJO: Android devolvió un tipo de URI distinto de content://. Sin efectos."
            busy(false)
            return@registerForActivityResult
        }
        worker.execute {
            val resultText = try {
                when (step) {
                    Step.EXPORT -> exportSelected(uri)
                    Step.RESTORE -> restoreSelected(uri)
                    null -> "ROJO: operación sin identidad"
                }
            } catch (e: Exception) {
                "ROJO: " + e.javaClass.simpleName + ". Sin activar V2."
            }
            runOnUiThread {
                if (!isDestroyed) {
                    status.text = resultText
                    busy(false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        retained = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == RetainedPickerState::class.java)
                @Suppress("UNCHECKED_CAST")
                return RetainedPickerState() as T
            }
        })[RetainedPickerState::class.java]
        if (retained.root == null) {
            retained.root = Files.createTempDirectory(noBackupFilesDir.toPath(), "t4-saf-synthetic-")
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = "FARO T4 - SAF REAL (datos sintéticos)"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "SOLO TEST: crea cinco SQLite aisladas en almacenamiento temporal. " +
                "El archivo exportado contiene únicamente datos artificiales. " +
                "No toca FARO habitual, no habilita V2, no usa la clave Groq. " +
                "Elegí un archivo de prueba en Descargas. No selecciones respaldos personales."
        })
        exportConsent = CheckBox(this).apply {
            text = "Autorizo guardar el respaldo SINTÉTICO en el destino que elija."
        }
        column.addView(exportConsent)
        exportButton = Button(this).apply { text = "1. Exportar prueba con selector Android" }
        column.addView(exportButton)
        restoreConsent = CheckBox(this).apply {
            text = "Autorizo leer el archivo de prueba elegido y restaurarlo SOLO en cuarentena."
        }
        column.addView(restoreConsent)
        restoreButton = Button(this).apply {
            text = "2. Elegir archivo y restaurar en cuarentena"
            isEnabled = false
        }
        column.addView(restoreButton)
        status = TextView(this).apply {
            text = "Pendiente de consentimiento. V2 OFF."
            textSize = 16f
        }
        column.addView(status)
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        // A new Activity instance may be created while DocumentsUI is visible.
        // Only a live, retained ViewModel may preserve the one-shot ticket.
        // If Android killed the process, no ticket is resurrected from Bundle.
        if (savedInstanceState != null && pending != null) {
            status.text = "Selector SAF en curso (sesión conservada tras recreación)."
        } else if (savedInstanceState != null) {
            status.text = "Sesión SAF interrumpida. Se requiere nueva autorización."
        }
        busy(false)
        exportButton.setOnClickListener {
            if (!exportConsent.isChecked) {
                status.text = "Marcá primero el consentimiento de exportación sintética."
                return@setOnClickListener
            }
            busy(true)
            status.text = "Preparando cinco almacenes aislados..."
            worker.execute {
                try {
                    val prepared = prepareSyntheticExport()
                    runOnUiThread {
                        if (!isDestroyed && exportConsent.isChecked) {
                            ready = prepared
                            pending = PickerGrant(Step.EXPORT, consentAtLaunch = true)
                            try {
                                picker.launch(prepared.adapter.createExportDocumentIntent(prepared.source))
                            } catch (_: Exception) {
                                pending = null
                                status.text = "ROJO: no se pudo abrir el selector Android. Sin efectos."
                                busy(false)
                            }
                        } else {
                            busy(false)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (!isDestroyed) {
                            status.text = "ROJO al preparar prueba: " + e.javaClass.simpleName
                            busy(false)
                        }
                    }
                }
            }
        }
        restoreButton.setOnClickListener {
            if (!restoreConsent.isChecked || ready == null) {
                status.text = "Primero exportá y autorizá la restauración sintética."
                return@setOnClickListener
            }
            busy(true)
            pending = PickerGrant(Step.RESTORE, consentAtLaunch = true)
            try {
                picker.launch(ready!!.adapter.createRestoreDocumentIntent())
            } catch (_: Exception) {
                pending = null
                status.text = "ROJO: no se pudo abrir el selector Android de restauración."
                busy(false)
            }
        }
    }

    private fun busy(isBusy: Boolean) {
        exportButton.isEnabled = !isBusy && ready == null
        restoreButton.isEnabled = !isBusy && ready != null
    }

    private fun prepareSyntheticExport(): Ready {
        val v1 = Files.createDirectory(testRoot.resolve("v1-sentinel"))
        Files.write(v1.resolve("protected"), "V1-untouched".toByteArray())
        val id = UUID.randomUUID().toString()
        val staged = FileStorageGenerationLayout(testRoot).stageWorkspaceV2(
            StorageGenerationDescriptor(
                StorageGenerationId("t4-synthetic-" + id),
                "schema-five-store-1",
                System.currentTimeMillis(),
            )
        )
        val dir = staged.directory
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        val gate = SingleProcessMaterialWriteGate()
        val source = FaroV5QuarantinedV2GraphAdmission.canonical(gate, Supplier { v1 })
            .open(staged).use { graph ->
                check(graph.verifyHealthy())
                val lease = object : MaterialWriteQuiescencePort {
                    override fun <T> withMaterialWritesQuiesced(
                        operationId: MaintenanceOperationId,
                        block: () -> T,
                    ): T = block()
                }
                FaroV5FiveStoreExportPreparation(
                    graph,
                    lease,
                    FilePortableBackupArchiveStore(testRoot.resolve("encrypted")),
                ) {
                    !FaroV5HostCertification.V2_ACTIVE_ENABLED &&
                        graph.verifyHealthy() &&
                        staged.directory.toRealPath() != v1.toRealPath()
                }.prepare(
                    MaintenanceOperationId("t4-synthetic-export-" + id),
                    BackupSetId("t4-synthetic-backup-" + id),
                    "5.0-t4-only",
                    System.currentTimeMillis(),
                    password.toCharArray(),
                    FiveStoreBackupExportConsent(true, true),
                    "t4-synthetic-effect-" + id,
                )
            }
        return Ready(
            source,
            AndroidBackupSafAdapter(
                BackupExternalEffectCoordinator(FileBackupExternalAttemptStore(testRoot))
            ),
        )
    }

    private fun exportSelected(uri: android.net.Uri): String {
        val prep = checkNotNull(ready)
        val receipt = prep.adapter.writeSelectedExportDocument(
            contentResolver, uri, prep.source, CancellationSignal()
        )
        check(Files.readString(testRoot.resolve("v1-sentinel/protected")) == "V1-untouched")
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        return if (receipt.status == BackupExternalWriteStatus.SUCCESS) {
            "VERDE: respaldo sintético cifrado escrito y verificado por Android. " +
                "Ahora elegí ESE MISMO archivo para restauración aislada."
        } else "ROJO: exportación " + receipt.status + ". No continuar con restore."
    }

    private fun restoreSelected(uri: android.net.Uri): String {
        val prep = checkNotNull(ready)
        val privateStore = FilePortableBackupArchiveStore(testRoot.resolve("ingress"))
        val artifact = prep.adapter.stageSelectedRestoreArchive(
            contentResolver, uri, privateStore,
            System.currentTimeMillis(), CancellationSignal(),
        )
        try {
            check(privateStore.verify(artifact))
            val encrypted = privateStore.readBytes(artifact)
            try {
                val opened = FaroV5FiveStorePortableBackupCrypto().open(
                    encrypted, password.toCharArray()
                )
                check(opened is FiveStorePortableBackupOpenResult.Opened)
                val layout = FileStorageGenerationLayout(testRoot)
                val predecessor = StorageGenerationDescriptor(
                    StorageGenerationId("t4-v1-predecessor"),
                    "v1-protected", System.currentTimeMillis(),
                )
                layout.stage(predecessor)
                val pointer = FileActiveGenerationPointer(testRoot)
                check(pointer.compareAndSet(null, predecessor.generationId) != null)
                val before = pointer.read()
                val op = MaintenanceOperationId("t4-restore-" + UUID.randomUUID().toString())
                val target = StorageGenerationDescriptor(
                    StorageGenerationId("t4-restore-quarantine-" + UUID.randomUUID().toString()),
                    "schema-five-store-1", System.currentTimeMillis(),
                )
                val lease = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
                val staged = FaroV5QuarantinedRestorePreparation(testRoot, lease).prepare(
                    op, target, expectedPreviousGenerationId = predecessor.generationId
                )
                val journal = FileBootstrapJournal(testRoot)
                val verified = FaroV5GenerationStoreGraph.openValidatedV2(staged).use { graph ->
                    val identity = FileRestoreArchiveIdentity(testRoot)
                    val admission = FaroV5RestoreJournalAdmission(
                        journal, pointer, target.generationId, lease,
                    )
                    val restored = FaroV5FiveStoreStagedRestore(
                        testRoot, staged, graph, admission, archiveIdentity = identity,
                    ).restoreAuthenticated(op, encrypted, password.toCharArray())
                    check(restored is FiveStoreStagedRestoreResult.StagedVerified)
                    FaroV5StagedRestoreJournalValidator(
                        testRoot, staged, graph, journal, pointer, identity, lease,
                    ).validateAuthenticatedStage(op, encrypted, password.toCharArray())
                }
                check(verified is FiveStoreJournalValidationResult.Validated)
                check(pointer.read() == before)
                check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
                check(Files.readString(testRoot.resolve("v1-sentinel/protected")) == "V1-untouched")
                return "VERDE: selector SAF real, cinco almacenes autenticados, " +
                    "journal VALIDATED, V1 intacta y V2 OFF."
            } finally {
                encrypted.fill(0)
            }
        } finally {
            privateStore.delete(artifact)
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
