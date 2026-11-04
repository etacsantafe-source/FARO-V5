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
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.surface.FaroV5SurfaceHost
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
    private var session: FaroV5V2CoreDevAuthority.Session? = null
    private lateinit var status: TextView
    private lateinit var openButton: Button

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
            text = "Solo Workspace y Chat local. Sin Groq/IA, Buscar, Apply, notificaciones ni conexión a Internet. Los datos de FARO habitual no se comparten."
        })
        status = TextView(this).apply { text = "Sin abrir. V2 habitual permanece apagada." }
        column.addView(status)
        openButton = Button(this).apply {
            text = "Abrir Chat local de desarrollo"
            setOnClickListener { openLocalChat() }
        }
        column.addView(openButton)
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        })
    }

    private fun openLocalChat() {
        if (!opening.compareAndSet(false, true)) return
        openButton.isEnabled = false
        status.text = "Verificando journal y cinco bases locales…"
        executor.execute {
            var opened: FaroV5V2CoreDevAuthority.Session? = null
            val failure = runCatching {
                val root = noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
                val current = FaroV5V2CoreDevAuthority(root).open()
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
                        opened?.close()
                    } else if (failure != null) {
                        opened?.close()
                        status.text = "No se abrió: ${failure.javaClass.simpleName}. Los datos permanecen intactos."
                        openButton.isEnabled = true
                    } else {
                        val active = checkNotNull(opened)
                        session = active
                        FaroV5SurfaceHost.install(
                            activity = this,
                            workspaceReadPort = active.workspaceReadPort,
                            timelineReadPort = active.timelineReadPort,
                            selectionPort = active.selectionPort,
                            composerPort = active.composerPort,
                            // No assistant, no review, no Apply, no Search or external effects.
                        )
                    }
                } finally {
                    opening.set(false)
                }
            }
        }
    }

    override fun onDestroy() {
        disposed.set(true)
        executor.shutdown()
        session?.close()
        session = null
        super.onDestroy()
    }
}
