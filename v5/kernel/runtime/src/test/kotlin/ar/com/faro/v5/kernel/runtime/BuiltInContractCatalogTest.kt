package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.BookmarkOperationIds
import java.security.MessageDigest
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInContractCatalogTest {
    @Test
    fun `generated catalog materializes the exact D-229 plus approved Guardados set`() {
        val catalog = BuiltInContractCatalog.create()

        assertEquals(54, catalog.capabilities.size)
        assertEquals(180, catalog.operations.size)

        val profileCounts = catalog.operations
            .groupingBy { it.executionProfile }
            .eachCount()

        assertEquals(
            mapOf(
                ExecutionProfile.READ_ONLY to 50,
                ExecutionProfile.REALITY_MUTATION to 70,
                ExecutionProfile.AUXILIARY_MUTATION to 42,
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
            180,
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

    @Test
    fun `registered Guardados set is exactly the eight approved typed contracts`() {
        val bookmarks = BuiltInContractCatalog.create().operations
            .filter { it.operationId.value.startsWith("search.bookmark.") }
        assertEquals(8, bookmarks.size)
        assertEquals(BookmarkOperationIds.proposedProfiles.keys, bookmarks.map { it.operationId }.toSet())
        bookmarks.forEach { contract ->
            assertEquals("N-018", contract.capabilityId.value)
            assertEquals("federated-search", contract.canonicalExecutor.value)
            assertEquals(BookmarkOperationIds.proposedProfiles[contract.operationId], contract.executionProfile)
            assertEquals(ContractExposure.BINDABLE, contract.exposure)
            assertEquals("metadata-v1", contract.contractVersion.value)
        }
    }

    @Test
    fun `all original 172 descriptor metadata remain byte identical`() {
        val original = BuiltInContractCatalog.create().operations
            .filterNot { it.operationId.value.startsWith("search.bookmark.") }
            .sortedBy { it.operationId.value }
        assertEquals(172, original.size)
        val body = original.joinToString("") { contract ->
            listOf(
                contract.operationId.value, contract.capabilityId.value,
                contract.canonicalExecutor.value, contract.executionProfile.name,
                contract.exposure.name, contract.contractVersion.value,
            ).joinToString("\t") + "\n"
        }.toByteArray(Charsets.UTF_8)
        val header = ("blob " + body.size + "\u0000").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1").digest(header + body)
            .joinToString("") { "%02x".format(it) }
        assertEquals("300bb32731bcebd6f4aea3f8e923a2341428dcd6", digest)
    }
}
