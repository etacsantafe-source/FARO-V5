# FARO V5 — S4 W04-F2C0 · DEV typed selected ACTIVE read port B7 PASS
2026-11-23 Argentina. Unit `S4_W04_F2C0_DEV_TYPED_SELECTED_READ_PORT`. Code commit `ff881ffc41e5a2ca719589a49536cb24792ec694` on `work/s3-night-20261102`.

## B7 — PASS_SCOPED_DEV_SELECTED_READ_6_6
* Preaudit `docs/v5/FARO_S4_W04_F2C0_DEV_TYPED_SELECTED_READ_PORT_B1_B5_20261123.md` B1–B5 and mandatory guard `45110132217` SUCCESS. Exact-three-path READY checkpoint `f4f0166f91122b76b13564f5c4dd1b7a4cf1f625`, protocol guard `45110261218` SUCCESS.
* B6 published **3 new scoped files only**: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPort.kt`, `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPortTest.kt`, `.github/workflows/faro-v5-s4-w04-f2c0-dev-selected-read-port.yml`. No certified F2B1 or F2B2C code modified.
* Required protocol guard **`45110499796` SUCCESS**, exact SHA `ff881ffc41e5a2ca719589a49536cb24792ec694`. Directed GitHub Actions **`45110499993` SUCCESS**, JVM job **`137481517316`** with JDK21, SDK37 configuration, real SQLite, Gradle `BUILD SUCCESSFUL in 3m37s`, original JUnit verifier `FARO_W04_F2C0_TYPED_SELECTED_ACTIVE_READ_XML_PASS_6_6`.
* Independently downloaded original JUnit XML artifact `14789426535`, `w04-f2c0-typed-selected-read-xml`; ZIP SHA256 **`ab1cfa68b61e68743419eb3e77423f9570fde9cc5b2b28552516c16cc4e6622c`**, exactly matches GitHub upload log; XML SHA256 **`888517467affcf8c317f0b71df4af24e77b3534de576226e2d66752aa85fa052`**. Parsed independently: **6 tests, 6 PASS, 0 failures, 0 errors, 0 skipped**. Distinct:
  1. `t01ExactSelectedHouseholdAndOnlyTypedProductUnderOneLease`
  2. `t02IndependentGrantsAndExplicitChoiceMustBothBePresent`
  3. `t03MidReadRevokedGrantAndEpochFailClosed`
  4. `t04PointerAbaWithSameIdNeverRestoresAuthority`
  5. `t05ExclusiveRootLeaseReleaseAndClosedPortNeverReuse`
  6. `t06WrongSchemaStalePointerAndTamperedTermsNeverExposeSource`

## B7 — Scope/security and mandatory continuation
* One public **DEV-only-named read wrapper**, not a production data graph, wraps the previously certified internal F2B1 `Household10DevReadSession`, sharing its exact selected pointer ID+sequence, one OS root lock and material write gate. Returns exactly one selected OPEN Household10 need and only typed Evidence2 observations for the explicitly selected product; preserves original full-source watermark, never silently enriches null or UNKNOWN typed terms. No writable owners or stage fallback.
* Independent Household and Commercial callbacks, explicit user selection, foreground/owner/host epoch and full pointer are verified before and after reads. Loss denies; close revokes and releases lease. F2B1/B2C historical 6/6 PASS unaffected.
* This is **F2C0 only** (source boundary). F2C1 **Android DEV foreground UI mount** is NOT IMPLEMENTED/NOT TESTED, and F2C2/F3 global Android API35 and physical Moto G15 acceptance remain separate. Do NOT mark W04-F or S4 integral closed.
* V2 productiva OFF, no Apply/pagos/stock, Internet, automation, secrets, Android hardware, shipping CompositionRoot or main/V4.4 changes. READY consumed; next F2C1 requires own B1-B5, guard and permission for its exact Android routes. Writer must RELEASED with matching final pointer/epoch.
