# FARO V5 — S3.A5.7 Compose Android B2/B3/B5 review + B7 RED_FIX #2
Fecha 2026-11-09 AR. Unit S3_A5_7_COMPOSE_CHAT_REAL_ANDROID. Global A5 OPEN.

## B1 — Evidence
- Original preaudit B1–B5 docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md, blob 320d7895b4998f0e988266005bab9a647a169374, READY permit before initial code. Scope test-only Android emulator API35.
- First code d0c61074 CI #42592348610 FAILURE: UNRESOLVED_IMPORT `assertExists` test line 9. B7 RED_FIX docs/s3-night/S3_A5_7_COMPOSE_CHAT_B7_RED_FIX_IMPORT_20261109.md, blob 043c58138f30681b222aaa10cc137aa311a0dabe. First correction code 4186e0b22c1afa939e57c5e5eab3772f040f0a50 removed only that import.
- Second CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42593488175 job 129080477313 COMPLETED FAILURE SHA 4186e0b2, `:app:compileV2CoreDevAndroidTestKotlin` with Kotlin compiler UNRESOLVED_IMPORT `onAllNodes`, source FaroV5S3A57ComposeChatUiTest.kt:11. Build failed in 4m 41s. No Android test executed and no `OK (2 tests)`. Guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42593488965 COMPLETED SUCCESS same SHA. No production regression observed.

## B2 — Comparative root cause after two same-family failures
- Both failed imports are incorrectly declared as top-level extensions but are member functions of `SemanticsNodeInteraction` and `SemanticsNodeInteractionsProvider`. Their call sites themselves are legal; wrong imports are the ONLY compiler errors returned on their respective CI attempts.
- Existing FARO test v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5F1QuarantinedComposeInstrumentationTest.kt uses `compose.onAllNodes(hasSetTextAction())` WITHOUT importing `onAllNodes`. It imports valid public `onNodeWithText`, `onAllNodesWithText`, `performClick`, `performTextInput`, `hasSetTextAction`.
- Current test imports `assertCountEquals`, `assertIsEnabled`, `assertIsNotEnabled`, `hasSetTextAction`, `createAndroidComposeRule`, `onAllNodesWithText`, `onNodeWithText`, `performClick`, `performTextInput`; Kotlin compiler's latest exhaustive error lists ONLY `onAllNodes` unsupported after removing `assertExists`. Do not modify any of these validated imports, test or production APIs.

## B3 — External source and options
- Android official Compose Testing API https://developer.android.com/develop/ui/compose/testing/apis shows `composeTestRule.onAllNodes(...)` receiver call: member requires no import. `SemanticsNodeInteraction.assertExists()` is also invoked as member. ADAPT existing code.
- Android official Compose testing https://developer.android.com/develop/ui/compose/testing documents `createAndroidComposeRule`, semantics, actions and synchronization: test architecture remains valid, USAR.
- Alternative A chosen: remove precisely the single remaining invalid import; no assertion rewrite nor dependencies. Alternative B rejected: replace real UI with fake/stubs or add testing dependency (already present). Alternative C rejected: repeat full historic Android/33 suites; unrelated to compile error.

## B4 — Only necessary implementation
- Update `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ComposeChatUiTest.kt` by deleting ONE line `import androidx.compose.ui.test.onAllNodes`; preserve both @Test and every assertion.
- No modifications to workflow, build tools, Activity, Compose surface, owners, generation, ACL, Apply, S4, catalog or G15. Git writer manual CAS, same unit and test-only approved path.

## B5 — Adversarial gate and STOP
- Two failures of same import hypothesis triggered this deeper B2/B3/B5 review; now no unsupported import remains among known compiler errors. Run directed CI again, require exact Android API35 `OK (2 tests)`, `INSTRUMENTATION_CODE: -1` and same SHA protocol guard. If failure occurs at runtime or distinct compile issue, stop and diagnose fresh B7; no assertion relaxation or repeated blind import edits.
- Do not modify previously certified A5 33/33, X07/X11 4/4, native X12 2/2. A3 Guardados official catalog172 frozen, V2 productive/S4/Auto/Noche OFF, Moto G15 single final campaign.

## B7 — Independent outcome of SHA 4186e0b2
**RED_FIX, NOT PASS** for Compose UI: job failed before instrumentation, user-facing Compose X12 acceptance NOT_RUN. Proven bounded correction is deletion of import `onAllNodes` only. Permission is SAME unit, with this exact B7 proof of previous code SHA; next B7 must inspect newly generated SHA/log instead of assuming success.
