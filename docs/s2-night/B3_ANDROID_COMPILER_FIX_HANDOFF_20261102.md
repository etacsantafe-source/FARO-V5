# FARO V5 — Noche B3: Kotlin Android test compilation corrected

**Branch:** `work/s2-chat-finish-night-20261102`. **Functional correction:** [`feec3f2`](https://github.com/etacsantafe-source/FARO-V5/commit/feec3f2c1ed4659d083324332e1822d145a18e5c). Writer NONE; handoff CLEAN. S2.A6 OPEN 6/8; F1 (#4) and T4 (#8) pending; V2 OFF; main and S3/S4 untouched.

## Observed CI result

- [Run #41398844415](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41398844415): **overall failure**. `compile-and-directed-jvm` **SUCCESS**: isolated APK built; F1 JVM directed gates success; exact A5 XML oracle printed **9/9 PASS**. APK artifact ID 13121090679 was uploaded, **not physically verified**.
- `android-debug-chat-entry` **FAILURE** before any instrumentation: `:app:compileDebugAndroidTestKotlin` reports `TYPE_INTERSECTION_AS_REIFIED_ERROR` at `FaroV5A5TestContentProvider.kt:32`.
- Cause: `arrayOf(FILE_NAME, TEXT.toByteArray(...).size)` mixed String and Int and Kotlin inferred an unsupported reified intersection type. Fixed exactly to `arrayOf<Any?>(FILE_NAME, TEXT.toByteArray(Charsets.UTF_8).size)` for `MatrixCursor.addRow`. No product source or Android permissions changed.
- Correction committed on branch with blob SHA check, verified branch HEAD. This code commit is **not** `[skip ci]`; the directed workflow is expected to run. Documentation commits use `[skip ci]` to avoid overlapping work. **Do not infer that the new CI passed until checking its jobs and XML.**

## Next session

1. Recover the new CI run for code SHA `feec3f2c1ed4659d083324332e1822d145a18e5c`; do not start another if pending. Confirm Android 5/5 integrated (2 Chat/Review/Apply/Delivery/restart + 3 A5 ContentResolver) from exact XML.
2. If RED, correct only the actual issue. If green, validate the one isolated debug APK package `ar.com.faro.alpha.s2isolated.v5dev`, SHA256 and signing certificate. Prepare a single minimal Moto G15 guided T4 route without repeating SAF already green.
3. No real Groq calls, no user Android consent while sleeping, no 8/8 or production activation without physical evidence.

## Persistence
`docs/s2-night/RUN_STATE.json` was updated in checkpoint commit `863b56d8aecb39253d033d43fe8a8a5f36325627`. Writer NONE and handoff CLEAN.
