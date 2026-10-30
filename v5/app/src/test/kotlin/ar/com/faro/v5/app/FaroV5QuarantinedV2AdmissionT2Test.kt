package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** JVM-safe host boundary; native SQLite gate covered in generation 3/3. */
class FaroV5QuarantinedV2AdmissionT2Test {
    @TempDir lateinit var root: Path
    private fun stage(id: String) = FileStorageGenerationLayout(root).stageWorkspaceV2(
        StorageGenerationDescriptor(StorageGenerationId(id), "schema-five-store-1", 4L)
    )

    @Test fun refusesActiveTargetBeforeAnyStoreOpen() {
        val staged = stage("host-active-reject")
        val gate = SingleProcessMaterialWriteGate()
        val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
        val host = FaroV5QuarantinedV2GraphAdmission(
            gate, verifier, Supplier { staged.directory }
        )
        assertThrows(IllegalStateException::class.java) { host.open(staged) }
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.REALITY)))
    }

    @Test fun hostRetainsExactInjectedGateAndVerifierAndRefusesIncompleteV2() {
        val staged = stage("host-incomplete-v2")
        val active = Files.createDirectory(root.resolve("other-active"))
        val gate = SingleProcessMaterialWriteGate()
        val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
        val host = FaroV5QuarantinedV2GraphAdmission(gate, verifier, Supplier { active })
        val gateField = host.javaClass.getDeclaredField("gate")
        gateField.isAccessible = true
        assertSame(gate, gateField.get(host))
        val verifierField = host.javaClass.getDeclaredField("verifier")
        verifierField.isAccessible = true
        assertSame(verifier, verifierField.get(host))
        val canonicalHost = FaroV5QuarantinedV2GraphAdmission.canonical(gate, Supplier { active })
        assertSame(gate, gateField.get(canonicalHost))
        assertSame(
            ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier,
            verifierField.get(canonicalHost)
        )
        assertThrows(IllegalStateException::class.java) { host.open(staged) }
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.REALITY)))
    }
}
