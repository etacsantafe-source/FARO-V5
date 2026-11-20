# FARO V5 — W03-E Android B7 scoped (19/11/2026)

Unit S4_W03_E_MATTERS_ANDROID_DEV, code SHA `5a2ae5417b0f25cf04b2291f72276d1f89e53f83`. Scope: isolated W03 DEV APK build and offline manifest only.

## B7 — PASS_SCOPED_BUILD_AND_ISOLATION

GitHub Actions mandatory protocol guard `44645128397` SUCCESS exact SHA. W03 Android workflow `44645128424` / job `135985632927` SUCCESS exact SHA; Gradle `:app:assembleW03MattersDev` BUILD SUCCESSFUL (132 tasks). `aapt` PASS: package `ar.com.faro.alpha.w03mattersdev`, one launcher `FaroV5W03MattersDevActivity`, no INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE.

APK `app-w03MattersDev.apk` SHA256 `31da7739c85c2d9fb36353eaab26301e9081f55803aa0b7060de9d04024e6b6c` per CI log. Artifact `w03-e-dev-apk-manifest` id 14587869971, zip digest `sha256:7af1e91befa6042af3c9d5d3bcb785a13effb0ed7aeed38296e13fb019079b1f` per Actions artifact metadata.

This B7 does NOT certify runtime behavior, Android API35 instrumentation, font scale 200%, TalkBack, receipts end-to-end, pause/ABA, or G15. Those remain W03-G NOT_RUN. W03-F OS delivery DEFERRED; no physical test, production V2 or main change. A/B/C/D previously certified and not rerun.
