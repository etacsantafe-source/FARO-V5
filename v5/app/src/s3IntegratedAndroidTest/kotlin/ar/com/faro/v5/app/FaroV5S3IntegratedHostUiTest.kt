package ar.com.faro.v5.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.surface.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Predicate
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** New integrated sandbox delta only. Synthetic gateway exists ONLY in this test APK. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3IntegratedHostUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val root get() = context.noBackupFilesDir.toPath().resolve("faro-s3-integrated-dev-root")
    private fun views(v: View): List<View> = buildList {
        add(v); if (v is ViewGroup) for (i in 0 until v.childCount) addAll(views(v.getChildAt(i)))
    }
    private fun field(o: Any, name: String): Any? = o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o)
    private fun session(s: ActivityScenario<FaroV5S3IntegratedDevActivity>): FaroV5V2CoreDevAuthority.Session {
        var found: FaroV5V2CoreDevAuthority.Session? = null
        s.onActivity { a -> found = FaroV5V2CoreDevActivity::class.java.getDeclaredField("session")
            .apply { isAccessible = true }.get(a) as FaroV5V2CoreDevAuthority.Session? }
        return checkNotNull(found)
    }
    private fun graph(s: FaroV5V2CoreDevAuthority.Session) = field(s, "graph") as FaroV5GenerationStoreGraph
    private fun click(s: ActivityScenario<FaroV5S3IntegratedDevActivity>, text: String) {
        s.onActivity { a -> assertTrue(views(a.window.decorView).filterIsInstance<Button>()
            .single { it.text.toString() == text }.performClick()) }
    }
    private fun open(s: ActivityScenario<FaroV5S3IntegratedDevActivity>, text: String = "Abrir Chat local de desarrollo"): FaroV5V2CoreDevAuthority.Session {
        click(s, text)
        val end = SystemClock.elapsedRealtime() + 60_000L
        var result: FaroV5V2CoreDevAuthority.Session? = null
        while (result == null && SystemClock.elapsedRealtime() < end) {
            result = runCatching { session(s) }.getOrNull()
            if (result == null) SystemClock.sleep(50L)
        }
        return checkNotNull(result) { "Actual native Session not opened" }
    }
    private fun fixture(font200: Boolean = false, block: (ActivityScenario<FaroV5S3IntegratedDevActivity>) -> Unit) {
        check(android.os.Build.VERSION.SDK_INT == 35)
        check(android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.MODEL.contains("sdk", true))
        assertEquals("ar.com.faro.alpha.s3integrateddev", context.packageName)
        assertFalse("Never delete existing app data to admit a test", root.toFile().exists())
        // Canonical disposable test setup only; no silent upgrade in the application.
        FaroV5V2CoreDevAuthority(root).open().close()
        FaroV5V2CoreDevAuthority(root).upgradePlanning8AfterExplicitForegroundRequest { true }.close()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val previousScale = if (font200) shell("settings get system font_scale") else ""
        if (font200) shell("settings put system font_scale 2.0")
        try { ActivityScenario.launch(FaroV5S3IntegratedDevActivity::class.java).use(block) }
        finally {
            if (font200) shell("settings put system font_scale " + previousScale.ifBlank { "1.0" })
            val occupied = FaroV5S3IntegratedDevActivity::class.java.getDeclaredField("occupied").apply { isAccessible = true }
            val end = SystemClock.elapsedRealtime() + 30_000L
            while ((occupied.get(null) as java.util.concurrent.atomic.AtomicBoolean).get() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50L)
            assertFalse((occupied.get(null) as java.util.concurrent.atomic.AtomicBoolean).get())
            assertTrue(root.toFile().deleteRecursively())
        }
    }
    private fun waitText(text: String) = compose.waitUntil(30_000L) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
    private fun send(text: String) {
        waitText("Enviar")
        val editors = compose.onAllNodes(hasSetTextAction())
        editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(text)
        compose.onNodeWithText("Enviar").assertIsEnabled().performClick()
        compose.waitUntil(30_000L) { runCatching {
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }.getOrDefault(false) }
        compose.waitForIdle()
    }
    private fun synthetic(session: FaroV5V2CoreDevAuthority.Session, gateway: ModelGateway): FaroV5DevAssistantSessionPorts {
        return FaroV5DevAssistantSessionPorts.open(graph(session), field(session, "gate") as MaterialWriteGate,
            field(session, "current") as Supplier<String>, field(session, "ready") as BooleanSupplier,
            FaroV5TrustedAssistantComponents(gateway, Supplier { ModelProfileId("integrated-local-test") }, Predicate { true }))
    }
    private fun mount(s: ActivityScenario<FaroV5S3IntegratedDevActivity>, session: FaroV5V2CoreDevAuthority.Session, ports: FaroV5DevAssistantSessionPorts) {
        s.onActivity { a -> FaroV5SurfaceHost.install(a,
            workspaceReadPort = session.workspaceReadPort, timelineReadPort = session.timelineReadPort,
            selectionPort = session.selectionPort, composerPort = session.composerPort,
            reviewPort = ports.reviewPort, applyRequestPort = ports.applyRequestPort,
            assistantTurnPort = ports.assistantTurnPort,
            assistantRequestMode = FaroV5AssistantRequestMode.EXPLICIT_CONSULT,
            reviewDisclosureMode = FaroV5ReviewDisclosureMode.EXPLICIT_OPEN) }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
    private fun response() = ReasoningResult.Success(ModelBindingId("local-test-only"), CanonicalPayload.fromEncoded(
        "text/plain", "{\"type\":\"profile.update_declared\",\"display_name\":\"S3 Integrada\"}".toByteArray()))

    @Test fun identityFreeAdmissionAndSettingsAreSecretFree() = fixture(font200 = true) { s ->
        s.onActivity { a -> assertTrue(a.resources.configuration.fontScale >= 1.99f) }
        instrumentation.waitForIdleSync()
        checkNotNull(instrumentation.uiAutomation.takeScreenshot()).useBitmap { image ->
            File(context.filesDir, "s3-integrated-font200.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        s.onActivity { a -> assertFalse(views(a.window.decorView).filterIsInstance<CheckBox>().single().isChecked) }
        click(s, "Abrir Chat IA integrado")
        assertTrue(runCatching { session(s) }.isFailure)
        // A human checkbox alone never supplies a credential or consent.
        s.onActivity { a -> views(a.window.decorView).filterIsInstance<CheckBox>().single().isChecked = true }
        click(s, "Abrir Chat IA integrado")
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var failed = false
        while (!failed && SystemClock.elapsedRealtime() < deadline) {
            s.onActivity { a -> failed = views(a.window.decorView).filterIsInstance<android.widget.TextView>()
                .any { it.text.toString().startsWith("No se abrió:") } }
            if (!failed) SystemClock.sleep(50L)
        }
        assertTrue(failed)
        assertTrue(runCatching { session(s) }.isFailure)
        ActivityScenario.launch<FaroV5AiSettingsActivity>(Intent(context, FaroV5AiSettingsActivity::class.java)).use { settings ->
            settings.onActivity { a ->
                val all = views(a.window.decorView)
                assertTrue(all.filterIsInstance<EditText>().single().text.isEmpty())
                assertFalse(all.filterIsInstance<Button>().any { it.visibility == View.VISIBLE && it.text.contains("Probar conexión") })
            }
        }
        assertFalse(FaroV5TrustedAssistantControlOwner.get(context).let { runBlocking { it.read() }.remoteReasoningEnabled })
    }

    @Test fun nativeSourcesShareCanonicalWorkspaceRealityAndGeneration() = fixture { s ->
        val current = open(s)
        send("SameRootIntegrated")
        val w = checkNotNull(graph(current).workspace)
        val chat = checkNotNull(runBlocking { current.workspaceReadPort.readSnapshot() }.activeConversationId)
        val message = checkNotNull(runBlocking { w.readTimeline(chat) }).messages.single { it.text == "SameRootIntegrated" }
        val actions = current.bookmarkChatActions(context.applicationContext as Application) { true }
        val review = checkNotNull(runBlocking { actions.prepare(chat, message.id, FaroV5BookmarkChatAction.SAVE_OR_REMOVE) })
        runBlocking { actions.confirm(review) }
        val reader = checkNotNull(current.openBookmarksAfterTrustedForegroundRequest(context.applicationContext as Application) { true })
        assertEquals(1, checkNotNull(runBlocking { reader.readAuthorized() }).items.size)
        assertEquals(1, runBlocking { current.searchReadPort.search("SameRootIntegrated") }.hits.size)
        val pinned = checkNotNull(FileActiveGenerationPointer(root).read())
        for ((button, binding) in listOf(
            "Abrir Chat y Actividad local" to "historyBinding",
            "Consultar avisos registrados (DEV)" to "avisosAuthority",
            "Consultar vencimientos registrados (DEV)" to "pendingBinding",
            "Consultar Guardados locales (DEV)" to "bookmarkBinding")) {
            s.recreate()
            val next = open(s, button)
            assertEquals(pinned, FileActiveGenerationPointer(root).read())
            assertEquals(pinned.generationId.value, graph(next).generationId)
            assertNotNull(field(next, binding))
            assertEquals(message, checkNotNull(runBlocking { graph(next).workspace!!.readTimeline(chat) }).messages.single { it.id == message.id })
        }
        assertFalse(context.noBackupFilesDir.resolve("faro-v2-core-dev-root").exists())
        assertFalse(context.noBackupFilesDir.resolve("faro-s3-x03-u02-isolated-debug-only").exists())
    }

    @Test fun syntheticConsultReviewApproveApplyCommitsOneSameRootReceipt() = fixture { s ->
        val current = open(s)
        val calls = AtomicInteger()
        synthetic(current, ModelGateway { _, _ -> calls.incrementAndGet(); response() }).use { ports ->
            mount(s, current, ports)
            send("Cambiar nombre declarado de prueba")
            assertEquals(0, calls.get())
            compose.onNodeWithText("Consultar FARO").performClick()
            waitText("Revisar cambios")
            val chat = checkNotNull(runBlocking { current.workspaceReadPort.readSnapshot() }.activeConversationId)
            val pending = runBlocking { ports.reviewPort.reviews(chat) }.single()
            assertTrue(runBlocking { ports.applyRequestPort.request(pending.context()) } is ConversationApplyHostOutcome.Denied)
            assertEquals(0L, graph(current).reality.read().revision)
            compose.onNodeWithText("Revisar cambios").performClick()
            compose.onNodeWithText("Confirmar propuesta").performClick()
            waitText("Ver propuesta aprobada")
            assertEquals(0L, graph(current).reality.read().revision)
            compose.onNodeWithText("Ver propuesta aprobada").performClick()
            compose.onNodeWithText("Aplicar cambios").performClick()
            compose.waitUntil(30_000L) { runBlocking { ports.reviewPort.reviews(chat) }.single().state == ConversationPlanReviewState.COMMITTED }
            val committed = runBlocking { ports.reviewPort.reviews(chat) }.single()
            val receipt = checkNotNull(committed.receipt)
            assertEquals(receipt, graph(current).reality.executionReceiptLookup.find(receipt.transactionId))
            assertEquals(1, graph(current).reality.backup.snapshot().committedReceipts.size)
            assertEquals("S3 Integrada", graph(current).reality.read().displayName!!.value)
            val history = checkNotNull(current.openActivityAfterTrustedForegroundRequest(context.applicationContext as Application) { true })
            assertEquals(1, checkNotNull(runBlocking { history.readActivity() }).entries.count { it.kind == FaroV5ActivityEventKind.APPLY_COMMITTED })
            assertTrue(runBlocking { ports.applyRequestPort.request(committed.context()) } is ConversationApplyHostOutcome.Denied)
            assertEquals(1, calls.get())
            s.recreate()
            assertNull(runBlocking { history.readActivity() })
            val reopened = open(s)
            val restored = runBlocking { checkNotNull(reopened.assistantPorts).reviewPort.reviews(chat) }.single()
            assertEquals(committed, restored)
            assertEquals(1, graph(reopened).reality.backup.snapshot().committedReceipts.size)
            assertEquals(1, calls.get())
        }
    }

    @Test fun pauseLateReplyRecoveryAndAbaNeverReplayOrReauthorize() = fixture { s ->
        val current = open(s)
        val calls = AtomicInteger()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val returned = CountDownLatch(1)
        synthetic(current, ModelGateway { _, _ ->
            calls.incrementAndGet(); entered.countDown()
            check(release.await(20, TimeUnit.SECONDS)); returned.countDown(); response()
        }).use { ports ->
            mount(s, current, ports); send("InterruptedSyntheticTurn")
            val chat = checkNotNull(runBlocking { current.workspaceReadPort.readSnapshot() }.activeConversationId)
            compose.onNodeWithText("Consultar FARO").performClick()
            assertTrue(entered.await(20, TimeUnit.SECONDS))
            s.moveToState(Lifecycle.State.STARTED)
            release.countDown(); assertTrue(returned.await(20, TimeUnit.SECONDS))
            assertTrue(runCatching { runBlocking { current.workspaceReadPort.readSnapshot() } }.isFailure)
            s.moveToState(Lifecycle.State.RESUMED)
            // Resume recreates admission UI, with no automatic root opening.
            val end = SystemClock.elapsedRealtime() + 15_000L
            while (runCatching { session(s) }.isSuccess && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50L)
            val reopened = open(s)
            assertEquals(1, checkNotNull(reopened.assistantPorts).recoveredInterruptedTurns)
            val timeline = checkNotNull(runBlocking { reopened.timelineReadPort.readTimeline(chat) })
            assertEquals(ConversationMessageState.FAILED, timeline.messages.single { it.role == ConversationMessageRole.FARO }.state)
            assertTrue(runBlocking { reopened.assistantPorts!!.reviewPort.reviews(chat) }.isEmpty())
            assertEquals(1, calls.get())
            val pointer = FileActiveGenerationPointer(root); val original = checkNotNull(pointer.read())
            val other = StorageGenerationId("s3-integrated-aba")
            assertNotNull(pointer.compareAndSet(original.generationId, other))
            assertNotNull(pointer.compareAndSet(other, original.generationId))
            assertNull(reopened.openActivityAfterTrustedForegroundRequest(context.applicationContext as Application) { true })
            assertNull(reopened.openPendingAfterTrustedForegroundRequest(context.applicationContext as Application) { true })
            assertTrue(runCatching { runBlocking { reopened.workspaceReadPort.readSnapshot() } }.isFailure)
        }
    }
}
