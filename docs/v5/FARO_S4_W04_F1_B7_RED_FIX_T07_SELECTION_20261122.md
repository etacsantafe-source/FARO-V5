# FARO V5 · W04-F1 · B7 RED_FIX2 — T07 selección por ID, no por orden
Fecha 2026-11-22. Unidad SAME: S4_W04_F1_AUTHORIZED_HISTORICAL_READ_COORDINATOR_DEV.

## B7 — RED_FIX, NO PASS
Después del primer RED_FIX de comando Gradle (documentado en `docs/v5/FARO_S4_W04_F1_B7_RED_FIX_SHELL_20261122.md`), el commit funcional 1effa68fc8e9378a954d26576f7202a5e0394ec3 cambió **solo** `.github/workflows/faro-v5-s4-w04-f1-historical-read-coordinator.yml`. Guardia exacta 45053465778 COMPLETED SUCCESS. CI JVM 45053465765 COMPLETED FAILURE; compilación del motor y del test `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin` PASS; ejecución `:app:testDebugUnitTest` REAL terminó con **8 casos, 7 PASS, 1 FAIL, 0 errores, 0 skipped**.
Artefacto JUnit `14766980716`, ZIP SHA-256 `72a65ab655dd1659a8b1421ea46a4f0c732dfca51605dd6cbe0c81c323dd8c78`; XML SHA-256 `6a554676d3264d02bc0c0dbe381d54dcc2a5b2692c402af6236dd18eacf5878f`. Se descargó e inspeccionó el XML. Caso fallido `t07MissingOtherProductIsPartialNeverZero()`, `java.util.NoSuchElementException: Collection contains no element matching the predicate` en `S4W04HistoricalAuthorizedReadCoordinatorTest.kt:197`.

**Causa comprobada:** el fixture `Fixture.choose()` selecciona `view.lines.first()`, lo que presume que Yerba ocupa el primer lugar. En T07, la necesidad `need-arroz` se ordena primero por ID en D0; esa primera línea no tiene observaciones, entonces `history.single { merchant == "Mercado A" }` lanza NoSuchElementException. No falló la política del motor ni existe prueba de falla en sus siete casos que pasaron. La expectativa del fixture era equivocada.

## B5 — Corrección mínima y prueba falsable
1. Cambiar SOLO `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04HistoricalAuthorizedReadCoordinatorTest.kt`: en `Fixture.choose()` reemplazar la obtención posicional `view.lines.first()` por **`view.lines.single { it.needId == need.id }`**. Mantener la elección humana explícita por observación/merchant y todas las otras aserciones, incluida falta de observación de Arroz.
2. Preservar `S4W04HistoricalAuthorizedReadCoordinator.kt` íntegro, workflow F1 ya corregido, D0–D4, E, esquema, manifest, owners, UI y archivos certificados. No hacer selección automática de un precio menor, ni transformar faltante en costo 0.
3. Disponer este B7 como RED_FIX y consumo de permiso anterior BLOCKED **en el mismo checkpoint documental**; después guardia protocolar SUCCESS, y emitir READY SAME UNIT exact3 independiente con B6 `READY_TO_IMPLEMENT`, B7 `PENDING_FINAL_AUDIT`, `last_code_review.result=RED_FIX` y referencia SHA/blob de este documento.
4. Reejecutar CI F1 exact SHA con 8 pruebas JUnit5 distintas. Descargar XML, exigir `tests=8,failures=0,errors=0,skipped=0` y guardia de código SUCCESS. Si aparece nueva falla, volver a registrar RED_FIX; NO suponer PASS.
5. W04-F2/F3 global no se activa por esta corrección. V2 productiva, Apply, pagos, G15, stock, redes, main y V4.4 fuera de alcance.
