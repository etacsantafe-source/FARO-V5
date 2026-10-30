package ar.com.faro.v5.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import ar.com.faro.v5.platform.android.update.AndroidUpdateForegroundStatePort;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-local foreground observation for update user-action routing. */
final class FaroV5ActivityForegroundTracker
        implements Application.ActivityLifecycleCallbacks, AndroidUpdateForegroundStatePort {
    private final AtomicInteger resumedActivities = new AtomicInteger(0);

    @Override
    public boolean isForeground() {
        return resumedActivities.get() > 0;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumedActivities.incrementAndGet();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        resumedActivities.updateAndGet(value -> Math.max(0, value - 1));
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}
}
