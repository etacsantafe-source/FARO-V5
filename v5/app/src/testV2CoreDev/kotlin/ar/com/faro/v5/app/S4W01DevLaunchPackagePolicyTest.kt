package ar.com.faro.v5.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Exact DEV package admission; not an Android Activity launch or a Finance read grant. */
class S4W01DevLaunchPackagePolicyTest {
    @Test fun d01OriginalDevRemainsAllowed() {
        assertTrue(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.v2coredev"))
    }

    @Test fun d02G15IsolatedDevRemainsAllowed() {
        assertTrue(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.g15isolated.v2coredev"))
    }

    @Test fun d03S3IntegratedDevRemainsAllowed() {
        assertTrue(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.s3integrateddev"))
    }

    @Test fun d04W01FinanceDevExactApplicationIdAllowed() {
        assertTrue(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.w01financedev"))
    }

    @Test fun d05ProductiveAndOrdinaryDebugDenied() {
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha"))
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.v5dev"))
    }

    @Test fun d06W01PrefixAndSuffixImpostorsDenied() {
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.w01financedev.extra"))
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("prefix.ar.com.faro.alpha.w01financedev"))
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.w01finance"))
    }

    @Test fun d07UnapprovedDevAndS3ImpostorsDenied() {
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.x03aidev"))
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("ar.com.faro.alpha.s3integrateddev.extra"))
    }

    @Test fun d08EmptyAndCaseVariationDenied() {
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts(""))
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts("AR.COM.FARO.ALPHA.W01FINANCEDEV"))
        assertFalse(FaroV5V2CoreDevPackagePolicy.accepts(" ar.com.faro.alpha.w01financedev"))
    }
}
