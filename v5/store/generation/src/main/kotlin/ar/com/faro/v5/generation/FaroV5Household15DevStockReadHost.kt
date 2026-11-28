package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Read admission from the trusted app owner. The owner advances epoch on replacement or revocation. */
data class FaroV5StockReadGrant(val principal: String, val epoch: Long) {
    init {
        require(epoch > 0 && principal.codePointCount(0, principal.length) in 1..128 &&
            principal.none { it <= ' ' || it == '\u007f' } &&
            principal.toByteArray(Charsets.UTF_8).let { it.size <= 384 && it.toString(Charsets.UTF_8) == principal })
    }
}

/** One-shot read of the pinned DEV selection. Every call, including resume, rechecks admission. */
class FaroV5Household15DevStockReadHost(
    private val storageRoot: Path,
    private val selected: ActiveGenerationPointer,
    private val gate: MaterialWriteGate,
    private val admitted: FaroV5StockReadGrant,
    private val currentGrant: () -> FaroV5StockReadGrant?,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    fun read(): FaroV5StockReadResult = runCatching {
        if (currentGrant() != admitted) return FaroV5StockReadResult.Hidden.DENIED
        if (!Files.isDirectory(storageRoot, NOFOLLOW_LINKS)) return FaroV5StockReadResult.Hidden.UNAVAILABLE
        val root = storageRoot.toRealPath()
        gate.withMaterialWrite { readPinned(root) }
    }.getOrDefault(FaroV5StockReadResult.Hidden.UNAVAILABLE)

    private fun readPinned(root: Path): FaroV5StockReadResult {
        val pointer = FileActiveGenerationPointer(root)
        fun completed() = FaroV5Household15Evidence2DevJournaledCutover.hasCompletedSelection(root, selected)
        fun status(): FaroV5StockReadResult.Hidden? = when {
            currentGrant() != admitted -> FaroV5StockReadResult.Hidden.DENIED
            pointer.read() != selected -> FaroV5StockReadResult.Hidden.STALE
            !completed() -> FaroV5StockReadResult.Hidden.UNAVAILABLE
            else -> null
        }

        status()?.let { return it }
        // The inspector pins the root lease, validates all stores and reads under WAL-aware snapshot.
        val rows = runCatching {
            FaroV5GenerationStoreGraph.inspectActiveHousehold15DevReadOnly(
                root, selected, gate, { currentGrant() == admitted }, ::completed,
            )
        }.getOrElse { return status() ?: FaroV5StockReadResult.Hidden.UNAVAILABLE }

        val projection = FaroV5Household15StockPositionProjection.fromVerifiedHistory(
            rows, admitted.principal, selected, clockMs(),
        )
        return status() ?: projection
    }
}
