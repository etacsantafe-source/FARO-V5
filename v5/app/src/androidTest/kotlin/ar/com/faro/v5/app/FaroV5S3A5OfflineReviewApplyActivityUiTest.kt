package ar.com.faro.v5.app

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.workspace.*
import ar.com.faro.v5.surface.*
import ar.com.faro.v5.store.reality.RealityTimedReceiptQuery
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Canonical candidate fixture exists ONLY in test APK. Real debug Activity/ports/stores, no HTTP. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A5OfflineReviewApplyActivityUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val root get() = File(context.noBackupFilesDir, "faro-s3-x03-u02-isolated-debug-only")
    private fun views(v: View): List<View> = buildList {
        add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(views(v.getChildAt(i)))
    }
    private fun awaitLease() {
        val field = FaroV5X03U02IsolatedAiActivity::class.java.getDeclaredField("occupied").apply { isAccessible = true }
        val end = SystemClock.elapsedRealtime()+15_000
        while ((field.get(null) as AtomicBoolean).get() && SystemClock.elapsedRealtime()<end) Thread.sleep(50)
        assertFalse("Host cleanup must release lease", (field.get(null) as AtomicBoolean).get())
    }
    private fun open(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>, grant: Boolean = false): FaroV5QuarantinedV2ChatHostBinding {
        awaitLease()
        s.onActivity { a ->
            val all=views(a.window.decorView)
            val check=all.filterIsInstance<CheckBox>().single()
            assertFalse("Offline must not require a Free claim", check.isChecked)
            all.filterIsInstance<Button>().single { it.text.toString()==if(grant) "Abrir Chat y Actividad offline" else "Abrir Chat offline" }.performClick()
        }
        compose.waitUntil(45_000) { runCatching {
            compose.onAllNodesWithText("Enviar").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        compose.onAllNodesWithText("Consultar FARO").assertCountEquals(0)
        var host: FaroV5QuarantinedV2ChatHostBinding?=null
        s.onActivity { a -> host=a.javaClass.getDeclaredField("host").apply { isAccessible=true }.get(a) as FaroV5QuarantinedV2ChatHostBinding }
        return requireNotNull(host)
    }
    private fun graph(h: FaroV5QuarantinedV2ChatHostBinding) = h.javaClass.getDeclaredField("graph").apply {
        isAccessible=true
    }.get(h) as FaroV5GenerationStoreGraph
    private data class Seed(val chat: ConversationId, val plan: PreparedPlan)
    private fun seed(h: FaroV5QuarantinedV2ChatHostBinding): Seed {
        val g=graph(h);val w=requireNotNull(g.workspace)
        val chat=runBlocking { h.workspaceReadPort.readSnapshot().activeConversationId }!!
        val plan=PreparedPlan.of(ApplyPlanNode(PlanNodeId("profile"),PreparedOperation(
            OperationContractDescriptor(ProfileOperations.UPDATE_DECLARED,ProfileOperations.CAPABILITY_ID,
                ProfileOperations.EXECUTOR_ID,ExecutionProfile.REALITY_MUTATION,ContractVersion("1"),
                ContractFingerprint("profile-update-declared-v1"),ContractExposure.BINDABLE),
            ProfileDeclaredUpdatePayload.payload(ProfileDeclaredUpdate(DeclaredDisplayName.of("A5 Offline Verified"))),
            DependencyStampSet.of(g.reality.read().dependencyStamp()),IdempotencyKey("a5offline-op")),emptySet()))
        val result=w.createPlanReview(WorkspacePlanReviewCreateRequest(chat,"a5offline-review",
            runBlocking { w.read(chat) }!!.authorityToken,w.snapshot().revision,plan.planFingerprint,
            "Cambiar nombre declarado a A5 Offline Verified",PreparedPlanEnvelopeCodec.encode(plan),IdempotencyKey("a5offline-seed")))
        assertTrue(result is WorkspacePlanReviewWriteResult.Saved)
        return Seed(chat, plan)
    }
    private fun collapsed() {
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Revisar cambios").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        compose.onAllNodesWithText("Confirmar propuesta").assertCountEquals(0)
        compose.onAllNodesWithText("Descartar").assertCountEquals(0)
        compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        compose.onAllNodesWithText("Cambiar nombre declarado a A5 Offline Verified").assertCountEquals(0)
    }
    private fun expanded() {
        compose.onNodeWithText("Revisar cambios").performClick()
        compose.onNodeWithText("Confirmar propuesta").assertExists()
        compose.onNodeWithText("Cambiar nombre declarado a A5 Offline Verified").assertExists()
    }
    private fun withFixture(block:(ActivityScenario<FaroV5X03U02IsolatedAiActivity>,FaroV5QuarantinedV2ChatHostBinding,Seed)->Unit) {
        assertFalse(root.exists())
        val s2=File(context.noBackupFilesDir,"faro-s2-f1-debug-only")
        val s2Before=if(s2.exists()) s2.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() } else emptyMap()
        check(android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.MODEL.contains("sdk", true))
        assertEquals("ar.com.faro.alpha.s2isolated.v5dev", context.packageName)
        try {
            ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { s ->
                val first=open(s);val seed=seed(first)
                // The actual Activity must discover the durable candidate on reopen.
                s.moveToState(Lifecycle.State.STARTED);awaitLease();s.moveToState(Lifecycle.State.RESUMED)
                val host=open(s)
                collapsed()
                block(s,host,seed)
            }
        } finally {
            awaitLease()
            val s2After=if(s2.exists()) s2.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() } else emptyMap()
            assertEquals(s2Before,s2After)
            assertTrue(root.deleteRecursively())
        }
    }

    private fun port(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>): ConversationApplyRequestPort {
        var p: ConversationApplyRequestPort?=null
        s.onActivity { a ->
            val session=a.javaClass.getDeclaredField("activeSession").apply { isAccessible=true }.get(a)
            p=session.javaClass.getDeclaredField("applyPort").apply { isAccessible=true }.get(session) as ConversationApplyRequestPort
        }
        return requireNotNull(p)
    }
    private fun approve(host: FaroV5QuarantinedV2ChatHostBinding,chat: ConversationId): ConversationPlanReviewSnapshot {
        expanded()
        compose.onNodeWithText("Confirmar propuesta").performClick()
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Ver propuesta aprobada").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        val approved=runBlocking { host.reviewPort.reviews(chat) }.single()
        assertEquals(ConversationPlanReviewState.APPROVED,approved.state)
        assertNull(approved.receipt)
        return approved
    }
    private fun reopen(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>, grant: Boolean = false): FaroV5QuarantinedV2ChatHostBinding {
        s.moveToState(Lifecycle.State.STARTED);awaitLease();s.moveToState(Lifecycle.State.RESUMED)
        return open(s, grant)
    }
    private fun history(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>): FaroV5ActivityReadPort {
        var result: FaroV5ActivityReadPort? = null
        s.onActivity { a ->
            val session = a.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.get(a)
            result = session.javaClass.getDeclaredField("history").apply { isAccessible = true }.get(session) as FaroV5ActivityReadPort
        }
        return requireNotNull(result)
    }
    private fun activity() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        val role = SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Button)
        compose.onNode(hasText("Actividad") and hasClickAction() and !role).performScrollTo().performClick()
    }
    private fun unavailable() {
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Actividad no disponible. No se consultaron datos.").fetchSemanticsNodes().size == 1
        }.getOrDefault(false) }
        compose.onAllNodesWithText("No hay actividad en las dos fuentes autorizadas. No incluye otras fuentes.").assertCountEquals(0)
        compose.onAllNodesWithText("Aplicación confirmada").assertCountEquals(0)
    }
    private fun commit(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>, host: FaroV5QuarantinedV2ChatHostBinding,
                       seed: Seed, doubleClick: Boolean = false): ExecutionReceipt {
        val r = graph(host).reality
        val pending = runBlocking { host.reviewPort.reviews(seed.chat) }.single()
        assertTrue(runBlocking { port(s).request(pending.context()) } is ConversationApplyHostOutcome.Denied)
        approve(host, seed.chat)
        assertEquals(0L, r.read().revision)
        assertTrue(r.backup.snapshot().committedReceipts.isEmpty())
        compose.onNodeWithText("Ver propuesta aprobada").performClick()
        val apply = compose.onNodeWithText("Aplicar cambios")
        if (doubleClick) {
            val click = apply.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            InstrumentationRegistry.getInstrumentation().runOnMainSync { click(); click() }
        } else apply.performClick()
        compose.waitUntil(30_000) {
            runBlocking { host.reviewPort.reviews(seed.chat) }.single().state == ConversationPlanReviewState.COMMITTED
        }
        val committed = runBlocking { host.reviewPort.reviews(seed.chat) }.single()
        val receipt = requireNotNull(committed.receipt)
        assertEquals(seed.plan.planFingerprint, receipt.planFingerprint)
        assertEquals(receipt, r.executionReceiptLookup.find(requireNotNull(committed.transactionId)))
        assertEquals(1L, r.read().revision)
        assertEquals(1, r.backup.snapshot().committedReceipts.size)
        return receipt
    }
    private fun sameReceipt(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>, host: FaroV5QuarantinedV2ChatHostBinding,
                            receipt: ExecutionReceipt) {
        val r = graph(host).reality
        val page = requireNotNull(runBlocking { history(s).readActivity() })
        val row = page.entries.single { it.source == FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT }
        val canonical = r.timedReceiptHistory.query(RealityTimedReceiptQuery()).receipts.single()
        assertEquals(receipt, canonical.receipt)
        assertEquals(receipt.transactionId.value, row.opaqueId)
        assertEquals(canonical.recordedAtEpochMillis, row.timestampEpochMillis)
        assertEquals(FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED, row.timeProvenance)
        assertEquals(receipt, r.executionReceiptLookup.find(receipt.transactionId))
        assertFalse(page.globallyComplete)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test fun offlineExplicitReviewApplyAndAuthorizedActivityShareCanonicalReceipt() = withFixture { s, host, seed ->
        assertNull(runBlocking { history(s).readActivity() })
        val receipt = commit(s, host, seed)
        assertNull("Apply does not grant history", runBlocking { history(s).readActivity() })
        val reopened = reopen(s, grant = true)
        sameReceipt(s, reopened, receipt)
        activity()
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Aplicación confirmada").fetchSemanticsNodes().size == 1
        }.getOrDefault(false) }
        compose.onNodeWithText("Actividad de dos fuentes autorizadas. Cobertura parcial de FARO.").assertIsDisplayed()
        compose.onAllNodesWithText(receipt.transactionId.value, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("A5 Offline Verified", substring = true).assertCountEquals(0)
    }

    @Test fun offlineDoubleClickAndReopenNeverDuplicateActivityReceipt() = withFixture { s, host, seed ->
        val receipt = commit(s, host, seed, doubleClick = true)
        repeat(2) {
            val reopened = reopen(s, grant = true)
            sameReceipt(s, reopened, receipt)
            val restored = runBlocking { reopened.reviewPort.reviews(seed.chat) }.single()
            assertEquals(receipt, restored.receipt)
            assertTrue(runBlocking { port(s).request(restored.context()) } is ConversationApplyHostOutcome.Denied)
            assertEquals(1L, graph(reopened).reality.read().revision)
            assertEquals(1, graph(reopened).reality.backup.snapshot().committedReceipts.size)
        }
    }

    @Test fun offlineActivityGrantRevokedOnPauseAndReturnRequiresNewGesture() = withFixture { s, host, seed ->
        val receipt = commit(s, host, seed)
        val granted = reopen(s, grant = true)
        sameReceipt(s, granted, receipt)
        val old = history(s)
        s.moveToState(Lifecycle.State.STARTED)
        assertNull(runBlocking { old.readActivity() })
        awaitLease()
        s.moveToState(Lifecycle.State.RESUMED)
        open(s)
        assertNull(runBlocking { history(s).readActivity() })
        assertNull("Old grant cannot resurrect", runBlocking { old.readActivity() })
        activity(); unavailable()
        val fresh = reopen(s, grant = true)
        sameReceipt(s, fresh, receipt)
        assertNull(runBlocking { old.readActivity() })
    }

    @Test fun offlineMissingGrantAndGenerationIdentityNeverBecomePhantomZero() = withFixture { s, _, _ ->
        activity(); unavailable()
        val granted = reopen(s, grant = true)
        val reader = history(s)
        assertNotNull("Authoritative empty is allowed only after explicit read grant", runBlocking { reader.readActivity() })
        val dir = graph(granted).generationDirectory.toFile()
        val descriptor = requireNotNull(dir.listFiles()).single { it.name.startsWith("descriptor-") && it.name.endsWith(".bin") }
        val bytes = descriptor.readBytes()
        try {
            descriptor.writeBytes(byteArrayOf(0, 1, 2))
            assertNull("Invalid physical generation must deny canonical read", runBlocking { reader.readActivity() })
            activity(); unavailable()
        } finally { descriptor.writeBytes(bytes) }
        assertNull("Restoring descriptor bytes must not resurrect consumed authority", runBlocking { reader.readActivity() })
    }
}
