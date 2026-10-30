package ar.com.faro.v5.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.WindowManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import ar.com.faro.v5.control.security.BiometricPromptSpec;
import ar.com.faro.v5.control.security.BiometricReauthenticationPort;
import ar.com.faro.v5.platform.android.cert.FaroV5P6CertificationActivity;
import ar.com.faro.v5.platform.android.security.AndroidBiometricReauthentication;
import ar.com.faro.v5.platform.android.security.AndroidScreenPrivacyPort;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FaroV5P6UiInstrumentationTest {
    @Test
    public void screenPrivacyFlagRoundTripsOnCertificationHost() {
        try (ActivityScenario<FaroV5P6CertificationActivity> scenario =
                ActivityScenario.launch(FaroV5P6CertificationActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AndroidScreenPrivacyPort privacy =
                                new AndroidScreenPrivacyPort(activity);
                        privacy.setEnabled(true);
                        assertTrue(
                                (activity.getWindow().getAttributes().flags
                                                & WindowManager.LayoutParams.FLAG_SECURE)
                                        != 0);
                        privacy.setEnabled(false);
                        assertEquals(
                                0,
                                activity.getWindow().getAttributes().flags
                                        & WindowManager.LayoutParams.FLAG_SECURE);
                    });
        }
    }

    @Test
    public void biometricAvailabilityAndCancellationCompleteExactlyOnce() throws Exception {
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<BiometricReauthenticationPort.Result> result =
                new AtomicReference<>();
        AtomicReference<BiometricReauthenticationPort.Availability> availability =
                new AtomicReference<>();

        try (ActivityScenario<FaroV5P6CertificationActivity> scenario =
                ActivityScenario.launch(FaroV5P6CertificationActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        AndroidBiometricReauthentication biometric =
                                new AndroidBiometricReauthentication(activity);
                        BiometricReauthenticationPort.Availability current =
                                biometric.availability();
                        availability.set(current);
                        BiometricReauthenticationPort.Attempt attempt =
                                biometric.authenticate(
                                        new BiometricPromptSpec(
                                                "FARO V5 P6",
                                                "Certificación biométrica",
                                                "Cancelar"),
                                        terminalResult -> {
                                            result.set(terminalResult);
                                            callbackCount.incrementAndGet();
                                            terminal.countDown();
                                        });
                        if (current
                                == BiometricReauthenticationPort.Availability.AVAILABLE) {
                            attempt.cancel();
                        }
                    });

            assertTrue(terminal.await(8, TimeUnit.SECONDS));
            Thread.sleep(500L);
            assertEquals(1, callbackCount.get());
            if (availability.get()
                    == BiometricReauthenticationPort.Availability.AVAILABLE) {
                assertEquals(
                        BiometricReauthenticationPort.Result.CANCELLED,
                        result.get());
            } else {
                assertEquals(
                        BiometricReauthenticationPort.Result.UNAVAILABLE,
                        result.get());
            }
        }
    }
}
