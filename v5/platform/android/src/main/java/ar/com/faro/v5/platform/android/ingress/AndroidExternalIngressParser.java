package ar.com.faro.v5.platform.android.ingress;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import ar.com.faro.v5.control.ingress.ExternalIngressTrigger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed Android ingress parser for Quick, deep-link and Sharesheet entry.
 *
 * <p>Only explicitly read fields cross this boundary. Unknown extras and nested Intents are never
 * forwarded. Shared content remains as content:// references for the P6.A2 bounded attachment path.
 */
public final class AndroidExternalIngressParser {
    public static final String ACTION_QUICK = "ar.com.faro.v5.action.QUICK";
    public static final String EXTRA_QUICK_ID = "ar.com.faro.v5.extra.QUICK_ID";

    private final Set<String> allowedDeepLinkOrigins;
    private final Map<String, String> deepLinkTargetsByPath;
    private final Map<String, String> quickTargetsById;
    private final String shareTarget;

    public AndroidExternalIngressParser(
            Set<String> allowedDeepLinkOrigins,
            Map<String, String> deepLinkTargetsByPath,
            Map<String, String> quickTargetsById,
            String shareTarget) {
        Objects.requireNonNull(allowedDeepLinkOrigins, "allowedDeepLinkOrigins");
        Objects.requireNonNull(deepLinkTargetsByPath, "deepLinkTargetsByPath");
        Objects.requireNonNull(quickTargetsById, "quickTargetsById");

        Set<String> origins = new HashSet<>();
        for (String origin : allowedDeepLinkOrigins) {
            origins.add(normalizeConfiguredOrigin(origin));
        }
        this.allowedDeepLinkOrigins = Collections.unmodifiableSet(origins);

        Map<String, String> paths = new HashMap<>();
        for (Map.Entry<String, String> entry : deepLinkTargetsByPath.entrySet()) {
            String path = normalizePath(entry.getKey());
            paths.put(path, ExternalIngressTrigger.deepLink(entry.getValue(), "").getTarget());
        }
        this.deepLinkTargetsByPath = Collections.unmodifiableMap(paths);

        Map<String, String> quicks = new HashMap<>();
        for (Map.Entry<String, String> entry : quickTargetsById.entrySet()) {
            String id = normalizeQuickId(entry.getKey());
            quicks.put(id, ExternalIngressTrigger.quick(entry.getValue()).getTarget());
        }
        this.quickTargetsById = Collections.unmodifiableMap(quicks);
        this.shareTarget = ExternalIngressTrigger.quick(shareTarget).getTarget();
    }

    public Result parse(Intent intent) {
        if (intent == null || intent.getAction() == null) return Result.ignored();
        String action = intent.getAction();

        try {
            if (ACTION_QUICK.equals(action)) return parseQuick(intent);
            if (Intent.ACTION_VIEW.equals(action)) return parseDeepLink(intent);
            if (Intent.ACTION_SEND.equals(action)) return parseShare(intent, false);
            if (Intent.ACTION_SEND_MULTIPLE.equals(action)) return parseShare(intent, true);
            return Result.ignored();
        } catch (RuntimeException malformedExternalPayload) {
            // BadParcelableException, hostile extras and malformed Uri state fail closed.
            return Result.rejected();
        }
    }

    private Result parseQuick(Intent intent) {
        if (intent.getData() != null || intent.getClipData() != null) return Result.rejected();
        if (intent.getExtras() == null
                || intent.getExtras().keySet().size() != 1
                || !intent.getExtras().containsKey(EXTRA_QUICK_ID)) {
            return Result.rejected();
        }
        String quickId = intent.getStringExtra(EXTRA_QUICK_ID);
        if (quickId == null) return Result.rejected();
        String target = quickTargetsById.get(quickId.trim());
        if (target == null) return Result.rejected();
        return Result.accepted(ExternalIngressTrigger.quick(target), Collections.emptyList());
    }

    private Result parseDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data == null || !data.isHierarchical() || intent.getClipData() != null) {
            return Result.rejected();
        }
        if (intent.getExtras() != null && !intent.getExtras().isEmpty()) return Result.rejected();
        if (data.getFragment() != null) return Result.rejected();
        if (!allowedDeepLinkOrigins.contains(originOf(data))) return Result.rejected();

        String target = deepLinkTargetsByPath.get(data.getPath());
        if (target == null) return Result.rejected();

        Set<String> queryNames = data.getQueryParameterNames();
        if (!queryNames.isEmpty()
                && !(queryNames.size() == 1 && queryNames.contains("ref"))) {
            return Result.rejected();
        }
        List<String> refs = data.getQueryParameters("ref");
        if (refs.size() > 1) return Result.rejected();

        try {
            String reference = refs.isEmpty() ? "" : refs.get(0);
            return Result.accepted(
                    ExternalIngressTrigger.deepLink(target, reference),
                    Collections.emptyList());
        } catch (IllegalArgumentException invalid) {
            return Result.rejected();
        }
    }

    private Result parseShare(Intent intent, boolean multiple) {
        if (intent.getData() != null) return Result.rejected();

        String text = "";
        CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) text = sharedText.toString();

        List<Uri> uris = multiple ? multipleStreams(intent) : singleStream(intent);
        if (uris.size() > 4) return Result.rejected();
        for (Uri uri : uris) {
            if (uri == null
                    || !ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
                return Result.rejected();
            }
        }

        // ClipData is not forwarded. If a sender supplies URI content outside the standard
        // EXTRA_STREAM fields, reject rather than widening the accepted ingress surface.
        ClipData clipData = intent.getClipData();
        if (clipData != null && !clipDataMatches(clipData, uris)) return Result.rejected();

        try {
            ExternalIngressTrigger trigger =
                    ExternalIngressTrigger.share(shareTarget, text, uris.size());
            return Result.accepted(trigger, uris);
        } catch (IllegalArgumentException invalid) {
            return Result.rejected();
        }
    }

    private static boolean clipDataMatches(ClipData clipData, List<Uri> uris) {
        if (clipData.getItemCount() != uris.size()) return false;
        for (int index = 0; index < clipData.getItemCount(); index++) {
            Uri clipUri = clipData.getItemAt(index).getUri();
            if (clipUri == null || !uris.contains(clipUri)) return false;
            if (clipData.getItemAt(index).getIntent() != null) return false;
        }
        return true;
    }

    private static List<Uri> singleStream(Intent intent) {
        Uri stream = getSingleStream(intent);
        if (stream == null) return Collections.emptyList();
        return Collections.singletonList(stream);
    }

    private static List<Uri> multipleStreams(Intent intent) {
        ArrayList<Uri> streams = getMultipleStreams(intent);
        if (streams == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(streams));
    }

    @SuppressWarnings("deprecation")
    private static Uri getSingleStream(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Uri> getMultipleStreams(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    }

    private static String normalizeConfiguredOrigin(String origin) {
        if (origin == null) throw new NullPointerException("origin");
        Uri uri = Uri.parse(origin.trim());
        if (!uri.isHierarchical()
                || uri.getScheme() == null
                || uri.getHost() == null
                || uri.getPath() != null && !uri.getPath().isEmpty()
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("invalid deep-link origin");
        }
        return originOf(uri);
    }

    private static String originOf(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) return "";
        int port = uri.getPort();
        return scheme.toLowerCase(Locale.ROOT)
                + "://"
                + host.toLowerCase(Locale.ROOT)
                + (port >= 0 ? ":" + port : "");
    }

    private static String normalizePath(String path) {
        if (path == null) throw new NullPointerException("path");
        String normalized = path.trim();
        if (!normalized.startsWith("/")
                || normalized.length() > 128
                || normalized.contains("..")
                || normalized.contains("//")
                || normalized.contains("?")
                || normalized.contains("#")) {
            throw new IllegalArgumentException("invalid deep-link path");
        }
        return normalized;
    }

    private static String normalizeQuickId(String id) {
        if (id == null) throw new NullPointerException("quickId");
        String normalized = id.trim();
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("invalid quick id");
        }
        return normalized;
    }

    public enum Status {
        ACCEPTED,
        REJECTED,
        IGNORED
    }

    public static final class Result {
        private final Status status;
        private final ExternalIngressTrigger trigger;
        private final List<Uri> contentUris;

        private Result(
                Status status,
                ExternalIngressTrigger trigger,
                List<Uri> contentUris) {
            this.status = status;
            this.trigger = trigger;
            this.contentUris =
                    Collections.unmodifiableList(new ArrayList<>(contentUris));
        }

        static Result accepted(ExternalIngressTrigger trigger, List<Uri> contentUris) {
            return new Result(
                    Status.ACCEPTED,
                    Objects.requireNonNull(trigger, "trigger"),
                    contentUris);
        }

        static Result rejected() {
            return new Result(Status.REJECTED, null, Collections.emptyList());
        }

        static Result ignored() {
            return new Result(Status.IGNORED, null, Collections.emptyList());
        }

        public Status status() {
            return status;
        }

        public boolean isAccepted() {
            return status == Status.ACCEPTED;
        }

        public ExternalIngressTrigger trigger() {
            if (trigger == null) throw new IllegalStateException("ingress was not accepted");
            return trigger;
        }

        public List<Uri> contentUris() {
            return contentUris;
        }
    }
}
