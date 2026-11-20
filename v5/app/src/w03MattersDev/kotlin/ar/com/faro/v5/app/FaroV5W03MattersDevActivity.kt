package ar.com.faro.v5.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import ar.com.faro.v5.surface.FaroV5AvisosReadPort
import ar.com.faro.v5.surface.FaroV5ReviewDisclosureMode
import ar.com.faro.v5.surface.FaroV5SurfaceHost
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** W03 isolated/offline launcher. Only the single existing DEV graph is mounted. */
class FaroV5W03MattersDevActivity : FaroV5V2CoreDevActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val viewEpoch = AtomicLong()
    private var owner: S4W03OwnerSessionActions? = null
    private var panel: FaroV5W03MattersPanel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        check(packageName == W03_MATTERS_DEV_ID)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        super.onCreate(savedInstanceState)
    }

    override fun screenTitle() = "FARO Asuntos DEV"
    override fun screenDescription() = "Chat, Asuntos, Recordatorios y Avisos locales. Sin servicios ni Internet."
    override fun enablesW01Finance9() = true

    private fun foreground() = !isFinishing && !isDestroyed &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun addAdmissionControls(column: LinearLayout) {
        column.addView(Button(this).apply {
            text = "Activar Finance9 (DEV, después de Reality8)"
            setOnClickListener { if (foreground()) openLocalChat(requestFinance9Upgrade = true) }
        })
        column.addView(Button(this).apply {
            text = "Activar Auxiliary4 (DEV, después de Finance9)"
            setOnClickListener { if (foreground()) openLocalChat(requestAuxiliary4Upgrade = true) }
        })
        column.addView(Button(this).apply {
            text = "Abrir Chat y Avisos locales"
            setOnClickListener { if (foreground()) openLocalChat(requestAvisosRead = true) }
        })
    }

    override fun onDedicatedOwnerSessionOpenedWithAvisos(
        active: FaroV5V2CoreDevAuthority.Session,
        avisosRead: FaroV5AvisosReadPort?,
    ): Boolean {
        if (!foreground()) return false
        owner?.close()
        val actions = active.mountW03OwnerActions(application, ::foreground) ?: return false
        owner = actions
        FaroV5SurfaceHost.install(
            activity = this,
            workspaceReadPort = active.workspaceReadPort,
            timelineReadPort = active.timelineReadPort,
            selectionPort = active.selectionPort,
            composerPort = active.composerPort,
            searchReadPort = active.searchReadPort,
            avisosReadPort = avisosRead,
            reviewPort = actions,
            applyRequestPort = actions,
            reviewDisclosureMode = FaroV5ReviewDisclosureMode.EXPLICIT_OPEN,
            mattersViewFactory = { openChat ->
                FaroV5W03MattersPanel(this, actions, openChat).also { panel = it }
            },
        )
        return true
    }

    internal fun <T> work(block: () -> T, onResult: (Result<T>) -> Unit) {
        if (!foreground()) return
        val epoch = viewEpoch.get()
        worker.execute {
            val result = runCatching(block)
            runOnUiThread { if (epoch == viewEpoch.get() && foreground()) onResult(result) }
        }
    }

    override fun onPause() {
        viewEpoch.incrementAndGet()
        owner?.clearViewGrants()
        panel?.clearOnPause()
        super.onPause()
    }

    override fun onDestroy() {
        viewEpoch.incrementAndGet()
        owner?.close()
        owner = null
        panel = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
