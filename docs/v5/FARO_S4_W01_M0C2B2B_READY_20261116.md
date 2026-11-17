# W01 M0C2B2B — READY exact five paths Finance9 full FBS2v4
Unit: S4_W01_M0C2B2B_FINANCE9_FULL_FBS2V4_CAPTURE_RESTORE. HEAD audited a078e29eb7925bda4bddcb773fe1ce0f45815f31. Guard 43994971047 completed/SUCCESS. Prev writer RELEASED epoch1003. No code in this commit.
## B1
Source docs/v5/FARO_S4_W01_M0C2B2B_FINANCE9_FULL_ARCHIVE_B1_B5_20261116.md blob 17818964b61a5c6dffba15629f767d5534fdf49a; preceding scope B7 6/6 plus 19/19 CI SUCCESS.
## B2
Reuse existing five store restore and sealed Finance9. Preserve v1-v3 legacy public fences and V4.4.
## B3
SQLite WAL multiple DB transactions are not globally atomic; use immutable archive identity and idempotent stage-only replay. References in preaudit.
## B4
Approved EXACT paths:
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9FullArchiveCoordinator.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9FullArchiveCoordinatorTest.kt
- .github/workflows/faro-v5-s4-w01-m0c2b2b-finance9-full-archive-directed.yml
Criteria: T01–T10 full five-store capture+Finance9, authenticated replay and crash recovery.
## B5
DENY foreign ACL, stale pointer, sidecars, nonfresh target, tamper, archive identity swap. No cutover or Android.
## B6
READY_TO_IMPLEMENT after exact-path preflight and next writer CAS; only distinct code commit.
## B7
PENDING_FINAL_AUDIT JUnit XML + guard + independent diff + RELEASED. M1/W1 next, W2 STOP.
