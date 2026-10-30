package ar.com.faro.v5.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import ar.com.faro.v5.delivery.api.DeliveryDesiredAction;
import ar.com.faro.v5.delivery.spi.NotificationProjectionRequest;
import ar.com.faro.v5.delivery.spi.NotificationSinkOutcome;
import ar.com.faro.v5.platform.android.notification.AndroidNotificationDeliverySink;
import org.junit.Assume;
import org.junit.Test;

public final class FaroV5NotificationDeniedInstrumentationTest {
    @Test
    public void deniedNotificationPermissionFailsClosed() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);

        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotEquals(
                PackageManager.PERMISSION_GRANTED,
                target.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));

        AndroidNotificationDeliverySink sink =
                new AndroidNotificationDeliverySink(
                        target,
                        R.drawable.ic_faro_notification);
        NotificationProjectionRequest request =
                new NotificationProjectionRequest(
                        "delivery-permission-smoke",
                        DeliveryDesiredAction.UPSERT,
                        "bounded permission smoke",
                        false);

        assertEquals(
                NotificationSinkOutcome.BLOCKED_PERMISSION,
                sink.dispatch(request));
    }
}
