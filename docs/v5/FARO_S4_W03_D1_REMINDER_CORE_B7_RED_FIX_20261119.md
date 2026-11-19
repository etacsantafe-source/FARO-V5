# FARO V5 — W03-D1 Reminder Review/Apply · B7 RED_FIX 1
Date 2026-11-19. Same unit S4_W03_D1_REMINDER_MUTATION_CORE. Code SHA de726c927e88a68a8504a2556f3148dfbe7b40eb committed with nine exact authorized routes; original READY parent c02e28a6becab3066c895eb0f79c0ce8896e4e24, preaudit docs/v5/FARO_S4_W03_D1_REMINDER_MUTATION_CORE_B1_B5_20261119.md blob ad7af2c9d72b608a4ddac812c0a9617b557321f0, guard 44557705130 SUCCESS, READY guard 44557973692 SUCCESS. Writer released epoch 1303. Code mandatory guard 44559945014 SUCCESS.

## B7 — REAL FAILURE / RED_FIX
W03-D1 directed run 44559946864, job 135686174761 completed FAILURE. Preflight exact 9 routes PASS; Android/JDK set up PASS. `app:compileDebugKotlin` FAIL `SMARTCAST_IMPOSSIBLE`: `linkedMatterId` public property declared in another module, at v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W03ReminderLocalNoticeProjection.kt:26. No tests ran; XML artifact missing due compile failure, therefore 0/9 verified (do NOT claim tests failed, or PASS). The unexpected earlier workflow W03-A 44559945318 failed its own old parent preflight under newly changed RealityStore; it is not the directed W03-D1 JVM outcome and must NOT be re-opened or amended here.

ROOT CAUSE: foreign-module public nullable val cannot be smart cast by Kotlin when read twice. RERUN ACTION: bind `val linkId = rule.linkedMatterId` locally inside the loop, use this stable local in both filter and NoticeSensitivity; no changes to Reminder owner semantics, SQL, history, grants, Android or other owners. CI workflow originally required exactly 9 changed paths per code commit; RED_FIX delta will modify only projection + workflow, so update its self-audit count to 1..9 while checking each diff path via protocol gate. No other paths in RED_FIX. This is a scoped correction of the SAME B1–B5 and SAME unit; the permit must remain READY_TO_IMPLEMENT/PENDING_FINAL_AUDIT with last_code_review RED_FIX referencing this blob for the failed prior code SHA. On CI GREEN repeat exact XML 7 host + 2 projection, artifact and mandatory guard; only then final B7 PASS.

## B1
No new product scope. Initial W03-D1 B1 evidence immutable.
## B2
Reuse existing null-safe local snapshot, no widened dependencies.
## B3
Kotlin official smart cast rules: public properties declared in other modules cannot be smart-cast; stable local val solves without adding !! assertions. Documentation https://kotlinlang.org/docs/typecasts.html .
## B4
RED_FIX exact two authorized existing routes: S4W03ReminderLocalNoticeProjection.kt and faro-v5-s4-w03-d1-reminder-core.yml. Other original paths must remain byte-identical.
## B5
Negative: null linkedMatterId should remain visible for eligible non-linked Reminder, non-null linkedMatterId denied when not independently granted, downstream source remains read-only. Re-run complete unit 9 tests and grants/receipt XML after compiler fix. W03-D2 Auxiliary Notice write still NOT_RUN.
