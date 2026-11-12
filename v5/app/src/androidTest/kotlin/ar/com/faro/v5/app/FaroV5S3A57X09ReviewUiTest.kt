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
import ar.com.faro.v5.owner.profile.api.*
import ar.com.faro.v5.workspace.*
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
class FaroV5S3A57X09ReviewUiTest {
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
    private fun open(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>): FaroV5QuarantinedV2ChatHostBinding {
        awaitLease()
        s.onActivity { a ->
            val all=views(a.window.decorView)
            val check=all.filterIsInstance<CheckBox>().single()
            assertFalse("Free confirmation must be fresh", check.isChecked)
            check.isChecked=true
            all.filterIsInstance<Button>().single { it.text.toString()=="Abrir Chat IA U02" }.performClick()
        }
        compose.waitUntil(45_000) { runCatching {
            compose.onAllNodesWithText("Enviar").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        var host: FaroV5QuarantinedV2ChatHostBinding?=null
        s.onActivity { a -> host=a.javaClass.getDeclaredField("host").apply { isAccessible=true }.get(a) as FaroV5QuarantinedV2ChatHostBinding }
        return requireNotNull(host)
    }
    private fun graph(h: FaroV5QuarantinedV2ChatHostBinding) = h.javaClass.getDeclaredField("graph").apply {
        isAccessible=true
    }.get(h) as FaroV5GenerationStoreGraph
    private fun seed(h: FaroV5QuarantinedV2ChatHostBinding): ConversationId {
        val g=graph(h);val w=requireNotNull(g.workspace)
        val chat=runBlocking { h.workspaceReadPort.readSnapshot().activeConversationId }!!
        val plan=PreparedPlan.of(ApplyPlanNode(PlanNodeId("profile"),PreparedOperation(
            OperationContractDescriptor(ProfileOperations.UPDATE_DECLARED,ProfileOperations.CAPABILITY_ID,
                ProfileOperations.EXECUTOR_ID,ExecutionProfile.REALITY_MUTATION,ContractVersion("1"),
                ContractFingerprint("profile-update-declared-v1"),ContractExposure.BINDABLE),
            ProfileDeclaredUpdatePayload.payload(ProfileDeclaredUpdate(DeclaredDisplayName.of("X09 Verified"))),
            DependencyStampSet.of(g.reality.read().dependencyStamp()),IdempotencyKey("x09-op")),emptySet()))
        val result=w.createPlanReview(WorkspacePlanReviewCreateRequest(chat,"x09-review",
            runBlocking { w.read(chat) }!!.authorityToken,w.snapshot().revision,plan.planFingerprint,
            "Cambiar nombre declarado a X09 Verified",PreparedPlanEnvelopeCodec.encode(plan),IdempotencyKey("x09-seed")))
        assertTrue(result is WorkspacePlanReviewWriteResult.Saved)
        return chat
    }
    private fun collapsed() {
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Revisar cambios").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        compose.onAllNodesWithText("Confirmar propuesta").assertCountEquals(0)
        compose.onAllNodesWithText("Descartar").assertCountEquals(0)
        compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        compose.onAllNodesWithText("Cambiar nombre declarado a X09 Verified").assertCountEquals(0)
    }
    private fun expanded() {
        compose.onNodeWithText("Revisar cambios").performClick()
        compose.onNodeWithText("Confirmar propuesta").assertExists()
        compose.onNodeWithText("Cambiar nombre declarado a X09 Verified").assertExists()
    }
    private fun withFixture(block:(ActivityScenario<FaroV5X03U02IsolatedAiActivity>,FaroV5QuarantinedV2ChatHostBinding,ConversationId)->Unit) {
        assertFalse(root.exists())
        val s2=File(context.noBackupFilesDir,"faro-s2-f1-debug-only")
        val s2Before=if(s2.exists()) s2.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() } else emptyMap()
        val owner=FaroV5TrustedAssistantControlOwner.get(context)
        val key=("test_x09_"+UUID.randomUUID().toString().replace("-","")).toByteArray()
        try {
            assertTrue(runBlocking { owner.enableWithCredential(key,owner.read().revision) } is FaroV5TrustedAssistantControlOwner.Change.Saved)
            ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { s ->
                val first=open(s);val chat=seed(first)
                // The actual Activity must discover the durable candidate on reopen.
                s.moveToState(Lifecycle.State.STARTED);awaitLease();s.moveToState(Lifecycle.State.RESUMED)
                val host=open(s)
                collapsed()
                block(s,host,chat)
            }
        } finally {
            awaitLease();key.fill(0)
            runBlocking { val now=owner.read();if(now.remoteReasoningEnabled) owner.disable(now.revision) }
            val s2After=if(s2.exists()) s2.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() } else emptyMap()
            assertEquals(s2Before,s2After)
            assertTrue(root.deleteRecursively())
        }
    }

    @Test fun canonicalReviewRequiresExplicitOpenBeforeApproveAndLeavesRealityUnchanged() = withFixture { _,host,chat ->
        val before=graph(host).reality.read()
        val pending=runBlocking { host.reviewPort.reviews(chat) }.single()
        assertEquals(ConversationPlanReviewState.PENDING,pending.state)
        expanded()
        val click=compose.onNodeWithText("Confirmar propuesta").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        InstrumentationRegistry.getInstrumentation().runOnMainSync { click();click() }
        compose.waitUntil(30_000) { runBlocking { host.reviewPort.reviews(chat) }.single().state==ConversationPlanReviewState.APPROVED }
        val approved=runBlocking { host.reviewPort.reviews(chat) }.single()
        assertEquals(pending.revision+1,approved.revision)
        assertNull(approved.receipt);assertNull(approved.transactionId)
        assertEquals(before,graph(host).reality.read())
        compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        compose.onAllNodesWithText("Confirmar propuesta").assertCountEquals(0)
        // Re-read SQLite after closing the UI in withFixture's caller cleanup.
        assertEquals(approved,runBlocking { requireNotNull(graph(host).workspace).reviews(chat) }.single())
    }

    @Test fun reviewRevokesOnLifecycleOrStaleContextWithoutApply() = withFixture { s,host,chat ->
        val before=graph(host).reality.read()
        expanded()
        val staleClick=compose.onNodeWithText("Confirmar propuesta").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        s.moveToState(Lifecycle.State.STARTED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { staleClick() }
        assertFalse(host.verifyQuarantined());awaitLease()
        s.moveToState(Lifecycle.State.RESUMED);s.recreate()
        val current=open(s);collapsed()
        val pending=runBlocking { current.reviewPort.reviews(chat) }.single()
        assertEquals(ConversationPlanReviewState.PENDING,pending.state)
        expanded()
        val captured=compose.onNodeWithText("Confirmar propuesta").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        // A canonical owner transition invalidates the context already visible to UI.
        assertTrue(runBlocking { current.reviewPort.decide(ConversationPlanReviewIntent.Discard(pending.context()),IdempotencyKey("x09-external-discard")) } is ConversationReviewDecision.Saved)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { captured() }
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("La propuesta cambió. Volvé a revisarla.").fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false) }
        val discarded=runBlocking { current.reviewPort.reviews(chat) }.single()
        assertEquals(ConversationPlanReviewState.DISCARDED,discarded.state)
        assertEquals(pending.revision+1,discarded.revision)
        assertNull(discarded.receipt);assertNull(discarded.transactionId)
        assertEquals(before,graph(current).reality.read())
        compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
}
