# FARO V5 — W05-A2C0B2 · SQL15 sealed consumption rows · B1–B5

**Fecha:** 2026-11-26 (Argentina). **Unidad:** `S4_W05_A2C0B2_SQL15_SEALED_CONSUMPTION_ROWS_DEV`. Baseline `ae365172a667ed62eebd92e462256e3ca8b90a74`, rama work/s3-night-20261102. Preauditoría sólo documental, sin permiso funcional todavía.

## B1 — Evidencia existente y dependencia

- No repetir A2B B7 PASS DEV 9/9; A2C0A SQL15 STAGED B7 PASS 7/7; A2C0B1 FBS2v12 autenticado B7 PASS 32/32 (guardia 45875957969 SUCCESS). Permiso A2C0B1 BLOCKED. V2 productiva/stock real/pagos/Auto/Noche OFF, G15 NOT_RUN.
- `RealityHousehold15ConsumptionSchema.kt` crea tabla STRICT append-only y triggers de CONSUME/CONSUME_UNDO sólo sobre STAGED inactivo. `RealityHousehold15StagedFilePort.kt` migra archivo SQL14 sellado a SQL15, sin cutover/Apply.
- Brecha verificada en código: SQL12 PurchaseRowsPort admite sólo versiones 12/13/14, SQL13 CompensationRowsPort usa `captureHistoryOnFourteen`, SQL14 ReservationSchema exige version 14, SQL14 ReservationRowsPort encapsula `captureConnection` sólo para v14. Capturar SQL15 exige *extensiones de lectura tipada exacta*, no fingir `PRAGMA user_version=14`.
- Aceptación: snapshot tipeado histórico SQL14 + filas SQL15, captura y restauración únicamente sobre archivo SQL15 STAGED sellado y autenticado por el owner, sin alterar historia ni pointer. Restore idempotente con fail-closed ante divergencia. No expone stock.consume.

## B2 — Comparación V4.4 / reutilización V5

- V4.4 distingue compra, recepción, reserva, consumo; reutilizar semántica, no stores/código V4.4. Conservar patrón V5 SQL14 RowsPort: READONLY immutable/NOFOLLOW; lease explícito; WAL FULL; `BEGIN IMMEDIATE`, readback, idempotencia y checkpoint posterior al COMMIT.
- Agregar `captureHistoryOnFifteen` al SQL13 RowsPort, `captureConnectionOnFifteen` al SQL14 RowsPort y aceptación explícita de versión 15 en PurchaseRowsPort. Parámetro `expectedVersion=15` de SQL14 Schema sólo para ruta nueva: las llamadas históricas siguen requiriendo exactamente 14.
- Nuevo `RealityHousehold15ConsumptionRowsPort` se ubica en Store Reality y trabaja con los DTO tipados existentes; no depende de Store Generation, no altera FBS2v12 ni crypto.

## B3 — Investigación externa real y alternativas

- **ADAPTAR** SQLite STRICT https://www.sqlite.org/stricttables.html: exige tipos y checks, no valida el saldo físico/reservado entre libros.
- **ADAPTAR** SQLite Isolation https://www.sqlite.org/isolation.html: leer en snapshot consistente (`BEGIN DEFERRED`) y escribir bajo transacción inmediata (`BEGIN IMMEDIATE`) con lease. Evitar `SQLITE_BUSY_SNAPSHOT`.
- **ADAPTAR** SQLite WAL https://www.sqlite.org/wal.html: FULL en el writer, checkpoint tras commit; si el checkpoint queda ocupado, reconocer estado COMMITTED y recuperar por readback, no prometer rollback de un commit ya hecho.
- **ADAPTAR** Foreign Keys https://www.sqlite.org/foreignkeys.html: activar `PRAGMA foreign_keys=ON` por conexión, validación lógica adicional en FARO.
- **DESCARTAR** bajar versión 15 a 14 temporalmente, duplicar contador mutable de disponibilidad, habilitar ACTIVE, trigger de consumo por receta/pago y soluciones web o dependencias externas.

## B4 — Diseño y fronteras

- DTO `RealityHousehold15ConsumptionRow`: eventId, intentKey, type, consumptionRef, stockItemRef, unit, quantityThousandths, principal, sourceRef, reservationRef?, occurredAtMs, recordedAtMs, revision. `RealityHousehold15ConsumptionRows(historical: RealityHousehold14ReservationRows, consumptions: List<...>)`.
- `captureTrustedSealedStageFifteen(file,requireExclusiveStage)`: verificar canonical path NOFOLLOW, owner lease, SQLite version 15 y DDL SQL15/14/13, lectura immutable transaccional; capture columnas y CAST BLOB para UTF-8 real; 8192 filas/4MiB sección; validación cronológica entre recibos, Undo recibido, reservas/releases, consumo/Undo consumo.
- `restoreOrVerifyTrustedSealedStageFifteen(file,incoming,requireExclusiveStage)`: validar entrada antes de modificar; BEGIN IMMEDIATE con WAL/FULL, historia exacta, si ledger idéntico `ALREADY_IDENTICAL`, si vacío agregar eventos y comparar captura antes de COMMIT, lease final; luego checkpoint y nueva captura. Divergencia STOP. No activar SQL15 ACTIVE ni stock real.
- Invariantes: clave (stockItemRef, unit, principal); por revisión `0<=reserved<=physical`, `physical = receipt - receiveUndo - CONSUME + CONSUME_UNDO`; Undo de consumo sólo contra evento anterior coincidente, saldo compensado <= origen, IDs/intent/revisions sin colisión; `reservationRef` NO NULO prohibido hasta A2C1. Validar bytes UTF-8, límites por campo y longitudes; `Long` para acumulaciones.
- **B6 candidato exact7 rutas**:
  1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt`
  2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationRowsPort.kt`
  3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationSchema.kt`
  4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationRowsPort.kt`
  5. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt`
  6. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPortTest.kt`
  7. `.github/workflows/faro-v5-s4-w05-a2c0b2-sql15-consumption-rows.yml`
- Siguientes unidades independientes: A2C0B3 replay cinco stores; A2C0C cutover journaled/active, A2C1 Review/Apply y receipts. Ninguna tiene permiso por herencia.

## B5 — Auditoría adversarial y pruebas exigidas

- T01: migración SQL14→15 STAGED; historia compra10, recibido7+3, compensado7, reservado1; SQL14 origen intacto y SQL15 inicialmente vacío.
- T02: insertar consumo válido1/Undo1 con origen y atributos idénticos, lectura tipada y recaptura exacta; segundo restore `ALREADY_IDENTICAL`.
- T03: target divergente o historia antigua desigual => rechazo sin modificación; no borrar histórico ni consumir otra vez.
- T04: lease revocado justo antes de COMMIT => rollback total; restauración posterior vuelve a funcionar.
- T05: checkpoint ocupado después de COMMIT => reconocimiento seguro de committed y retry idempotente, no declarar rollback.
- T06: over-consume sobre disponible, Undo anterior al origen o superior a consumo, ref/source/principal/SKU/unidad distintos, reserva referenciada sin liberación => rechazo.
- T07: malformación de textos, UTF-8 CAST x'FF', whitespace/NUL/surrogate, duplicación de revision/intent/event y colisión con recibos/reservas, cantidad 0, exceso > IntMax, fechas negativas.
- T08: versiones 14/16, tamper DDL/triggers SQL15, schema extra inbound FK, symlink/alias/hardlink/sidecar, falta de lease: fail-closed.
- T09: 8192 filas, 4MiB y suma Long; no reservar memoria por cantidades declaradas maliciosas.
- T10: regresión dirigida SQL14 ReservationRowsPortTest y SQL15 ConsumptionSchemaTest + JUnit XML original sin fallas, guardia 7 bloques PASS.
- T11: sin acceso al host productivo, sin Apply, sin permisos nuevos, sin G15 físico, sin pagos/Auto/Noche.
- T12: no interpretar rows como prueba de stock real ni habilitar `available` en pantalla.

**Gate:** publicar esta preauditoría con writer CAS, guardia documental SUCCESS. Después emitir permiso READY exact7 en commit exclusivamente documental, blob proof B1–B5, preflight las siete rutas y recién B6 con `FARO-UNIT:S4_W05_A2C0B2_SQL15_SEALED_CONSUMPTION_ROWS_DEV`. Verificar CI del SHA y XML original, B7, consumir permiso y RELEASED. Ante fallo STOP o RED_FIX sólo de esta unidad.
