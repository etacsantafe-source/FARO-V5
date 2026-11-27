# FARO V5 · W05-A2C1D · admisión de Undo compensatorio del consumo SQL15 — B1–B5

**Unidad propuesta exacta:** `S4_W05_A2C1D_CONSUMPTION_UNDO_ADMISSION`.
**Base auditada:** `b9e87c7c4a618b33736a0a5a4ecafb496411f72e` en `work/s3-night-20261102`; 27/11/2026 (Argentina).
**Estado inicial:** B1–B5 DOCUMENTADOS, pendientes de guardia de este commit; B6 NOT_RUN, B7 NOT_RUN, permiso A2C1D NO_READY. A2C1E/F no autorizadas.
**Predecesor:** A2C1C B7 PASS DEV 5/5, código `b322e5d166876c106f71eac9a734c62d31b55eda`, CI `45939930692` SUCCESS y guardia `45939930719` SUCCESS; checkpoint `b9e87c7c4a618b33736a0a5a4ecafb496411f72e`. No repetir A2C1A/B/C.
**Frontera:** únicamente preauditoría y admisión Review para deshacer `stock.consume` bajo DEV aislado. No ejecuta CONSUME_UNDO, no integra UI shipping, no stock real ni pagos, V2 productiva/Auto/Noche/G15 OFF.

## B1 — evidencia, límites existentes y brecha comprobada

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt` (blob `4af7a385640977f6e984d5a49878926b7ad60287`): `RealityHousehold15ConsumptionType` ya enumera `CONSUME` y `CONSUME_UNDO`. La validación exige que el Undo haga referencia a un CONSUME anterior, mismo principal/item/unit/sourceRef/reservationRef, cantidad positiva <= pendiente de ese consumo, timestamp monotónico, revisión global única y físico >= reservado. El readback es validador, NO ejecución de Undo.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionApplyPort.kt` (blob `969b7ad48f144a4f92074d07edaeb748eba80ad3`): el writer certificado A2C1B solo hace INSERT `CONSUME`, no `CONSUME_UNDO`; usa una sola transacción de RealityStore.
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockConsumptionDevApplyHost.kt` (host A2C1C): `stock.consume` usa Review→confirmación explícita→Apply durable, fence/pointer y recibo COMMITTED; ningún camino de Undo de consumo ni autoridad heredada.
4. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockUndoDevAdmission.kt` (blob `1c7a9815b7f17c2324f5bcbed4a9205c365cc448`): **solo** acepta N-016 `undo.execute` cuyo `sourceOperationId == stock.receive` y prueba COMMITTED original; no sirve directamente como permiso para deshacer `stock.consume`.
5. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionReview.kt` (blob `da763b477078d01645416eef5045b542bc5930cf`): request canónico original incluye sourceRef, principal, generation+sequence, hostEpoch, item, unidad, cantidad, intentKey y revisión; el payload se valida por decodificación canónica.
6. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionDevAdmission.kt` (blob `d878c7b2430f340478f66be25e47656b3eadcb0f`): Review de consumo comprueba fuente real, consentimiento nuevo, cantidad disponible y descriptor N-035. Este consentimiento NO se traslada a deshacer.
7. `v5/spec/generated/contract-baseline.tsv`: N-016 `undo.execute` existente (ejecutor `undo-coordinator`) y N-035 `stock.consume` (ejecutor `household`); no agregar `stock.consume.undo` por inferencia. B1 de D debe rechazar descritores divergentes sin ampliar catálogo.

**Brecha verificable:** falta una admisión de Undo de `stock.consume` con origen COMMITTED auténtico y posición SQL15 actual que emita únicamente Review, sin ser capaz de escribir. No se toca writer SQL15 ni host en D. Si N-016 no contempla semánticamente esa procedencia según su contrato, B5=STOP y reauditar antes del permiso; no forzar descriptor.

## B2 — comparación V4.4 y reutilización explícita

`docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` (blob `4b9789004459eed6e95bc4d4f8032ee3b4c9f340`) R12/R13/R17: stock físico, reservado, comprado y pagado son estados diferentes; preview no consume; Undo durable exige origen/revisión/receipt/recovery y no un `UPDATE balance` directo. V4.4 se lee sin escribir. ADAPTAR solo patrones ya certificados de `HouseholdStockUndoDevAdmission` G1A, `UndoExecuteIntent`, contrato N-016 y provenance de recibos G1B, pero sustituir su fuente `stock.receive` por un `stock.consume` COMMITTED real. **NO reutilizar** recibos, grants, tests ni host como si fueran el Undo de consumo.

## B3 — investigación externa real, alternativas y falsación

Fuentes primarias verificadas 27/11/2026:
- SQLite `BEGIN IMMEDIATE` y `SQLITE_BUSY`: https://www.sqlite.org/lang_transaction.html . REFERENCIA para E: único writer transaccional, rollback al fallar; sin prometer éxito de COMMIT tras contención.
- SQLite errores/contención: https://www.sqlite.org/rescode.html . REFERENCIA para E y pruebas negativas de BUSY y reinicio.
- Android offline-first: https://developer.android.com/topic/architecture/data-layer/offline-first . ADAPTAR fuente de verdad local y reconciliación durable; DESCARTAR sincronización de red/background para deshacer.
- Grocy OpenAPI: https://github.com/grocy/grocy/blob/master/grocy.openapi.json ; `/stock/bookings/{bookingId}/undo` y `/stock/transactions/{transactionId}/undo`. REFERENCIA semántica; NO copiar código ni incorporar su servidor (proyecto GPL-3.0), ni asumir que su endpoint prueba los fences FARO.

Opciones confrontadas: **A / ADAPTAR:** política N-016 de Undo Review con origen COMMITTED `stock.consume` y posición SQL15 inmutable durante la revisión, revalidable al final; sin escritura en D. **B / DESCARTAR:** permitir Undo solo por row id/evento o botón de UI sin vincular receipt COMMITTED, pues permite eventos forjados y doble compensación. **C / DESCARTAR:** usar directamente el admission de Undo de `stock.receive` cambiando a ciegas el `sourceOperationId`; sus invariantes de posición SQL13/recepción no equivalen a saldo consumido. **D / DESCARTAR:** consumir API Grocy/servidor por acto, rompe offline/local-only y autorización. Hipótesis falsable: un CONSUME observado en SQL15 sin Workspace COMMITTED + Reality receipt/attempt coincidentes jamás habilita Review de Undo.

## B4 — delta mínima, límites y rutas CANDIDATAS

Delimitación de la cadena para evitar rehacer trabajo:
- **A2C1D (actual):** `HouseholdStockConsumptionUndoDevAdmission`, solo tipado de solicitud, prueba de origen COMMITTED y posición, consentimiento específico N-016 y ReviewOnly; sin SQL ni Apply.
- **A2C1E (futura):** writer SQL15 `CONSUME_UNDO` en transacción existente, revision/intent/source/quantity bound, efecto físico compensatorio controlado; su propio B1–B5 + permiso.
- **A2C1F (futura):** host Review→Confirm→Apply/reconcile para Undo de consumo, receipt COMMITTED y CI integral; su propio B1–B5 + permiso. Este reparto es un plan de preauditoría, no código ni permisos concedidos.

Rutas exactas candidatas SOLO para A2C1D, tras guardia del documento y permiso nuevo:
1. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionUndoDevAdmission.kt` (NUEVA).
2. `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionUndoDevAdmissionTest.kt` (NUEVA).
3. `.github/workflows/faro-v5-s4-w05-a2c1d-consumption-undo-admission.yml` (NUEVA).

Contrato de diseño D: `UndoExecuteIntent` original sourceOperationId=`stock.consume`, `targetObjectId` igual al intent/evento de consumo; sourceReview, sourceReceipt, planFingerprint y payload del `HouseholdStockConsumptionReview` deben coincidir tras cotejo en Workspace COMMITTED, Reality attempt COMMITTED y receipt durable. Proponer interfaces `TrustedConsumptionUndoCommittedOriginPort`, `TrustedConsumptionUndoPositionPort` y `ConsumptionUndoConsentPort`, inyectadas, NO implementadas contra DB en D. Contexto DEV seleccionado, principal, pointer generation+sequence, hostEpoch, revisión y nuevo intentKey obligatorios; verificar snapshot actual antes/después y si varía fail closed. El `remainingUndoable` se deduce de CONSUME original menos todos sus `CONSUME_UNDO` verificados; `reservationRef` siempre null. No crear autoridad a partir de una fila SQL aislada; no adelantar firma de método E sin comprobar el contrato de N-016. La admisión **nunca** entrega `MaterialWriteGate`, transaction scope, grant ni Apply.

## B5 — pruebas adversariales previstas, no ejecutadas

- D01: origen `stock.consume` sin Workspace Review COMMITTED o sin Reality receipt/attempt COMMITTED auténtico ⇒ ORIGIN/BLOCKED; una fila SQL15 aislada no basta.
- D02: `sourceOperationId` de `stock.receive`, `stock.reserve`, `undo.execute` o payload distinto de CONSUME original ⇒ BLOCKED.
- D03: `targetObjectId` diferente del CONSUME original, receiptId confundido con consumptionRef, fingerprints/transacción discordantes ⇒ BLOCKED.
- D04: Undo previo total, parcial que supera resto, cantidad cero/overflow, reserved ref != null, fuente/procedencia/owner/unit distinta ⇒ BLOCKED.
- D05: principal, hostEpoch, pointer ABA, generación secuencia, foreground, sourceRef o ledgerRevision cambiados entre primer y segundo read ⇒ STALE/BLOCKED.
- D06: contrato N-016 inexistente, executor/capability/exposure alterados o consentimiento ausente/revocado entre reads ⇒ BLOCKED; consentimiento de consumo no autoriza Undo.
- D07: preview/review/invalid intent/duplicate id jamás insertan CONSUME_UNDO ni receipt; no UI, Chat, receta, pago ni stock real activados.
- D08: saldo con físico7 reservado2 consumo4 → físico3 reservado2; Undo válido de4 restauraría físico7 reservado2 y la misma fuente, *únicamente* en E/F tras transacción y recibo. En D no mutación.
- D09: ledger de 8.192 eventos, SQLITE_BUSY, reinicio/crash y rollback son riesgos de E/F, no certificar como probados en D; D debe fail closed si no puede reconstruir posición.
- D10: CI dirigida Android/JVM para los tests de admisión, JUnit XML original, cero fallas/errores/skip, guard del mismo SHA y B7 independiente. Dos fallos de la misma hipótesis ⇒ volver a B2/B3/B5.

## Salida / puertas de autorización

**Este documento no autoaprueba B6.** Primero publicar bajo writer CAS y verificar guardia documental. Después, en **otro** commit solo documental, se podrá considerar `FARO_PROTOCOL_WORK_PERMIT.json` READY para el unit id de D y **las 3 rutas exactas**, con evidencia SHA/blob de B1–B5, sin extender a E/F. Comprobar `scripts/quality/faro-protocol-gate.py --mode preflight` para las tres rutas antes de todo código. B6 requiere writer GLOBAL CAS y CI real; B7 auditoría final y consumo de permiso. Al terminar cada turno checkpoint remoto verificado y writer RELEASED. Si no se logra guardia o escritor, BLOCKED. 
