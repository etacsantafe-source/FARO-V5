# IME composer — B7 PASS scoped Android API35, 2026-11-12
Unit `S3_G15_IME_COMPOSER_RESIZE_ANDROID`.
Code SHA `923910dd28f963daf0263de87d2eada4f8263571`.
Android run `43302542761` SUCCESS; protocol guard `43302542646` SUCCESS.

## B7
Both actual Android35/Gboard tests passed: `imeComposerAndSendAboveRealKeyboardThenHidden` and `imeComposerRemainsReachableAfterBackgroundAndResume`. Second test uses real HOME + am start of existing singleTask Activity, requires RESUMED/window focus, then real editor touch, actual IME visibility, strict bounds and keyboard hide. No messages sent. Earlier manifest-only negative and lifecycle-only test timeout remain recorded as RED_FIX, not counted as passing runs.

Independent ZIP inspection verifies source SHA, named start/end instrumentation events exactly `(1,0)` once each, JUnit `2 tests,0 failures,0 errors,0 skipped`, both APK hashes, actual binary manifests/target package, and three evidence images. App package `ar.com.faro.g15isolated.v2coredev`, versionCode66/min29/target36; sole launcher CORE DEV, windowSoftInputMode16; backupfalse; only same-package signature receiver permission; no INTERNET/provider/receiver/service/alias. CI apksigner verified application and test APKs. PNG before/after real task switch inspected visually. No new signing configuration/credential was introduced.

Measurements identical shown/before-background/resumed: IME height833/top1507; composer `(0,1199)-(1080,1353)`, Enviar `(840,1386)-(1080,1496)`, enabled click action fully above keyboard. Hidden-keyboard checks also pass. Scaffold navigation inset is consumed once before imePadding; no extra bottom gap observed.

Artifact13983235915; ZIP SHA256 `1342dcad49d8bac0f2522132ad3cf71575a97e97983f1ea829a078948e03c2ef`; app APK SHA256 `26e45d5c79855a6a47df27498dfbfd05fefce9aa2c85f4e56f10f8b0c7b7c90c`. Machine record: `S3_G15_IME_ARTIFACT_VERIFICATION_20261112.json`.

PASS scoped to isolated DEV emulator/UI layout and real task return. Moto G15 physical correction NOT_RUN; previous physical navigation/A3/A4 results retained, SEND_CLICK_UNCERTAIN never retried. No Groq/provider/remote reasoning, no productive V2/S4/Auto/Noche activation. Main/V4.4/S2 unchanged by this unit. Permit consumed BLOCKED; writer released after checkpoint. A5 offline next requires its own READY after HEAD/preaudit revalidation; global A5/S3 and X03 live OPEN, formal S3 0/5.
