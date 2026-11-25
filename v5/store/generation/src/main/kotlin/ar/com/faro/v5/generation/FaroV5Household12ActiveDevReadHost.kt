package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseReceiptRows
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/** Lease-bound read-only host for an already selected isolated Reality12/Evidence2 generation. */
internal class FaroV5Household12ActiveDevReadHost private constructor(
    private val graph: FaroV5GenerationStoreGraph,
    private val gate: MaterialWriteGate,
    private val channel: FileChannel,
    private val isCurrent: () -> Boolean,
) : AutoCloseable {
    fun readPurchaseReceipts(): RealityHousehold12PurchaseReceiptRows? = gate.withMaterialWrite {
        if (!isCurrent()) return@withMaterialWrite null
        val read = runCatching { graph.reality.snapshotActiveHousehold12RowsForTrustedHost() }.getOrNull()
        read?.takeIf { isCurrent() }
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        if (channel.isOpen) {
            try {
                graph.close()
            } finally {
                channel.close()
            }
        }
    }

    companion object {
        fun reopen(
            storageRoot: Path,
            expected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            ownerAdmission: () -> Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5Household12ActiveDevReadHost = gate.withMaterialWrite {
            check(ownerAdmission() && Files.isDirectory(storageRoot, NOFOLLOW_LINKS)) {
                "Reality12 ACTIVE DEV admission denied"
            }
            val root = storageRoot.toRealPath()
            val channel = FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                val lease = try {
                    checkNotNull(channel.tryLock()) { "DEV root is already leased" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("DEV root is already leased", occupied)
                }
                val pointer = FileActiveGenerationPointer(root)
                val fence = {
                    runCatching { lease.isValid && ownerAdmission() && pointer.read() == expected }
                        .getOrDefault(false)
                }
                check(fence()) { "Reality12 ACTIVE DEV pointer is no longer current" }
                val canonical = FileStorageGenerationLayout(root).requireStaged(expected.generationId)
                check(canonical.directory.toRealPath() == canonical.directory) { "Reality12 stage path is aliased" }
                val graph = FaroV5GenerationStoreGraph.openActiveHousehold12ForHeldDevHost(
                    canonical, gate, fence, planVerifier,
                )
                FaroV5Household12ActiveDevReadHost(graph, gate, channel, fence)
            } catch (failure: Throwable) {
                runCatching { channel.close() }
                throw failure
            }
        }
    }
}
