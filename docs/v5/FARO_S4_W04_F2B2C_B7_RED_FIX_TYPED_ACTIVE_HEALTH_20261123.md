# FARO V5 — F2B2C · B7 RED_FIX typed ACTIVE health after pointer CAS
2026-11-23. SAME UNIT `S4_W04_F2B2C_TYPED_JOURNALED_CUTOVER_DEV`. Preaudit `docs/v5/FARO_S4_W04_F2B2C_TYPED_CUTOVER_RECOVERY_B1_B5_20261123.md`, guard `45102888902` SUCCESS. READY parent `79d7e4dbd2bbd7dd880394b726cb2b9a2ca7d3e9` guard `45102983647` SUCCESS. Functional commit `b48c83c52028a72d5347a38590633b8f916e2723`, guard `45103190637` SUCCESS.

## B7 — RED_FIX_NOT_PASS
First directed GitHub CI `45103190625`, job `137458477727` completed FAILURE. JUnit XML downloaded independently: artifact `14786711294`, ZIP SHA256 `08a49ff7b1f1d8fb54f78655a2bea834a56b5a5bdca74b4d6d599c0c93cc6f28`, XML SHA256 `edf023fafe5f6064f45e86da639353402a5fb63dcb150d06f28060e38b59ee01`. **6 tests executed / 3 passed / 3 failed / 0 error / 0 skipped**.
* T04 failed health rolls back before fence: PASS.
* T05 denies revoked foreground and wrong password/tamper/old envelope before journal: PASS.
* T06 exclusive root lease, typed v2 ticket rejects legacy opener, target tamper/ABA: PASS.
* T01 successful typed cutover: FAIL, expected ACTIVE_TEN but returned ROLLED_BACK at test line219.
* T02 crash after pointer publish: FAIL, expected ACTIVE_TEN but recovered ROLLED_BACK at line248.
* T03 crash after fence: FAIL, expected HEALTH_VERIFIED but observed ROLLED_BACK_POST_SWITCH at line260.
These three all point to **typed target health/admission failure after pointer CAS and before replay fence**, NOT a failed authenticated FBS2v7 restore. Existing `FaroV5Household10DevCutoverPreflight.guarded` swallows the exact Exception and returns false, so source cause is not yet observed. Do not claim target health root cause or B7 PASS.

## B1 — Directed evidence and falsifiable diagnosis
`FaroV5Household10DevCutoverOwner.targetIsHealthy()` checks pinned target fingerprint/format, `targetRuntimeHealthy` and typed `preflight.verifyAlreadyActive`. The latter opens `FaroV5GenerationStoreGraph.verifyReopenedHousehold10Evidence2Dev` through the same quiesced gate. This new path differs from historical Evidence1 stage-only healthy graph and certified F2B1 already-selected read host. Need distinguish (a) target sealed format mismatch; (b) preflight exception suppressed; (c) actual unhealthy store; (d) gate/callback incompatibility.

## B2 — Delta only
Keep the three PASS safety tests, existing F2B2B02 STAGED archive, F2B1 active read, journal CAS, rollback and replay fence unchanged. Do not broaden permissions or modify any V4.4/legacy code.

## B3 — Safe diagnosis alternatives
(1) Weaken active health to always true: **REJECTED**; would publish unsafe target. (2) Remove ACTIVE typed verification entirely and trust STAGED F2B2B02: **REJECTED**; fails pre-switch runtime validation. (3) Preserve fail-closed rules, add test-only direct typed-health call on selected target via runtime `targetRuntimeHealthy` callback that throws AssertionError with the *underlying exception* before fallback, so CI reveals the exact failed check: **ADOPTED** as RED_FIX first step. No product logging of database paths or secrets. SQLite WAL docs https://www.sqlite.org/wal.html and Java FileChannel docs https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html remain applicable. User requested direct code, not redesign.

## B4 — Exact same-unit red-fix
After guard SUCCESS reissue READY for prior EXACT6 paths. First functional diagnostic changes **only** `FaroV5Household10Evidence2CutoverRuntimeTest.kt`: T01 pass a custom `healthy` lambda that invokes `FaroV5GenerationStoreGraph.verifyReopenedHousehold10Evidence2Dev` against selected exact pointer with shared material gate and throws a test-visible AssertionError with cause instead of silent rollback. No modifications to runtime/owner/cutover when diagnosis is pending. Direct CI same six tests with XML real. If diagnostic shows a discrete defect, record next RED_FIX and patch only the bounded root cause. Avoid repeated blind changes; after two failed hypotheses return B3/B5.

## B5 — Safety/acceptance
Fail closed while cause unknown. Never treat a ROLLED_BACK as activation. No forced replay fence, no changing journal state artificially, no loss of pinned original Finance9 authority. Wrong passphrase, legacy archive, ticket downgrade, target corruption and pointer ABA remain negative. V2 productiva/Android Apply/payments OFF. F2C and integral acceptance not authorized. Capture CI SHA exact, downloadable XML and independent parse.
