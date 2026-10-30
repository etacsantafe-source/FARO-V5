package ar.com.faro.v5.evidence

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommercialEvidenceMaintenancePortTest {
    @Test
    fun `maintenance inspection reports initialized schema and physical integrity`() {
        CommercialEvidenceStore(":memory:").use { store ->
            val inspection = store.maintenance.inspect()

            assertTrue(inspection.schemaVersion > 0)
            assertTrue(inspection.integrityHealthy)
        }
    }

    @Test
    fun `maintenance inspection fails closed after store close`() {
        val store = CommercialEvidenceStore(":memory:")
        val maintenance = store.maintenance
        store.close()

        assertThrows(IllegalStateException::class.java) {
            maintenance.inspect()
        }
    }
}
