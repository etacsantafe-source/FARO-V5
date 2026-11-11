package ar.com.faro.v5.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5AssistantRequestMode
import ar.com.faro.v5.surface.FaroV5SurfaceHost
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/** Separate debug stage; consented real gateway, no Review/Apply authority in UI. */
class FaroV5X03U02IsolatedAiActivity : ComponentActivity() {
    private companion object {
        val occupied = AtomicBoolean(false)
        const val ROOT = "faro-s3-x03-u02-isolated-debug-only"
        const val GENERATION = "x03-u02-isolated-debug-1"
    }
    private val worker = Executors.newSingleThreadExecutor()
    private var foreground = false // UI thread only
    private var opening = false
    private var lifetime: AtomicBoolean? = null
    private var host: FaroV5QuarantinedV2ChatHostBinding? = null
    private lateinit var free: CheckBox
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        showAdmission()
    }

    private fun showAdmission() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        column.addView(TextView(this).apply {
            text = "FARO X03 · IA aislada"; textSize = 23f
        })
        column.addView(TextView(this).apply {
            text = "Chat de prueba separado. Enviar guarda tu mensaje; Consultar FARO solicita IA. " +
                "Sin revisión ni aplicación de cambios. La declaración de plan Free es tuya: " +
                "FARO no verifica la facturación de Groq."
        })
        free = CheckBox(this).apply {
            text = "Verifiqué que mi organización Groq está en el plan Free y no autoricé un plan pago"
            isChecked = false
            isSaveEnabled = false // Never restore a session grant from view state/Bundle.
        }
        column.addView(free)
        column.addView(Button(this).apply {
            text = "Configurar o revocar Groq"
            setOnClickListener {
                startActivity(Intent(this@FaroV5X03U02IsolatedAiActivity,
                    FaroV5AiSettingsActivity::class.java))
            }
        })
        status = TextView(this).apply { text = "Confirmá plan Free y consentimiento antes de abrir." }
        column.addView(status)
        column.addView(Button(this).apply {
            text = "Abrir Chat IA U02"
            setOnClickListener { startChat() }
        })
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun startChat() {
        if (!foreground || opening || host != null) return
        if (!free.isChecked) {
            status.text = "No se abrió Chat: falta confirmar plan Free en esta sesión."
            return
        }
        if (!occupied.compareAndSet(false, true)) {
            status.text = "Esperá el cierre del Chat aislado anterior."
            return
        }
        opening = true
        val live = AtomicBoolean(true)
        lifetime = live
        status.text = "Validando consentimiento y Keystore…"
        worker.execute {
            val result = runCatching { buildHost(live) }
            runOnUiThread {
                opening = false
                val ready = result.getOrNull()
                if (!live.get() || !foreground || isDestroyed || isFinishing) {
                    // The queued pause cleanup owns the lease until resources close.
                    worker.execute { try { ready?.close() } finally { occupied.set(false) } }
                    if (isDestroyed) worker.shutdown()
                } else if (ready == null) {
                    live.set(false)
                    occupied.set(false)
                    status.text = "No se abrió Chat: revisá consentimiento, clave o integridad del almacén."
                } else {
                    host = ready
                    val dispatch = ConversationAssistantTurnDispatchPort { turn, finished ->
                        live.get() && ready.assistantTurnPort.dispatchAcceptedTurn(turn) {
                            // A late completion cannot revive a paused/replaced UI session.
                            if (live.get()) finished()
                        }
                    }
                    FaroV5SurfaceHost.install(
                        activity = this,
                        workspaceReadPort = ready.workspaceReadPort,
                        timelineReadPort = ready.timelineReadPort,
                        selectionPort = ready.selectionPort,
                        composerPort = ready.composerPort,
                        assistantTurnPort = dispatch,
                        assistantRequestMode = FaroV5AssistantRequestMode.EXPLICIT_CONSULT,
                        reviewPort = null,
                        applyRequestPort = null,
                        onOpenAiSettings = Runnable {
                            startActivity(Intent(this, FaroV5AiSettingsActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    private fun buildHost(live: AtomicBoolean): FaroV5QuarantinedV2ChatHostBinding {
        fun admitted() { check(live.get()) { "session paused" } }
        admitted()
        val owner = FaroV5TrustedAssistantControlOwner.get(applicationContext)
        val control = runBlocking { owner.read() }
        check(control.remoteReasoningEnabled &&
            control.activeProfile == FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE)
        val credential = checkNotNull(control.credentialRefFor(FaroV5TrustedModelGatewayFactory.BINDING_ID))
        checkNotNull(owner.secretReader().readValueOrNull(credential)) { "Keystore credential missing" }
        checkNotNull(FaroV5TrustedModelGatewayFactory(
            { runBlocking { owner.read() } }, owner.secretReader(),
        ).create())
        admitted()
        val root = noBackupFilesDir.toPath().resolve(ROOT)
        Files.createDirectories(root)
        val layout = FileStorageGenerationLayout(root)
        val id = StorageGenerationId(GENERATION)
        val staged = try { layout.requireStaged(id) } catch (_: IllegalStateException) {
            // Existing partial directories are rejected by stageWorkspaceV2, never reset.
            layout.stageWorkspaceV2(StorageGenerationDescriptor(id, "schema-five-store-1", 4L))
        }
        val dir = staged.directory
        if (!Files.exists(dir.resolve(GenerationStoreFiles.WORKSPACE))) {
            check(GenerationStoreFiles.WORKSPACE_V2.none { Files.exists(dir.resolve(it)) })
            admitted()
            RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
            CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
            WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
            AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
            ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        }
        GenerationStoreFiles.requireMaterialized(dir, GenerationStoreFormat.WORKSPACE_V2)
        admitted()
        val sentinel = Files.createDirectories(root.resolve("v1-protected-sentinel"))
        val ready = checkNotNull(FaroV5F1ConsentedQuarantineHost.open(
            applicationContext, staged, SingleProcessMaterialWriteGate(),
            // All host ports and reasoning publication recheck this supplier. Pause
            // invalidates synchronously, before any asynchronous resource cleanup.
            Supplier { admitted(); sentinel },
            exclusiveRestartRecovery = BooleanSupplier { live.get() && occupied.get() },
        ))
        try {
            admitted()
            check(ready.verifyQuarantined())
            val snapshot = runBlocking { ready.workspaceReadPort.readSnapshot() }
            if (snapshot.conversations.isEmpty()) {
                check(runBlocking {
                    ready.selectionPort.createAndSelect(ConversationId("x03-u02-chat"),
                        System.currentTimeMillis(), IdempotencyKey("x03-u02-first-chat"))
                } is ConversationSelectionResult.Saved)
            } else if (snapshot.activeConversationId == null) {
                check(runBlocking {
                    ready.selectionPort.select(snapshot.conversations.first().id,
                        IdempotencyKey("x03-u02-resume-chat"))
                } is ConversationSelectionResult.Saved)
            }
            admitted()
            return ready
        } catch (failure: Throwable) {
            ready.close()
            throw failure
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        // No automatic host reopen, dispatch replay or restored Free checkbox.
        if (lifetime?.get() != true) showAdmission()
    }

    override fun onPause() {
        foreground = false
        lifetime?.set(false)
        free.isChecked = false
        val closing = host
        host = null
        if (closing != null) worker.execute {
            try { closing.close() } finally { occupied.set(false) }
        }
        super.onPause()
    }

    override fun onDestroy() {
        lifetime?.set(false)
        // onPause already queued cleanup. Late initialization is handed back to
        // the UI before the executor is shut down, so cleanup can still enqueue.
        if (!opening) worker.shutdown()
        super.onDestroy()
    }
}
