# FARO V5 — W04-C1b2E · respaldo tipado Evidence2 STAGED · B7 PASS

Fecha local 2026-11-20. Unidad `S4_W04_C1B2E_STAGED_TYPED_BACKUP_DEV`. Código B6 exacto `d17dbe344a8714ca30a6f0f4cfbd46e184bb3b92`, parent READY `8b8d37cbcf99c191b01c6714ce49223ecf493e69`, B1–B5 doc `FARO_S4_W04_C1B2E_STAGED_TYPED_BACKUP_B1_B5_20261120.md` blob `421478fe93ed70541d7a5d84f5c5e60c77227bcc`.

## B7
**PASS_SCOPED_STAGED_DEV**. Protocolo guard run `44821224076` COMPLETED SUCCESS mismo SHA de código. CI dirigida `44821224309` COMPLETED SUCCESS, job `136582290204`; cuatro preflights del parent READY PASS; `./gradlew :evidence:test` BUILD SUCCESSFUL; salida JUnit XML: `W04_C1B2E_TYPED_BACKUP_JVM_XML_PASS 9/9`, 9 tests, 0 errors, 0 failures, 0 skips. Artifact `14671679327`, `w04-c1b2e-staged-typed-backup-xml`, tamaño 7569 bytes y no expirado al verificar.

Rutas de código/test/CI exactas (cuatro):
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidenceBackupPort.kt`
- `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceStagedTypedBackupTest.kt`
- `.github/workflows/faro-v5-s4-w04-c1b2e-staged-backup.yml`.

Cubre snapshot schema2 opt-in STAGED, restore íntegro con una sola transacción y reopen, constancia literal de ID/fingerprint/idempotency/sourceKind/sourceRef/observedAt/acquiredAt, distinción de terms físicamente ausentes vs UNKNOWN explícito, target fresh/exact, replay idempotente sin duplicar, rechazo de mismatch y snapshot inválido, detección de fingerprint de términos corrupto, protección de v1/portability/semanticMigration legacy y regresión JVM Evidence existente.

Límites: **no** se implementó codec FBS1v4/FBS2, manifest hash que incluya términos, portabilidad v2, export JSON, generación activa ni UI/Android G15; tampoco prueba de kill/crash real de cinco stores. Es un B7 de owner Evidence2 STAGED únicamente, no prueba de restore integral activo. Los puertos legacy siguen FAIL-CLOSED ante schema2 para impedir pérdida silenciosa. W4-C1 global permanece OPEN. C0A/C0B/C1a/C1b1 permanecen PASS, NO repetir.

Permiso C1b2E **consumir BLOCKED** con resultado B7 PASS y doc exacto; nueva unidad C1b2F debe pasar preauditoría/guard/READY antes de código.
