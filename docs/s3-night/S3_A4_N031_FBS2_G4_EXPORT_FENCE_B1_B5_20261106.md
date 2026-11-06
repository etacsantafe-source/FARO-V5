# FARO V5 — S3.A4 G4 FBS2 external export fail-closed (same-unit RED_FIX B1–B5)
Date 2026-11-06. Unit `S3_A4_N031_FBS2_G4_CAPTURE`. Prior code `7f9685450b1c4086e89eccda3f41c81a1b6a62cb`, B7 RED_FIX `docs/s3-night/S3_A4_N031_FBS2_G4_CAPTURE_B7_RED_FIX_20261106.md`.

## B1 — Evidence and acceptance
- 6/6 exact SHA workflows previously green, including paired capture 5/5, G4 codec 5/5 and FBS2 legacy 7/7. `FaroV5FiveStoreBackupCapture` correctly emits v2 when G4 nonempty and v1 for genuinely empty G4.
- `FaroV5FiveStoreStagedRestore.restoreAuthenticated` explicitly blocks FBS2 v2 `g4Domains != null` before pin/write. In contrast `FaroV5FiveStoreExportPreparation.prepare()` currently calls capture and encrypts/stages **regardless of g4Domains**, with explicit user consent and activeAdmission. This yields an export that the current supported restore will refuse. Standalone internal semantic snapshot still allowed; external export not.
- Acceptance: `prepare()` must check non-null G4 section **before** encrypting/staging; valid consent/active authority with committed G4 returns an exception, no archive bytes staged; if G4 genuinely empty then existing v1 export fixture remains PASS. No V2 production, no SAF picker change.

## B2 — Compare V4.4 and existing FARO V5
- V4.4 follow-up actually persists Matter+Reminder+MonthlyPlan domain data. Exporting a backup without a matching restore path is worse than failing closed; user must not be told a nonrestorable archive is a successful backup.
- Reuse existing `FaroV5FiveStoreExportPreparation` consent+authority gates, and the real typed `FaroV5FiveStoreSemanticSet.g4Domains` marker. No new owner, cache, operation ID, database, UI or extra encryption/dependency.
- Alternatives: (a) pretend G4 absent and export v1 (REJECT silent loss); (b) export unsupported FBS2 v2 (REJECT invalid backup promise); (c) block export with G4 until authenticated staged restore is certified (ADOPT); (d) implement full restore within this RED_FIX (REJECT scope escalation).

## B3 — External investigations, classified
- Android official Auto Backup https://developer.android.com/identity/data/autobackup : REFERENCE for backup scope and protection; never confuse creation of bytes with guaranteed restore. 
- Joplin restore https://github.com/JackGruber/joplin-plugin-backup/blob/master/FULLRESTORE.md : ADAPT preservation of identities and ability to restore exact user records, reject creating misleading incomplete imports/exports. No source copied.
- SQLite https://www.sqlite.org/backup.html: REFERENCE snapshot coherence, file copy caveats, and paired restore requirement. This unit does not copy database files or alter SQLite.
- Existing Crypto `FaroV5FiveStorePortableBackupCrypto` AEAD authenticates plaintext but does NOT guarantee a valid future restoration route: USE explicit feature fence. No new APIs, licences, cost, cloud, passwords in logs.

## B4 — Exact corrective implementation, 3 paths
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreExportPreparation.kt`: after real snapshot capture and before first encryption/staging, add fail-closed `check(snapshot.g4Domains == null)` with clear internal error class; do not downgrade v2 to v1. Keep authority recheck and staging intact.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreExportPreparationT2Test.kt`: add one test with consent true, activeAdmission=true, an actual Matter row, assert no FBS2 export artifact and no backup corruption; class goes 3→4 tests, no personal data.
3. `.github/workflows/faro-v5-s3-fbs2-g4-export-fence-directed.yml`: CI exact XML `FaroV5FiveStoreExportPreparationT2Test 4/4`, `FaroV5FiveStoreBackupCaptureT2Test 5/5` and `FaroV5FiveStoreStagedRestoreT2Test 7/7` to ensure v1 paths stay working.

## B5 — Adversarial preaudit
- A valid G4 archive must remain internally encodable/decodable for testing the future staged restore; only **consented external export preparation** is blocked. No builder fallback to FBS2 v1, false receipt, partial artifact, or accidental G4 leak.
- Host activeAdmission=true in JVM fixture is deliberately stronger than current V2 prod OFF, proving the check works even if authority were granted. Consent is not sufficient for unsupported restore.
- Check fail occurs after capture but before `seal`, `stageBytes`, `activeAdmission` second check and external SAF; test no staging directory artifacts. Legacy export (G4 null) stays unaffected and receipt policy unchanged.
- Exactly same unit ID `S3_A4_N031_FBS2_G4_CAPTURE` as previous RED_FIX, no mixed protocol controls + code commit, no Android physical, no S2, V4.4, S4, Noche/Auto, catalog 172 modifications.
- **B5 verdict READY** for only these 3 paths after separate docs-only permit, exact CI and B7 final.
