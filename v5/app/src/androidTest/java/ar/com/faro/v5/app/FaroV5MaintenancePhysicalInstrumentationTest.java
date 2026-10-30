package ar.com.faro.v5.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import org.junit.Test;

@RequiresDevice
public final class FaroV5MaintenancePhysicalInstrumentationTest {
    @Test
    public void startupRecoveryLeavesActiveGenerationHealthyAndMaterialWritesOpen()
            throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Application application = (Application) target.getApplicationContext();
        assertTrue(application instanceof FaroV5Application);

        FaroV5CompositionRoot root = compositionRoot((FaroV5Application) application);
        waitForStartupReprojection(root);

        assertEquals(
                FaroV5CompositionRoot.StartupReprojectionState.PASS,
                root.getStartupReprojectionState());

        String activeGenerationId = root.activeGenerationId();
        assertNotNull(activeGenerationId);
        assertFalse(activeGenerationId.trim().isEmpty());

        assertTrue(root.activateAndVerifyGeneration(activeGenerationId));
        assertEquals(
                Boolean.TRUE,
                root.getMaterialWriteGate().supplyMaterialWrite(() -> Boolean.TRUE));
    }

    private static FaroV5CompositionRoot compositionRoot(FaroV5Application application)
            throws Exception {
        Field field = FaroV5Application.class.getDeclaredField("compositionRoot");
        field.setAccessible(true);
        FaroV5CompositionRoot root = (FaroV5CompositionRoot) field.get(application);
        assertNotNull(root);
        return root;
    }

    private static void waitForStartupReprojection(FaroV5CompositionRoot root)
            throws InterruptedException {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            FaroV5CompositionRoot.StartupReprojectionState state =
                    root.getStartupReprojectionState();
            if (state == FaroV5CompositionRoot.StartupReprojectionState.PASS
                    || state == FaroV5CompositionRoot.StartupReprojectionState.DEGRADED) {
                return;
            }
            Thread.sleep(50L);
        }
    }
}
