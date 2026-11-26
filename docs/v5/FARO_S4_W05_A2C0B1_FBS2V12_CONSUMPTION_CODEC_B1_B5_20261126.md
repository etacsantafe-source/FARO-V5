# FARO V5 · W05-A2C0B1 — FBS2v12 consumo tipado y autenticado · B1–B5

Fecha 2026-11-26 (Argentina). Repositorio `etacsantafe-source/FARO-V5`; rama `work/s3-night-20261102`.
Unidad candidata: `S4_W05_A2C0B1_AUTH_FBS2V12_CONSUMPTION_CODEC_DEV`. Baseline de lectura: `4d85ac03ef89114c567d6eb2d30be13338814e49` (A2C0A B7 SQL15 STAGED). **Sólo preauditoría documental; permiso previo BLOCKED. Ni B6 ni B7 ejecutados en este documento.**

## B1 — Evidencia existente y criterio de aceptación

- W05-A1 global DEV cerrado; A2A B7 PASS; A2B B7 PASS 9/9 en `docs/v5/FARO_S4_W05_A2B1C_GLOBAL_B7_PASS_A2B_CLOSED_20261126.md`. No repetir. A2C0A B7 PASS scoped SQL15 STAGED 7/7 en `docs/v5/FARO_S4_W05_A2C0A_B7_PASS_SQL15_STAGED_CONSUMPTION_SCHEMA_20261126.md`. No inferir SQL15 ACTIVE, consumos, backup FBS2v12 ni G15 físico.
- Inspeccionados `FaroV5Household14Fbs2Codec.kt` (FBS2v11, header 80 bytes, base FBS2v10 exacta + reservas, digests SHA-256 por sección, longitud global, referencias y cronología), `FaroV5Household13Fbs2Codec.kt` (FBS2v10), `FaroV5FiveStorePortableBackupCrypto.kt` (FAROBAK2 AES-GCM con cabecera autenticada), `RealityHousehold15ConsumptionSchema.kt` y `RealityHousehold15StagedFilePort.kt`. El esquema SQL15 ya define `CONSUME` y `CONSUME_UNDO`, doce campos tipados más revision, libro append-only, origen y balances Undo; no hay ejecutor de consumo.
- Objetivo binario A2C0B1: **codec canónico FBS2v12** con sección FBS2v11 intacta y sección SQL15 de consumo, y dos métodos tipados de `seal/open` mediante **el FAROBAK2 ya existente**; lectura autenticada antes de parsear; rechazo de corrupción, replay semántico imposible y backups legados bajo tipo equivocado. Codec solamente, no SQL/restore/cutover ni cambio de ACTIVE.
- Límites verificables: snapshot con compras, recibos, compensaciones, reservas y consumos; prueba round-trip exacta, compatibilidad de bytes FBS2v11, no sobregiro físico/reservado en historia ordenada y rechazo estricto de orígenes Undo. El codec **no garantiza por sí solo** garantía de captura atómica de cinco stores ni autorización para restaurar/activar.

## B2 — Comparación V4.4 y reutilización

- Mantener la separación V4.4 consolidada en P02/P03: compra, recepción, reserva y uso son sucesos diferentes. No copiar stores ni modelos de V4.4; reutilizar **solo la semántica de operación humana explícita** y la diferenciación entre vista y Reality.
- V5: extender por composición `FaroV5Household14Fbs2Snapshot` y la implementación FBS2v11, sin reescribirla. Reutilizar el envelope FAROBAK2 y sus clases `FiveStoreAuthenticatedPayload` y `FiveStorePortableBackupOpenFailure` sin modificar la criptografía, clave, KDF, nonce o contratos públicos.
- SQL15 define origen `consumption_ref`, `source_ref`, `reservation_ref` opcional y `revision`; el snapshot tipado los debe preservar **sin reinterpretar**. Captura de filas y replay quedan a cargo de A2C0B2/B3, no A2C0B1.

## B3 — Investigación externa y alternativas contrastadas

- **ADAPTAR** OWASP Cryptographic Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html — apertura sólo después de autenticación, protección de integridad de todos los campos relevantes, gestión de tamaño y errores sin exposición de datos; FARO ya tiene AES-GCM/AAD y NO debe agregar nueva librería ni cifrado paralelo.
- **ADAPTAR** SQLite STRICT: https://www.sqlite.org/stricttables.html — validación tipada y `CHECK` del ledger de origen; el backup requiere además validación de referencias, balances y cronología porque las restricciones SQL solas no expresan todos los invariantes cross-ledger.
- **EVALUAR/DESCARTAR** serialización genérica Protocol Buffers https://protobuf.dev/programming-guides/encoding/ — formato extensible pero el orden de serialización no es necesariamente canónico; no sustituir el FBS2 versionado y reproducible ni sumar dependencia para una sección delimitada de enteros y textos.
- **REFERENCIA FUNCIONAL, SIN CÓDIGO** Grocy (https://github.com/grocy/grocy) modela uso y reversión de existencias como operaciones separadas; su API y servidor no prueban grants, single writer, Undo ni autenticación local FARO. No introducir red ni dependencia de producto.
- **DESCARTAR**: guardar `consumed` mutable, reusar reserva como consumo, consumir por recetas/carrito/pagos o restaurar SQL15 ACTIVE implícitamente.

## B4 — Diseño acotado de B6 y cadena futura

- Crear `Household15ConsumptionEventType`, `Household15ConsumptionBackupRow` y `FaroV5Household15Fbs2Snapshot(base: FaroV5Household14Fbs2Snapshot, consumptions: List<...>)`. Campos 1:1 SQL15: `eventId, intentKey, type, consumptionRef, stockItemRef, unit, quantityThousandths, principal, sourceRef, reservationRef, occurredAtMs, recordedAtMs, revision`. Tipos `String`, enum, `Int`, `Long`, `String?`.
- FBS2v12: `MAGIC=0x46425332`, versión=12, 2 secciones delimitadas (FBS2v11 exacta y ledger de consumo), longitudes enteras y hashes SHA-256 por sección, con límites estrictos; **la sección v11 debe coincidir byte a byte con su re-encode canónico**. Mantener presupuesto global `FaroV5FiveStoreSemanticCodec.MAX_PLAINTEXT_BYTES`, sección consumo <= 4 MiB, filas <= 8192 y referencias según SQL15. Rechazar trailing bytes, tamaños negativos/overflow, UTF-8 inválido, orden no canónico, dupes y tipo ajeno, sin OOM.
- Preservar invariantes cruzados mediante simulación **por revision global** de recibos/Undo, reservas/releases y consumo/consumeUndo. Clave coherente `(stockItemRef, unit, principal)`. `physical = receipts - receiveUndo - consumes + validConsumeUndo >= 0`; `reserved >= 0` y `reserved <= physical` en **cada** paso, no sólo al final. Usar `Long` en sumas y evitar overflow. Revocar implicitación de disponibilidad por solo FBS2v11.
- Conservador hasta A2C1: **rechazar `reservationRef != null` para consumos y Undo**; consumir parte reservada requiere contrato explícito de release+consume atómico y receipt, fuera de este permiso. Un `CONSUME_UNDO` debe referir a CONSUME previo con la misma clave/principal/source/reservation, revisión y tiempos crecientes, y Undo acumulado <= consumo original; no borrar el original. Consumir sólo stock no reservado disponible en la revisión correspondiente.
- Validar `eventId == consumptionRef` para CONSUME y distinto para Undo; intenciones y eventos únicos; `revision` única contra recibos, compensaciones y reservas. Limitar timestamps no negativos, `recorded >= occurred`, Undo `recorded >=` original, y secuencia de revisiones ascendente. Secciones vacías legales; las versiones previas conservan lectura bajo **sus tipos originales**, no se convierten silenciosamente.
- Ampliar `FaroV5FiveStorePortableBackupCrypto` sólo con `sealWithHouseholdConsumption15` y `openWithHouseholdConsumption15`, internos, reutilizando `sealPayload/openPayload`. No tocar algoritmos, parámetros, `open()` genérico ni datos productivos.
- **Rutas B6 candidatas exact4, sin autorización todavía**:
  1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15Fbs2Codec.kt`
  2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt`
  3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15Fbs2CodecTest.kt`
  4. `.github/workflows/faro-v5-s4-w05-a2c0b1-fbs2v12-codec.yml`
- Después: **A2C0B2** SQL15 rows capture/restore bajo permiso propio; **A2C0B3** replay 5 stores y fencing; **A2C0C** cutover journaled/lectura ACTIVE; **A2C1** Review/Apply y recibos/Undo; **A2D** lectura/UI; **A2E** aceptación global. No anticipar ni mezclar rutas de esas unidades.

## B5 — Auditoría adversarial antes de cualquier B6

1. T01 round-trip FBS2v12 con base v11 auténtica y eventos `CONSUME` + `CONSUME_UNDO`, sección v11 byte-for-byte inalterada, igualdad del snapshot y re-encode de v12 idéntico.
2. T02 historia: compra10, recibido7+3, Undo de7, físico3, reserva2/liberación1, consumo1 no reservado => físico2/reservado1, Undo consumo1 => físico3; cada transición válida y en orden de revisiones sin saldos negativos.
3. T03 variantes de orden: consumo antes de recepción, Undo antes de origen, reserva simultánea no disponible, consumo posterior que deja `reserved > physical` y saldo temporal negativo aunque al final se compense => STOP.
4. T04 denegar cualquier `reservationRef` no nulo hasta autorización separada, incluso si hoy SQL15 acepta columna; no crear una falsa operación de transferencia de reservas.
5. T05 Undo sin original, Undo repetido en exceso, referencia distinta, SKU/unidad/principal/source diferente, timestamps o revision incompatibles => STOP.
6. T06 ids, intentKeys y revisions repetidos, colisión con históricos físicos/reservas, textos inválidos (whitespace de control, NUL, surrogate, UTF-8 corrupto), unidad fuera de catálogo, longitud extrema, cantidad 0, negativa e int overflow => STOP.
7. T07 cabeceras magic/version, longitudes inválidas, SHA-256 sección alterada, bytes trailing, sección v11 no canónica re-firmada, truncación y > presupuesto => STOP seguro sin OOM.
8. T08 FAROBAK2: passphrase errónea, archivo corrupto, sobre v11 leído como v12 y sobre v12 leído como v11 => rechazo tipado, nunca devolver snapshot parcial. No exponer secretos en logs.
9. T09 frontera 8192 eventos/4 MiB y sumas `Long`; evitar asignaciones en función de longitud declarada antes de validar presupuesto; datos de prueba locales sin APIs externas.
10. T10 regresiones FBS2v11/v10/v9 dirigidas y XML JUnit originales con tests > 0, failures=errors=skipped=0. No reclamar PASS sin CI del SHA funcional, artefacto verificable y guardia siete bloques.
11. T11 no invocar capturas SQL15, replay, cutover, stock.consume, pagos, activación V2 ni UI; Moto G15 sigue NOT_RUN.
12. T12 ante error de autenticación/decodificación el archivo fuente no se modifica, no se escribe puntero ACTIVE ni se marca snapshot validado.

### Puerta de autorización

Publicar **sólo este documento** bajo writer remoto legítimo y guardia de protocolo SUCCESS del SHA documental. Revalidar B5 frente a código exacto. Luego, en **commit documental separado**, cargar permiso `READY` **exact4** con `stages.B1..B5` `VERIFIED`, `blob_sha` de este documento y `reviewed_head` exacto; comprobar preflight de las cuatro rutas. Recién entonces B6 con mensaje `FARO-UNIT:S4_W05_A2C0B1_AUTH_FBS2V12_CONSUMPTION_CODEC_DEV`, CI dirigida + XML, B7 y consumir permiso. Sin GREEN no hay B6.

**Estado:** A2C0B1 B1–B5 propuestos para guardia, `B6 NOT_RUN`, `B7 NOT_RUN`, `READY NOT_ISSUED`. A2C y W05 siguen abiertos. V2 productiva/stock real/pagos/Auto/Noche OFF; sin APIs pagas ni modificaciones a `main`, V4.4, Finance, otros owners, claves o datos físicos.
