package ar.com.faro.v5.app

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.generation.FaroV5ClosedHousehold12DevMaintenance
import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.HouseholdManualPurchaseReview
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold12StagedFilePort
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaroV5W05A1DevAcceptanceAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    // Bundled SQLite's NOFOLLOW rejects Android's /data/user/0 alias in the parent path.
    private val root get() = context.noBackupFilesDir.toPath().toRealPath().resolve("faro-v2-core-dev-root")
    private val catalog = BuiltInContractCatalog.create()
    private val gate = SingleProcessMaterialWriteGate()
    private val principal get() = FaroV5W05StockPanelPolicy.principal(context.packageName, context.applicationInfo.uid)
    private val chat get() = ConversationId("stock-dev-" + principal.take(32))
    private val purchaseRef = "purchase-1"

    @Before
    fun resetDisposableEmulatorRoot() {
        assertEquals(35, Build.VERSION.SDK_INT)
        assertTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assertEquals("ar.com.faro.alpha.v2coredev", context.packageName)
        assertTrue((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        assertEquals(context.noBackupFilesDir.canonicalFile, root.toFile().canonicalFile.parentFile)
        assertFalse(Files.isSymbolicLink(root))
        if (Files.exists(root)) assertTrue(root.toFile().deleteRecursively())
    }

    @Test
    fun continuesNativeStockHistoryAcrossAuthenticatedCutoverAtFont200() {
        val active12 = seedSql12()
        ActivityScenario.launch(FaroV5V2CoreDevActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(2f, it.resources.configuration.fontScale, 0.01f) }
            openReceive()
            click("Consultar compra")
            awaitText("Recibido 0 · Pendiente 10")
            input("Cantidad física recibida", "7")
            click("Revisar recepción")
            click("Aprobar revisión")
            awaitText("Confirmar y aplicar")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertFalse(nodes().any { it.text?.toString() == "Confirmar y aplicar" })
            openReceive()
            click("Consultar compra")
            awaitText("Recibido 0 · Pendiente 10")
            receive("7")
            click("Consultar compra")
            awaitText("Recibido 7 · Pendiente 3")
            receive("3")
            click("Consultar compra")
            awaitText("Recibido 10 · Pendiente 0")
        }
        val receipts = FaroV5GenerationStoreGraph.openActiveHousehold12DevForPurchaseHost(
            root, active12, gate, CanonicalPreparedPlanEnvelopeVerifier, { true },
        ).use {
            val rows = it.snapshotPurchaseReceipts().receipts
            assertEquals(2L, rows.revision)
            assertEquals(2, rows.receipts.size)
            rows.receipts.associate { row -> row.quantityThousandths to row.receiptId }
        }
        val receipt = receipts.getValue(7000)

        val active13 = transition(active12)
        ActivityScenario.launch(FaroV5V2CoreDevActivity::class.java).use {
            openReceive()
            click("Consultar compra")
            awaitText("No disponible.")
            click("Historial y deshacer · SQL13")
            input("Referencia de recepción física", receipt)
            click("Consultar historial de revisiones")
            click("Revisar deshacer · Recibir 7 UNIT de yerba")
            click("Aprobar revisión")
            click("Confirmar y aplicar")
            awaitText("Confirmado. Recibo")
        }
        S4W05StockUndoDevApplyHost(
            root, active13, gate, catalog, principal,
            1L, { 1L }, { true }, { true }, { actor, operation, _ ->
                actor == principal && operation == OperationId("undo.execute")
            }, { 6000L },
        ).use { host ->
            assertNull(host.position(receipt))
            assertEquals(3_000L, checkNotNull(host.position(receipts.getValue(3000))).netReceivedThousandths)
            val reviews = runBlocking { host.reviewDecisions.reviews(chat) }
            assertEquals(2, reviews.count { it.reviewId.startsWith("w05:receive:") && it.receipt != null })
            assertEquals(1, reviews.count { it.reviewId.startsWith("w05:stock-undo:") && it.receipt != null })
        }
    }

    private fun openReceive() {
        click("Abrir recepción de stock (DEV)")
        click("Compra y recepción · SQL12")
        input("Referencia de compra", purchaseRef)
    }

    private fun receive(quantity: String) {
        input("Cantidad física recibida", quantity)
        click("Revisar recepción")
        click("Aprobar revisión")
        click("Confirmar y aplicar")
        awaitText("Confirmado. Recibo")
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            nodes += node
            repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
        }
        visit(root)
        return nodes
    }

    private fun seek(label: String, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var scrollForward = true
        val until = SystemClock.elapsedRealtime() + 25_000L
        while (SystemClock.elapsedRealtime() < until) {
            val current = nodes()
            current.firstOrNull { it.isVisibleToUser && match(it) }?.let { return it }
            val scroll = current.lastOrNull { it.isScrollable }
            if (scroll != null) {
                val action = if (scrollForward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (!scroll.performAction(action)) scrollForward = !scrollForward
            }
            SystemClock.sleep(160L)
        }
        throw AssertionError("Missing actionable accessibility node: $label")
    }

    private fun awaitText(prefix: String) = seek(prefix) {
        it.text?.toString()?.lineSequence()?.any { line -> line.startsWith(prefix, ignoreCase = true) } == true
    }

    private fun click(label: String) {
        val button = seek(label) {
            it.isEnabled && it.isClickable && it.text?.toString().equals(label, ignoreCase = true)
        }
        assertTrue(label, button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun input(label: String, value: String) {
        val field = seek(label) { it.isEditable && it.hintText?.toString() == label }
        assertTrue(label, field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }

    private fun transition(origin: ActiveGenerationPointer): ActiveGenerationPointer {
        val operation = MaintenanceOperationId("global-a1-sql13-cutover")
        val selections = FileActiveGenerationPointer(root)
        fun descriptor(id: String) = StorageGenerationDescriptor(StorageGenerationId(id), "global-a1-dev", 20L)
        val stages = FaroV5ClosedHousehold12DevMaintenance.Stages(
            descriptor("global-a1-copy12"), descriptor("global-a1-bridge13"), descriptor("global-a1-active13"),
        )
        val key = "global-a1-isolated-fixture".toCharArray()
        val selected = FaroV5ClosedHousehold12DevMaintenance.acquire(
            root, gate, CanonicalPreparedPlanEnvelopeVerifier,
        ) { true }.use { owner ->
            val prepared = owner.prepare(operation, origin, stages, key) { true }
            assertEquals(origin, selections.read())
            assertEquals(BootstrapPhase.VALIDATED, FileBootstrapJournal(root).read(operation)?.phase)
            assertNull(owner.start(prepared, key) { false })
            assertEquals(origin, selections.read())
            checkNotNull(owner.start(prepared, key) { true })
        }
        key.fill('\u0000')
        assertEquals(ActiveGenerationPointer(origin.sequence + 1L, stages.v10Destination.generationId), selected)
        assertEquals(selected, selections.read())
        assertEquals(BootstrapPhase.COMPLETED, FileBootstrapJournal(root).read(operation)?.phase)
        return selected
    }

    private fun seedSql12(): ActiveGenerationPointer {
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("stock-ui-origin")))
        val seed = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("stock-ui-sql12"), "stock-ui-fixture", 1L),
        )
        val stage = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("stock-ui-finance9"), seed, origin, "a".repeat(64), 2L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
        CommercialEvidenceStore(
            fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        val active = selectInitialSql12(origin, stage)
        S4W05ManualPurchaseDevApplyHost(
            root, active, gate, catalog, principal, "fixture-epoch", { "fixture-epoch" },
            { true }, { true }, { actor, operation, target ->
                actor == principal && operation == HouseholdManualPurchaseReview.OPERATION && target == purchaseRef
            }, { 1234L },
        ).use { host ->
            val selection = runBlocking { host.chatSelection.createAndSelect(chat, 1L, IdempotencyKey("stock-ui-select")) }
            assertTrue(selection is ConversationSelectionResult.Saved)
            val pending = checkNotNull(host.submit(chat, "stock-ui-purchase", purchaseRef, "yerba",
                HouseholdNeedQuantity(10_000L, HouseholdNeedUnit.UNIT)))
            val approval = runBlocking {
                host.reviewDecisions.decide(ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("stock-ui-approve"))
            } as ConversationReviewDecision.Saved
            assertTrue(host.executeAfterExplicitConfirmation(approval.review.context()) is ReviewedConversationApplyResult.Committed)
        }
        return active
    }

    /** Establish the initial isolated SQL12 fixture. SQL13 selection uses authenticated maintenance. */
    private fun selectInitialSql12(origin: ActiveGenerationPointer,
                                      target: StagedStorageGeneration): ActiveGenerationPointer {
        val active = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(origin.generationId, target.descriptor.generationId))
        val operation = MaintenanceOperationId("stock-ui-selection-12")
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE, origin.generationId, active.generationId,
            BootstrapPhase.COMPLETED, materialWritesAccepted = true,
        ))
        FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, active.generationId)
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use {
            it.writeUTF("FARO_SQL12_EVIDENCE2_CUTOVER_V1")
            it.writeUTF(operation.value)
            it.writeLong(origin.sequence)
            it.writeUTF(origin.generationId.value)
            it.writeUTF(active.generationId.value)
            it.writeUTF(origin.generationId.value)
            repeat(3) { _ -> it.writeUTF("a".repeat(64)) }
        }
        val payload = stream.toByteArray()
        Files.write(root.resolve("bootstrap/sql12-evidence2-cutover.ticket"),
            payload + MessageDigest.getInstance("SHA-256").digest(payload))
        return active
    }
}
