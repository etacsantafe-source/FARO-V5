# FARO V5 — W01-B3B1 · Admisión de lectura Finanzas y adapter UI (B1–B5)
**Fecha AR:** 2026-11-17. Base exact `5b1e815cccaef5c370085bd024ddae82fb8cb829`; checkpoint B3B0 B7 scoped, guard `44081612501` SUCCESS, writer RELEASED epoch1064. Mode DOC_ONLY no READY and no functional changes. W2 STOP.

## B1 — Fuente comprobada
- B3A `FaroV5FinanceReadSurface.kt`: `FaroV5FinanceReadPort` requires suspend read + `Flow<Unit>` invalidations; UI distinguishes READY/EMPTY_CONFIRMED/PARTIAL/DENIED/UNAVAILABLE/STALE and amounts `Long?`. B7 code `f9c524cfc3b0ddda93d3def16371efbbcda9f933`, 8/8 JUnit, 19 relevant regressions; do not repeat.
- B3B0 `RealityFinance9OpeningProvenanceReadPort.kt`: read-only STAGED9 DTO `sourceRevision` and accounts with `CONFIRMED_IN_SQL`/LEGACY_UNVERIFIED/MISSING; 8/8 SQLite and 20/20 workflows on code `9cfab42fa147aedd109eafc872263055cd4d6aec`, final B7 `docs/v5/FARO_S4_W01_B3B0_OPENING_PROVENANCE_B7_PASS_SCOPED_20261117.md`. DB `CONFIRMED_IN_SQL` is only consistency, NOT human authorization or durable read grant.
- Existing `FaroV5FinanceSearchReadSource.kt` (DEV not mounted) relies on user-supplied callbacks `admitted()`, `aclEpoch()` and generation before+after. This is a useful fail-closed *read protocol*, **not** an authority source; a fake callback is trivially forgeable in tests.
- `RealityFinance9Acl` grants/revokes scopes using `principal_hash,generation_id,pointer_sequence,operation_id,object_scope,policy_epoch` and SQL monotonic state; currently its issuance path `issueAfterTrustedStageAdmission` requires external authorization and existing grants are for final Apply scope, NOT proof of `finance.snapshot.read` right.
- `FaroV5CompositionRoot.java` and `FaroV5ShellNavigation.kt` do NOT mount B3A Finance screen. V2 shipping OFF, G15 AndroidKeyStore physical NOT_RUN.

## B2 — Reuso vs V4.4
Reuse existing Reality9 ACL schema and transaction state, `ActiveGenerationPointerPort` exact sequence fencing, current host epoch/UID, `Finance9OpeningProvenanceSnapshot`, `FaroV5FinanceUiSnapshot` UI model and `Flow<Unit>` invalidation pattern from Home/Avisos. Do not copy V4.4 DB/UI values, don't change Finance ledger/Apply/receipts, zero inferred through COALESCE prohibited. UX `FARO_S4_W01_ARCH05_06_UX_ACCEPTANCE_DECISIONS_20261116.md`: Chat landing, ARS/USD separated, W08 proyecciones deferred.

## B3 — Options and trust separation
A **ADOPT design**: a dedicated local Finance **read authorization** checked against durable Reality9 principal/generation/epoch, separately from write grants. No authorization by a boolean or `CONFIRMED_IN_SQL`. A trusted app adapter reads pointer/host epoch + durable ACL BEFORE and AFTER B3B0, then maps confirmed source to UI `VERIFIED`, others to `OPENING_UNVERIFIED`/PARTIAL; revoke/change or ABA between calls -> deny and invalidate. Only staged DEV until Android hardware and shipping B7.
B **REJECT**: directly map B3B0 raw DTO into UI READY; caller-provided `admitted()=true` or `aclEpoch()="x"` as sole proof; resurrect old cached balances after revoke/restore; coalesce null amounts into 0; use M1C1B1C write grants as implicit read rights.
C **ADOPT interim test-only seam**: pure converter can be tested with synthetic exact ACL admission; tests MUST say `FAKE_READ_ADMISSION_POLICY_ONLY` and never assert a human/production ACL.
Sources SQLite https://www.sqlite.org/isolation.html and Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first; prior W01 B5/doc research already certified.

## B4 — Safe execution plan, NO code permission yet
Before implementing the app adapter, split two exclusive subunits with separate READYs:
- **B3B1A — durable scoped read authority in Reality9**, candidate paths `RealityFinance9ReadAdmissionPort.kt` NEW, `RealityStore.kt` extend with read-only stage9 factory bound to *specific* `finance.snapshot.read` scope, `RealityFinance9ReadAdmissionPortTest.kt` NEW, own directed workflow NEW. Must pin principalHash/generationId/pointerSequence/objectScope and monotonic epoch; verify row revoked/expires, stage9/schema, no implicit write/Apply. This is an **authorization lookup**, not a grant issuer. Needs independent B5 on use of existing `finance_acl_policy` scope and locking, and whether read admission can be checked without treating the plain DTO as proof of identity. If scope model cannot safely supply read grant, STOP and specify missing durable writer, do not shim it.
- **B3B1B — staged app bridge** once B3B1A has B7 scoped, new `S4W01B3B1Finance9ReadAdapter.kt` (implements `FaroV5FinanceReadPort`) and JUnit tests+CI; no Activity/CompositionRoot wiring. Must pin UID/current generation + sequence + host epoch, ensure SQL ACL admission before/after opening read; `Flow<Unit>` from real owner generation/revoke source, not an arbitrary UI-injected flow; cancel/close flushes cached data and port. Avoid multi-process TOCTOU: last read authorization must be linearizable with corresponding data snapshot or label data STALE/UNAVAILABLE, not READY.
- **B3B2** later isolated Android host wiring + instrumentation G15; only after B3B1A/B B7 and physical permitted, product shipping OFF until full W1/F.
None of these candidate paths has READY, no tests run and no count can be declared.

## B5 — Adversarial gate and blockers
1. No confirmed opening if B3B0 status UNKNOWN; no fake zero even if FinanceSnapshot ledger reads 0.
2. No `READY` when durable `finance.snapshot.read` scope absent/revoked, expired, wrong principal/UID, wrong generationId/pointerSequence, wrong host epoch or cross-process revoke raced the read.
3. No stale cached value shown across `Flow<Unit>` invalidation, restore/cutover ABA, rotation or Compose back stack. `DENIED` and `STALE` must carry zero account rows, and `PARTIAL` no amounts for unverified rows.
4. No direct app DTO `readAdmission=true` as standalone security; untrusted synthetic test admission is NOT Android human proof. Verify source of principal in Keystore actual callback (M1C1B1C staged only) separately.
5. Check existing ACL SQL: operation `finance.snapshot.read` is a reader capability, while `issueAfterTrustedStageAdmission` was built for short-lived Apply scopes. A fresh read grant/renewal/revocation must not consume or impersonate an Apply grant; avoid overlapping permission and audit exact choice independently.
6. No `BEGIN IMMEDIATE` around normal UI rendering or file I/O; stage nine read owner must compose with WAL cross-process constraints. Any access change during read => fail-closed or STALE, never READY.
7. S2/S3/main/V4.4, FBS2v4, catalog180, W2 STOP, product V2 OFF, Auto/Noche OFF, no paid APIs or external money movement.

**B5 decision:** PASS to continue **READ_ONLY investigation of B3B1A durable Finance read authority**. **STOP / NOT READY** for B3B1A/B6 and any live Finance UI until exact policy-model security analysis and independent permission. This is a technical architecture gap; do not claim app Finance already functional.

## B6
NOT_RUN. DOC_ONLY.
## B7
NOT_RUN. Current doc guard, HEAD and writer release must be checked before declaring preaudit closed.
