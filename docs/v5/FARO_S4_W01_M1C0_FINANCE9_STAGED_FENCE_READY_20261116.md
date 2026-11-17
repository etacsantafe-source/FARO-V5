# W01 M1C0 Finance9 two OS-process STAGED9 COMMIT fence — READY exact five paths
Date local 2026-11-16, reviewed HEAD `be99fe41e7895eb0e5dac1e3b16197c1821eaf14`. Source preaudit `docs/v5/FARO_S4_W01_M1C_TWO_PROCESS_FINANCE_COMMIT_B1_B5_20261116.md` blob `c92289aea2381a7495d35523a7c55cf7652e8f1e`; guard `44006769756` SUCCESS after final M1B B7; writer RELEASED epoch1017 before this READY. **M1C0 STAGED9 ONLY, not productive Apply or human Android consent**.
## B1
`RealityStore.run` already has BEGIN IMMEDIATE and COMMIT; M1A ACL exposes only out-of-transaction read, and M1B code dormant has no production authority. Need SQL same transaction scope.
## B2
Reuse existing RealityStore and M1A owner. No V4.4 finance ACL; do not touch UI, catalog, legacy schema7/8, S2/S3, main.
## B3
SQLite BEGIN IMMEDIATE serializes two OS processes using same database. Use Kotlin JUnit parent with Python sqlite3 child OS PID (Ubuntu official Python3) for independent SQLite process and bounded IPC. No sync JVM substitute, no extra DB/ATTACH.
## B4
Exact paths:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9AclCrossProcessTest.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9AclProcessWorker.kt`
- `.github/workflows/faro-v5-s4-w01-m1c-process-fence-directed.yml`
C01–C08 exact negative tests: revoke-before-begin, lock ordering, after-Review-before-Apply deny, crash rollback effect/receipt, one-use grant/receipt exact simulated SQL stage only, revoke/regrant epoch and ABA, schema7/8 DENY, foreign scope and stale sealed stage deny. Must verify distinct OS PIDs, bounded timeouts, JUnit XML exactly 8. A single SQLite connection/loop proves NOTHING about 2 OS processes.
## B5
Read within `RealityStore.run` active SQL transaction only, not an external ACL callback. Stage-only internal seam cannot be called from shipping; explicit financeStage9+planningStage8 gates, current RealityTransactionScope provenance, no nested transactions. Python child sends no real credential, no cross-origin grants, no real money or Android. Any missing test C01–C08 makes B7 blocked, not fake PASS.
## B6
READY_TO_IMPLEMENT after exact 5-path gate/preflight and writer CAS new epoch; code commit separate from this permit.
## B7
PENDING JUnit XML 8/8, distinct PID evidence, guard exact SHA, owner release, no production inference. M1C1 Android host and physical biometric consent NOT_RUN. W2 STOP.
