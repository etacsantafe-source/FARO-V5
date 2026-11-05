# FARO V5 · S3.A1 N015 / schema7 generation XML gate · B1–B5 — 05/11/2026

**Unidad exacta:** `S3_A1_N015_SCHEMA7_GENERATION_XML_CI`; baseline `d73c0131895e516a88ee17a3089ef928ace5905a`, writer manual exclusivo. **Objetivo acotado:** ejecutar pruebas REALES previamente agregadas pero nunca ejecutadas de cinco bases, migración y preflight, sin modificar lógica persistente ni repetir investigación funcional. No equivale a cierre A1, V2 productiva o aprobación de Guardados.

## B1 — Inventario de hechos, propietarios y falla pendiente
1. Commit `dc06c01da9597c6642d7e4055f0d71cb11fb217e` agregó dos pruebas `exactRealityPreflightAllowsOnlyFiveSixSeven` y `existingSchemaSixRealityIsAdmittedAndUpgradedToSeven` a `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraphV2T2Test.kt`. Clase completa: **17 @Test**, ninguna de las dos pruebas nuevas se ejecutó aún. B7 anterior lo rotula `2_NOT_RUN`, no asumir certificación.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` ya verifica Reality 7/6/5 con perfiles explícitos; `StagedSqlitePreflight` y `ActiveSqlitePreflight` conservan integrity/foreign key/symlink/WAL. La lógica no debe alterarse sin un RED_FIX respaldado por un fallo real.
3. CI real `42113189767` de V2 DEV dio **SUCCESS 10/10 +1/1** y validó esquema7 inicial nuevo. CI `42113190102` G4 y `42113189882` Reality/backup también SUCCESS. El workflow existente `.github/workflows/faro-v5-v2-core-directed.yml` ejecuta pruebas de app V2 y valida XML, **no** invoca `:store:generation:test`. Este hueco es de evidencia, no razón para reabrir S2 o rediseñar owners.
4. Permiso actual `FARO_PROTOCOL_WORK_PERMIT.json` está BLOCKED tras B7; preflight exige READY exacto en commit padre antes de cambiar workflow, y B7 posterior independiente.

## B2 — Comparación/reutilización V4.4 y V5
V4.4 no usa generación aislada de cinco stores y carece de gate schema7; copiar su migración no corrige evidencia. V5 tiene un workflow dirigido probado, XML exacto y 17 fixtures nativos ya escritos. **Reutilizar** CI en lugar de nuevo workflow, dependencia o implementación de estructura paralela. Conservar V2 OFF, no invocar Android físico ni esquema de datos reales.

## B3 — Investigación dirigida, alternativas y elección
- Gradle test filtering: https://docs.gradle.org/current/userguide/java_testing.html#test_filtering — `--tests` selecciona clase específica; **USAR** en tarea JVM de generación. No confundir compilar con ejecutar.
- Gradle JUnit XML: https://docs.gradle.org/current/userguide/java_testing.html#test_reporting — comprobar suite exacta, `tests=17 failures=0 errors=0 skipped=0`; **USAR** mismo patrón de verificación del workflow V2 existente.
- SQLite schema migration: https://www.sqlite.org/pragma.html#pragma_user_version — validar versiones observadas y no inventar compatibilidad por declarar un rango. **REFERENCIA** ya estudiada en B1–B5 previo.
Alternativas: A) **USAR** mismo workflow V2 + una etapa adicional de JVM generación, XML estricto; B) **DESCARTAR** workflow nuevo o CI global masiva, desperdicia tiempo y genera duplicación; C) **DESCARTAR** marcar tests PASS por fuente compilada o añadir `SKIPPED` tolerado.

## B4 — Diseño del delta mínimo, sin código de datos
Única modificación funcional permitida: `.github/workflows/faro-v5-v2-core-directed.yml`. Añadir **después de la verificación de tests V2** una etapa `./gradlew ... :store:generation:test --tests "ar.com.faro.v5.generation.FaroV5GenerationStoreGraphV2T2Test"` y paso validador XML exacto de clase (17,0,0,0). Conservar intactos todos los pasos/prohibiciones de app/manifest, no cambiar timeout/cancel-in-progress ni agregar workflow_dispatch ejecutado manualmente. El archivo ya dispara CI por cambio propio.

## B5 — Auditoría adversarial previa y criterios de salida
- El gate nuevo **falla** ante XML faltante, total distinto de 17, error/failure/skipped; no hace `|| true`.
- Ejerce aceptación v7, matriz legacy 5/6, migración fixture 6→7, rechazo schema4/8 y preflight. Declarar *certificada* únicamente la cobertura realmente ejecutada por la suite; un schema5 con recibos reales puede requerir prueba propia posterior.
- El mecanismo de permisos de GitHub CI se mantiene `contents: read`; sin instalar app, sin APK, sin egress, sin escrituras a stores del usuario ni cambios de catálogo.
- Los 11 tests V2 previos y verificación de manifest deben seguir verdes; si 17 fallan, **B7 RED_FIX** y preauditar corrección, no cambiar expectativa de XML para engañar el gate.
- Exact commit SHA con `FARO-UNIT:S3_A1_N015_SCHEMA7_GENERATION_XML_CI`; observación independiente de CI, anotación B7 separada, regreso a BLOCKED y writer RELEASED.

**B1–B5 VERIFIED** para instrumentar el gate **solo** en ruta de workflow. B6 aún no ejecutado, B7 pendiente; no se abre otra etapa ni se declara S3.A1 CLOSED.
