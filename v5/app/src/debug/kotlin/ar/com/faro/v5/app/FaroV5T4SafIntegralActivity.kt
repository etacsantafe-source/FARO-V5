package ar.com.faro.v5.app

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
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
 * T4-only isolated synthetic harness, NOT the normal SAF / production gateway.
 *
 * Design invariant: an Android picker result is NOT authorization to write or
 * restore. Separate, deterministic ActivityResult launchers identify the action
 * even when Android destroys and recreates this Activity or its entire process.
 * A newly checked box and explicit button press AFTER picker return are required.
 *
 * No URI/consent/secret is saved to disk/Bundle; all staged data lives under a
 * new noBackupFilesDir temp directory and is deleted after each operation.
 * No V1 production pointer or real Reality/Workspace store is opened.
 */
class FaroV5T4SafIntegralActivity : ComponentActivity() {
    private enum class Step { EXPORT, RESTORE }
    private data class Selection(val step: Step, val uri: Uri)
    private data class Ready(
        val source: PreparedBackupExternalWriteSource,
        val adapter: AndroidBackupSafAdapter,
    )

    // Retained across a configuration recreation only. A process death resets
    // this state, but delivery through either launcher still demands fresh consent.
    private class UiState : ViewModel() {
        var pickerOpen: Step? = null
        var selection: Selection? = null
        val gate = FaroV5T4PostPickerGate()
    }

    private val password = "synthetic-only-t4-manual-20261101"
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var retained: UiState
    private lateinit var status: TextView
    private lateinit var consent: CheckBox
    private lateinit var exportButton: Button
    private lateinit var restoreButton: Button
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private var isWorking = false
    private var activeCancellation: CancellationSignal? = null

    // TWO stable launchers avoid depending on a lost pending ticket, a restored
    // checkbox, ViewModel retention or a previous process after returning from SAF.
    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handlePicked(Step.EXPORT, result) }
    private val restorePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handlePicked(Step.RESTORE, result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        retained = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == UiState::class.java)
                @Suppress("UNCHECKED_CAST")
                return UiState() as T
            }
        })[UiState::class.java]
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = "FARO T4 - SAF integral aislado"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "Solo cinco almacenes SINTÉTICOS. No toca FARO habitual, no habilita V2, " +
                "no usa Groq. Primero elegí destino o archivo. Al volver, marcá " +
                "el consentimiento NUEVO y confirmá. Para restaurar elegí solamente " +
                "un respaldo sintético exportado por esta prueba."
        })
        exportButton = Button(this).apply { text = "1. Elegir DESTINO con Android" }
        column.addView(exportButton)
        restoreButton = Button(this).apply { text = "3. Elegir RESPALDO sintético" }
        column.addView(restoreButton)
        consent = CheckBox(this).apply {
            text = "Autorizo la operación elegida, DESPUÉS de regresar del selector."
        }
        column.addView(consent)
        confirmButton = Button(this).apply { text = "2/4. Confirmar operación seleccionada" }
        column.addView(confirmButton)
        cancelButton = Button(this).apply { text = "Cancelar operación en curso" }
        column.addView(cancelButton)
        status = TextView(this).apply {
            text = "Elegí destino o archivo. V2 OFF."
            textSize = 16f
        }
        column.addView(status)
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        })

        // Never restore a prior checkbox, consent, Uri or passphrase from Bundle.
        consent.isChecked = false
        status.text = when {
            retained.selection != null ->
                "Selección recuperada tras recreación. Requiere NUEVA confirmación."
            savedInstanceState != null ->
                "Pantalla recreada: al volver del selector deberá confirmar de nuevo."
            else -> "Elegí destino o respaldo sintético. V2 OFF."
        }
        exportButton.setOnClickListener { startPicker(Step.EXPORT) }
        restoreButton.setOnClickListener { startPicker(Step.RESTORE) }
        confirmButton.setOnClickListener {
            val selected = retained.selection
            if (selected == null) {
                status.text = "Primero elegí un destino o archivo desde Android."
                return@setOnClickListener
            }
            if (!retained.gate.authorizeAfterReturn(selected.step.name, consent.isChecked)) {
                status.text = "Falta consentimiento NUEVO después del selector."
                return@setOnClickListener
            }
            if (selected.uri.scheme != ContentResolver.SCHEME_CONTENT) {
                retained.selection = null
                consent.isChecked = false
                status.text = "ROJO: Android no devolvió content://. Sin operación."
                updateButtons()
                return@setOnClickListener
            }
            retained.selection = null
            consent.isChecked = false
            isWorking = true
            updateButtons()
            status.text = if (selected.step == Step.EXPORT)
                "Preparando y verificando cinco almacenes sintéticos..."
            else "Importando y validando solamente en cuarentena..."
            val cancellation = CancellationSignal()
            activeCancellation = cancellation
            worker.execute {
                val resultText = try {
                    cancellation.throwIfCanceled()
                    val root = Files.createTempDirectory(
                        noBackupFilesDir.toPath(), "t4-saf-integral-"
                    )
                    try {
                        cancellation.throwIfCanceled()
                        when (selected.step) {
                            Step.EXPORT -> {
                                val prepared = prepareSyntheticExport(root)
                                cancellation.throwIfCanceled()
                                exportSelected(selected.uri, prepared, root, cancellation)
                            }
                            Step.RESTORE -> restoreSelected(selected.uri, root, cancellation)
                        }
                    } finally {
                        // Best effort cleanup. Never remove user-selected document.
                        root.toFile().deleteRecursively()
                    }
                } catch (_: OperationCanceledException) {
                    "CANCELADO: un destino externo parcialmente escrito puede requerir eliminación manual."
                } catch (e: Exception) {
                    "ROJO: " + e.javaClass.simpleName +
                        ". No se activó V2. Si fue exportación, descartá el archivo externo."
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        activeCancellation = null
                        isWorking = false
                        status.text = resultText
                        updateButtons()
                    }
                }
            }
        }
        cancelButton.setOnClickListener {
            activeCancellation?.cancel()
            status.text = "Cancelando... el archivo de destino podría estar incompleto."
        }
        updateButtons()
    }

    private fun startPicker(step: Step) {
        if (isWorking || retained.pickerOpen != null) return
        retained.selection = null
        retained.gate.reset()
        retained.pickerOpen = step
        consent.isChecked = false
        updateButtons()
        status.text = "Selector Android abierto. Todavía no hay autorización para escribir."
        try {
            when (step) {
                Step.EXPORT -> exportPicker.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_TITLE, "faro-t4-sintetico.farobak")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                )
                Step.RESTORE -> restorePicker.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        } catch (e: Exception) {
            retained.pickerOpen = null
            status.text = "ROJO: no se abrió el selector Android (" +
                e.javaClass.simpleName + ")."
            updateButtons()
        }
    }

    private fun handlePicked(step: Step, result: ActivityResult) {
        retained.pickerOpen = null
        retained.selection = null
        retained.gate.reset()
        consent.isChecked = false
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        if (result.resultCode != Activity.RESULT_OK) {
            status.text = "CANCELADO: FARO no inició escritura ni restauración."
        } else if (uri == null) {
            status.text = "ROJO: selector devolvió OK sin archivo. Sin operación FARO."
        } else if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            status.text = "ROJO: URI distinto de content://. Sin operación FARO."
        } else {
            retained.selection = Selection(step, uri)
            retained.gate.pickerReturned(step.name, true)
            status.text = if (step == Step.EXPORT)
                "Destino elegido. NO se escribió todavía. Marcá el consentimiento " +
                    "y pulsá CONFIRMAR para exportar los cinco almacenes sintéticos."
            else "Archivo elegido. NO se restauró todavía. Marcá el consentimiento " +
                "y pulsá CONFIRMAR para validar en cuarentena."
        }
        updateButtons()
    }

    private fun updateButtons() {
        val idle = !isWorking && retained.pickerOpen == null
        exportButton.isEnabled = idle
        restoreButton.isEnabled = idle
        consent.isEnabled = idle && retained.selection != null
        confirmButton.isEnabled = idle && retained.selection != null
        cancelButton.isEnabled = isWorking
    }

    private fun prepareSyntheticExport(testRoot: Path): Ready {
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

    private fun exportSelected(uri: Uri, prep: Ready, testRoot: Path, cancellation: CancellationSignal): String {
        // A fresh attempt starts only AFTER the post-picker human authorization.
        prep.adapter.createExportDocumentIntent(prep.source)
        val receipt = prep.adapter.writeSelectedExportDocument(
            contentResolver, uri, prep.source, cancellation
        )
        check(Files.readString(testRoot.resolve("v1-sentinel/protected")) == "V1-untouched")
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        return if (receipt.status == BackupExternalWriteStatus.SUCCESS) {
            "VERDE: respaldo sintético cifrado escrito y verificado por Android. " +
                "Ahora elegí ESE MISMO archivo para restauración aislada."
        } else "ROJO: exportación " + receipt.status + ". No continuar con restore."
    }

    private fun restoreSelected(uri: Uri, testRoot: Path, cancellation: CancellationSignal): String {
        // Each attempt has a fresh isolated sentinel; restore never depends on a previous
        // Activity, picker session, or a source that may have died with the process.
        val sentinel = Files.createDirectory(testRoot.resolve("v1-sentinel"))
        Files.write(sentinel.resolve("protected"), "V1-untouched".toByteArray())
        val adapter = AndroidBackupSafAdapter(
            BackupExternalEffectCoordinator(FileBackupExternalAttemptStore(testRoot))
        )
        val privateStore = FilePortableBackupArchiveStore(testRoot.resolve("ingress"))
        val artifact = adapter.stageSelectedRestoreArchive(
            contentResolver, uri, privateStore,
            System.currentTimeMillis(), cancellation,
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
        // Never leave an operation writing after an Activity teardown.
        activeCancellation?.cancel()
        worker.shutdownNow()
        super.onDestroy()
    }
}
