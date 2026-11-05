# FARO V5 — S3.A2/G4 Reality durable Objetivos y Asuntos · B1–B5 (05/11/2026)

**Unidad:** `S3_A2_G4_REALITY_DURABLE_DOMAIN_OWNERS`; HEAD pre-revisión `d0940c6a585c77165a7fe6438a2162efd0e17fd5`. S2 CLOSED/FROZEN 8/8, G1/G2/G3 CLOSED; G4 global OPEN; V2 productiva/S4/Auto/Noche OFF. Esta unidad NO habilita exposición en App, escritura de catálogo, backup FBS3 ni navegación; añade backing real dentro de Reality, protegido por transacción única y rechazo explícito de backup legado si contiene nuevos datos.

## B1 EVIDENCIA Y BASE VERIFICADA
- `v5/store/reality/RealityStore.kt` actualmente tiene `SCHEMA_VERSION=5`, WAL+FULL, single `RealityTransactionScope` con `requireActiveScope`, `MaterialWriteGate`, migrations 0–4 a 5 y API Finance existente. `RealityStore.readBackupSnapshot()` usa `readSchemaVersion()` y no conoce Objetivos/Asuntos.
- `RealityBackupSnapshot` y `SemanticBackupSetCodec` FBS1 guardan explícitamente `schemaVersion=5` en el legacy y datos Finance/Profile; el test `SemanticBackupRestoreT2Test.kt` restaura `RealityBackupSnapshot(schemaVersion=5)`. `FaroV5FiveStoreSemanticCodec` FBS2 v1 encapsula el FBS1 exacto, y test `FaroV5FiveStoreSemanticCodecT2Test` prohíbe alterar bytes/version1. NO reescribir FBS1/FBS2.
- `RealityMaintenanceInspection` y `freshForSemanticMigration` no inspeccionan nuevos dominios: un restore V1 sin guardia los borraría silenciosamente.
- Contratos puros de Objetivos/Asuntos B7 código `85efd609`, 8/8 PASS, sin dueño. Finance locator B7 código `6303c6f` (6/6), no montado; NO tocarlos.
- No crear sexto store ni reutilizar el repositorio in-memory V4.4. No operaciones productivas hasta binding dedicado más adelante.

## B2 COMPARACIÓN CON PATRONES EXISTENTES Y V4.4
- Finance: `FinancePersistencePort` se expone como adaptador de RealityStore y exige mismo `RealityTransactionScope` para todos los writes. Replica patrón de puerto, no el dominio financiero.
- V4.4 `FaroV4ObjectivesGateway.readForSearch()`, `MatterRepository.all()` aportan semántica de título, estado, detalle/fechas; NO tomar su memoria como fuente duradera de V5.
- V1 backup FBS1 solo puede continuar codificando **Profile+Finance**, sin modificar versiones; un nuevo owner con filas debe bloquear export/migración legada si todavía no existe el contenedor de backup G4 versionado.

## B3 INVESTIGACIÓN EXTERNA Y DECISIÓN
- SQLite official ALTER TABLE: https://sqlite.org/lang_altertable.html — realizar migration en una transacción y verificar FK/integrity, no editar `sqlite_schema` manualmente. **ADOPTAR** tablas nuevas aditivas, `PRAGMA user_version` 5→6, camino incremental 0..5→6.
- Android Room migration: https://developer.android.com/training/data-storage/room/migrating-db-versions — fallback destructivo elimina datos; **RECHAZAR** todo downgrade/fallback destructivo.
- Nextcloud Unified Search https://docs.nextcloud.com/server/stable/developer_manual/digging_deeper/search.html — aislamiento por owner; no se conecta aún índice ni se declara cobertura `COMPLETE`.
- Alternativa A (elegida): 2 tablas de estado revisionado + 2 tablas de filas en SAME Reality SQLite, aditivo, `upsert` idempotente dentro del writer y versiones de backup distinguibles. Alternativa B: 2 archivos independientes = sexto/séptimo store, rechazado. Alternativa C: rows de Workspace/Finance o in-memory, rechazado por pérdida de autoridad/backup.
- Restricción contra pérdida: `SCHEMA_VERSION=6` **interno**, y `LEGACY_BACKUP_SCHEMA_VERSION=5` **externo** solo si NO existen filas nuevas. FBS1 v1 continúa bytes idénticos cuando Objetivos/Asuntos están vacíos; una fuente con filas G4 no se exporta silenciosamente por ese camino. Backup de dominios nuevos se expondrá únicamente mediante snapshot propio, con restauración versionada en unidad siguiente.

## B4 PLAN ACOTADO Y PATH SET
1. `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/spi/ObjectivePersistencePort.kt`: interfaz `read/upsert` transaccional sin exposición al catálogo.
2. `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/spi/MatterPersistencePort.kt`: mismo patrón.
3. `v5/store/reality/build.gradle.kts`: dependencias a ambos módulos.
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: migration 0..5→6 aditiva; tablas y revisiones; ports y lecturas canónicas; `upsert` protegido por `requireActiveScope`, timestamps monotónicos, duplicados idempotentes; `backup.snapshot` y `semanticMigration.snapshot` FAIL-CLOSED si hay G4 material; legacy schemaVersion5 externo cuando vacíos; `restoreOrVerify` acepta legacy5 solo sobre destino nuevo sin datos.
5. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityMaintenancePort.kt`: contar/revisar Objetivos y Asuntos y agregarlos al guard fresh.
6. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityDomainBackupPort.kt`: snapshot read-only de nuevos propietarios con revisiones y filas, sin exportación ni restore todavía.
7. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityDomainOwnersT2Test.kt`: 6/6 casos nuevos sobre persistencia/reopen/idempotencia/rollback/scope ajeno/migración5→6/fail-closed backup, más regresión legada acotada.
8. `.github/workflows/faro-v5-g4-reality-domains-directed.yml`: JVM test dirigido con XML exacto, además ejecutar test Reality legacy para regresión de almacenamiento; NO ejecutar tests físicos.

## B5 ADVERSARIAL / ACEPTACIÓN NEGATIVA
- Migración: de SQLite user_version5 con Finance/Profile preservados a 6 sin tabla objetivo previa, sin recrear ni vaciar datos; versión 6 ya iniciada no reimporta filas; mayor versión debe STOP. En `PRAGMA integrity_check` y `foreign_key_check`, ninguna inconsistencia.
- Autoridad: `upsert` exige scope del mismo RealityStore+transacción active, no una interfaz pública de UI. Rollback revierte fila y revisión juntos; misma escritura idéntica no incrementa revisión, update con timestamp anterior rechaza. No se exponen `OperationBinding` ni capacidades sin aprobación.
- Backups: mantener FBS1/FBS2 v1 intactos; al existir G4 rows (o revisión de dominio positiva), todo snapshot V1 de Reality y semantic migration debe **fallar explícitamente** antes de perder datos. Restore legacy exige target fresh incluyendo nuevos dominios. Nuevo `RealityDomainBackupSnapshot` es solo lectura, no suficiente para certificar FBS3/restore; no activar V2.
- Otra cara: esta etapa SOLA **NO cierra G4** y no permite lanzar producto con filas G4 porque falta backup/restauración versionados, host/grants y navegación. B7 debe decir `PASS_STORAGE_CORE_ONLY` o RED_FIX, jamás certificación global. Si no se pueden preservar invariantes/legacy CI, STOP sin publicar.
- B5 **READY_BOUNDED_FOR_CODE_ONLY** condicionado a las pruebas y al cierre de los otros 2 CI Android/V2 del commit anterior (ambos ya SUCCESS al revisar). Writer CAS y permisos por ruta; sin GitHub Actions manuales.
