# FARO V5 — S4 W04-F1 · Coordinador de lectura histórica autorizada · B7 PASS
Fecha: 2026-11-22. Unidad: `S4_W04_F1_AUTHORIZED_HISTORICAL_READ_COORDINATOR_DEV`. Rama `work/s3-night-20261102`.

## B7 — PASS_SCOPED_DEV_JVM_READ_ONLY

**Evidencia previa:** B1–B5 `docs/v5/FARO_S4_W04_F1_AUTHORIZED_HISTORICAL_COORDINATOR_B1_B5_20261122.md`, blob `ac70446ee71cd2d5a46dd556c006e72fc4677e1b`; guardia preaudit `45052343449` SUCCESS. Permiso READY initial `8c7bc77fbff0ecd39067b3d9e4d3634033cd4b56`, guardia `45052448714` SUCCESS. Código inicial `03be898c99605b2c51abdc0dc8c540d165cebe75` sobre tres rutas exactas: coordinador JVM, test JVM y workflow específico. Guardia `45052666972` SUCCESS.

**Auditoría RED_FIX real, sin ocultar fallos:**
- Primer CI `45052667038` FAILURE: workflow ejecutaba Gradle en dos líneas físicas y la tarea no se ejecutó; 0 tests. Documento `FARO_S4_W04_F1_B7_RED_FIX_SHELL_20261122.md`, blob `62871ad8d55e395f949265a6278be19940f146f2`. Disposición formal con guardia `45053159678` SUCCESS; reautorización corregida con guardia `45053370629` SUCCESS. Commit de reparación SOLO workflow `1effa68fc8e9378a954d26576f7202a5e0394ec3`.
- Segundo CI `45053465765` ejecutó realmente las ocho pruebas: 7 PASS, 1 FAIL `t07MissingOtherProductIsPartialNeverZero()`, `NoSuchElementException` al asumir `view.lines.first()` en `Fixture.choose()`. El orden canónico coloca `need-arroz` antes de `need-yerba` y Arroz no tiene historial. Código productivo compilado. Artefacto `14766980716`, ZIP SHA256 `72a65ab655dd1659a8b1421ea46a4f0c732dfca51605dd6cbe0c81c323dd8c78`. Documento `docs/v5/FARO_S4_W04_F1_B7_RED_FIX_T07_SELECTION_20261122.md`, blob `b9c15b550b4f3c9c536318e8697db6a96e9a9f3e`, y disposición `410bec2d1fe3183d5f4708ff40360a05e4dc7f23` con guardia `45054153341` SUCCESS.

**Reparación final aislada:** permiso SAME UNIT READY exact tres rutas en `0681993e83d8b59fd396e9f99641c8f07015ba11`, guardia `45054355294` SUCCESS. Código final `a748c4c13e23f1ad826cda01a7fa4990500cbcce` cambia ÚNICAMENTE `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04HistoricalAuthorizedReadCoordinatorTest.kt`: selecciona `view.lines.single { it.needId == need.id }` y preserva los otros siete tests, el coordinador y el workflow.

**Resultados independientes del código final:**
- Guardia exact SHA [45054446084](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45054446084) COMPLETED SUCCESS.
- CI dirigida [45054446092](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45054446092) COMPLETED SUCCESS, job `137306470524`. Preflight READY parent PASS, Java21, SDK37, Gradle `BUILD SUCCESSFUL in 4m 19s`, marker `W04_F1_JVM_EXPLICIT_OBSERVATION_8_8_PASS`.
- Artifact `14766509616`, `w04-f1-historical-read-xml`, ZIP SHA-256 `ea8b6025e22b62a0e3c214ff610c84130319283126b931032c4b533d2db48f71`. **Inspección independiente** de ZIP y XML: un único archivo `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04HistoricalAuthorizedReadCoordinatorTest.xml`, SHA256 XML `16e4750d3e0c6ac94d7f431e9e9707ec8a0b77e4e58d723e30d5a86ddecbf35f`, `tests=8, failures=0, errors=0, skipped=0`; ocho nombres JUnit distintos.

**Qué certifica:** F1 compone exclusivamente lectura histórica con selección humana de observación exacta, grants Household y Evidence inyectados por host confiable, asOf/fence/clock, validaciones D1→D2A→D2C1→D3A→D3B→D4 y relectura final. Nunca elige automáticamente menor precio; faltantes permanecen parciales, sin tratarse como importe cero. NO crea store, Android Activity, host de producción, Finance, pagos, stock, Apply, credenciales ni network egress. No se repitieron suites de D0–D4/E.

**Límite crítico de F1:** Sus callbacks/fuente se prueban en fixture JVM con Evidence2 tipada STAGED; **NO EXISTE AÚN MONTAJE ACTIVE tipado Household10 + Evidence2 bajo mismo lease**. La API `FaroV5GenerationStoreGraph.openActiveHousehold10DevForApplyHost` no habilita `commercialEvidence2StagedDev`, que expresamente requiere `!activeGeneration`. F2 deberá definir acceso tipado ACTIVE mediante migración/cutover/owner autorizados y revalidación real, sin segundo SQLite opener ni falsa actualidad. F3 hará la conexión Android y aceptación integral. **W04-F GLOBAL sigue OPEN**, W04-E renderer DEV conserva sus 4/4.

Se consume READY → BLOCKED; B6 DONE_VERIFIED y B7 PASS_SCOPED_DEV_JVM_READ_ONLY. Checkpoint final y writer CAS RELEASED se confirman con guardia final del commit documental. Sin S4 global PASS, G15, V2 productiva ni pagos.
