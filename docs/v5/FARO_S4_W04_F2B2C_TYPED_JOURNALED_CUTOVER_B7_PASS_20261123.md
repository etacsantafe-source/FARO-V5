# FARO V5 · S4 W04 F2B2C — B7 PASS scoped typed DEV journaled cutover
Date: 2026-11-23. Unit `S4_W04_F2B2C_TYPED_JOURNALED_CUTOVER_DEV`, branch `work/s3-night-20261102`. **B7 PASS only for DEV isolated**; V2 productiva OFF. No Android/Apply/payments, no main or V4.4 mutation.

## B7 — PASS_SCOPED_TYPED_EVIDENCE2_JOURNALED_DEV_6_6
* Baseline B7 PASS F2B2B02 STAGED 6/6 checkpoint `5211bbf6b2f3f24af797798fed48802b3cf32fe5`; no repeats.
* Original F2B2C B1–B5 `docs/v5/FARO_S4_W04_F2B2C_TYPED_CUTOVER_RECOVERY_B1_B5_20261123.md`; initial code `b48c83c52028a72d5347a38590633b8f916e2723`. First CI `45103190625` RED 3/6. Follow-up diagnostics `45103981540`, `45104913005`, `45106889384` all RED 3/6, *not* misclassified as PASS.
* Last diagnostic CI `45106889384` artifact `14787239277` gave precise root: `reality.db-wal`, ordinary non-symlink file, size 0 bytes, created by the **post-CAS live ACTIVE Reality10 health read**, and rejected by strict `NOFOLLOW_LINKS` check after the health graph closed. This failure correctly caused rollback before the replay fence. RED_FIX4 B1–B5 `docs/v5/FARO_S4_W04_F2B2C_REDFIX4_SEALED_TYPED_HEALTH_B1_B5_20261123.md`; guard `45107515643` SUCCESS. READY exact2 `c3ae09500db497a23464831d4add2c7f8bccd2f7`, guard `45107629698` SUCCESS.
* Final functional commit **`eff8b142e4cf31d3100e220b2ce793e3ab69a822`**: `FaroV5GenerationStoreGraph.verifyReopenedHousehold10Evidence2Dev` performs strictly **sealed immutable typed FBS2v7 maintenance health** under existing closed-host, exact selected pointer, canonical descriptor and caller's material write quiescence. Reuses tested legacy Household10 sealed health profile and typed STAGED archival schema2 reader; still demands `requireSealedHousehold10Evidence2ArchiveStage` before and after and verifies the same full pointer and all stores. The **normal typed ACTIVE host session remains unchanged**. Restored original six cutover tests, removing diagnostic injections. Scope only `FaroV5GenerationStoreGraph.kt` and `FaroV5Household10Evidence2CutoverRuntimeTest.kt`; no new durable coordinator or SQL schema.
* Mandatory protocol guard **`45107740637` SUCCESS** on exact SHA. Directed GitHub Actions **`45107740571` COMPLETED SUCCESS**, job `137472794895`; JDK21 SDK37; exact READY preflight SUCCESS; Gradle `BUILD SUCCESSFUL` in 2m59s; JUnit verifier marker `FARO_W04_F2B2C_TYPED_JOURNALED_CUTOVER_XML_PASS_6_6`.
* Independently downloaded GitHub Actions JUnit XML artifact **`14787744886`**, name `w04-f2b2c-typed-journal-cutover-xml`. ZIP SHA256 `393f39483d030d445ed44f4f57706f11f6101aead9c6947b22539438282b4d0a` exactly equals uploader; internal XML SHA256 `a0395347c229d32a3d34f20ce77542dacd1d53bf7bca4db3bd04995a19ab6155`. XML parsed independently: **6 unique directed tests; 6 passed, 0 failures, 0 errors, 0 skipped**. Cases:
  - t01ActivatesTypedAndReopensExistingReadOnlyHostAfterRestart
  - t02CrashAfterPointerPublishRecoversWithoutSecondCas
  - t03CrashAfterFenceRecoversAndCorruptionCannotRollBack
  - t04BadTargetHealthRollsBackBeforeFenceAndPreservesOrigin
  - t05RefusesRevokedForegroundWrongSecretTamperAndOldEnvelopeBeforeJournal
  - t06ExclusiveRootLeaseTicketProfileAndTargetMutationFailClosed

## B7 scope, exclusions and next
* Verified for **closed, isolated JVM/SQLite DEV root**: authenticated FBS2v7 selected Household10/Evidence2 target, journal exact origin/target, foreground confirmation, CAS + restart recovery, checkpoint replay fence and rollback before fence, post-fence corruption BLOCK, wrong password/tamper/v6 rejection, no pointer ABA or unsafe lease sharing.
* The standard V2 production host is **NOT activated**; this B7 does NOT certify Android UI, real G15, shipping backup/export, global S4 acceptance, user data migration, background automation, Apply, or any purchases.
* Consume READY, mark B6 DONE_VERIFIED and B7 PASS_SCOPED_TYPED_EVIDENCE2_JOURNALED_DEV_6_6, preserve all prior B7 history including true REDs. Follow-up **F2C Android DEV integration** needs separate B1–B5, READY/guard, B6 and Android CI/physical acceptance where appropriate. S4 integral acceptance remains pending.
