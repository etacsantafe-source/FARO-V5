package ar.com.faro.v5.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.*
import ar.com.faro.v5.kernel.contracts.*
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking

/** Native DEV controls. Construction/render/resume never loads REMOVED or grants mutation. */
internal class FaroV5V2CoreDevBookmarkFolderControls(
    private val activity: Activity,
    private val reader: FaroV5BookmarkReadHostBinding,
    private val executor: Executor,
    private val foreground: () -> Boolean,
    private val onOpenSource: (FaroV5BookmarkHostPage, FaroV5BookmarkHostItem) -> Unit = { _, _ -> },
    private val actions: () -> FaroV5BookmarkFolderActionBinding?,
) : AutoCloseable {
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val status = TextView(activity)
    private val sequence = AtomicLong(0)
    private var sourceRequest: Pair<Long, FaroV5BookmarkHostPage>? = null
    private var dialog: AlertDialog? = null
    @Volatile private var closed = false
    private fun live() = !closed && foreground()
    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(activity).apply { text = label; setOnClickListener { if (live()) action() } })
    }
    init {
        view.addView(status)
        button(view, "Ver activos") { loadActive() }
        button(view, "Ver quitados") { loadRemoved() }
        button(view, "Crear carpeta") { askName("Crear carpeta") { name ->
            prepare(BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId(UUID.randomUUID().toString()), name)))
        } }
        view.addView(rows)
    }
    fun loadActive(cursor: BookmarkId? = null) {
        val ticket = sequence.incrementAndGet(); rows.removeAllViews(); status.text = "Cargando…"
        executor.execute {
            val page = runCatching { runBlocking { reader.readAuthorized(cursor) } }.getOrNull()
            activity.runOnUiThread {
                if (!live() || sequence.get() != ticket) return@runOnUiThread
                if (page == null || !reader.isPageCurrent(page)) { unavailable(); return@runOnUiThread }
                status.text = if (page.items.isEmpty()) "Vacío" else "Referencias activas"
                page.folders.forEach { folder ->
                    rows.addView(TextView(activity).apply { text = "Carpeta: ${folder.name}" })
                    button(rows, "Renombrar carpeta") { askName("Renombrar carpeta") { prepare(BookmarkMutation.FolderRename(folder.id, it)) } }
                    button(rows, "Eliminar carpeta vacía") { prepare(BookmarkMutation.FolderDelete(folder.id)) }
                }
                page.items.forEachIndexed { index, item ->
                    rows.addView(TextView(activity).apply { text = "Referencia ${index + 1}" })
                    val sourceClick = FaroV5BookmarkSourceClick(page, item) {
                        live() && dialog?.isShowing != true && sequence.get() == ticket && reader.isPageCurrent(page)
                    }
                    button(rows, "Abrir fuente") {
                        if (sourceClick.consume()) {
                            sourceRequest = sequence.incrementAndGet() to page
                            rows.removeAllViews(); status.text = "Verificando fuente…"
                            onOpenSource(page, item)
                        }
                    }
                    button(rows, if (item.favorite) "Quitar favorito" else "Marcar favorito") { prepare(BookmarkMutation.FavoriteSet(item.id, !item.favorite), item) }
                    button(rows, "Quitar referencia") { prepare(BookmarkMutation.LifecycleSet(item.id, BookmarkLifecycle.REMOVED), item) }
                    button(rows, "Mover a raíz") { prepare(BookmarkMutation.FolderAssign(item.id, null), item) }
                    page.folders.forEach { folder -> button(rows, "Mover a «${folder.name}»") { prepare(BookmarkMutation.FolderAssign(item.id, folder.id), item) } }
                }
                if (page.hasMore) button(rows, "Siguiente") { loadActive(page.items.lastOrNull()?.id) }
            }
        }
    }
    private fun loadRemoved(cursor: BookmarkId? = null) {
        val ticket = sequence.incrementAndGet(); rows.removeAllViews(); status.text = "Cargando…"
        executor.execute {
            val page = runCatching { runBlocking { reader.readRemovedAfterExplicitRequest(cursor) } }.getOrNull()
            activity.runOnUiThread {
                if (!live() || sequence.get() != ticket) return@runOnUiThread
                if (page == null || !reader.isRecoveryCurrent(page)) { unavailable(); return@runOnUiThread }
                status.text = if (page.items.isEmpty()) "Vacío" else "Referencias quitadas"
                page.items.forEachIndexed { index, item ->
                    rows.addView(TextView(activity).apply { text = "Referencia quitada ${index + 1}" })
                    button(rows, "Restaurar") { prepare(BookmarkMutation.LifecycleSet(item.id, BookmarkLifecycle.ACTIVE)) }
                }
                if (page.hasMore) button(rows, "Siguiente") { loadRemoved(page.items.lastOrNull()?.id) }
            }
        }
    }
    private fun askName(title: String, accept: (String) -> Unit) {
        val input = EditText(activity)
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity).setTitle(title).setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Revisar") { _, _ ->
                if (live()) runCatching { BookmarkPolicy.requireFolderName(input.text.toString()); accept(input.text.toString()) }
                    .onFailure { status.text = "Nombre inválido (1–80 caracteres, sin espacios en los extremos)" }
            }.show()
    }
    private fun prepare(mutation: BookmarkMutation, item: FaroV5BookmarkHostItem? = null) {
        val ticket = sequence.incrementAndGet(); rows.removeAllViews(); status.text = "Cargando revisión…"
        executor.execute {
            val binding = if (live()) actions() else null
            val review = runCatching { runBlocking { binding?.prepare(mutation, item) } }.getOrNull()
            activity.runOnUiThread {
                if (!live() || sequence.get() != ticket) { binding?.close(); return@runOnUiThread }
                if (binding == null || review == null || !binding.isCurrent(review)) { unavailable(); return@runOnUiThread }
                dialog?.dismiss()
                dialog = AlertDialog.Builder(activity).setTitle("Confirmar Guardados").setMessage(review.label)
                    .setOnCancelListener { binding.cancel(review) }
                    .setNegativeButton("Cancelar") { _, _ -> binding.cancel(review); status.text = "Cancelado. Volvé a consultar." }
                    .setPositiveButton("Confirmar") { _, _ ->
                        if (!live()) { binding.cancel(review); return@setPositiveButton }
                        status.text = "Confirmando…"
                        executor.execute {
                            val result = runCatching { runBlocking { binding.confirm(review) } }.getOrDefault("Resultado pendiente de verificar")
                            activity.runOnUiThread {
                                if (live() && sequence.get() == ticket) status.text =
                                    if (result == "Guardados actualizado" && !binding.isSuccessCurrent()) "Resultado pendiente de verificar" else result
                            }
                        }
                    }.show()
            }
        }
    }
    fun isSourceRequestCurrent(page: FaroV5BookmarkHostPage): Boolean {
        val request = sourceRequest ?: return false
        return live() && request.first == sequence.get() && request.second === page && reader.isPageCurrent(page)
    }
    private fun unavailable() { rows.removeAllViews(); status.text = "No disponible. Volvé a abrir Guardados." }
    override fun close() { closed = true; sequence.incrementAndGet(); dialog?.dismiss(); dialog = null; rows.removeAllViews(); status.text = "No disponible" }
}

/** Object identity + one-shot consumption prevents forged rows and repeated button callbacks. */
internal class FaroV5BookmarkSourceClick(
    private val page: FaroV5BookmarkHostPage, private val item: FaroV5BookmarkHostItem,
    private val current: () -> Boolean,
) {
    private val consumed = java.util.concurrent.atomic.AtomicBoolean(false)
    fun consume(): Boolean = consumed.compareAndSet(false, true) && page.items.any { it === item } && current()
}
