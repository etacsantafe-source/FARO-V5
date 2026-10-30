package ar.com.faro.v5.platform.android.ingress;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import ar.com.faro.v5.control.ingress.QuickTriggerSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dynamic launcher shortcut projection.
 *
 * <p>Shortcuts only carry a bounded quick id into the same Android ingress path. They never carry
 * approval, owner payloads or a material FARO command.
 */
public final class AndroidQuickShortcutAdapter {
    private final Context appContext;

    public AndroidQuickShortcutAdapter(Context context) {
        this.appContext = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    public PublishResult publish(ComponentName targetActivity, List<QuickTriggerSpec> specs) {
        Objects.requireNonNull(targetActivity, "targetActivity");
        Objects.requireNonNull(specs, "specs");
        if (specs.isEmpty()) return PublishResult.REJECTED;

        ShortcutManager manager = appContext.getSystemService(ShortcutManager.class);
        if (manager == null) return PublishResult.UNAVAILABLE;
        if (manager.isRateLimitingActive()) return PublishResult.RATE_LIMITED;
        if (specs.size() > manager.getMaxShortcutCountPerActivity()) {
            return PublishResult.REJECTED;
        }

        Set<String> ids = new HashSet<>();
        List<ShortcutInfo> shortcuts = new ArrayList<>(specs.size());
        for (QuickTriggerSpec spec : specs) {
            if (spec == null || !ids.add(spec.getId())) return PublishResult.REJECTED;

            Intent intent =
                    new Intent(AndroidExternalIngressParser.ACTION_QUICK)
                            .setComponent(targetActivity)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(
                                    AndroidExternalIngressParser.EXTRA_QUICK_ID,
                                    spec.getId());

            ShortcutInfo.Builder builder =
                    new ShortcutInfo.Builder(appContext, spec.getId())
                            .setShortLabel(spec.getShortLabel())
                            .setIntent(intent);
            if (!spec.getLongLabel().isEmpty()) {
                builder.setLongLabel(spec.getLongLabel());
            }
            shortcuts.add(builder.build());
        }

        try {
            return manager.setDynamicShortcuts(shortcuts)
                    ? PublishResult.PUBLISHED
                    : PublishResult.REJECTED;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            return PublishResult.REJECTED;
        }
    }

    public enum PublishResult {
        PUBLISHED,
        RATE_LIMITED,
        UNAVAILABLE,
        REJECTED
    }
}
