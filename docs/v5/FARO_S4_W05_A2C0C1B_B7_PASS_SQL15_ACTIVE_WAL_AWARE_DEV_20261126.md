# FARO V5 — W05-A2C0C1B · B7 PASS scoped DEV (SQL15 ACTIVE WAL-aware)

2026-11-26, repo `etacsantafe-source/FARO-V5`, branch `work/s3-night-20261102`.
**Unit:** `S4_W05_A2C0C1B_SQL15_ACTIVE_WAL_READONLY_DEV`.
**Final verified functional SHA:** `534445246a1c4572961823e40ef72e7a7f5afa95`. No production activation, real stock use, journaled C2 cutover or G15 campaign.

## B7 — Final bounded acceptance, original JUnit XML

- B1–B5 independently preaudited at `2b7838a61d766791f9704a38b2c35b492b1cfce0`, doc blob `fbce8503e2d9474ed8ff546930f51841da2758b0`, guard `45904196687` SUCCESS. Separate permission READY exact5 `1e1b52e9de8d7809d1204b56d9b87c0f70413121`, guard `45904328597` SUCCESS. No files outside five authorized paths.
- First B6 `066eaead39093ae154193d7fd42641bdbc5e4a23` had directed CI failure `45905334687` (4 new Reality fixtures failed; original RED_FIX documented). First corrective SHA `b5ec66ba8bdf5feaf4b3a07e035b9bf00b731ca7`, code guard `45906736785` SUCCESS, directed CI `45906736737` FAILURE with original XML 43/44: one test incorrectly mixed ACTIVE WAL read and sealed-stage migration; `B7 RED_FIX2` recorded `docs/v5/FARO_S4_W05_A2C0C1B_B7_RED_FIX_2_INDEPENDENT_SQLITE_FIXTURES_20261126.md`, guard `45907421917` SUCCESS. No duplicate certified work.
- Final corrective SHA `534445246a1c4572961823e40ef72e7a7f5afa95` changes **one path** `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ActiveDevReadTest.kt` and preserves the earlier exact5 bounded permission, uses independent SQL14 ACTIVE / SQL15 STAGED fixture files instead of deleting legitimate SQLite WAL/SHM. Both code commits include `FARO-UNIT:S4_W05_A2C0C1B_SQL15_ACTIVE_WAL_READONLY_DEV`.
- Mandatory code guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/45907516169, **COMPLETED SUCCESS** exact SHA `534445246a1c4572961823e40ef72e7a7f5afa95`, including seven-block chain and scoped preflight.
- Directed CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/45907516099, job `140205163100` **COMPLETED SUCCESS** for same SHA. Gradle Generation and Reality test steps SUCCESS, original XML verification step SUCCESS.
- Original ZIP artifact `w05-a2c0c1b-sql15-active-wal-readonly-xml` ID **15158472749**, digest `sha256:878c0ab2c8ea94fd3cf80fc9949778727339022bab41e10b30f5a2e17407c080`. Downloaded and parsed independently, with exact `tests/failures/errors/skipped` per suite:
  * `FaroV5Household15ActiveDevReadTest`: 5/0/0/0
  * `FaroV5Household15V12StagedReplayTest`: 15/0/0/0
  * `FaroV5Household14V11StagedReplayTest`: 5/0/0/0
  * `FaroV5Household15Fbs2CodecTest`: 8/0/0/0
  * `RealityHousehold15ActiveDevReadTest`: 4/0/0/0
  * `RealityHousehold15ConsumptionRowsPortTest`: 7/0/0/0
  * TOTAL **44/44 PASS**; 44 unique testcase identifiers, errors=0, failures=0, skipped=0.
- Scoped contract: `FaroV5GenerationStoreGraph.inspectActiveHousehold15DevReadOnly` performs five-store exact ACTIVE preflight 4/15/4/2/2 with WAL-aware reader, exclusive DEV root lock, full pointer sequence+ID, owner and synthetic fixture selection fence, and returns only typed SQL15 Reality history. `RealityStore` uses `SQLITE_OPEN_READONLY|SQLITE_OPEN_NOFOLLOW`, never `immutable=1` for ACTIVE, and does not export Apply/stock writer. Origin/archive SQL15 staged semantic equality was separately B7 PASS C1A (35/35).
- Boundary: C1B **DOES NOT** certify a real completed SQL15 journaled cutover: fixture selection callback is a DEV test seam, not proof of C2 ticket. No real ACTIVE15 host, no stock.consume, no Android UI or physical Moto G15. **Next independent gate A2C0C2** journaled SQL15 cutover requires its own B1–B5, READY, B6/CI/B7 and rollback/recovery verification. A2C1/A2D/A2E stay OPEN.
- Close only after publishing this B7 document, consuming this exact5 permit to BLOCKED, updating continuity pointer, verifying writer RELEASED epoch 2137 and final B7 document guard SUCCESS. This document does not predeclare those postcommit checks.
- Main, V4.4, V2 productive, stock real, payments and Auto/Noche unchanged OFF. No paid API.
