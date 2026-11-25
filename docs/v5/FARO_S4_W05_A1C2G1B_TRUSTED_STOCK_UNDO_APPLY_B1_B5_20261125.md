# FARO V5 · W05-A1C2G1B · Trusted Review→Confirm→Apply stock Undo N-016

Fecha 2026-11-25. Rama `work/s3-night-20261102`, baseline `a6e2d68db376488a803a70acc05e6ba095493839`.
**B1–B5 PREAUDITADOS; NO_READY mientras guardia no esté verificada; B6 NO ejecutado, B7 NO demostrado.**
G0 y G1A no se repiten. Priorizar B6 directo tras guardia y nuevo permiso exacto.

## B1 — Evidencia en código actual y misión

- **G0** SQL13 compensation/internal DEV B7 18/18, CI 45639876681, guard 45641157003, documento `FARO_S4_W05_A1C2G0_B7_PASS_SQL13_COMPENSATION_DEV_20261125.md`: `RealityHousehold13CompensationApplyPort` ofrece posición y `compensate`, escribe en la misma `RealityTransactionScope`, valida revisión y evita doble Undo. Ya existe la sesión `FaroV5GenerationStoreGraph.openActiveHousehold13DevForUndoHost(root,selected,gate,planVerifier,ownerAdmission)` que exige lock raíz y `hasCompletedSelection` SQL13 Evidence2 y expone `workspace`, `attempts`, `receipts`, `transactions`, `commits`, `dependencies`. No se requiere nuevo opener.
- **G1A** admisión tipada B7 PASS 18/18 (10 nuevas + 8 regresión); CI `45642672627`, guard de cierre `45643360000`, SHA funcional `b911e9bce5bd233073ae64dd271f1869a748a6d9` y cierre `a6e2d68db376488a803a70acc05e6ba095493839`: `HouseholdStockUndoDevAdmission` verifica `UndoExecuteIntent`, `HouseholdStockReceiveReview`, catálogo N-016, recepción original, receipt físico `receive.intentKey`, nueva clave de Undo, posición SQL13, neto, principal, revisión, grant fresco y revalidación.
- `S4W05StockReceiveDevApplyHost.kt` aporta patrón de creación Workspace Review, confirmación explícita, CAS, `ApplyTransactionFinalCommitGate`, `ApplyRetryReceiptGate`, `ReviewedConversationApplyCoordinator` y `reconcileCommittedOnly`. Su operación N-035 **no es** el permiso N-016. `S4W04HouseholdNeedApplyHost.kt` posee patrón de source `workspace.loadCommitted(context)` más receipt original, binding `undo.execute`; para stock hay que aplicar el mismo principio con nueva admisión, no copiar permisos de Needs.
- `ConversationWorkspaceStore.loadCommitted(context)` devuelve solo revisión histórica COMMITTED del contexto exacto y envelope validado contra fingerprint mediante `CanonicalPreparedPlanEnvelopeVerifier`. En selected SQL13 deben además coincidir `session.receipts.find(sourceTx)` con receipt original y `session.attempts.find(sourceTx).state == COMMITTED`.
- **Dos IDs diferentes:** el recibo físico de stock `HouseholdStockReceiveReview.request.intentKey` (= `RealityHousehold11ReceiptRow.receiptId`) y el `ExecutionReceipt.receiptId` de la transacción Apply original NO se equiparan. La fuente es el plan N-035 COMMITTED del Workspace; el destino de G0 `position(receiptId)` es el físico. Los dos deben corroborarse.
- `UndoExecutePayload` de kernel ya es codec UTF-8 estricto con `sourceReview`, receipt Apply, sourceOperation, original payload, receipt físico como `targetObjectId`, cancelAt. No crear otro codec ni operación. Catálogo N-016: `undo.execute`, `undo-coordinator`, REALITY_MUTATION, BINDABLE.
- La fuente de N-035 corresponde al SQL12 anterior y puede referir una generación/sequence distinta del SQL13 seleccionado. No rechazar por desigualdad de ID a secas; corroborar presencia de plan+receipt+ledger histórico migrados al grafo SQL13 seleccionado y completitud del ticket de cutover. Si no se puede probar, STOP.

## B2 — Reutilización V4.4/V5 y delta mínimo

- V4.4: preservar AtomicApply, 1 effect/1 receipt, PREPARED/COMMITTED, idempotencia, Undo con evento inverso y startup/recovery; referencia `FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar ningún store V4.4.
- V5: reutilizar `FaroV5GenerationStoreGraph.Household13StockUndoDevSession`, `HouseholdStockUndoDevAdmission` (G1A), `UndoExecutePayload`, `HouseholdStockReceiveReview.decode`, `CanonicalPreparedPlanEnvelopeVerifier`, `workspace.loadCommitted`, `ReviewedConversationApplyCoordinator`, `ApplyRetryReceiptGate`, `ApplyPreflightGate`, `ApplyBindingResolutionGate`, `ApplyTransactionExecutor`, `ApplyTransactionFinalCommitGate`, `ApplyAttemptLookupPort` y `ExecutionReceiptLookupPort`.
- Delta G1B: un host de aplicación **aislado** que conecta source trusted + G1A + G0 en una nueva revisión y nuevo Apply con receipt de compensación. No cambios a SQL13, FBS2v10, modelo de compra o catálogo.

## B3 — Alternativas externas investigadas y descartadas

- SQLite oficial `https://www.sqlite.org/lang_transaction.html`: writer único y transacción IMMEDIATE, no efectuar commit en un binding hijo; `https://www.sqlite.org/wal.html`, `https://www.sqlite.org/pragma.html`: WAL + synchronous FULL; **adaptado** por `RealityStore.run` ya existente.
- AndroidX Room `https://developer.android.com/reference/androidx/room/Transaction.html`: serialización de transacciones; **referencia** sin dependencia nueva. G1B no crea ni usa un segundo SQLite owner.
- Contratos propios `UndoExecutePayload`, `ApplyRetryReceiptGate` y `ReviewedConversationApplyCoordinator` son preferibles a idempotencia remota o cloud; no APIs pagas, no automatismo de IA, no pagos.
- Rechazado: introducir `stock.undo` propio fuera del catálogo, reactivar N-035 para Undo, hacer `DELETE` de recibo histórico, saltarse COMMITTED Workspace, asumir que fila física es receipt Apply, añadir permisos implícitos, ejecutar en background/Auto/Noche o usar un `receipt` desde el texto de chat.

## B4 — Rutas exactas propuestas para la unidad G1B

1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHost.kt` (nuevo): root/lease SQL13, source envelope COMMITTED + Reality receipt/attempt, admisión G1A, preparación Review sin side effects, consentimiento N-016, autorización APPLYING, binding G0, clock nuevo, reconciliación COMMITTED/UNKNOWN, denial fail-closed.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHostTest.kt` (nuevo): JUnit con un root DEV SQL13 seleccionado por cutover real y data+receipt+Workspace heredados; pruebas de origen COMMITTED, compensación 7→3, segundo receipt, duplicate/retry/reopen, stale revision, wrong owner/context, ABA/revocación y exception rollback. Al menos 8 casos dirigidos, sin fabricar autorización por fixtures.
3. `.github/workflows/faro-v5-s4-w05-a1c2g1b-stock-undo-apply.yml` (nuevo): preflight de exactamente tres rutas desde parent READY, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W05StockUndoDevApplyHostTest` y regresión `S4W05StockReceiveDevApplyHostTest`, XML originales `v5/app/build/test-results/**/TEST-...` cero errors/failures/skips y artefacto.

**Si la prueba del source COMMITTED revela falta real de API de generación, STOP antes de editar cuarta ruta**, registrar dependencia y preparar nuevo scope/permiso. No ampliar silenciosamente.

## B5 — Auditoría adversarial / contratos de salida (no pruebas ejecutadas)

- T01: fuente Workspace Review COMMITTED exacta + envelope fingerprint + Reality source receipt y attempt COMMITTED + origen `stock.receive` correcto → Review G1B pendiente; sin escritura SQL.
- T02: ausencia de source en Workspace aunque exista receipt Reality, falsificación de source plan, source en REJECTED o PREPARED → DENIED sin compensación.
- T03: receipt Apply original distinto del receipt físico; `UndoExecuteIntent.targetObjectId` apunta exactamente a `stock.receive.intentKey`. Confusión de IDs → DENIED.
- T04: N-016 grant/consent independiente de stock.receive, Context current foreground, principal y host epoch; revocación antes de última frontera → no COMMIT.
- T05: compra 10, recepción física 7+3, Undo de 7 → compensación event 7, neto 3; ledger original 7+3 intacto, revision++ y nuevo executionReceipt COMMITTED.
- T06: receipt físico ya compensado, intentKey repetido, stock consumido/neto menor al original, unidad/item mismatch, revision staleness → DENIED sin mutar, sin saldo negativo.
- T07: fuente SQL12 pasada al SQL13 por cutover journaled COMPLETED conserva proof sin aceptar ABA/pointer swap o ticket incompleto.
- T08: PREPARED crash antes de SQLite COMMIT: rollback completo sin compensación ni receipt nuevo. UNKNOWN con receipt COMMITTED: reconciliar primer resultado sin segundo efecto.
- T09: retry idéntico tras proceso nuevo: usar `ApplyRetryReceiptGate` y receipt existente; segundo Undo con nuevo idempotency key denegado por receipt físico compensado.
- T10: cambio de aprobación a APPLYING, cierre de host, root lease ocupado, revocación en `ApplyTransactionFinalCommitGate` → DENIED/RECOVERING sin bypass.
- T11: leer historia legacy original, compensación y 5-store snapshot/restart sin pérdida de información ni nuevo formato.
- T12: los XML de tests y build/guard pertenecen al SHA real, no a los anteriores; todos los tests 0 fallos/errores/omitidos. Android físico después (A1C3); main/V4.4, V2 productiva, stock real, pagos, Auto/Noche OFF.

**READY solo tras guardia documental SUCCESS**, evidence blob, permiso exact3, preflight parent y no mezcla de código/control; B6 y B7 deben ser explícitos. G1B no autoriza activación de producción. Luego A1C3 Android y A1C4 aceptación integral.
