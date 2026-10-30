package ar.com.faro.v5.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public final class FaroV5S0LifecycleInstrumentationTest {
    private static final long STARTUP_TIMEOUT_MILLIS = 20_000L;

    @Test
    public void healthyStartupAndActivityRecreationRemainAuthorityNeutral()
            throws Exception {
        FaroV5Application application = application();
        waitForLocalReady(application);
        waitForStartupIdle(application);
        FaroV5CompositionRoot root = compositionRoot(application);
        String generationBefore = root.activeGenerationId();

        assertNotNull(generationBefore);
        assertTrue(root.getStartupReadiness().isLocalReady());
        assertEquals(
                Boolean.TRUE,
                root.getMaterialWriteGate().supplyMaterialWrite(() -> Boolean.TRUE));

        try (ActivityScenario<FaroV5BootstrapActivity> scenario =
                ActivityScenario.launch(FaroV5BootstrapActivity.class)) {
            assertHealthyBootstrapSurface(scenario);
            scenario.recreate();
            assertHealthyBootstrapSurface(scenario);
        }

        assertEquals(generationBefore, root.activeGenerationId());
        assertTrue(root.getStartupReadiness().isLocalReady());
        assertEquals(
                Boolean.TRUE,
                root.getMaterialWriteGate().supplyMaterialWrite(() -> Boolean.TRUE));
    }

    @Test
    public void recoveryFailureSurvivesRecreationFailClosedAndCanonicalRetryRecovers()
            throws Exception {
        FaroV5Application application = application();
        waitForLocalReady(application);
        waitForStartupIdle(application);
        FaroV5CompositionRoot root = compositionRoot(application);

        try {
            assertFalse(injectRecoveryFailure(root));
            assertEquals(
                    FaroV5StartupReadiness.State.MAINTENANCE_ONLY,
                    root.getStartupReadiness().getState());
            assertMaterialWriteRejected(root);

            try (ActivityScenario<FaroV5BootstrapActivity> scenario =
                    ActivityScenario.launch(FaroV5BootstrapActivity.class)) {
                assertMaintenanceSurface(scenario);
                scenario.recreate();
                assertMaintenanceSurface(scenario);
            }

            assertEquals(
                    FaroV5StartupReadiness.State.MAINTENANCE_ONLY,
                    root.getStartupReadiness().getState());
            assertMaterialWriteRejected(root);

            assertTrue(application.retryStartupRecovery());
            waitForLocalReady(application);
            waitForStartupIdle(application);
            assertEquals(
                    Boolean.TRUE,
                    root.getMaterialWriteGate().supplyMaterialWrite(() -> Boolean.TRUE));
        } finally {
            if (!application.getStartupReadiness().isLocalReady()) {
                application.retryStartupRecovery();
                waitForLocalReady(application);
                waitForStartupIdle(application);
            }
        }
    }

    private static FaroV5Application application() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Application application = (Application) target.getApplicationContext();
        assertTrue(application instanceof FaroV5Application);
        return (FaroV5Application) application;
    }

    private static FaroV5CompositionRoot compositionRoot(FaroV5Application application)
            throws Exception {
        Field field = FaroV5Application.class.getDeclaredField("compositionRoot");
        field.setAccessible(true);
        FaroV5CompositionRoot root = (FaroV5CompositionRoot) field.get(application);
        assertNotNull(root);
        return root;
    }

    private static boolean injectRecoveryFailure(FaroV5CompositionRoot root)
            throws Exception {
        Method method =
                FaroV5CompositionRoot.class.getDeclaredMethod(
                        "executeStartupRecoveryStep",
                        FaroV5StartupReadiness.MaintenanceReason.class,
                        FaroV5StartupReadiness.RecoveryKind.class,
                        Runnable.class);
        method.setAccessible(true);
        Object result =
                method.invoke(
                        root,
                        FaroV5StartupReadiness.MaintenanceReason.ACTIVE_GENERATION_HEALTH_FAILED,
                        FaroV5StartupReadiness.RecoveryKind.GENERIC,
                        (Runnable)
                                () -> {
                                    throw new IllegalStateException(
                                            "S0.A6 injected recovery failure");
                                });
        return (Boolean) result;
    }

    private static void assertMaterialWriteRejected(FaroV5CompositionRoot root) {
        boolean rejected = false;
        try {
            root.getMaterialWriteGate().supplyMaterialWrite(() -> Boolean.TRUE);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue("material write must remain fail-closed", rejected);
    }

    private static void assertHealthyBootstrapSurface(
            ActivityScenario<FaroV5BootstrapActivity> scenario) {
        AtomicBoolean checked = new AtomicBoolean(false);
        scenario.onActivity(
                activity -> {
                    View root = activity.getWindow().getDecorView();
                    String maintenanceTitle =
                            activity.getString(R.string.bootstrap_maintenance_title);
                    assertFalse(
                            "healthy bootstrap must not show maintenance title",
                            containsVisibleText(root, maintenanceTitle));
                    checked.set(true);
                });
        assertTrue(checked.get());
    }

    private static void assertMaintenanceSurface(
            ActivityScenario<FaroV5BootstrapActivity> scenario) {
        AtomicBoolean checked = new AtomicBoolean(false);
        scenario.onActivity(
                activity -> {
                    View root = activity.getWindow().getDecorView();
                    assertTrue(
                            containsVisibleText(
                                    root,
                                    activity.getString(
                                            R.string.bootstrap_maintenance_title)));
                    assertTrue(
                            containsVisibleText(
                                    root,
                                    activity.getString(
                                            R.string.bootstrap_retry_action)));
                    assertTrue(
                            containsVisibleText(
                                    root,
                                    activity.getString(
                                            R.string.bootstrap_diagnostics_show)));
                    checked.set(true);
                });
        assertTrue(checked.get());
    }

    private static boolean containsVisibleText(View view, String expected) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && expected.contentEquals(value)) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsVisibleText(group.getChildAt(index), expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void waitForStartupIdle(FaroV5Application application)
            throws Exception {
        Field field = FaroV5Application.class.getDeclaredField("startupRecoveryRunning");
        field.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) field.get(application);
        long deadline =
                android.os.SystemClock.uptimeMillis() + STARTUP_TIMEOUT_MILLIS;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (!running.get()) {
                return;
            }
            Thread.sleep(25L);
        }
        assertFalse("startup recovery did not become idle", running.get());
    }

    private static void waitForLocalReady(FaroV5Application application)
            throws InterruptedException {
        long deadline =
                android.os.SystemClock.uptimeMillis() + STARTUP_TIMEOUT_MILLIS;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (application.getStartupReadiness().isLocalReady()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertTrue(
                "startup did not reach LOCAL_READY: "
                        + application.getStartupReadiness().getState(),
                application.getStartupReadiness().isLocalReady());
    }
}
