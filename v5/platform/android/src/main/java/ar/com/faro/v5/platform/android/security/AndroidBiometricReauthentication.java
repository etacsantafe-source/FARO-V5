package ar.com.faro.v5.platform.android.security;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;
import ar.com.faro.v5.control.security.BiometricPromptSpec;
import ar.com.faro.v5.control.security.BiometricReauthenticationPort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AndroidBiometricReauthentication implements BiometricReauthenticationPort {
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG;
    private final FragmentActivity activity;

    public AndroidBiometricReauthentication(FragmentActivity activity) {
        this.activity = Objects.requireNonNull(activity, "activity");
    }

    @Override
    public Availability availability() {
        int result = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS);
        if (result == BiometricManager.BIOMETRIC_SUCCESS) return Availability.AVAILABLE;
        if (result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) return Availability.NONE_ENROLLED;
        if (result == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE || result == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) return Availability.HARDWARE_UNAVAILABLE;
        return Availability.UNSUPPORTED;
    }

    @Override
    public Attempt authenticate(BiometricPromptSpec promptSpec, Callback callback) {
        Objects.requireNonNull(promptSpec, "promptSpec");
        Objects.requireNonNull(callback, "callback");
        if (availability() != Availability.AVAILABLE) {
            BiometricReauthenticationPort.deliver(callback, Result.UNAVAILABLE);
            return Attempt.noOp();
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, activity.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        finishOnce(finished, callback, Result.AUTHENTICATED);
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        finishOnce(finished, callback, mapError(errorCode));
                    }
                    @Override public void onAuthenticationFailed() { }
                });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptSpec.title())
                .setNegativeButtonText(promptSpec.negativeActionLabel())
                .setAllowedAuthenticators(AUTHENTICATORS);
        if (!promptSpec.subtitle().isEmpty()) builder.setSubtitle(promptSpec.subtitle());
        BiometricPrompt.PromptInfo promptInfo = builder.build();

        activity.runOnUiThread(() -> {
            if (!finished.get() && !activity.isFinishing() && !activity.isDestroyed()) {
                biometricPrompt.authenticate(promptInfo);
            } else {
                finishOnce(finished, callback, Result.CANCELLED);
            }
        });

        return () -> activity.runOnUiThread(() -> {
            if (finished.compareAndSet(false, true)) {
                biometricPrompt.cancelAuthentication();
                BiometricReauthenticationPort.deliver(callback, Result.CANCELLED);
            } else {
                biometricPrompt.cancelAuthentication();
            }
        });
    }

    private static Result mapError(int errorCode) {
        switch (errorCode) {
            case BiometricPrompt.ERROR_CANCELED:
            case BiometricPrompt.ERROR_USER_CANCELED:
            case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
                return Result.CANCELLED;
            case BiometricPrompt.ERROR_LOCKOUT:
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                return Result.LOCKED_OUT;
            case BiometricPrompt.ERROR_HW_UNAVAILABLE:
            case BiometricPrompt.ERROR_HW_NOT_PRESENT:
            case BiometricPrompt.ERROR_NO_BIOMETRICS:
                return Result.UNAVAILABLE;
            default:
                return Result.ERROR;
        }
    }

    private static void finishOnce(AtomicBoolean finished, Callback callback, Result result) {
        if (finished.compareAndSet(false, true)) BiometricReauthenticationPort.deliver(callback, result);
    }
}
