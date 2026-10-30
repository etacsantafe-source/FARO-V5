package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5FiveStoreBackupCaptureT2Test {
    @TempDir lateinit var temp: Path

    private class Lease(
        val reject: Boolean = false,
    ) : MaterialWriteQuiescencePort {
        var calls = 0
        var released = 0
        var active = false

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            calls += 1
            if (reject) error("global write quiescence unavailable")
            check(!active) { "nested/non-global quiescence not allowed" }
            active = true
            try {
                return block()
            } finally {
                active = false
                released += 1
            }
        }
    }

    private fun openFive(name: String): FaroV5GenerationStoreGraph {
        val staged = FileStorageGenerationLayout(temp.resolve(name)).stageWorkspaceV2(
            StorageGenerationDescriptor(
                generationId = StorageGenerationId("generation-$name"),
                schemaSetFingerprint = "five-store-capture-native",
                createdAtEpochMillis = 1234,
            )
        )
        val dir = staged.directory
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
        return FaroV5GenerationStoreGraph.openValidatedV2(staged)
    }

    private fun capture(
        graph: FaroV5GenerationStoreGraph,
        lease: Lease,
    ): FaroV5FiveStoreSemanticSet =
        FaroV5FiveStoreBackupCapture(graph, lease).capture(
            operationId = MaintenanceOperationId("five-store-capture-test"),
            backupSetId = BackupSetId("five-store-capture-backup"),
            productVersion = "5.0-capture-native",
            createdAtEpochMillis = 54321L,
        )

    @Test fun oneLeaseCapturesAllFiveOwnersFromExactGraphGeneration() {
        openFive("complete").use { graph ->
            val lease = Lease()
            val set = capture(graph, lease)
            assertEquals(1, lease.calls)
            assertEquals(1, lease.released)
            assertFalse(lease.active)
            assertEquals(graph.generationId, set.fourStore.manifest.sourceGenerationId.value)
            assertEquals(graph.reality.backup.snapshot(), set.fourStore.reality)
            assertEquals(graph.commercialEvidence.backup.snapshot(), set.fourStore.commercialEvidence)
            assertEquals(graph.webEvidence.backup.snapshot(), set.fourStore.webEvidence)
            assertEquals(graph.auxiliary.backup.snapshot(), set.fourStore.auxiliary)
            assertEquals(graph.workspace!!.backup.snapshot(), set.workspace)
            // Separate semantic gate is already certified; this checks integration only.
            assertEquals(set, FaroV5FiveStoreSemanticCodec.decode(
                FaroV5FiveStoreSemanticCodec.encode(set),
            ))
        }
    }

    @Test fun failedGlobalQuiescenceDoesNotProducePartialAuthorityOrMutateStores() {
        openFive("unavailable").use { graph ->
            val before = graph.workspace!!.backup.snapshot()
            val lease = Lease(reject = true)
            assertThrows(IllegalStateException::class.java) {
                capture(graph, lease)
            }
            assertEquals(1, lease.calls)
            assertEquals(0, lease.released)
            assertFalse(lease.active)
            assertEquals(before, graph.workspace!!.backup.snapshot())
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun legacyFourStoreGraphCannotSilentlyExportIncompleteFiveStoreSet() {
        val dir = temp.resolve("legacy")
        Files.createDirectories(dir)
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }

        FaroV5GenerationStoreGraph.open(dir, "legacy-four").use { graph ->
            assertNull(graph.workspace)
            val lease = Lease()
            assertThrows(IllegalStateException::class.java) {
                capture(graph, lease)
            }
            assertEquals(0, lease.calls)
            assertTrue(graph.verifyHealthy())
        }
    }
}
