# FARO V5 — W03-G global Android DEV · B1–B5 preaudit (19/11/2026)

Unit `S4_W03_G_GLOBAL_B7_ANDROID_DEV`. Base `5a2ae5417b0f25cf04b2291f72276d1f89e53f83`, W03-E B7 scoped at `docs/v5/FARO_S4_W03_E_MATTERS_ANDROID_B7_PASS_SCOPED_20261119.md`. This document is NOT READY; require preaudit-document guard SUCCESS, separate permission READY and preflight before B6.

## B1 — Evidence

A/B/C/D owners integrated with JVM B7, E compilation/offline manifest PASS `44645128424`, mandatory guard `44645128397`. Missing real Android API35 user action and receipt, pause/resume/rotation, font 200%, Notice attention and recovery tests. W02 native+Compose Android instrumentation exists at `v5/app/src/w02AndroidTest/kotlin/ar/com/faro/v5/app/S4W02ObjectivesAndroidTest.kt`.

## B2 — V4.4 comparison and reuse

Legacy V4.4 `FaroV4FollowUpSurface.kt` reference as cited by E B2: dates, state, priorities, reminders. Reuse W02 `createAndroidComposeRule`, native View label/click helper, actual Android Session, XML checks, README-free isolated build setup. Never reuse V4.4 database, service or OS delivery. Chat default and menu route ASUNTOS, readable UI at font 200%.

## B3 — External primary alternatives

ADAPT AndroidX official instrumented testing https://developer.android.com/training/testing/instrumented-tests, Espresso https://developer.android.com/training/testing/espresso/setup, Android accessibility https://developer.android.com/guide/topics/ui/accessibility/testing and Espresso accessibility https://developer.android.com/training/testing/espresso/accessibility-checking. Compare: instrumentation on real emulator observes UI/SQLite lifecycle; JVM fake owners and screenshot-only cannot prove integration. No new external runtime SDK/API/fee or pasted licensed source. Prefer AndroidX Test dependencies already in repo.

## B4 — Unified exact routes and test obligations

API35 google_apis x86_64; font_scale 2.0. Build isolated W03 app and instrumented APK; JUnit XML, manifest, APK hash and artifact. Drive explicit DEV onboarding via Activity foreground: Reality8→Finance9→Auxiliary4, each session closed before switching. Chat is launch destination, menu→Asuntos. Test create/cancel and Review→Approve→Apply/receipt for Matter, status/edit/history/reopen/Undo when valid; Reminder create/update/cancel and authorized Matter link; Avisos canonical read, exact Notice revision and explicit Seen/Dismiss CAS receipt, no OS delivery; session pause and stale permission must deny; API35 200% accessibility and persistence after re-create. Negative controls must assert **no write** on discard or revoked foreground. All checks must run real owners; never mock receipts or infer delivery.

Bounded 10 paths (G may fix observed W03-only defects; no unrelated scope):
- `v5/app/build.gradle.kts`
- `v5/app/src/w03AndroidTest/kotlin/ar/com/faro/v5/app/S4W03MattersAndroidTest.kt`
- `.github/workflows/faro-v5-s4-w03-g-global-android.yml`
- `v5/app/src/w03MattersDev/kotlin/ar/com/faro/v5/app/FaroV5W03MattersDevActivity.kt`
- `v5/app/src/w03MattersDev/kotlin/ar/com/faro/v5/app/FaroV5W03MattersPanel.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactory.kt`
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurface.kt`
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt`

## B5 — Adversarial STOP cases

Reject fake success for just APK compilation; Android screen at 200% only is insufficient. Test stale revision, denied grants after pause, lost session/generation, no Apply without review+approval, no read of secret Notice summary, no Notice click without original revision, no dismissal of Matter/Reminder by dismissing Notice, no duplicate transactions/replay, UNKNOWN only reconcile via receipt. If unable to prove key assertion, report NOT_RUN and hold global B7. No production V2, main, V4.4, OS permissions, physical G15 or autos. B6 code/test only with exact READY; CI real and B7 afterwards.
