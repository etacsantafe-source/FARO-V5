# FARO V5 — W05-A1C2G1B B7 RED_FIX2 · 2026-11-25

**Unidad:** `S4_W05_A1C2G1B_TRUSTED_STOCK_UNDO_APPLY`
**Código RED observado:** `bf9f2d079e67b5f6f399324bd4bae5564a40e77a`
**Límite:** mismas 3 rutas exactas (host/test/workflow), sin tocar G0, G1A, producción ni contrato.

## B7 — Evidencia de fallo real, segunda ejecución

La guardia `45663321599` sobre `bf9f2d079e67b5f6f399324bd4bae5564a40e77a` terminó **SUCCESS** y validó el RED_FIX del anterior código `84ceeda8`.
CI dirigida G1B `45663321658` terminó **FAILURE**. XML original en artefacto `15046442106`, inspeccionado: `S4W05StockUndoDevApplyHostTest` 9 tests / 5 failures / 0 errors / 0 skipped; regresión `S4W05StockReceiveDevApplyHostTest` 8 tests / 0 failures/errors/skipped. 17 totales, **12 PASS, 5 FAIL**, los cinco esperaban `Committed` pero observaron `RecoveryRequired`. Compilación JVM/Android terminó, falló JUnit. No publicar PASS.
Las cinco fallas son las mismas que el CI previo `45647661813`: Undo 7/10, doble Undo, rollback de G0, stale revision y recovery interrupt.

La primera corrección eliminó lecturas de Workspace desde `UndoBinding.execute` y `finalGate`, pero **no resolvió el fallo**. La causa concreta de `RecoveryRequired` permanece por determinar: puede venir de `afterBeginApply`, `ApplyTransactionExecutor.Rejected`, `ApplyTransactionExecutor.RecoveryRequired` o reconciliación posterior; el framework convierte varias causas en el mismo resultado. NO adjudicar un motor SQL sin evidencia.

## B6 — Protocolo de diagnóstico acotado

- Instrumentar **temporalmente, solo en host DEV**, una etapa interna no sensible (no receipt IDs, tokens, plan ni principals) que marque entrada y salida de `afterBeginApply`, `UndoBinding.execute`, `compensation.compensate` y `finalGate`.
- La suite JUnit dirige la ruta de éxito fallida a un mensaje diagnóstico con etapa, estado del Review y saldo visible, manteniendo la prueba **fallida** hasta que el comportamiento sea verdadero. Los casos no dan autoridad a la ejecución.
- Usar el resultado real para una corrección causal; retirar todo el diagnóstico temporal antes del B7 final. Si es un problema de `afterBegin`, no desactivar el approval ni saltar verificación de Workspace; corregir la comparación o el estado.
- Mantener `ApplyTransactionFinalCommitGate`, `RealityTransactionScope`, CAS de SQL13, consentimiento independiente N-016, root lock y receipt original COMMITTED. Ejecutar 17/17 sin skips y guard/CI exact SHA, XML originales y artefacto.

## B4 — Mismas rutas permitidas

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHost.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHostTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1c2g1b-stock-undo-apply.yml`

G1B NO cerrado. G1A B7 PASS 18/18, G0 B7 PASS 18/18 intactos. Writer CAS sin robo. V2 productiva, pagos, stock real, Auto/Noche OFF. Moto G15 NOT_RUN.
