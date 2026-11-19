# FARO V5 — W03-D2B2A sealed STAGED Auxiliary4 Notice receipt ledger B7 PASS
2026-11-19. Unit S4_W03_D2B2A_STAGE4_NOTICE_RECEIPTS_DEV. **B7 PASS_SCOPED_D2B2A_STAGED_DEV_JVM_16_OF_16**. Does NOT certify active schema4, FBS2 backup/restore, Android host or full D2B2/W03. Production V2 OFF.

## B1
AGENTS.md, pointer, consumed D2B1 permit, manual protocol read. D2B1 CAS B7 PASS 8/8 code156d5ac185c0a8aaf1ee3f842bceb84b755697ef, guard44587744774, writer RELEASED1321. Preaudit W03-D2B2A docs/v5/FARO_S4_W03_D2B2A_STAGE4_NOTICE_RECEIPTS_B1_B5_20261119.md blob90d5c14eaf474b380d6a81c08324a9088f6c6b88, commit8f2a28446e5c9a982f43c0b1797c752a310f6ef1, guard44589970857 SUCCESS; READY commit7fb72e413ebf57ccb75bae9a74f33e40bb515731, guard44590281593 SUCCESS, scope exact four files. No old work repeated beyond relevant regression tests.

## B2
Reused AuxiliaryDeliveryStore exact single SQLite connection/lock, MaterialWriteGate and BEGIN IMMEDIATE plus CanonicalHashBuilder; distinct NoticeId, NoticeAttention revision and local SUPPRESSED_POLICY owner. A new independent notice_attention_receipt table and immutable canonical SHA256 request fingerprint are v4 scoped, no reuse of Guardados bookmark_receipt. V4.4 separation between Avisos and Asuntos/Recordatorios preserved; no source mutation, OS delivery or payments.

## B3
Native SQLite user_version and transaction semantics https://www.sqlite.org/pragma.html and https://www.sqlite.org/lang_transaction.html, WAL sealing https://sqlite.org/wal.html; ExistingGenerationFormatReader and StagedSqlitePreflight enforce immutable V2 marker, quiescent staged DB and exact schema. No new third-party library, API, network, migration on ACTIVE, second database or owner.

## B4
Four authorized paths in code SHA **5156d98eb924477293ad054759ea0d2068d5b581**:
1 AuxiliaryDeliveryStore.kt: optional quarantined stage4 opener with continuous stageFence; default schema3 untouched. Validate base v3, health/FK; v3->v4 adds table+index and updates PRAGMA user_version inside one schemaTransaction; v4 schema validated. New mutateReminderNoticeAttentionWithReceipt performs key+hash replay, identity-conflict, expectedRevision CAS Notice and receipt INSERT atomically; returns committed historical revision not current Notice. Existing normal v3 CAS unaffected. Standard v4 backup/restore/portability fail closed: old FBS2 codec cannot silently omit ledger.
2 FaroV5GenerationStoreGraph.kt: explicit openQuarantinedAuxiliaryReceiptV4Stage, V2 marker, actual STAGED, strict read-only v3/v4 tables, no hot SQLite sidecars, full active pointer sequence+ID fence, no switch or promotion. Existing ACTIVE/Finance9 schema3 preflight remains exact.
3 S4W03NoticeReceiptStage4Test.kt: six new integration adversarial tests with sealed V2 stages, SQLite.
4 .github/workflows/faro-v5-s4-w03-d2b2a-stage4-receipts.yml exact route preflight, 16 directed JVM tests and XML artifact.

## B5
Verifies: v3 owners Notice+Bookmark rows preserved; v4 fingerprint duplicate replay after reopen no new Notice revision; different payload same request key yields IdentityConflict; SQL receipt insert trigger fails and full Notice update rolls back; DENIED/Stale/Terminal/NotFound do not write receipts; active generation and pointer sequence change rejected before migration; corrupt/future/missing schema4 table fails read-only preflight; v3 default schema unchanged. Stage4 FBS2 backup and portability blocked until receipt-inclusive codec and restore. No physical tests, UI or Android OS; no work on W01/W02/W03 earlier units.

## B6
Directed workflow run **44591846981 SUCCESS**, code SHA 5156d98eb924477293ad054759ea0d2068d5b581, job **135799630277 SUCCESS**. Exact four-path preflight PASS against READY parent 7fb72e413ebf57ccb75bae9a74f33e40bb515731; Gradle :store:auxiliary:test targeted D2B1 old CAS **5/5 PASS**; :store:generation:test targeted S4W03NoticeReceiptStage4Test **6/6 PASS**, FaroV5AuxiliaryV3PreflightTest **5/5 PASS**. JUnit XML verifier **16 tests, 0 failures, 0 errors, 0 skipped**, log marker W03_D2B2A_STAGED_NOTICE_RECEIPT4_XML_PASS 16/16. Artifact w03-d2b2a-staged-receipt-v4-xml ID **14562778382**, sha256:1787fcc8021bacb25a945de8d4263c03060d00bac7b1e6b99fdd3ce690db08d9, not expired. Mandatory guard 44591846342 SUCCESS on exact SHA. Existing S3/old workflows triggered by Auxiliary source change do not overwrite scope of directed CI; global CI not asserted.

## B7
**PASS_SCOPED_W03_D2B2A_STAGED_DEV_JVM_16_OF_16**, NOT full D2B2 or W03. READY permit consumed to BLOCKED; continuity next W03-D2B2B: receipt-inclusive Auxiliary4 semantic backup/restore/portability FBS2 versioned codec, complete staged cutover with exact ACTIVE preflight and normal host binding, adversarial rollback/pointer/ABA, CI/JUnit/Android DEV. NO migration of currently ACTIVE Auxiliary3 until audited new generation. No G15 physical or OS notification. Final B7 doc guard must be SUCCESS, writer RELEASED epoch1325 and exact HEAD verified before closure.
