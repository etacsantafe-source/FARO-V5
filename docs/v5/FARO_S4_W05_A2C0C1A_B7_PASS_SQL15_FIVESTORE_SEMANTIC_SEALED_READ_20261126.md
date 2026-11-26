# FARO V5 — W05-A2C0C1A · B7 PASS — SQL15 full five-owner semantic staged read

2026-11-26 Argentina. Repository `etacsantafe-source/FARO-V5`, branch `work/s3-night-20261102`.
**Unit:** `S4_W05_A2C0C1A_SQL15_FIVESTORE_SEMANTIC_SEALED_DEV`.
**Result: B7 PASS SCOPED DEV, 35/35 JUnit XML original.** No ACTIVE SQL15, no cutover, no stock writer or G15 physical verification.

## B7 — Evidence, bounded acceptance and next independent gate

- Baseline `9a0ee69288001da2bac0067f547a41f2a5630cca` A2C0C0 B7 PASS SQL15 sealed precutover 33/33. Earlier A2C0A, A2C0B1, A2C0B2, A2C0B3 and all A2B 9/9 already certified. No duplication.
- B1–B5 independent preaudit `docs/v5/FARO_S4_W05_A2C0C1A_SQL15_FIVESTORE_SEMANTIC_READBACK_B1_B5_20261126.md`, commit `5b7c5979434c961083e0d77c719e37e1516d39b5`, blob `5197fe1474eeb5c24638e0131fba55bb0e2a4fd9`, guard run `45886890081` **SUCCESS**.
- READY exact8 documentary permit commit `62750f050487a3968dece88383e1ca3c7417e448`, guard run `45887005903` **SUCCESS**. B6 SHA `b327656a14eea360b148122250f31929df7b7ffc` with required `FARO-UNIT:S4_W05_A2C0C1A_SQL15_FIVESTORE_SEMANTIC_SEALED_DEV`, exactly eight authorized paths: Finance9 sealed15, Needs11 sealed15, RealityStore STAGED15 read-only, Graph explicit SQL15 archive profile, V10 five-owner semantic snapshot, PreCutover v12 equality, tests + new CI workflow.
- Guard of B6 `45887407541`, job `140141446622` **SUCCESS**.
- Directed GitHub Actions run `45887407597`, job `140141446975` **COMPLETED SUCCESS**, https://github.com/etacsantafe-source/FARO-V5/actions/runs/45887407597. Parent protocol preflight **PASS** at READY SHA `62750f050487a3968dece88383e1ca3c7417e448`; Gradle Generation `BUILD SUCCESSFUL in 4m 3s`; Gradle Reality `BUILD SUCCESSFUL in 40s`. The `Verify original JUnit XML test vectors` step **SUCCESS** with exact suite counts, zero failures/errors/skips and testcase names unique.
- Four XML suites independently checked **within CI, not separately downloaded**:
  * `FaroV5Household15V12StagedReplayTest`: **15/15 PASS** incl. two A2C0C1A tests that reject unexpected `finance_budget_limit` and CommercialEvidence2 observation even when SQL15 stock history remains identical and SQLite is otherwise healthy.
  * `FaroV5Household14V11StagedReplayTest`: **5/5 PASS** existing SQL14 replay regression.
  * `FaroV5Household15Fbs2CodecTest`: **8/8 PASS** authentication/validation.
  * `RealityHousehold15ConsumptionRowsPortTest`: **7/7 PASS** sealed rows regression.
  * TOTAL **35/35 PASS**, errors=0, failures=0, skipped=0.
- Original JUnit XML artifact `w05-a2c0c1a-sql15-fivestore-semantic-readback-xml`, ID **15150486993**, digest `sha256:698eef2a96a9e65c96c26771993ba1c045e5c3bb60d7c5de57cf627fef34c904`. Original ZIP not downloaded manually; XML verified by CI.
- Technical contract: `FaroV5Household15PreCutoverReadback.verifyOnly` authenticates FBS2v12 original ciphertext, sealed stage pointer/lease, five SQLite schema/integrity and exact SQL15 receipt/purchase/compensation/reservation/consume ledger, then reuses `FaroV5FiveStoreBackupCapture` to compare **all five semantic owner snapshots** with the historical FBS2v12 projection: Workspace, Reality legacy/G4/Planning/Finance/Household, Auxiliary4, Web2, CommercialEvidence2. Revalidates stage bytes/pin/pointer. No new restore path, no ticket, no exposed ACTIVE owner or grant.
- Negative checks targeted finance budget row and CommercialEvidence2 record divergence; C0's structural check alone would not detect these. In C1A fail-close after semantic comparison; no writes repair or delete divergent data.
- Historical workflows on shared `RealityStore.kt` and Graph can show FAIL/SKIP in obsolete permission preflight; these do **not** establish globally green CI and require independent maintenance. The **directed** CI and B7 guard are SUCCESS for this SHA.
- A2C0C1A does **NOT** certify a live SQL15 ACTIVE host: **A2C0C1B** must establish WAL-aware read-only opener, exact pointer + completed selection verification in DEV isolated fixtures and health proof without writes, separately B1–B7. **A2C0C2** journaled switch/rollback after C1B B7; **A2C1** stock.consume Review/Apply; A2D UI; A2E global acceptance all OPEN. No permit inheritance.
- Closing this unit requires BLOCKED consumed permit and updated continuity pointer, writer RELEASED epoch expected 2128 and FINAL document guard SUCCESS, each verifiable on remote SHA; this text does not predeclare those checks.
- V2 productiva, stock real, pagos, Auto/Noche OFF; main/V4.4 untouched, Moto G15 NOT_RUN, no paid APIs.
