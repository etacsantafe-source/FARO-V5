# X02 — restored Avisos destination, B7 RED_FIX (static audit)
2026-11-12. Unit S3_A5_7_X02_AUXILIARY_AVISOS_AUTHORIZED_DEV_READ. Reviewed code 07480f65622f553be349f9550dfcee471f164742. Android run 43148379317 still IN_PROGRESS at audit; no test failure or PASS invented. Protocol guard 43148379082 SUCCESS.

## B1
The new openNative test helper waits exclusively for Chat's Enviar after every native click. Both tests recreate after selecting Avisos. Existing shell rememberSaveable retains its destination. Historical certified SearchBackgroundRevokeUiTest explicitly handles restored Search instead of waiting for Chat; the same real mechanism applies here. This is a concrete test synchronization defect found by source inspection, not evidence of a product ACL failure.

## B2
Reuse existing destination restoration and lifecycle semantics. Do not alter production navigation or certified prior tests. V4.4 reference UX only. Source: FaroV5S3A57SearchBackgroundRevokeUiTest explains restored destination.

## B3
Official Android ActivityScenario guidance https://developer.android.com/guide/components/activities/testing and Compose synchronization https://developer.android.com/develop/ui/compose/testing/synchronization read 2026-11-12: use lifecycle scenario and observable UI state for asynchronous work. ADAPT existing restored-destination helper. Reject forced navigation reset (changes app behavior) and timeout increase (does not resolve wrong destination). No dependencies, cost or network in app. AndroidX source https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java documents saved state recreation, Apache-2.0 existing reference, no code vendored.

## B4
One exact path only: v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X02AuxiliaryAvisosUiTest.kt.
Wait for either Chat or restored Avisos semantics after native open, and navigate drawer only if Chat is actually shown. Preserve no-gesture denial, explicit grant, privacy/filtering, pause/recreate revocation and owner immutability assertions. Two tests still required; no relaxed production acceptance.

## B5
A native screen alone never satisfies the wait. Restored unavailable Avisos may satisfy mount but positive test still requires actual canonical public text after explicit grant. No fake owner/hit, no production edits or broadened paths. New run is causal test fix in same unit, not repetition of certified tests.

## B7
RED_FIX_STATIC_TEST_RESTORED_DESTINATION. Same unit only; no PASS yet. First CI remains an independent recorded result when it completes. Restrict READY to the single test path, publish documentary guard then preflight before correction. B7 PASS requires new exact SHA CI 2/0/0/0 XML, artifact hashes/manifests and guard.
