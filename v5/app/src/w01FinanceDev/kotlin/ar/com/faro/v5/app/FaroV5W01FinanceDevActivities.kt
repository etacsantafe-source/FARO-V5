package ar.com.faro.v5.app

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import ar.com.faro.v5.surface.FaroV5FinanceReadPort
import ar.com.faro.v5.surface.FaroV5FinanceCoverage
import ar.com.faro.v5.surface.FaroV5FinanceSurfacePolicy
import ar.com.faro.v5.surface.FaroV5FinanceUiSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    override fun enablesW01Finance9(): Boolean = true
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
                    openLocalChat(onAccepted = { financeRequested = true })
                }
            }
        })
        column.addView(Button(this).apply {
            text = "Activar Finance9 (DEV)"
            setOnClickListener {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    openLocalChat(requestFinance9Upgrade = true, onAccepted = { financeRequested = true })
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
 * The launcher Session owns the active graph. The read mapper preserves
 * snapshot certainty across Planning8 and Finance9.
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
    private val executor = Executors.newSingleThreadExecutor()
    private val viewGeneration = AtomicLong(0L)
    private var display: TextView? = null
    private var financeControls: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID)
        check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        column.addView(TextView(this).apply {
            text = "Finanzas — fuente activa local. " +
                "Cuentas informativas y saldos de apertura aún sin verificar. " +
                "Seguridad opcional desactivada; sin huella ni PIN."
            textSize = 18f
        })
        column.addView(Button(this).apply {
            text = "Volver al Chat DEV"
            setOnClickListener { finish() }
        })
        display = TextView(this).apply {
            text = "Finanzas no disponible. Ningún saldo supuesto."
            textSize = 16f
        }
        column.addView(checkNotNull(display))
        financeControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(checkNotNull(financeControls))
        setContentView(ScrollView(this).apply {
            FaroV5NativeScreenInsets.protect(this)
            addView(column)
        })

        // Keep D1B0's Finance9 denial in place. The separate ACTIVE
        // informational read does not issue a Finance9 read policy.
        val owner = S4W01FinanceReadForegroundReconciliation(
            newObservedFence = {
                throw SecurityException("El lector independiente requiere autorización propia")
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

    private fun foreground(): Boolean =
        !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    /** UI uses the already certified read presentation policy, not Compose classes. */
    private fun renderNativeFinance(snapshot: FaroV5FinanceUiSnapshot?): String {
        val overview = FaroV5FinanceSurfacePolicy.overview(snapshot)
        val items = snapshot?.takeIf {
            it.coverage == FaroV5FinanceCoverage.PARTIAL ||
                it.coverage == FaroV5FinanceCoverage.READY
        }?.accounts.orEmpty()
        val accountLines = items.joinToString("\n") { account ->
            account.label + " (" + account.currencyCode + "): " +
                FaroV5FinanceSurfacePolicy.balanceText(account)
        }
        return overview +
            (if (accountLines.isBlank()) "" else "\n\n" + accountLines) +
            "\n\n" + FaroV5FinanceSurfacePolicy.projectionMessage()
    }

    override fun onResume() {
        super.onResume()
        val id = viewGeneration.incrementAndGet()
        // RESUMED is dispatched by Android after onResume returns on some
        // API levels. Posting the one-shot handoff prevents false denial.
        display?.post {
            if (viewGeneration.get() != id || !foreground()) return@post
            val session = S4W01D1B1BFinanceSessionHandoff.consume(this)
            financePort.enter(session, application, ::foreground)
            val controls = financeControls
            val actions = session?.mountW01FinanceActions(application, ::foreground)
            if (actions != null && controls != null) {
                display?.text = "Finanzas Finance9"
                FaroV5W01FinanceForms(
                    controls, actions,
                    execute = { work -> executor.execute { work() } },
                    deliver = { apply ->
                        runOnUiThread { if (viewGeneration.get() == id && foreground()) apply() }
                    },
                ).show()
                return@post
            }
            display?.text = "Consultando Finance ACTIVE local…"
            executor.execute {
                val observed = runCatching { runBlocking { financePort.readFinance() } }.getOrNull()
                runOnUiThread {
                    if (viewGeneration.get() == id && foreground()) {
                        display?.text = renderNativeFinance(observed)
                    }
                }
            }
        }
    }

    override fun onPause() {
        viewGeneration.incrementAndGet()
        financePort.leave() // permanently revoke old loan + clear screen first
        financeControls?.removeAllViews()
        display?.text = "Finanzas no disponible. Volvé a entrar desde el Chat."
        super.onPause()
    }

    internal fun readIsDeniedForAndroidTest(): Boolean =
        reconciliation?.observedAfterResumeForDevOnly() == null
    internal fun clearsForAndroidTest(): Int = clears.get()
    internal fun closedForAndroidTest(): Boolean =
        reconciliation?.isClosed() ?: true

    override fun onDestroy() {
        viewGeneration.incrementAndGet()
        display = null
        financeControls = null
        executor.shutdownNow()
        financePort.close()
        privateInvalidation?.close()
        lifecycleBinding?.close()
        reconciliation?.close()
        super.onDestroy()
    }
}
