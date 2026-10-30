package ar.com.faro.v5.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import ar.com.faro.v5.platform.android.alarm.FaroExactAlarmExecutionBridge;
import ar.com.faro.v5.platform.android.notification.AndroidNotificationDeliverySink;
import ar.com.faro.v5.platform.android.work.FaroWorkerFactory;
import org.junit.Test;

public final class FaroV5PlatformInstrumentationTest {
    @Test
    public void applicationProvidesCanonicalWorkerFactory() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Application application = (Application) target.getApplicationContext();
        assertTrue(application instanceof FaroV5Application);
        Configuration configuration =
                ((FaroV5Application) application).getWorkManagerConfiguration();
        assertTrue(configuration.getWorkerFactory() instanceof FaroWorkerFactory);
        assertTrue(FaroExactAlarmExecutionBridge.isInstalled());
    }

    @Test
    public void notificationIdentityIsStableAndSeparated() {
        String a1 = AndroidNotificationDeliverySink.stableTag("delivery-smoke-a");
        String a2 = AndroidNotificationDeliverySink.stableTag("delivery-smoke-a");
        String b = AndroidNotificationDeliverySink.stableTag("delivery-smoke-b");
        assertEquals(a1, a2);
        assertNotEquals(a1, b);
    }
}
