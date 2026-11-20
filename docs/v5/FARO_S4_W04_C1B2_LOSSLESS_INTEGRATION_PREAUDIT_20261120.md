# FARO V5 — W04-C1b2 · backup/restore y portabilidad Evidence2 + generación · B1–B5 preauditoría de integración
Fecha 2026-11-20. Baseline de código verificado C1b1 `0e3664bb47f0e0bc81781668a4590e29ef0dd5dd` (CI 44813495451 SUCCESS, XML 9/9, guard 44813495275 SUCCESS). Unidad futura `S4_W04_C1B2_EVIDENCE2_LOSSLESS_INTEGRATION_DEV`. **DOC_ONLY; PREAUDIT_NO_READY / NO B6 / NO ACTIVACIÓN**. No usar permiso C1b1 consumido ni repetir C0A/C0B/C1a.

## B1
- `CommercialEvidence.kt` v2 staged tiene `commercial_evidence_quote_terms` y `recordWithTypedTerms` atómico, pero `readBackupSnapshot`, `restoreOrVerifyBackup`, `readSemanticMigrationSnapshot` y `readPortabilitySnapshot` paran ante schema2: defensa explícita, NO funcionalidad pendiente simulada.
- `EvidenceBackupPort.kt` blob `23859d874c6203edf8cebdf078a5771315bdc5b0`: `CommercialEvidenceBackupSnapshot(schemaVersion, records)` sin terms; `EvidencePortabilityPort.kt` blob `104522e7605f3a2924fcf7ccbcf8f40a04a08399`: `PortableCommercialObservation` sólo v1 y watermark domain `faro-portability-commercial-v1`; `CommercialEvidenceMaintenancePort.kt` blob `06b2e5f912fd274e80dd0acb2e923f78c3a09d83`: semanticMigration `IMPORT` con identidad fabricada, NO válido para MANUAL/TOOL existentes.
- `SemanticBackupSetCodec.kt` blob `4c090ca1c1e819fb83eee6ce822c3628edbb868e`: FBS1 v1–v3 y `writeCommercial/readCommercial` no codifican terms. `SemanticBackupRestore.kt` blob `3963e1df21fe1a910fd16c3b566f84039bd0b3d1`: `BackupManifestBuilder.commercialSection` usa fingerprint `faro-v5-backup-commercial-evidence-v1` sin terms. El códec v2 debe incluir metadata en la huella o permitiría corrupción silenciosa.
- `PortableBundleJsonWriter.kt` blob `4fe914e0792325019fffa62a071e0a885a7a7062`: export JSON commercial únicamente v1; `PortabilityCore.kt` blob `3d62c2f6f2bb849d642a176444436a2874db9ba7` obtiene snapshot del puerto; nueva proyección v2 debe mantener consistencia con preview/fingerprint y serialización, no basta cambiar el data class.
- `FaroV5GenerationStoreGraph.kt` preflight `COMMERCIAL_EVIDENCE=1` en `requireStrictV2StoreSchemas`, opener comercial `CommercialEvidenceStore(...)` legacy; `FaroV5FiveStoreBackupCapture.kt` blob `5c6dd7f2ea2653170015ecb1a49770cdb9e6491f` llama `commercialEvidence.backup` bajo quiescencia; `FaroV5FiveStoreSemanticCodec.kt` blob `2624bbe4c89a5a8a3c9276223190e1d1face8328` envuelve FBS1 en FBS2. Sin end-to-end, schema2 no se admite en ACTIVE.
- Pruebas v2 backup/FBS2/portability: NO EJECUTADAS. Las 9/9 de C1b1 prueban exclusivamente el staged schema/typed y FAIL-CLOSED.

## B2
V4.4 SmartCompra L11/L15 podía mostrar contexto de cotización; V5 separa Evidence observacional de Reality y Finance. El backup restaurable de V5 exige identidad estable de los registros MANUAL/TOOL/IMPORT sin cambiarlos a IMPORT por heurística, epochs de adquisición/observación distintas, y sin fabricar validez/localidad. Reutilizar puertos existentes y cinco stores en un solo graph y quiescencia global; no introducir segundo root ni store ni tocar W1 Finanzas. Mantener bytes de backups FBS1 v1–v3/vía FBS2 sin alteraciones al volver a serializar un snapshot v1.

## B3
Investigación externa relevante:
- ADAPTAR: SQLite atomic schema migrations `https://www.sqlite.org/lang_altertable.html`, PRAGMA integrity/foreign key `https://www.sqlite.org/pragma.html` y WAL `https://www.sqlite.org/wal.html`: transacción con rollback y lectura de versiones.
- REFERENCIA: SQLite Online Backup API `https://www.sqlite.org/backup.html` para snapshot consistente; no sustituye backup semántico FBS1/FBS2 ni identity/receipt.
- ADAPTAR: Android Room migration tests `https://developer.android.com/training/data-storage/room/migrating-db-versions`: fixtures de versiones antiguas y reapertura; sin rehacer los stores de FARO en Room.
- ELEGIDA CONDICIONAL: agregar lista tipada y canónica de terms al snapshot Evidence2; nuevo FBS1 wire v4 *sólo cuando commercial schema2*, manifiesto con hash domain v2, mantener encoder antiguo intacto para schema1; portabilidad versionada con watermark v2 y serializer JSON explícito, opener DEV STAGED version2 separado con preflight y gates estrictos. VERIFICAR que FBS2 versión vigente pueda envolver FBS1 v4 sin tocar outer wire; si requiere versión de FBS2, gate y pruebas propias.
- DESCARTAR: adjuntar JSON al sourceRef, añadir segundo Evidence owner/root, downgrade v2→v1 sin terms, reimportar MANUAL como IMPORT, export v1 cuando hubo terms, permitir ACTIVE en preflight por default, FX automática o APIs pagas.

## B4
Contrato de integración previsto:
1) Snapshot v2 lossless: records originales + terms (UNKNOWN explícito para filas v1), orden canónico y FK por evidenceId, conteo 1:1, fingerprint metadata y bounds. Restauración sólo a destino fresco o idéntico; transactional, se conserva exactly original idempotencyKey/payloadFingerprint/evidenceId/sourceKind. V1 legacy snapshot/codec byte-equivalent.
2) FBS1 v4 gate con escritura/lectura comercial versionada + manifest hash v2. FBS2 wrapper sin inventar un cambio si ya transporta FBS1v4 correctamente; capturar siempre con quiescencia de cinco owners, verify/reopen y restore replay fences antes de activar DEV.
3) Portability v2 sin fuga ni pérdida: tipo portable con terms, watermark v2, export JSON completo y determinista, preview de alcance/currency sin FX; política de egress local, sourceRef y promotionSourceRef son literales, no URIs ejecutables.
4) Preflight exacto: stage schema2 sólo por path/opener explícito y probado, preservando fail-closed de ACTIVE schema1; no upgrade automático ni cambio a V2 productiva. C1a v1 y C0B no se reescriben.
5) Si auditoría de rutas y fallos excede autoridad de una unidad, dividir en C1b2-E Evidence/codec y C1b2-G cinco-store/portabilidad, con **permiso separado** para cada uno, sin heredar READY de C1b1.

Rutas **CANDIDATAS, NO AUTORIZADAS** hasta inspección final y freeze de un subdelta:
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceTypedTerms.kt`
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidenceBackupPort.kt`
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt`
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceMaintenancePort.kt`
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/SemanticBackupSetCodec.kt`
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/SemanticBackupRestore.kt`
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortableBundleJsonWriter.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreSemanticCodec.kt`
- tests dirigidos Evidence `CommercialEvidenceTypedTermsTest.kt`, `EvidencePortabilityPortTest.kt` y `CommercialEvidenceMaintenancePortTest.kt`; maintenance `SemanticBackupRestoreT2Test.kt`, `PortabilityCoreT2Test.kt`; generation `FaroV5FiveStoreBackupCaptureT2Test.kt`, `FaroV5FiveStoreSemanticCodecT2Test.kt`; workflow DEV nuevo. **Límite de 20 rutas por permiso; congelar scope exacto después de verificar impacto de mantenimiento y FBS2**.

## B5
T01 v1 backup fixtures y FBS1 versión antigua reencode byte-identical, schema1 sin terms/UNKNOWN.
T02 Evidence2 STAGED con registros import/manual/tool, snapshot→restore→reopen; mismos IDs, fingerprints, sourceKind, fechas y terms (incluidas UNKNOWN), count exacto y FK; repetido no duplica.
T03 FBS1v4/FBS2 capture+decode+restore y quiescencia cinco-store, manifest/fingerprint incluye terms, corrupción metadata altera digest y falla.
T04 cold crash/kill durante migración, restore, WAL/restart, pointer staged, rollback/journal/lease, idempotencia y no SQL parcial.
T05 portabilidad JSON versionada, watermark estable ante orden canónico, preservación de locality/validity/presentation/promotion, literal sourceRef hostile, límite de bytes y egress local.
T06 FBS2 legacy v1–v4 rehace fixtures sin cambiar semántica, no introducir Finance ni borrar receipts existentes.
T07 Active openners schema1 siguen fail-closed ante v2; STAGED v2 abre exclusivamente con permisos, shared gate y propia versión; no V2 productiva.
T08 fechas incoherentes, moneda ARS/USD sin FX, pack/unidad incompatibles, elegibilidad sin prueba, promociones sin fuente, locality UNKNOWN, descuentos inválidos, entradas enormes: reject/UNKNOWN sin precio vigente falso.
T09 N-034 read/write, revocación, confirmación humana, exclusive writer, CAS, generación, no compra/stock/pagos.
T10 CI JVM XML dirigido por SHA y guard; Android DEV API35/G15 campaña **separada** después de B7 lógico integral.
**NO RUN para C1b2**.

## Gate
Estado **PREAUDIT_DESIGN_ONLY_NO_READY**. C1b1 B7 PASS scoped no cubre estas obligaciones. Antes de B6: cerrar scope exacto y dependencias (incluido `SemanticBackupRestore`, `PortableBundleJsonWriter`), permitir solo su delta, guard documental SUCCESS, permiso nuevo READY por rutas y preflight contra HEAD. C1 global no cerrado.
