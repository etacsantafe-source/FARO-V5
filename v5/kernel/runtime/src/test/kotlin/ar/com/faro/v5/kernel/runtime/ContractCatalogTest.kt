package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContractCatalogTest {
    @Test fun `catalog snapshots inputs and resolves known entries`() {
        val capabilities = mutableListOf(capability("N-025"))
        val operations = mutableListOf(operation("finance.snapshot.read", "N-025"))
        val catalog = ContractCatalog.of(capabilities, operations)
        capabilities.clear(); operations.clear()
        assertEquals(1, catalog.capabilities.size)
        assertEquals(1, catalog.operations.size)
        assertEquals(OperationId("finance.snapshot.read"), catalog.operation(OperationId("finance.snapshot.read"))?.operationId)
        assertNull(catalog.operation(OperationId("unknown.operation")))
    }
    @Test fun `duplicate capability ids fail closed`() {
        assertThrows(IllegalArgumentException::class.java) { ContractCatalog.of(listOf(capability("N-025"), capability("N-025")), emptyList()) }
    }
    @Test fun `duplicate operation ids fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContractCatalog.of(listOf(capability("N-025")), listOf(operation("finance.snapshot.read","N-025"), operation("finance.snapshot.read","N-025")))
        }
    }
    @Test fun `operation referencing unknown capability fails closed`() {
        assertThrows(IllegalArgumentException::class.java) { ContractCatalog.of(listOf(capability("N-025")), listOf(operation("calendar.read","N-999"))) }
    }
    private fun capability(id:String)=CapabilityDescriptor(CapabilityId(id))
    private fun operation(id:String, capabilityId:String)=OperationContractDescriptor(
        OperationId(id), CapabilityId(capabilityId), ExecutorId("fixture-owner"), ExecutionProfile.READ_ONLY,
        ContractVersion("1"), ContractFingerprint("fp-$id"), ContractExposure.BINDABLE
    )
}
