package ar.com.faro.v5.platform.android.security;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Looper;
import android.view.WindowManager;
import ar.com.faro.v5.control.security.ScreenPrivacyPort;
import java.util.Objects;

public final class AndroidScreenPrivacyPort implements ScreenPrivacyPort {
    private final Activity activity;
    private final boolean allowDebugCapture;

    public AndroidScreenPrivacyPort(Activity activity) { this(activity, false); }

    public AndroidScreenPrivacyPort(Activity activity, boolean allowDebugCapture) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.allowDebugCapture = allowDebugCapture;
    }

    @Override
    public void setEnabled(boolean enabled) {
        Runnable apply = () -> {
            boolean debuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            boolean secure = enabled && !(debuggable && allowDebugCapture);
            if (secure) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) apply.run(); else activity.runOnUiThread(apply);
    }
}
