# FARO V5 F2C1B1B1B1A — B7 RED_FIX1, Kotlin AndroidTest internal migration

Date 2026-11-23. Unit S4_W04_F2C1B1B1B1A_ANDROID_REAL_ACTIVE_OWNER_SELECTED_DEV; code 4d85703e15628735c365e222195fc7410aff7405. NOT B7 PASS.

## B7 — Real failure

Guard 45212623900 SUCCESS; Android API35 CI 45212624076 job 137825194547 FAILURE in :app:compileW04CommercialDevAndroidTestKotlin, BEFORE ANY TEST/ANDROID XML. Kotlin INVISIBLE_REFERENCE: class FaroV5CommercialEvidence2StagedUpgrade is internal in generation module; AndroidTest in app module cannot access it (test line115). CI compiled app buildType successfully; test source alone failed. No app/runtime failures asserted.

## RED_FIX exact one ready path

Only v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialOwnerBackedPositiveAndroidTest.kt. The prohibited call is a test-fixture-only upgrade used to produce schema2 from existing copied Evidence1 database. Its internal source implementation uses public CommercialEvidenceStore(target, stagedTypedTermsV2=true) to transact the very migration. Replace internal FaroV5CommercialEvidence2StagedUpgrade.upgrade(...) with that existing public Evidence store API ONLY for isolated test seed, verifying schemaVersion 2 and no invented terms before inserting typed observations. Keep source and target files on the same private staged root; no pointer publication until schema and rows ready; B1B1B1B0 real graph validates full manifest and stores at mount.

Do NOT widen internal generation visibility, alter B0/JVM certified source, expose migration to production, mock source, or skip Android API35. No changes to host or workflow. Retain READY exact three routes and preflight one changed route. Rerun identical AndroidTest class + original XML three cases. If red, record actual next failure, NO PASS.

## Pending

F2C1B1B1B1A B6 RED_FIX, B7 PENDING; full F2C1B1B1B1 includes further F1 D4 E explicit observation UI, and F3 global OPEN. Writer RELEASED after doc, V2 productive OFF.
