# FARO V5 — W04-C1b · Términos comerciales tipados y migración Evidence2 · Preauditoría de diseño

Fecha local: 2026-11-20. Punto de partida código C1a `42c7a6366b7aeced94250ffe84ed9fd7f6694ef1`, C1a B7 PASS DEV (CI `44803997142`, 9/9).
Unidad objetivo `S4_W04_C1B_TYPED_COMMERCIAL_TERMS_MIGRATION_DEV`. **READ_ONLY DESIGN, NO READY, NO B6**, no migración ejecutada. Esta unidad cierra la parte de C1 que C1a no debe simular.

## B1 — Contrato y evidencia verificada
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt` @blob `cf9d53089cafd93538e6b03f9873c98264d20b65`: `SCHEMA_VERSION=1`, `initializeSchema()` crea tabla `commercial_evidence`, `priceHistory` retorna registro v1; `CommercialPriceObservation` carece de locality, issuedAt, validUntil, presentation, payment, eligibility y promotion. `sourceRef` tiene texto opaco, NO campo JSON/URI de metadata.
- `EvidenceBackupPort.kt` @blob `23859d874c6203edf8cebdf078a5771315bdc5b0`: `CommercialEvidenceBackupSnapshot(schemaVersion,records)` serializa records v1. `CommercialEvidenceStore.restoreOrVerifyBackup` exige `snapshot.schemaVersion == SCHEMA_VERSION` y target vacío o exactamente igual; no es conversor de versiones.
- `EvidencePortabilityPort.kt` @blob `104522e7605f3a2924fcf7ccbcf8f40a04a08399`: `PortableCommercialObservation` y watermark `faro-portability-commercial-v1` no incluyen términos tipados. Cambiar esquema sin codec nuevo perdería evidencia/identidad en export.
- `CommercialEvidenceMaintenancePort.kt`: `CommercialEvidenceMigrationRecord` y `CommercialEvidenceMigrationIdentity` solo representan campos v1 y `sourceKind=IMPORT`; `CommercialEvidenceSemanticMigrationPort.importFresh` requiere destino sin registros. No asumir que migración de registros MANUAL previos será una reimportación inocua.
- `FaroV5GenerationStoreGraph.kt`: `requireStrictV2StoreSchemas` fija `StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1, listOf("commercial_evidence"))`; abrir activa una base version2 sin actualizar esta prueba **debe** fallar. C1b debe preservarla hasta que se haya validado un opener DEV versionado específico.
- `FaroV5FiveStoreBackupCapture.kt` usa `graph.commercialEvidence.backup` en captura coordinada; también hay restore y portable backup en `:store:generation`. Necesita prueba cinco-store y retorno verificado.
- C1a `ManualCommercialQuoteTerms` rechaza términos explícitos `REQUIRES_VERSIONED_TERMS`; no ocultar bloqueo ni inferir vigente/localidad.

## B2 — Comparación y compatibilidad
V4.4 SmartCompra L11/L15 permitía ofertas con contexto comercial; V5 exige preservación de evidencia frente a verdad financiera. **Invariante:** los registros antiguos Evidence v1 deben reabrir con campos nuevos UNKNOWN sin inventar localidad, fecha de vencimiento, cantidad por presentación, condiciones, descuentos o tasas de cambio. Mantener `observedAt` opcional y `acquiredAt` separado. No afectar W1 Finanzas, W4-B1 necesidades ni C0B lectura ya certificada.
Migración debe ser reversible por backup verificable o activar una nueva generación STAGED tras prueba completa; no cambiar schema activo in-place a ciegas.

## B3 — Alternativas evaluadas
- ADAPTAR/USAR (candidata preferida sujeta a pruebas): **evolución versionada en LA MISMA SQLite CommercialEvidence**, agregando campos tipados opcionales/UNKNOWN, codec v2 y lectura v1 compatible, bajo generación staged/backup. Puede utilizar tabla separada de metadata dentro de la misma SQLite si existen invariantes referenciales e idempotencia fuertes.
- REFERENCIA: tabla `commercial_evidence_quote_terms` dentro del mismo Evidence store, vinculada a `evidence_id` y `schemaVersion=2` por FK; útil si evita modificar el payload original/identity. Debe probar restore/replay/consistencia WAL antes de elegir.
- DESCARTAR: segundo store/root, JSON secreto en sourceRef, sobrescribir precio v1, inferir localidad o vigencia, desactivar `StagedSqlitePreflight`, usar moneda externa/FX automático o pagar APIs, volcar precios a Finance.
Criterio de decisión: historia v1 byte-equivalente y semánticamente estable, nuevo registro con términos tipados completos, backup/restore/captura/portabilidad sin pérdida y preflight de generación estricta; alternativas que no pasen estos tests quedan rechazadas.

## B4 — Requisitos de diseño, rutas candidatas, dependencias
**Contrato tipado propuesto** (no congelado, campos que pueden exigirse): locality identificada o UNKNOWN explícito; `issuedAtEpochMillis` opcional, `validFromEpochMillis` y `validUntilEpochMillis` con intervalos coherentes; presentación (cantidad milésimos/unidad/pack), método de pago, elegibilidad y promoción con fuente separada; `sourceRef` sigue literal. Campos desconocidos NO equivalen a oferta vigente. Importe `amountMinor >0`, `currencyCode` exactamente 3 mayúsculas. Validaciones no dependen de Internet.
Ciclo `v1 → STAGED v2 → backup+restore+reopen/portability roundtrip → preflight/health → explicit DEV activation`; no cambiar activo V2 productivo. Preservar idempotency key/fingerprint/evidenceId en migración o documentar equivalencia fuerte con receipt firmado; no regrabar MANUAL como IMPORT por defecto.
Rutas CANDIDATAS que deben auditarse antes de emitir permiso (NO autorizadas):
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`
2. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidenceBackupPort.kt`
3. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt`
4. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceMaintenancePort.kt`
5. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
6. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt`
7. Suites exactas Evidence y Generation + workflow DEV segregado; **congelar archivos y rutas después de inspeccionar restore/import actual**.
Si el delta excede una autoridad segura, dividir B6 en C1b esquema/codec staged y C1b integración y prueba del active opener con **dos permisos propios**, no heredar el permiso C1a.

## B5 — Pruebas y riesgos para gate
T01 DB fresh v2, lectura de v1 antes/después con metadata UNKNOWN.
T02 migración v1 con varios records MANUAL/TOOL/IMPORT, idempotency exacta, timestamp y sourceRef intactos; repeat no duplica.
T03 backup v1 y v2, restauración verificable y compatibilidad; ningún byte/registro sin representación.
T04 capture coordinada 5-store, semantic migration, portable watermark y export/import versionado no pierden términos ni historia.
T05 kill/crash antes, durante y después de COMMIT; journal/pointer/WAL, rollback/recovery; no abrir mitad de schema.
T06 ámbito de `StagedSqlitePreflight`: versión1 legacy sigue válida en su ruta; v2 sólo en opener DEV certificado; desconocido fail-closed.
T07 locality, moneda ARS/USD, presentación incompatible, fecha inválida, expiración, elegibilidad sin prueba, promociones, fuente hostil, vacío; rechazos correctos sin FX.
T08 permisos N-034 READ/WRITE, revocación, confirmación humana, idempotencia y persistencia bajo un solo writer/gate; no Finance/stock.
T09 Android DEV/API35 y G15 sólo campaña separada una vez que B7 lógico global esté listo.
**Ninguna de estas pruebas fue ejecutada en C1b.**
## Puerta siguiente
Estado: **PREAUDIT_DESIGN_ONLY**, requiere B1–B5 delta final por rutas exactas, preflight y guard documental, luego permiso READY individual para programar. C1a está cerrado; C1 global y C2/C3 NO cerrados.
