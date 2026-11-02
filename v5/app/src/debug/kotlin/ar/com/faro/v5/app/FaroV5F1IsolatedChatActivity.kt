package ar.com.faro.v5.app

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import ar.com.faro.v5.delivery.api.NoticeRouter
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5SurfaceHost
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/** Debug-only five-owner Chat. No V1 pointer, production V2 or synthetic reply. */
class FaroV5F1IsolatedChatActivity : ComponentActivity() {
    private companion object {
        val occupied = AtomicBoolean(false)
        const val GENERATION = "f1-chat-isolated-debug-1"
    }
    private class Session(
        val host: FaroV5QuarantinedV2ChatHostBinding,
        val workspace: ConversationWorkspaceStore,
        val reality: RealityStore,
        val delivery: AuxiliaryDeliveryStore,
        val applyPort: ConversationApplyRequestPort,
    ) : AutoCloseable {
        override fun close() {
            host.close()
            workspace.close()
            reality.close()
            delivery.close()
        }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val initializing = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private var ownsSession = false
    private var session: Session? = null
    private lateinit var status: TextView
    private lateinit var open: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        column.addView(TextView(this).apply {
            text = "FARO S2 · Chat aislado"
            textSize = 23f
        })
        column.addView(TextView(this).apply {
            text = "Datos separados de FARO habitual. V2 OFF. Groq requiere clave y " +
                "consentimiento en el teléfono. Apply solo modifica Reality de " +
                "prueba. Delivery guarda un aviso local, sin notificación Android."
        })
        status = TextView(this).apply { text = "Configurá IA antes de abrir Chat." }
        column.addView(status)
        column.addView(Button(this).apply {
            text = "Configurar o revocar Groq"
            setOnClickListener {
                startActivity(Intent(this@FaroV5F1IsolatedChatActivity,
                    FaroV5AiSettingsActivity::class.java))
            }
        })
        open = Button(this).apply {
            text = "Abrir Chat aislado"
            setOnClickListener { startChat() }
        }
        column.addView(open)
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
    }

    private fun startChat() {
        if (!occupied.compareAndSet(false, true)) {
            status.text = "Otro Chat aislado está abierto. Cerralo y reintentá."
            return
        }
        ownsSession = true
        initializing.set(true)
        open.isEnabled = false
        status.text = "Validando consentimiento y abriendo Workspace…"
        worker.execute {
            val result = runCatching { buildSession() }
            runOnUiThread {
                try {
                val ready = result.getOrNull()
                if (isDestroyed || isFinishing) {
                    ready?.close()
                    release()
                } else if (ready == null) {
                    status.text = "No se abrió Chat: " +
                        (result.exceptionOrNull()?.message ?: "sin autorización") +
                        ". V2 permanece OFF."
                    open.isEnabled = true
                    release()
                } else {
                    session = ready
                    FaroV5SurfaceHost.install(
                        activity = this,
                        workspaceReadPort = ready.host.workspaceReadPort,
                        timelineReadPort = ready.host.timelineReadPort,
                        composerPort = ready.host.composerPort,
                        reviewPort = ready.host.reviewPort,
                        selectionPort = ready.host.selectionPort,
                        applyRequestPort = ready.applyPort,
                        assistantTurnPort = ready.host.assistantTurnPort,
                        onOpenAiSettings = Runnable {
                            startActivity(Intent(this,
                                FaroV5AiSettingsActivity::class.java))
                        },
                    )
                }
                } finally {
                    initializing.set(false)
                    if (disposed.get()) release()
                }
            }
        }
    }

    private fun buildSession(): Session {
        // Consent and Keystore admission BEFORE any five-store access.
        val owner = FaroV5TrustedAssistantControlOwner.get(applicationContext)
        check(FaroV5TrustedModelGatewayFactory(
            readControl = { runBlocking { owner.read() } },
            secretReader = owner.secretReader(),
        ).create() != null) { "falta consentimiento o clave Groq local" }
        val root = noBackupFilesDir.toPath().resolve("faro-s2-f1-debug-only")
        Files.createDirectories(root)
        val layout = FileStorageGenerationLayout(root)
        val id = StorageGenerationId(GENERATION)
        val staged = try {
            layout.requireStaged(id)
        } catch (_: IllegalStateException) {
            // A partial V2 directory cannot be silently reset or adopted.
            layout.stageWorkspaceV2(StorageGenerationDescriptor(
                id, "schema-five-store-1", 4L,
            ))
        }
        val dir = staged.directory
        if (!Files.exists(dir.resolve(GenerationStoreFiles.WORKSPACE))) {
            check(GenerationStoreFiles.WORKSPACE_V2.none {
                Files.exists(dir.resolve(it))
            }) { "partial five-owner initialization: recovery required" }
            RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
            CommercialEvidenceStore(
                dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()
            ).use { }
            WebEvidenceStore(
                dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()
            ).use { }
            AuxiliaryDeliveryStore(
                dir.resolve(GenerationStoreFiles.AUXILIARY).toString()
            ).use { }
            ConversationWorkspaceStore(
                dir.resolve(GenerationStoreFiles.WORKSPACE).toString()
            ).use { }
        }
        GenerationStoreFiles.requireMaterialized(dir, GenerationStoreFormat.WORKSPACE_V2)
        val gate = SingleProcessMaterialWriteGate()
        val sentinel = Files.createDirectories(root.resolve("v1-protected-sentinel"))
        val host = checkNotNull(FaroV5F1ConsentedQuarantineHost.open(
            applicationContext, staged, gate, Supplier { sentinel },
            exclusiveRestartRecovery = BooleanSupplier { occupied.get() },
        )) { "consentimiento revocado antes de abrir Chat" }
        var workspace: ConversationWorkspaceStore? = null
        var reality: RealityStore? = null
        var delivery: AuxiliaryDeliveryStore? = null
        try {
            check(host.verifyQuarantined())
            val snapshot = runBlocking { host.workspaceReadPort.readSnapshot() }
            if (snapshot.conversations.isEmpty()) {
                check(runBlocking {
                    host.selectionPort.createAndSelect(
                        ConversationId("f1-debug-conversation"),
                        System.currentTimeMillis(),
                        IdempotencyKey("f1-debug-first-conversation"),
                    )
                } is ConversationSelectionResult.Saved)
            } else if (snapshot.activeConversationId == null) {
                check(runBlocking {
                    host.selectionPort.select(
                        snapshot.conversations.first().id,
                        IdempotencyKey("f1-debug-resume-selection"),
                    )
                } is ConversationSelectionResult.Saved)
            }
            val applyWorkspace = ConversationWorkspaceStore.openExistingWithPlanVerifier(
                dir.resolve(GenerationStoreFiles.WORKSPACE).toString(),
                CanonicalPreparedPlanEnvelopeVerifier,
                materialWriteGate = gate,
            )
            workspace = applyWorkspace
            val isolatedReality = RealityStore(
                dir.resolve(GenerationStoreFiles.REALITY).toString(),
                materialWriteGate = gate,
            )
            reality = isolatedReality
            val isolatedDelivery = AuxiliaryDeliveryStore(
                dir.resolve(GenerationStoreFiles.AUXILIARY).toString(),
                materialWriteGate = gate,
            )
            delivery = isolatedDelivery
            val apply = FaroV5ActiveReviewedApplySession(
                host.generationId, applyWorkspace, isolatedReality, gate,
                Supplier { host.generationId },
                BooleanSupplier { host.verifyQuarantined() },
            )
            val router = NoticeRouter(isolatedDelivery)
            val admitted = BooleanSupplier { host.verifyQuarantined() }
            val reconcile = FaroV5QuarantinedCommittedDeliveryReconciler(
                host.reviewPort, router, gate, admitted,
            )
            runBlocking {
                host.workspaceReadPort.readSnapshot().conversations.forEach {
                    reconcile.reproject(it.id)
                }
            }
            val applyPort = FaroV5QuarantinedReviewedApplyDeliveryPort(
                upstream = object : ConversationApplyRequestPort {
                    override suspend fun request(
                        context: ConversationPlanReviewContext,
                    ): ConversationApplyHostOutcome =
                        apply.executeAfterHumanRequest(context)
                },
                router = router,
                gate = gate,
                admitted = admitted,
                onProjectionFailure = { failure ->
                    android.util.Log.e("FaroF1Debug", "Notice needs reconciliation", failure)
                },
                dispatcher = null,
            )
            return Session(host, applyWorkspace, isolatedReality, isolatedDelivery, applyPort)
        } catch (failure: Throwable) {
            runCatching { delivery?.close() }
            runCatching { reality?.close() }
            runCatching { workspace?.close() }
            host.close()
            throw failure
        }
    }

    private fun release() {
        if (ownsSession) {
            ownsSession = false
            occupied.set(false)
        }
    }
    override fun onDestroy() {
        disposed.set(true)
        worker.shutdown()
        runCatching { session?.close() }
        session = null
        if (!initializing.get()) release()
        super.onDestroy()
    }
}
