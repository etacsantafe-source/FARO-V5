# X12 — adaptive layout and semantic accessibility, queued B1–B5
2026-11-12. Unit S3_A5_7_X12_ADAPTIVE_ACCESSIBILITY_SEMANTICS_ANDROID. Base c5f3ec06cf283864cc6759807da711bc1a6a6d52; X06 code 67bee2004800c2f81bbca7fe268d0f50d7228a92 changes test/workflow only. X06 and X08 scoped B7 must finish before READY. No code authorized here.

## B1 — exact gap and existing source
Do not rerun original native lifecycle 2/2, Chat Compose 2/2, or Search pause/recreate 1/1 as unchanged baseline. Remaining scope: actual configuration change to 200% font and expanded window, semantic reachability/action labels/target size, visible canonical notice and cross-destination revocation under those configurations.
Inspected FaroV5SurfaceHost.install -> FaroV5AdaptiveShell -> currentWindowAdaptiveInfoV2; permanent drawer chosen only when windowSizeClass.minWidthDp >=840. Shell uses saved backstack, menu contentDescription "Abrir menú" only for modal; NavigationDrawerItem has scrollable parent, not a plain static column. Avisos heading semantics and existing MaterialButton; FaroV5Theme supplies LocalMinimumInteractiveComponentSize from FaroV5Minimums.interactiveTarget. Search has editable label and clickable Search button, while drawer also contains a clickable Buscar; tests must select Role.Button, not first arbitrary text match in expanded layout. Existing DEV native button already admits Auxiliary via X02.
Current dependency BOM 2026.09.00; existing ui-test-junit4 is sufficient, no new accessibility library allowed in the two-path scope.

## B2 — reuse and comparison
Reference V4.4 layout behavior only. Use actual DEV Activity, native gesture, actual Auxiliary backup fixture and production AdaptiveShell, no fake page or replacement scaffold. Reuse tested lifecycle APIs and source-specific grants; new configuration is the material delta. Do not revalidate unrelated receipt/Apply/IA behavior. No production data migrations.

## B3 — external sources, options
Primary official sources read this turn:
- https://developer.android.com/about/versions/14/features : maximum font size 200% should be UI tested; nonlinear scaling is system-owned.
- https://developer.android.com/develop/ui/compose/accessibility/testing : semantic automated checks complement actual TalkBack/Switch Access; they cannot replace manual assistive-service validation.
- https://developer.android.com/develop/ui/compose/testing/common-patterns and https://developer.android.com/training/testing/different-screens/tools : configuration overrides are an alternative for composable-only tests.
- https://developer.android.com/guide/components/activities/testing and https://developer.android.com/develop/ui/compose/testing/synchronization : actual ActivityScenario recreations, observable waits rather than sleeps.
CHOOSE A: fixed UiAutomation shell commands on disposable CI emulator (font_scale, wm size/density) to exercise the actual Activity-owned setContent/AdaptiveShell and OS-driven recreation. Restore baseline in finally; never run on G15. SDK/AndroidX already maintained official dependencies, Apache-2.0 references only, no added code/library or paid cost.
REJECT B: replacing SurfaceHost with a forced test scaffold merely to inject DeviceConfigurationOverride; it could bypass actual adaptive selection and Activity grant revocation.
DEFER C: new ui-test-junit4-accessibility dependency (would expand Gradle scope); semantic labels/roles/bounds are feasible now, TalkBack remains separate physical scope.
REJECT D: screenshot alone or fake metrics; need actual resource fontScale/screenWidthDp, live visible semantic bounds and real source/owner tests.

## B4 — two paths, two directed tests, no production changes
1. v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X12AdaptiveSemanticsUiTest.kt
2. .github/workflows/faro-v5-s3-a5-7-x12-adaptive-semantics-android-directed.yml
Test x12LargeFontCanonicalAvisosActionsAndRevocation: set actual emulator font_scale 2.0, await Activity resources >=1.99; fresh private DEV fixture with canonical real Auxiliary NORMAL and SENSITIVE notice, no DeliveryIntent. Native Avisos gesture, modal drawer scroll to Avisos, assert labeled/clickable summary, heading semantics and visible notice bounds, no sensitive content/IDs. Check minimum interactive target and viewport inclusion; capture screenshot. Pause/resume redacts notice/summary, navigate Search and verify source unavailable without regrant; controls retain visible labels and correct Role.Button. Restore font_scale in finally.
Test x12ExpandedWindowNativeDrawerAndRestoredSourceIsolation: set emulator size/density to actual expanded width (e.g. 1600x1200 and 160 dpi), normal font, await screenWidthDp >=840. Fresh fixture/native action, permanent drawer (no menu icon), navigate Avisos via correctly identified drawer item, positive canonical notice + labeled summary visible, independent Search button selected by Role.Button rather than drawer label. Configuration/Activity recreate must discard grant and summary; fresh native gesture + restored destination handling can re-admit only Auxiliary. Capture wide screenshot and configuration record. Restore wm size/density/font in finally.
Only class above on API35 offline. Require actual 2/0/0/0 start/end XML, app/test identities, no INTERNET, APK hashes/signatures/source SHA, screenshot/config artifact and independent visual check before B7. If font/layout exposes a production bug, STOP NEEDS_PREP with exact delta first; do not weaken visibility/role/geometry assertions to pass. Native fixture connection closes before Activity opens; no parallel seed writers.

## B5 — adversarial and scoped decision
Do not equate partially clipped semantics with usable layout: compare unclipped control/text bounds to actual viewport, assert displayed and usable target size. Recreated screen may remain Avisos or Buscar via saved state; wait for current destination, never require Chat unconditionally. No duplicated gesture grants, cached summary, sensitive notices or reopened Search grant after pause/config return. Screenshot content must contain only synthetic CI fixture, no data/keys. Always restore emulator configuration even on failure, avoid ordering dependence.
No production hook/dependency/manifest edit, no Internet/OS delivery, physical G15, main/S2, paid API, V2 production, S4, Auto or Noche. This will certify only two configuration/semantics scenarios; manual TalkBack and complete X12/A5 acceptance remain OPEN.
DICTAMEN PREAUDITED_QUEUED_TWO_PATHS_NOT_READY_UNTIL_X08_B7_AND_HEAD_REVALIDATION.
