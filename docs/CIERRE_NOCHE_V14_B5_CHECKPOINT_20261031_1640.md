# FARO V5 — V1.4 Noche B turno 16:40 · 31/10/2026

**Resultado:** F3 positive Android runtime regression repaired and verified. S2.A6 **OPEN 3/8**. V2 OFF, S3/S4 CLOSED, T4 physical NOT_RUN.

## CI failure investigated, corrected and verified
- [Actions #41233238496](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41233238496), SHA `f8edbcbf7c7836c5c8fb5c93694bcb191c4f860e`: **FAILURE 13/14**. The new positive test `consentedContentProviderRestoreCreatesValidatedJournalWithoutV1Switch` threw `IllegalStateException: material writes are suspended until maintenance recovery completes`. CI 19:14:05–19:28:16 UTC, wall **851 s**, job started 19:19:48 UTC, queue **343 s**, execution **508 s**. No false PASS.
- Root cause: `FaroV5CompositionRoot` constructs `FaroV5StartupReadinessGate` that **closes** `SingleProcessMaterialWriteGate` until `completeStartupRecovery()`. The isolated test injected only `startupReprojectionState=PASS` and never completed admission. This is a test fixture omission, not permission to bypass production gate.
- [Commit a4515676](https://github.com/etacsantafe-source/FARO-V5/commit/a4515676efa6f10192b0e0256979529e294e6ebb): test-only fixture now asserts fail-closed gate, invokes root's existing private `completeStartupRecovery` transition after synthetic PASS, and asserts gate opens. Shipping code and recovery gates unchanged. This fixture **does not certify real startup**.
- [Actions #41235339491](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41235339491), SHA `a4515676efa6f10192b0e0256979529e294e6ebb`: **SUCCESS**. XML oracle `F3_V14_PINNED_PROVIDER_JOURNAL_PASS 14/14`, **zero failures/errors/skips**. Emulator Android API35; includes authenticated real MediaStore `content://` to quarantined five-owner VALIDATED journal with V1 pointer preserved. Run 19:40:42–19:47:20 UTC = **398 s**, job start 19:40:44 = **2 s queue**, **396 s execution**.
- Prior 13/13 evidence remains reusable. No redundant CI rerun. The new 14/14 is on exact corrected SHA.

## Remaining S2 criteria
1 FIVE_OWNERS CERTIFIED; 2 FBS2_ISOLATED_RESTORE CERTIFIED; 3 JOURNAL_REPLAY_WAL CERTIFIED; 4 F1_ANDROID_CHAT_E2E OPEN; 5 F2_ANDROID_ACTIVATION_RECOVERY OPEN; 6 F3_FIVE_STORE_BACKUP_SAF_CONSENT OPEN; 7 F4_INTEGRAL_REGRESSIONS OPEN; 8 T4_PHYSICAL NOT_RUN. **3/8**.
F3 still needs a positive real **human SAF picker and consent** path, not only a synthetic MediaStore URI. Production V2 remains OFF. F1 real remote consent/Keystore, F2 startup/recovery, F4 integral and physical T4 remain unverified. No S3/S4.

## Time and exclusivity
- Scheduled 16:40:00 Argentina; runtime began 16:36:49.763 UTC−03, READ_ONLY before window. Acquired writer epoch42 at 16:40:00 Argentina via CAS after HEAD+task checks. Previous writer NONE epoch41 and Noche A terminal.
- Checkpoint prepared **2026-10-31T19:48:55.655Z UTC**. Observed wall from runtime start **726 s**; scheduled-window elapsed **536 s**; writer held until checkpoint **536 s**; pre-window 190 s. **Exclusive activity breakdown NO_REGISTRADO**, including code, research, review, wait, idle; do not infer a productive/idle percentage from wall. CI overlapped the session and is recorded separately, not double counted.
- Writer release in this commit is **CAS expected HEAD**, state **NONE epoch42**. Post-commit read must confirm before claiming release. No main, historical FARO, V1/V4.4, FARO Auto, secrets, billing, visibility, or cutover changed.

## Next work
Recheck writer and branch HEAD. Reuse #41235339491 exact 14/14, do not repeat. Prioritize F3 Android real SAF picker consent with positive user-controlled selection in isolated host; if unavailable, mark pending rather than PASS, and move to functional F1/F2/F4 gaps. T4 physical requires user. Absolute cutoff 18:10 Argentina.
