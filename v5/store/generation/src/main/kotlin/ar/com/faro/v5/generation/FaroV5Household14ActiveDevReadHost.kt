package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRows
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationType
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

internal data class Household14StockPosition(
    val stockItemRef: String,
    val unit: String,
    val physicalThousandths: Long,
    val reservedThousandths: Long,
)

internal data class Household14ActiveDevStockSnapshot(
    val selected: ActiveGenerationPointer,
    val revision: Long,
    val positions: List<Household14StockPosition>,
)

/** Local stock read for an admitted principal on a completed SQL14 DEV selection. */
internal class FaroV5Household14ActiveDevReadHost private constructor(
    private val reality: RealityStore,
    private val gate: MaterialWriteGate,
    private val channel: FileChannel,
    private val selected: ActiveGenerationPointer,
    private val principal: String,
    private val live: () -> Boolean,
) : AutoCloseable {
    fun readStock(): Household14ActiveDevStockSnapshot? = gate.withMaterialWrite {
        if (!live()) return@withMaterialWrite null
        runCatching { project(reality.snapshotActiveHousehold14RowsForTrustedHost()) }
            .getOrNull()?.takeIf { live() }
    }

    private fun project(snapshot: RealityHousehold14ReservationRows): Household14ActiveDevStockSnapshot {
        val history = snapshot.historical.historical
        val balances = mutableMapOf<Pair<String, String>, Household14StockPosition>()
        val purchases = history.purchases.filter { it.confirmedBy == principal }.associateBy { it.purchaseRef }

        fun addPhysical(item: String, unit: String, amount: Long) {
            val key = item to unit
            val position = balances.getOrPut(key) { Household14StockPosition(item, unit, 0, 0) }
            balances[key] = position.copy(physicalThousandths = position.physicalThousandths + amount)
        }

        for (row in history.receipts.receipts) {
            val purchase = purchases[row.purchaseRef] ?: continue
            addPhysical(purchase.stockItemRef, purchase.unit, row.quantityThousandths.toLong())
        }
        for (undo in snapshot.historical.compensations) {
            val purchase = purchases[undo.purchaseRef] ?: continue
            addPhysical(purchase.stockItemRef, purchase.unit, -undo.quantityThousandths.toLong())
        }
        for (row in snapshot.reservations) {
            if (row.principal != principal) continue
            val key = row.stockItemRef to row.unit
            val position = balances.getValue(key)
            val quantity = row.quantityThousandths.toLong()
            val delta = if (row.type == RealityHousehold14ReservationType.RESERVE) quantity else -quantity
            balances[key] = position.copy(reservedThousandths = position.reservedThousandths + delta)
        }

        val revision = maxOf(history.receipts.revision, snapshot.reservations.lastOrNull()?.revision ?: 0L)
        return Household14ActiveDevStockSnapshot(
            selected, revision, balances.values.sortedWith(compareBy({ it.stockItemRef }, { it.unit })),
        )
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        if (channel.isOpen) {
            try {
                reality.close()
            } finally {
                channel.close()
            }
        }
    }

    companion object {
        fun reopen(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            principal: String,
            ownerAdmission: (String) -> Boolean,
        ): FaroV5Household14ActiveDevReadHost = gate.withMaterialWrite {
            require(principal.codePointCount(0, principal.length) in 1..128 &&
                principal.none { it <= '\u0020' || it == '\u007f' } &&
                principal.toByteArray(Charsets.UTF_8).let { it.size <= 384 && it.toString(Charsets.UTF_8) == principal })
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS) && ownerAdmission(principal))
            val root = storageRoot.toRealPath()
            val path = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(path, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val lease = try {
                    checkNotNull(channel.tryLock()) { "SQL14 DEV root is already leased" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("SQL14 DEV root is already leased", occupied)
                }
                val live = {
                    runCatching {
                        lease.isValid && ownerAdmission(principal) &&
                            FaroV5Household14Evidence2DevJournaledCutover.hasCompletedSelection(root, selected)
                    }.getOrDefault(false)
                }
                check(live()) { "SQL14 ACTIVE DEV requires a completed C1 selection" }
                val stage = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
                FaroV5GenerationStoreGraph.requireActiveHousehold14ReadProfile(stage)
                check(live())
                val reality = RealityStore(
                    stage.directory.resolve(GenerationStoreFiles.REALITY).toString(),
                    materialWriteGate = gate, planningStage8 = true, householdActive14DevReadOnly = live,
                )
                try {
                    check(live())
                    FaroV5Household14ActiveDevReadHost(reality, gate, channel, selected, principal, live)
                } catch (failure: Throwable) {
                    reality.close()
                    throw failure
                }
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }
    }
}
