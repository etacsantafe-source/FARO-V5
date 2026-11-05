# FARO V5 — S3.A1 N015 / Reality schema7 ↔ five-owner DEV preflight · B1–B5 (05/11/2026)

Unit `S3_A1_N015_SCHEMA7_DEV_GRAPH_PREFLIGHT`; reviewed parent `055ce36fef9716fa9b8a0e665ca501c1b183ead0`. A1 remains OPEN, new Reality Activity read 6/6 scoped B7 PASS. **This unit only repairs a proven regression of N015 storage; not A1 federation or a V2 productive activation.**

## B1 — Exact live evidence / causal chain

New RealityStore `SCHEMA_VERSION=7`, `commit_recorded_at_epoch_millis` nullable for receipts, code `55ef7edd`, G4 [42107761539](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42107761539) PASS 9/9 and receipt [42107761716](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42107761716) PASS 3/3, FBS1 4/4, FBS2 7/7. Existing `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` `requireStrictV2StoreSchemas` still expects **Reality 6**, legacy **5**. This is inconsistent with fresh `RealityStore` 7. `FaroV5V2CoreDevAuthority` initializes fresh five stores, then calls `openValidatedV2WithSharedGate` whose read-only preflight rejects schema7 before open; all DEV first-open tests break.

Exact GitHub Actions [42110935035](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935035), code SHA `9d2491f6a03114753e68cdb4c37fb2f0a4c6b046`: **FAILURE** `M2 active five-owner JVM directed tests`, **11/11 failed** with `IllegalStateException` in `FaroV5V2CoreDevAuthorityTest` and `FaroV5G5ActiveFourOwnerSearchTest`. Compilation succeeded. No known user data was changed by CI fixtures. Other jobs same code SHA: Home [42110935335](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935335) success 6/6 new, M5 APK [42110935250](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935250) success, guard [42110935072](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935072) success. A build PASS is not actual DEV session certification.

Preflight `StagedSqlitePreflight.Schema` currently admits exact `version=6` or `legacyVersion=5` only; `ActiveSqlitePreflight` reuses `requiredTables`. Reality schema5 lacks objectives/matters; schema6 and7 have them. Unversioned deactivation or accepting arbitrary versions would violate fail closed.

## B2 — V4.4/V5 comparison and already validated assets

V4.4 has no five-store V2 graph schema7; copying legacy mutable startup is unsafe. V5 S2 uses exactly-versioned staged/active preflight, symlink/WAL fences, journal pointer CAS and SQLite migration. Keep existing preflight and Reality migration from 5/6 to7; never suppress user_version mismatch or initialize blank stores. The N015 Reality 6→7 migration has already passed G4 historical fixture; do not repeat or rewrite it.

## B3 — External alternatives and rejection

- SQLite official `PRAGMA user_version`: https://www.sqlite.org/pragma.html#pragma_user_version. Application-defined integer is available for exact version checks; **USAR** existing SQLite preflight. Changing `schema_version` is **DESCARTAR**: unsafe because it is a different SQLite internal schema cookie.
- SQLite official `ALTER TABLE`: https://www.sqlite.org/lang_altertable.html — nullable additive columns and explicit migration; **ADAPTAR** previously tested RealityStore migration, not a new migration engine.
- Android Developers migration testing: https://developer.android.com/training/data-storage/room/migrating-db-versions — preserving existing user data is essential; **REFERENCIA** test methodology; Reality uses SQLite driver, not Room migration API.
- Alternatives: **(A) USAR** exact schema7 plus explicitly bounded compatible schemas6/5 with correct required-table profiles and upgrade handled only by existing RealityStore; **(B) DESCARTAR** schema7-only, which breaks existing schema6 DEV data; **(C) DESCARTAR** unbounded version admission or manually rewriting SQLite header; **(D) DESCARTAR** disabling preflight to turn 11 failures green. No new libraries, licenses or API expense.

## B4 — Minimal agreed delta and explicit limits

Update `FaroV5GenerationStoreGraph.kt` Reality preflight expected version 7. Keep 6 with full tables and 5 with pre-existing 8 tables as **two explicit legacy versions**, no wildcard. Extend `StagedSqlitePreflight.Schema` with ONE optional additionalOlderLegacyVersion/table list, validate it is positive, smaller than primary legacy and unique; apply same policy in existing staged and active read-only preflights. `RealityStore` existing transactional migrations remain sole database-writing owner; no direct SQL in graph bootstrap or preflight.

Tests in `FaroV5GenerationStoreGraphV2T2Test.kt` check schema5/6/7 acceptance matrix and rejection 4/8; test read-only preflight of fresh7 and actual sealed-v6 migration to7 plus preservation of undated receipt legacy (existing G4). Then rerun **same exact** V2 DEV 11 tests and schema preflight generation tests using CI. Keep one physical Moto G15 campaign in S3.A5; no forced migration to installed app.

## B5 — Adversarial before code

1. New schema7 generated by current Reality opens safely with five owners, not 11 false negatives.
2. Schema6 (existing DEV) is explicitly admitted only if full schema6 tables and integrity match; existing Reality migrations upgrade atomically to7, historical receipt time remains NULL. Failure stops, no data deletion/reset.
3. Older schema5 admits only exact historical table set, not missing/partial/corrupt schemas; migration integrity verified by owner.
4. Schema4 and future schema8 are denied before write; also malformed DB, sidecar symlinks, broken FK and incomplete Workspace remain denied.
5. Staged immutable vs active committed-WAL preflight **unchanged**, no unsafe cross-process bypass. Retain single writer/owner, stored pointer/journal fence.
6. No functional changes to S2, main, V4.4, shipping V2 activation, egress, catalog (172), User Moto G15 or A5 tests. Unit test fixtures only.
7. B6 requires code in only the three approved paths, DEV 11 tests success, actual XML, generation graph tests and guard exact SHA; B7 separately audits code/CI. If any red, return to B5 and annotate RED_FIX, do not just change expected test counts.

**B1–B5 VERIFIED bounded for this concrete hotfix**, not permission itself. Emit READY under gate only after revalidating HEAD, writer and immutable evidence. Final A1 status still OPEN pending N015 federation, N031 unified planning and Guardados owner.
