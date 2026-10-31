package ar.com.faro.v5.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.delivery.api.DeliveryEffectState
import ar.com.faro.v5.delivery.api.NotificationDeliveryDispatcher
import ar.com.faro.v5.delivery.api.NotificationDispatchExecution
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeRouteResult
import ar.com.faro.v5.delivery.api.NoticeRouter
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.platform.android.notification.AndroidNotificationDeliverySink
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.workspace.*
import java.util.function.BooleanSupplier
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

    @Test fun realProviderQuarantineRequiresAndroidConsentBeforeOpeningStores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f1-consent-")
        try {
            val staged = FileStorageGenerationLayout(root).stageWorkspaceV2(
                StorageGenerationDescriptor(
                    StorageGenerationId("f1-consent-denied"),
                    "schema-five-store-1", 4L,
                )
            )
            // Fresh debug test sandbox has no user grant or Keystore credential.
            // A denied real-provider host must not even initialize Workspace.
            val control = FaroV5TrustedAssistantControlOwner.get(context)
            assertFalse(runBlocking { control.read().remoteReasoningEnabled })
            val activeV1 = Files.createDirectory(root.resolve("active-v1"))
            val denied = FaroV5F1ConsentedQuarantineHost.open(
                context, staged, SingleProcessMaterialWriteGate(), Supplier { activeV1 },
            )
            assertNull(denied)
            assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        } finally {
            root.toFile().deleteRecursively()
        }
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
    @Test fun composeHumanReviewAppliesOnlyToQuarantinedRealityWithCanonicalReceipt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f1-review-ui-")
        try {
            val staged = FileStorageGenerationLayout(root).stageWorkspaceV2(
                StorageGenerationDescriptor(
                    StorageGenerationId("f1-review-ui-isolated"),
                    "schema-five-store-1", 4L,
                )
            )
            materialize(staged.directory)
            val activeV1 = Files.createDirectory(root.resolve("active-v1"))
            val generation = "f1-review-ui-isolated"
            val chat = ConversationId("review-ui-chat")
            val gate = SingleProcessMaterialWriteGate()
            val pointer = Supplier { generation }
            val ready = BooleanSupplier { true }
            val realityPath = staged.directory.resolve(GenerationStoreFiles.REALITY).toString()
            val workspacePath = staged.directory.resolve(GenerationStoreFiles.WORKSPACE).toString()

            // Trusted host/test creates a frozen, verified plan before any UI
            // sees it. The UI never supplies an ApplyPlan, binding or receipt.
            RealityStore(realityPath, materialWriteGate = gate).use { reality ->
                val descriptor = OperationContractDescriptor(
                    ProfileOperations.UPDATE_DECLARED,
                    ProfileOperations.CAPABILITY_ID,
                    ProfileOperations.EXECUTOR_ID,
                    ExecutionProfile.REALITY_MUTATION,
                    ContractVersion("1"),
                    ContractFingerprint("profile-update-declared-v1"),
                    ContractExposure.BINDABLE,
                )
                val operation = PreparedOperation(
                    descriptor,
                    ProfileDeclaredUpdatePayload.payload(
                        ProfileDeclaredUpdate(DeclaredDisplayName.of("FARO UI Verified"))
                    ),
                    DependencyStampSet.of(reality.read().dependencyStamp()),
                    IdempotencyKey("f1-review-ui-operation"),
                )
                val plan = PreparedPlan.of(
                    ApplyPlanNode(PlanNodeId("profile"), operation, emptySet())
                )
                ConversationWorkspaceStore.openExistingWithPlanVerifier(
                    workspacePath, CanonicalPreparedPlanEnvelopeVerifier,
                    materialWriteGate = gate,
                ).use { workspace ->
                    val selection = FaroV5ActiveChatSelectionSession(
                        generation, workspace, gate, pointer, ready,
                    )
                    assertTrue(runBlocking {
                        selection.port.createAndSelect(
                            chat, 1710000000000L, IdempotencyKey("f1-review-ui-create"),
                        )
                    } is ConversationSelectionResult.Saved)
                    val token = requireNotNull(runBlocking { workspace.read(chat) }).authorityToken
                    assertTrue(workspace.createPlanReview(
                        WorkspacePlanReviewCreateRequest(
                            chat, "f1-review-ui-plan", token, workspace.snapshot().revision,
                            plan.planFingerprint, "Actualizar nombre de perfil",
                            PreparedPlanEnvelopeCodec.encode(plan),
                            IdempotencyKey("f1-review-ui-stage"),
                        )
                    ) is WorkspacePlanReviewWriteResult.Saved)
                }
            }

            val noNetwork = ModelGateway { _, _ ->
                error("Review-only Compose certification must not call a model")
            }
            FaroV5QuarantinedV2ChatHostBinding.openForCertification(
                staged, gate, Supplier { activeV1 }, noNetwork,
                Supplier { ModelProfileId("f1-review-local") }, { false },
            ).use { host ->
                RealityStore(realityPath, materialWriteGate = gate).use { reality ->
                    ConversationWorkspaceStore.openExistingWithPlanVerifier(
                        workspacePath, CanonicalPreparedPlanEnvelopeVerifier,
                        materialWriteGate = gate,
                    ).use { applyWorkspace ->
                        val apply = FaroV5ActiveReviewedApplySession(
                            generation, applyWorkspace, reality, gate,
                            pointer, BooleanSupplier { host.verifyQuarantined() },
                        )
                        FaroV5F1QuarantineUiFixture.host = host
                        // Debug-only explicit human request; the shipping port
                        // remains denied because V2_ACTIVE_ENABLED is false.
                        // The only projection here is a durable generic Notice in
                        // isolated Auxiliary; never post an Android notification.
                        // The UI does not supply a receipt or a delivery identity.
                        val deliveryFailures = AtomicInteger(0)
                        val deliveryStore = AuxiliaryDeliveryStore(
                            staged.directory.resolve(GenerationStoreFiles.AUXILIARY).toString(),
                            materialWriteGate = gate,
                        )
                        FaroV5F1QuarantineUiFixture.applyPort =
                            FaroV5QuarantinedReviewedApplyDeliveryPort(
                                upstream = object : ConversationApplyRequestPort {
                                    override suspend fun request(
                                        context: ConversationPlanReviewContext,
                                    ): ConversationApplyHostOutcome =
                                        apply.executeAfterHumanRequest(context)
                                },
                                router = NoticeRouter(deliveryStore),
                                gate = gate,
                                admitted = BooleanSupplier { host.verifyQuarantined() },
                                onProjectionFailure = { deliveryFailures.incrementAndGet() },
                            )
                        try {
                            ActivityScenario.launch(
                                FaroV5F1QuarantineCertificationActivity::class.java,
                            ).use {
                                compose.waitUntil(30_000) {
                                    compose.onAllNodesWithText("Confirmar propuesta")
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                compose.onNodeWithText("Confirmar propuesta").performClick()
                                compose.waitUntil(30_000) {
                                    compose.onAllNodesWithText("Aplicar cambios")
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                assertNull(reality.read().displayName)
                                compose.onNodeWithText("Aplicar cambios").performClick()
                                compose.waitUntil(30_000) {
                                    compose.onAllNodesWithText("Aplicado y registrado")
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                assertEquals("FARO UI Verified", reality.read().displayName?.value)
                                val review = runBlocking { host.reviewPort.reviews(chat) }.single()
                                assertEquals(ConversationPlanReviewState.COMMITTED, review.state)
                                assertNotNull(review.receipt)
                                assertEquals(1L, reality.read().revision)
                                assertEquals(0, deliveryFailures.get())
                                val notices = deliveryStore.readNotices()
                                assertEquals(1, notices.size)
                                assertEquals("Cambios aplicados y registrados", notices.single().summary.value)
                                assertEquals(NoticeSensitivity.NORMAL, notices.single().sensitivity)
                                // Reprojection uses the canonical durable intent, not
                                // a second Apply or a duplicate Notice.
                                val notice = notices.single()
                                val replay = NoticeRouter(deliveryStore).route(
                                    NoticeCandidate(
                                        notice.noticeKey, notice.sourceRef, notice.occurrenceRef,
                                        notice.materiality, notice.sensitivity, notice.summary,
                                        notice.destinationRef,
                                    )
                                )
                                assertTrue(replay is NoticeRouteResult.Materialized)
                                val materialized = replay as NoticeRouteResult.Materialized
                                assertFalse(materialized.created)
                                // Real Android delivery adapter, with no notification
                                // permission: the OS projection must fail closed.
                                // Never grant permission or post from quarantine.
                                assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                assertNotEquals(
                                    PackageManager.PERMISSION_GRANTED,
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
                                )
                                val delivery = NotificationDeliveryDispatcher(
                                    deliveryStore,
                                    AndroidNotificationDeliverySink(
                                        context, R.drawable.ic_faro_notification,
                                    ),
                                ).dispatch(materialized.deliveryIntent.deliveryIntentId)
                                assertTrue(delivery is NotificationDispatchExecution.Completed)
                                assertEquals(
                                    DeliveryEffectState.BLOCKED_PERMISSION,
                                    (delivery as NotificationDispatchExecution.Completed).deliveryIntent.state,
                                )
                                assertEquals(1, deliveryStore.readNotices().size)
                                assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
                            }
                        } finally {
                            FaroV5F1QuarantineUiFixture.applyPort = null
                            FaroV5F1QuarantineUiFixture.host = null
                            deliveryStore.close()
                        }
                    }
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

}
