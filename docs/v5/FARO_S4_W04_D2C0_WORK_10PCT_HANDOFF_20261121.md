# FARO V5 — W04-D2C0 · WORK TURBO (10% remaining weekly)

**ONLY TASK: FINISH W04-D2C0 B6–B7, one small JVM DEV unit.** No D2C1, D3/D4/W04-E/F, no user-facing UI. Repo etacsantafe-source/FARO-V5, branch work/s3-night-20261102. After documentary READY commit and its guard are confirmed, do NOT redo B1–B5, code of D0/D1/D2A/D2B, Android integration, finance, or research. Read AGENTS.md, docs/v5/FARO_CONTINUITY_POINTER.json, docs/v5/FARO_PROTOCOL_WORK_PERMIT.json, the preaudit docs/v5/FARO_S4_W04_D2C0_MERCHANT_SUMMARY_B1_B5_20261121.md (blob 0370e9f5edd5bf06504d6e279e21710bb0b4b632), and this handoff.

## B6 — Exactly three routes, no extras
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04MerchantWhatIfSummary.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04MerchantWhatIfSummaryTest.kt
3. .github/workflows/faro-v5-s4-w04-d2c0-merchant-summary.yml

Implement one pure Kotlin internal read-model: summarize(base D2A Estimate, scenario D2B Scenario) -> typed Decision. Verify identity/fences, unique needs, base/source-context/pack/price/discount consistency and original D2A/D2B group subtotals via Math.addExact, fail closed on any forged amount or overflow. Output immutable rows separately by (currency,merchant,locality,payment): observed historical subtotal, modeled subset base, hypothetical discounted amount and hypothetical after; group COVERAGE FULL/PARTIAL, global partial/unmodeled needs. **NO global totals, merchant price ranking, cashback amount, current quote, redeemed saving or real payment.** Cashback status must be UNVERIFIED_NOT_APPLIED marker WITHOUT numeric value. Read-only, no I/O, no SQLite, no FX, no Finance, no stock, no grants, no Apply, no new dependencies, no G15.

Eight directed JUnit5 test methods from B5 T01–T08, not twelve. Workflow Java21 Android SDK37, :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04MerchantWhatIfSummaryTest, assert JUnit XML EXACT (tests=8 failures=0 errors=0 skipped=0) + eight distinct testcases and upload artifact. Use precedent D2B workflow, including per-path parent READY preflight, bounded 1..3 route count for subsequent RED_FIX (initial B6 should alter all 3). Commit has FARO-UNIT:S4_W04_D2C0_MERCHANT_WHATIF_SUMMARY_DEV; permission was created in prior docs-only commit. **Acquire global CAS writer from writer-lock branch, verify HEAD and READY before any write, release during CI.**

## B7 — Real evidence or safe stop
Run actual GitHub Actions guard + directed CI, fix only real faults within the same unit. Download/inspect XML artifact, verify 8/8 zero error/failure/skip and exact SHA of all paths. B7 PASS only if evidence real; consume READY→BLOCKED in docs-only checkpoint, guard docs, release writer CAS, report checkpoint/run/test count. On RED, record RED_FIX with actual logs and permission for same exact unit before any functional correction. If the last 10% weekly Work allocation runs out, prioritize a safe checkpoint and writer RELEASED, not repeat or expand.
