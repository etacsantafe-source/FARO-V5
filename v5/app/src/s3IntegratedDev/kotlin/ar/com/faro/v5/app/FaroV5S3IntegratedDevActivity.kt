package ar.com.faro.v5.app

import android.content.Intent
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/** New sandbox only; existing native controls and canonical owners are reused. */
class FaroV5S3IntegratedDevActivity : FaroV5V2CoreDevActivity() {
    private companion object { val occupied = AtomicBoolean(false) }
    private lateinit var free: CheckBox
    private var remoteRequested = false
    private var admission: FaroV5DevOpenAdmission? = null
    private var paused = false

    internal override fun rootPath() = noBackupFilesDir.toPath().resolve("faro-s3-integrated-dev-root")
    internal override fun screenTitle() = "FARO S3 INTEGRADA"
    internal override fun screenDescription() = "Entorno nuevo y separado. No importa datos ni claves de S2, CORE o X03. " +
        "Enviar guarda; Consultar FARO solicita IA. Revisar, confirmar y aplicar son acciones separadas."

    internal override fun addAdmissionControls(column: LinearLayout) {
        check(packageName == "ar.com.faro.alpha.s3integrateddev")
        free = CheckBox(this).apply {
            text = "Verifiqué personalmente el plan Free de Groq para esta sesión"
            isChecked = false
            isSaveEnabled = false
        }
        column.addView(free)
        column.addView(Button(this).apply {
            text = "Configurar o revocar Groq"
            setOnClickListener { startActivity(Intent(this@FaroV5S3IntegratedDevActivity, FaroV5AiSettingsActivity::class.java)) }
        })
        column.addView(Button(this).apply {
            text = "Abrir Chat IA integrado"
            setOnClickListener {
                remoteRequested = true
                try { openLocalChat() } finally { remoteRequested = false }
            }
        })
    }

    internal override fun beginOpening(): FaroV5DevOpenAdmission {
        check(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        check(!remoteRequested || free.isChecked)
        check(occupied.compareAndSet(false, true)) { "previous integrated root still closing" }
        return FaroV5DevOpenAdmission(remoteRequested) { occupied.set(false) }.also { admission = it }
    }

    internal override fun trustedAssistantComponents(admission: FaroV5DevOpenAdmission): FaroV5TrustedAssistantComponents? {
        check(admission.live.get())
        if (!admission.remote) return null
        val owner = FaroV5TrustedAssistantControlOwner.get(applicationContext)
        val control = runBlocking { owner.read() }
        check(control.remoteReasoningEnabled && control.activeProfile == FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE)
        val credential = checkNotNull(control.credentialRefFor(FaroV5TrustedModelGatewayFactory.BINDING_ID))
        checkNotNull(owner.secretReader().readValueOrNull(credential))
        return checkNotNull(FaroV5TrustedModelGatewayFactory({ runBlocking { owner.read() } }, owner.secretReader()).create())
    }

    override fun onPause() {
        admission?.revoke() // Before inherited grant revocation or any asynchronous completion.
        if (::free.isInitialized) free.isChecked = false
        paused = true
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (paused) {
            paused = false
            recreate() // Admission UI only; old Session/late bootstrap retains lease until actual close.
        }
    }
}
