package ar.com.faro.v5.app

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.apply.ReviewedConversationApplyResult
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.owner.household.api.HouseholdManualPurchaseReview
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdStockReceiveReview
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRow
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking

internal object FaroV5W05StockPanelPolicy {
    fun permits(packageName: String, debuggable: Boolean, productive: Boolean): Boolean =
        debuggable && !productive && (packageName == "ar.com.faro.alpha.v2coredev" ||
            packageName == "ar.com.faro.g15isolated.v2coredev")

    fun principal(packageName: String, uid: Int) = CanonicalHashBuilder.domain("w05.stock.panel.principal")
        .string("package", packageName).string("uid", uid.toString()).sha256Hex()

    private val decimalQuantity = Regex("[0-9]{1,7}([.,][0-9]{1,3})?")

    fun quantity(input: String): Long? {
        val value = input.trim()
        if (!decimalQuantity.matches(value)) return null
        return value.replace(',', '.').toBigDecimal().movePointRight(3).longValueExact()
            .takeIf { it in 1L..Int.MAX_VALUE.toLong() }
    }
}

/** Separate foreground panel. Hosts are borrowed for one action at a time under the same pin. */
internal class FaroV5W05StockReceiveDevPanel(
    private val activity: ComponentActivity,
    private val root: Path,
    private val executor: Executor,
    private val foreground: () -> Boolean,
    private val onClosed: () -> Unit,
) : AutoCloseable {
    private val principal = FaroV5W05StockPanelPolicy.principal(activity.packageName, activity.applicationInfo.uid)
    private val purchasePrefix = "w05:purchase:" + hash("w05.purchase.principal", 32) + ":"
    private val receivePrefix = "w05:receive:" + hash("w05.stock.receive.principal", 32) + ":"
    private val undoPrefix = "w05:stock-undo:" + hash("w05.stock.undo.principal", 24) + ":"
    private val undoOperation = OperationId("undo.execute")
    private val chat = ConversationId("stock-dev-" + principal.take(32))
    private val alive = AtomicBoolean(true)
    private val epoch = nextEpoch.incrementAndGet()
    private val purchaseEpoch = UUID.randomUUID().toString()
    private val pointer = FileActiveGenerationPointer(root)
    private val gate = SingleProcessMaterialWriteGate()
    private val catalog = BuiltInContractCatalog.create()
    private val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
    private val status = TextView(activity).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        text = "Elegí la operación según la generación DEV activa."
    }
    private val controls = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var busy = false
    private var pinned: ActiveGenerationPointer? = null
    private val dialog: AlertDialog

    init {
        column.addView(status)
        column.button("Compra y recepción · SQL12") { choose(Mode.RECEIVE) }
        column.button("Historial y deshacer · SQL13") { choose(Mode.UNDO) }
        column.addView(controls)
        dialog = AlertDialog.Builder(activity).setTitle("Recepción de stock (DEV)")
            .setView(ScrollView(activity).apply { addView(column) })
            .setNegativeButton("Cerrar", null).create()
        dialog.setOnCancelListener { close() }
        dialog.setOnDismissListener { close() }
    }

    fun show() = dialog.show()

    private enum class Mode { RECEIVE, UNDO }

    private fun choose(mode: Mode) {
        if (busy) return
        controls.removeAllViews()
        val reference = controls.field(if (mode == Mode.RECEIVE) "Referencia de compra" else "Referencia de recepción física")
        val amount = if (mode == Mode.RECEIVE) controls.field("Cantidad física recibida", true) else null
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val reviewArea = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        controls.button(if (mode == Mode.RECEIVE) "Consultar compra" else "Consultar recepción") {
            val ref = reference.text.toString().trim()
            if (ref.isBlank()) return@button
            results.removeAllViews()
            reviewArea.removeAllViews()
            work({ pin ->
                if (mode == Mode.RECEIVE) readPurchase(pin, ref) else undoHost(pin, ref).use { host ->
                    val position = checkNotNull(host.position(ref))
                    "Compra ${position.purchaseReference} · ${position.stockItem}\n" +
                        "Recibido neto ${format(position.netReceivedThousandths)} · ${position.quantity.unit}"
                }
            }) { status.text = it }
        }
        if (amount != null) controls.button("Revisar recepción") {
            val ref = reference.text.toString().trim()
            val quantity = FaroV5W05StockPanelPolicy.quantity(amount.text.toString())
            if (ref.isBlank() || quantity == null) {
                status.text = "Ingresá una referencia y una cantidad positiva con hasta tres decimales."
                return@button
            }
            results.removeAllViews()
            reviewArea.removeAllViews()
            work({ pin ->
                val verified = verifiedPurchase(pin, ref)
                receiveHost(pin, ref).use { host ->
                    select(host.chatSelection, create = true)
                    val position = checkNotNull(host.position(ref))
                    check(position.purchase.revision == verified.row.revision)
                    checkNotNull(host.submit(chat, UUID.randomUUID().toString(), ref, position.purchase.stockItem,
                        HouseholdNeedQuantity(quantity, position.purchase.purchased.unit)))
                }
            }) { showReview(reviewArea, Mode.RECEIVE, ref, it) }
        }
        controls.button("Consultar historial de revisiones") {
            val ref = reference.text.toString().trim()
            if (ref.isBlank()) {
                status.text = "Ingresá la referencia para consultar el historial o reconciliar."
                return@button
            }
            results.removeAllViews()
            reviewArea.removeAllViews()
            work({ pin -> withMode(pin, mode, ref) { modes ->
                runBlocking { modes.reviews.reviews(chat) }.filter { matches(mode, it) }
            } }) { reviews ->
                status.text = if (reviews.isEmpty()) "Sin revisiones de stock en este panel." else "Historial de stock DEV"
                for (review in reviews) {
                    results.label("${review.reviewText}\n${review.state.name}")
                    if (mode == Mode.UNDO && review.reviewId.startsWith(receivePrefix) &&
                        review.state == ConversationPlanReviewState.COMMITTED) {
                        results.button("Revisar deshacer · ${review.reviewText}") {
                            reviewArea.removeAllViews()
                            work({ pin -> undoHost(pin, ref).use { host ->
                                select(host.chatSelection, create = false)
                                checkNotNull(host.submitUndo(review.context(), UUID.randomUUID().toString()))
                            } }) { showReview(reviewArea, mode, ref, it) }
                        }
                    }
                    if (!review.reviewId.startsWith(purchasePrefix) && (review.state == ConversationPlanReviewState.APPLYING ||
                        review.state == ConversationPlanReviewState.RECOVERING_UNKNOWN)) {
                        results.button("Reconciliar recibo · ${review.reviewText}") {
                            apply(reviewArea, mode, ref, review, reconcile = true)
                        }
                    }
                }
            }
        }
        controls.addView(reviewArea)
        controls.addView(results)
    }

    private fun showReview(area: LinearLayout, mode: Mode, ref: String, review: ConversationPlanReviewSnapshot) {
        area.removeAllViews()
        status.text = "Revisión local de stock DEV"
        area.label(review.reviewText)
        if (review.state == ConversationPlanReviewState.PENDING) area.button("Aprobar revisión") {
            decide(area, mode, ref, review, ConversationPlanReviewIntent.Approve(review.context()))
        }
        if (review.state == ConversationPlanReviewState.APPROVED) area.button("Confirmar y aplicar") {
            apply(area, mode, ref, review, reconcile = false)
        }
        if (review.state in setOf(ConversationPlanReviewState.PENDING, ConversationPlanReviewState.APPROVED)) {
            area.button("Cancelar revisión") {
                decide(area, mode, ref, review, ConversationPlanReviewIntent.Discard(review.context()))
            }
        }
    }

    private fun decide(area: LinearLayout, mode: Mode, ref: String, review: ConversationPlanReviewSnapshot,
                       intent: ConversationPlanReviewIntent) {
        work({ pin -> withMode(pin, mode, ref) { host ->
            check(matches(mode, review) && live(pin))
            val result = runBlocking { host.reviews.decide(intent, key()) }
            checkNotNull((result as? ConversationReviewDecision.Saved)?.review)
        } }) { showReview(area, mode, ref, it) }
    }

    private fun apply(area: LinearLayout, mode: Mode, ref: String, review: ConversationPlanReviewSnapshot, reconcile: Boolean) {
        if (busy) return
        area.removeAllViews()
        work({ pin -> withMode(pin, mode, ref) { host ->
            check(matches(mode, review))
            select(host.selection, create = false)
            if (reconcile) host.reconcile(review.context()) else host.apply(review.context())
        } }) { result ->
            status.text = when (result) {
                is ReviewedConversationApplyResult.Committed -> "Confirmado. Recibo ${result.receipt.receiptId.value}"
                is ReviewedConversationApplyResult.Denied -> "Operación no disponible para esta revisión y referencia."
                ReviewedConversationApplyResult.RecoveryRequired -> "Resultado pendiente. Consultá el historial y reconciliá el recibo."
            }
        }
    }

    private fun matches(mode: Mode, review: ConversationPlanReviewSnapshot): Boolean = when (mode) {
        Mode.RECEIVE -> review.reviewId.startsWith(receivePrefix) || review.reviewId.startsWith(purchasePrefix)
        Mode.UNDO -> review.reviewId.startsWith(undoPrefix) ||
            (review.reviewId.startsWith(receivePrefix) && review.state == ConversationPlanReviewState.COMMITTED)
    }

    private fun readPurchase(pin: ActiveGenerationPointer, ref: String): String {
        val verified = verifiedPurchase(pin, ref)
        val purchase = verified.row
        val position = receiveHost(pin, ref).use { checkNotNull(it.position(ref)) }
        check(verified.ledger.revision == position.ledgerRevision && position.purchase.revision == purchase.revision)
        val history = verified.ledger.receipts.filter { it.purchaseRef == ref }.sortedBy { it.revision }
        return "Compra manual confirmada · ${purchase.stockItemRef}\n" +
            "Comprado ${format(purchase.quantityThousandths.toLong())} · ${purchase.unit}\n" +
            "Recibido ${format(position.purchase.receivedThousandths)} · Pendiente ${format(position.pendingThousandths)}" +
            history.joinToString("") { row -> "\nRecepción ${format(row.quantityThousandths.toLong())} · ${row.receiptId}" }
    }

    private data class VerifiedPurchase(val row: RealityHousehold12PurchaseRow, val ledger: RealityHousehold11ReceiptRows)

    private fun verifiedPurchase(pin: ActiveGenerationPointer, ref: String): VerifiedPurchase {
        val purchase = S4W05ManualPurchaseDevApplyHost(root, pin, gate, catalog, principal, purchaseEpoch,
            { purchaseEpoch.takeIf { live(pin) } }, { live(pin) }, ::isLive,
            consent(pin, HouseholdManualPurchaseReview.OPERATION, ref), System::currentTimeMillis,
        ).use { checkNotNull(it.find(ref)) }
        return FaroV5GenerationStoreGraph.openActiveHousehold12DevForPurchaseHost(
            root, pin, gate, CanonicalPreparedPlanEnvelopeVerifier, { live(pin) },
        ).use { session ->
            val workspace = session.workspace
            val contract = checkNotNull(catalog.current(HouseholdManualPurchaseReview.OPERATION))
            val committed = workspace.snapshot().workspace.conversations.any { entry ->
                runBlocking { workspace.reviews(entry.id) }.any validProof@{ review ->
                    val receipt = review.receipt
                    if (receipt == null || !review.reviewId.startsWith(purchasePrefix)) return@validProof false
                    val sealed = workspace.loadCommitted(review.context()) ?: return@validProof false
                    val node = PreparedPlanEnvelopeCodec.decode(sealed.envelope()).applyPlan
                        .nodesInTopologicalOrder().singleOrNull() ?: return@validProof false
                    val prepared = node.preparedOperation
                    val source = HouseholdManualPurchaseReview.decode(prepared.payload) ?: return@validProof false
                    val attempt = session.attempts.find(receipt.transactionId) ?: return@validProof false
                    node.dependsOn.isEmpty() && prepared.contract == contract &&
                        prepared.idempotencyKey == IdempotencyKey(review.reviewId) &&
                        source.principal == principal && source.purchaseRef == ref &&
                        source.itemRef == purchase.stockItemRef &&
                        source.quantity.thousandths == purchase.quantityThousandths.toLong() &&
                        source.quantity.unit.name == purchase.unit &&
                        attempt.state == ApplyAttemptJournalState.COMMITTED &&
                        attempt.planFingerprint == receipt.planFingerprint &&
                        session.receipts.find(receipt.transactionId) == receipt
                }
            }
            check(committed && live(pin))
            val snapshot = session.snapshotPurchaseReceipts()
            check(snapshot.purchases.any { it == purchase })
            VerifiedPurchase(purchase, snapshot.receipts)
        }
    }

    private fun receiveHost(pin: ActiveGenerationPointer, ref: String) = S4W05StockReceiveDevApplyHost(
        root, pin, gate, catalog, principal, epoch, { epoch.takeIf { live(pin) } }, { live(pin) }, ::isLive,
        consent(pin, HouseholdStockReceiveReview.OPERATION, ref), System::currentTimeMillis,
    )

    private fun undoHost(pin: ActiveGenerationPointer, ref: String) = S4W05StockUndoDevApplyHost(
        root, pin, gate, catalog, principal, epoch, { epoch.takeIf { live(pin) } }, { live(pin) }, ::isLive,
        consent(pin, undoOperation, ref), System::currentTimeMillis,
    )

    private data class Host(
        val reviews: ConversationReviewPort,
        val selection: ConversationWorkspaceSelectionPort,
        val apply: (ConversationPlanReviewContext) -> ReviewedConversationApplyResult,
        val reconcile: (ConversationPlanReviewContext) -> ReviewedConversationApplyResult,
    )

    private fun <T> withMode(pin: ActiveGenerationPointer, mode: Mode, ref: String, use: (Host) -> T): T = when (mode) {
        Mode.RECEIVE -> receiveHost(pin, ref).use {
            use(Host(it.reviewDecisions, it.chatSelection, it::executeAfterExplicitConfirmation, it::reconcileCommittedOnly))
        }
        Mode.UNDO -> undoHost(pin, ref).use {
            use(Host(it.reviewDecisions, it.chatSelection, it::executeAfterExplicitConfirmation, it::reconcileCommittedOnly))
        }
    }

    private fun consent(pin: ActiveGenerationPointer, operation: OperationId, ref: String): (String, OperationId, String) -> Boolean =
        { actor, requested, target -> actor == principal && requested == operation && target == ref && live(pin) }

    private fun select(selection: ConversationWorkspaceSelectionPort, create: Boolean) = runBlocking {
        val selected = selection.select(chat, key())
        if (selected is ConversationSelectionResult.Saved) return@runBlocking
        check(create && selection.createAndSelect(chat, System.currentTimeMillis(), key()) is ConversationSelectionResult.Saved)
    }

    private fun isLive() = alive.get() && foreground()
    private fun live(pin: ActiveGenerationPointer) = isLive() && pointer.read() == pin

    private fun <T> work(action: (ActiveGenerationPointer) -> T, publish: (T) -> Unit) {
        if (busy || !isLive()) return
        busy = true
        status.text = "Consultando la autoridad local…"
        executor.execute {
            val result = runCatching {
                val pin = pinned ?: checkNotNull(pointer.read()).also { pinned = it }
                check(live(pin))
                action(pin).also { check(live(pin)) }
            }
            activity.runOnUiThread {
                busy = false
                if (isLive()) result.fold(publish) {
                    status.text = "No disponible. Verificá el permiso, la referencia y la generación DEV activa."
                }
            }
        }
    }

    override fun close() {
        if (alive.compareAndSet(true, false)) {
            controls.removeAllViews()
            status.text = "Panel DEV cerrado"
            dialog.dismiss()
            onClosed()
        }
    }

    private fun LinearLayout.field(label: String, number: Boolean = false) = EditText(context).also {
        it.hint = label
        it.inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
        addView(it)
    }

    private fun LinearLayout.label(value: String) = addView(TextView(context).apply { text = value })
    private fun LinearLayout.button(label: String, action: () -> Unit) = addView(Button(context).apply {
        text = label
        setOnClickListener { if (!busy && isLive()) action() }
    })

    private fun hash(domain: String, length: Int) = CanonicalHashBuilder.domain(domain)
        .string("principal", principal).sha256Hex().take(length)
    private fun key() = IdempotencyKey("stock-panel:" + UUID.randomUUID())
    private fun format(thousandths: Long) = thousandths.toBigDecimal().movePointLeft(3).stripTrailingZeros().toPlainString()

    private companion object {
        // A new process must not recreate the epoch of an approved Review from disk.
        val nextEpoch = AtomicLong(java.security.SecureRandom().nextLong().ushr(1).coerceIn(1, Long.MAX_VALUE / 2))
    }
}
