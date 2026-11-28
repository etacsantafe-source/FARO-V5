package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Household15DevStockReadHost
import ar.com.faro.v5.generation.FaroV5StockReadGrant
import ar.com.faro.v5.generation.FaroV5StockReadResult
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.surface.FaroV5StockUiHistory
import ar.com.faro.v5.surface.FaroV5StockUiMovement
import ar.com.faro.v5.surface.FaroV5StockUiPosition
import ar.com.faro.v5.surface.FaroV5StockUiState
import ar.com.faro.v5.surface.FaroV5StockUiUnit
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A stock-only foreground admission minted by the isolated Activity. Never uses a Workspace grant. */
internal class S4W05StockReadDevBinding private constructor(
    private val pointer: FileActiveGenerationPointer,
    private val grant: FaroV5StockReadGrant,
    private val foreground: () -> Boolean,
) : AutoCloseable {
    private val monitor = Any()
    private val current = MutableStateFlow<FaroV5StockUiState>(FaroV5StockUiState.Hidden.UNAVAILABLE)
    private var stopped = false
    private val pointerChanged = Runnable { stop(FaroV5StockUiState.Hidden.STALE) }

    val state = current.asStateFlow()

    private fun liveGrant() = synchronized(monitor) {
        grant.takeIf { !stopped && foreground() }
    }

    private fun start(root: Path, gate: MaterialWriteGate, executor: Executor) {
        pointer.addChangeListener(pointerChanged)
        executor.execute {
            val view = runCatching {
                val selected = pointer.read() ?: return@runCatching FaroV5StockUiState.Hidden.UNAVAILABLE
                val host = FaroV5Household15DevStockReadHost(root, selected, gate, grant, ::liveGrant)
                val result = present(host.read())
                if (pointer.read() != selected) FaroV5StockUiState.Hidden.STALE else result
            }.getOrDefault(FaroV5StockUiState.Hidden.UNAVAILABLE)
            synchronized(monitor) {
                if (!stopped) current.value = if (foreground()) view else FaroV5StockUiState.Hidden.DENIED
            }
        }
    }

    private fun stop(state: FaroV5StockUiState.Hidden) {
        synchronized(monitor) {
            if (stopped) return
            stopped = true
            current.value = state
        }
        pointer.removeChangeListener(pointerChanged)
    }

    override fun close() = stop(FaroV5StockUiState.Hidden.DENIED)

    companion object {
        internal fun afterForegroundClick(
            root: Path,
            grant: FaroV5StockReadGrant,
            gate: MaterialWriteGate,
            executor: Executor,
            foreground: () -> Boolean,
        ): S4W05StockReadDevBinding {
            check(foreground())
            val binding = S4W05StockReadDevBinding(FileActiveGenerationPointer(root), grant, foreground)
            try {
                binding.start(root, gate, executor)
                return binding
            } catch (failure: Exception) {
                binding.close()
                throw failure
            }
        }

        internal fun present(result: FaroV5StockReadResult): FaroV5StockUiState = when (result) {
            FaroV5StockReadResult.Hidden.UNAVAILABLE -> FaroV5StockUiState.Hidden.UNAVAILABLE
            FaroV5StockReadResult.Hidden.DENIED -> FaroV5StockUiState.Hidden.DENIED
            FaroV5StockReadResult.Hidden.STALE -> FaroV5StockUiState.Hidden.STALE
            is FaroV5StockReadResult.Verified -> if (result.positions.isEmpty()) {
                FaroV5StockUiState.EmptyConfirmed
            } else {
                FaroV5StockUiState.Ready(
                    result.positions.map {
                        FaroV5StockUiPosition(it.stockItemRef, FaroV5StockUiUnit.valueOf(it.unit),
                            it.physicalThousandths, it.reservedThousandths, it.consumedThousandths,
                            it.pendingReceiptThousandths)
                    },
                    result.history.map {
                        FaroV5StockUiHistory(it.stockItemRef, FaroV5StockUiUnit.valueOf(it.unit),
                            FaroV5StockUiMovement.valueOf(it.type.name), it.quantityThousandths, it.recordedAtMs,
                            it.revision)
                    },
                )
            }
        }
    }
}
