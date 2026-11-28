@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5StockEventType
import ar.com.faro.v5.generation.FaroV5StockHistoryEntry
import ar.com.faro.v5.generation.FaroV5StockPosition
import ar.com.faro.v5.generation.FaroV5StockReadGrant
import ar.com.faro.v5.generation.FaroV5StockReadResult
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.surface.FaroV5StockUiMovement
import ar.com.faro.v5.surface.FaroV5StockUiState
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W05StockReadDevBindingTest {
    @TempDir lateinit var root: Path
    private val grant = FaroV5StockReadGrant("human", 1)

    @Test fun mapsVerifiedBalanceHistoryAndHiddenStates() {
        val pointer = ActiveGenerationPointer(1, StorageGenerationId("local"))
        val read = FaroV5StockReadResult.Verified(
            pointer, 10, 2, listOf(FaroV5StockPosition("Yerba", "UNIT", 7_000, 2_000, 0, 5_000, 3_000)),
            listOf(FaroV5StockHistoryEntry("receipt", null, "Yerba", "UNIT", FaroV5StockEventType.RECEIPT, 7_000, 5, 1)),
        )
        val view = S4W05StockReadDevBinding.present(read) as FaroV5StockUiState.Ready
        assertEquals(5_000L, view.positions.single().available)
        assertEquals(FaroV5StockUiMovement.RECEIPT, view.history.single().movement)
        assertSame(FaroV5StockUiState.EmptyConfirmed, S4W05StockReadDevBinding.present(
            FaroV5StockReadResult.Verified(pointer, 10, 0, emptyList(), emptyList()),
        ))
        FaroV5StockReadResult.Hidden.entries.forEach {
            assertSame(FaroV5StockUiState.Hidden.valueOf(it.name), S4W05StockReadDevBinding.present(it))
        }
    }

    @Test fun backgroundRevocationCannotBeOverwrittenByAnInFlightRead() {
        val queued = mutableListOf<Runnable>()
        val binding = S4W05StockReadDevBinding.afterForegroundClick(
            root, grant, SingleProcessMaterialWriteGate(), { queued.add(it) }, { true },
        )
        binding.close()
        queued.single().run()
        assertSame(FaroV5StockUiState.Hidden.DENIED, binding.state.value)
    }

    @Test fun pointerChangeInvalidatesAndNewReadCannotRestoreTheOldView() {
        val pointer = FileActiveGenerationPointer(root)
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("one")))
        val queued = mutableListOf<Runnable>()
        S4W05StockReadDevBinding.afterForegroundClick(
            root, grant, SingleProcessMaterialWriteGate(), { queued.add(it) }, { true },
        ).use { binding ->
            val selected = checkNotNull(pointer.compareAndSet(origin.generationId, StorageGenerationId("two")))
            pointer.compareAndSet(selected.generationId, origin.generationId)
            queued.single().run()
            assertSame(FaroV5StockUiState.Hidden.STALE, binding.state.value)
        }
    }
}
