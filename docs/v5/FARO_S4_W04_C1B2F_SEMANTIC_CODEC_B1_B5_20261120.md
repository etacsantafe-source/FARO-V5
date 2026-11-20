# FARO V5 — W04-C1b2F · FBS1v4 Commercial Evidence2 lossless codec/manifest · B1–B5

Fecha 2026-11-20. Baseline código C1b2E `d17dbe344a8714ca30a6f0f4cfbd46e184bb3b92` (CI `44821224309` SUCCESS JUnit 9/9, guard `44821224076` SUCCESS). Unidad futura `S4_W04_C1B2F_SEMANTIC_BACKUP_CODEC_DEV`. **PREAUDIT EXACT6, SIN READY/NO B6** al publicar. C0A, C0B, C1a, C1b1 y C1b2E B7 PASS, NO repetir.

## B1
- Owner Evidence2 `CommercialEvidence.kt` blob `af1031ede77f80a9a16e9ae1a3c758a9d4b8e136`, `EvidenceBackupPort.kt` blob `2643129ab99d2a24eaa3b589b89107f2d6f4b41b`: `stagedTypedBackup.snapshot/restoreOrVerify` válido STAGED, con `CommercialEvidenceStagedTypedBackupSnapshot(schemaVersion=2,rows(record,terms?))`; `terms=null` es ausencia de fila v1, `terms=UNKNOWN` es fila v2 explícita. El port `CommercialEvidenceBackupPort` legado rechaza schema2 todavía.
- `SemanticBackupSetCodec.kt` blob `4c090ca1c1e819fb83eee6ce822c3628edbb868e`: FBS1 wire 1 (legacy), 2 (timed receipts), 3 (bookmarks); `writeCommercial/readCommercial` escriben campos Commercial v1, no terms. Cuando version>=3 el codec asume sección bookmarks presente; v4 debe definir expresamente presence flag o restricción fuerte para no romper backup v4 sin bookmarks.
- `SemanticBackupRestore.kt` blob `3963e1df21fe1a910fd16c3b566f84039bd0b3d1`: `BackupManifestBuilder.commercialSection` calcula fingerprint en dominio `faro-v5-backup-commercial-evidence-v1` sobre records de v1; sin metadata v2 el manifiesto validaría false-positive respecto de pérdida de terms. `SemanticBackupSet` contiene `CommercialEvidenceBackupSnapshot`; `SemanticBackupCaptureCoordinator` captura port legacy y restauración usa `restoreOrVerify`.
- `SemanticBackupRestoreT2Test.kt` blob `842460271bda835ffeec2269232f2f4b6ba62c8b`: fixtures de capture/restore FBS1 históricos y pruebas codec v1/v2/v3; deben seguir pasando exactamente. La estructura exterior `FaroV5FiveStoreSemanticCodec` FBS2 envuelve bytes FBS1; C1b2F NO modifica FBS2 ni grafo todavía.
- C1b2E usa cuatro rutas exactas, PASS propio; lo nuevo es integración de backup semántico FBS1 + huella, todavía sin prueba, no invocar código schema2 en producción.

## B2
V4.4 L11/L15 es referencia de cotización contextual, NO de formato de respaldo. V5 requiere Evidence≠Reality, identidad v1 byte-equivalente, `sourceKind` MANUAL/TOOL/IMPORT sin reinterpretar, `sourceRef` opaco, timestamps originales, moneda sin FX. Reusar C1b2E staging y `SemanticBackupCaptureCoordinator` bajo quiescencia del caller, no instalar root/owner nuevo ni tocar Finance/C0A/C0B/C1a/C1b1.
Invariante: para cualquier backup con schema1, `SemanticBackupSetCodec.encode` debe producir exactamente el mismo wire v1–v3 y manifest v1 ya probado. Para schema2, un respaldo sin terms explícitamente serializados+hasheados es inválido.

## B3
Alternativas (investigación primaria y código existente):
- **USAR/ADAPTAR** framing versionado y límites actuales de FBS1, canonical hash `CanonicalHashBuilder`, firmas `SemanticBackupSetIntegrity.matches`; referencias externas SQLite `https://www.sqlite.org/pragma.html` (integrity_check NO verifica foreign keys, usar ambos), `https://www.sqlite.org/wal.html` (estado WAL) y `https://www.sqlite.org/backup.html` (consistencia; no reemplaza semántica).
- **REFERENCIA** `https://developer.android.com/training/data-storage/room/migrating-db-versions` para pruebas de compatibilidad old-version fixtures, sin reescribir el owner como Room.
- **ELEGIDA CONDICIONAL**: extender `CommercialEvidenceBackupSnapshot` con componente v2 tipado opcional y validación *record identities match*; en schema1 null y bytes históricos intactos, en schema2 obligatorio y sin downgrade. Adaptar el backup port actual SOLO cuando el schema2 STAGED tiene registro+terms íntegros. Añadir FBS1 wire versión4 (solamente snapshot schema2), serialize terms/physical absence, presencia explícita de bookmarks y bounded length; hash comercial en nuevo dominio v2 incluye presence+metadata fingerprint. Manifest formatVersion histórico 1 conservado; wire-version no equivale a manifest-format-version.
- **DESCARTAR**: convertir MANUAL en IMPORT, introducir JSON en `sourceRef`, usar FBS1v3 para schema2, ignorar terms en hash, alterar bytes v1–v3, activar generational owner por hacer pasar codec.
- FBS2 externo y portable export: **FUERA de este permiso**; sus pruebas de integración pertenecen a C1b2G/H y requieren READY propio.

## B4
Contrato previsto: `CommercialEvidenceBackupSnapshot(schemaVersion=1|2, records, typedSnapshot?=null)`, v1 exige null y v2 exige objeto schema2 con `typed.rows.map { record } == records`, orden/idempotency canónico, límites y field validation. Owner `CommercialEvidenceStore.backup` permite v2 sólo en constructor stagedTypedTermsV2 y usa `stagedTypedBackup` como fuente/restore de exactamente el mismo registro y terms bajo gate. Owner legacy v1 no admite v2 ni upgrade automático.
`SemanticBackupSetCodec` conserva el codec legacy para schema1; usa FBS1v4 sólo para schema2 con términos completos y flag de bookmarks si es opcional. `readCommercial` prohíbe schema2 en v1–v3; todos los campos de `CommercialEvidenceTypedTerms` se serializan con presencia y límites. `SemanticBackupRestore` calcula `commercialSection` hash v2 incluyendo `terms-present` y fingerprint de terms por índice, además de cada registro v1 y su identidad. No convertir moneda ni inferir vigencia.
**Exactamente SEIS rutas candidatas congeladas para B6 de C1b2F (aún NO AUTORIZADAS):**
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidenceBackupPort.kt`
2. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
3. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/SemanticBackupSetCodec.kt`
4. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/SemanticBackupRestore.kt`
5. `v5/maintenance/src/test/kotlin/ar/com/faro/v5/maintenance/SemanticBackupRestoreT2Test.kt`
6. `.github/workflows/faro-v5-s4-w04-c1b2f-semantic-codec.yml` (nueva)
No `v5/store/generation`, portabilidad, `main`, apps, Android físico ni Finance. Si aparecen otras rutas necesarias, STOP y nueva preauditoría/permiso, no ampliar silenciosamente.

## B5
T01 golden v1/v2/v3 de FBS1: encode/reencode byte-identical, antiguos manifest fingerprints intactos, y suite existente completa.
T02 snapshot schema2 mixed MANUAL/TOOL/IMPORT, legacy absent vs UNKNOWN explicit, sourceRef literal, fecha nullable; FBS1v4→decode igualdad exacta →restore a target staged fresco →reopen.
T03 SHA fingerprint comercial v2 cubre presencia de fila, locality, issuedAt, validFrom/Until, presentation, pay, eligibility, conditions, promo/amount/source; tampering de terms altera hash y `SemanticBackupSetIntegrity.matches` rechaza.
T04 schema2 por FBS1v1–v3/legacy opener rechazo sin bytes parciales; schema1 por versión4 rechazo/canon definido explícito.
T05 sección bookmarks presente/ausente; receipts temporales heredados, no downgrade de v4, no omitir campos, no confundir versión wire con manifest.
T06 límites bytes/records/string, unknown enum, truncado, trailing bytes y snapshots no canónicos, cero pérdidas silenciosas; overflow de promociones/quantities rechazo. No FX.
T07 `SemanticBackupCaptureCoordinator` conserva owner+gate quiesced, orig idempotency/evidenceId, no segundo root ni Finance/stock. Kill/replay cinco-store final reservado a C1b2G.
T08 CI JVM `:maintenance:test` y `:evidence:test`, XML exacto de tests nuevos, guard protocolo por SHA, revisión B7 independiente. C1b2E 9/9 ya PASS no repetir por rutina salvo regresión de módulo necesaria; V2 productiva OFF, G15 NOT_RUN.
**C1b2F NOT_RUN** antes de B6.

## Puerta
Este documento **NO da permiso**. Guardia documental SUCCESS y `FARO_PROTOCOL_WORK_PERMIT.json` READY exclusivo con las seis rutas/markers B1–B5 antes de cualquier código. B7 sólo con CI/XML reales. C1b global OPEN; C1b2G/H (FBS2/generation/portability) permanecen sin READY.
