# FARO V5 W01-M1A — READY exact-three Finance9 SQL stage-only
Date local 2026-11-16. Unit `S4_W01_M1A_FINANCE9_DURABLE_ACL_STAGE_SQL`. B1–B5 source `docs/v5/FARO_S4_W01_M1A_FINANCE9_DURABLE_ACL_STAGE_SQL_B1_B5_20261116.md` Git blob `938594bc7da00dfd3a45c4f49f5c1b0f0754fbe8`. Audited HEAD `48c5735910c565494baa0a9f3a28546919ba6716`, guard `43997338912` COMPLETED SUCCESS. Writer before this document RELEASED epoch1007; B6 NOT_RUN.
## B1
Finance9 schema9 already has ACL tables/state but lacks functional policy owner; current DEV grant inprocess is not durable.
## B2
Reuse same SQLite Reality9 BEGIN IMMEDIATE, single stage-only owner. V4.4 contains no Finance ACL; preserve existing v7/8, backup and unchanged 180 contracts.
## B3
SQLite WAL/isolation/transactions confirm single SQL transaction for epoch/revoke. Android human OS principal and two-OS-process final checks remain M1B/M1C.
## B4
READY EXACT paths:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9AclStage9Test.kt`
- `.github/workflows/faro-v5-s4-w01-m1a-finance9-acl-stage-directed.yml`
Accept: A1–A7 issue, monotonic regrant, 2 connections, stage/schema fence, overflow, foreign scope, rollback fault. JUnit XML EXACT 7/7, no Android or product Apply.
## B5
No epoch reuse; no ACL export; never human permission from a test-supplied scope; no COMMIT binding without later M1C. New paths or behavior -> STOP/reaudit.
## B6
READY_TO_IMPLEMENT only after exact-path preflight + writer CAS new epoch and separate code commit.
## B7
PENDING_FINAL_AUDIT code SHA, XML, guard, writer RELEASED; M1B/C and W2 STOP.
