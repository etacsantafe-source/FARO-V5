package ar.com.faro.v5.store.reality

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealityMaintenancePortTest {
    @Test
    fun `maintenance inspection reports current schema and physical integrity`() {
        RealityStore(":memory:").use { store ->
            val inspection = store.maintenance.inspect()

            assertEquals(RealityStore.SCHEMA_VERSION, inspection.schemaVersion)
            assertTrue(inspection.integrityHealthy)
        }
    }

    @Test
    fun `maintenance inspection fails closed after store close`() {
        val store = RealityStore(":memory:")
        val maintenance = store.maintenance
        store.close()

        assertThrows(IllegalStateException::class.java) {
            maintenance.inspect()
        }
    }
}
