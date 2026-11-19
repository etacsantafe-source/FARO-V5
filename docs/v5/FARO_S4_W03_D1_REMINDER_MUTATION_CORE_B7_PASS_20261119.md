# FARO V5 — S4 W03-D1 Reminder Review/Apply Core · B7 PASS scoped DEV
Date 2026-11-19. Unit S4_W03_D1_REMINDER_MUTATION_CORE. **PASS_SCOPED_W03_D1_DEV_JVM_9_OF_9** only, not W03-D whole, W03 global, OS notifications nor physical G15.

## B1
Recovered AGENTS.md, current pointer and protocol permit. W03-A 33/33, W03-B 8/8, W03-C 10/10 previously closed; not retested. B1-B5 specific preaudit docs/v5/FARO_S4_W03_D1_REMINDER_MUTATION_CORE_B1_B5_20261119.md blob ad7af2c9d72b608a4ddac812c0a9617b557321f0. Doc audit guard 44557705130 SUCCESS. Permit READY commit c02e28a6becab3066c895eb0f79c0ce8896e4e24 guard 44557973692 SUCCESS, original nine routes exact.

## B2
Reused built-in N-032 canonical operation IDs reminder.create/update/cancel/attend, W03-B Workspace Review/Apply/receipts as a pattern, existing Finance9 RealityStore SQLite owners with one active SQL lease, W03-C civil occurrence fingerprint, and existing Auxiliary NoticeCandidate data model. Kept Reminder and Matter owner separate, with distinct linked Matter scope. No new schema, DB, catalog operation or auto scheduler.

## B3
Kotlin module boundary is enforced: internal ReminderPersistencePort stays private to RealityStore. Controlled trusted DEV composition factory is used instead of raw ports. Existing isPlanningBackupSchemaMounted() checks admitted schema8 or Finance9 schema9 with planningStage8 flag, preserving schema7 exclusion. Source and follow-up docs: https://kotlinlang.org/docs/visibility-modifiers.html ; https://developer.android.com/topic/architecture/data-layer/offline-first ; https://sqlite.org/lang_returning.html . No code copied from GPL/AGPL alternatives, no paid API or sync dependency.

## B4
Original B6 commit de726c927e88a68a8504a2556f3148dfbe7b40eb changed 9 approved paths. RED_FIX1 compile nullable property cross-module, B7 doc docs/v5/FARO_S4_W03_D1_REMINDER_CORE_B7_RED_FIX_20261119.md, code 6d3499262848fc9b07188f8cceb03b3308e3a80a. RED_FIX2 app module attempted to read internal Reality SPI, doc docs/v5/FARO_S4_W03_D1_REMINDER_CORE_B7_RED_FIX2_20261119.md, code 3973690406dba3671f1f62cf408886319de300e6. RED_FIX3 exact schema8 guard blocked admitted Finance9 DEV graph, doc docs/v5/FARO_S4_W03_D1_REMINDER_CORE_B7_RED_FIX3_20261119.md, code **0e4333a25d74c1bb59863f1835683f4b0b687256**. Final 1-route fix reuses already audited isPlanningBackupSchemaMounted() at trusted Reminder read/factory/active Apply insert/mutation, preserving stage-only API prohibition, SQL CAS, active transaction/grant, owner isolation and all remaining original routes. No permissions widened to production and no changes W01/W02/W03-A/B/C.

## B5
Directed independent negative and positive tests:
1 createEditAndDurableReceipts: human Review, create/edit and durable receipts, duplicate replay denied.
2 cancelKeepsRuleAndAcknowledgedHistory: cancel sets enabled=false with preserved ACK row.
3 attendRejectsForgedCivilIdAndCannotDoubleAcknowledge: exact civil ID, one ACK.
4 concurrentStaleAndDiscardNeverOverwrite: stale owner CAS, Discard no mutation.
5 foregroundPolicyAndSelectionDeny: revoked/absent host authority denied.
6 linkedMatterRequiresIndependentGrant: Matter link denied without independent policy, explicit DEV policy allows valid existing Matter.
7 canonicalTamperingAndReconcileOnlyFailClosed: trailing bytes and receipt-only path can't mutate.
8 sourceCandidatesAreReadOnlyDistinctAndCrossOwnerLinksFailClosed: projected NoticeCandidates only, no Auxiliary inserts or Matter effects.
9 ackCancelledAndDisabledDoNotFabricateLocalNoticeOrMutateMatter: ACK/disabled suppress source candidate.
Limitations: per-process DEV policy provider in tests, not Android production. No two-process crash-in-COMMIT test or OS permission/dispatch tests, no physical G15. W03-D2 must materialize Auxiliary notices and implement independently authorized seen/dismiss, without mutating Reminder or Matter.

## B6
Final functional SHA 0e4333a25d74c1bb59863f1835683f4b0b687256, guard workflow run 44566045497 **SUCCESS** same SHA. W03-D1 directed workflow run 44566044923 completed **SUCCESS**, job 135707921637 SUCCESS. Preflight against READY parent 94158256d7f84a2ee02f17b1365e0302334d1d21 PASS for changed single route. Gradle :app:testDebugUnitTest targeted S4W03ReminderReviewHostTest & S4W03ReminderLocalNoticeProjectionTest BUILD SUCCESSFUL 115 tasks. CI logs/XML confirmed 7/7 + 2/2, 9 tests failures=0 errors=0 skipped=0, marker W03_D1_REMINDER_REVIEW_XML_PASS 9/9. Official artifact w03-d1-reminder-core-xml id 14552217418 digest sha256:f85f4a40bd2ac15aabcccbc7b5d802407e424499ec41f5de44998b77c6029e1c, not expired.

## B7
**PASS_SCOPED_W03_D1_DEV_JVM_9_OF_9**. Original PREAUDIT immutable; RED_FIX documents retained, no synthetic PASS for red runs. Permit READY consumed to BLOCKED; next W03-D2 Auxiliary Notice materialization+seen/dismiss own B1-B5/guard/READY/B6/CI/B7. W03-E Android, W03-G global and optional OS F remain NOT_RUN. Writer must be RELEASED epoch1310 and final B7 documentation guard SUCCESS before unit is declared fully checkpoint verified.
