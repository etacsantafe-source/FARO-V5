# FARO V5 — W04-D2A · B7 RED_FIX 2026-11-21

Unidad `S4_W04_D2A_OBSERVED_BUDGET_DEV`. Rama `work/s3-night-20261102`. Cambio funcional inicial `7410f26599fb55254a8524e7f1d1c6c39a36be25`: exactamente tres rutas permitidas (motor, 12 tests y workflow), B1–B5 READY vigente y guardia `44899047034` SUCCESS con preflight EXACT3.

## B7

**RED_FIX, NO PASS**. CI dirigida `44899047048` concluyó FAILURE en job `136830825648`. `:app:compileDebugKotlin` falló con **SMARTCAST_IMPOSSIBLE** en `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudget.kt:126`: Kotlin no hace smart cast de `terms.presentationQuantityThousandths` de tipo `Long?` porque es propiedad pública de otro módulo. Java21, Android SDK37, checkout y preflight pasaron. No se ejecutó JUnit; XML 12/12 **NOT_RUN**; upload-artifact también falló por ausencia de XML. Advertencias preexistentes de otros módulos no causaron el fallo.

### Corrección mínima autorizada por RED_FIX
Únicamente **una ruta de las tres permitidas**: `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudget.kt`. Capturar la cantidad nullable en variable local de Kotlin antes de exigir `> 0L`, preservando rechazo de null/0/negativa y sin modificar el contrato del estimador. No tocar D0, D1, Evidence2, Finance, tablas, Android físico ni workflow/tests.

Revalidar HEAD y writer CAS; nueva guardia del commit documental y preflight exacto del **parent READY** antes de cambiar código. El siguiente commit funcional debe conservar `FARO-UNIT:S4_W04_D2A_OBSERVED_BUDGET_DEV` y diferir sólo en la ruta del motor. CI dirigida nuevamente, luego B7 independiente con XML exacto; no declarar PASS antes. Writer RELEASED entre ejecuciones CI. V2 productiva OFF.
