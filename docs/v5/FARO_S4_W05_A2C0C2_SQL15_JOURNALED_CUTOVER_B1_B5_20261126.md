# FARO V5 — W05-A2C0C2 · SQL15 journaled five-store DEV cutover B1–B5

Date 2026-11-26 Argentina. Repo `etacsantafe-source/FARO-V5`, branch `work/s3-night-20261102`.
**Unit**: `S4_W05_A2C0C2_SQL15_JOURNALED_FIVESTORE_CUTOVER_DEV`.
Baseline confirmed `e761d0a42290fdcd8a6d8d87bb6e6509b98f1d88` (A2C0C1B B7 PASS 44/44; guard `45908034417` SUCCESS; writer RELEASED epoch 2137).
**PREAUDIT ONLY — NOT READY**. No code, no cutover, no stock real, no plan Apply, no productive V2, no paid APIs, no Auto/Noche. This is an independent scope beyond A2C0C1A and B, not an inherited permission.

## B1 — Current evidence, missing exact capability

- W05-A2B 9/9 B7 PASS (do not repeat). A2C0A schema SQL15, C0B1 FBS2v12 auth, C0B2 SQL15 rows, C0B3 five-store STAGED replay, C0C0 readback structural, C0C1A full five-owner semantic STAGED compare (35/35) all B7 PASS DEV.
- A2C0C1B SQL15 ACTIVE WAL-aware isolated read-only B7 PASS on code `534445246a1c4572961823e40ef72e7a7f5afa95`, original XML **44/44** (artifact `15158472749`, CI `45907516099` SUCCESS, code guard `45907516169` SUCCESS), final doc `docs/v5/FARO_S4_W05_A2C0C1B_B7_PASS_SQL15_ACTIVE_WAL_AWARE_DEV_20261126.md`, final guard `45908034417` SUCCESS. A2C0C1B's `completedDevSelection` parameter is deliberately a synthetic test seam, **NOT** a real SQL15 ticket nor production host.
- What remains missing: a separately authenticated, journaled SQL15 **generation selection** (SQL14 ACTIVE origin → full SQL15 STAGED target) that preserves the pointer sequence across crash/rollback and certifies completed selection with a *real* ticket. No `FaroV5Household15Evidence2DevJournaledCutover` exists. The existing SQL14 cutover `FaroV5Household14Evidence2DevJournaledCutover` only certifies SQL14 and cannot be reused as proof for SQL15.
- Before any pointer switch, `FaroV5Household15PreCutoverReadback.verifyOnly` already authenticates FBS2v12 bytes/pin, checks five sealed SQLite files and semantic equality, returning `EvidenceOnly(sourceGenerationId,stageGenerationId,authenticatedArchiveSha256)`. **Require returned sourceGenerationId == selected SQL14 origin ID**, not merely source != target. Origin must carry a real COMPLETED SQL14 Evidence2 journal ticket.
- Writer/actual branch permissions are independent: current `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` is BLOCKED/consumed for A2C0C1B. Do not code C2 before new guard + READY. `main`/V4.4 untouched; physical Moto G15 NOT_RUN.

## B2 — Reuse and V4.4 comparison

- Reuse `FaroV5Household14Evidence2DevJournaledCutover` as **design** only: acquire root `finance9-dev-root.lock`, gate `SingleProcessMaterialWriteGate`, authenticate/pin archive, durable one-use ticket, BLAKE? **NO** — exact SHA-256 for ticket/archive/five-store identity, `FileBootstrapJournal`, `GenerationSwitchCoordinator`, `ValidatedGenerationActivationCoordinator`, `FileGenerationReplayFence`, `FileActiveGenerationPointer` and pointer equality **(ID plus monotonic sequence)**; no copy-paste alternate state machine.
- Reuse `FaroV5Household14Evidence2DevJournaledCutover.hasCompletedSelection(root,origin)` to prove the prior SQL14 origin was selected, rather than accepting a caller boolean. Preserve archived source provenance and the exact FBS2v12 cipher bytes; no re-encryption, lossy 14→15 conversions or secondary writer. Preserve all five owner's verified identity/canonical descriptor.
- Reuse `FaroV5Household15PreCutoverReadback.verifyOnly` for read-only pre-switch semantic proof, then `ActiveSqlitePreflight.requireAll` to inspect the *new* ACTIVE SQL15 without `immutable=1` and with WAL-aware read semantics during provisional post-switch activation. Only expose `FaroV5GenerationStoreGraph.inspectActiveHousehold15DevReadOnly` to subsequent DEV read consumers after real C2 `hasCompletedSelection`, not to issue a ticket.
- V4.4 references for shopping/stock UX cannot authenticate v12 bytes, provide SQL15 recovery, or replace V5 journaled generation authority. No files in V4.4/main or existing certified A2C0 stages need alteration.

## B3 — External engineering evidence and alternatives

- SQLite WAL reference: https://www.sqlite.org/wal.html — committed frames can remain in WAL; do not use `immutable=1` or delete live `-wal/-shm` after switch. Read-only active connections may legitimately create sidecars, so target sealed fingerprint rules and post-switch ACTIVE fingerprint rules differ.
- SQLite Isolation: https://www.sqlite.org/isolation.html — a WAL reader observes a consistent snapshot; post-switch health needs a real active preflight and a fresh full-pointer fence.
- SQLite Atomic Commit: https://www.sqlite.org/atomiccommit.html — crash/power loss must be tested around durable transitions, but **SQLite's intra-DB atomicity does not by itself make five independent SQLite DBs and the generation pointer atomic**. The preexisting V5 bootstrap journal/recovery is the required outer authority.
- SQLite Transaction: https://www.sqlite.org/lang_transaction.html — `BEGIN DEFERRED` readers versus `IMMEDIATE` writers; never construct a second write transaction during readback.
- Existing implementation ecosystems: SQLite (public-domain SQLite engine already used via AndroidX bundled driver in FARO), AndroidX SQLite driver (project's Kotlin SQLite adapter). The reference material is used for behavior, with no external source code imported or new paid dependency.
- Rejected: (a) calling `FileActiveGenerationPointer.compareAndSet` outside validated journal; (b) marking the C1B synthetic selection callback `true` and claiming C2 completed; (c) copying bytes with WAL active or unlinking sidecars; (d) cross-database SQLite multi-ATTACH atomicity assumed; (e) skipping recovered pointer check on process restart; (f) replaying the FBS2v12 archive twice after a committed cutover; (g) activating auto/purchases/stock.consume based only on health.
- Chosen: isolated DEV journaled two-phase admission, content-attested ticket + recovery/rollback, separately verified ACTIVE health and a completed-selection predicate, **no Apply or stock semantics**. No risk downgrade of existing SQL14 cutover.

## B4 — Smallest independent implementation and exact route scope

Proposed bounded `EXACT3` new paths; all previous certified code and tests **remain untouched**:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15Evidence2DevJournaledCutover.kt` (new isolated owner, ticket and replay journal).
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15Evidence2DevJournaledCutoverTest.kt` (new end-to-end staged/active/rollback/crash vectors).
3. `.github/workflows/faro-v5-s4-w05-a2c0c2-sql15-journaled-cutover.yml` (new directed CI exact3 + parent preflight + original XML).

Contract sketch:
- `acquire`: DEV-only root/closed-host lease, writer gate, no automatic takeover. `authorize`: foreground affirmative decision, existing full SQL14 origin pointer with real COMPLETED SQL14 ticket, exact FBS2v12 original bytes/pin and C1A full semantic readback, target sealed/attested SHA, same source identity, no pending unrelated journal; persist immutable ticket and journal to `VALIDATED` without changing pointer.
- `start`: same operation, target, archive bytes, passphrase, full pointer and live owner permission; enforce reverified sealed target, owner context, ticket identity and journal; transition to `SWITCH_PREPARED`, then CAS pointer exactly once under `GenerationSwitchCoordinator`. Never use `compareAndSet` outside journal state machine.
- `recover`: resume from any interrupted phase, recognize pointer already switched without performing a second CAS; if new SQL15 ACTIVE health/projection cannot be verified, rollback to certified SQL14 origin *only before material target writes are accepted*. No second Apply.
- Runtime `health`: use exact ACTIVE five-store schema 4/15/4/2/2, WAL-aware preflight, no SQLite migrations or initializer. Before COMPLETED ticket, the C1B host callback must remain untrusted/disabled outside tests. Validate archive source/ticket/selection, no cross-version shortcut. `hasCompletedSelection(root,selected)` must bind ticket SHA, journal COMPLETED, material write fence, replay fence, pending operations empty and **full pointer ID+sequence**. The completed ticket is not a grant for stock.consume, UI, payments, or production.
- `rollback`: validate SQL14 origin health and intended pointer rollback sequence, reject if target accepted material writes; no silent data loss. Do not assume old source sealed/no-sidecar after ACTIVE use. Revalidate owner/lease/permissions before every state transition; close channels and zero/copy sensitive password buffers as appropriate.
- Avoid a new store or DB schema, DAO, owner capability, or generalized activation root. Only new class and its bounded tests/workflow. If this minimal scope genuinely requires changing a pre-certified file, **STOP**, update B4/B5 with concrete evidence and obtain a separate bounded permission rather than silently expanding exact3.

## B5 — Adversarial acceptance before implementing

Tests must be real, original directed JUnit XML with zero failures/errors/skips and unique names; candidate cases:
1. Reject absent SQL14 origin's COMPLETED ticket even with healthy SQL14 DB and valid-looking pointer.
2. Reject unpinned/incorrect FBS2v12 passphrase, altered/resealed ciphertext, wrong archive source, target==source or wrong stage descriptor, without ticket or pointer mutation.
3. Reject user confirmation absent/revoked at authorize or start; no pointer switch and no Apply.
4. After successful authorize: journal phase VALIDATED and pointer untouched; repeated authorize idempotent only for exact identical ticket; replacement ticket denied.
5. Success: completed foreground start creates exactly one SQL15 pointer generation step, full five-store ACTIVE health 4/15/4/2/2 and completed ticket bound to exact pointer sequence. C1B read-only returns typed snapshot only once C2 certified, no stock writer.
6. Crash injection after SWITCH_PREPARED before pointer publish, and after pointer publish before journal advance; recovery consistent/idempotent, never second pointer step.
7. Recovery from SWITCHED/HEALTH_VERIFIED/CLEANUP_PENDING phases without duplicate replay.
8. Unhealthy target after switch yields certified SQL14 rollback **before target material writes**; pointer sequence increments as designed and journal reaches ROLLBACK_VERIFIED; origin reopens with WAL.
9. Pointer ABA (switch away then back to same ID with new sequence) rejected by ticket and host, even if table rows match.
10. Empty, tampered or symlinked ticket; stale lease; concurrent owner lock; stale operation; path alias/hardlink or WAL symlink; foreign key/integrity/version mismatch any store => fail-closed.
11. Adversarial interruption before and after material-write fence: rollback prohibited after writes accepted; do not delete SQL15 consumption events or re-run Apply.
12. ALREADY_COMPLETE retry exact same ticket and reboot recovery no mutation. Restart cannot manufacture a new target pointer from stale evidence.
13. Negative test that a C1B fixture callback with `completedDevSelection={true}` is not a substitute for `hasCompletedSelection` in C2.
14. Regression C1A 35/35 and C1B original 44/44 on their certified SHA (do not repeat their full Gradle suites unless a verified dependency requires it); directed C2 needs its **own** original XML plus journal/rollback vectors.

Gate sequence: commit this documentary B1–B5, release writer, verify guard SUCCESS and exact blob markers. Then independent docs-only READY exact3 permit, release writer, guard SUCCESS and per-path preflight. Only after that can B6 code be committed with `FARO-UNIT:S4_W05_A2C0C2_SQL15_JOURNALED_FIVESTORE_CUTOVER_DEV`. New C2 B7 needs directed original XML + code guard and B7 receipt, permit consumed, pointer/writer verified. No inferred PASS.

V2 productiva, real stock, payments, Auto/Noche OFF; G15 NOT_RUN. A2C1 stock.consume, A2D UI, A2E acceptance global remain OPEN.
