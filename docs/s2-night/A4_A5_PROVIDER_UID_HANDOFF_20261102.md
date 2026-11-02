# FARO V5 — Noche A4: A5 Android provider UID hardening

**Branch:** `work/s2-chat-finish-night-20261102`. **Functional commit:** [`5d4add3`](https://github.com/etacsantafe-source/FARO-V5/commit/5d4add333389fe84d16a38ebf404404ca781c202). S2.A6 **OPEN 6/8**; F1 (#4) and T4 (#8) remain open. Writer NONE; handoff CLEAN; V2 OFF, main/S3/S4 untouched.

## Real code change

Android instrumentation runs in the **target app process**. A non-exported provider registered in the separate `androidTest` APK is not reliably accessible to the target app UID. Rather than export a synthetic provider to other apps, moved `FaroV5A5TestContentProvider` into the isolated **debug-only** app source and its manifest, retaining `android:exported="false"`. The A5 Android tests now resolve it through `InstrumentationRegistry.getInstrumentation().targetContext`. Test APK manifest no longer declares it. This is a real test integration correction, not a product permission change. No release/production source, Groq, user data, or signing changed.

Updated isolated S2 workflow push path filters to cover `v5/app`, `v5/surface`, `v5/kernel/contracts`, and `v5/platform`, so future changes to Chat/consent modules cannot bypass directed CI merely because app files were untouched.

## Verification boundary

- Prior run [#41398844415](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41398844415) was **FAILURE** overall; JVM succeeded and A5 9/9 XML PASS, Android test compilation failed in previous source and was fixed in B3.
- Newer CI for B3 commit `feec3f2c1ed4659d083324332e1822d145a18e5c` could not be identified from the available push-run lookup; **do not claim success or failure**.
- A4 commit deliberately has `[skip ci]` to avoid overlapping that run. No Android instrumentation, APK signature, or SHA attestation exists for A4 yet.

## Next turn

1. Check the in-flight B3 Actions (no overlap); if RED, inspect actual failure. A4 preemptively addresses potential test-only ContentResolver cross-UID access without exporting fixture.
2. After B3 finishes, trigger **one** directed CI for A4 source, confirm exact 9 A5 JVM + 4 F1 JVM + 5 Android tests from XML. If red, correct actual issue.
3. On green, validate **one** debug APK `ar.com.faro.alpha.s2isolated.v5dev`, signature and SHA-256, and minimal Moto G15 route. Do not repeat SAF, request Groq secret, claim 8/8, or activate V2.

## Additional A4 test hardening

- [Commit `9161ca1`](https://github.com/etacsantafe-source/FARO-V5/commit/9161ca157fffb242b62a7885dc030c6ecf8d30a2) adds an Android regression assertion that the provider is actually registered in the target app and remains `exported=false`. This is a test of the specific cross-UID fix, not a claim of Android PASS.
- Both A4 code commits carry `[skip ci]` because the B3 push run status could not be resolved via the available connector. Next run must validate the latest SHA after previous CI ends; never infer PASS.
