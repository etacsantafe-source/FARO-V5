# FARO V5 — W04-F2C1B0 · Selected ACTIVE typed read → F1 contracts · B7 PASS

2026-11-23 Argentina. Unit `S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`. Branch `work/s3-night-20261102`. Final code SHA `752f7be8d862c152c61440968670c96222b6332c`.

## B7 — PASS_SCOPED_DEV_SELECTED_ACTIVE_PROJECTION_6_6

### Protocol / code provenance
- Unit-specific B1–B5 preaudit `docs/v5/FARO_S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_B1_B5_20261123.md`, blob `2383d3f3ae2c6c1f75f82dd65719d67329781449`, guard `45118332602` SUCCESS. READY exact3 checkpoint `3339f2511a4f97c61962501197d60f78eb1b3d2b` and guard `45118443393` SUCCESS.
- Initial code commit `169a9ddaf5191aae6c434dd4ba80cd982ff5ea7f` (exact3 routes). B7 RED_FIX first compiler `org.junit.jupiter.api.Assertions.assertNotNull` returns Unit in Kotlin; artifact `docs/v5/FARO_S4_W04_F2C1B0_B7_RED_FIX_ASSERT_NOT_NULL_20261123.md`; single-test-file repair commit `a41acb9685a0b881d50ee0e9c46f653a42cdc2ba`.
- Second B7 RED_FIX: exact3 workflow count false oracle on 1-path repair; `docs/v5/FARO_S4_W04_F2C1B0_B7_RED_FIX_WORKFLOW_DELTA_COUNT_20261123.md`, workflow-only correction `9dad3185931124fbce13707bfff72ebb87dd8bb2`.
- Third B7 RED_FIX: six real tests succeeded but Gradle/JUnit XML adds `()` to names; `docs/v5/FARO_S4_W04_F2C1B0_B7_RED_FIX_JUNIT_XML_METHOD_SUFFIX_20261123.md` and final workflow-only correction `752f7be8d862c152c61440968670c96222b6332c`. Existing strict test cardinality/names preserved. No unrelated functional sources modified.

### Final real CI evidence
- Mandatory protocol guard **`45120468792` SUCCESS**, exact final SHA `752f7be8d862c152c61440968670c96222b6332c`.
- Directed JVM run **`45120468801` SUCCESS**, job `137512816657`, JDK21/SDK37. `:app:testDebugUnitTest` and XML strict oracle green; isolated DEV pure adapter only, no Android UI or physical.
- Original CI artifact `14793472947` `w04-f2c1b0-selected-typed-projection-xml` downloaded and parsed independently, ZIP SHA256 `5d58ca66552a84889f75d488c0bdcc0fb6d5a16220669b989dda8bb8d18dfa59`, equals GitHub artifact digest. JUnit XML SHA256 `fe737382a662f8e57098cce58433e56c69667024172cffcde8d1595f69f0c012`.
- **6 unique testcases, 6 PASS, 0 failures, 0 errors, 0 skipped**, exact test methods:
  1. `t01SameGenerationSelectedOpenNeedAndProductBindExactF1Contracts`
  2. `t02NeedProductOrGenerationMismatchAndForeignRecordsNeverProject`
  3. `t03CompletedInvalidQuantityAndRevisionFailClosed`
  4. `t04BeforeAndAfterRevocationOrThrownCallbackDenied`
  5. `t05WatermarkUnknownTermsAndRecordCopyNeverChange`
  6. `t06BadPrincipalEpochOrRowIdentityRefused`
- Ancillary Home notice regression of corrected test SHA `a41acb9685a0b881d50ee0e9c46f653a42cdc2ba`, run `45119417698`, SUCCESS; other earlier red CI explicitly recorded as RED_FIX, never mislabeled PASS.

### Scoped implementation / remaining gates
- New `S4W04SelectedActiveTypedReadProjection` accepts only already-certified F2C0 `SelectedRead` and trusted callbacks for selected generation/read admission, maps one OPEN Household10 row and the same product filtered Evidence2 snapshot to existing `HouseholdNeedsSnapshot` / `CommercialNeedReadFence`, with unchanged original full Evidence2 watermark; refuses stale/revoked, mismatches, invalid row fields, bad identity. Pure memory, no extra opener/store/SQL/Apply or grant minting. The copied snapshot is **NOT** an authorization token.
- Does NOT implement F2C1B1: real Android DEV per-source/selected-object read issuer with trusted app gesture, process sandbox and canonical private root remains OPEN. Nor does it mount D0/F1/D4/E live; W04-F2C1 global, F3 Android E2E and S4 integral OPEN. No V2 productive, paid API/network, transfers, payments, stock, physical G15, Auto/Noche, main or V4.4 changes.
- This B7 is PASS **scoped** to the three originally permitted paths (code/test/workflow). Permit to be consumed to BLOCKED and writer RELEASED epoch1700; final documentary guard and remote HEAD verification required after this commit before final turnover.
