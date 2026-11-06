# FARO V5 S3.A4 — FBS2 G4 staged restore fail-closed · corrective B1–B5
2026-11-06. SAME unit ID `S3_A4_N031_FBS2_G4_CODEC_V2` (RED_FIX from SHA `f33dcc24be6c5b164d0a86239ca94263fd542540`).

## B1 — Verified evidence and narrow acceptance
- Codec v2 now decodes optional G4 domains into `FaroV5FiveStoreSemanticSet.g4Domains`, tested 5/5 new and 7/7 FBS2 v1 on exact code SHA `f33dcc24`, runs `42213197401` and protocol `42213197241` SUCCESS; V2 core `42213196905` SUCCESS.
- `FaroV5FiveStoreStagedRestore.kt` contains `restoreAuthenticated`: after authenticated `open()` it ignores `g4Domains` and restores only legacy Reality, Evidence, Auxiliary and Workspace, then returns `StagedVerified` if those match. This can falsely certify a G4-containing archive; B7 `docs/s3-night/S3_A4_N031_FBS2_G4_CODEC_B7_RED_FIX_20261106.md` documents RED_FIX.
- **Acceptance:** valid FAROBAK2 archive with FBS2 v2 and G4 non-null must return a distinct `Blocked(UNSUPPORTED_G4_ARCHIVE)` BEFORE any pinOrVerify, SQLite write, Workspace write, or pointer change. Genuine FBS2 v1 remains restorable under the already scoped legacy staging contract. No new G4 restore until a future gated unit.

## B2 — Compare V4.4 and reuse code
- V4.4 `FaroV4FollowUpGateway.readBlocking()` uses authoritative Matter, Reminder and MonthlyPlan records, so import of user data cannot silently drop a domain. V5 Reality G4 owner snapshot+restore is already a separate internally certified primitive, but NOT integrated into five-store archive. Reuse existing `FaroV5FiveStoreStagedRestore` authenticated decode, quiescence/stage identity prechecks, and `FiveStoreStagedRestoreBlock` result; no new storage, no migration, no host enablement.
- Alternatives: (a) fully implement v2 G4 restore now (REJECT without full capture/generation/ACL preaudit); (b) ignore unknown v2 section (REJECT user data loss); (c) reject v2 before any mutation (ADOPT). Retain v1 behavior unchanged.

## B3 — External research and negative test justification
- Tasks.org backup changelog https://github.com/tasks/tasks/blob/main/CHANGELOG.md cites duplicated task imports and older backup import failures (15.7.1): ADAPT negative no-duplicates, no silent import success; no code/library copied.
- Joplin backup restore https://github.com/JackGruber/joplin-plugin-backup/blob/master/FULLRESTORE.md explicitly warns that restored items may duplicate when import semantics are wrong: ADAPT no false-success semantics; existing FARO's fresh-or-identical contract stays.
- SQLite transactions https://www.sqlite.org/lang_transaction.html: USE refusal before first `BEGIN IMMEDIATE`/write; no partial multi-owner rollback assumption. OWASP MASVS storage https://mas.owasp.org/MASVS/05-MASVS-STORAGE/ : avoid exposing G4 data in logs or exports. Third-party code not copied, so no dependency/licensing or paid API impact.

## B4 — Minimum corrective scope
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt`: append explicit `UNSUPPORTED_G4_ARCHIVE` block reason, and early fail-closed `if (backup.g4Domains != null)` **before** generation identity, archive pin and any `restoreOrVerify`. It must reject even an *empty* but explicitly present G4 section to avoid dropping coverage.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Fbs2G4RestoreFenceTest.kt`: one genuine valid encrypted v2 fixture with nonempty G4, assert rejection and unchanged Reality/Workspace/pointer with no stage or pin side effect.
3. `.github/workflows/faro-v5-s3-fbs2-g4-restore-fence-directed.yml`: directed CI exact XML new 1/1 + existing `FaroV5FiveStoreStagedRestoreT2Test` 7/7 + codec new 5/5. No changes to existing expectations, protocol baseline, product, Android.
- New code commit must contain `FARO-UNIT:S3_A4_N031_FBS2_G4_CODEC_V2`, and scope only those 3 files after docs-only READY.

## B5 — Independent adversarial preaudit
- The previous codec B7 is RED_FIX, not PASS. Next change is the SAME unit and narrowly repairs unsupported-v2 restoration. No unrelated feature additions or modifications to FBS1, FBS2 codec v2 or Crypto.
- A valid encrypted v2 is **not malformed**; use distinct UNSUPPORTED_G4_ARCHIVE, not INVALID_ARCHIVE/unknown 0. No source/target generation or active pointer mutations on rejection; no pinOrVerify executed. v1 must still pass 7/7.
- The actual authenticated G4 restore into Reality remains blocked pending a *separate* B1–B5/READY across quiesced capture, schema/codec AEAD and stage recovery. No certification of source–target identity for G4, physical Android or full N031/A4.
- **B5 VERDICT READY for three-path same-unit RED_FIX only.** Writer CAS, code, exact CI/XML and B7 PASS scoped required. V2 prod/Noche/Auto/S4 OFF, S2 8/8 frozen, A3 Guardados +8 unapproved, G15 NOT_RUN.
