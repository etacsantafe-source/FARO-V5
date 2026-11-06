# FARO V5 — S3.A4 N031 Reality Matter due read · B7 (2026-11-06)

## B7 — Independent final audit: PASS_SCOPED, NOT N031 CLOSED

**Exact code SHA:** `eb83a1cd62e58929fb9a80e284b4e9f8c3fa1119`. **Unit:** `S3_A4_N031_REALITY_MATTER_DUE_SOURCE`.
**Verdict:** **PASS_SCOPED** for a read-only Matter due-date owner contributor from real canonical `MatterPersistencePort` only. It is NOT three-owner Pending/Attention, NOT Home Pendientes integration, NOT A4 closed and NOT physical certification.

### Verified 7-block chain
- B1–B5 separate preaudit doc `docs/s3-night/S3_A4_N031_REALITY_MATTER_DUE_B1_B5_20261106.md`, blob `5898de601a5d8f1488f32a00697bf1ef269712d7`, docs-only SHA `87b233a6655fffdf2acb159429c681f154e9d53e`; B3 compares actual Tasks.org recurrence/reminders, official Android WorkManager and OWASP privacy guidance and rejects fake owner/counter.
- Code admission was a separate docs-only READY SHA `9b1f9ee237561d7368840a0e3191fa63bc6ad5ba`, protocol guard [42167886602](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42167886602) SUCCESS.
- B6 code commit `eb83a1cd62e58929fb9a80e284b4e9f8c3fa1119` contains `FARO-UNIT:S3_A4_N031_REALITY_MATTER_DUE_SOURCE` and changes exactly:
  1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealityMatterDueReadSource.kt`;
  2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5RealityMatterDueReadSourceTest.kt`;
  3. `.github/workflows/faro-v5-s3-matter-due-directed.yml`.
- Mandatory protocol guard [42168243907](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42168243907) **SUCCESS**, exact SHA.
- Directed app compile [42168244125](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42168244125) **SUCCESS**, exact SHA: `BUILD SUCCESSFUL in 3m 56s` and **`S3_N031_REALITY_MATTER_DUE_XML_PASS 6/6`**, 0 failures/errors/skipped.
- [42168243952](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42168243952) Home notice regressions **SUCCESS**; [42168243894](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42168243894) isolated M5 APK gate **SUCCESS** on code SHA. Separate V2 core run [42168243914](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42168243914) may still be running: no verdict without checking its CI. G15 physical NOT_RUN.

### Owner correctness and trust boundary
- The source reads `MatterPersistencePort.read()`, a real Reality 7 owner, not Search index or UI local data; derives only ID, real status OPEN/IN_PROGRESS/WAITING, and canonical `dueAtEpochMillis`. Only records due by the local civil date **+7 inclusive**, sorted by due time and ID. No title/detail leak, no mutations.
- Host-supplied expected/current generation, current durable pointer + epoch, ACL epoch and read admission are required pre/post. A revoked epoch returns **null**, not zero; cancellation propagates. Tests cover 6 cases: denial/true empty, due/states, timezone boundary, truncation/limits, ABA/revocation, fail-closed/cancellation.
- The B6 tests use **typed owner fixtures**, not physical Android or an end-to-end G5 SQLite generation. Persistent Matter owner and its schema are prior work; we did not repeat their proof or activate a production host.
- `MatterRecord` lacks HIGH priority, snooze and createdAt; none were fabricated. `completeWithinMatterDue` only represents the bounded Matter due subset; `globalAttentionComplete=false`, `hasReminderOwner=false`, `hasMonthlyPlanOwner=false` on every page. No Home Pendientes count is exposed.
- **Known blockers for full N031:** no **persisted Reminder** nor **MonthlyPlan** domain owner in this repository, so they require explicit schema/migration, FBS2 recovery/backup, genuine ACL and host mounting. Reclassifying Auxiliary delivery notice or generic `MatterAttentionProjection` as those owners is prohibited. User-visible N031 count must remain UNKNOWN until all three authoritative owners exist.
- **Known blockers for full N015:** two-source Activity adapter B7 exists, but no real active host grant + UI Activity integration or all-domain history; no false global coverage.
- A4 stage remains **OPEN**. S3 scoped A1/A2 2/5, final integrated 0/5; A3 Guardados +8 candidate operations NOT APPROVED, 172 unchanged; A5 X01–X12 and single G15 campaign NOT_RUN; S2 closed 8/8. V2 productive OFF, Night/Auto OFF, S4 OFF, main/V4.4 unchanged.

**Next:** this unit B7 PASS_SCOPED only; immediately reset permit to `BLOCKED` and release writer epoch 340. Proceed with new B1–B5/READY scoped to the actual Reminder/MonthlyPlan owner persistence with FBS2 and host integration; no new code without fresh review. In the final communication, DO NOT say A4 finished.
