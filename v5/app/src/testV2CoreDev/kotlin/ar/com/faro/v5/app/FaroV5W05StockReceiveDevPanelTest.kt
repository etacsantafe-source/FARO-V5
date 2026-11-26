package ar.com.faro.v5.app

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaroV5W05StockReceiveDevPanelTest {
    @Test
    fun admitsOnlyTheTwoDebugPackagesWhenProductionIsOff() {
        val allowed = listOf("ar.com.faro.alpha.v2coredev", "ar.com.faro.g15isolated.v2coredev")
        allowed.forEach { id ->
            assertTrue(FaroV5W05StockPanelPolicy.permits(id, true, false))
            assertFalse(FaroV5W05StockPanelPolicy.permits(id, false, false))
            assertFalse(FaroV5W05StockPanelPolicy.permits(id, true, true))
        }
        listOf("ar.com.faro.alpha", "ar.com.faro.alpha.s3integrateddev",
            "ar.com.faro.alpha.w01financedev", "${allowed.first()}.test").forEach {
            assertFalse(FaroV5W05StockPanelPolicy.permits(it, true, false))
        }
    }

    @Test
    fun usesExactPositiveThousandthsWithoutRounding() {
        assertEquals(7_000L, FaroV5W05StockPanelPolicy.quantity("7"))
        assertEquals(3_125L, FaroV5W05StockPanelPolicy.quantity("3,125"))
        assertEquals(Int.MAX_VALUE.toLong(), FaroV5W05StockPanelPolicy.quantity("2147483.647"))
        listOf("", "0", "-1", "0.0001", "3.14159", "2147483.648", "NaN").forEach {
            assertNull(FaroV5W05StockPanelPolicy.quantity(it), it)
        }
    }

    @Test
    fun isolatesPrincipalsAcrossPackagesAndUids() {
        val id = "ar.com.faro.alpha.v2coredev"
        val principal = FaroV5W05StockPanelPolicy.principal(id, 1000)
        assertEquals(principal, FaroV5W05StockPanelPolicy.principal(id, 1000))
        assertNotEquals(principal, FaroV5W05StockPanelPolicy.principal(id, 1001))
        assertNotEquals(principal, FaroV5W05StockPanelPolicy.principal("ar.com.faro.g15isolated.v2coredev", 1000))
    }
}
