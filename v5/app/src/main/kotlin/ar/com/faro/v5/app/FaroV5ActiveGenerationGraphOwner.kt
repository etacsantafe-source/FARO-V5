package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Path

/**
 * Single active-authority graph selector. The durable pointer, immutable descriptor
 * and V2 format marker are checked before *any* store opens, then rechecked.
 *
 * The shipping Android root MUST supply v2HostCertified=false until complete
 * A6 F1-F4 certification and a separate V2 activation admission are authorized.
 * Opening a V2 graph here never switches a pointer or accepts material writes.
 */
object FaroV5ActiveGenerationGraphOwner {
    @JvmStatic
    fun open(
        storageRoot: Path,
        expectedGenerationId: String,
        materialWriteGate: MaterialWriteGate,
        v2HostCertified: Boolean,
    ): FaroV5GenerationStoreGraph {
        val expected = StorageGenerationId(expectedGenerationId)
        val pointer = FileActiveGenerationPointer(storageRoot)
        check(pointer.read()?.generationId == expected) {
            "active generation pointer differs from requested runtime authority"
        }
        val staged = FileStorageGenerationLayout(storageRoot).requireStaged(expected)
        val format = ExistingGenerationFormatReader.requireAndRead(staged)
        // No SQLite open (not even read-only) is permitted for unapproved V2.
        if (format == GenerationStoreFormat.WORKSPACE_V2) {
            check(v2HostCertified) {
                "active V2 host is not certified and cannot become material authority"
            }
        }
        val graph = when (format) {
            GenerationStoreFormat.LEGACY_V1 ->
                FaroV5GenerationStoreGraph.open(staged.directory, expectedGenerationId)
            GenerationStoreFormat.WORKSPACE_V2 ->
                FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(
                    staged, CanonicalPreparedPlanEnvelopeVerifier, materialWriteGate,
                )
        }
        try {
            check(pointer.read()?.generationId == expected) {
                "active generation pointer changed while opening runtime graph"
            }
            check(graph.generationId == expectedGenerationId && graph.verifyHealthy()) {
                "selected active generation failed five-owner health verification"
            }
            return graph
        } catch (failure: Throwable) {
            graph.close()
            throw failure
        }
    }


    /**
     * A5: exact-schema8 / full-pointer host opener for a previously certified
     * DEV or product host. NOT a fallback of open(), not an activation, and
     * does not authorize person/domain reads or material writes.
     *
     * The ordinary shipping open() keeps its schema7 admission unchanged.
     */
    internal fun openExistingPlanning8ForCertifiedHost(
        storageRoot: Path,
        expectedGenerationId: String,
        expectedPointerSequence: Long,
        materialWriteGate: MaterialWriteGate,
        v2HostCertified: Boolean,
    ): FaroV5GenerationStoreGraph {
        check(v2HostCertified) { "Active Reality8 requires certified host" }
        require(expectedGenerationId.isNotBlank() && expectedPointerSequence > 0L)
        val expected = StorageGenerationId(expectedGenerationId)
        val pointer = FileActiveGenerationPointer(storageRoot)
        val pinned = checkNotNull(pointer.read()) { "Active Reality8 pointer missing" }
        check(pinned.generationId == expected &&
            pinned.sequence == expectedPointerSequence) {
            "Active Reality8 full pointer mismatch"
        }
        val staged = FileStorageGenerationLayout(storageRoot).requireStaged(expected)
        check(ExistingGenerationFormatReader.requireAndRead(staged) ==
            GenerationStoreFormat.WORKSPACE_V2) { "Active Reality8 requires V2 marker" }
        val graph = FaroV5GenerationStoreGraph.openActivePlanning8ForCertifiedHost(
            storageRoot, staged, expectedPointerSequence,
            CanonicalPreparedPlanEnvelopeVerifier, materialWriteGate,
            v2HostCertified = true,
        )
        try {
            check(pointer.read() == pinned && graph.generationId == expectedGenerationId &&
                graph.workspace != null &&
                graph.reality.maintenance.inspect().schemaVersion == 8 &&
                graph.verifyHealthy() && pointer.read() == pinned) {
                "Active Reality8 host graph health or identity changed"
            }
            return graph
        } catch (failure: Throwable) {
            graph.close()
            throw failure
        }
    }


    /**
     * N033C2B2: one-shot schema8 readiness of an ALREADY active V2 candidate.
     * No runtime graph, ACL, Review or Apply authority escapes. This method
     * is not wired into shipping CompositionRoot; V2_ACTIVE_ENABLED stays false.
     * Any later operation MUST acquire fresh pointer + object authorization.
     */
    internal fun isActivePlanning8ReadyForCertifiedHost(
        storageRoot: Path,
        expectedGenerationId: String,
        materialWriteGate: MaterialWriteGate,
        v2HostCertified: Boolean,
    ): Boolean {
        // Deny before even attempting any SQLite opens when uncertified.
        if (!v2HostCertified || expectedGenerationId.isBlank()) return false
        return runCatching {
            val pointer = FileActiveGenerationPointer(storageRoot)
            val pinned = pointer.read() ?: return@runCatching false
            if (pinned.generationId.value != expectedGenerationId) {
                return@runCatching false
            }
            val staged = FileStorageGenerationLayout(storageRoot)
                .requireStaged(pinned.generationId)
            if (ExistingGenerationFormatReader.requireAndRead(staged) !=
                    GenerationStoreFormat.WORKSPACE_V2) {
                return@runCatching false
            }
            FaroV5GenerationStoreGraph.isActivePlanning8HealthyForHost(
                storageRoot, staged, pinned.sequence,
                CanonicalPreparedPlanEnvelopeVerifier, materialWriteGate,
            ) && pointer.read() == pinned
        }.getOrDefault(false)
    }
}
