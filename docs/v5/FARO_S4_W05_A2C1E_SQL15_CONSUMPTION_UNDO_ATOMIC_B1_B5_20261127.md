# FARO V5 — W05-A2C1E: Undo compensatorio SQL15 transaccional DEV · B1–B5

**Unidad futura acotada:** `S4_W05_A2C1E_SQL15_CONSUMPTION_UNDO_ATOMIC`. **Rama:** `work/s3-night-20261102`. **Base de lectura:** `6da46781e2a15f3b5a5afef184d959ae1a7d6ae2` (27/11/2026 Argentina).
**Estado:** B1–B5 DOCUMENTADOS PARA GUARDIA, B6 NOT_RUN y B7 NOT_RUN; permiso A2C1D CONSUMIDO/BLOCKED, A2C1E NO_READY hasta prueba documental y nuevo permiso de 4 rutas exactas.
**Frontera:** SQL15 DEV aislado; ningún host, UI, Android físico ni stock real. V2 productiva, pagos, FARO Auto/Noche OFF.

## B1 — evidencia comprobada y brecha específica

- A2C1D ya B7 PASS scoped DEV: código `97e3155e9dd619fdbe31dd2e2c7a88d2828c9ba4`, CI `45954154676` SUCCESS 19/19 XML, guardia funcional `45954154596` SUCCESS, guardia checkpoint `45954756541` SUCCESS, estado `docs/v5/FARO_S4_W05_A2C1D_B7_PASS_CONSUMPTION_UNDO_ADMISSION_20261127.md` (blob `3f663508bdcb1c400641a88aab80b62bf94d44a6`). El contrato D solo admite **Undo FULL** de consumo intacto, fuente COMMITTED autenticada por puertos del futuro host, consentimiento NUEVO N-016 y relectura de posición; NO se reimplementa D.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt` (blob `4af7a385640977f6e984d5a49878926b7ad60287`) ya conoce `CONSUME_UNDO`, exige que `consumption_ref` señale CONSUME anterior, idénticos principal/item/unit/sourceRef/reservationRef, cantidad ≤ pendiente de origen y timestamps/revisiones únicas; aplica delta positivo al físico pero mantiene reservado. Límite 8192 eventos y 4 MiB.
- `RealityHousehold15ConsumptionSchema.kt` (blob `fd0b95087eef386ed8acbdf2613225c0cee41917`) define tabla STRICT append-only, trigger de referencialidad, sumatoria y colisiones; NO habilita una operación de aplicación por sí sola.
- `RealityHousehold15ConsumptionApplyPort.kt` (blob `969b7ad48f144a4f92074d07edaeb748eba80ad3`) certifica escritor `CONSUME` sólo con `RealityStore.run(scope)`, `position()`, prevalidación y readback; no tiene `CONSUME_UNDO`. No rehacer este writer ni sus 8/8 B7 PASS previos.
- `RealityStore.kt` (blob `45dc43129e851af1c5df99ae54c9391697f673c0`) permite SQL15 RW exclusivamente bajo `householdActive15DevReadOnly` fence + `MaterialWriteGate` + flag `householdStockConsumptionWritableDev`. No hay flag **separado** ni puerto de Undo; un flag de consumo NO debe otorgar permiso Undo. `requireActiveScope`, `activeApplyScope`, `BEGIN IMMEDIATE` y COMMIT/ROLLBACK provienen de `RealityStore.run`.
- `HouseholdStockConsumptionUndoDevAdmission.kt` (D) aporta `HouseholdStockConsumptionUndoDevDecision.ReviewOnly` y `HouseholdStockConsumptionUndoCurrentPosition`. Su `TrustedConsumptionUndoCommittedOriginPort` es interfaz de host futuro F; una fila SQL15 no prueba Workspace COMMITTED.
- **Brecha exacta:** crear puerto SQL15 DEV de Undo FULL con origen + revisión + fence/lease + idempotencia y readback, separado de escritor CONSUME; aceptar solo un ReviewOnly provisto por el futuro host confiable F. No usar `stock.consume.undo` nuevo, se preserva N-016.

## B2 — V4.4, V5 certificado y reutilización mínima

`docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` R12/R13/R17 (blob `4b9789004459eed6e95bc4d4f8032ee3b4c9f340`) exige separar físico/reservado/compra/pago, compensar de forma auditable y no hacer Apply por preview/receta/Chat. **CONSERVAR:** append-only SQL15 y triggers, validación cronológica y sourceRef, writer `RealityStore.run` con una sola transacción, root-owner fence de A2C1C, receipt/Undo/provenance D. **ADAPTAR:** sólo SQL15 transaccional y binding nuevo; diferencia `CONSUME_UNDO` con efecto físico positivo. **DESCARTAR:** reescritura de saldo, transacción independiente, alteración del catálogo y heredar grant N-035 de `stock.consume`. V4.4 histórica solo lectura, sin copia ni modificación.

## B3 — investigación externa, alternativas, costo y pruebas falsables

- **SQLite transacciones/contención:** https://www.sqlite.org/lang_transaction.html (BEGIN IMMEDIATE puede devolver SQLITE_BUSY); https://www.sqlite.org/isolation.html (serialización; WAL/snapshot). ADAPTAR transacción existente y rollback en un solo Scope. No retry ciego, ni asegurar COMMIT en BUSY.
- **SQLite triggers/ABORT:** https://www.sqlite.org/lang_createtrigger.html . ADAPTAR defensa SQL como segunda barrera; el trigger por sí solo no autentica la confirmación humana.
- **Android Offline First:** https://developer.android.com/topic/architecture/data-layer/offline-first . REFERENCIA de fuente de verdad local; DESCARTAR servidor, red, cola externa, sincronización o WorkManager para este acto explícito.
- **Grocy (GPL-3.0) OpenAPI:** https://github.com/grocy/grocy/blob/master/grocy.openapi.json . REFERENCIA del modelo de bookings y Undo de transacciones; no integrar código ni backend.
- **A / ADAPTAR:** nuevo SQL15 Undo writer de reversión total, con su flag exclusivo DEV y revalidación del origin + posición dentro de una transacción existente; mínimo, durable y fácil de testear.
- **B / DESCARTAR:** `UPDATE physical_balance` directo (pierde origen, receipts, revisión y posibilidad de reconciliar).
- **C / DESCARTAR:** usar writer CONSUME con cantidad negativa o una segunda conexión/transacción (rompe invariantes y atomicidad).
- **D / DESCARTAR:** API remota de inventario/recetas como ejecutor (egress, pagos, dependencia, licencias y fail-open).
Hipótesis falsables: Undo sin CONSUME origin real, sin revision vigente o con segundo Undo jamás altera SQL15; fallo de test/reapertura/SQLITE_BUSY nunca se declara éxito. N-016 sigue requerimiento del host F.

## B4 — diseño exacto para B6: cuatro rutas CANDIDATAS (NO permiso aún)

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionUndoApplyPort.kt` (NUEVO): interfaz `position(consumptionRef)` y `apply(review: HouseholdStockConsumptionUndoDevDecision.ReviewOnly, recordedAtMs, scope)`; escritor interno sobre misma SQLiteConnection, sin `BEGIN/COMMIT`. `position` reconstruye origin, contador Undo FULL y máxima revisión, verifica sourceRef, principal, generación y cantidad exacta. `apply` requiere ReviewOnly de D con payload canónico y receipt/origen exactos, cantidad FULL y no Undo previo, revision actual idéntica, eventId/intent global únicos, timestamp >= origen, saldo físico anterior válido y NO `reservationRef`. Inserta `CONSUME_UNDO` con nuevo `undoIntentKey`, readback total SQL15 con historia intacta; objeto Review por sí solo NO autentica un COMMITTED: la autoridad independiente corresponde al host F.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` (delta acotada): flag DEV exclusivo `householdStockConsumptionUndoWritableDev=false` por defecto; incompatible con `householdStockConsumptionWritableDev`, SQL12–14 y Stage. Permitir RW SQL15 sólo con flag Undo + mismo `householdActive15DevReadOnly` fence/gate. Añadir `household15ConsumptionUndoForTrustedDev(principal, generation)` que exige nuevo flag, `checkOpen`, `requireActiveScope`, `activeApplyScope===scope`, fence antes y previo a COMMIT, y revisión/readback bajo mismo lock. Nunca publicarlo en shipping ni ampliar permisos del host C.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionUndoApplyPortTest.kt` (NUEVO): fixture real SQL15 con compra MANUAL_EXPLICIT recibida, físico7/reservado2/CONSUME4 con receipt simulated **solo en test**, Undo FULL4 => físico7/reservado2, CONSUME1 + CONSUME_UNDO1 persistentes tras reopen. Tests para stale, duplicado intent/ref, dos Undo, sourceRef/principal/unit/cantidad distinto, origen ausente, consent/Context D inyectado, ROLLBACK/crash/foreign scope, separación flags y fences, overflow/8192 y timestamp. No afirmar provenance COMMITTED real de host F en tests de puerto E.
4. `.github/workflows/faro-v5-s4-w05-a2c1e-consumption-undo-atomic.yml` (NUEVO): scope exact4 y preflight padre READY, tests JVM dirigidos `:store:reality:test` con XML JUnit original, test>0, failures/errors/skips=0, artifact y guard por mismo SHA.

Restricciones: B6 sólo puede tocar esas 4 rutas bajo writer GLOBAL CAS, parent permit exacto previamente guardado. Si se necesita un quinto archivo o cambiar schema, reauditar y publicar un nuevo permiso antes de codificar. No tocar `FaroV5GenerationStoreGraph` (le corresponde a F), `HouseholdStockConsumptionUndoDevAdmission` ni código certificado. Sin comprar ni modificar stock real.

## B5 — matriz adversarial binaria previa (no pruebas ya corridas)

- **E01** original CONSUME4 de sourceRef auténtica y antes físico7 reservado2: Undo FULL4 restaura físico7 reservado2; histórico intacto y 2 eventos SQL15.
- **E02** segundo Undo con mismo `undoIntentKey`, distinto key para mismo consumo, intento parcial/over, `CONSUME_UNDO` recursivo o referencia inexistente => STOP, sin fila.
- **E03** original solo en SQL15 pero sin Workspace COMMITTED + receipt no puede saltarse host F; el puerto E no se conecta a UI, operación ni general Apply. El ReviewOnly es dato y no concede permiso productivo.
- **E04** context pointer/hostEpoch/principal/foreground revoked, stale generation/sequence, revisión cambiada, sourceRef/item/unit mismatch, consentimiento D previo revocado en host F => STOP. En E la revalidación actual interna no supone consentimiento humano comprobado.
- **E05** BUY/pago/compra sin recepción, otro principal, reservado o physical menor a reserved, origen malformed => STOP antes del insert.
- **E06** INSERT trigger ABORT, SQLITE_BUSY, fallo al readback o crash antes de commit => rollback único; crash después se reconcilia con receipt COMMITTED en F sin reinsertar.
- **E07** SQL15 solo-lectura de siempre, writer CONSUME y writer UNDO mutuamente exclusivos; SQL14/13 y Stage sin ampliación, ningún permiso shipping.
- **E08** ids/revisiones/intents históricos colisionados, timestamp negativo/backdated, bytes > 4MiB y eventos > 8192 => STOP; cero corrupción.
- **E09** root fence+material gate revocados y scope de otro RealityStore => throw/STOP, incluso antes de COMMIT; no doble transacción.
- **E10** XML original dirigido, casos>0, sin fallos/errores/omitidas; guard same SHA; B7 independiente, permiso consumido y writer RELEASED. No certificar A2C1 global ni G15.

## Puertas antes de código y salida

Esta preauditoría debe publicarse **solamente como documento** bajo writer CAS y guardia SUCCESS. En commit diferente, emitir permiso `READY` para E con 4 rutas anteriores y los blobs exactos B1–B5 `## B1` ... `## B5`; confirmar guardia del permiso y preflight exact4. B6 implementa + CI, B7 audita hechos y XML original; si CI roja, documentar RED_FIX y resolver solo esa unidad. F queda NO_READY. V2 productiva, stock real, pagos, Auto/Noche y Moto G15 OFF.
