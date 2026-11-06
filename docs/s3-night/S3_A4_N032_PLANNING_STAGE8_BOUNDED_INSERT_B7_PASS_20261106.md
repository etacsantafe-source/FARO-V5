# FARO V5 S3.A4 N032 — STAGED Planning8 transactional insert + bounded rollback, B7 PASS_SCOPED
2026-11-06 AR. **Functional SHA** `8863adf3cf9a974fd87f27b535483164fc5a5692`. Unit `S3_A4_N032_PLANNING_STAGE8_BOUNDED_INSERT`.

## B7 — Independent final audit / verdict
**PASS_SCOPED_STAGED_PLANNING8_INSERT_BOUNDED**. **Not** production user CRUD, not a catalog action, not AtomicApply/Receipt/ACL, not S3.A4 CLOSED.

### B1–B5 before code; four scoped files
- Pre-audit `docs/s3-night/S3_A4_N032_PLANNING_STAGE8_BOUNDED_INSERT_B1_B5_20261106.md`, blob `f7b5081ae9a1f56febbc830a70ed5f0a38d57c31`, docs-only commit `b0f27cd0fd5040252af46599082bc327aa96eaf5`.
- READY docs-only permit commit `f2d33d2ce4bde6c4fc890bb2b75f62b03c28321a` enumerated exactly 4 paths. Functional B6 code `8863adf3cf9a974fd87f27b535483164fc5a5692` message `FARO-UNIT:S3_A4_N032_PLANNING_STAGE8_BOUNDED_INSERT`, modified only: new internal `RealityPlanningStageMutationPort.kt`, existing `RealityStore.kt`, new `RealityPlanningStageMutationT2Test.kt` and a workflow. No app/Chat/manifest/export/restore/172 official operation changes.
- Writer global CAS acquired epoch389 before work; RELEASED epoch390 for CI; reacquired epoch391 for B7; release planned epoch392 after checkpoint. Noche/Auto/V2 productiva/S4 OFF.

### Exact-SHA CI evidence, eight workflows completed SUCCESS
- [N032 directed #42298175424](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175424): **47/47 XML** with zero skipped/errors/failures. N032 new `RealityPlanningStageMutationT2Test` **6/6**, N031 Planning8 owners **7/7**, Reality G4 restore **5/5**, generation FBS2v3 stage **6/6**, graph legacy **17/17**, v3 envelope **6/6**.
- [N031 full Planning8 recovery #42298175598](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175598): **48/48 XML** and SUCCESS.
- [V2 isolated core #42298175351](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175351): SUCCESS, Chat session **10/10**, G5 host **1/1**, M2 debug **7/7**, schema7 generation **17/17**, dev M2B **4/4**, M1 **4/4**.
- [Reality G4 owner regressions #42298175349](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175349): SUCCESS; [G4 domain primitive #42298175547](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175547): SUCCESS.
- [FBS2 G4 paired capture #42298175368](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175368): SUCCESS, source capture **5/5**, G4 codec **5/5**, legacy codec **7/7**.
- [Reality receipts/finance/FBS1/FBS2 #42298175263](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175263): SUCCESS, receipts **3/3**, finance activity **3/3**, FBS1 **4/4**, FBS2 **7/7**.
- [Mandatory seven-block guard #42298175469](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42298175469): SUCCESS.
- All eight runs have `status=completed`, `conclusion=success`, `head_sha=8863adf3cf9a974fd87f27b535483164fc5a5692` confirmed; no selective result omission.

### What was actually implemented and tested
- Typed `RealityPlanningStageInsert` with Rule / Occurrence / MonthlyItem; **internal to Reality module**. Only `RealityStore(planningStage8=true)` can execute it; normal schema7 is rejected. Calls under Reality's single mutex and existing material write admission.
- `BEGIN IMMEDIATE` BEFORE current owner revision read, compare expected revision, insert bound values and increment ONLY affected owner revision using CAS; exact `changes()==1`, then read complete Planning snapshot and validate portable 4MiB/20k rows, canonical ordering, consistency with unmodified other owner and all FK **before COMMIT**. Every failure explicitly ROLLBACKs.
- New tests verify durable reopen and paired FBS2 read, stale version and duplicate IDs, missing Matter/rule foreign keys, schema7 refusal, rejection of nested Apply, 4MiB overflow with 7781 canonical long CJK-title rules (checks rollback state and revision), concurrent SQLite connections rejecting stale writer. Existing schema7 and FBS2v3 remain green.
- **No durable Apply receipt or idempotency key for this internal staged writer**. A duplicate is rejected, not claimed as an idempotent action; this is intentionally NOT callable from Chat/host.
- **No user data touched**, no Android physical G15 tests, no export/restore public v3, no scheduling/notification recurrence engine, no category 172 change, Guardados +8 STILL NOT_APPROVED. S2 8/8 frozen, S3 A4 OPEN and A5 X01–X12 NOT_RUN. V2 productive, Noche, Auto and S4 OFF.

### Exact next unit: N033
Require fresh B1–B5/permission for **trusted chat Review/Apply and ACL that binds Planning mutations to the durable ApplyPlan/receipts/Undo/recovery and active-generation identity**. Reuse the now-certified staged writer/Reality8/FBS2v3; do not silently promote stage-only port or declare A4 closed. Separate N031 Home pending and N015 Activity grounded in canonical owners, never invent counts. Avoid repeated proof of N031/N032; no large global certification.
