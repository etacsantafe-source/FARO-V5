# FARO V5 W01 B3B1A — exact-four READY for Finance9 observed read policy (DEV)
Date AR 2026-11-17. Unit `S4_W01_B3B1A_FINANCE9_READ_POLICY_OBSERVATION_DEV`. Reviewed HEAD `fbc9bff487797934389550bcccd772c54fa763f1`, guard B5 `44083559345` SUCCESS. Independent B5 `docs/v5/FARO_S4_W01_B3B1A_READ_POLICY_OBSERVER_INDEPENDENT_B5_20261117.md` blob `da42f55794f37a3dead5fb391436b4afa9a9bdd9`; prior W01-B3B1 B1–B5 `docs/v5/FARO_S4_W01_B3B1_FINANCE_ACL_READ_ADAPTER_B1_B5_20261117.md` guard `44082066328` SUCCESS.

## B1
Finance9 ACL existing policy SQL exact-five-key `principal_hash/generation_id/pointer_sequence/operation_id/object_scope`, epoch revocation and expiry; `RealityFinance9Acl.observedCurrentEpochOrNull` already read-only and explicitly NOT product authority. B3B0 source read and B3A UI B7 PASS DEV; do not repeat certified tests.
## B2
Reuse `RealityFinance9Acl.observedCurrentEpochOrNull`, `observedCounterForStageTest`, `Finance9AclScope`, single SQLite WAL owner. No cloned SQL parser, new DDL, changes to Finance grants/Apply or UI.
## B3
Chosen observational DEV-only read with fixed operation `finance.snapshot.read`, exact caller-staged scope, policy epoch and global revision under one `BEGIN DEFERRED` / `ROLLBACK` read snapshot. Inputs can be forged; NO authenticated human or trusted timestamp and NO shippable permission. Rejected callback-only ACL, Apply grant implication and standalone UI READY. SQLite https://www.sqlite.org/lang_transaction.html.
## B4
Exact four functional paths:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ReadPolicyObservationPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ReadPolicyObservationTest.kt`
4. `.github/workflows/faro-v5-s4-w01-b3b1a-read-policy-directed.yml`
New typed observer DTO and request, new RealityStore STAGED9 factory/read txn only, new 8-case real SQLite suite, directed JUnit workflow. No app, Surface, manifest, W2, V4.4, main or productive V2.
## B5
8 new tests: absent row null; read row observed exact epoch + global revision no grant; foreign scope denied; write-only row denied; expires boundary denied; revoke+reissue ABA previous epoch denied; schema7/8/closed denied; invalid request + pure read-only and no receipts. Synthetic test setup for issuing ACL read row is NOT verified Android trusted identity. No backend/UI uses result as authorization. Future B3B1B must perform *atomic* ACL+Finance9 data read with verified app principal, generation/pointer/UID and revocation changes, not two separate reads; still STOP for product/physical.
## B6
READY_TO_IMPLEMENT only once exact permit preflight SUCCESS; no code in doc commit.
## B7
PENDING_FINAL_AUDIT CI/XML, guard SHA, writer RELEASED, permit consumed/BLOCKED.
