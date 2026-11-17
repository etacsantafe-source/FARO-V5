# FARO V5 — W01-B3B0 · B7 PASS scoped opening provenance reader DEV/STAGED9
**Fecha AR:** 2026-11-17. Unit `S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_READ_DEV`. Funcional code exact SHA `9cfab42fa147aedd109eafc872263055cd4d6aec`. This B7 certifies SQLite consistency DEV only, not human authentication or shipping.

## B1
Original scoped B1–B5 `docs/v5/FARO_S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_B1_B5_20261117.md` validated by guard `44078404122` SUCCESS. Independent B5 `docs/v5/FARO_S4_W01_B3B0_OPENING_INDEPENDENT_B5_20261117.md` guard `44079410651` SUCCESS. Exact-four READY `docs/v5/FARO_S4_W01_B3B0_OPENING_PROVENANCE_READY_20261117.md`; preflight guard `44079649023` SUCCESS on SHA `6f019543c1c8a59ba82f182fa5bfe7503c46555f`.
## B2
Code B6 SHA `9cfab42fa147aedd109eafc872263055cd4d6aec`, exact FOUR allowed functional paths:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadTest.kt`
4. `.github/workflows/faro-v5-s4-w01-b3b0-opening-provenance-directed.yml`
Marker `FARO-UNIT:S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_READ_DEV` present in commit message. No additional app, Surface, manifest, DDL, FBS2v4, catalog180, S2/S3, main or V4.4 code edited. Writer RELEASED epoch1063 before CI.
## B3
New DEV `RealityFinance9OpeningProvenanceReadPort` returns source revision and per-account opening provenance status `LEGACY_UNVERIFIED/MISSING_CONFIRMATION/CONFIRMED_IN_SQL`. Only CONFIRMED SQL rows consistent with physical positive opening, or explicit zero in confirmation with no physical positive opening, can carry a nonnull opening and calculated ledger balance. Otherwise balances NULL. Contradiction, numeric overflow, schema mismatch, closed owner, active Apply scope or SQL errors DENY/null; NULL is not an authorized empty snapshot.
`RealityStore` uses ONE `BEGIN DEFERRED TRANSACTION` read-only transaction around existing FinanceSnapshot and joined opening/confirmation rows, protected by local mutex, with `ROLLBACK` in finally even on error. Cross-process SQLite WAL snapshot consistency explicitly tested. No `BEGIN IMMEDIATE`, no grant, Apply, receipt or SQL mutations in reader.
## B4 — Exact tests and CI SHA
- Directed run `44080304397`: COMPLETED SUCCESS code SHA exact. New JUnit named XML **O01–O08 8/8**, no errors/failures/skipped. Existing scoped `RealityFinance9MigrationTest.legacyOpeningRemainsUnknownRatherThanInferredZero` regression **1/1 PASS**. Log exact markers `W01_B3B0_REAL_SQL_OPENING_8_OF_8_PASS`, `W01_B3B0_MIGRATION_LEGACY_REGRESSION_1_OF_1_PASS`. JUnit artifact `faro-v5-w01-b3b0-opening-junit` ID `14327660332`.
- Code guard `44080304018` SUCCESS exact SHA. **20/20 auto-triggered workflows** completed SUCCESS on code SHA, including Reality8 legacy, Finance9 previous admission/gate, V2 Core and G4. NONE failed, not manually rerun.
- O01 LEGACY_UNVERIFIED positive opening → NULL; O02 absent confirmation → NULL; O03 explicit persisted CONFIRMED zero → zero; O04 positive disagreement → DENY; O05 positive CONFIRMED without physical opening → DENY; O06 income/expense balance Math exact + overflow DENY; O07 WAL old read snapshot and new revision; O08 schema7/schema8/closed store deny without modifying source.
## B5 — Residual risks
- SQL `CONFIRMED_IN_SQL` proves consistency of staged database state ONLY, not who signed a human confirmation. The stage fixture can write the confirmation table directly. Future trusted confirmation writer/real AndroidKeyStore G15 campaign and app owner ACL access B3B1 must remain distinct; FinanceSnapshot COALESCE 0 is still NOT a verified opening source.
- The port is DEV/STAGED9, unmounted in product Shell/CompositionRoot. No host read grant, no UI F02/F03 full workflow, no real account balances, no external payments. No override to productive V2. W01-B3B1/B3B2/B4/C/D/E/F remain OPEN; W08 predictive UI not implemented; W2 STOP.
- B7 scope is exactly new source + tests and verified existing regressions, NOT W01 full acceptance, no Android physical or talkback claims.
## B6
IMPLEMENTED `9cfab42fa147aedd109eafc872263055cd4d6aec`.
## B7
**PASS_SCOPED_DEV_STAGED9_OPENING_PROVENANCE_SQL_8_OF_8_AND_20_CI**. Consume permission/BLOCKED with last_code_review PASS, writer RELEASED epoch1064 and final document guard verify after this checkpoint.
