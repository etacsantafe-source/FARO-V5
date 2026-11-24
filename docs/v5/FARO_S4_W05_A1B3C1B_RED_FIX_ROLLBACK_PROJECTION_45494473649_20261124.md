# FARO V5 · W05-A1B3C1B · RED_FIX rollback projection validation · 2026-11-24

Unidad `S4_W05_A1B3C1B_FBS2V8_SQL11_JOURNALED_CUTOVER_DEV`, rama `work/s3-night-20261102`, commit de código original `e836dd6e511e58eb99b6e98bcfb2a043bdcefb44`.
Preauditoría original `docs/v5/FARO_S4_W05_A1B3C1B_SQL11_JOURNALED_CUTOVER_B1_B5_20261124.md`, blob `2e39f4293f1466e65226387d5a60df0ebc360678`, guard 45493942595 SUCCESS. READY exact3 commit `fb28748bc1489918b74d61aa7bc48e453821cff7`, guard `45494090819` SUCCESS. Código original guard `45494473484` SUCCESS.

## B1 — Fallos REALES, CI/XML original

CI `45494473649`, job `138801167737`, COMPLETED FAILURE tras compilación correcta; preflight parent READY exact3 PASS. Artifact original `14967777673`, zip SHA256 `db239ad5750dbc4ec3761007cf28c04dcb6315314a387fa2da0c8549fd744b82`, descargado y SHA verificado. Inspección independiente JUnit5 XML `FaroV5Household11DevJournaledCutoverTest`: **8 tests, 2 failures, 0 errors, 0 skipped**. 6 casos PASS: switch normal, idempotent restart, crash después de pointer publish antes de journal, foreground gate, ciphertext/ticket mismatch, corrupt Reality/host/pointer. Casos FAIL exactos:
- `unhealthySql11ReopenRollsBackBeforeWritesAndPreservesOldGeneration()`: expected ROLLED_BACK pero BLOCKED, Kotlin test línea 221.
- `crashAfterRollbackPointerBeforeJournalAdvanceRecoversOldAuthority()`: expected ROLLED_BACK pero BLOCKED, Kotlin test línea 245.

## B2 — Análisis causal comprobado

El `ValidatedGenerationActivationCoordinator` realiza correctamente SWITCHED → ROLLBACK_PREPARED → ROLLED_BACK_POST_SWITCH y llama `verifyRolledBackRuntime`, que invoca `projections.reconcile(kind,previousGenerationId)` **también al volver al origen**. La implementación nueva de `FaroV5Household11DevJournaledCutover` restringió su callback DEV a `id == ticket.targetId`, por lo que rechaza justamente la reconciliación legítima a `ticket.origin.generationId`. El wrapper `guarded` transforma la excepción en BLOCKED. No se trata de un fallo del motor general de journal ni de los tests; corregir el callback específico.

## B3 — Decisión de reparación

A ELEGIDA: callback DEV NOOP explícito para ambos IDs válidos (target elegido u origen recuperado), validación adicional del puntero completo `ticket.selected` o `ticket.rolledBack`, del journal/identidad y del stage sellado bajo gate y root lock. Sin proyección OS real ni permisos adicionales. B descartada: eliminar el chequeo/ignorar excepciones (perdería fail-close). C descartada: cambiar `ValidatedGenerationActivationCoordinator` global o debilitar el rollback.

## B4 — Dos rutas exactas RED_FIX

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevJournaledCutover.kt`: en `projections` aceptar y comprobar `targetId` bajo full selected pointer, y `origin.generationId` bajo full rolledBack pointer. Conservar `requireSealed(ticket)`. Denegar cualquier tercer ID, fuente incoherente o host revocado.
2. `.github/workflows/faro-v5-s4-w05-a1b3c1b-journaled-cutover.yml`: admitir commit RED_FIX exactamente dos rutas, manteniendo preflight real parent y **las mismas ocho pruebas obligatorias**. Permiso READY conserva las 3 rutas originales, `last_code_review.result=RED_FIX` con este documento.

No modificar prueba, datos, Android, motor CAS, Stage SQL11, V2 productiva ni main.

## B5 — Matriz de regresión

Repetir **las mismas 8 pruebas**, incluyendo ambos fallos de rollback y las 6 ya aprobadas, XML real con 8/8, guard SUCCESS, per-owner fingerprints, pointer full, journal phases, no doble switch, no stock Apply/Undo. Revalidad estricta de C1A ticket y FBS2v8; resultados falsos o faltantes no se declaran PASS.

## B7 — Estado del primer intento

**RED_FIX — NO PASS** por los dos casos de rollback. No cambió generación productiva ni instaló V2/Auto/pagos. Se mantiene A1B3C1B abierta hasta nueva CI + XML y B7 independiente; A1 completo no se declara.
