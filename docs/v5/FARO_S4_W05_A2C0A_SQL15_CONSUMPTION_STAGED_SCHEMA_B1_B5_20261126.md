# FARO V5 — W05-A2C0A · SQL15 STAGED, ledger de consumo — preauditoría B1–B5

Fecha: 2026-11-26 (Argentina). Baseline **3fff7848b2824b066f365b0a399376c8263dcd42**. Unidad candidata `S4_W05_A2C0A_SQL15_CONSUMPTION_STAGED_SCHEMA_DEV`. Estado: **B1–B5 documentados para revisión, guardia aún no ejecutada**; no READY, no B6/B7, no CI nueva. Rama work/s3-night-20261102; main/V4.4 congelados. Writer para publicación documental exclusivamente.

## B1 — Evidencia actual, separación y aceptación

- A2B cerrado DEV **9/9 B7 PASS**, cierre `FARO_S4_W05_A2B1C_GLOBAL_B7_PASS_A2B_CLOSED_20261126.md`, SHA `3fff7848b2824b066f365b0a399376c8263dcd42`; CI A2B1C `45865955416` SUCCESS, JUnit original 2/2 y APK DEV; guardia `45865955289` SUCCESS. Permiso A2B consumido/BLOCKED; **no reabrir**.
- SQL14 ACTIVE aislado tiene `household_stock_reservation_event` append-only y validación por revisión, principal, SKU y unidad. Se inspeccionaron `RealityHousehold14ReservationSchema.kt`, `RealityHousehold14ReservationRowsPort.kt`, `RealityHousehold14ReservationApplyPort.kt`, `RealityHousehold14StagedFilePort.kt` y `FaroV5Household14Fbs2Codec.kt`. El respaldo FBS2v11 contiene sección base v10 + reservas, con digest por sección, sin consumo.
- `FaroV5Household14Fbs2Codec` verifica capacidad histórica sobre recibos, compensaciones y reservas; la posición actual no descuenta consumos porque **no existe ledger SQL15**. Ninguna pantalla puede interpretar físico menos reservas como stock definitivo consumible.
- Contrato del roadmap `FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md`: A2C0 ledger consumo SQL15 STAGED + FBS2v12, A2C1 Review/Apply, A2D lectura/UX, A2E aceptación; requieren permisos separados. Esta subunidad **A2C0A sólo crea esquema y migración 14→15 en archivo STAGED inactivo**.
- Criterio binario: archivo SQL14 sellado/auténtico puede convertirse a SQL15 con tabla de eventos vacía; datos históricos SQL14, reservas, compras, recibos, compensaciones y puntero ACTIVE se conservan, o la operación falla sin cambio durable. **No habilita `stock.consume` ni consume unidad alguna**.

## B2 — V4.4 frente a V5 y reutilización

- V4.4 separaba semánticamente compra, recepción, reserva y consumo; consultar matrices P02/P03 históricas. Reutilizar la regla, no migrar stores/código V4.4 ni duplicar modelos.
- V5 conserva `RealityHousehold14ReservationSchema` y `RowsPort` como fuente de verdad SQL14; replicar únicamente el patrón auditado de migración sellada, transacción `BEGIN IMMEDIATE`, `beforeCommit` de lease, WAL/FULL/NOFOLLOW, DDL STRICT, bloqueos UPDATE/DELETE, esquema exacto, rollback y readback. **No modificar SQL14 ni FBS2v11**.
- Evolución mínima: SQL15 añade un ledger nuevo, no una columna `stock` mutable ni un decremento de `receipt`; `consumed=0` solo como ledger vacío y **nunca** como prueba de disponibilidad productiva. El host ACTIVE y generación/cutover requieren posteriores unidades A2C0B–D.

## B3 — Alternativas externas concretas y decisión

- **ADAPTAR** `https://www.sqlite.org/stricttables.html`: STRICT + CHECK y validación explícita evitan tipos arbitrarios; STRICT no reemplaza verificación semántica de balances ni FK entre libros.
- **ADAPTAR** `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/wal.html` y `https://www.sqlite.org/foreignkeys.html`: transacción única y rollback, checkpoint después de commit, claves externas por conexión. Si checkpoint falla, no prometer rollback de un COMMIT consumado: el readback/recovery debe reconocer el estado COMMITTED.
- **REFERENCIA** `https://github.com/grocy/grocy/blob/master/grocy.openapi.json`: Grocy separa consumo de producto, bookings y Undo de bookings. Su servidor/endpoints no aportan CAS de owner, journal ni grants de FARO; no incorporar red, código o nueva dependencia.
- **ADAPTAR principio** `https://developer.android.com/topic/architecture/data-layer/offline-first`: almacenamiento local como fuente canónica, no estado UI ni cálculos derivados de preview. FARO permanece LOCAL_ONLY.
- **DESCARTAR** contador mutable `consumed` en SQL14; consumo derivado de receta/carrito/pago sin Apply; meter eventos en `household_stock_reservation_event` existente; activar SQL15 sobre archivo ACTIVE. Hipótesis falsable: migración SQL14→15 STAGED mantiene byte/captura lógica de sección FBS2v11 y deja nuevas filas de consumo vacías, incluso con reservas abiertas.

## B4 — Diseño mínimo, fronteras y cadena

- **Esquema SQL15 propuesto, sujeto a B5 y validación de implementación:** tabla STRICT `household_stock_consumption_event` append-only, con `event_id`, `intent_key`, `event_type = CONSUME|CONSUME_UNDO`, `consumption_ref` (origen autorreferenciado por CONSUME y referencia al origen para Undo), `stock_item_ref`, `unit` de seis unidades existentes, `quantity_thousandths` entero positivo, `principal`, `source_ref` explícito de acto humano, `reservation_ref` opcional, `occurred_at_ms`, `recorded_at_ms`, `revision` positiva única. A2C0A NO escribe eventos; validez relacional y limites de Undo/consumo reservado se demostrarán en filas/codec/Apply posteriores. No aceptar un esquema que haga imposible demostrar esos invariantes.
- Migrar únicamente copia SQL14 **inactiva**, sellada y con lease exclusivo: verificar versión, esquema SQL14 auténtico, rutas normalizadas, NOFOLLOW, WAL y `synchronous=FULL`; crear tabla, índices/triggers y `user_version=15` dentro de una sola transacción; capturar antes y después la historia SQL14; exigir callback de lease inmediatamente antes de COMMIT; checkpoint WAL posterior, lectura independiente y rechazo si la tabla no está vacía. Sin modificación del puntero ni de rutas SQL14 ACTIVE.
- Triggers prohíben UPDATE/DELETE; PK/event/intent/revision únicos; checks unit, cantidades, refs, timestamps, evento+origen. Un `CONSUME_UNDO` no es un DELETE; requerirá balance de compensaciones y origen auténtico antes de activarse. A2C0A no prueba todavía ese enforcement de saldo.
- **Cuatro rutas exactas de B6 candidato**, a aprobar tras guardia, sin otras rutas:
  1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionSchema.kt`
  2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15StagedFilePort.kt`
  3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionSchemaTest.kt`
  4. `.github/workflows/faro-v5-s4-w05-a2c0a-sql15-consumption-schema.yml`
- **Unidades posteriores, sin permisos heredados:** A2C0B1 codec FBS2v12 bajo FAROBAK2; A2C0B2 rows SQL15 capture/restore; A2C0B3 replay cinco stores; A2C0C journaled cutover + active read/backup; A2C1 Review/Apply/receipts/Undo consumos. Dividirlas nuevamente si exigen más rutas o exponen interdependencias. El contrato de uso reservado exige decidir explícitamente si consumo libera una reserva de forma atómica o se realiza sólo desde no reservado; **NO decidir por una UI, receta o pago**.
- Nunca tocar `main`, versiones 13/14 existentes, seguridad/crypto, Finanzas, recetas, catálogo, pagos, productiva, stock real, Auto/Noche, Moto G15 o UI.

## B5 — Auditoría adversarial previa y pruebas exigidas

- T01: SQL14 válido con compra10, receipts7+3, Undo7, neto físico3 y reserva/liberación (final reservado1); copia STAGED→SQL15 conserva compras, historial y reservas exactos, nuevo ledger vacío, available no publicado.
- T02: `user_version=14` inicial, `15` solo en STAGED tras COMMIT; ACTIVE y puntero idénticos; ninguna fuente de Finance o otros cuatro stores alterada.
- T03: callback `beforeCommit` aborta: DDL+versión íntegramente ROLLBACK; no tabla parcial.
- T04: entradas SQL13/SQL16, archivo corrupto, SQL14 DDL tamper, symlink/alias, lease ausente y lector no autorizado → STOP, sin escrituras.
- T05: estructura STRICT y CHECK: event_id/intent/principal/source/ref vacíos, whitespace/NUL, UTF-8 inválido, unidad no permitida, 0/negativo/overflow, timestamp negativo y revisión duplicada → rechazo.
- T06: duplicados event/intent, INSERT con tipo desconocido, UPDATE/DELETE del libro → rechazo, historia intacta.
- T07: consumo desde reserva sin autorización, duplicación de consumo y compensación sin origen **se rechazan en A2C0B/A2C1**; la mera tabla vacía no certifica estas negativas.
- T08: fallo antes de COMMIT vs interrupción durante WAL/checkpoint; recuperación distingue estado PREPARED, COMMITTED o UNKNOWN sin doble Apply ni corrupción SQL14.
- T09: fixture máximo 8192 filas y límites de bytes de futura lectura: no montar SQL15 si codec/rows no los validan; A2C0A no debe inventar tests de filas aún inexistentes.
- T10: comprobar no regresión SQL14 reservados y A1 recibos; utilizar fixtures existentes, sin reejecutar todo el B7 A2B.
- T11: ningún `stock.consume` expuesto ni stock real/available activo, ninguna llamada remota ni permisos productivos; APK/G15 NO_RUN para esta unidad.

### Gate hacia B6

Publicar preauditoría **solo documental** y comprobar guardia siete bloques SUCCESS sobre SHA de ese commit. Revisar brechas B5. Recién después emitir **otro commit documental** con permiso individual READY exact4 y blobs de evidencia B1–B5, verificar preflight de cada ruta. Código posterior con `FARO-UNIT:S4_W05_A2C0A_SQL15_CONSUMPTION_STAGED_SCHEMA_DEV`, CI de SHA exacto, XML original tests>0/failures=errors=skipped=0, B7 independiente; luego consumir permiso y liberar writer. No asumir que cualquier CI SUCCESS equivale a B7.

**Estado actual:** preauditoría escrita; guardia documental pendiente; permiso A2B BLOCKED consumido; B6/B7 A2C0A NOT_RUN; A2C global OPEN. Productiva/stock real/pagos/Auto/Noche OFF.
