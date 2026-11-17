# FARO V5 — W01-B3B1A Independent B5: Finance9 read policy observer ONLY
Fecha AR: 2026-11-17. Base real `49be93987fb57d78317ee53f9b9338f801cf18bc`, guard `44082066328` SUCCESS, global writer epoch1066 HELD documental. Unidad candidata `S4_W01_B3B1A_FINANCE9_READ_POLICY_OBSERVATION_DEV`. Prior W01-B3B1 B1–B5 `docs/v5/FARO_S4_W01_B3B1_FINANCE_ACL_READ_ADAPTER_B1_B5_20261117.md` blob `e70129ac9a5e053392c3e74139c76ec10fe9eea1`. This review is independent of the prior B5 document; it does not grant user-facing read authority.

## B1
Read actual `RealityFinance9Acl.kt` at HEAD:
- `Finance9AclScope` pins principalHash SHA256, generationId, pointerSequence, operationId and objectScope. SQL `finance_acl_policy` PK is all five fields; row carries policy_epoch, revoked and expires_at_ms. `finance_acl_state` stores global monotonic next_policy_epoch and policy_revision.
- `observedCurrentEpochOrNull(key,atMs)` only checks current SQL policy row and expiry. The **code explicitly says it is informational only**, and cannot authorize COMMIT or a human identity.
- `issueAfterTrustedStageAdmission` inserts/renews one policy row with independent external trusted admission; it does NOT create a usable Finance Apply grant. `issueGrantAfterTrustedStageAdmission` uses a different `finance_apply_grant` table and cannot be substituted for a read permission.
- Existing `finance.snapshot.read` is a documented operation id, while `finance.account.create` / `finance.entry.record` are different and MUST NOT authorize reads. This unit never calls the issuer or revoker from its read path.
- B3B0 `RealityFinance9OpeningProvenanceReadPort.kt` B7 PASS dev read SOURCE, no ACL. B3A surface B7 PASS DEV dormant. Shipping/root trust and human principal still NOT PROVEN.

## B2
Reuse the already certified `RealityFinance9Acl.observedCurrentEpochOrNull`, `observedCounterForStageTest`, existing SQLite9 policy and single RealityStore owner/connection, not a cloned policy parser. Do not alter DDL, grant issuer, apply gate, FBS2v4, V4.4, owner Finance, surface Compose, manifest or product application. B3B0 source read remains unmodified, not combined yet.

## B3
Option A **APPROVED FOR DEV OBSERVATION ONLY**: a STAGED9 `RealityFinance9ReadPolicyObservationPort` accepting immutable request for principalHash, exact generation/pointer, objectScope, expectedPolicyEpoch and observed-at time. It hardcodes operationId `finance.snapshot.read`, runs the existing ACL observation inside a single `BEGIN DEFERRED TRANSACTION` / `ROLLBACK` SQLite WAL read snapshot and returns epoch + policy revision ONLY on exact live match. Invalidation by revoke, issue again or expiry gives null on next observation. No Apply grant, no balance or UI authorization in return type. A caller CAN FORGE an input DTO or a timestamp, so it is NOT secure against arbitrary same-UID code; keep DEV only.
Option B **REJECTED**: any boolean callback `admitted()` or `CONFIRMED_IN_SQL` as read authorization. Option C **REJECTED**: reusing an Apply grant as read permission, suppressing epoch/expiry, or using `BEGIN IMMEDIATE` during render. Option D **DEFERRED**: atomic observation+Finance9 provenance read and trusted app-generation/Keystore/UID binding in B3B1B or an earlier new unit, with independent permissions.
Source references: SQLite https://www.sqlite.org/isolation.html and https://www.sqlite.org/lang_transaction.html. No paid services or Internet.

## B4
Exact candidate B6 paths for separate READY:
1 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ReadPolicyObservationPort.kt` new immutable request and observed result; call result `ObservedPolicy` rather than `Authorized`.
2 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: explicit STAGED9-only factory. On each read, checkOpen, stage flags, schema, absence active Apply; `BEGIN DEFERRED TRANSACTION`; construct **fixed** `Finance9AclScope(operationId=finance.snapshot.read)`, evaluate existing `observedCurrentEpochOrNull` and global policy revision **in same read txn**; require exact expected epoch; `ROLLBACK` even on error. Return null/deny on inconsistent or expired scope. No issuer call and no writes.
3 `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ReadPolicyObservationTest.kt`: real SQLite9 tests.
4 `.github/workflows/faro-v5-s4-w01-b3b1a-read-policy-directed.yml`: directed JUnit XML and regression for M1A.
Tests Q01 missing policy DENY; Q02 explicit synthetic external issuance read-scope OBSERVED and no `finance_apply_grant`; Q03 cross-principal/generation/sequence/scope DENY; Q04 write-only operation cannot satisfy read; Q05 expiry exact time DENY; Q06 revoke + renewed epoch ABA old DENY; Q07 closed store, schema7/8 and active tx DENY; Q08 source policy observation produces no finance data, no receipt, read-only and epoch revision monotonic. New tests NOT_RUN before B6.

## B5 — Independent negative/security adjudication
The existing policy table can durably hold the correct **read-specific scope**, but its issuer is currently only a STAGED synthetic external-admission primitive. **No trusted app identity** and **no durable product read principal** is established. Therefore B3B1A may only certify a *policy observation*, NOT an authorization, NOT human consent, NOT a root of trust and NOT a shippable `FaroV5FinanceReadPort`. The DTO's `principalHash`, `generationId`, `pointerSequence`, `expectedPolicyEpoch` and `atMs` MUST NOT confer authority merely by matching a row; same-process code can forge them. Binding to a verified Keystore-local principal, true generation pointer+epoch, trusted host state and revocation-driven invalidations requires a **separate explicit unit**, with testable two-process revocation semantics.
No double write, no direct surface→Reality connection. No claim that a read policy observation and B3B0 data from *different* transactions are atomic. Future B3B1B must combine permission and data in one consistent snapshot/owner read and also ensure no stale UI across revocation or restore; if that cannot be proven, STOP instead of READY.
B3B1A **PASS design limited DEV observer; separate READY gate still needed before code**. W01-B3B1B, B3B2, B4/C/D/E/F OPEN; W2 STOP, V2 productiva OFF, S2/S3/main/V4.4 unchanged, one writer, no paid APIs, no G15 physical.

## B6
NOT_RUN. No functional commit without new exact4 READY + preflight guard.
## B7
PENDING CI XML and independent audit; release writer after doc, no PASS until guard SHA.
