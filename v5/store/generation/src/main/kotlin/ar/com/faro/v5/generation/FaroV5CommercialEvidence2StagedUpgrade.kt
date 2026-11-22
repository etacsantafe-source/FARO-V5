package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * W04-F2A. Upgrades ONLY the Commercial Evidence database in a sealed,
 * byte-identical, different-generation copy of the selected Household10 DEV root.
 *
 * A successful return is a STAGED artifact, NOT an ACTIVE generation. No pointer
 * publication, read host, restore/cutover, new grant or checkout is available here.
 */
internal class FaroV5CommercialEvidence2StagedUpgrade(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val requireExclusiveClosedDevHost: () -> Unit,
    private val afterSchemaUpgrade: () -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val layout = FileStorageGenerationLayout(root)
    private val pointer = FileActiveGenerationPointer(root)

    fun upgrade(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): StagedStorageGeneration = quiescence.withMaterialWritesQuiesced(operationId) {
        requirePinned(expectedActive)
        check(staged.descriptor.generationId != expectedActive.generationId) {
            "F2A target must not be active"
        }
        val target = layout.requireStaged(staged.descriptor.generationId)
        val source = layout.requireStaged(expectedActive.generationId)
        check(target.descriptor == staged.descriptor &&
            target.directory.toAbsolutePath().normalize() == staged.directory) {
            "F2A target descriptor and path must be canonical"
        }
        val originalSource = sealedHashes(source.directory)
        val originalTarget = sealedHashes(target.directory)
        check(originalTarget == originalSource) {
            "F2A requires an exact sealed copy of the pinned ACTIVE Household10 source"
        }
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            check(!Files.isSameFile(source.directory.resolve(name), target.directory.resolve(name))) {
                "F2A hard-link alias to ACTIVE source: $name"
            }
        }
        // Both are independently checked as complete Household10 + Evidence1 sets
        // BEFORE any SQLite opener can upgrade a file.
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(source)
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(target)
        requirePinned(expectedActive)
        val commercial = target.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        // The pre-existing schema1 -> schema2 migration is already transactional,
        // and preserves legacy rows with terms physically ABSENT, not "KNOWN".
        CommercialEvidenceStore(commercial.toString(), stagedTypedTermsV2 = true).use { store ->
            val snapshot = store.stagedTypedBackup.snapshot()
            check(snapshot.schemaVersion == 2 && snapshot.rows.all { it.terms == null }) {
                "F2A never enriches legacy evidence with fabricated typed terms"
            }
            check(store.maintenance.inspect().integrityHealthy &&
                store.maintenance.inspect().foreignKeysHealthy) {
                "F2A migrated Commercial Evidence2 must be healthy"
            }
        }
        afterSchemaUpgrade()
        requirePinned(expectedActive)
        FileChannel.open(commercial, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        FileChannel.open(target.directory, READ, NOFOLLOW_LINKS).use { it.force(true) }

        check(sealedHashes(source.directory) == originalSource) {
            "F2A ACTIVE source changed during isolated schema upgrade"
        }
        val after = sealedHashes(target.directory)
        check(after.filterKeys { it != GenerationStoreFiles.COMMERCIAL_EVIDENCE } ==
            originalTarget.filterKeys { it != GenerationStoreFiles.COMMERCIAL_EVIDENCE }) {
            "F2A modified a store other than target Commercial Evidence"
        }
        check(after[GenerationStoreFiles.COMMERCIAL_EVIDENCE] !=
            originalTarget[GenerationStoreFiles.COMMERCIAL_EVIDENCE]) {
            "F2A Evidence2 migration did not change the staged schema"
        }
        StagedSqlitePreflight.requireAll(target.directory, listOf(
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.COMMERCIAL_EVIDENCE, 2,
                listOf("commercial_evidence", "commercial_evidence_quote_terms"),
            ),
        ))
        requirePinned(expectedActive)
        target
    }

    private fun requirePinned(expected: ActiveGenerationPointer) {
        requireExclusiveClosedDevHost()
        check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root)
        check(pointer.read() == expected) { "F2A ACTIVE generation pointer or sequence changed" }
    }

    private fun sealedHashes(directory: Path): Map<String, String> {
        check(Files.isDirectory(directory, NOFOLLOW_LINKS) &&
            directory.toRealPath() == directory.toAbsolutePath().normalize())
        fun requireNoSidecars() {
            for (name in GenerationStoreFiles.WORKSPACE_V2) {
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS)) {
                        "F2A unsealed SQLite sidecar: $name$suffix"
                    }
                }
            }
        }
        requireNoSidecars()
        val hashes = GenerationStoreFiles.WORKSPACE_V2.associateWith { name ->
            val file = directory.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "F2A requires a regular unaliased sealed database: $name"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buf)
                    if (count < 0) break
                    digest.update(buf, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        requireNoSidecars()
        return hashes
    }
}
