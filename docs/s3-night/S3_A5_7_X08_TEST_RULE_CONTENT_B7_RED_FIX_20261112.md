# X08 — test-rule content API, B7 RED_FIX
2026-11-12. Unit S3_A5_7_X08_REAL_OWNER_INTERRUPTION_ABA_ANDROID, code 760d1140571055f42702ff220d15edb34eb28d13.

## B1
Android run 43152882781 job 130965985798 COMPLETED FAILURE, source 760d1140571055f42702ff220d15edb34eb28d13. Compiler :app:compileV2CoreDevAndroidTestKotlin reports UNRESOLVED_IMPORT, Unresolved reference 'setContent', line3 of new X08 test. No Android cases executed or XML certified. Code guard 43152882652 SUCCESS. Actual app compilation succeeded; test source import is the defect.

## B2
Existing app test dependencies include ui-test-junit4, but activity-compose is implementation-owned inside :surface, not a new direct test dependency. No production or Gradle change needed. Keep actual source/adapter fixture, three new cases and scope unchanged. V4.4 irrelevant to test compilation.

## B3
Official current https://developer.android.com/develop/ui/compose/accessibility/testing shows composeTestRule.setContent for test-owned composition; https://developer.android.com/develop/ui/compose/testing/common-patterns and synchronization guidance already read. USAR existing AndroidComposeTestRule.setContent API. REJECT adding new dependency/exporting :surface implementation, widening Gradle scope or replacing source assertions. No library/network/cost.

## B4
Only v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X08OwnerInterruptionUiTest.kt: remove androidx.activity.compose.setContent import; mount SearchScreen with rule.setContent directly rather than Activity extension in runOnUiThread. Rule handles main-thread content mounting and synchronization. Native DEV launcher has not installed Compose for these two test-owned compositions; one setContent per test. Preserve all owner/ACL/ABA and 3-test XML/APK acceptance.

## B5
Do not alter typed sources, owner reads, interruption boundary, grants, assertions, workflows or manifest. Real three-case API35 rerun is necessary for this actual compile correction, not repetition of certified tests. No PASS before new exact SHA compile/runtime/artifact/guard. Restrict READY to single test path after documentary guard.

## B7
RED_FIX_CAUSAL_TEST_COMPOSITION_API, previous code 760d1140571055f42702ff220d15edb34eb28d13. No runtime result claimed. Same-unit correction only, then exact API35 3/0/0/0 XML, APK/hash/manifest and independent B7.
