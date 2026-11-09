# FARO V5 — S3.A5.7 Compose X12 RED_FIX3 B6 WAIT_CI
Fecha 2026-11-09 AR. Unit S3_A5_7_COMPOSE_CHAT_REAL_ANDROID. Global A5 OPEN.

- Original B1–B5 preaudit docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md.
- Three Kotlin test-only compile errors resolved with dedicated B7 RED_FIX docs: CI #42592348610 unresolved import assertExists; CI #42593488175 unresolved import onAllNodes; CI #42594626211 unresolved onLast selector. All three FAILED before Android instrumentation; none counted PASS. The latest root-cause audit docs/s3-night/S3_A5_7_COMPOSE_CHAT_B7_REDFIX3_SELECTOR_API_20261109.md blob 633add2edab2728c9d9e690b1faccf49ff975459 uses official AndroidX SemanticsNodeInteractionCollection member get(index).
- Fourth code SHA 82e0858f6f18879aacb3d6d7d63fb9180e6eceeb replaces only one selector expression in test; the two original Android UI tests remain with original assertions; no production/manifest/Gradle/workflow change.
- CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42595625941 and mandatory guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42595625984 QUEUED at initial observation, results NOT_VERIFIED. Exact required output: Android API35 real emulator, stdout OK (2 tests), INSTRUMENTATION_CODE: -1, guard same SHA SUCCESS. No PASS assumed.
- Previous six A5 subunits 33/33, X07/X11 four JVM 4/4 and native X12 two emulator 2/2 preserved PASS_SCOPED untouched. X01/X02 preaudit docs/s3-night/S3_A5_7_X01_X02_REAL_ACTIVITY_AVISOS_UI_B1_B5_20261109.md is DOC_ONLY, NO READY permit until Compose B7.
- Catálogo 172, A3 Guardados blocked, S4/V2 productive/Auto/Noche OFF, Moto G15 not used (one final physical campaign).

Next: verify exact new CI and guard. If green audit B7 PASS_SCOPED, if red audit exact novel reason without relaxing tests. Writer must RELEASE after checkpoint.
