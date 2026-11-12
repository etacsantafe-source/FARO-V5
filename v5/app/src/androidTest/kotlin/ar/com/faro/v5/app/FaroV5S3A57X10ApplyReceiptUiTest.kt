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
class FaroV5S3A57X10ApplyReceiptUiTest {
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
    private data class Seed(val chat: ConversationId, val plan: PreparedPlan)
    private fun seed(h: FaroV5QuarantinedV2ChatHostBinding): Seed {
        val g=graph(h);val w=requireNotNull(g.workspace)
        val chat=runBlocking { h.workspaceReadPort.readSnapshot().activeConversationId }!!
        val plan=PreparedPlan.of(ApplyPlanNode(PlanNodeId("profile"),PreparedOperation(
            OperationContractDescriptor(ProfileOperations.UPDATE_DECLARED,ProfileOperations.CAPABILITY_ID,
                ProfileOperations.EXECUTOR_ID,ExecutionProfile.REALITY_MUTATION,ContractVersion("1"),
                ContractFingerprint("profile-update-declared-v1"),ContractExposure.BINDABLE),
            ProfileDeclaredUpdatePayload.payload(ProfileDeclaredUpdate(DeclaredDisplayName.of("X10 Verified"))),
            DependencyStampSet.of(g.reality.read().dependencyStamp()),IdempotencyKey("x10-op")),emptySet()))
        val result=w.createPlanReview(WorkspacePlanReviewCreateRequest(chat,"x10-review",
            runBlocking { w.read(chat) }!!.authorityToken,w.snapshot().revision,plan.planFingerprint,
            "Cambiar nombre declarado a X10 Verified",PreparedPlanEnvelopeCodec.encode(plan),IdempotencyKey("x10-seed")))
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
        compose.onAllNodesWithText("Cambiar nombre declarado a X10 Verified").assertCountEquals(0)
    }
    private fun expanded() {
        compose.onNodeWithText("Revisar cambios").performClick()
        compose.onNodeWithText("Confirmar propuesta").assertExists()
        compose.onNodeWithText("Cambiar nombre declarado a X10 Verified").assertExists()
    }
    private fun withFixture(block:(ActivityScenario<FaroV5X03U02IsolatedAiActivity>,FaroV5QuarantinedV2ChatHostBinding,Seed)->Unit) {
        assertFalse(root.exists())
        val s2=File(context.noBackupFilesDir,"faro-s2-f1-debug-only")
        val s2Before=if(s2.exists()) s2.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() } else emptyMap()
        val owner=FaroV5TrustedAssistantControlOwner.get(context)
        val key=("test_x10_"+UUID.randomUUID().toString().replace("-","")).toByteArray()
        try {
            assertTrue(runBlocking { owner.enableWithCredential(key,owner.read().revision) } is FaroV5TrustedAssistantControlOwner.Change.Saved)
            ActivityScenario.launch(FaroV5X03U02IsolatedAiActivity::class.java).use { s ->
                val first=open(s);val seed=seed(first)
                // The actual Activity must discover the durable candidate on reopen.
                s.moveToState(Lifecycle.State.STARTED);awaitLease();s.moveToState(Lifecycle.State.RESUMED)
                val host=open(s)
                collapsed()
                block(s,host,seed)
            }
        } finally {
            awaitLease();key.fill(0)
            runBlocking { val now=owner.read();if(now.remoteReasoningEnabled) owner.disable(now.revision) }
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
    private fun reopen(s: ActivityScenario<FaroV5X03U02IsolatedAiActivity>): FaroV5QuarantinedV2ChatHostBinding {
        s.moveToState(Lifecycle.State.STARTED);awaitLease();s.moveToState(Lifecycle.State.RESUMED)
        return open(s)
    }
    private fun verifyResult() {
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Revisar estado").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        compose.onAllNodesWithText("Verificar resultado").assertCountEquals(0)
        compose.onNodeWithText("Revisar estado").performClick()
        compose.onNodeWithText("Verificar resultado").performClick()
    }

    @Test fun explicitApprovedApplyCommitsOnceAndReceiptSurvivesReopen() = withFixture { s,host,seed ->
        val chat=seed.chat;val r=graph(host).reality
        val pending=runBlocking { host.reviewPort.reviews(chat) }.single()
        val p=port(s)
        assertTrue(runBlocking { p.request(pending.context()) } is ConversationApplyHostOutcome.Denied)
        assertEquals(0L,r.read().revision);assertTrue(r.backup.snapshot().committedReceipts.isEmpty())
        val approved=approve(host,chat)
        assertEquals(0L,r.read().revision)
        compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        compose.onNodeWithText("Ver propuesta aprobada").performClick()
        val click=compose.onNodeWithText("Aplicar cambios").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        InstrumentationRegistry.getInstrumentation().runOnMainSync { click();click() }
        compose.waitUntil(30_000) { runBlocking { host.reviewPort.reviews(chat) }.single().state==ConversationPlanReviewState.COMMITTED }
        val committed=runBlocking { host.reviewPort.reviews(chat) }.single()
        val receipt=requireNotNull(committed.receipt)
        assertEquals(receipt,r.executionReceiptLookup.find(requireNotNull(committed.transactionId)))
        assertEquals(approved.planFingerprint,receipt.planFingerprint)
        assertEquals(1L,r.read().revision);assertEquals("X10 Verified",r.read().displayName!!.value)
        assertEquals(1,r.backup.snapshot().committedReceipts.size)
        val resumed=reopen(s)
        val restored=runBlocking { resumed.reviewPort.reviews(chat) }.single()
        assertEquals(committed,restored)
        assertEquals(receipt,graph(resumed).reality.executionReceiptLookup.find(receipt.transactionId))
        assertTrue(runBlocking { port(s).request(restored.context()) } is ConversationApplyHostOutcome.Denied)
        assertEquals(1L,graph(resumed).reality.read().revision)
        assertEquals(1,graph(resumed).reality.backup.snapshot().committedReceipts.size)
        compose.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test fun unknownNeverReexecutesAndCommittedReceiptReconcilesAfterReopen() = withFixture { s,host,seed ->
        val chat=seed.chat;val g=graph(host);val r=g.reality;val w=requireNotNull(g.workspace)
        val approved=approve(host,chat)
        val p=port(s)
        assertTrue(runBlocking { p.request(approved.context().copy(expectedRevision=approved.revision+10)) } is ConversationApplyHostOutcome.Denied)
        assertEquals(0L,r.read().revision)
        val identity=r.applyAttemptIdentities.resolve(seed.plan)
        val begun=w.advance(ConversationPlanReviewIntent.BeginApply(approved.context(),identity),IdempotencyKey("x10-fixture-begin"),r.applyAttemptLookup,r.executionReceiptLookup) as ConversationReviewDecision.Saved
        val uncertain=w.advance(ConversationPlanReviewIntent.MarkRecovering(begun.review.context()),IdempotencyKey("x10-fixture-unknown"),r.applyAttemptLookup,r.executionReceiptLookup) as ConversationReviewDecision.Saved
        assertEquals(ConversationPlanReviewState.RECOVERING_UNKNOWN,uncertain.review.state)
        s.moveToState(Lifecycle.State.STARTED)
        assertTrue(runBlocking { p.request(approved.context()) } is ConversationApplyHostOutcome.Denied)
        awaitLease();s.moveToState(Lifecycle.State.RESUMED)
        val current=open(s)
        verifyResult()
        compose.waitUntil(30_000) { runCatching {
            compose.onAllNodesWithText("Resultado pendiente de verificar. Reabrí FARO antes de reintentar.").fetchSemanticsNodes().size==1
        }.getOrDefault(false) }
        val cr=graph(current).reality
        assertEquals(0L,cr.read().revision)
        assertTrue(cr.backup.snapshot().committedReceipts.isEmpty())
        assertEquals(ApplyAttemptJournalState.PREPARED,cr.applyAttemptLookup.find(identity.transactionId)!!.state)
        assertEquals(ConversationPlanReviewState.RECOVERING_UNKNOWN,runBlocking { current.reviewPort.reviews(chat) }.single().state)
        // Test-only crash frontier: commit Reality once, deliberately omit Workspace delivery.
        val binding=ApplyBindingResolutionGate(listOf(ProfileUpdateDeclaredBinding(cr))).resolve(seed.plan) as ApplyBindingResolution.Resolved
        val execution=ApplyTransactionExecutor(cr,cr).execute(seed.plan,identity,binding) as ApplyTransactionExecution.Completed
        assertEquals(1L,cr.read().revision)
        val after=reopen(s)
        verifyResult()
        compose.waitUntil(30_000) { runBlocking { after.reviewPort.reviews(chat) }.single().state==ConversationPlanReviewState.COMMITTED }
        val settled=runBlocking { after.reviewPort.reviews(chat) }.single()
        assertEquals(execution.receipt,settled.receipt)
        assertEquals(identity.transactionId,settled.transactionId)
        assertEquals(1L,graph(after).reality.read().revision)
        assertEquals(1,graph(after).reality.backup.snapshot().committedReceipts.size)
        assertEquals(execution.receipt,graph(after).reality.executionReceiptLookup.find(identity.transactionId))
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
}
