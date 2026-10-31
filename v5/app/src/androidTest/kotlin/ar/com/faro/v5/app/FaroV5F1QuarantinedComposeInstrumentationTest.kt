package ar.com.faro.v5.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Compose UI → actual five-owner staged Workspace → accepted turn →
 * trusted local ModelGateway → durable reply → activity recreation.
 * Not full F1 E2E: Apply and external Delivery intentionally absent, T4 NOT_RUN.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5F1QuarantinedComposeInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun materialize(path: Path) {
        RealityStore(path.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(path.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(path.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(path.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(path.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
    }

    @Test fun composeSendUsesCanonicalFiveOwnerWorkspaceAndRecoversAfterRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f1-ui-")
        try {
            val staged = FileStorageGenerationLayout(root).stageWorkspaceV2(
                StorageGenerationDescriptor(
                    StorageGenerationId("f1-ui-isolated"),
                    "schema-five-store-1", 4L,
                )
            )
            materialize(staged.directory)
            val activeV1 = Files.createDirectory(root.resolve("active-v1"))
            val calls = AtomicInteger(0)
            val gateway = ModelGateway { request, _ ->
                calls.incrementAndGet()
                assertEquals(ModelProfileId("local-f1-ui"), request.profileId)
                assertEquals("Hola FARO", String(request.input.copyBytes(), Charsets.UTF_8))
                ReasoningResult.Success(
                    ModelBindingId("local-no-network"),
                    CanonicalPayload.fromEncoded(
                        "text/plain", "Respuesta local".toByteArray(Charsets.UTF_8),
                    ),
                )
            }
            FaroV5QuarantinedV2ChatHostBinding.openForCertification(
                staged, SingleProcessMaterialWriteGate(),
                Supplier { activeV1 }, gateway,
                Supplier { ModelProfileId("local-f1-ui") }, { true },
            ).use { host ->
                val chat = ConversationId("compose-f1-chat")
                assertTrue(runBlocking {
                    host.selectionPort.createAndSelect(
                        chat, 1710000000000L, IdempotencyKey("f1-ui-create"),
                    )
                } is ConversationSelectionResult.Saved)
                assertTrue(host.verifyQuarantined())
                FaroV5F1QuarantineUiFixture.host = host
                try {
                    ActivityScenario.launch(
                        FaroV5F1QuarantineCertificationActivity::class.java,
                    ).use { scenario ->
                        compose.waitUntil(30_000) {
                            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
                        }
                        compose.onNode(hasSetTextAction()).performTextInput("Hola FARO")
                        compose.onNodeWithText("Enviar").performClick()
                        compose.waitUntil(30_000) {
                            compose.onAllNodesWithText("Respuesta local")
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                        assertEquals(1, calls.get())
                        val saved = runBlocking { host.timelineReadPort.readTimeline(chat) }
                        assertEquals(
                            listOf("Hola FARO", "Respuesta local"),
                            requireNotNull(saved).messages.map { it.text },
                        )
                        scenario.recreate()
                        compose.waitUntil(30_000) {
                            compose.onAllNodesWithText("Respuesta local")
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                        assertEquals(1, calls.get())
                        assertTrue(runBlocking { host.reviewPort.reviews(chat) }.isEmpty())
                        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
                    }
                } finally {
                    FaroV5F1QuarantineUiFixture.host = null
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
