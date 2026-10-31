package ar.com.faro.v5.app

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import ar.com.faro.v5.kernel.contracts.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/**
 * Minimal S2 F1 consent/credential entry. This is NOT the final S5 Settings UI
 * and never enables the V2 host. No provider call occurs from this Activity.
 */
class FaroV5AiSettingsActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var owner: FaroV5TrustedAssistantControlOwner
    private lateinit var status: TextView
    private lateinit var credential: EditText
    private lateinit var consent: CheckBox
    private lateinit var save: Button
    private lateinit var disable: Button
    private lateinit var probe: Button
    private var revision: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        owner = FaroV5TrustedAssistantControlOwner.get(this)

        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        val title = TextView(this).apply {
            text = "IA de FARO · configuración local"
            textSize = 23f
        }
        column.addView(title)
        status = TextView(this).apply {
            text = "Leyendo configuración…"
            textSize = 15f
        }
        column.addView(status)
        credential = EditText(this).apply {
            hint = "Clave de Groq"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setSaveEnabled(false)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(credential)
        consent = CheckBox(this).apply {
            text = "Autorizo enviar mensajes admitidos a Groq para obtener respuestas de IA."
            isChecked = false
        }
        column.addView(consent)
        save = Button(this).apply {
            text = "Guardar clave y habilitar IA remota"
            isEnabled = false
        }
        column.addView(save)
        disable = Button(this).apply {
            text = "Desactivar IA remota y borrar clave"
            isEnabled = false
        }
        column.addView(disable)
        // Manual debug-only transport check: explicit tap, fixed non-personal
        // input, Keystore credential, no Workspace/Reality write or V2 cutover.
        probe = Button(this).apply {
            text = "Probar conexión Groq (solo debug)"
            isEnabled = false
            visibility = if ((applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                View.VISIBLE else View.GONE
        }
        column.addView(probe)
        column.addView(TextView(this).apply {
            text = "Los secretos se guardan con Android Keystore. Los cambios de activación se aplican tras reiniciar FARO. V2 sigue desactivada."
        })
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)

        save.setOnClickListener {
            if (!consent.isChecked) {
                status.text = "Necesitás aceptar expresamente el envío remoto."
                return@setOnClickListener
            }
            val bytes = credential.text.toString().toByteArray(StandardCharsets.UTF_8)
            credential.text.clear()
            if (revision < 0L) {
                bytes.fill(0)
                return@setOnClickListener
            }
            val expected = revision
            busy(true)
            worker.execute {
                val result = try {
                    runBlocking { owner.enableWithCredential(bytes, expected) }
                } catch (_: Exception) { FaroV5TrustedAssistantControlOwner.Change.Unavailable }
                finally { bytes.fill(0) }
                runOnUiThread {
                    if (!isDestroyed) {
                        showResult(result)
                        busy(false)
                    }
                }
            }
        }
        disable.setOnClickListener {
            if (revision < 0L) return@setOnClickListener
            val expected = revision
            busy(true)
            worker.execute {
                val result = try { runBlocking { owner.disable(expected) } }
                    catch (_: Exception) { FaroV5TrustedAssistantControlOwner.Change.Unavailable }
                runOnUiThread {
                    if (!isDestroyed) {
                        showResult(result)
                        busy(false)
                    }
                }
            }
        }
        probe.setOnClickListener {
            busy(true)
            status.text = "Probando Groq con un mensaje fijo, sin datos personales…"
            worker.execute {
                val message = try {
                    val components = FaroV5TrustedModelGatewayFactory(
                        readControl = { runBlocking { owner.read() } },
                        secretReader = owner.secretReader(),
                    ).create()
                    if (components == null) {
                        "Primero guardá una clave y autorizá expresamente la IA remota."
                    } else {
                        val result = components.gateway.reason(
                            ReasoningRequest(
                                ModelRequestId("f1-debug-probe-" + java.util.UUID.randomUUID()),
                                FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE.profileId,
                                ModelRequirements(setOf(ModelCapability.TEXT)),
                                CanonicalPayload.fromEncoded(
                                    "text/plain",
                                    "Respondé solo FARO_OK".toByteArray(Charsets.UTF_8),
                                ),
                            ),
                            NeverCancelled,
                        )
                        when (result) {
                            is ReasoningResult.Success ->
                                if (FaroV5GroqProbeVerifier.accepts(result.output))
                                    "Groq respondió FARO_OK. Transporte real OK; chat y Apply no se probaron."
                                else
                                    "Groq respondió, pero no devolvió FARO_OK. La prueba no pasó; chat y Apply no se probaron."
                            is ReasoningResult.Failure ->
                                "Groq no respondió: ${result.failure.kind}. Revisá conexión, clave y límites gratuitos."
                        }
                    }
                } catch (_: Exception) {
                    "No se pudo completar la prueba. No se modificó Reality."
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        status.text = message
                        busy(false)
                    }
                }
            }
        }
        refresh()
    }

    private fun busy(active: Boolean) {
        save.isEnabled = !active && revision >= 0L
        disable.isEnabled = !active && revision >= 0L
        probe.isEnabled = !active && revision >= 0L && probe.visibility == View.VISIBLE
    }

    private fun showResult(result: FaroV5TrustedAssistantControlOwner.Change) {
        when (result) {
            is FaroV5TrustedAssistantControlOwner.Change.Saved -> {
                revision = result.snapshot.revision
                consent.isChecked = false
                status.text = if (result.snapshot.remoteReasoningEnabled)
                    "Clave y consentimiento guardados. Reiniciá FARO para cargar la IA."
                else "IA remota desactivada. Los mensajes no se enviarán."
            }
            FaroV5TrustedAssistantControlOwner.Change.Stale -> {
                status.text = "La configuración cambió. Actualizá antes de guardar."
                refresh()
            }
            FaroV5TrustedAssistantControlOwner.Change.InvalidCredential ->
                status.text = "Clave inválida. Revisá el formato sin compartirla."
            FaroV5TrustedAssistantControlOwner.Change.Unavailable ->
                status.text = "No se pudo guardar de forma segura. No se habilitó el envío."
        }
    }

    private fun refresh() {
        worker.execute {
            val snapshot = try { runBlocking { owner.read() } } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (snapshot == null) {
                    revision = -1L
                    status.text = "Configuración no disponible; envío remoto no habilitado."
                } else {
                    revision = snapshot.revision
                    status.text = if (snapshot.remoteReasoningEnabled)
                        "IA remota autorizada. Podés revocar el consentimiento."
                    else "IA remota desactivada por defecto."
                }
                busy(false)
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
