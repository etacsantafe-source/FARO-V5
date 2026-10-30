package ar.com.faro.v5.platform.android;

import android.content.Context;
import android.net.Uri;
import ar.com.faro.v5.platform.android.evidence.AndroidContentUriAttachmentSource;
import ar.com.faro.v5.platform.android.ingress.AndroidExternalIngressParser;
import ar.com.faro.v5.platform.android.ingress.AndroidQuickShortcutAdapter;
import ar.com.faro.v5.platform.android.search.AndroidAppSearchProjection;
import ar.com.faro.v5.platform.android.voice.AndroidReadAloudAdapter;
import ar.com.faro.v5.platform.android.voice.AndroidVoiceRecognitionIntentAdapter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lazy construction boundary for context-bound P6 Android adapters.
 *
 * <p>The factory owns no domain state and performs no startup side effects. Activity-bound
 * biometric/window adapters remain surface-bound and are intentionally not exposed through this
 * CompositionRoot-facing API.
 */
public final class AndroidPlatformAdapterFactory {
    private final Context appContext;

    public AndroidPlatformAdapterFactory(Context context) {
        this.appContext = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    public AndroidContentUriAttachmentSource createAttachmentSource(Uri uri) {
        return new AndroidContentUriAttachmentSource(
                appContext,
                Objects.requireNonNull(uri, "uri"));
    }

    public AndroidVoiceRecognitionIntentAdapter createVoiceRecognition() {
        return new AndroidVoiceRecognitionIntentAdapter();
    }

    public AndroidReadAloudAdapter createReadAloud() {
        return new AndroidReadAloudAdapter(appContext);
    }

    public AndroidAppSearchProjection createSearchProjection() {
        return new AndroidAppSearchProjection(appContext);
    }

    public AndroidExternalIngressParser createExternalIngressParser(
            Set<String> allowedDeepLinkOrigins,
            Map<String, String> deepLinkTargetsByPath,
            Map<String, String> quickTargetsById,
            String shareTarget) {
        return new AndroidExternalIngressParser(
                allowedDeepLinkOrigins,
                deepLinkTargetsByPath,
                quickTargetsById,
                shareTarget);
    }

    public AndroidQuickShortcutAdapter createQuickShortcutAdapter() {
        return new AndroidQuickShortcutAdapter(appContext);
    }
}
