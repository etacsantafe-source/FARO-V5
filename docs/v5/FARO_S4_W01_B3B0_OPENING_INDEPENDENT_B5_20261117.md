# FARO V5 — W01-B3B0 independent B5 · SQLite9 opening source and coherent read
**Fecha:** 2026-11-17 AR. Baseline audited HEAD `e2ddcd4c0d7a57ad20f0cb8d82437b57eaadf845`. Work unit `S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_READ_DEV`. Evidence original: `docs/v5/FARO_S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_B1_B5_20261117.md`. Its guard `44078404122` and pointer-seal guard `44078570682` both SUCCESS. NO permit READY yet.

## B1
READ real `RealityStore.kt` and schema9:
- `readFinance()` reads finance_state.revision then account list with `COALESCE(finance_account_opening.amount_minor,0)` and entries. A 0 does **not** attest an explicit opening.
- `finance_account_opening`: `amount_minor INTEGER NOT NULL CHECK(amount_minor>0)`, so absence represents 0 for ledger arithmetic but **not provenance**. `createFinanceAccount` inserts opening only when >0.
- `finance_opening_confirmation` schema9: `LEGACY_UNVERIFIED` amount NULL; `CONFIRMED` amount non-NULL, timestamp non-NULL, provenance string. 8→9 migration records unverified (even if opening=500); no confirmation from a user at migration time.
- `RealityStore` uses one `connection` and synchronized `lock`, `activeTransactionId` for write scope; `readFinance()` is reentrant inside the lock. Requires an explicit **read transaction** around ALL SELECTs because synchronized(lock) alone does not freeze other OS writers. The ordinary schema7/8 openers must reject the Finance9-only port.
- Reference tests `RealityFinance9MigrationTest.legacyOpeningRemainsUnknownRatherThanInferredZero`, `RealityFinance9TrustedStageGrantIssuerPortTest`, `RealityFinance9Schema.installOnStagedEightConnection` are already certified and not duplicated.

## B2
Reuse `readFinance()` and `FinanceSnapshot.ledgerMinor()` ONLY after physical+confirmation equality check. No migrations, DDL changes, second Reality owner, derived balance SQL, source mutation, receipt creation, permissions or FX. Do not rewrite V4.4, S2/S3, FBS2v4, 180 descriptors or UI B3A.

## B3
Options: A (chosen) per-call `BEGIN DEFERRED TRANSACTION` on SAME RealityStore SQLite connection under Kotlin mutex after `activeTransactionId==null`; read snapshot and joined provenance within this transaction, `ROLLBACK` in finally (read-only, no COMMIT) even on failure; SQLite WAL pins snapshot at first SELECT, allowing concurrent external writers without mixing versions. B (rejected) multiple independent SELECTs with Kotlin lock alone; may mix OS process data. C (rejected) `BEGIN IMMEDIATE` to block other writers for UI read; excessive write blocking. D (rejected) `FinanceSnapshot.ledgerMinor()` blind as verified.
Sources: https://www.sqlite.org/lang_transaction.html and https://www.sqlite.org/isolation.html; legacy user UX decision `docs/v5/FARO_S4_W01_ARCH05_06_UX_ACCEPTANCE_DECISIONS_20261116.md`.

## B4
Exactly four routes; NO app/Surface integration or product wiring:
1 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadPort.kt` new immutable DTO `RealityFinance9OpeningProvenanceRead`, account states `UNVERIFIED/CONFIRMED/MISSING`; amount and computed balance nullable, never fabricated 0; sourceRevision and rows. Every row enforces provenance invariants. This port is **NOT** a user ACL grant.
2 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` new STAGED9 dev factory + private owner method guarding schema9, closed/active write, read transaction and confirmed amount comparison. A positive confirmed opening must match a physical `finance_account_opening` positive row. A confirmed zero is permitted only if `finance_opening_confirmation` row explicitly says `CONFIRMED` with amount 0 and no physical positive opening; no invented 0.
3 `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadTest.kt` new eight JUnit SQLite stage-only cases `o01..o08`.
4 `.github/workflows/faro-v5-s4-w01-b3b0-opening-provenance-directed.yml` new CI compile/tests, named XML 8/8, scoped schema9 regression. Other files strictly disallowed.
Raw DTO read returns synthetic STAGED source only. No ACL authenticated; app adapter B3B1 still to do, and Finance9 opening CONFIRMED can be written by tests. No shipping use.

## B5
Independent adversarial dispositions:
- **PASS FOR DEV READ SOURCE ONLY:** explicit `CONFIRMED` amount equals raw positive row, or `CONFIRMED` zero with zero raw opening rows. Absence of confirmation => UNKNOWN with null balance, even if finance ledger happened to be 0. Unknown/partial accounts may carry account metadata but NEVER amount.
- **DENY whole snapshot** on contradictory positive CONFIRMED amount, missing positive raw row, unexpected confirmation state, numeric overflow, duplicate IDs, or SQL errors; return `null` not false EMPTY_CONFIRMED. Retain unknown rows for missing/legacy confirmation with null values. Empty confirmed snapshot does not attest consumer ACL: future adapter must decide if authorized.
- **Read consistency:** single `BEGIN DEFERRED` + `ROLLBACK` same connection under lock after no active Apply, reentrant `readFinance()`. No `BEGIN IMMEDIATE`, no transactions nested inside Apply. Last usable state cannot be cached across future ACL revocation; never expose source to UI directly.
- Epoch/revocation/UID/pointer/human admission are **NOT** implemented here. Because `finance_opening_confirmation` can be directly written by tests, `CONFIRMED` here certifies SQL agreement ONLY, NOT authenticated human consent. Product requires trustworthy confirmation writer and two-process ACL source B3B1.
- Test matrix O01 legacy 500 -> unknown; O02 missing row -> unknown; O03 explicit zero -> confirmed; O04 positive mismatch -> null DENY; O05 confirmed positive with no physical opening -> null; O06 entries +/-/overflow -> positive/negative exact and fail closed overflow; O07 SQL WAL snapshot coherent across external write, no transaction residue; O08 schema7/8/closed -> DENY. Proposed 8 tests NOT_RUN until B6.
- Keep W2 STOP; V2 product OFF; S4 B3A dormant; no Android G15, paid APIs, exports or external payment.

## B6
NOT_RUN; requires future independent READY exact4 and guard success.
## B7
PENDING; future real CI XML+SHA, no false hardware/product claims.
