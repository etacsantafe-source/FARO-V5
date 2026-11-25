# FARO V5 · W05-A1C2G0 · B7 RED_FIX tras fallo real de Kotlin

Fecha: 2026-11-25. Rama: `work/s3-night-20261102`.
Unidad original: `S4_W05_A1C2G0_SQL13_UNDO_DEV_WRITE_ADMISSION`.
Código rojo exacto: `8056a65959647a4ae58b0616d5c1f198ec1c84bc`.
Guardia de protocolo del código: `45637952691` — **SUCCESS** (no equivale a B7).

## B7 — Evidencia CI RED_FIX, NO PASS

- [CI 45637952977](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45637952977) terminó **FAILURE** sobre SHA exacto `8056a65959647a4ae58b0616d5c1f198ec1c84bc`, job `139288389063`. Preflight cinco rutas, Java y SDK PASS; `:store:reality:compileKotlin` falla; regresión de generación y verificación XML no se ejecutaron.
- Log original: `Kotlin compiler: CLASSIFIER_REDECLARATION`. El nuevo `RealityHousehold13CompensationApplyPort.kt` línea 40 declara `internal class RealityHousehold13CompensationRows`, nombre que ya existe como `data class RealityHousehold13CompensationRows` en `RealityHousehold13CompensationRowsPort.kt`. Error reproducible de símbolo; no implica fallo de la lógica SQLite.
- Una auditoría estática adicional detecta que el nuevo workflow arma la ruta de XML `ar.com.faro.v5.reality.*` cuando el paquete correcto del módulo reality es `ar.com.faro.v5.store.reality.*`. De dejarlo sin arreglar, una posterior ejecución con tests exitosos fallaría la comprobación XML. El paquete de generación sí es `ar.com.faro.v5.generation.*`.
- Este B7 es disposición **RED_FIX**, no certificación. Número de pruebas XML verificadas en esa CI: **0**. No repetir unidades históricas de A1.

## B6 RED_FIX propuesto, mismo contrato y alcances reducidos

Autorizar únicamente estas **tres rutas preauditadas de las cinco originales**:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationApplyPort.kt`: renombrar exclusivamente la implementación privada a `RealityHousehold13CompensationSqlWriter`, preservando interfaz, query, CAS, transacción, ledger y validaciones.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: usar el nuevo nombre de clase en el único constructor existente del puerto, sin alterar gates ni SQL.
3. `.github/workflows/faro-v5-s4-w05-a1c2g0-sql13-undo-write-admission.yml`: componer nombres XML por paquete exacto reality/generation en el validador de 18 suites; dejar el chequeo de permiso de cinco rutas, la CI y sus regresiones originales.

No modificar `FaroV5GenerationStoreGraph.kt`, los test Kotlin, tablas, migraciones, otros módulos, ni el contrato original. El commit funcional de reparación debe tener el mismo trailer `FARO-UNIT:S4_W05_A1C2G0_SQL13_UNDO_DEV_WRITE_ADMISSION`.

## B1–B5 vigentes, B7 aún pendiente

La preauditoría original `FARO_S4_W05_A1C2G0_SQL13_UNDO_DEV_WRITE_ADMISSION_B1_B5_20261125.md` mantiene sus B1–B5 (guardia previa `45636259619` SUCCESS). No se abre otra unidad y no se repiten pruebas certificadas. Tras esta auditoría documental, exigir nueva guardia y un permiso READY explícito exact3 para RED_FIX, luego CI/BUILD/XML reales, y documentar B7 final solo si SUCCESS. Mantener Moto G15 NOT_RUN, V2 productiva OFF, Apply productivo OFF, stock real OFF, pagos y Auto/Noche OFF.
