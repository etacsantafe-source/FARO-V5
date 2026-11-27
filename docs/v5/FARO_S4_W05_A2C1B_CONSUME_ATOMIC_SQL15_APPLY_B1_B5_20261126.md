# FARO V5 — W05-A2C1B · stock.consume SQL15 writer transaccional, sin host · B1–B5

**Unidad:** `S4_W05_A2C1B_CONSUME_ATOMIC_SQL15_APPLY`. **Predecesor:** A2C1A B7 PASS DEV (8/8), código `3885084a6447febde9586da2c5a02414e3c4aa40`, auditoría `docs/v5/FARO_S4_W05_A2C1A_B7_PASS_CONSUME_REVIEW_DEV_20261126.md` blob `2464bfc1bd8d711536cf4717f5da9225c4f76d71`.
**Base reconciliada:** HEAD `de43e37b2c3db658841a0b884ad3d320f31e1c17`, guardia de reparación 45926448108 SUCCESS, escritor RELEASED al entrar. Documento local A2C1B del ZIP revisado con código vigente.
**Estado:** preauditoría DOCUMENTAL A2C1B; B6 NOT_RUN, B7 NOT_RUN, permiso A2C1B BLOCKED/NON_EXISTENT hasta publicación de guardia y commit READY separado. No habilita stock real ni consumo reservado.

## B1 — Evidencia verificable y brecha concreta

- `RealityHousehold15ConsumptionSchema.kt` (blob `fd0b95087eef386ed8acbdf2613225c0cee41917`) crea tabla STRICT append-only `household_stock_consumption_event`, eventos CONSUME/CONSUME_UNDO, revision/event_id/intent_key únicos y triggers de protección.
- `RealityHousehold15ConsumptionRowsPort.kt` (blob `4af7a385640977f6e984d5a49878926b7ad60287`) captura las filas SQL15, valida historia y ledger global, prohíbe `reservationRef != null` e impone `0 <= reserved <= physical` sobre eventos ordenados.
- `RealityHousehold14ReservationApplyPort.kt` (blob `16e35d467bb8a348eb9c5c6ecd3580a3f0e6f6fc`) muestra writer SQL14 con `position()/apply()` dentro de la transacción `RealityStore`, sin BEGIN/COMMIT alternos.
- `RealityStore.kt` (blob `11f04794d9890a92070e41095f50101f08594403`) mantiene SQL15 ACTIVE **estrictamente read-only** con `householdActive15DevReadOnly`. Su guardia impide actualmente un writer SQL15; habilitar solo una rama DEV explícita y aislada en esta unidad, jamás convertir la lectura actual en escritura general.
- `HouseholdStockConsumptionReview.kt` (blob `da763b477078d01645416eef5045b542bc5930cf`) fija request canónico con `sourceRef`, `intentKey`, generation, principal, epoch y revisión. `HouseholdStockConsumptionDevAdmission.kt` (blob `d878c7b2430f340478f66be25e47656b3eadcb0f`) exige consentimiento, descriptor N-035, posición actual y fuente autenticada. **No existe** todavía un executor SQL15 de consumo efectivo dentro del alcance certificado.
- Catálogo N-035 `stock.consume`: `REALITY_MUTATION`, owner `household`, `BINDABLE`. No crear nueva operación `stock.consume.undo` por inferencia.

## B2 — Comparación V4.4 y delta mínimo

Matriz `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`, R12/R13 (blob `4b9789004459eed6e95bc4d4f8032ee3b4c9f340`): stock físico no equivale a reserva ni a compra/pago; receta/Chat no hacen consumo automático. ADAPTAR patrón SQL14 de writer + `RealityStore` / SQL15 rows para una sola transacción; NO duplicar owners, no rehacer A2C0 cutover/FBS2v12 ni retroportar ejecutores V4.4.

## B3 — Alternativas y referencias externas documentadas

Alternativas evaluadas en el paquete A2C1: (a) append-only SQL15 en transacción única `RealityStore` (ADAPTAR), (b) UPDATE directo del saldo (DESCARTAR por perder traza de origen), (c) saga entre varios owners para una sola mutación (DESCARTAR por atomicidad). Referencias primarias: SQLite [transacciones](https://www.sqlite.org/lang_transaction.html) y [WAL](https://www.sqlite.org/wal.html); Android [offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first) y Room [@Transaction](https://developer.android.com/reference/androidx/room/Transaction.html). Grocy [OpenAPI](https://github.com/grocy/grocy/blob/master/grocy.openapi.json) solo como REFERENCIA de separación entre stock, reserva, consumo y undo, sin dependencia ni servicio externos. Probar BUSY/rollback en vez de asumir COMMIT garantizado. Sin APIs pagas.

## B4 — Diseño limitado y cuatro rutas CANDIDATAS

Writer `RealityHousehold15ConsumptionApplyPort` con `position()` en lectura DEV confiable y `apply(review, recordedAtMs, scope)` bajo `RealityTransactionScope` ya activo. `RealityStore` deberá tener flag **exclusivo DEV** para SQL15 activo, negado por defecto y condicionado al mismo `materialWriteGate` y al fence de generación; no abrir segundo connection/BEGIN/COMMIT. Ninguna nueva ruta se agrega durante B6 sin reauditar B1–B5 y nuevo permiso.

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionApplyPort.kt` (nueva)
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` (existente, delta mínima)
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionApplyPortTest.kt` (nueva)
4. `.github/workflows/faro-v5-s4-w05-a2c1b-consume-atomic.yml` (nueva)

**Regla de sourceRef:** para B6 usar únicamente un `purchaseRef` auténtico de compra explícita `MANUAL_EXPLICIT` con recepciones efectivas del mismo principal/item/unidad; reconstruir saldo de esa fuente a partir de sus receipts, compensaciones y consumos previos que usen esa misma sourceRef. Rechazar fuente que solo está comprada/pagada/no recibida, refs ajenas, ambigüedad o ausencia de procedencia. Debe ser compatible con el `sourceRef` del Review A2C1A; si la fuente auténtica no puede probarse, STOP y actualizar B4/B5 antes de B6.

Antes del INSERT validar active SQL15 COMPLETED, material fence/ACL, consent y contexto Review revalidables en nivel host futuro, revision global actual, `quantity in 1..Int.MAX_VALUE`, `quantity <= physical-reserved` y <= saldo de fuente, sin colisión de IDs/intent/revision ni `reservationRef`. Insertar CONSUME append-only y verificar readback SQL15 con historia heredada intacta; repetir intent no puede emitir otra fila.

## B5 — Negativas exigidas antes de B7

- **B01** Físico 7, reservado 2, consumo 4 auténtico => físico 3, reservado 2, disponible 1.
- **B02** Consumo 6 con disponibilidad 5 => rechazo, cero filas nuevas.
- **B03** Duplicado intentKey/eventId => rechazo o recuperación de receipt por capa superior, **sin** segunda escritura.
- **B04** Error SQL/COMMIT, `SQLITE_BUSY` o fallo de readback => rollback único, ninguna fila parcial.
- **B05** Generation/pointer/epoch/revision/sourceRef/principal stale => rechazo antes de mutar.
- **B06** Histórico SQL12/SQL13/SQL14 intacto; reservar no equivale a consumir, `reservationRef != null` => rechazo.
- **B07** Compra no recibida, `purchaseRef` ajeno, provenance distinta, compensación que agota fuente, unidad cruzada => rechazo.
- **B08** Overflow, timestamp inválido, 8.192 filas, id/intent/revision colisionados => rechazo.
- **B09** SQL15 lectura con flag por defecto nunca abre writer; SQL14/SQL13 escritores incompatibles y productor productivo OFF.

## Salida segura

**B1–B5 documentados para auditoría; no afirmar VERIFIED hasta guardia SUCCESS de este SHA.** A2C1A B7 se conserva; A2C1B requiere permiso READY individual en commit documental posterior con evidencia por blob y rutas exactas; después verificar preflight de las 4 rutas. Sin permiso nunca ejecutar B6. Posterior CI exacta + XML JUnit original y B7 independiente. A2C1C–F NO READY. Moto G15 NOT_RUN. No producción, stock real, pagos ni FARO Auto histórico.
