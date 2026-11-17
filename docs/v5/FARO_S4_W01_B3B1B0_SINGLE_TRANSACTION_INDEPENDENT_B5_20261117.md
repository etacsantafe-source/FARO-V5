# FARO V5 W01 B3B1B0 — independent B5 of one SQLite read transaction
Date Argentina 2026-11-17, HEAD `5d7f7137504b000257e7775861836d748d140009`, existing B3B1B0 B1-B5 `docs/v5/FARO_S4_W01_B3B1B0_ATOMIC_READ_ACL_SOURCE_B1_B5_20261117.md`, guard `44086110476` SUCCESS. Writer epoch1071 HELD DOC_ONLY. Exact candidate unit `S4_W01_B3B1B0_FINANCE9_SINGLE_SNAPSHOT_POLICY_AND_OPENING_DEV`, NO READY.

## B1
Real `RealityStore.kt` at `5d7f7137504b000257e7775861836d748d140009`: the entire B3B0 private function `readFinance9OpeningProvenanceForDevOnly()` occupies an exact bounded 5,539-character region (from signature to before M1C1B1C comment). It already starts `BEGIN DEFERRED TRANSACTION`, then calls `readFinance()`, joins `finance_account_opening` + `finance_opening_confirmation`, verifies physical-vs-confirmed amount, computes ledger exactly, returns a `RealityFinance9OpeningProvenanceSnapshot`, and always `ROLLBACK`. The B3B1A reader `observeFinance9ReadPolicyForDevOnly()` separately owns another `BEGIN DEFERRED` and rolls back. Both already B7 scoped with real JUnit and no shippable authority.
## B2
To avoid nested transactions, extract **only the B3B0 body between `// The first SELECT pins...` and the `result = RealityFinance9OpeningProvenanceSnapshot(...)` assignment** into a PRIVATE method returning the same snapshot, with no SQL BEGIN/ROLLBACK; original B3B0 method remains a thin try/finally transaction wrapper calling this helper, preserving errors/null/rollback. The new B3B1B0 factory calls the same helper inside its own one-transaction `BEGIN DEFERRED` **after** observing exact fixed read ACL scope, and collects global policy revision in the same snapshot. NO duplicate policy SQL, NO ledger copy, NO new schema.
## B3
A: One WAL read snapshot, one local owner mutex, two owner reads inside: ACL then source, no `BEGIN IMMEDIATE`, no writes; APPROVED for DEV observational source only. Reuse `RealityFinance9Acl.observedCurrentEpochOrNull` and B3B0 snapshot helper. B: two calls to existing public ports -> REJECTED as TOCTOU. C: duplicate provenance SELECT -> REJECTED. D: attach this observed source directly to Compose -> REJECTED until authenticated host principal and revocation/restore invalidations.
## B4
Scope exactly four paths to be separately READY:
1 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ObservedSourceReadPort.kt`, new typed result `ObservedPolicy+OpeningProvenance` (no grant).
2 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`, refactor existing B3B0 reader preserving behavior, add DEV STAGED9 factory and internal composite reader.
3 `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ObservedSourceReadTest.kt`, 8 directed SQLite tests with two connections and synthetic policy rows, unknown and contradiction.
4 `.github/workflows/faro-v5-s4-w01-b3b1b0-atomic-read-directed.yml`, exact XML and affected B3B0/B3B1A regression because both are touched by helper extraction.
No product UI/Android, main, V4.4, S2/S3, FBS2v4, catalog180, grants, Apply or actual money.

## B5
**PASS for DEV atomic observed snapshot only** conditional on the following mandatory negative tests/observations:
- B3B0 previous test suite remains identical PASS (legacy UNVERIFIED→null, absent opening→null, explicitly confirmed zero→0, mismatch/overflow→DENY, WAL snapshot, schema7/8/closed). B3B1A previous suite unchanged PASS. Refactor only a private method; no public return changes.
- Exact policy `finance.snapshot.read`, `finance:all` scope, principalHash/generation/pointer/expectedEpoch/atMs; unrelated write policies cannot pass, revoked/expired/epoch ABA denied. Combined source may only be observed when exact policy row holds **at first SELECT** and physical provenance consistent in the SAME WAL snapshot.
- If second OS process revokes AFTER first SELECT while reader still holds WAL snapshot, old policy may be observed. **This is consistent snapshot semantics but NOT a current authorization at render time.** External trusted reader must use generation/UID/hostEpoch and durable revocation/invalidations before UI access, not claim this staged result is a grant. `FileActiveGenerationPointer.addChangeListener` only cross-instance in-process, not cross-process. Host integration separate.
- `CONFIRMED_IN_SQL` is not authenticated human presence, DTO request is forgeable, source balance remains sensitive; composite port **must not be wired into product or Search/UI** and must clearly document test-only stage. Do not return if SQL ROLLBACK failed or active Apply; never generate `finance_apply_grant` or receipt.
- T01 missing ACL DENY; T02 valid read policy + explicitly confirmed positive opening coherent; T03 write-only/foreign policy DENY; T04 revoke and old epoch/renewal DENY; T05 expiry boundary DENY; T06 legacy/absent opening reported null under valid SQL policy, not fake zero; T07 contradiction/overflow DENY; T08 store closed/schema7/8 and no mutation, previous B3B0+B3B1A regression tests continue to pass. Tests NOT_RUN until B6.
- No physical Moto G15, no pay API, W2 STOP, V2 production OFF. **No READY** until independent doc guard and exact preflight. B3B1B1 real app adapter and Flow from canonical owner are FUTURE, not authorized by this work.

## B6
NOT_RUN. Only explicit new exact4 permit after guard success authorizes code.
## B7
PENDING, real JUnit XML, auto regression, guard exact SHA, writer RELEASED. B3B1B0 not shipping Finance access.
