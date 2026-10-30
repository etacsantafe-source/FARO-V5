package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * A1.3a format boundaries. Existing source generations MUST continue to use
 * LEGACY_V1/ALL until an explicit, separately certified V2 stage+activation.
 * This declaration does not mount, materialize, or activate Workspace.
 */
enum class GenerationStoreFormat {
    LEGACY_V1,
    WORKSPACE_V2,
}

object GenerationStoreFiles {
    const val REALITY = "reality.db"
    const val COMMERCIAL_EVIDENCE = "commercial-evidence.db"
    const val WEB_EVIDENCE = "web-evidence.db"
    const val AUXILIARY = "auxiliary.db"
    const val WORKSPACE = "workspace.db"

    val LEGACY_V1: List<String> =
        listOf(
            REALITY,
            COMMERCIAL_EVIDENCE,
            WEB_EVIDENCE,
            AUXILIARY,
        )

    val WORKSPACE_V2: List<String> = LEGACY_V1 + WORKSPACE

    /** Compatibility alias. Older generation readers intentionally require 4 files. */
    val ALL: List<String> = LEGACY_V1

    fun names(format: GenerationStoreFormat): List<String> =
        when (format) {
            GenerationStoreFormat.LEGACY_V1 -> LEGACY_V1
            GenerationStoreFormat.WORKSPACE_V2 -> WORKSPACE_V2
        }

    /** Existing calls remain strictly legacy: they cannot silently migrate files. */
    fun requireMaterialized(directory: Path) {
        requireMaterialized(directory, GenerationStoreFormat.LEGACY_V1)
    }

    /** Read-only format validation. This method NEVER creates a database. */
    fun requireMaterialized(directory: Path, format: GenerationStoreFormat) {
        val normalized = directory.toAbsolutePath().normalize()
        names(format).forEach { fileName ->
            val path = normalized.resolve(fileName)
            check(!Files.isSymbolicLink(path)) {
                "StorageGeneration store must not be a symbolic link: $fileName"
            }
            check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                "StorageGeneration store is missing: $fileName"
            }
        }
    }
}

class GenerationAuthorityStoreGraph private constructor(
    val generationId: StorageGenerationId,
    val directory: Path,
    val reality: RealityStore,
    val commercialEvidence: CommercialEvidenceStore,
    val webEvidence: WebEvidenceStore,
    val auxiliary: AuxiliaryDeliveryStore,
) : AutoCloseable {
    fun verifyHealthy(): Boolean {
        val realityHealth = reality.maintenance.inspect()
        val commercialHealth = commercialEvidence.maintenance.inspect()
        val webHealth = webEvidence.maintenance.inspect()
        val auxiliaryHealth = auxiliary.maintenance.inspect()
        return realityHealth.integrityHealthy &&
            realityHealth.foreignKeysHealthy &&
            commercialHealth.integrityHealthy &&
            commercialHealth.foreignKeysHealthy &&
            webHealth.integrityHealthy &&
            webHealth.foreignKeysHealthy &&
            auxiliaryHealth.integrityHealthy &&
            auxiliaryHealth.foreignKeysHealthy
    }

    fun verifyFreshForReset(): Boolean {
        val realityHealth = reality.maintenance.inspect()
        val commercialHealth = commercialEvidence.maintenance.inspect()
        val webHealth = webEvidence.maintenance.inspect()
        val auxiliaryHealth = auxiliary.maintenance.inspect()
        return verifyHealthy() &&
            realityHealth.freshForSemanticMigration &&
            commercialHealth.freshForSemanticMigration &&
            webHealth.fetchRecordCount == 0L &&
            webHealth.searchRecordCount == 0L &&
            auxiliaryHealth.freshForSemanticMigration
    }

    override fun close() {
        runCatching { auxiliary.close() }
        runCatching { webEvidence.close() }
        runCatching { commercialEvidence.close() }
        runCatching { reality.close() }
    }

    companion object {
        fun openExisting(
            staged: StagedStorageGeneration,
        ): GenerationAuthorityStoreGraph {
            check(
                ExistingGenerationFormatReader.requireAndRead(staged) ==
                    GenerationStoreFormat.LEGACY_V1
            ) {
                "Workspace V2 must use its complete generation-aware runtime owner"
            }
            return open(staged)
        }

        fun open(
            staged: StagedStorageGeneration,
        ): GenerationAuthorityStoreGraph {
            val directory = staged.directory.toAbsolutePath().normalize()
            // Protect the creating open() path too; openExisting() already
            // checks. Otherwise an incomplete V2 stage could silently
            // initialize four V1 SQLite stores before a Workspace owner exists.
            ExistingGenerationFormatReader.requireLegacyUnmarkedCandidate(directory)
            val reality =
                RealityStore(directory.resolve(GenerationStoreFiles.REALITY).toString())
            var commercial: CommercialEvidenceStore? = null
            var web: WebEvidenceStore? = null
            var auxiliary: AuxiliaryDeliveryStore? = null
            try {
                commercial =
                    CommercialEvidenceStore(
                        directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()
                    )
                web =
                    WebEvidenceStore(
                        directory.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()
                    )
                auxiliary =
                    AuxiliaryDeliveryStore(
                        directory.resolve(GenerationStoreFiles.AUXILIARY).toString()
                    )
                val graph =
                    GenerationAuthorityStoreGraph(
                        generationId = staged.descriptor.generationId,
                        directory = directory,
                        reality = reality,
                        commercialEvidence = commercial,
                        webEvidence = web,
                        auxiliary = auxiliary,
                    )
                check(graph.verifyHealthy()) {
                    "StorageGeneration authority graph failed health verification"
                }
                return graph
            } catch (failure: Throwable) {
                runCatching { auxiliary?.close() }
                runCatching { web?.close() }
                runCatching { commercial?.close() }
                runCatching { reality.close() }
                throw failure
            }
        }
    }
}

class EmptyGenerationResetTargetPort : ResetTargetGenerationPort {
    override fun materializeAndVerify(
        plan: ResetPlan,
        staged: StagedStorageGeneration,
    ): Boolean {
        if (staged.descriptor.generationId != plan.targetGenerationId) {
            return false
        }
        if (staged.descriptor.schemaSetFingerprint != plan.schemaSetFingerprint) {
            return false
        }
        return GenerationAuthorityStoreGraph.open(staged).use { graph ->
            graph.verifyFreshForReset()
        }
    }
}
