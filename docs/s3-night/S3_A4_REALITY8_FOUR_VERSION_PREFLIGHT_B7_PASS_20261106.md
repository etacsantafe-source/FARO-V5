# FARO V5 S3.A4 — Reality8 four-version read-only preflight B7 PASS_SCOPED
Fecha: 2026-11-06 Argentina. Unidad `S3_A4_REALITY8_FOUR_VERSION_PREFLIGHT`.
**SHA funcional exacto:** `c6ec477a57515c3bc01ed15b17fce8396addc34b`.

## B7 — Auditoría final independiente y veredicto
**PASS_SCOPED_READ_ONLY_FOUR_VERSION_ADMISSION**, no Reality8 instalado, no migración efectuada, no Reminder/MonthlyPlan owner persistente, no user backup/export/restore v3 ni A4 cerrado.

### Evidencia de protocolo y alcance exacto
- B1–B5 ANTES de código en `docs/s3-night/S3_A4_REALITY8_FOUR_VERSION_PREFLIGHT_B1_B5_20261106.md`, blob `fbe0c1e364e4fa5d1708d7923121750538526f93`, doc-only commit `ac0a88ed55d7effcf1e81c9b397991f76fd1314f`, READY doc-only commit `99bdc3d9c05b70c254646ba68111c797c3cd4be1`.
- B6 commit `c6ec477a57515c3bc01ed15b17fce8396addc34b` con `FARO-UNIT:S3_A4_REALITY8_FOUR_VERSION_PREFLIGHT` modificó SOLO tres rutas aprobadas: `StagedSqlitePreflight.kt`, nueva suite `StagedSqliteFourVersionPreflightT2Test.kt` y workflow dirigido `faro-v5-s3-reality8-four-version-preflight-directed.yml`. No cambios en `RealityStore`, DB existentes, `GenerationStoreGraph`, FBS2, app, manifest, S2, main, V4.4 ni catálogo172.
- Writer único CAS epoch377 para el código; RELEASED epoch378 durante CI; retomado epoch379 para B7; liberar epoch380 tras checkpoint.

### CI exacta y pruebas verificadas
Cinco workflows SUCCESS y head SHA exacto `c6ec477a`:
- [#42263308102](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42263308102) seven-block mandatory protocol guard: **SUCCESS**.
- [#42263308259](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42263308259) dirigido: **SUCCESS**, JUnit XML `StagedSqliteFourVersionPreflightT2Test` **6/6** más `FaroV5GenerationStoreGraphV2T2Test` **17/17**, total **23/23** sin failures, errors o skips, BUILD SUCCESSFUL 3m11s.
- [#42263308186](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42263308186) Reality G4 owners: **SUCCESS**, XML domains **9/9**, backup filtering3, finance entry5, finance6.
- [#42263308266](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42263308266) Reality receipt/history: **SUCCESS**, receipts **3/3**, finance activity **3/3**, FBS1 restore **4/4**, FBS2 semantic **7/7**.
- [#42263308058](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42263308058) V2 core: **SUCCESS**, DEV_SEARCH_SESSION_XML **10/10**, G5_HOST_FOUR_OWNER **1/1**, M2_DEBUG_T4 **7/7**, A1_SCHEMA7_GENERATION **17/17**, M2B ISOLATION **4/4**, M1 ISOLATION **4/4**; no Android físico.

### Qué prueba y qué NO prueba
- `StagedSqlitePreflight.Schema` soporta opcionalmente 4 perfiles EXACTOS `current8/legacy7/older6/oldest5` con perfiles de tablas por versión y estricta cadena descendente. Omisión del cuarto conserva comportamiento anterior `7/6/5`. Rechaza 4/9, duplicados, tablas exigidas faltantes, valores de perfil incompletos, symlink final y STAGED con WAL externo. Activo y STAGED usan el mismo `requiredTables`.
- Test de admisión con **fixtures temporales/sintéticos**, NO migración real de información de usuario de schema5/6/7, no prueba tablas reales de Planning, no prueba FBS2v3 recovery. Los controles STAGED immutable y ACTIVE readonly originales permanecen.
- La migración 8 debe ejecutarse bajo autoridad explícita de generación inactiva/copia staged, no al abrir un Reality7 productivo. Necesita nueva integración deliberada entre Graph/Reality, con conservación de receipts/AtomicApply/ACL, captura FBS2v3 de Planning desde el MISMO Reality mutex, restauración idempotente y reintentos con pin AEAD. **Asegurar además que escrituras de Planning NO superen los límites de 4MiB/20k registros recuperables**; rechazar en la misma transacción ANTES de commit, no durante un backup posterior. Las reglas/ocurrencias y mes civil no se derivan de Notificaciones ni Finance.
- La versión v3 existente continúa **FENCED** para export/restore. `global_pending_count=UNAVAILABLE` hasta N031 owner real y host ACL. A1/A2 2/5 alcance automatizado, A3 Guardados+8 NO APROBADO (catálogo172), A4 OPEN, A5 G15 X01-X12 NOT_RUN. V2 prod/Noche/Auto/S4 OFF. S2 8/8 congelado.

### Próximo bloque permitido
Cerrar permiso `BLOCKED`, writer RELEASED epoch380. **Nuevo B1–B5 integrado:** plan de migración explícita schema8 y propietarios ReminderRule/Occurrence/MonthlyPlan con presupuesto y respaldo/restauración v3; no añadir tablas ni permitir escrituras persistentes mientras cualquiera de esas invariantes siga sin pruebas. CI exacta + B7 o STOP. No repetir unidades anteriores.
