package ar.com.faro.v5.app

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import ar.com.faro.v5.surface.FaroV5FinanceReadPort
import ar.com.faro.v5.surface.FaroV5FinanceReadScreen
import ar.com.faro.v5.surface.FaroV5FinanceUiSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-shot in-process handoff. The Session never travels in an Intent and
 * never opens another graph; process death erases the ticket. No regrant on
 * Activity recreation, Home or background/resume.
 */
internal object S4W01D1B1BFinanceSessionHandoff {
    private data class Ticket(
        val source: WeakReference<FaroV5W01FinanceDevLauncherActivity>,
        val session: FaroV5V2CoreDevAuthority.Session,
        val application: Application,
        val issuedAt: Long,
    )
    private var ticket: Ticket? = null

    @Synchronized fun stage(
        source: FaroV5W01FinanceDevLauncherActivity,
        session: FaroV5V2CoreDevAuthority.Session,
    ): Boolean {
        ticket = null
        if (source.packageName != S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID ||
            !source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        ticket = Ticket(WeakReference(source), session, source.application, SystemClock.elapsedRealtime())
        return true
    }

    @Synchronized fun consume(target: FaroV5W01FinanceReadDevActivity): FaroV5V2CoreDevAuthority.Session? {
        val existing = ticket ?: return null
        ticket = null // irrevocable, including failed validation
        val elapsed = SystemClock.elapsedRealtime() - existing.issuedAt
        val launcher = existing.source.get() ?: return null
        return existing.session.takeIf {
            elapsed in 0..10_000L &&
                target.application === existing.application &&
                target.packageName == S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID &&
                !launcher.isDestroyed && !launcher.isFinishing
        }
    }

    @Synchronized fun cancel(source: FaroV5W01FinanceDevLauncherActivity) {
        if (ticket?.source?.get() === source) ticket = null
    }
}

/** Only the W01 app uses the dedicated Finance button; Chat remains available. */
class FaroV5W01FinanceDevLauncherActivity : FaroV5V2CoreDevActivity() {
    private var financeRequested = false

    override fun screenTitle(): String = "FARO W01 Finanzas — DEV aislado"
    override fun skipChatSelectionForDedicatedRoute(): Boolean = financeRequested
    override fun onDedicatedOwnerOpenFailed() { financeRequested = false }

    override fun onDedicatedOwnerSessionOpened(active: FaroV5V2CoreDevAuthority.Session): Boolean {
        if (!financeRequested) return false
        financeRequested = false
        if (!S4W01D1B1BFinanceSessionHandoff.stage(this, active)) return false
        startActivity(Intent(this, FaroV5W01FinanceReadDevActivity::class.java))
        return true
    }

    override fun addAdmissionControls(column: LinearLayout) {
        column.addView(Button(this).apply {
            text = "Finanzas (DEV · sin huella ni PIN)"
            setOnClickListener {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    financeRequested = true
                    // The certified launcher opens the ONE real ACTIVE owner.
                    openLocalChat()
                }
            }
        })
    }

    override fun onDestroy() {
        S4W01D1B1BFinanceSessionHandoff.cancel(this)
        super.onDestroy() // destroys the source Session, invalidating every loan
    }
}

/**
 * Finance8/Planning8 ACTIVE read mapped to the existing W01 Finance UI.
 * The same Session provides the only SQL owner. No balances are promoted to
 * Finance9 VERIFIED until D1B1C migration/cutover is separately certified.
 */
internal class S4W01D1B1BFinanceReadPort : FaroV5FinanceReadPort, AutoCloseable {
    private val changes = MutableStateFlow(0L)
    override val invalidations: Flow<Unit> = changes.map { Unit }
    @Volatile private var loan: S4W01D1B1BActiveFinanceBorrow? = null
    @Volatile private var isClosed = false

    @Synchronized fun enter(
        session: FaroV5V2CoreDevAuthority.Session?,
        application: Application,
        foreground: () -> Boolean,
    ) {
        loan?.close()
        loan = null
        if (!isClosed && session != null && foreground()) {
            loan = session.borrowW01ActiveFinanceRead(application, foreground)
        }
        changes.value += 1L
    }

    @Synchronized fun leave() {
        loan?.close()
        loan = null
        changes.value += 1L
    }

    override suspend fun readFinance(): FaroV5FinanceUiSnapshot {
        val current = loan ?: return S4W01D1B1BActiveFinanceUiMapper.mapObserved(null)
        val result = S4W01D1B1BActiveFinanceUiMapper.mapObserved(current.observedForDevOnly())
        return if (!isClosed && loan === current) result
            else S4W01D1B1BActiveFinanceUiMapper.mapObserved(null)
    }

    override fun close() {
        isClosed = true
        leave()
    }
}

/** Non-exported, isolated real Android screen. D1B0 tests remain observable. */
class FaroV5W01FinanceReadDevActivity : FragmentActivity() {
    private var reconciliation: S4W01FinanceReadForegroundReconciliation? = null
    private var lifecycleBinding: S4W01FinanceReadForegroundLifecycleAdapter? = null
    private var privateInvalidation: S4W01FinanceReadPrivateInvalidation? = null
    private val clears = AtomicInteger(0)
    private val financePort = S4W01D1B1BFinanceReadPort()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID)
        check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FaroV5NativeScreenInsets.protect(this)
        }
        column.addView(TextView(this).apply {
            text = "Fuente: Finance ACTIVE local (no Finance9). " +
                "Cuentas informativas; saldos de apertura pendientes de validación. " +
                "Sin huella ni PIN."
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        })
        column.addView(Button(this).apply {
            text = "Volver al inicio DEV"
            setOnClickListener { finish() }
        })
        column.addView(ComposeView(this).apply {
            setContent { MaterialTheme { FaroV5FinanceReadScreen(financePort) } }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        setContentView(column)

        // Preserve the existing D1B0 Finance9 fail-closed reconciliation.
        // D1B1B borrows a different ACTIVE Finance read-only owner; never
        // fabricates a Finance9 grant to bypass these existing checks.
        val owner = S4W01FinanceReadForegroundReconciliation(
            newObservedFence = {
                throw SecurityException("Finance9 is STAGED; no ACTIVE Finance9 read")
            },
            clearBeforeRead = { clears.incrementAndGet() },
        )
        reconciliation = owner
        try {
            lifecycleBinding = S4W01FinanceReadForegroundLifecycleAdapter(lifecycle, owner)
            privateInvalidation = S4W01FinanceReadPrivateInvalidation(this, owner)
        } catch (_: Exception) {
            privateInvalidation?.close()
            lifecycleBinding?.close()
            owner.close()
            financePort.leave()
        }
    }

    override fun onResume() {
        super.onResume()
        val session = S4W01D1B1BFinanceSessionHandoff.consume(this)
        financePort.enter(session, application) {
            !isFinishing && !isDestroyed &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
    }

    override fun onPause() {
        financePort.leave() // invalidate visible data before window loses foreground
        super.onPause()
    }

    internal fun readIsDeniedForAndroidTest(): Boolean =
        reconciliation?.observedAfterResumeForDevOnly() == null
    internal fun clearsForAndroidTest(): Int = clears.get()
    internal fun closedForAndroidTest(): Boolean =
        reconciliation?.isClosed() ?: true

    override fun onDestroy() {
        financePort.close()
        privateInvalidation?.close()
        lifecycleBinding?.close()
        reconciliation?.close()
        super.onDestroy()
    }
}
