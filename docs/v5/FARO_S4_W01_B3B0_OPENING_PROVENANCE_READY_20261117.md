# FARO V5 W01 B3B0 — independent READY exact4 for Finance9 opening provenance read
Fecha AR: 2026-11-17. Unit `S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_READ_DEV`. Reviewed HEAD `3befada49f6bbafb54c094d35e61b7b654d75f64`; independent B5 `docs/v5/FARO_S4_W01_B3B0_OPENING_INDEPENDENT_B5_20261117.md` blob `0c8a80f859ccf11d1bc84c4562e042a874869e14`; guard `44079410651` SUCCESS on exact SHA. B1–B5 initial `docs/v5/FARO_S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_B1_B5_20261117.md` guard `44078404122` SUCCESS.

## B1
Source `RealityStore.kt` real `readFinance()` with `COALESCE`, `finance_account_opening.amount_minor>0`, `finance_opening_confirmation` schema9 with explicit CONFIRMED + amount or LEGACY_UNVERIFIED NULL, migration 8→9 marks unverified. No product read. B3A scoped PASS and dormant.
## B2
Reuse true FinanceSnapshot ledger under one read-only SQLite deferred transaction to ensure cross-process consistency; no schema changes, no extra owner, no UI, no new ACL authority, no repeat of 8/8 B3A or M1C1B1C 10/10.
## B3
Chosen `BEGIN DEFERRED TRANSACTION` and `ROLLBACK` in finally on SAME owner connection under JVM lock, check activeTransactionId absent. No `BEGIN IMMEDIATE` for display. Inconsistency/errors -> null fail-closed. SQLite WAL snapshot docs https://www.sqlite.org/isolation.html.
## B4
Exact four permitted functional paths:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadTest.kt`
4. `.github/workflows/faro-v5-s4-w01-b3b0-opening-provenance-directed.yml`
One DTO/port source is dev/stage and **not an authority grant**, `RealityStore` factory+transactional reader, one 8-case JUnit, one directed CI with XML. Do not touch Android app, Surface, main, V4.4, FBS2v4, schema definitions, catalog or W2.
## B5
Independent audit: `CONFIRMED` must match positive physical opening; `CONFIRMED` zero only with explicit row and no physical positive opening, `LEGACY_UNVERIFIED` returns null amount, missing confirmation returns null, contradiction/overflow returns null whole read. Single snapshot must not mix OS process updates; fail closed when active write or closed. NEVER claim human authenticated by DB flag; B3B1 owner-approved ACL/source adapter is future independent unit. O01–O08 directed tests, no hardware G15; W2 STOP.
## B6
READY_TO_IMPLEMENT ONLY after guard preflight on exact4; no functional code in this doc commit.
## B7
PENDING_FINAL_AUDIT real CI/XML SHA, guard, writer RELEASED. No product Finance.
