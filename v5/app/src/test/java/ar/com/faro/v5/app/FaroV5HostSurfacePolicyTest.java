package ar.com.faro.v5.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FaroV5HostSurfacePolicyTest {
    @Test
    void initializingAndMaintenanceAlwaysStayOnBootstrap() {
        for (FaroV5HostSurfacePolicy.OnboardingGate gate
                : FaroV5HostSurfacePolicy.OnboardingGate.values()) {
            assertEquals(
                    FaroV5HostSurfacePolicy.Surface.BOOTSTRAP,
                    FaroV5HostSurfacePolicy.resolve(
                            FaroV5StartupReadiness.initializing(),
                            gate));
            assertEquals(
                    FaroV5HostSurfacePolicy.Surface.BOOTSTRAP,
                    FaroV5HostSurfacePolicy.resolve(
                            FaroV5StartupReadiness.maintenanceOnly(
                                    FaroV5StartupReadiness.MaintenanceReason
                                            .ACTIVE_GENERATION_HEALTH_FAILED),
                            gate));
        }
    }

    @Test
    void localReadyWaitsOnlyForUnresolvedOnboarding() {
        assertEquals(
                FaroV5HostSurfacePolicy.Surface.BOOTSTRAP,
                FaroV5HostSurfacePolicy.resolve(
                        FaroV5StartupReadiness.localReady(),
                        FaroV5HostSurfacePolicy.OnboardingGate.UNRESOLVED));
        assertEquals(
                FaroV5HostSurfacePolicy.Surface.ONBOARDING,
                FaroV5HostSurfacePolicy.resolve(
                        FaroV5StartupReadiness.localReady(),
                        FaroV5HostSurfacePolicy.OnboardingGate.PRESENT));
        assertEquals(
                FaroV5HostSurfacePolicy.Surface.SHELL,
                FaroV5HostSurfacePolicy.resolve(
                        FaroV5StartupReadiness.localReady(),
                        FaroV5HostSurfacePolicy.OnboardingGate.CLEAR));
    }
}
