package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRows
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/**
 * Isolated read-only SQL13/Evidence2 ACTIVE DEV host. The caller MUST separately
 * certify the completed journaled cutover; this class neither selects a pointer
 * nor mints an operation grant or permits any SQL mutation.
 */
internal class FaroV5Household13ActiveDevReadHost private constructor(
    private val graph: FaroV5GenerationStoreGraph,
    private val gate: MaterialWriteGate,
    private val channel: FileChannel,
    private val live: () -> Boolean,
) : AutoCloseable {
    fun readHistory(): RealityHousehold13CompensationRows? = gate.withMaterialWrite {
        if (!live()) return@withMaterialWrite null
        val rows = runCatching { graph.reality.snapshotActiveHousehold13RowsForTrustedHost() }.getOrNull()
        rows?.takeIf { live() }
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
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            ownerAdmission: () -> Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5Household13ActiveDevReadHost = gate.withMaterialWrite {
            check(ownerAdmission() && Files.isDirectory(storageRoot, NOFOLLOW_LINKS)) {
                "Reality13 ACTIVE DEV admission denied"
            }
            val root = storageRoot.toRealPath()
            val lockFile = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(lockFile, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val held = try {
                    checkNotNull(channel.tryLock()) { "Reality13 DEV root already leased" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("Reality13 DEV root already leased", occupied)
                }
                val pointer = FileActiveGenerationPointer(root)
                val fence = {
                    runCatching { held.isValid && ownerAdmission() && pointer.read() == selected }
                        .getOrDefault(false)
                }
                check(fence()) { "Reality13 selected full pointer changed" }
                val stage = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
                check(stage.directory.toRealPath() == stage.directory) { "Reality13 aliased generation" }
                val graph = FaroV5GenerationStoreGraph.openActiveHousehold13ForHeldDevHost(
                    stage, gate, fence, planVerifier,
                )
                FaroV5Household13ActiveDevReadHost(graph, gate, channel, fence)
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }
    }
}
