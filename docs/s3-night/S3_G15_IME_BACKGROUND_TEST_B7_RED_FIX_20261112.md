# IME — B7 RED_FIX 2: real task foreground, 2026-11-12
Unit `S3_G15_IME_COMPOSER_RESIZE_ANDROID`; code `8fca8f47bcfd1ed832f9be58e57038dbf7e92c87`.
Android API35 `43300304067`, guard `43300303644` SUCCESS.

## B7
Android compiled and verified the actual isolated APK/manifest. Instrumentation: first test SUCCESS (code0), second FAIL (code-2): timeout waiting for OS keyboard visibility AFTER synthetic ActivityScenario STARTED/RESUMED. It did not fail the geometry check. This is 1/2, NOT B7 PASS.

Artifact `13980499203` downloaded and independently hashed: ZIP `f972afb3978fee6bceeee393bffb6440ed0d30d9dd996ba0460c5301fce351a8`. Both initial and before-background measurements: IME top1507 height833; composer bounds0,1199–1080,1353; Enviar840,1386–1080,1496. PNG manually inspected: real Gboard, composer and enabled Enviar fully visible. Binary AndroidManifest.xml parsed independently from APK: exact package ar.com.faro.g15isolated.v2coredev, Activity windowSoftInputMode16, target36/min29, allowBackupfalse, only self receiver permission, no INTERNET or background component. Functional insets hypothesis succeeded; no further product delta inferred from the later timeout.

## Causal B2/B3/B5 delta
ActivityScenario moveToState models lifecycle; it does not demonstrate a real Home/task/window-focus transition. Current test does not assert that the app has regained actual window focus before touching a text input. Replace ONLY the second test's synthetic transition with real Android HOME then `am start -W` of the same singleTask Activity; wait for actual RESUMED + window focus before a physical editor touch. Keep actual OS IME show/hide and strict geometry assertions. Add failure capture of screenshot, IME state and current window focus BEFORE propagating a visibility timeout. No force-show keyboard, looser geometry or automatic Send. Same test path already READY; no extra production code or scope.

This is a distinct test orchestration hypothesis, not a third attempt of manifest-only resize. A second failure of the REAL task-focus hypothesis must return to B2/B3/B5 with diagnostics. A5 remains blocked on complete IME B7.
