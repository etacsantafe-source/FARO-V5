# FARO V5 — S3.A4 Reality 8 / compatibilidad estricta 5–8 del preflight · B1–B5
Fecha 2026-11-06 AR. Baseline `ce70305e9b29c1a0dd22602203b1814faebd749e`. Unidad `S3_A4_REALITY8_FOUR_VERSION_PREFLIGHT`.
**Únicamente extender el guardián READ_ONLY de versiones históricas. NO subir schema Reality, NO ejecutar migraciones, NO crear tablas/owners, NO cambiar export/import ni activar host.**

## B1 — Evidencia viva, hipótesis y aceptación exacta
- `StagedSqlitePreflight.Schema` hoy acepta actual + un `legacyVersion` + `olderLegacyVersion`; `requiredTables(observedVersion)` no reconoce cuarta versión. `FaroV5GenerationStoreGraph.requireStrictV2StoreSchemas` hoy current7, legacy6, older5. Para schema8 se exigirán rutas 7,6,5 y cada una con tablas requeridas distintas. Sin ampliar el contrato, se perdería la ruta de schema5 o se relajaría la admisión de tablas.
- `ActiveSqlitePreflight.requireAll` y `StagedSqlitePreflight.requireAll` comparten exactamente `Schema.requiredTables`, preservan NOFOLLOW/symlink, quick_check y foreign_key_check. STAGED usa URI immutable=1 solo en generación sellada sin WAL/SHM, ACTIVE usa read-only normal para observar WAL; mantener.
- B7 previo FBS2v3 semántico `docs/s3-night/S3_A4_N031_FBS2V3_SEMANTIC_ENVELOPE_B7_PASS_20261106.md` código `8fb44d23` 29/29 y siete CI SUCCESS, formato v3 bloqueado para export/restore porque Reality schema8+owners NO existen.
- Criterio binario de esta unidad: cuatro perfiles explícitos current8/legacy7/older6/oldest5, validación estricta ordenada, admisión STAGED/ACTIVE con SQLite local read-only, fail closed para 4/9/missing table/sidecar/symlink y regresión del antiguo patrón actual7/legacy6/older5; JUnit XML exactamente 6/6. A4 permanece OPEN.

## B2 — V4.4 y componentes V5 existentes
- V4.4 no usa `StagedSqlitePreflight`; su puerta y migración de bases es distinta, por lo cual NO copiar comportamiento/UID ni conceder compatibilidad genérica. Reminders/MonthlyPlan de V4.4 documentados en `S3_A4_N031_REMINDER_MONTHLYPLAN_OWNERS_B1_B5_STOP_20261106.md` sirven solo como semántica de dominio posterior.
- REUSAR `StagedSqlitePreflight.Schema` con chequeos estrictos, `ActiveSqlitePreflight` existente, `FaroV5GenerationStoreGraphV2T2Test.exactRealityPreflightAllowsOnlyFiveSixSeven` y `RealityStore.schemaVersion()=7` sin modificar. El contrato nuevo es opt-in por parámetro, no altera constructor ni preflight 7 actual.
- Alternativas: (A) quitar la admisión antigua schema5: DESCARTAR por pérdida de datos históricos; (B) aceptar rangos 5..8 sin validar tablas: DESCARTAR por corrupción/DDL faltante; (C) añadir perfil oldest5 explícito y negar el resto: ELEGIR. No dependencia nueva, no API paga.

## B3 — Referencias independientes, fallas y riesgos
- SQLite PRAGMA user_version https://www.sqlite.org/pragma.html: header controlado por app, no prueba integridad de tablas; REFERENCIA. El test verificará las **tablas requeridas por versión**, no solo el entero user_version.
- SQLite WAL https://www.sqlite.org/wal.html: archivo WAL en read-only debe inspeccionarse en modo compatible para no ignorar commits; ADAPTAR: STAGED immutable SOLO sin sidecar, ACTIVE read-only normal preservado.
- Android migration https://developer.android.com/training/data-storage/room/migrating-db-versions: probar rutas de migración preservando datos; REFERENCIA para la siguiente unidad Reality 8, no incorporar Room ni fallback destructivo.
- Sin código externo copiado. Pruebas negativas: ordenar 8>7>6>5, inconsistencia null/tablas, dos versiones iguales, admisión 4/9 y tabla ausente, sidecar malicioso. Test fresh schema8 sintético **no implica migración real 5→8**.

## B4 — Delta única unidad (3 paths exactos)
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/StagedSqlitePreflight.kt`: añadir `oldestLegacyVersion:Int?=null` y `oldestLegacyTables:List<String> = emptyList()`, exigir oldest5 < older6 < legacy7 < current8 y parejas version/tablas válidas. Actual lógica para 7/6/5 intacta. `requiredTables` solo autoriza exactamente 4 versiones cuando configuradas.
2. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/StagedSqliteFourVersionPreflightT2Test.kt`: seis casos exactos de perfiles, malformed config, SQLite STAGED+ACTIVE read-only y negativa de tabla/versión/sidecar. No escribe tablas de datos reales de FARO, solo fixtures temporales.
3. `.github/workflows/faro-v5-s3-reality8-four-version-preflight-directed.yml`: Java21 + Gradle `:store:reality:test` dirigido solo a nueva suite, 6/6 XML, guard obligatorio y regresión automática V2 core que pueda dispararse. Android físico NOT_RUN.
- Ningún cambio a `RealityStore`, `FaroV5GenerationStoreGraph`, FBS2 capture/export/restore, schema usuario, API de escritura ni main.

## B5 — Auditoría adversarial y permiso preciso
- El agregado solo modifica contrato de inspección: por default oldest=null deja esquema7 EXACTO; no migra, no escribe, no rebaja seguridad. Mismo `immutable=1` sellado con rechazo sidecar, NOFOLLOW, quick_check, foreign_key_check y escritura fuera de alcance.
- Comprobar que oldestLegacy requiere legacy y olderLegacy, y tablas no vacías/distintas por perfil; rejecting versiones fuera de lista. Evitar permitir oldest7 cuando older6 (debe ser estrictamente menor). Compatibilidad STAGED/ACTIVE solo lectura; los tests crean SQLite en directorios efímeros y verifican rechazo sin mutaciones.
- Diferenciar **certificación de estructura del guard** de **migración real**: próximo bloque debe añadir Reality schema8 con preservación Objective/Matter/receipts y propietarios Reminder/MonthlyPlan SOLO junto con FBS2v3 captura+restore completa y pruebas 5/6/7→8; anteriores B5 STOP siguen para escrituras de datos de usuario. Requiere B1–B5 propio y permiso READY acotado.
- S2 CLOSED 8/8; S3 A1/A2 2/5 alcance automatizado; A3 Guardados+8 no aprobados, catálogo172; S3 A4 OPEN, A5 G15 X01–X12 NOT_RUN, V2 productiva/Noche/Auto/S4 OFF. GitHub writer single CAS, liberar durante CI.
- **B5 READY exclusivamente 3 paths de la extensión estricta READ_ONLY; NO autoriza schema8 o owner durable**.
