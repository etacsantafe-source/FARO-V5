package ar.com.faro.v5.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import java.util.concurrent.atomic.AtomicInteger

/**
 * W01 DEV-only subclass. Chat continues to use its existing V2 Core DEV owner;
 * no shipping or S3 Activity is replaced and no Finance data is loaded here.
 */
class FaroV5W01FinanceDevLauncherActivity : FaroV5V2CoreDevActivity() {
    override fun screenTitle(): String = "FARO W01 Finanzas — DEV aislado"

    override fun addAdmissionControls(column: LinearLayout) {
        column.addView(Button(this).apply {
            text = "Finanzas (DEV · lectura bloqueada)"
            setOnClickListener {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    startActivity(Intent(
                        this@FaroV5W01FinanceDevLauncherActivity,
                        FaroV5W01FinanceReadDevActivity::class.java,
                    ))
                }
            }
        })
    }
}

/**
 * Real Android FragmentActivity owned by W01 only.
 * Mounts the already B7-scoped foreground lifecycle + private invalidation
 * on the main thread, and closes both on destroy. There is NO authority to
 * read SQL: no human biometric, read-policy issuer, principal or epoch is
 * available in this D1B0 unit. A missed notice never enables a read.
 *
 * D1B1 must wire real SQL and trusted consent BEFORE any Finance rendering.
 */
class FaroV5W01FinanceReadDevActivity : FragmentActivity() {
    private var reconciliation: S4W01FinanceReadForegroundReconciliation? = null
    private var lifecycleBinding: S4W01FinanceReadForegroundLifecycleAdapter? = null
    private var privateInvalidation: S4W01FinanceReadPrivateInvalidation? = null
    private val clears = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "ar.com.faro.alpha.w01financedev") {
            "W01 Finance reader must have its own isolated Android UID"
        }
        check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "W01 Finance reader is debug-only"
        }
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED) {
            "Productive V2 is OFF"
        }

        val status = TextView(this).apply {
            text = "Finanzas DEV — acceso protegido. " +
                "Todavía no hay autorización biométrica ni lectura de saldos. " +
                "Volvé al Chat para seguir trabajando."
            textSize = 18f
            val spacing = (20 * resources.displayMetrics.density).toInt()
            setPadding(spacing, spacing, spacing, spacing)
        }
        setContentView(status)

        // Fail closed by construction. No fake Finance9ReadPolicy or read grant.
        val owner = S4W01FinanceReadForegroundReconciliation(
            newObservedFence = {
                throw SecurityException("D1B0 has no trusted Finance9 SQL read authority")
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
            status.text = "Finanzas DEV — lector no disponible; acceso denegado."
        }
    }

    // Observable ONLY inside isolated W01 Android instrumentation; never a UI read.
    internal fun readIsDeniedForAndroidTest(): Boolean =
        reconciliation?.observedAfterResumeForDevOnly() == null

    internal fun clearsForAndroidTest(): Int = clears.get()

    internal fun closedForAndroidTest(): Boolean =
        reconciliation?.isClosed() ?: true

    override fun onDestroy() {
        privateInvalidation?.close()
        lifecycleBinding?.close()
        reconciliation?.close()
        super.onDestroy()
    }
}
