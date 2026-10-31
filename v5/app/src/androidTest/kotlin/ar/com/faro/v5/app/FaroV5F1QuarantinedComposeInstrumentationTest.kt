package ar.com.faro.v5.app

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.spi.NotificationProjectionObservation
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
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Compose UI → actual five-owner staged Workspace → accepted turn →
 * trusted local ModelGateway → durable reply → activity recreation.
 * Not full F1 E2E: Apply and external Delivery intentionally absent, T4 NOT_RUN.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
// Notification permission is process-wide. Run denied-permission UI tests
// before the positive OS-post test; revoking a runtime permission during
// instrumentation kills the app process on Android 13+.
class FaroV5F1QuarantinedComposeInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun awaitUi(description: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(30_000, condition)
        } catch (failure: Throwable) {
            // The actual semantics tree distinguishes a missing Activity/root
            // from a failed Workspace selection or an absent Composer/Review.
            val tree = runCatching {
                compose.onRoot(useUnmergedTree = true).printToString()
            }.getOrElse { "No Compose root: ${it.javaClass.simpleName}: ${it.message}" }
            throw AssertionError("F1 UI timeout at $description. Semantics: $tree", failure)
        }
    }

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
            // Real provider-neutral host admission uses explicit Android
            // consent and Keystore, but never sends a request in this test.
            materialize(staged.directory)
            val synthetic = ("test_" + java.util.UUID.randomUUID().toString()
                .replace("-", "")).toByteArray(Charsets.UTF_8)
            try {
                val initial = runBlocking { control.read() }
                assertTrue(runBlocking {
                    control.enableWithCredential(synthetic, initial.revision)
                } is FaroV5TrustedAssistantControlOwner.Change.Saved)
                FaroV5F1ConsentedQuarantineHost.open(
                    context, staged, SingleProcessMaterialWriteGate(), Supplier { activeV1 },
                ).use { opened ->
                    assertNotNull("Consented Keystore-backed F1 host", opened)
                    assertTrue(requireNotNull(opened).verifyQuarantined())
                }
                val allowed = runBlocking { control.read() }
                assertTrue(runBlocking {
                    control.disable(allowed.revision)
                } is FaroV5TrustedAssistantControlOwner.Change.Saved)
                assertNull(FaroV5F1ConsentedQuarantineHost.open(
                    context, staged, SingleProcessMaterialWriteGate(), Supplier { activeV1 },
                ))
            } finally {
                synthetic.fill(0)
                val current = runBlocking { control.read() }
                if (current.remoteReasoningEnabled) {
                    runBlocking { control.disable(current.revision) }
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }


    @Test fun recoverOrphanedProcessingAfterExclusiveHostRestartWithoutProviderReplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f1-restart-")
        try {
            val staged = FileStorageGenerationLayout(root).stageWorkspaceV2(
                StorageGenerationDescriptor(
                    StorageGenerationId("f1-restart-isolated"),
                    "schema-five-store-1", 4L,
                )
            )
            materialize(staged.directory)
            val activeV1 = Files.createDirectory(root.resolve("active-v1"))
            val gate = SingleProcessMaterialWriteGate()
            val modelCalls = AtomicInteger(0)
            val model = ModelGateway { _, _ ->
                modelCalls.incrementAndGet()
                error("recovery must not call model provider")
            }
            val chat = ConversationId("f1-restart-chat")
            fun open(exclusive: Boolean) =
                FaroV5QuarantinedV2ChatHostBinding.openForCertification(
                    staged, gate, Supplier { activeV1 }, model,
                    Supplier { ModelProfileId("local-recovery") }, { true },
                    exclusiveRestartRecovery = if (exclusive) BooleanSupplier { true } else null,
                )
            open(false).use { host ->
                assertTrue(runBlocking {
                    host.selectionPort.createAndSelect(
                        chat, 1710000000000L, IdempotencyKey("f1-restart-create"),
                    )
                } is ConversationSelectionResult.Saved)
                val draft = requireNotNull(runBlocking { host.composerPort.read(chat) })
                val saved = runBlocking {
                    host.composerPort.save(ConversationDraftSaveRequest(
                        chat, draft.authorityToken, draft.revision,
                        "Mensaje aceptado antes del cierre", IdempotencyKey("f1-restart-draft"),
                    ))
                } as ConversationDraftWriteResult.Saved
                assertTrue(runBlocking {
                    host.composerPort.sendDraft(ConversationDraftSendRequest(
                        ConversationDraftSendCandidate(
                            chat, saved.snapshot.authorityToken, saved.snapshot.revision,
                            saved.snapshot.text,
                        ),
                        ConversationMessageId("f1-restart-user"),
                        ConversationTurnId("f1-restart-turn"),
                        1710000000001L, IdempotencyKey("f1-restart-send"),
                    ))
                } is ConversationTimelineWriteResult.Saved)
            }
            ConversationWorkspaceStore(
                staged.directory.resolve(GenerationStoreFiles.WORKSPACE).toString()
            ).use { workspace ->
                val before = requireNotNull(runBlocking { workspace.readTimeline(chat) })
                val processing = ConversationMessage(
                    ConversationMessageId("f1-restart-faro"), chat,
                    ConversationTurnId("f1-restart-turn"),
                    ConversationMessageRole.FARO, ConversationMessageState.PROCESSING,
                    "", 1710000000002L,
                )
                assertTrue(runBlocking {
                    workspace.writeTimeline(ConversationTimelineWriteRequest(
                        chat, before.authorityToken, before.revision,
                        IdempotencyKey("f1-restart-processing"),
                        ConversationTimelineIntent.StartFaro(processing),
                    ))
                } is ConversationTimelineWriteResult.Saved)
            }
            open(true).use { host ->
                assertEquals(1, host.recoveredInterruptedTurns)
                val timeline = requireNotNull(runBlocking {
                    host.timelineReadPort.readTimeline(chat)
                })
                assertEquals(ConversationMessageState.FAILED, timeline.messages.last().state)
                assertEquals("", timeline.messages.last().text)
                assertEquals(0, modelCalls.get())
                assertTrue(host.verifyQuarantined())
            }
            open(true).use { host ->
                assertEquals(0, host.recoveredInterruptedTurns)
                assertEquals(0, modelCalls.get())
            }
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
                        assertEquals(chat, runBlocking {
                            host.workspaceReadPort.readSnapshot().activeConversationId
                        })
                        assertEquals(chat, ConversationWorkspaceProjector.project(runBlocking {
                            host.workspaceReadPort.readSnapshot()
                        }).activeConversationId)
                        awaitUi("Composer after canonical Workspace selection") {
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
        // Fresh emulator starts denied; never revoke during instrumentation,
        // because Android kills the instrumented process on runtime revocation.
        assertNotEquals(PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
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

            // Only select the canonical Workspace conversation here. No review
            // or ApplyPlan is pre-staged: the durable structured model reply
            // must generate the trusted, fingerprinted proposal itself.
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
                assertTrue(runBlocking { workspace.reviews(chat) }.isEmpty())
            }

            // One Android screen must carry the same canonical conversation
            // through Composer -> Workspace -> local IA -> human Review -> Apply.
            // The local model is deterministic; real remote-provider consent
            // remains a separate security gate, never a fake network success.
            val structured = """{"type":"profile.update_declared","display_name":"FARO UI Verified"}"""
            val replies = AtomicInteger(0)
            val localGateway = ModelGateway { request, _ ->
                replies.incrementAndGet()
                assertEquals("Hola FARO", String(request.input.copyBytes(), Charsets.UTF_8))
                ReasoningResult.Success(
                    ModelBindingId("local-f1-combined"),
                    CanonicalPayload.fromEncoded(
                        "text/plain", structured.toByteArray(Charsets.UTF_8),
                    ),
                )
            }
            FaroV5QuarantinedV2ChatHostBinding.openForCertification(
                staged, gate, Supplier { activeV1 }, localGateway,
                Supplier { ModelProfileId("f1-review-local") }, { true },
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
                                dispatcher = NotificationDeliveryDispatcher(
                                    deliveryStore,
                                    AndroidNotificationDeliverySink(context, R.drawable.ic_faro_notification),
                                ),
                            )
                        try {
                            ActivityScenario.launch(
                                FaroV5F1QuarantineCertificationActivity::class.java,
                            ).use {
                                assertEquals(chat, runBlocking {
                                    host.workspaceReadPort.readSnapshot().activeConversationId
                                })
                                assertEquals(chat, ConversationWorkspaceProjector.project(runBlocking {
                                    host.workspaceReadPort.readSnapshot()
                                }).activeConversationId)
                                awaitUi("Composer in combined F1 flow") {
                                    compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
                                }
                                compose.onNode(hasSetTextAction()).performTextInput("Hola FARO")
                                compose.onNodeWithText("Enviar").performClick()
                                awaitUi("Trusted Review created after durable structured IA reply") {
                                    compose.onAllNodesWithText("Confirmar propuesta")
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                assertEquals(1, replies.get())
                                val timeline = runBlocking { host.timelineReadPort.readTimeline(chat) }
                                assertEquals(
                                    listOf("Hola FARO", structured),
                                    requireNotNull(timeline).messages.map { it.text },
                                )
                                val proposed = runBlocking { host.reviewPort.reviews(chat) }.single()
                                assertEquals(ConversationPlanReviewState.PENDING, proposed.state)
                                assertNotNull(proposed.planFingerprint)
                                assertNull(reality.read().displayName)
                                awaitUi("Review after canonical Workspace selection") {
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
                                // Recover from the canonical committed Workspace
                                // receipt without issuing another Apply or Notice.
                                val newNotices = runBlocking {
                                    FaroV5QuarantinedCommittedDeliveryReconciler(
                                        host.reviewPort, NoticeRouter(deliveryStore),
                                        gate, BooleanSupplier { host.verifyQuarantined() },
                                    ).reproject(chat)
                                }
                                assertEquals(0, newNotices)
                                assertEquals(1L, reality.read().revision)
                                assertEquals(1, deliveryStore.readNotices().size)
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
                                // The human Apply callback now reaches the real OS
                                // adapter; denied permission must block the effect.
                                assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                assertNotEquals(
                                    PackageManager.PERMISSION_GRANTED,
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
                                )
                                val delivery = requireNotNull(
                                    deliveryStore.readDeliveryIntent(materialized.deliveryIntent.deliveryIntentId)
                                )
                                assertEquals(DeliveryEffectState.BLOCKED_PERMISSION, delivery.state)
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

    @Test fun permittedAndroidDeliveryPostsOnceAndIsObservableInIsolatedStage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        assertNotEquals("OS grant must start denied in the isolated emulator",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f1-os-post-")
        var postedTag: String? = null
        try {
            ui.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue("OS notifications disabled despite grant", manager.areNotificationsEnabled())
            AuxiliaryDeliveryStore(root.resolve("auxiliary.db").toString()).use { store ->
                val routed = NoticeRouter(store).route(
                    NoticeCandidate(
                        NoticeKey.of("f1-os-post-" + System.nanoTime()),
                        SourceRef.of("f1-staged-android"),
                        OccurrenceRef.of("committed"),
                        NoticeMateriality.NOTICE,
                        NoticeSensitivity.NORMAL,
                        NoticeSummary.of("Cambios aplicados y registrados"),
                        DestinationRef.of("chat-staged"),
                    ),
                ) as NoticeRouteResult.Materialized
                val intentId = routed.deliveryIntent.deliveryIntentId
                postedTag = AndroidNotificationDeliverySink.stableTag(intentId.value)
                val sink = AndroidNotificationDeliverySink(
                    context, R.drawable.ic_faro_notification,
                )
                val dispatcher = NotificationDeliveryDispatcher(store, sink)
                val first = dispatcher.dispatch(intentId)
                assertTrue(first is NotificationDispatchExecution.Completed)
                assertEquals(DeliveryEffectState.POSTED_TO_OS,
                    (first as NotificationDispatchExecution.Completed).deliveryIntent.state)
                compose.waitUntil(5_000) {
                    sink.observe(intentId.value) == NotificationProjectionObservation.ACTIVE
                }
                assertEquals(1, manager.activeNotifications.count { it.tag == postedTag })
                assertTrue("OS effect must not be repeated",
                    dispatcher.dispatch(intentId) is NotificationDispatchExecution.NotEligible)
                assertEquals(DeliveryEffectState.POSTED_TO_OS,
                    requireNotNull(store.readDeliveryIntent(intentId)).state)
                assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
            }
        } finally {
            // The emulator notification must never survive the test.
            postedTag?.let { manager.cancel(it, 1001) }
            // Do not revoke inside instrumentation: Android kills the process.
            // This CI job uses a disposable emulator; no user permission changes.
            root.toFile().deleteRecursively()
        }
    }

    @Test fun quarantinedF2FiveOwnerRecoveryHealthKeepsV1PointerAndRejectsMissingWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "f2-health-")
        try {
            val layout = FileStorageGenerationLayout(root)
            val pointer = FileActiveGenerationPointer(root)
            val journal = FileBootstrapJournal(root)
            val oldId = StorageGenerationId("f2-active-v1")
            val targetId = StorageGenerationId("f2-staged-v2")
            val operation = MaintenanceOperationId("f2-validated-restore")
            val old = layout.stage(StorageGenerationDescriptor(oldId, "schema-five-store-1", 4L))
            GenerationStoreFiles.LEGACY_V1.forEach {
                Files.write(old.directory.resolve(it), byteArrayOf(1))
            }
            assertEquals(oldId, requireNotNull(pointer.compareAndSet(null, oldId)).generationId)
            val stage = layout.stageWorkspaceV2(
                StorageGenerationDescriptor(targetId, "schema-five-store-1", 4L),
            )
            materialize(stage.directory)
            journal.create(BootstrapJournalEntry(
                operation, MaintenanceOperationKind.RESTORE, oldId, targetId,
                BootstrapPhase.VALIDATED,
            ))
            val pointerBefore = pointer.read()
            val journalBefore = journal.read(operation)
            val inspector = FaroV5QuarantinedV2RecoveryHost(
                layout, journal, pointer, SingleProcessMaterialWriteGate(),
            )
            assertEquals(
                V2QuarantinedRecoveryEvidence.Candidate(targetId.value),
                inspector.inspectHealthy(operation),
            )
            assertEquals(pointerBefore, pointer.read())
            assertEquals(journalBefore, journal.read(operation))
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)

            val workspaceFile = stage.directory.resolve(GenerationStoreFiles.WORKSPACE)
            Files.delete(workspaceFile)
            assertEquals(
                V2QuarantinedRecoveryEvidence.Blocked,
                inspector.inspectHealthy(operation),
            )
            assertFalse("recovery preflight must not recreate missing Workspace",
                Files.exists(workspaceFile))
            assertEquals(pointerBefore, pointer.read())
            assertEquals(journalBefore, journal.read(operation))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

}
