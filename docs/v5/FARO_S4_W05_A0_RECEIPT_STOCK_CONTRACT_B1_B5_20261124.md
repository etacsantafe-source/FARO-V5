# FARO V5 · W05-A0 · Contrato de recepción física e inventario (B1–B5)

Fecha local: 2026-11-24 (Argentina). Unidad propuesta: `S4_W05_A0_RECEIPT_STOCK_CONTRACT_DEV`.
Checkpoint de partida: `a53e18f73c6420c46bc0bd876ff360fd0094cd37`. **Alcance de esta entrega: preauditoría documental B1–B5**, sin B6/B7, sin READY, sin mutación de Reality ni alta automática de stock.
Rama: `work/s3-night-20261102`; W04 SmartCompra GLOBAL LOCAL DEV cerrado; permiso W04 consumido/BLOCKED.
El presupuesto inicial para W05-A0 son **exactamente dos rutas de código nuevas y un workflow nuevo**, sujetos a permiso por rutas después de guardia:
- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiptPreview.kt`
- `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiptPreviewTest.kt`
- `.github/workflows/faro-v5-s4-w05-a0-receipt-preview.yml`

## B1 — Evidencia y brecha actual

- W04-F3G B7 PASS Android API35 1/1: `docs/v5/FARO_S4_W04_F3G_GLOBAL_LOCAL_DEV_B7_PASS_20261124.md`; CI `45371653822` SUCCESS y guard `45373679110` SUCCESS en el checkpoint. Esto acredita SmartCompra local DEV, **no** stock/Cocina, G15, main, Chat/S3 ni V2 productiva.
- W04 Household10 tiene `household_need_state` y `household_need` en `RealityHousehold10Schema.kt` (versión 10). No hay en esa DDL tabla de lotes, recepciones físicas, reservas o consumo.
- `HouseholdNeedModel.kt` ya modela cantidades enteras en **milésimos** y unidades tipadas `UNIT/PACKAGE/GRAM/KILOGRAM/MILLILITER/LITER`; reusar sin conversiones ocultas.
- P05 `S04/S05`, `PF21–PF25`; P07 `G05/G09`, `P7T04–07`; P08 `W05` contienen el alcance. Histórico no es prueba ejecutada de W05.
- Criterio binario A0: modelo de **previsualización inerte** puro y JVM, recibo explícito asociado a una compra, sin cambiar Reality, Finance o catálogos. A0 no certifica almacenamiento, idempotencia durable o recibos persistentes.

## B2 — Comparación V4.4/V5 y reutilización

- V4.4 L11–L15 distingue comprar, recibir, reservar, consumir y Cocina. Reutilizar el flujo funcional, **no** copiar `SmartCompraStore` o el runtime V4.4.
- V5 W04 ya certificó Household10, fuente comercial Evidence2, selección explícita y revisión local DEV. Reutilizar solo modelo de cantidad/unidad, identidad opaca, ACL/generation, revisión, receipts y writer único; ninguna observación de precio pasa a ser recepción ni saldo.
- `shopping.payment.record` es hecho comercial, `finance.entry.record` es dinero, `stock.receive` futuro es movimiento físico. No crear un séptimo owner, otra DB ni un writer distinto.

## B3 — Investigación externa verificable y decisión

- **Grocy** `https://github.com/grocy/grocy/blob/master/routes.php` y OpenAPI `https://github.com/grocy/grocy/blob/master/grocy.openapi.json`: mantiene operaciones explícitas separadas `/stock/products/{productId}/add`, `consume`, y transacciones/undo de stock; **REFERENCIA/ADAPTAR semántica**, no copiar PHP, runtime, dependencias, servidor ni código GPL/AGPL. Riesgo detectado: tratar compra/lista como incremento físico o consumir receta parcial sin confirmación.
- **KitchenOwl** `https://github.com/TomBursch/kitchenowl`: listas y recetas, backend Flask/cliente Flutter, AGPL-3.0/self-hosting. **REFERENCIA UX**, no adoptar infraestructura ni código; su lista de ingredientes no autoriza cambio en inventario.
- **SQLite** `https://www.sqlite.org/transactional.html`, `https://www.sqlite.org/wal.html`, y Android Room `https://developer.android.com/reference/androidx/room/RoomDatabaseKt`: **ADAPTAR al futuro B6 de persistencia**, una transacción/receipt durable y FULL WAL; no hacer dos escritores ni emular atomicidad desde UI. W05-A0 no introduce SQLite, requiere etapa posterior.
- Alternativa A: primer contrato inerte de recepción (elegida por alcance reducido); B: `stock += comprado` tras pago (DESCARTAR); C: ingesta automática por lector/lista/receta (DESCARTAR por identidad, permisos y falsos positivos).

## B4 — Unificación y alcance exacto A0

- `HouseholdStockReceiptPreview` debe recibir compra identificada, cantidad pactada tipada, suma de recepciones **confirmadas existentes** y una cantidad candidata. El preview retorna `recibidoActual`, `recibir`, `recibidoTrasConfirmar` y `pendiente`; no genera receipt ni escribe.
- Validar: clave de compra opaca no vacía, unidades exactamente iguales, milésimos positivos, suma sin overflow, cantidad ya recibida entre 0 y pactado, candidato no excede faltante. Cualquier inconsistencia produce rechazo explícito/STOP.
- A0 sólo calcula, no autoriza una recepción ni conserva idempotencia entre llamadas. El futuro W05-A1 debe introducir operación revisada y grant independiente por `stock.receive`, SQL, receipt idempotente, generation y backup; W05-A2 reservas/consumo; W05-A3 Cocina en SmartCompra. No crear tablas ni habilitar producto en A0.
- Conservar catálogo180 exacto; sin Finance, pagos, compras reales, IA autónoma, egress, S3/Chat ni G15 físico.

## B5 — Auditoría adversarial previa

Pruebas de JVM diseñadas (NO_EJECUTADAS en esta publicación):
1. Compra 10 unidades, recepción confirmada 0, candidata 7: posterior 7, pendiente 3; no suma 10.
2. Segunda recepción 3 sobre confirmado 7: posterior 10, pendiente 0.
3. Candidata 4 sobre ya recibido 7 de 10: rechazo por sobre-recepción.
4. Unidad `UNIT` frente a `PACKAGE`: rechazo, sin conversión.
5. Candidato 0/negativo y pactado/confirmado fuera de rango: rechazo.
6. Suma límite de `Int.MAX_VALUE` en milésimos sin overflow ni clamp silencioso.
7. Previsualización repetida de la misma entrada es determinista, no acumula; **no equivale** a idempotencia persistida.
8. Reserva futura, plan de receta y pago no son entradas del contrato ni producen mutaciones.
9. Generación/ACL stale, doble tap, reboot, restore, crash y Undo: **STOP/NO_COVERAGE_A0**, exigir B5/B6/B7 nuevos de W05-A1 antes de presentar stock físico activo.

**Decisión preauditoría:** diseño A0 apto como contrato inerte **pendiente guardia y permiso READY exact3**. En este commit NO hay autorización funcional, NO nuevas pruebas ejecutadas, NO B7. La guardia documental y emisión de permiso deberán usar el HEAD vigente posterior, y un B6 específico llevará código/pruebas/CI. Prohibido usar el permiso consumido de W04.
