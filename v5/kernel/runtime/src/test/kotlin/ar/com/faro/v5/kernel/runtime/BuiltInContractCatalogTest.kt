package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInContractCatalogTest {
    @Test
    fun `generated catalog materializes the exact D-229 set`() {
        val catalog = BuiltInContractCatalog.create()

        assertEquals(54, catalog.capabilities.size)
        assertEquals(172, catalog.operations.size)

        val profileCounts = catalog.operations
            .groupingBy { it.executionProfile }
            .eachCount()

        assertEquals(
            mapOf(
                ExecutionProfile.READ_ONLY to 49,
                ExecutionProfile.REALITY_MUTATION to 70,
                ExecutionProfile.AUXILIARY_MUTATION to 35,
                ExecutionProfile.OBSERVATIONAL_WRITE to 7,
                ExecutionProfile.EXTERNAL_EFFECT to 5,
                ExecutionProfile.MAINTENANCE_EFFECT to 6,
            ),
            profileCounts,
        )

        assertEquals(
            ContractExposure.INTERNAL_ONLY,
            catalog.operation(OperationId("memory.learn_validated"))?.exposure,
        )
        assertEquals(
            ContractExposure.BINDABLE,
            catalog.operation(OperationId("finance.snapshot.read"))?.exposure,
        )
        assertEquals(
            172,
            catalog.operations.map { it.contractFingerprint }.toSet().size,
        )
    }

    @Test
    fun `contract metadata version and canonical fingerprint are stable`() {
        val contract = requireNotNull(
            BuiltInContractCatalog.create().operation(OperationId("finance.snapshot.read"))
        )

        assertEquals("metadata-v1", contract.contractVersion.value)
        assertEquals(
            "0714603df12f402fac2d98d1bc080de2b3d8c699976e3649298f94b9c697dee1",
            contract.contractFingerprint.value,
        )
        assertTrue(contract.contractFingerprint.value.matches(Regex("[0-9a-f]{64}")))
    }
}
