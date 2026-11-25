package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.SealedCommercialEvidence2SnapshotReader
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import java.nio.file.Path

/**
 * W05-C2B1A: semantic equality of every commercial Evidence2 row against the
 * original authenticated FBS2v8. No durable authorization, ticket, pointer
 * switch, open ACTIVE owner, stock Apply, or OS projection.
 */
internal class FaroV5Household11Evidence2SemanticPreflight(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterObservation: () -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val noNestedQuiescence = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    fun inspect(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FaroV5Household11CutoverInspection =
        quiescence.withMaterialWritesQuiesced(operationId) {
            val bytes = encryptedArchive.copyOf()
            try {
                val preflight = FaroV5Household11DevCutoverPreflight(
                    root, noNestedQuiescence, assertExclusiveClosedDevHost,
                    expectedEvidenceSchema = 2,
                )
                val first = preflight.inspect(
                    operationId, target, expectedActive, bytes, passphrase)
                val opened = when (val result = crypto.openWithHouseholdReceipts11(
                    bytes, passphrase,
                )) {
                    is FiveStoreAuthenticatedPayload.Opened -> result.payload
                    is FiveStoreAuthenticatedPayload.Rejected ->
                        error("Evidence2 semantic source must be authenticated FBS2v8")
                }
                val commercial = opened.base.base.base.fourStore.commercialEvidence
                check(commercial.schemaVersion == 2)
                val authenticated = checkNotNull(commercial.stagedTyped) {
                    "Evidence2 semantic source has no canonical typed snapshot"
                }
                val file = target.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
                check(SealedCommercialEvidence2SnapshotReader.read(file) == authenticated) {
                    "Evidence2 restored commercial terms differ from authenticated backup"
                }
                afterObservation() // adversarial host/pointer/SQLite drift seam
                val repeated = preflight.inspect(
                    operationId, target, expectedActive, bytes, passphrase)
                check(repeated == first) {
                    "Evidence2 semantic preflight changed after observation"
                }
                check(SealedCommercialEvidence2SnapshotReader.read(file) == authenticated) {
                    "Evidence2 semantic data changed after observation"
                }
                assertExclusiveClosedDevHost()
                first
            } finally {
                bytes.fill(0)
            }
        }
}
