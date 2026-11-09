package ar.com.faro.v5.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.surface.FaroV5SurfaceHost
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/** DEV-only app: no FARO shipping CompositionRoot, WorkManager, or model bootstrap. */
class FaroV5V2CoreDevApplication : Application()

/**
 * The only launcher for the isolated V2 core package.
 * Explicit user action opens a durable local Workspace; no network, model,
 * reviewed Apply, Search ACL bypass, delivery, OS dispatch or credentials.
 */
class FaroV5V2CoreDevActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val opening = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val pendingActivityReadRequest = AtomicBoolean(false)
    private val pendingDueReadRequest = AtomicBoolean(false)
    private var session: FaroV5V2CoreDevAuthority.Session? = null
    private lateinit var status: TextView
    private lateinit var openButton: Button
    private lateinit var activityButton: Button
    private lateinit var dueButton: Button
    private lateinit var upgradeButton: Button
    private var dueDisplay: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "ar.com.faro.alpha.v2coredev") {
            "V2 core development requires a separate Android application ID"
        }
        check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "V2 core development is debug-only"
        }
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED) {
            "V2 core development must not bypass the production V2 gate"
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        column.addView(TextView(this).apply {
            text = "FARO V2 — Núcleo local aislado"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "Workspace, Chat y Buscar local. Sin Groq/IA, Apply, notificaciones ni conexión a Internet. Los datos de FARO habitual no se comparten."
        })
        status = TextView(this).apply { text = "Sin abrir. V2 habitual permanece apagada." }
        column.addView(status)
        openButton = Button(this).apply {
            text = "Abrir Chat local de desarrollo"
            setOnClickListener { openLocalChat() }
        }
        column.addView(openButton)
        activityButton = Button(this).apply {
            text = "Abrir Chat y Actividad local"
            setOnClickListener { openLocalChat(requestActivityRead = true) }
        }
        column.addView(activityButton)
        dueButton = Button(this).apply {
            text = "Consultar vencimientos registrados (DEV)"
            setOnClickListener { openLocalChat(requestPendingRead = true) }
        }
        column.addView(dueButton)
        column.addView(TextView(this).apply {
            text = "Reality8 (solo DEV): crea una generación nueva y vacía. " +
                "NO copia datos de Reality7; la generación anterior queda intacta. " +
                "Primero debe estar inicializado el Chat local."
        })
        upgradeButton = Button(this).apply {
            text = "Crear Reality8 vacía (DEV)"
            setOnClickListener {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    openLocalChat(requestPlanning8Upgrade = true)
            }
        }
        column.addView(upgradeButton)
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
    }

    private fun openLocalChat(
        requestActivityRead: Boolean = false,
        requestPendingRead: Boolean = false,
        requestPlanning8Upgrade: Boolean = false,
    ) {
        if (!opening.compareAndSet(false, true)) return
        // Only this dedicated foreground button can request an Activity read.
        pendingActivityReadRequest.set(requestActivityRead)
        pendingDueReadRequest.set(requestPendingRead)
        openButton.isEnabled = false
        activityButton.isEnabled = false
        dueButton.isEnabled = false
        upgradeButton.isEnabled = false
        // Revoke all old per-session read grants and close five SQLite stores
        // before the explicit Reality8 cutover can acquire the same global gate.
        session?.close()
        session = null
        status.text = if (requestPlanning8Upgrade)
            "Verificando origen, journal y Reality8 vacía (DEV)…"
        else "Verificando journal y cinco bases locales…"
        executor.execute {
            var opened: FaroV5V2CoreDevAuthority.Session? = null
            val failure = runCatching {
                val root = noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
                val authority = FaroV5V2CoreDevAuthority(root)
                val current = if (requestPlanning8Upgrade) {
                    authority.upgradePlanning8AfterExplicitForegroundRequest {
                        !disposed.get() && !isFinishing && !isDestroyed &&
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    }
                } else authority.open()
                opened = current
                val snapshot = runBlocking { current.workspaceReadPort.readSnapshot() }
                if (snapshot.conversations.isEmpty()) {
                    check(runBlocking {
                        current.selectionPort.createAndSelect(
                            ConversationId("v2-core-dev-local-chat"),
                            System.currentTimeMillis(),
                            IdempotencyKey("v2-core-dev-create-local-chat"),
                        )
                    } is ConversationSelectionResult.Saved)
                } else if (snapshot.activeConversationId == null) {
                    check(runBlocking {
                        current.selectionPort.select(
                            snapshot.conversations.first().id,
                            IdempotencyKey("v2-core-dev-resume-local-chat"),
                        )
                    } is ConversationSelectionResult.Saved)
                }
            }.exceptionOrNull()
            runOnUiThread {
                try {
                    if (disposed.get() || isFinishing || isDestroyed) {
                        pendingActivityReadRequest.set(false)
                        pendingDueReadRequest.set(false)
                        opened?.close()
                    } else if (failure != null) {
                        opened?.close()
                        status.text = "No se abrió: ${failure.javaClass.simpleName}. Los datos permanecen intactos."
                        pendingActivityReadRequest.set(false)
                        pendingDueReadRequest.set(false)
                        openButton.isEnabled = true
                        activityButton.isEnabled = true
                        dueButton.isEnabled = true
                        upgradeButton.isEnabled = true
                    } else {
                        val active = checkNotNull(opened)
                        session = active
                        // A background transition cancels the original click: no delayed grant.
                        val userRequestedActivity = pendingActivityReadRequest.getAndSet(false)
                        val activityRead = if (userRequestedActivity &&
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        ) active.openActivityAfterTrustedForegroundRequest(
                            application = application,
                            foreground = {
                                !disposed.get() && !isFinishing && !isDestroyed &&
                                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                            },
                        ) else null
                        val userRequestedPending = pendingDueReadRequest.getAndSet(false)
                        if (userRequestedPending) {
                            openPendingView(active)
                        } else FaroV5SurfaceHost.install(
                            activity = this,
                            workspaceReadPort = active.workspaceReadPort,
                            timelineReadPort = active.timelineReadPort,
                            selectionPort = active.selectionPort,
                            composerPort = active.composerPort,
                            searchReadPort = active.searchReadPort,
                            activityReadPort = activityRead,
                            // DEV-only canonical Workspace Search; no assistant, Apply or external effects.
                        )
                    }
                } finally {
                    opening.set(false)
                }
            }
        }
    }

    /** Dedicated foreground read, never triggered by Chat or Activity. */
    private fun openPendingView(active: FaroV5V2CoreDevAuthority.Session) {
        val foreground = {
            !disposed.get() && !isFinishing && !isDestroyed &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val binding = if (foreground()) active.openPendingAfterTrustedForegroundRequest(
            application = application, foreground = foreground,
        ) else null

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        column.addView(TextView(this).apply {
            text = "Vencimientos registrados — DEV local"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "Solo Asuntos, Recordatorios y Plan mensual persistidos en Reality8. " +
                "No es el total global de Pendientes. Sin prioridad, snooze ni recurrencias inferidas."
        })
        val output = TextView(this).apply {
            text = if (binding == null) FaroV5V2CoreDevPendingPresentation.unavailable
                else "Consultando fuentes locales autorizadas…"
            textSize = 16f
        }
        dueDisplay = output
        column.addView(output)
        column.addView(Button(this).apply {
            text = "Volver al inicio DEV"
            setOnClickListener {
                dueDisplay?.text = FaroV5V2CoreDevPendingPresentation.unavailable
                active.revokePendingReadingOnPause()
                recreate()
            }
        })
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
        if (binding == null) return
        // SQL runs only on the worker. Any pause or ABA invalidates this result.
        executor.execute {
            val page = runCatching {
                binding.readAuthorized(
                    now = Instant.now(),
                    zone = ZoneId.of("America/Argentina/Cordoba"),
                    limit = 50,
                )
            }.getOrNull()
            runOnUiThread {
                if (session === active && foreground() &&
                    active.isPendingReadCurrent(binding, foreground)
                ) {
                    dueDisplay?.text = FaroV5V2CoreDevPendingPresentation.render(page)
                } else {
                    dueDisplay?.text = FaroV5V2CoreDevPendingPresentation.unavailable
                }
            }
        }
    }

    override fun onPause() {
        // Revoke regardless of whether Compose currently shows the Activity page.
        pendingActivityReadRequest.set(false)
        pendingDueReadRequest.set(false)
        dueDisplay?.text = FaroV5V2CoreDevPendingPresentation.unavailable
        session?.revokePendingReadingOnPause()
        session?.revokeActivityReadingOnPause()
        // Search's four owner-specific grants must not outlive RESUMED.
        // The existing StateFlow invalidations redact any visible result.
        session?.revokeSearchReadingOnPause()
        super.onPause()
    }

    override fun onDestroy() {
        disposed.set(true)
        pendingActivityReadRequest.set(false)
        pendingDueReadRequest.set(false)
        dueDisplay = null
        executor.shutdown()
        session?.close()
        session = null
        super.onDestroy()
    }
}
