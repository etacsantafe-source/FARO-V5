package ar.com.faro.v5.app;

import java.util.Objects;

/** Pure presentation gate for the single FARO launcher host. */
final class FaroV5HostSurfacePolicy {
    enum Surface {
        BOOTSTRAP,
        ONBOARDING,
        SHELL
    }

    enum OnboardingGate {
        UNRESOLVED,
        PRESENT,
        CLEAR
    }

    private FaroV5HostSurfacePolicy() {}

    static Surface resolve(
            FaroV5StartupReadiness readiness,
            OnboardingGate onboardingGate) {
        Objects.requireNonNull(readiness, "readiness");
        Objects.requireNonNull(onboardingGate, "onboardingGate");

        if (!readiness.isLocalReady()) {
            return Surface.BOOTSTRAP;
        }

        switch (onboardingGate) {
            case UNRESOLVED:
                return Surface.BOOTSTRAP;
            case PRESENT:
                return Surface.ONBOARDING;
            case CLEAR:
                return Surface.SHELL;
            default:
                throw new IllegalStateException(
                        "Unhandled onboarding gate: " + onboardingGate);
        }
    }
}
