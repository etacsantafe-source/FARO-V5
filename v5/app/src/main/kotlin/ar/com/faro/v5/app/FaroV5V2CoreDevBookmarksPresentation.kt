package ar.com.faro.v5.app

/** Native projection deliberately excludes source IDs, text and authority tokens. */
internal object FaroV5V2CoreDevBookmarksPresentation {
    const val unavailable = "Guardados no disponibles. Volvé a abrirlos para autorizar la lectura."
    fun render(page: FaroV5BookmarkHostPage?): String {
        if (page == null) return unavailable
        if (page.items.isEmpty()) return "No hay referencias guardadas disponibles."
        val folders = page.folders.associate { it.id to it.name }
        return page.items.mapIndexed { index, item ->
            val favorite = if (item.favorite) " · Favorito" else ""
            val folder = item.folderId?.let { folders[it] }?.let { " · " + it } ?: ""
            "Referencia " + (index + 1) + favorite + folder
        }.joinToString("\n") + if (page.hasMore) "\nHay más referencias guardadas." else ""
    }
}
