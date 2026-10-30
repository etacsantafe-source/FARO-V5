package ar.com.faro.v5.control.security;

import java.util.Objects;

public interface BiometricReauthenticationPort {
    enum Availability { AVAILABLE, NONE_ENROLLED, HARDWARE_UNAVAILABLE, UNSUPPORTED, UNKNOWN }
    enum Result { AUTHENTICATED, CANCELLED, LOCKED_OUT, UNAVAILABLE, ERROR }

    Availability availability();
    Attempt authenticate(BiometricPromptSpec prompt, Callback callback);

    @FunctionalInterface
    interface Callback { void onResult(Result result); }

    interface Attempt {
        void cancel();
        static Attempt noOp() { return () -> { }; }
    }

    static void deliver(Callback callback, Result result) {
        Objects.requireNonNull(callback, "callback").onResult(Objects.requireNonNull(result, "result"));
    }
}
