# FARO V5 · W05-A1C1 · Recepción manual N-035 DEV

## B1 — Delta de evidencia

Continúa la preauditoría `FARO_S4_W05_A1C1_STOCK_RECEIVE_REVIEW_APPLY_B1_B5_PREAUDIT_20261125.md`. A1C0A certificó el contrato inerte N-035 y su preview. A1C0B3 certificó grafo y cutover de SQL12. A1C0B4 tiene B7 PASS en 3b1d1dee7e6ac8215efe26acb2d77a0c01b6342b, CI 45603410162, guardia 45603409875 y XML 8/8. La fuente confiable es la compra de origen MANUAL_EXPLICIT del mismo principal, leída en Reality12 ACTIVE bajo el puntero completo. Un backup no emite autorización de recepción.

## B2 — Reutilización

Conservar P02/P03 y reutilizar `HouseholdStockReceiveDevAdmission`, `HouseholdStockReceiptPreview`, `ReviewedConversationApplyCoordinator`, `AtomicApply` y `MaterialWriteGate`. Los grants de compra N-036 y de necesidades siguen separados de recepción N-035. Reutilizar el lease del root y la comprobación del cutover COMPLETED. [SQLite transacciones](https://www.sqlite.org/lang_transaction.html), [SQLite WAL](https://sqlite.org/wal.html).

## B3 — Delta de diseño

El Review fija principal, época foreground, puntero completo, referencia de compra, revisión de compra y del ledger, artículo, unidad, intent y cantidad. Apply vuelve a leer compra confirmada y receipts en la transacción SQLite, revalida el preview, verifica el CAS de la revisión singleton y escribe el evento append-only con intent único. Persiste el evento y recibo AtomicApply en el mismo COMMIT. Un reintento COMMITTED consulta el receipt durable. La proyección calcula el físico recibido y lo pendiente a partir del ledger con cantidades Long acotadas. El host abre un modo DEV con binding y puerto exclusivos de recepción, comparte la implementación del lease con compra y conserva read-only para los consumidores existentes. Sin cambio de esquema y sin compras, pagos ni Undo implícitos.

## B4 — Rutas exactas

1. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiveReview.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12StockReceiveApplyPort.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
4. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
5. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockReceiveDevApplyHost.kt`
6. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReceiveDevApplyHostTest.kt`
7. `.github/workflows/faro-v5-s4-w05-a1c1-stock-receive-dev.yml`

## B5 — Aceptación

Compra confirmada de 10, recepción explícita de 7 con saldo pendiente 3, segunda recepción 3, intent reintentado conservando un efecto, rechazo de cantidad excesiva y unidad incompatible, pérdida de autorización, compra de otro principal, revisión obsoleta y full pointer ABA. Prueba de dos planes sobre la misma revisión, rollback conjunto y conciliación tras COMMIT. Los casos de portabilidad SQL12/FBS2v9 ya certificados se conservan sin cambio de esquema; el recorrido autenticado integral corresponde a A1C4. CI y guardia por SHA, artefacto XML original. Android y Undo requieren sus permisos.

