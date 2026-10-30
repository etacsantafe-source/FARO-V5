package ar.com.faro.v5.app;

import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import ar.com.faro.v5.surface.FaroV5SurfaceHost;
import ar.com.faro.v5.control.OnboardingMutationOutcome;
import ar.com.faro.v5.control.OnboardingSnapshot;
import java.util.UUID;

/**
 * Bounded S0 bootstrap/maintenance presentation.
 *
 * <p>This Activity owns no Reality, stores or domain navigation. Maintenance controls are limited
 * to retrying canonical startup recovery and revealing local/redacted diagnostic detail.
 */
public final class FaroV5BootstrapActivity extends ComponentActivity {
    private static final long SPLASH_MAX_HOLD_MILLIS = 500L;

    private volatile FaroV5StartupReadiness.State latestStartupState =
            FaroV5StartupReadiness.State.INITIALIZING;

    private FaroV5Application faroApplication;
    private TextView titleView;
    private TextView bodyView;
    private TextView supportingView;
    private ProgressBar progressView;
    private Button onboardingPrimaryButton;
    private Button onboardingLaterButton;
    private Button retryButton;
    private Button diagnosticsButton;
    private TextView diagnosticsView;
    private boolean diagnosticsExpanded;
    private OnboardingLoadState onboardingLoadState = OnboardingLoadState.NOT_REQUESTED;
    private OnboardingSnapshot onboardingSnapshot;
    private long onboardingEpoch;
    private FaroV5HostSurfacePolicy.Surface mountedSurface =
            FaroV5HostSurfacePolicy.Surface.BOOTSTRAP;

    private final FaroV5StartupReadinessListener readinessListener =
            readiness -> {
                latestStartupState = readiness.getState();
                runOnUiThread(
                        () -> {
                            if (!isDestroyed()) {
                                render(readiness);
                            }
                        });
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long splashHoldDeadline =
                SystemClock.uptimeMillis() + SPLASH_MAX_HOLD_MILLIS;
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(
                () ->
                        latestStartupState
                                        == FaroV5StartupReadiness.State.INITIALIZING
                                && SystemClock.uptimeMillis() < splashHoldDeadline);

        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());

        faroApplication = (FaroV5Application) getApplication();
        latestStartupState = faroApplication.getStartupReadiness().getState();
        setContentView(createContent());
        mountedSurface = FaroV5HostSurfacePolicy.Surface.BOOTSTRAP;
        render(faroApplication.getStartupReadiness());
    }

    @Override
    protected void onStart() {
        super.onStart();
        faroApplication.addStartupReadinessListener(readinessListener);
        render(faroApplication.getStartupReadiness());
    }

    @Override
    protected void onStop() {
        faroApplication.removeStartupReadinessListener(readinessListener);
        super.onStop();
    }

    private View createContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.faro_bootstrap_surface));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        int horizontalPadding = dp(28);
        int verticalPadding = dp(36);

        titleView = new TextView(this);
        titleView.setTextColor(getColor(R.color.faro_bootstrap_on_surface));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxWidth(dp(560));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            titleView.setAccessibilityHeading(true);
        }
        titleView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(
                titleView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        bodyView = new TextView(this);
        bodyView.setTextColor(getColor(R.color.faro_bootstrap_on_surface));
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setMaxWidth(dp(560));
        LinearLayout.LayoutParams bodyParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(16);
        content.addView(bodyView, bodyParams);

        progressView = new ProgressBar(this);
        progressView.setIndeterminate(true);
        progressView.setContentDescription(
                getString(R.string.bootstrap_progress_accessibility));
        progressView.getIndeterminateDrawable()
                .setTint(getColor(R.color.faro_bootstrap_primary));
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(24);
        content.addView(progressView, progressParams);

        supportingView = new TextView(this);
        supportingView.setTextColor(
                getColor(R.color.faro_bootstrap_on_surface_secondary));
        supportingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        supportingView.setGravity(Gravity.CENTER);
        supportingView.setMaxWidth(dp(560));
        LinearLayout.LayoutParams supportingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        supportingParams.topMargin = dp(20);
        content.addView(supportingView, supportingParams);

        onboardingPrimaryButton = new Button(this);
        onboardingPrimaryButton.setText(R.string.onboarding_start_action);
        onboardingPrimaryButton.setAllCaps(false);
        onboardingPrimaryButton.setMaxWidth(dp(360));
        stylePrimaryButton(onboardingPrimaryButton);
        onboardingPrimaryButton.setVisibility(View.GONE);
        onboardingPrimaryButton.setOnClickListener(
                ignored -> submitOnboarding(false));
        LinearLayout.LayoutParams onboardingPrimaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        onboardingPrimaryParams.gravity = Gravity.CENTER_HORIZONTAL;
        onboardingPrimaryParams.topMargin = dp(24);
        content.addView(onboardingPrimaryButton, onboardingPrimaryParams);

        onboardingLaterButton = new Button(this);
        onboardingLaterButton.setText(R.string.onboarding_later_action);
        onboardingLaterButton.setAllCaps(false);
        onboardingLaterButton.setMaxWidth(dp(360));
        styleSecondaryButton(onboardingLaterButton);
        onboardingLaterButton.setVisibility(View.GONE);
        onboardingLaterButton.setOnClickListener(
                ignored -> submitOnboarding(true));
        LinearLayout.LayoutParams onboardingLaterParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        onboardingLaterParams.gravity = Gravity.CENTER_HORIZONTAL;
        onboardingLaterParams.topMargin = dp(8);
        content.addView(onboardingLaterButton, onboardingLaterParams);

        retryButton = new Button(this);
        retryButton.setText(R.string.bootstrap_retry_action);
        retryButton.setAllCaps(false);
        retryButton.setMaxWidth(dp(360));
        stylePrimaryButton(retryButton);
        retryButton.setOnClickListener(
                ignored -> {
                    retryButton.setEnabled(false);
                    if (!faroApplication.retryStartupRecovery()) {
                        render(faroApplication.getStartupReadiness());
                    }
                });
        LinearLayout.LayoutParams retryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.gravity = Gravity.CENTER_HORIZONTAL;
        retryParams.topMargin = dp(24);
        content.addView(retryButton, retryParams);

        diagnosticsButton = new Button(this);
        diagnosticsButton.setText(R.string.bootstrap_diagnostics_show);
        diagnosticsButton.setAllCaps(false);
        diagnosticsButton.setMaxWidth(dp(360));
        styleSecondaryButton(diagnosticsButton);
        diagnosticsButton.setOnClickListener(
                ignored -> {
                    diagnosticsExpanded = !diagnosticsExpanded;
                    render(faroApplication.getStartupReadiness());
                });
        LinearLayout.LayoutParams diagnosticsButtonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        diagnosticsButtonParams.gravity = Gravity.CENTER_HORIZONTAL;
        diagnosticsButtonParams.topMargin = dp(8);
        content.addView(diagnosticsButton, diagnosticsButtonParams);

        diagnosticsView = new TextView(this);
        diagnosticsView.setTextColor(
                getColor(R.color.faro_bootstrap_on_surface_secondary));
        diagnosticsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        diagnosticsView.setTypeface(Typeface.MONOSPACE);
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setMaxWidth(dp(560));
        LinearLayout.LayoutParams diagnosticsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        diagnosticsParams.topMargin = dp(16);
        content.addView(diagnosticsView, diagnosticsParams);

        scroll.addView(
                content,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        applySystemInsets(scroll, horizontalPadding, verticalPadding);
        return scroll;
    }

    private void applySystemInsets(
            View root,
            int horizontalPadding,
            int verticalPadding) {
        root.setOnApplyWindowInsetsListener(
                (view, windowInsets) -> {
                    int left;
                    int top;
                    int right;
                    int bottom;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets bars =
                                windowInsets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        left = bars.left;
                        top = bars.top;
                        right = bars.right;
                        bottom = bars.bottom;
                    } else {
                        left = windowInsets.getSystemWindowInsetLeft();
                        top = windowInsets.getSystemWindowInsetTop();
                        right = windowInsets.getSystemWindowInsetRight();
                        bottom = windowInsets.getSystemWindowInsetBottom();
                    }
                    view.setPadding(
                            horizontalPadding + left,
                            verticalPadding + top,
                            horizontalPadding + right,
                            verticalPadding + bottom);
                    return windowInsets;
                });
        root.requestApplyInsets();
    }

    private void render(FaroV5StartupReadiness readiness) {
        latestStartupState = readiness.getState();

        if (!readiness.isLocalReady()) {
            invalidateOnboardingState();
            ensureBootstrapSurface();
            renderBootstrap(readiness);
            return;
        }

        renderLocalReady(readiness);
    }

    private void renderBootstrap(FaroV5StartupReadiness readiness) {
        FaroV5BootstrapPresentation.Model model =
                FaroV5BootstrapPresentation.from(readiness);

        titleView.setText(model.getTitleResId());
        bodyView.setText(model.getBodyResId());
        progressView.setVisibility(
                model.shouldShowProgress() ? View.VISIBLE : View.GONE);
        onboardingPrimaryButton.setVisibility(View.GONE);
        onboardingLaterButton.setVisibility(View.GONE);

        if (model.getSupportingResId() == 0) {
            supportingView.setVisibility(View.GONE);
            supportingView.setText("");
        } else {
            supportingView.setText(model.getSupportingResId());
            supportingView.setVisibility(View.VISIBLE);
        }

        if (!model.shouldShowMaintenanceActions()) {
            retryButton.setVisibility(View.GONE);
            diagnosticsButton.setVisibility(View.GONE);
            diagnosticsView.setVisibility(View.GONE);
            diagnosticsExpanded = false;
            return;
        }

        retryButton.setVisibility(View.VISIBLE);
        retryButton.setEnabled(true);
        diagnosticsButton.setVisibility(View.VISIBLE);
        diagnosticsButton.setText(
                diagnosticsExpanded
                        ? R.string.bootstrap_diagnostics_hide
                        : R.string.bootstrap_diagnostics_show);

        if (!diagnosticsExpanded) {
            diagnosticsView.setVisibility(View.GONE);
            diagnosticsView.setText("");
            return;
        }

        diagnosticsView.setText(
                getString(R.string.bootstrap_diagnostics_title)
                        + "\n"
                        + getString(R.string.bootstrap_diagnostics_supporting)
                        + "\n\n"
                        + FaroV5MaintenanceDiagnostics.from(readiness));
        diagnosticsView.setVisibility(View.VISIBLE);
    }

    private void renderLocalReady(FaroV5StartupReadiness readiness) {
        if (onboardingLoadState == OnboardingLoadState.NOT_REQUESTED) {
            ensureBootstrapSurface();
            renderReadyPlaceholder();
            onboardingLoadState = OnboardingLoadState.LOADING;
            long requestEpoch = ++onboardingEpoch;
            faroApplication.readOnboardingState(
                    snapshot ->
                            runOnUiThread(
                                    () -> {
                                        if (!acceptOnboardingCallback(requestEpoch)) {
                                            return;
                                        }
                                        onboardingSnapshot = snapshot;
                                        onboardingLoadState = OnboardingLoadState.LOADED;
                                        render(faroApplication.getStartupReadiness());
                                    }),
                    failure ->
                            runOnUiThread(
                                    () -> {
                                        if (!acceptOnboardingCallback(requestEpoch)) {
                                            return;
                                        }
                                        onboardingSnapshot = null;
                                        onboardingLoadState = OnboardingLoadState.UNAVAILABLE;
                                        render(faroApplication.getStartupReadiness());
                                    }));
            return;
        }

        FaroV5HostSurfacePolicy.OnboardingGate gate = onboardingGate();
        FaroV5HostSurfacePolicy.Surface target =
                FaroV5HostSurfacePolicy.resolve(readiness, gate);

        if (target == FaroV5HostSurfacePolicy.Surface.SHELL) {
            showShellSurface();
            return;
        }

        ensureBootstrapSurface();
        if (target == FaroV5HostSurfacePolicy.Surface.BOOTSTRAP) {
            renderReadyPlaceholder();
            return;
        }

        renderOnboarding();
    }

    private FaroV5HostSurfacePolicy.OnboardingGate onboardingGate() {
        if (onboardingLoadState == OnboardingLoadState.NOT_REQUESTED
                || onboardingLoadState == OnboardingLoadState.LOADING) {
            return FaroV5HostSurfacePolicy.OnboardingGate.UNRESOLVED;
        }

        OnboardingSnapshot snapshot = onboardingSnapshot;
        if (onboardingLoadState == OnboardingLoadState.LOADED
                && snapshot != null
                && snapshot.shouldPresentAutomatically()) {
            return FaroV5HostSurfacePolicy.OnboardingGate.PRESENT;
        }

        return FaroV5HostSurfacePolicy.OnboardingGate.CLEAR;
    }

    private void renderReadyPlaceholder() {
        progressView.setVisibility(View.GONE);
        titleView.setText(R.string.bootstrap_ready_title);
        bodyView.setText(R.string.bootstrap_ready_body);
        supportingView.setVisibility(View.GONE);
        supportingView.setText("");
        onboardingPrimaryButton.setVisibility(View.GONE);
        onboardingLaterButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        diagnosticsButton.setVisibility(View.GONE);
        diagnosticsView.setVisibility(View.GONE);
        diagnosticsExpanded = false;
    }

    private void renderOnboarding() {
        progressView.setVisibility(View.GONE);
        titleView.setText(R.string.onboarding_welcome_title);
        bodyView.setText(R.string.onboarding_welcome_body);
        supportingView.setText(R.string.onboarding_welcome_supporting);
        supportingView.setVisibility(View.VISIBLE);
        onboardingPrimaryButton.setEnabled(true);
        onboardingLaterButton.setEnabled(true);
        onboardingPrimaryButton.setVisibility(View.VISIBLE);
        onboardingLaterButton.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        diagnosticsButton.setVisibility(View.GONE);
        diagnosticsView.setVisibility(View.GONE);
        diagnosticsExpanded = false;
        mountedSurface = FaroV5HostSurfacePolicy.Surface.ONBOARDING;
    }

    private void ensureBootstrapSurface() {
        if (mountedSurface == FaroV5HostSurfacePolicy.Surface.SHELL) {
            setContentView(createContent());
        }
        mountedSurface = FaroV5HostSurfacePolicy.Surface.BOOTSTRAP;
    }

    private void showShellSurface() {
        if (mountedSurface == FaroV5HostSurfacePolicy.Surface.SHELL) {
            return;
        }

        FaroV5SurfaceHost.install(
                this,
                faroApplication.readActiveWorkspacePort(),
                faroApplication.readActiveTimelinePort(),
                faroApplication.activeComposerPort(),
                faroApplication.activeReviewPort(),
                faroApplication.activeWorkspaceSelectionPort(),
                faroApplication.activeApplyRequestPort(),
                faroApplication.activeAssistantTurnPort());
        mountedSurface = FaroV5HostSurfacePolicy.Surface.SHELL;
    }

    private boolean acceptOnboardingCallback(long callbackEpoch) {
        return !isDestroyed()
                && callbackEpoch == onboardingEpoch
                && faroApplication.getStartupReadiness().isLocalReady();
    }

    private void invalidateOnboardingState() {
        if (onboardingLoadState == OnboardingLoadState.NOT_REQUESTED
                && onboardingSnapshot == null) {
            return;
        }
        onboardingEpoch++;
        onboardingLoadState = OnboardingLoadState.NOT_REQUESTED;
        onboardingSnapshot = null;
    }

    private void submitOnboarding(boolean defer) {
        OnboardingSnapshot snapshot = onboardingSnapshot;
        if (snapshot == null || onboardingLoadState != OnboardingLoadState.LOADED) {
            return;
        }

        onboardingPrimaryButton.setEnabled(false);
        onboardingLaterButton.setEnabled(false);
        long mutationEpoch = onboardingEpoch;
        String idempotencyKey =
                "onboarding-"
                        + (defer ? "defer-" : "complete-")
                        + snapshot.getCurrentVersion()
                        + "-"
                        + UUID.randomUUID();

        java.util.function.Consumer<OnboardingMutationOutcome> onSuccess =
                outcome ->
                        runOnUiThread(
                                () -> {
                                    if (!acceptOnboardingCallback(mutationEpoch)) {
                                        return;
                                    }
                                    onboardingSnapshot = outcome.getSnapshot();
                                    onboardingLoadState = OnboardingLoadState.LOADED;
                                    render(faroApplication.getStartupReadiness());
                                });
        java.util.function.Consumer<Exception> onFailure =
                failure ->
                        runOnUiThread(
                                () -> {
                                    if (!acceptOnboardingCallback(mutationEpoch)) {
                                        return;
                                    }
                                    onboardingSnapshot = null;
                                    onboardingLoadState = OnboardingLoadState.UNAVAILABLE;
                                    render(faroApplication.getStartupReadiness());
                                });

        if (defer) {
            faroApplication.deferOnboarding(
                    snapshot.getRevision(),
                    idempotencyKey,
                    onSuccess,
                    onFailure);
        } else {
            faroApplication.completeOnboarding(
                    snapshot.getRevision(),
                    idempotencyKey,
                    onSuccess,
                    onFailure);
        }
    }

    private enum OnboardingLoadState {
        NOT_REQUESTED,
        LOADING,
        LOADED,
        UNAVAILABLE
    }

    private void stylePrimaryButton(Button button) {
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(120));
        button.setPadding(dp(20), dp(10), dp(20), dp(10));
        button.setTextColor(getColor(R.color.faro_bootstrap_on_primary));
        button.setBackgroundTintList(
                ColorStateList.valueOf(getColor(R.color.faro_bootstrap_primary)));
    }

    private void styleSecondaryButton(Button button) {
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(120));
        button.setPadding(dp(20), dp(10), dp(20), dp(10));
        button.setTextColor(getColor(R.color.faro_bootstrap_on_surface));
        button.setBackgroundTintList(
                ColorStateList.valueOf(getColor(R.color.faro_bootstrap_surface)));
    }

    private int dp(int value) {
        return Math.round(
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        value,
                        getResources().getDisplayMetrics()));
    }
}
