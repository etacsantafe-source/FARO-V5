# FARO V5 · W05-A2B0A · Esquema STAGED SQL14 de reservas — B1–B5

Fecha: 2026-11-26 (Argentina). Unidad propuesta `S4_W05_A2B0A_SQL14_RESERVATION_STAGED_SCHEMA_DEV`. Baseline `382b124b42eac7ea302e82abbcf1b7824c12ece1`. Writer observado RELEASED época 2059 antes de esta preparación. Este documento es **B1–B5 documental**; no reclama CI, B6, B7 ni READY automático.

## B1 — Evidencia, alcance y brecha verificable

- A1 GLOBAL DEV cerrado: `docs/v5/FARO_S4_W05_A1_GLOBAL_DEV_B7_ACCEPTANCE_20261125.md`, JVM+Android API35 2/2, sin G15 físico.
- A2A B7 PASS acotado: `ce30bb6f5498a52b70b74decb15a490171923fe1`; CI 45746937979, JUnit XML original 5/5, guardia 45746937952 SUCCESS, checkpoint `382b124b42eac7ea302e82abbcf1b7824c12ece1`, writer RELEASED epoch 2059. `FaroV5Household13DevStockSnapshot` calcula SOLO físico neto por (SKU,unidad); reservado, consumido y disponible siguen `null`.
- Código verificado: `RealityHousehold13CompensationSchema.kt` define `VERSION=13`, tabla STRICT, triggers append-only y migración transaccional desde SQL12 en STAGED; `RealityHousehold13StagedFilePort.kt` usa `SQLITE_OPEN_NOFOLLOW`, `PRAGMA foreign_keys=ON`, `synchronous=FULL` y checkpoint WAL. `FaroV5Household13Fbs2Codec.kt` conserva FBS2v10 con base, compras y compensaciones. No hay ledger persistente de reservas y NO puede mostrarse disponible como físico.
- Hoja de ruta `FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md`: A2B0 requiere SQL14 STAGED y después FBS2v11/cinco stores, sin tocar SQL13 ACTIVE. Esta subunidad **A2B0A** solo incorpora esquema vacío y migración aislada. A2B0B hará codec/restore; A2B1 hará Review/Apply de reserva/liberación. No activar SQL14 ACTIVE desde A2B0A.
- Requisitos P05 S04/PF22–PF23; P07 separa Household de Finance; catálogo `stock.reserve` y `stock.release` no equivale a grant ni executor activo.

## B2 — Reutilización V4.4 / V5, sin repetición

V4.4 histórico L11–L13 distinguía comprar, recibir, reservar y consumir. Reutilizar solo la semántica; NO código legado ni stores. V5 reutiliza las definiciones y pruebas SQL13, validación de ruta STAGED sellada, lease, generación, puntero completo, receipts y recuperación ya certificados en A1. Las operaciones nuevas tendrán un writer de datos y authority explícita, pero no se crean en esta unidad. SQL13 y FBS2v10 siguen pudiendo leerse/reabrirse exactamente como antes; un respaldo FBS2v10 no se convertirá silenciosamente en una reserva vacía **activa**.

## B3 — Alternativas externas reales y decisión

1. **ADAPTAR patrón** de [SQLite Transactions](https://www.sqlite.org/lang_transaction.html), [SQLite WAL](https://www.sqlite.org/wal.html) y [SQLite Foreign Keys](https://www.sqlite.org/foreignkeys.html): instalación transaccional DDL en una copia inactiva con rollback, integridad, checkpoint y validación estricta de nombres/definiciones. Rechazar DDL sobre base ACTIVE o cambio no autenticado de `user_version`; las claves externas deben activarse por conexión.
2. **REFERENCIA, no incorporar código**: [Grocy OpenAPI](https://github.com/grocy/grocy/blob/master/grocy.openapi.json) registra stock bookings, transacciones y Undo como actos diferenciados. Sus rutas remotas y modelo de stock no satisfacen por sí mismos el owner/ACL/receipts/generation de FARO; no usar servidor, red ni importar licencias/dependencias.
3. **ADAPTAR principio arquitectónico** de [Android offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first): fuente local persistente única, lectura a través de owner autenticado, nunca estado de UI o cache como inventario real.
4. **DESCARTAR**: (a) agregar columna `reserved` mutable a `household_stock_receipt` — mezcla hechos históricos y disponibilidad; (b) tabla en SQL13 ACTIVE — rompe restauración y versiones certificadas; (c) solo contador en memoria — pierde causalidad, Undo y crash recovery. Hipótesis falsable: migrar una copia sellada SQL13 a SQL14 instala solamente ledger vacío, conserva historia SQL13 y ante interrupción deja versión 13 intacta.

## B4 — Contrato y cuatro rutas exactas de B6

**Decisión acotada:** A2B0A instala en SQL14 STAGED `household_stock_reservation_event` como tabla STRICT append-only, inicialmente vacía, con identidad de evento, intent idempotente, tipo `RESERVE|RELEASE`, `reservation_ref` (RESERVE identifica origen, RELEASE lo referencia), `stock_item_ref`, unidad tipada, cantidad entera positiva en milésimas, principal, timestamp y revisión única positiva. Prohibir UPDATE/DELETE mediante triggers y validar canonicidad, límites y DDL exacta. Relación release→reserve, no doble liberación, capacidad `reserved<=physicalNet-consumed` y CAS de revisión son **invariantes para A2B0B/A2B1** y deben rechazarse si no se puede demostrar su enforcement bajo el mismo writer de Reality. Nunca inferir de este esquema que esas acciones ya funcionan.

**Condición de entrada:** un archivo SQL13 inactivo, sellado y exclusivo aportado por generation owner; no abrir la ruta ACTIVE de otro proceso; `NOFOLLOW`, `synchronous=FULL`, FK ON. Instalar tabla/triggers y `user_version=14` en una única transacción con callback `beforeCommit` de verificación de lease/fence; hacer checkpoint WAL únicamente tras commit. Verificar antes/después que compras, recepciones, compensaciones, otras tablas y datos de los cinco stores permanecen idénticos. Si cualquier comparación falla, rollback íntegro. No generar reservas, no modificar puntero, no montar SQL14, no activar stock ni disponible. El diseño detallado de índices/FOREIGN KEY de fuente queda sujeto a B5 y debe ser autocontenido en el esquema nuevo, sin alterar DDL SQL13 heredada.

**Rutas funcionales nuevas, exactamente cuatro**:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationSchema.kt`: DDL, triggers y transacción de migración 13→14; inspección exacta del esquema.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14StagedFilePort.kt`: exclusivamente adaptador de archivo sellado STAGED.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationSchemaTest.kt`: JDBC/SQLite real y pruebas negativas.
4. `.github/workflows/faro-v5-s4-w05-a2b0a-sql14-reservation-schema.yml`: CI dirigida y artefacto JUnit XML original.

No cambiar `RealityHousehold13CompensationSchema`, backup/crypto, host ACTIVE, generation cutover, permisos, catálogo, UI/Android ni `main`. Si el B5 real exige tocar quinta ruta, parar y emitir nuevo alcance B1–B5 + permiso; nunca ampliar de hecho.

**Cadena siguiente, sin adelantar permisos:** A2B0B = contrato/codec FBS2v11 y restore cinco stores SQL14 STAGED; A2B0C = cutover/recovery DEV si se demuestra seguro; A2B1 = reserva y liberación por Review→Apply con receipts/Undo. Se decidirán rutas y unidades propias. El roadmap A2B0 permanece OPEN tras A2B0A.

## B5 — Auditoría adversarial previa y aceptación binaria

- T01: fixture SQL13 válido con compra 10/receipts 7+3/Undo7 conserva físicamente 3 UNIT tras migrar copia STAGED a SQL14; reservas vacías, disponible sigue UNKNOWN.
- T02: `user_version=13` antes y `14` después solo en el archivo STAGED; SQL13 ACTIVE byte/lógicamente intacta y ningún cambio de puntero.
- T03: `beforeCommit` lanza fallo, transacción retrocede íntegra, sin tabla parcial ni `user_version=14`.
- T04: SQL12/14 ajeno, archivo corrupto, ruta simbólica/alias, sello o lease ausentes → STOP sin side effects.
- T05: SQLite STRICT rechaza IDs vacíos, espacios, NUL, unidad fuera de seis tipos, cantidad no positiva/overflow, tipo desconocido, revisión no positiva/duplicada.
- T06: INSERT duplicado intent/event, UPDATE o DELETE de ledger → STOP/RAISE, sin pérdida de historia.
- T07: RELEASE con origen inexistente/otra SKU/unidad/principal, release excesiva o repetida: diseñar guardia inequívoca; **si no puede probarse en A2B0A, mantener tabla vacía/no publicable y exigir enforcement en B0B/B1 antes de activar operaciones**.
- T08: simular fallo durante WAL/checkpoint y reabrir; nunca mitad de migración; ningún registro de otras tablas alterado.
- T09: reabrir y validar definiciones SQL13 y nuevas; no convertir `reserved=null` en 0 de stock disponible.
- T10: pruebas de no regresión sobre compra/receipt/compensación SQL13 existentes sin reejecutar suites completas A1.
- T11: 8.193 filas, referencia UTF-8 inválida, revisión desordenada y límites de bytes en lectura futura: STOP antes de ofrecer restore/Apply (se exige en B0B).
- T12: no exponer `stock.reserve`, `stock.release`, ACL nuevo, autorun, pago o puente Finance al instalar schema.

**Entrada a B6:** antes, guardar esta preauditoría en commit solo documentación y exigir guardia protocolo SUCCESS sobre su SHA. Emitir luego permiso individual READY con `unit_id`, evidencia de B1–B5 y **cuatro rutas exactas** en otro commit documental. Ejecutar cuatro preflight STOP/PASS y empezar B6 solo si PASS; código con `FARO-UNIT:S4_W05_A2B0A_SQL14_RESERVATION_STAGED_SCHEMA_DEV`; CI del SHA exacto, XML real `tests>0` y `failures=errors=skipped=0`; B7 independiente antes de abrir B0B.

**Estado de este documento al publicar:** B1–B5 **DOCUMENTADOS**, guardia documental por verificar; permiso anterior A2A **BLOCKED consumido**; B6/B7 A2B0A **NOT_RUN**, A2B global OPEN. G15 físico NOT_RUN; V2 productiva, inventario real, pagos, Auto/Noche OFF.