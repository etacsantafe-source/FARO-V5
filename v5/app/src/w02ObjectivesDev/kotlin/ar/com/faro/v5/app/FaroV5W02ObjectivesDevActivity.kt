package ar.com.faro.v5.app

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import ar.com.faro.v5.surface.FaroV5ReviewDisclosureMode
import ar.com.faro.v5.surface.FaroV5SurfaceHost
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Chat and Objectives share the Activity's single active DEV session. */
class FaroV5W02ObjectivesDevActivity : FaroV5V2CoreDevActivity() {
    private var objectives: S4W02ObjectiveSessionActions? = null
    private var panel: FaroV5W02ObjectivesPanel? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val viewEpoch = AtomicLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        check(packageName == W02_OBJECTIVES_DEV_ID)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        super.onCreate(savedInstanceState)
    }

    override fun screenTitle() = "FARO Objetivos DEV"
    override fun screenDescription() = "Chat y objetivos locales"

    private fun foreground() =
        !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun onDedicatedOwnerSessionOpened(active: FaroV5V2CoreDevAuthority.Session): Boolean {
        if (!foreground()) return false
        objectives?.close()
        val actions = active.mountW02ObjectiveActions(application, ::foreground) ?: return false
        objectives = actions
        FaroV5SurfaceHost.install(
            activity = this,
            workspaceReadPort = active.workspaceReadPort,
            timelineReadPort = active.timelineReadPort,
            selectionPort = active.selectionPort,
            composerPort = active.composerPort,
            searchReadPort = active.searchReadPort,
            reviewPort = actions,
            applyRequestPort = actions,
            reviewDisclosureMode = FaroV5ReviewDisclosureMode.EXPLICIT_OPEN,
            objectivesViewFactory = { openChat ->
                FaroV5W02ObjectivesPanel(this, actions, openChat).also { panel = it }
            },
        )
        return true
    }

    internal fun <T> work(block: () -> T, finished: (Result<T>) -> Unit) {
        if (!foreground()) return
        val epoch = viewEpoch.get()
        worker.execute {
            val result = runCatching(block)
            runOnUiThread {
                if (epoch == viewEpoch.get() && foreground()) finished(result)
            }
        }
    }

    override fun onPause() {
        viewEpoch.incrementAndGet()
        objectives?.clearReferenceGrants()
        panel?.clearOnPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        panel?.reloadIfAttached()
    }

    override fun onDestroy() {
        objectives?.close()
        objectives = null
        panel = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
