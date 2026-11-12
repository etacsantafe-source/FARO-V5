# X08 — test-owned empty-root precondition, static B7 RED_FIX
2026-11-12. Reviewed corrected code 743e28f4f6a6afb94ab66ba0b8dd8181e4353816. Run 43153903965 currently running; no runtime result invented.

## B1
Primary AndroidX implementation read live via inherited HTTPS: https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-test/src/androidMain/kotlin/androidx/compose/ui/test/ComposeUiTest.android.kt lines1055-1072. AndroidComposeUiTest.setContent requires android.R.id.content to have no children before it installs the test composition. Actual DEV onCreate populates a native ScrollView, so changing to rule.setContent alone fixes import but would violate that precondition. This is verified source-level defect in test mounting, not owner/ACL behavior.

## B2
DEV Activity has no Session for first two test-owned fixtures and no Compose mounted; it only shows native launcher. Safely detach that native content in this isolated test before rule.setContent. Third test uses no UI and stays unchanged. Existing Activity behavior and actual canonical fixtures remain intact.

## B3
USAR documented rule API with its actual primary-source precondition. Source above and AndroidComposeTestRule.android.kt forwarding setContent inspected. Alternative adding activity-compose or extra test Activity/dependencies rejected as needless scope. Alternative ignoring root guard rejected. AndroidX maintained Apache-2.0 reference only, no code vendored, no new dependency or payment.

## B4
Only same X08 test path. Inside mountActualSearch, runOnUiThread removes children from android.R.id.content, then rule.setContent installs existing SearchScreen. No production host changes, grants, mocks, assertions or workflow change. One composition mount per test.

## B5
Detach native launcher only when fixture test owns this Activity; no app Session exists or user data. Do not clear any owner store/grant when detaching view. Keep exact three tests/real snapshot boundary/owner close/pointer checks. Need new exact API35 runtime/XML/artifact/guard; no fake PASS. Current earlier run remains auditable.

## B7
RED_FIX_STATIC_TEST_RULE_EMPTY_ROOT. Previous code 743e28f4f6a6afb94ab66ba0b8dd8181e4353816 lacks required native-root detach. Same-unit single-path correction; no outcome claimed for ongoing CI. Original compile issue fixed independently; primary-source investigation now closes test mounting precondition before further runtime attempts.
