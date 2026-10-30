package ar.com.faro.v5.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.control.AiControlPreferenceStore
import ar.com.faro.v5.platform.android.security.AndroidKeystoreSecretStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F1 Android-only, isolated consent/Keystore/bootstrap integration.
 *
 * No real provider call, paid API, V2 cutover or active-generation mutation.
 * This must execute on Android; compilation on Linux does NOT certify F1 E2E
 * or substitute the user's physical T4.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5TrustedAssistantAndroidIntegrationTest {
    @Test
    fun isolatedKeystoreConsentAndRevocationGateTheRealModelGateway() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(
            context.noBackupFilesDir,
            "faro-v5-f1-instrumentation-${UUID.randomUUID()}.preferences_pb",
        )
        val store = AiControlPreferenceStore(
            file, FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE,
        )
        val owner = FaroV5TrustedAssistantControlOwner(
            store, AndroidKeystoreSecretStore(context),
        )
        var activeRevision: Long? = null
        try {
            val initial = owner.read()
            assertFalse(initial.remoteReasoningEnabled)
            val factory = FaroV5TrustedModelGatewayFactory(
                readControl = { runBlocking { owner.read() } },
                secretReader = owner.secretReader(),
            )
            assertNull("No implicit cloud consent", factory.create())

            // Synthetic test credential only. Never use or store a real API key.
            val fakeCredential = ("test_" + UUID.randomUUID().toString()
                .replace("-", "")).toByteArray(Charsets.UTF_8)
            val result = try {
                owner.enableWithCredential(fakeCredential, initial.revision)
            } finally {
                fakeCredential.fill(0)
            }
            assertTrue(result is FaroV5TrustedAssistantControlOwner.Change.Saved)
            val allowed = owner.read()
            activeRevision = allowed.revision
            assertTrue(allowed.remoteReasoningEnabled)
            val id = allowed.credentialRefFor(
                FaroV5TrustedModelGatewayFactory.BINDING_ID,
            )
            assertNotNull(id)
            assertNotNull("Android Keystore credential persisted", owner.secretReader()
                .readValueOrNull(checkNotNull(id)))
            assertNotNull("Real composed gateway constructed after consent", factory.create())

            val revoked = owner.disable(allowed.revision)
            assertTrue(revoked is FaroV5TrustedAssistantControlOwner.Change.Saved)
            activeRevision = null
            assertFalse(owner.read().remoteReasoningEnabled)
            assertNull("Revoked Keystore secret must not be readable", owner.secretReader()
                .readValueOrNull(checkNotNull(id)))
            assertNull("Revoked consent must remove model gateway admission", factory.create())
            assertFalse("V2 remains dormant", FaroV5HostCertification.V2_ACTIVE_ENABLED)
        } finally {
            // No test consent or credential survives an assertion failure.
            activeRevision?.let {
                try { owner.disable(owner.read().revision) } catch (_: Exception) { }
            }
            store.close()
            file.delete()
        }
    }

    @Test
    fun localSettingsScreenIsNotExportedAndCannotActivateV2() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, FaroV5AiSettingsActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse("AI settings must not be externally launchable", info.exported)
        assertFalse("No uncertified chat activation", FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
}
