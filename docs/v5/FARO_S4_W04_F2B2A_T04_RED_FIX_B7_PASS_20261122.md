# FARO V5 — W04 F2B2A B7 PASS SCOPED · T04 inesperado rechazado

Fecha local 2026-11-22/23. SAME UNIT `S4_W04_F2B2A_HOUSEHOLD10_TYPED_SEALED_STAGE_ADMISSION_DEV`.
Code SHA `81d9e436674a15a295591786ce2be3d0632301ff`; READY parent `1036f26076d4bba4e99824d83dd5fc5ebdb6dea5`. La primera CI `45059180422` 5/6 T04 FAIL generó RED_FIX y no fue declarada PASS; cierre RED_FIX previo `docs/v5/FARO_S4_W04_F2B2A_B7_RED_FIX_TRIGGER_20261122.md`.
Preauditoría vigente B1–B3 `docs/v5/FARO_S4_W04_F2B2A_TYPED_SEALED_ADMISSION_B1_B5_20261122.md` y B4–B5 delta `docs/v5/FARO_S4_W04_F2B2A_T04_DRIVER_DEPENDENCY_B4_B5_DELTA_20261122.md`, con permiso SAME UNIT READY exact4 emitido solo después de guardia documental `45096468591` SUCCESS.

## B7 — PASS_SCOPED, pruebas reales verificadas independientemente

1. Guard protocol obligatorio GitHub Actions `45096617145`, SHA `81d9e436674a15a295591786ce2be3d0632301ff`: COMPLETED SUCCESS.
2. CI dirigida `45096617186`, job `137437718930`, SHA idéntico: COMPLETED SUCCESS; preflight READY parent (3 rutas tocadas), JDK21, SDK37, `:store:generation:test --tests ar.com.faro.v5.generation.FaroV5Household10Evidence2SealedStageAdmissionTest` compilación BUILD SUCCESSFUL.
3. Job verificó XML mediante `ElementTree`, `tests=6,failures=0,errors=0,skipped=0`, seis nombres únicos; marcador `W04_F2B2A_SEALED_HOUSEHOLD10_EVIDENCE2_JVM_XML_PASS_6_6`.
4. Independientemente se descargó artefacto original GitHub Actions `14783202790`, `w04-f2b2a-sealed-typed-stage-xml.zip`, 811 bytes. ZIP SHA256 `35131184f3576917744a4dd287de1d0f48b737a6b93e968e6adcbbddb8526182` (coincide con GitHub Action upload). XML interno `test/TEST-ar.com.faro.v5.generation.FaroV5Household10Evidence2SealedStageAdmissionTest.xml` SHA256 `28c7682dd343f607252a8a6467b578300798fac5f13d01f0cd31c59747d0ebf1`; lectura `ElementTree` independiente confirma **6 tests, 0 failures, 0 errors, 0 skipped**.
5. Casos individuales PASS: T01 admisión sellada y bytes/puntero; T02 schema1 legacy vs schema2 tipado; T03 tabla/version; **T04 trigger inesperado Y orphan FK rechazados**; T05 sidecars/symlinks/hardlinks; T06 Reality10/Aux4/puntero.

### Diff de código y límites
Commit funcional modificó exclusivamente:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/StagedSqlitePreflight.kt`: campo `rejectTriggers:Boolean=false`, chequeo `sqlite_master` en el mismo driver SQLite READONLY, sellado, ya protegido por quick_check y foreign_key_check.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`: activa el chequeo solo si `commercialEvidence2StagedDev`.
- `.github/workflows/faro-v5-s4-w04-f2b2a-sealed-typed-stage.yml`: evento de la ruta SQLite incluida.

No se cambiaron los seis tests, dependencias Gradle, schema1 legacy, reader F2B1, FBS2v6, ACTIVE cutover, pointer switch, Apply, pagos, V2 productiva, G15, main ni V4.4.
Algunos workflows históricos que se disparan por rutas compartidas tienen preflight de **otras unidades** y fallan por marker scope; no tomarlos como certificación positiva de una regresión ni afirmar CI global toda verde. El alcance certificado es solo el workflow F2B2A y guard general de protocolo SUCCESS.

### Continuidad
Cierre B7 solo de F2B2A. F2B2B: archivo FBS2 tipado autenticado Evidence2 (sin romper v6), luego F2B2C cutover/recovery durable y F2C Android, seguidos de aceptación integral. Estas unidades siguen OPEN/NOT_AUTHORIZED y exigen permiso propio. Writer debe liberarse tras actualizar pointer+permit. Política offline y V2 productiva OFF.
