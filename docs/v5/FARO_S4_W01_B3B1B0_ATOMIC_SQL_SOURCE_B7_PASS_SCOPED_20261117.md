# FARO V5 — W01-B3B1B0 · B7 PASS DEV staged Finance9 one-snapshot ACL+opening
Fecha AR 2026-11-17. Unidad `S4_W01_B3B1B0_FINANCE9_SINGLE_SNAPSHOT_POLICY_AND_OPENING_DEV`. Code commit `ba6b83c39fdbfe2274fad64e103fe5d93a871bd2`. **ONLY SQL STAGE OBSERVED SOURCE; never an authenticated user read or shipping UI authorization.**

## B1
Baseline B1–B5 `docs/v5/FARO_S4_W01_B3B1B0_ATOMIC_READ_ACL_SOURCE_B1_B5_20261117.md`, guard `44086110476` SUCCESS. Independent B5 `docs/v5/FARO_S4_W01_B3B1B0_SINGLE_TRANSACTION_INDEPENDENT_B5_20261117.md`, guard `44086447791` SUCCESS. Exact-four READY `docs/v5/FARO_S4_W01_B3B1B0_ATOMIC_POLICY_SOURCE_READY_20261117.md`, preflight `44086712191` SUCCESS on SHA `4252add672fb6634acf3846597daab8d00eb8aa3`.
## B2
B6 code commit `ba6b83c39fdbfe2274fad64e103fe5d93a871bd2` includes exact four permit paths and required marker `FARO-UNIT:S4_W01_B3B1B0_FINANCE9_SINGLE_SNAPSHOT_POLICY_AND_OPENING_DEV`:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ObservedSourceReadPort.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ObservedSourceReadTest.kt`
- `.github/workflows/faro-v5-s4-w01-b3b1b0-atomic-read-directed.yml`
No Android app, Surface/CompositionRoot, schema DDL, migration, FBS2v4, grants, receipt, S2/S3/main/V4.4 product files altered. Writer epoch1073 HELD→RELEASED before CI.
## B3
`RealityStore` has extracted its existing B3B0 private SQL provenance body as `readFinance9OpeningProvenanceInsideReadTransaction()`; original `readFinance9OpeningProvenanceForDevOnly()` still owns its own `BEGIN DEFERRED`/finally `ROLLBACK`, preserving return/error behavior. New `finance9ObservedSourceReadForDevOnly()` checks STAGED9/closed/active-Apply and uses ONE `BEGIN DEFERRED` WAL snapshot to observe exact read-only `finance.snapshot.read/finance:all` epoch+global revision through existing `RealityFinance9Acl`, then calls the same private SQL provenance helper without nested transaction. It always rolls back its read snapshot, returns NULL on denied/mismatched/overflow SQL; no write grant, no Apply or source mutation.
The resulting `RealityFinance9ObservedSourceRead` is a DEV **observation** of policy and provenance at one WAL snapshot, NOT permission to show a product balance.
## B4 — Evidence by exact code SHA
- CI directed run `44087261313` completed SUCCESS on `ba6b83c39fdbfe2274fad64e103fe5d93a871bd2`; verified **8/8 new JUnit named cases** and **8/8 affected B3B0 + 8/8 affected B3B1A regressions**, total 24/24 PASS, failures/errors/skipped 0. Exact log `W01_B3B1B0_ATOMIC_SQL_OBSERVED_8_OF_8_PASS`, `W01_B3B1B0_AFFECTED_B3B0_8_OF_8_B3B1A_8_OF_8_PASS`, `NO_TRUSTED_ANDROID_USER_OR_PRODUCT_FINANCE_AUTH_ATTESTED`. JUnit artifact `faro-v5-w01-b3b1b0-atomic-read-junit` ID `14329473968`.
- Mandatory code guard `44087261177` COMPLETED SUCCESS exact SHA.
- **22/22 GitHub Actions workflows** auto-triggered for `ba6b83c39fdbfe2274fad64e103fe5d93a871bd2` completed SUCCESS, including G4/Reality8/M1C1B1B/M1C1B1C/V2 Core and previous B3B0/B3B1A. Zero failed workflows, no retries, no repeated manual certifications. Scope delta not a product APK.
- T01 absent ACL returns null; T02 exact read-policy and confirmed positive opening observed; T03 write-only and foreign scope denied; T04 revocation+ABA old epoch denied; T05 expiry boundary denied; T06 migrated legacy positive opening remains null; T07 mismatched physical/confirmation and ledger overflow deny; T08 schema7/8/closed and zero grant/receipt side effects deny.
## B5 — Essential residual security limits
The request includes **untrusted caller-supplied** principalHash, generationId, pointerSequence, expectedEpoch and `observedAtMs`. A caller inside DEV process can forge these fields, and policy issuance still depends on a non-product external staged admission. No authenticated human, UID/pinned Keystore proof, active production generation pointer/hostEpoch or trusted cross-process UI invalidation was tested. The SQLite snapshot linearizes at first SELECT, so a later OS-process revoke may coexist with earlier observed data and MUST trigger an independent UI revalidation before display. `FileActiveGenerationPointer.addChangeListener` is intra-process; **not sufficient** for cross-process restoration. The app `FaroV5FinanceReadPort` remains UNMOUNTED, product FINANZAS still inactive, and `CONFIRMED_IN_SQL` is not a human signature. B3B1B1 trusted app-side adapter + revocation/fence, B3B2 host+G15, W01 B4/C/D/E/F all OPEN; W08 predictive chart deferred. W2 STOP, V2 productiva OFF, Auto/Noche OFF.
## B6
IMPLEMENTED code `ba6b83c39fdbfe2274fad64e103fe5d93a871bd2`.
## B7
**PASS_SCOPED_DEV_STAGED9_SINGLE_SNAPSHOT_OBSERVED_SOURCE_8_NEW_16_AFFECTED_22_CI**. Consume READY permit to BLOCKED, record last_code_review PASS, release writer epoch1074, then verify this B7 doc guard on new HEAD.
