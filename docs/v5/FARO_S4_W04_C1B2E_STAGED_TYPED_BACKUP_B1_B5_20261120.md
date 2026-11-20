# FARO V5 — W04-C1b2E · Respaldo/restore tipado de Evidence2 STAGED, B1–B5

Fecha local: 2026-11-20. HEAD de referencia: `b58f6508079b2a0fe7cbe18ea73d349b4b43819e`. Unidad exacta: `S4_W04_C1B2E_STAGED_TYPED_BACKUP_DEV`. **Esta preauditoría es solo documentación: NO READY ni B6.** No repetir C0A 12/12, C0B 9/9, C1a 9/9 ni C1b1 9/9 B7 PASS. C1b1 código `0e3664bb47f0e0bc81781668a4590e29ef0dd5dd`, CI `44813495451`, guard `44813495275`; último checkpoint `b58f6508079b2a0fe7cbe18ea73d349b4b43819e`, guard `44814349864` SUCCESS, permiso consumido BLOCKED y writer RELEASED epoch 1450 antes de este delta.

## B1
- Código `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`: store schema1 histórico y schema2 STAGED explicit opt-in, `commercial_evidence_quote_terms` 1:1 por evidenceId y FK, `recordWithTypedTerms` transacción IMMEDIATE, `readTypedTerms` valida fingerprint de metadata, `readAllRecords` mantiene orden por evidenceId. `readBackupSnapshot` y `restoreOrVerifyBackup` FAIL-CLOSED para schema2, igual que `readPortabilitySnapshot` y `readSemanticMigrationSnapshot`: no alterar esos cierres en esta unidad.
- `EvidenceBackupPort.kt` blob `23859d874c6203edf8cebdf078a5771315bdc5b0`: `CommercialEvidenceBackupSnapshot(schemaVersion,records)` exclusivo v1; port backup v1 no tiene terms. `CommercialEvidenceTypedTerms.kt` blob `f5078d1ebb9a6541fa673a9b4dc3d4903fdef2c1`: tipos canónicos, `UNKNOWN`, fingerprint exacto de metadata.
- El FBS1 `SemanticBackupSetCodec.kt`, manifest hash `SemanticBackupRestore.kt`, FBS2, portable export JSON y graph opener continúan **solo versión1**; recibir un `CommercialEvidenceBackupSnapshot` v2 actual perdería terms, por eso **NO desbloquear legacy backup ni export** en este delta.
- Tests C1b1 `CommercialEvidenceTypedTermsTest.kt` 9/9 ya PASS pero no incluyen restauración completa schema2. Necesidad nueva: snapshot/restauración tipada reversible, con evidencia v1 intacta y separación entre `terms absent` (fila legacy migrada) y `terms UNKNOWN explicit` (fila v2 tipada). Pruebas C1b2E aún NOT_RUN.

## B2
- V4.4 SmartCompra L11/L15 como referencia de oferta contextual; Evidence v1 mantiene los bytes de los identificadores (evidenceId, idempotencyKey, fingerprint), `sourceKind` MANUAL/TOOL/IMPORT y timestamps independientes: **prohibido reimportar MANUAL como IMPORT**.
- Reutilizar `CommercialEvidenceStore`, SQLite WAL FULL, `BEGIN IMMEDIATE`, `materialWriteGate`, inspección PRAGMA integrity_check/foreign_key_check y dest fresh o exact match; no cambiar cinco owners, formato FBS1/FBS2, catalog, Finanzas, C0A/C0B/C1a/C1b1 productivo.
- Semántica: `terms == null` en snapshot = fila v1 legacy sin metadata física (proyección de lectura UNKNOWN); `terms == UNKNOWN` explícito = registro v2 cuya metadata existe. Roundtrip debe distinguirlos sin inventar vigencia/localidad.

## B3
Revisión de soluciones externas primarias (referencias técnicas, no incorporar librerías):
- SQLite schema migration + integridad: https://www.sqlite.org/lang_altertable.html, https://www.sqlite.org/pragma.html, https://www.sqlite.org/wal.html — **USAR/ADAPTAR** transacción, integridad y análisis de consistencia; restaurar en único BEGIN/COMMIT.
- SQLite backup API https://www.sqlite.org/backup.html — **REFERENCIA** para backup consistente de DB, no reemplaza backup semántico serializado por FARO.
- Android Room migration tests https://developer.android.com/training/data-storage/room/migrating-db-versions — **ADAPTAR** patrón de test v1→v2, sin convertir driver a Room. El port separado conserva la estructura existente y no agrega dependencia.
Alternativa A **ELEGIDA**: `CommercialEvidenceStagedTypedBackupPort` solo en owner Evidence2 opt-in, con snapshot `schemaVersion=2` de filas canónicas `(originalRecord, terms?)`. Restore-or-verify en destino staged fresco o EXACTAMENTE idéntico; transacción única base+terms por fila, verificación post-commit, no altera interfaz legacy ni se integra al FBS1 prematuramente.
Alternativa B **DESCARTADA EN ESTE DELTA**: agregar terms al `CommercialEvidenceBackupSnapshot` global sin actualizar codec, hash e integridad FBS1/FBS2; abriría pérdida silenciosa.
Alternativa C **DESCARTADA**: guardar terms en sourceRef JSON, segunda SQLite, importFresh que altera identidades, exportar snapshot sin terms, permitir active schema2 o pagos.

## B4
Contrato de C1b2E (stage-only):
1. Modelo `CommercialEvidenceStagedTypedBackupRow(record, terms: CommercialEvidenceTypedTerms?)`, `effectiveTerms` UNKNOWN cuando null, preserva ausencia/explicitud física. `CommercialEvidenceStagedTypedBackupSnapshot(schemaVersion=2, rows)`: orden estricto por id, límites max100000, IDs/idempotency únicos, validación normal, sin otra autoridad.
2. Port `CommercialEvidenceStagedTypedBackupPort` accesible en `CommercialEvidenceStore.typedBackupV2`, no en grafo generacional ni host: `snapshot()` y `restoreOrVerify(snapshot)` requieren flag stagedTypedTermsV2, schema exactamente2 y salud DB; rechazan versión1, target no vacío salvo igualdad exacta; si desiguales FAIL-CLOSED (sin merge). Integridad/post-verificación; no mutación en snapshot.
3. Restore usa `insert(record)` y, solo para metadata físicamente presente, `insertTypedTerms(record.evidenceId,terms)` en UN solo BEGIN IMMEDIATE/COMMIT. ROLLBACK ante fallo. Conserva sourceKind y todas las identidades, tiempos y sourceRef literales, sin pagos/stock/FX.
4. Puertos antiguos `backup`, `portability`, `semanticMigration` siguen rechazando schema2, hasta nuevos permisos independientes para FBS1v4/FBS2/portability. ACTIVE opener sigue requiriendo schema1.
5. No exponer nuevas capacidades N-034, no modificar app/android ni generación, writer CAS global, código sólo en rama de desarrollo.

**Rutas exactas congeladas: CUATRO, no globs**
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
2. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidenceBackupPort.kt`
3. `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceStagedTypedBackupTest.kt` (nueva)
4. `.github/workflows/faro-v5-s4-w04-c1b2e-staged-backup.yml` (nueva)
No otros archivos, no Finance, no app, no generación. Cambio fuera del freeze => STOP, nueva auditoría/permiso. FBS1/FBS2, manifest hash y portabilidad serán C1b2 siguientes por permisos diferentes.

## B5
- T01 migrar v1 con registros MANUAL y TOOL, Snapshot Evidence2 los presenta sin fila de terms, UNKNOWN solo en proyección; metadata explícita full y UNKNOWN siguen distinguibles. Identidades y timestamps intactos.
- T02 snapshot de tres tipos, restore a destino staged fresco, reopen y equality exacta, sin duplicados; PRAGMA integrity y foreign keys PASS.
- T03 restaurar de nuevo mismo snapshot devuelve igualdad; restaurar diferente sobre no vacío rechaza y conserva before; restaurar en destino no vacío NO merge.
- T04 snapshot v2 al owner v1, entrada schema !=2 y ord/IDs duplicados: rechazo. Old backup/restore, portability y semanticMigration de v2 siguen FAIL-CLOSED.
- T05 corrupción de terms_fingerprint detectada sin producir snapshot; fuente hostil preservada literal, moneda ARS/USD sin FX; ausencia de fecha no implica vigencia.
- T06 preflight 4 rutas exactas, `:evidence:test` JVM y JUnit XML conteos reales, guard B7 y regresión histórica implícita por suite existente. No pruebas físicas, no UI, no V2 productiva.
- T07 fault injection de rollback/kill si no está disponible en este scope: no afirmar PASS; cubrir rollback ante constraints, y dejar prueba de interrupción real para cutover integral de cinco stores.
**Todas C1b2E NOT_RUN antes de B6.**

## Gate
Estado al publicar: `B1_B5_DOC_ONLY`, requiere guardia de este SHA SUCCESS y aprobación READY individual de estas cuatro rutas en commit SEPARADO. Sólo entonces B6; CI real/XML y B7 propio. C1b global OPEN, C1b2E no cierra FBS1/FBS2, export portable ni graph.
