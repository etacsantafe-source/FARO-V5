# FARO V5 · W05-A1C2C · Filas SQL13 de compensaciones en STAGED

## B1 — Delta de evidencia

Continúa la preauditoría FARO_S4_W05_A1C2_STOCK_UNDO_RECOVERY_B1_B5_PREAUDIT_20261125.md. A1C2A certificó el esquema SQL13. A1C2B tiene B7 PASS en 79dd1a83baff16a92b92ff69e16f1da64a767124, CI 45610919772, guardia 45610919673 y XML 18/18. Para la restauración tipada, capturar en una misma transacción las compras, recibos originales y compensaciones, incluyendo una recepción posterior que supera el acumulado histórico permitido en SQL12 pero conserva el físico neto.

## B2 — Reutilización

Reusar los validadores de columnas históricas, layout y versión, port de filas de SQL12 STAGED, SQLite STRICT, transacciones explícitas, checkpoint y readback nativo. Conservar la auditoría histórica P02/P03. Reusar semántica FBS2v10 de orden por revisión, máximo válido de cantidades y vínculo a compra confirmada. [SQLite transacciones](https://www.sqlite.org/lang_transaction.html), [SQLite WAL](https://sqlite.org/wal.html).

## B3 — Delta de diseño

Un port STAGED SQL13 específico captura todas las filas del owner en una transacción SQLite DEFERRED sobre archivo canónico sellado. Restore solo a destino inactivo vacío o con el ledger v8 exacto ya restaurado y sin compras ni compensaciones. Validar estructura y límites, consistencia temporal y suma física neta de cada compra frente al histórico completo. Reintento idéntico verifica; destino divergente rechaza. Persistir compras, receipts, compensaciones y revisión en una transacción IMMEDIATE. Verificar de nuevo antes del COMMIT y tras cierre, conservar archivo fuente. El lector SQL12 mantiene el tope bruto histórico y su versión exacta. El coordinador de cinco stores, aplicación DEV ACTIVE y binding Undo se certifican por separado.

## B4 — Rutas exactas

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationRowsPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationSchema.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt`
4. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationRowsPortTest.kt`
5. `.github/workflows/faro-v5-s4-w05-a1c2c-sql13-compensation-rows.yml`

## B5 — Aceptación

Ronda SQL13 de compra10, recibos7 y3, Undo7 y segunda recepción7 con historial original íntegro. Validar rollback conjunto, reintento idéntico, ledger restaurado exacto, ausencia de duplicación, revisión indebida, compensación cruzada o inválida, divergencia y alias/sidecars. Mantener puertos SQL12 exactos y sus validaciones. CI dirigida, XML original y guardia por SHA.
