# FARO V5 W01 M0B — READY exact paths
Fecha AR 2026-11-16, unit S4_W01_M0B_FINANCE9_GENERATION_ADMISSION. Reviewed HEAD 5b7aa35501d5819d8b0d83c0568ab7c1372fa512. Guard 43952286232 completed/SUCCESS; writer RELEASED epoch983. Fuente B1-B5 docs/v5/FARO_S4_W01_M0B_STAGE_GENERATION_B1_B5_20261116.md blob 5daaf7b6afebc3c66bb944a1dbf0fd906e437ed6.

## B1
Código M0A cierre B7 PASS scoped 7/7, Reality9 internal. Read StagedSqlitePreflight and FileStorageGenerationLayout and FileActiveGenerationPointer; no new schema in RealityStore default.
## B2
Reuse M0A. Four new files only, no edits to old generation graph / ledger / catalog.
## B3
SQLite bundled driver remains inside store:reality, owner generation retains descriptor/pointer admission. No paid APIs, no ACTIVE migration.
## B4
READY exclusively:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9StagedFilePort.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigration.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigrationTest.kt
- .github/workflows/faro-v5-s4-w01-m0b-finance9-generation-directed.yml
7 directed generation tests JVM in store:generation. No production host/cutover/backup9.
## B5
Pre-open exact 5-store sealed preflight, symlink/WAL refuse, source8 only, full pointer sequence pinned and tested. No runtime Apply grant, Android, G15, M0C/M1 yet.
## B6
READY_TO_IMPLEMENT after independent exact gate preflight and CAS writer, code commit separately from permit. If CI RED, same unit RED_FIX only.
## B7
PENDING_FINAL_AUDIT. Verify 7/7 XML and sha guard, writer RELEASED, permit consumed.