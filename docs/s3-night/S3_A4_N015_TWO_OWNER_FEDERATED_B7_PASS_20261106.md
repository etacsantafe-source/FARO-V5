# S3.A4 N015: Reality + Workspace federated history — B7 audit (06/11/2026)

## B7 — Independent final gate / PASS_SCOPED

**Code SHA:** `8d3f3cdbeb01e7364f235f278814a2cab97cf426`. **Unit:** `S3_A4_N015_TWO_OWNER_FEDERATED_HISTORY`. **Verdict:** **PASS_SCOPED for the two-owner source adapter only. Not N015 global closure or A4 CLOSED.**

### Seven-step chain and exact permitted paths

- B1–B5 preaudit documentation-only commit `720208e4dc73c71db0bad4b9cae28eba20f4c7e2`, `docs/s3-night/S3_A4_N015_FEDERATED_HISTORY_B1_B5_20261105.md`, blob `c94f22defcf9d4a3265d04ebc21c39a6b48a08bb`. B3 compares real ActivityWatch, SQLite cursor docs, OWASP privacy and Android invalidation; rejects duplicate Truth and permission-by-UI.
- Separate scoped permit READY documentation-only commit `a085bdea77060da9ef73ebb7bc2d32ede899dd0e`, guard run [42149952686](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42149952686) SUCCESS.
- B6 code commit `8d3f3cdbeb01e7364f235f278814a2cab97cf426` has `FARO-UNIT:S3_A4_N015_TWO_OWNER_FEDERATED_HISTORY`, edits only 3 permitted paths:
  1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FederatedHistoryReadSource.kt`
  2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5FederatedHistoryReadSourceTest.kt`
  3. `.github/workflows/faro-v5-s3-federated-history-directed.yml`.
- [42166664979](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42166664979) mandatory 7-block guard **SUCCESS** on exact code SHA.
- [42166665044](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42166665044) app compiled and **SUCCESS** on exact SHA: `BUILD SUCCESSFUL in 4m 7s`, JUnit `S3_N015_FEDERATED_HISTORY_XML_PASS 6/6` with 0 failed/error/skipped.
- [42166664978](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42166664978) Home notice app regressions **SUCCESS** on exact SHA. V2 core and M5 isolated APK runs started and may still be pending: only claim their verdict after verifying results.
- G15 physical **NOT_RUN**; staging only.

### Reviewed security and actual behavior

- Merges two **real canonical sources**: `RealityStore.timedReceiptHistory` (COMMITTED receipt, own time provenance) and Workspace `ConversationWorkspaceStore.readHistoryPage` (accepted/completed message, producer-declared timestamp), deterministic ordering timestamp DESC, domain, ID. No text leak, no extra store, no mutations, no new OperationIds (catalog remains 172).
- Injected host-supplied independent Reality/Workspace admissions, ACL epochs, generation identity and pointer epoch. Checks before first read, between owners, after merge; denial returns **null**, never fake zero. The host MUST mint actual grants and durable ABA witness; this class never mints credentials or activates V2.
- Per-owner bounded `limit 1..100` queries and cursor tied to generation/ACL/pointer/Workspace revision; advancement uses **only emitted** events so interleaved pages do not skip rows. Preserve `legacyUndatedReality` and `globallyComplete=false`. Tests assert real tie ordering, multi-page no losses, pointer A→B→A revocation, grant revocation, stale Workspace revisions, exceptions and cancellation.
- B7 residual limitations: no real host factory binding, no Activity Surface screen, no Finance activity in this view, unindexed Workspace date scale may need benchmark. It is a **two-owner view, not completed history.query federation of all canonical domains**. No cross-store transaction; timestamps are display order, not causal guarantee.
- N031 not complete: real Matters source exists in Reality, but no durable Reminder or MonthlyPlan owner exists in V5. No zero counter may be emitted from nonexistent sources. Need schema, recovery, 5-store backup, real authorization, tests and UI in future scoped units. A3 Guardados+8 stay reviewed/NOT approved; A5 X01-X12 and one G15 campaign pending.

**Outcome:** release writer and set protocol permit **BLOCKED** before another functional unit; checkpoint S3.A4 **IN_PROGRESS**, S2 8/8 closed, S3 scoped A1/A2 2/5 and final integrated 0/5. V2 productive OFF; Night/Auto/S4 OFF; V4.4/main untouched.
