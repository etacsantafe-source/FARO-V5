package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C117 staged V2 graph gate wiring; NOT a host cutover or physical T4. */
class FaroV5FiveOwnerSharedGateT2Test {
    @TempDir lateinit var root: Path

    private fun staged(id: String): StagedStorageGeneration =
        FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(StorageGenerationId(id), "schema-five-store-1", 4L)
        )

    private fun materialize(staged: StagedStorageGeneration) {
        val p = staged.directory
        RealityStore(p.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(p.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(p.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(p.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(p.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
    }

    private fun open(staged: StagedStorageGeneration, gate: SingleProcessMaterialWriteGate) =
        FaroV5GenerationStoreGraph.openValidatedV2WithSharedGate(
            staged, PreparedPlanEnvelopeVerificationPort { _, _ -> false }, gate
        )

    @Test fun exactSameGateInjectedIntoEveryOwnerAndRealityWriteStops() {
        val staged = staged("gate-all-five")
        materialize(staged)
        val gate = SingleProcessMaterialWriteGate()
        open(staged, gate).use { graph ->
            val stores = listOf(
                graph.reality, graph.commercialEvidence, graph.webEvidence,
                graph.auxiliary, graph.workspace!!
            )
            stores.forEach { owner ->
                val field = owner.javaClass.getDeclaredField("materialWriteGate")
                field.isAccessible = true
                assertSame(gate, field.get(owner), "Every V2 owner must share exactly one gate")
            }
            gate.closeMaterialWriteAdmission()
            assertTrue(graph.verifyHealthy(), "pure reads must remain available during quiescence")
            assertThrows(IllegalStateException::class.java) {
                graph.reality.run(TransactionId("tx-denied")) {
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            gate.openMaterialWriteAdmission()
            graph.reality.run(TransactionId("tx-allowed")) {
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
    }

    @Test fun closedGateRefusesSealedV2OpenWithoutActivatingGeneration() {
        val staged = staged("gate-deny-open")
        materialize(staged)
        val gate = SingleProcessMaterialWriteGate()
        gate.closeMaterialWriteAdmission()
        assertThrows(IllegalStateException::class.java) { open(staged, gate) }
        assertTrue(Files.isRegularFile(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
        gate.openMaterialWriteAdmission()
        open(staged, gate).use { assertTrue(it.verifyHealthy()) }
    }
    @Test fun maintenanceOwnedRecoveryMayCommitWhileAdmissionRemainsClosed() {
        val staged = staged("gate-recovery-owned")
        materialize(staged)
        val gate = SingleProcessMaterialWriteGate()
        open(staged, gate).use { graph ->
            gate.closeMaterialWriteAdmission()
            gate.withQuiescedMaterialWrites {
                graph.reality.run(TransactionId("tx-maintenance")) {
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            assertThrows(IllegalStateException::class.java) {
                graph.reality.run(TransactionId("tx-unrelated")) {
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            assertTrue(graph.verifyHealthy())
            assertTrue(!gate.isMaterialWriteAdmissionOpen())
        }
    }

}
