# FARO V5 — W01 B3B1A · B7 PASS scoped DEV Finance9 read policy observation
**Fecha AR:** 2026-11-17. Unit `S4_W01_B3B1A_FINANCE9_READ_POLICY_OBSERVATION_DEV`. Exact code SHA `ea17c533544553728a23b79f2534f4f41ffec56c`. **This is NOT a trusted application authorization, a user identity, or shipping Finance UI.**

## B1
Prior global W01-B3B1 B1-B5 `docs/v5/FARO_S4_W01_B3B1_FINANCE_ACL_READ_ADAPTER_B1_B5_20261117.md`, guard `44082066328` SUCCESS. Independent B5 `docs/v5/FARO_S4_W01_B3B1A_READ_POLICY_OBSERVER_INDEPENDENT_B5_20261117.md`, guard `44083559345` SUCCESS. Exact-four READY `docs/v5/FARO_S4_W01_B3B1A_READ_POLICY_OBSERVER_READY_20261117.md` with preflight `44083831887` SUCCESS. Global writer RELEASED epoch1068 after code and before CI.
## B2
Only FOUR paths in commit `ea17c533544553728a23b79f2534f4f41ffec56c` with `FARO-UNIT:S4_W01_B3B1A_FINANCE9_READ_POLICY_OBSERVATION_DEV`:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ReadPolicyObservationPort.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ReadPolicyObservationTest.kt`
- `.github/workflows/faro-v5-s4-w01-b3b1a-read-policy-directed.yml`
No app/Surface/manifest, Reality9 schema, Apply, receipt, FBS2v4, catalog180, main, V4.4, S2/S3 or product V2 changed.
## B3
The new DEV factory `finance9ReadPolicyObservationForDevOnly()` refuses schema7/8, closed and active Apply. For each request it checks exact principal SHA256, generationId and pointerSequence, fixed `operationId="finance.snapshot.read"` plus `objectScope="finance:all"`, expected policyEpoch and provided stage test timestamp. ONE SQLite WAL `BEGIN DEFERRED TRANSACTION` snapshot reads policy + global revision through existing `RealityFinance9Acl`, then `ROLLBACK` in finally; no grant writer/revoke call, no receipt, no balances. Returns `RealityFinance9ObservedReadPolicy` (informational-only), or null fail closed on expired/revoked/wrong epoch/scope. No port is connected to Android or UI.
## B4 — Exact verification
- Directed GitHub Actions run `44084363932` COMPLETED/SUCCESS at code SHA `ea17c533544553728a23b79f2534f4f41ffec56c`. JUnit XML confirmed EXACT **8/8 new named cases**, zero failures/errors/skipped. Log markers `W01_B3B1A_OBSERVED_ONLY_SQL_POLICY_8_OF_8` and `NO_FINANCE_UI_AUTHORIZATION_NO_ANDROID_HUMAN_ATTESTATION`. Artifact `faro-v5-w01-b3b1a-read-policy-junit` ID `14328228661`.
- Mandatory guard on code `44084363731` COMPLETED/SUCCESS at exact SHA.
- **21/21 auto-triggered workflows** on code SHA completed SUCCESS, no failures, including B3B0 opening provenance, M1C1B1B receipt gate, M1C1B1C trusted host grant staging, Reality8, G4 and V2 Core. No repeated manual certification or speculative code fix.
- Q01 absent policy null; Q02 explicit synthetic SQL read policy observed without grant/receipt; Q03 foreign identity/generation/pointer/epoch null; Q04 write-only policy cannot authorize read; Q05 expires at boundary; Q06 revoke+renew preserves monotonic epoch/ABA deny; Q07 schema7/8/closed deny; Q08 malformed/duplicate observations remain pure read, grants/receipts zero.
## B5 — Residual security limits
**CRITICAL**: A matching SQL policy and a request bearing `principalHash`, `generationId`, `pointerSequence`, `expectedPolicyEpoch`, `observedAtMs` are **NOT** independently authenticated, nor is stage timestamp trusted. A malicious same-process caller can forge request fields. The port is only a **policy observation** for future verified host composition, never a shipping read authority or permit to display actual balances. SQL scope is not automatically a Finance Apply grant. Future W01-B3B1B must combine ACL and B3B0 Finance data under one coherent read, verify actual host principal UID/epoch, generation/pointer and revocation-driven invalidations and fail closed on races; independent B1-B5 and exact permission required. G15 biometric physical NOT_RUN. W01 B3B2/B4/C/D/E/F OPEN; W08 projection not built, W2 STOP, V2 product OFF.
## B6
IMPLEMENTED code `ea17c533544553728a23b79f2534f4f41ffec56c`.
## B7
**PASS_SCOPED_DEV_STAGE9_READ_POLICY_OBSERVATION_ONLY_8_OF_8_AND_21_CI**. Consume permit/BLOCKED with last_code_review PASS, release global writer epoch1069, verify final B7 doc guard after publication.
