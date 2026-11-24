# FARO V5 · W05-A1A · Esquema transaccional STAGED de recepciones físicas (B1–B5)

Fecha: 2026-11-24 (Argentina). Unidad `S4_W05_A1A_PHYSICAL_RECEIPT_STAGED_SCHEMA_DEV`. HEAD revisado `8ae2e8bfcac354911dea76ddd3d1e2d28a61121b`. Rama `work/s3-night-20261102`.

**Alcance acotado:** sólo instalar el esquema de eventos de recepción confirmada en **copia STAGED Reality10 → Reality11**; ninguna migración ACTIVE ni apertura productiva. No stock visible, registro efectivo de mercancía, transacción de compra/pago, nueva API/owner, nueva UX, V2 productiva, G15, IA, Auto/Noche. Esta es la primera unidad de W05-A1, no su cierre.

## B1 — Evidencia vigente y criterio binario

- W05-A0: contrato de previsualización inerte B7 PASS, código `b2ce0bd786871fc9d23f21034e64f2bdc98a60d6`, CI `45387220139` SUCCESS, 10/10 XML; guard del checkpoint `45387976281` SUCCESS.
- `RealityHousehold10Schema.kt` versión 10 sólo tiene `household_need_state`/`household_need`, no stock, recepción, reservas ni receta. Su instalador y `RealityHousehold10MigrationTest.kt` validan copia 9→10, DDL transaccional, esquema completo, FK, rollback y rechazo del opener Finance9.
- P05 S04/S05 PF21–PF25; P07 G05/G09 P7T04–P7T07; P08 W05: comprar/pagar no aumenta físico; recibo efectivo explícito 7 de 10 deja pendientes 3. No existe demostración de idempotencia o stock durable.
- Entrada A1A: archivo Reality10 STAGED sellado por caller futuro; validador exige `user_version=10`, tablas base Finance9 + Household10 completas, `foreign_keys=ON`, `integrity_check=ok` y `foreign_key_check` vacío. Rechazar anterior, incompleto, ya migrado o contaminado.

## B2 — Reutilización sin duplicación

- V4.4 L11–L15: producto comprado, recibo, reserva y consumo son hechos distintos. Reusar semántica, **no** código/storage heredado.
- V5: reutilizar patrón interno de `RealityHousehold10Schema.installOnStagedNineConnection`, `RealityFinance9Schema` y fixture de `RealityHousehold10MigrationTest` sin modificar archivos existentes ni importar dos bases/escritores. Persistencia efectiva será un subbloque posterior con grant/Apply/recovery, no parte de DDL.
- Mantener unidades `HouseholdNeedQuantity`: enteros milésimos positivos `1..Int.MAX_VALUE`, mismas seis unidades; identidad opaca de compra y de artículo, sin deducir identidad a partir del precio, necesidad o texto ambiguo.

## B3 — Investigación externa verificable, clasificación

- SQLite `https://www.sqlite.org/lang_createtable.html`, `https://sqlite.org/stricttables.html`, `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/pragma.html`: **ADAPTAR** formato STRICT, CHECK/UNIQUE e instalación atomicamente dentro de BEGIN IMMEDIATE; STRICT requiere SQLite ≥3.37.0, ya exigido por Household10. Comprobar que la copia sigue v10 cuando se inyecta falla antes de COMMIT.
- Grocy rutas `https://github.com/grocy/grocy/blob/master/routes.php`: distingue `stock/products/.../add` y `consume`, con stockjournal. **REFERENCIA semántica**, no copiar código web/GPL ni activar servidor/sincronización; riesgo evitable: equiparar pedido a receipt real.
- Alternativa A elegida: ledger de eventos de **recepción explícita** con clave de intención única en Reality11 STAGED, sin almacenar stock agregado modificable. Alternativa B descartada: incrementar stock por registro de pago o carrito. Alternativa C descartada: nueva DB propia, auto-ingesta por IA/receta/lista o tabla mutable de saldo sin trazabilidad. El futuro consumo tendrá otro tipo de evento y revisión (W05-A2), no se simula aquí.
- B3 no constituye autorización de cambios de esquema ACTIVO.

## B4 — Diseño técnico y rutas exactas de A1A

Tres rutas nuevas previstas para permiso independiente después de guard documental:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11ReceiptSchema.kt`
2. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11ReceiptSchemaTest.kt`
3. `.github/workflows/faro-v5-s4-w05-a1a-receipt-schema.yml`

DDL interno, sin constructor público de Runtime ni migración del origen:
- `household_stock_receipt_state` singleton, revisión no negativa inicial cero; el writer futuro deberá CAS revision/epoch.
- `household_stock_receipt` append-only **por contrato futuro**, clave `receipt_id` opaca PK, `intent_key` UNIQUE, `purchase_ref` (identidad comercial confirmada **futura**, sin FK inventada a W04), `stock_item_ref` (artículo físico explícitamente elegido), cantidad milésimos y unidad, fecha física, timestamp de registro; contraints NOT NULL/STRICT y checks para identidad/fecha/cantidad. **La DDL no garantiza append-only**; el futuro puerto de escritura debe prohibir UPDATE/DELETE fuera de Undo transaccional aprobado. No declarar ni probar esa garantía todavía.
- Evitar FK de `household_stock_receipt` a `household_need`: necesidad no representa una compra ni un lote. La compra y el límite de recepción deben revalidarse en W05-A1B contra fuente comercial autorizada, antes de escribir.
- La fuente no contiene recepción real: migrar produce tabla vacía, por lo que ningún `stock` aparece por magia. La copia origen Reality10 debe conservar bytes y versión; WAL truncado bajo fixture, sin afirmar host quiescence real.

## B5 — Auditoría adversarial y criterios de prueba A1A

Las siguientes pruebas están **DISEÑADAS/NOT_RUN** en este documento, ejecutar en B6:
1. Copia Reality10→11 vacía, preserva origen bytes, tabla Household necesidades y Finance, e integridad FK.
2. Falla inyectada justo antes del COMMIT revierte DDL y `user_version`; reintento único válido.
3. Rechaza Reality9, esquema10 incompleto y Reality11 ya migrado.
4. Rechaza foreign_keys OFF y violaciones heredadas FK.
5. Rechaza registros inválidos (cantidad cero, desborde, unidad desconocida, fecha fuera de rango, referencias vacías).
6. PK `receipt_id` y `intent_key` únicos, no duplica evento de mismo intento.
7. El opener ACTIVE de Finance9/Household10 no puede abrir Reality11 silenciosamente.
8. Ningún pago ni necesidad preexistente produce filas en `household_stock_receipt` y `household_stock_receipt_state.revision=0`.

**B5 negativo posterior (no cubierto A1A):** escritura real/Apply, revocación de grant/ABA, recepción 7→3, doble tap transaccional, interleaving, rollback, restauración de 5 stores, WAL activo, Undo y consumo/receta. Requieren unidades A1B+ independientes con B1–B7 propios, sin entregar ACTIVE prematuramente. NO usar READY previo W05-A0.

**Decisión:** B1–B5 preauditables para la unidad de esquema STAGED A1A. Emitir READY exact3 **sólo** tras guardia de este documento. Código y CI posteriores; B7 requiere comprobación del SHA + XML real. Writer RELEASED al cerrar turno.
