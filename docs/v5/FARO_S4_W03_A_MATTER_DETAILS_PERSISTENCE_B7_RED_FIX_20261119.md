# FARO V5 · W03-A · B7 RED_FIX · aserción de test invertida

Fecha: 2026-11-19. Unidad `S4_W03_A_MATTER_DETAILS_PERSISTENCE`. Código `3446cc2dbde6134af3f18cf6f2f28c0c9b272fb8`. **B7 NO PASS; RED_FIX SCOPED y test pendiente de CI nueva.**

## B1
- Permiso exact-route previo `358ea7ad6e2cdc87f6f75ed79f2d288267ef6af2`; guardia del código run [44534211567](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44534211567) SUCCESS, preflight 8/8 PASS en job.
- Workflow dirigido W03-A [44534211526](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44534211526) FAILURE: `:owner:matters:test` tuvo 6 pruebas, 1 FAIL. La excepción `AssertionFailedError` en `MatterModelTest.kt:107`. Los siguientes tests Reality/Generation quedaron SKIPPED, no afirmar su resultado.
- Código funcional de modelo/SQLite/codec compiló hasta owner Matter; fallo de **prueba lógica** en aserción de preservación de prefijo. No hay evidencia de regresión SQLite todavía, queda pendiente.

## B2
- El contrato `MatterDetails.retainsHistoryOf(previous)` está diseñado como la semántica append-only de W02 Objectives: el estado **nuevo** debe conservar el prefijo del **anterior**. V4.4 exige conservar historial, no invertir la relación. Se reutiliza contrato ya auditado; NO reabrir modelo.

## B3
- La comparación con secuencias/prefijos append-only y el patrón ObjectiveDetails de W02 confirma la dirección: `new.retainsHistoryOf(old)` true; `old.retainsHistoryOf(new)` false. Alternativas: A corregir solo llamada del test **USAR**; B cambiar implementación y permitir pérdida del prefijo **DESCARTAR**; C omitir prueba/hacerla verde forzado **DESCARTAR**. Sin licencia ni nueva dependencia.

## B4
- Corregir exclusivamente `MatterModelTest.kt` línea 107, con `old.copy(history = expanded).retainsHistoryOf(old)`. Conservar prueba negativa `old.copy(history = emptyList()).retainsHistoryOf(old) == false`. No tocar lógica/DB/backup ni tests ya certificados.
- Reducir permiso RED_FIX a ruta exacta de test; sigue misma unidad `S4_W03_A_MATTER_DETAILS_PERSISTENCE` y `FARO-UNIT` en commit. La preauditoría original B1–B5 y sus SHA siguen vigentes; el problema es solo la dirección del assertion.

## B5
- T-A05 verificar que nueva secuencia conserva prefijo y truncada NO; que el uso inverso falle como corresponde. La corrección no relaja casos negativos.
- Mantener T-A01..A06 de original: código nuevo no se certifica hasta ejecutar toda la suite W03-A y verificar XML de Reality/G4/FBS2. No permitir cambios de otras unidades ni B7 global adelantado.

## B7
- Estado observado: **RED_FIX** del commit `3446cc2dbde6134af3f18cf6f2f28c0c9b272fb8`; guardia SUCCESS pero CI W03-A FAILURE, no hay XML completo 11/11 ni B7 PASS.
- Causa reproducida en código exacto: llamada `old.retainsHistoryOf(old.copy(...))` invierte candidato/previous; la función correctamente devuelve false, la prueba esperaba true. Corrección mínima un único archivo y relanzamiento automático workflow.
- Requiere checkpoint documental/guardia SUCCESS, READY mismo unit con `last_code_review.result=RED_FIX`, preflight exacto y código limitado; después CI W03-A completa y B7 final. G15 NOT_RUN, V2 productiva/Auto/Noche OFF, writer RELEASED durante CI.
