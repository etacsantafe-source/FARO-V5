# FARO V5 — G4 relevo 2026-11-05

Repo etacsantafe-source/FARO-V5. Branch work/s3-night-20261102. Current code/CI HEAD a49b5cea329b34d15d56891a12606cd33ab27c04.

## Verified
- G1/G2/G3 CLOSED. G4 **OPEN**. G5 physical later. S2 8/8, S3 formal 0/5, A2 3/5, S3 development ~40% **heuristic**.
- Reality Objectives+Matters durable SQLite v6: code 0fab63a, CI #42011878467 PASS 21/21.
- Four canonical search sources Workspace/Finance/Objectives/Matters, independent session ACL, DEV-only typed read-only navigation: code 05de1c6 + 40ceb0c, CI #42016712209 PASS 27/27, B7 docs/s3-night/S3_A2_G4_REAL_FEDERATION_TYPED_NAV_B7_20261105.md.
- SQLite migration v5 to v6 has strict compatibility preflight; production V2 stays OFF. Legacy FBS1/FBS2 backup cannot encode Objectives/Matters and fails closed when rows exist. No versioned portable G4 backup/restore implemented.
- Trigger-only workflow change a49b5cea extends V2 Core regression for Reality and generation changes. Protocol guard run #42018187421 PASS.

## RED — last CI
- GitHub Actions #42018187304, HEAD a49b5cea, **FAILURE**: FaroV5V2CoreDevAuthorityTest 9 PASS / 1 FAIL, case durablePointerSwitchRevokesDevSearchAndEmitsLiveInvalidation(). Failure AssertionFailedError reported against method line 366, not exact assertion line.
- Test file v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthorityTest.kt lines 366-389. It expects initial 1 invalidation then second on durable pointer switch. Host now merges Workspace + three Reality owner grant invalidations. Multiple initial invalidations are a **hypothesis**, not a proven cause; inspect actual assertion and event emissions before changing anything.
- Because test step failed, T4 debug 7/7 and manifest isolation steps in this CI did NOT run. Previous APK PASS is from earlier SHA. Do not claim V2 Core 10/10 or G4 closed.

## Exact next steps
1. On "Continuar FARO V5": recover this handoff, continuity pointer, latest CI and GitHub writer. Confirm CAS and lock RELEASED. Protocol permission is BLOCKED until new narrow B1-B5 permit.
2. Diagnose only the failed pointer-invalidation test; distinguish test assumption from genuine host bug. Minimal RED_FIX, targeted V2 Core 10/10, T4 7/7 and manifests, protocol guard. Reuse previous Reality 21/21 and federation 27/27 unless affected.
3. B7 final and G4 CLOSED only if acceptance is fully verified. Avoid any false claim about portable backup. G5 physical later; S4/Auto/Noche and shipping V2 OFF.
4. Always show S3 estimated vs formal progress separately and qualitative chat thermometer.

Writer for this handoff acquired epoch 261; release at completion. No background work.
