package ar.com.faro.v5.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5W05Household11DevStartupTest {
    @Test fun originalCoreDevIsAllowedOnlyWhenDebugAndProductiveOff() {
        assertTrue(FaroV5W05Household11DevStartup.permitsIsolatedDev(
            "ar.com.faro.alpha.v2coredev", true, false))
    }
    @Test fun isolatedG15DevIsExplicitlyAllowed() {
        assertTrue(FaroV5W05Household11DevStartup.permitsIsolatedDev(
            "ar.com.faro.g15isolated.v2coredev", true, false))
    }
    @Test fun productiveV2IsAlwaysDenied() {
        assertFalse(FaroV5W05Household11DevStartup.permitsIsolatedDev(
            "ar.com.faro.alpha.v2coredev", true, true))
    }
    @Test fun nonDebugBuildIsAlwaysDenied() {
        assertFalse(FaroV5W05Household11DevStartup.permitsIsolatedDev(
            "ar.com.faro.alpha.v2coredev", false, false))
    }
    @Test fun otherVariantsCannotInheritSql11ReadAuthority() {
        for (id in listOf("ar.com.faro.alpha", "ar.com.faro.alpha.v5dev",
            "ar.com.faro.alpha.s3integrateddev", "ar.com.faro.alpha.w01financedev",
            "ar.com.faro.alpha.w03mattersdev", "ar.com.faro.alpha.w04commercialdev")) {
            assertFalse(FaroV5W05Household11DevStartup.permitsIsolatedDev(id, true, false))
        }
    }
}
