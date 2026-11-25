# FARO V5 · W05-A1C2G0 · Admisión de escritura SQL13 ACTIVE DEV para N-016

Fecha: 2026-11-25. Rama: `work/s3-night-20261102`. Baseline exacta: `2fb75f7848579eac04c74b8d0f2f9edaf1a845e5`.
**Estado: B1–B5 DELTA PREPARADO; NO_READY; B6 NO AUTORIZADO; B7 NO EJECUTADO.**
No es un permiso funcional. El último B7 certificado sigue siendo A1C2F, CI 45629774472 XML 31/31 y guardia 45629774384 SUCCESS. Stock real, Apply productivo y V2 productiva OFF.

## Unidad y corte de dependencias

A1C2G (N-016 `undo.execute` compensatorio) se divide por una dependencia técnica comprobada:
- **A1C2G0:** admitir, exclusivamente en la generación SQL13 ACTIVE DEV y dentro del único coordinador de Apply, un *puerto interno* de compensación. Debe seguir siendo inaccesible sin un owner/host con grant fresco. **Esta unidad no conecta chat, no crea Review, no ejecuta Undo ni genera recibos.**
- **A1C2G1:** contrato tipado de solicitud, Review→Confirm→Apply N-016, receipt padre/COMMITTED, revalidación y reconciliación; solo después de B7 de G0 y un nuevo permiso propio.
- Luego A1C3 Android DEV y A1C4 aceptación global. Ninguna unidad recibe READY heredado.

## B1 — Evidencia en HEAD y criterio binario

1. A1C1 `S4W05StockReceiveDevApplyHost` y `RealityHousehold12StockReceiveApplyPort`: recepción física N-035 ya B7 PASS 8/8, pero opera **SQL12** mediante `openActiveHousehold12DevForStockReceiveHost`; no reutilizar el N-035 como permiso de Undo. Fuente: `docs/v5/FARO_S4_W05_A1C1_B7_PASS_STOCK_RECEIVE_DEV_20261125.md`.
2. A1C2A/B/C/D/E/F: SQL13 STAGED, FBS2v10, conservación de compensaciones, replay de cinco stores, lectura SQL13 ACTIVE, y cutover Evidence2 journaled ya certificados por unidad. A1C2F código `e581dcba2191b988c90ca6271b1bf9c209d13eac`; B7 documental `2fb75f7848579eac04c74b8d0f2f9edaf1a845e5`. 31/31 XML, guardia SUCCESS. **No repetir estas pruebas salvo regresión dirigida.**
3. En `RealityStore.kt`, `householdActive13DevReadOnly` prohíbe explícitamente `household12WritableDev`; el opener SQL13 usa conexión SQLite read-only, su init valida `captureConnection`, y `snapshotActiveHousehold13RowsForTrustedHost` únicamente lee. En `FaroV5GenerationStoreGraph.kt`, `openActiveHousehold13ForHeldDevHost` es lectura. En `FaroV5Household13ActiveDevReadHost.kt` se usa el lock de raíz y la secuencia exacta de pointer, pero no se expone Apply.
4. `RealityHousehold13CompensationSchema.kt` ya define tabla STRICT, `receipt_id UNIQUE`, triggers append-only UPDATE/DELETE bloqueados y chequeos de procedencia. `RealityHousehold13CompensationRowsPort.kt` valida compensaciones, secuencia cronológica y saldo neto, pero su `restoreOrVerifyTrustedSealedStageThirteen` es **solo STAGED**. No convertirlo en escritura ACTIVE.
5. Catálogo180: `undo.execute` = N-016, `undo-coordinator`; `stock.receive` = N-035. `RealityPlanningUndoExecuteBinding` cubre Planner, NO stock. La recepción original exige un receipt original COMMITTED atribuible a la compra confirmada. Condición de paso: ningún atajo de ACL, ningún port accesible desde UI/modelo, ningún SQL13 ACTIVE sin ticket COMPLETED legítimo.

## B2 — Comparación y reutilización sin rehacer

- V4.4 P02/P03: preservar AtomicApply, PREPARED/COMMITTED, Undo/recovery e idempotencia, no copiar la persistencia legacy. Referencias: `FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`.
- V5: reutilizar `MaterialWriteGate`, único `RealityStore`, `ApplyTransactionScope`, `FileActiveGenerationPointer`, root lease, `FaroV5Household13Evidence2DevJournaledCutover.hasCompletedSelection`, `RealityHousehold13CompensationRowsPort.captureConnection` y controles `SQLITE_OPEN_NOFOLLOW`, WAL ACTIVE, `PRAGMA foreign_keys`, `synchronous=FULL`.
- A1C1 aporta el patrón de sesión de cinco stores y la protección contra ABA/revocación, **no** el permiso N-016 ni el cálculo SQL12 de bruto recibido. Los readers SQL13 certificados siguen inalterados.
- Delta: un modo SQL13 **separado** de escritura interna y un único método `compensate(..., RealityTransactionScope)` sin bypass de la transacción Apply. No mezclar la firma pública de snapshot STAGED con el runtime ACTIVE.

## B3 — Alternativas externas y decisión

- **A ADAPTAR:** SQLite `BEGIN IMMEDIATE`, actualización CAS de `household_stock_receipt_state.revision` y `INSERT` de compensación en la misma transacción de Apply; sin `COMMIT` desde un puerto hijo. SQLite explica que `BEGIN IMMEDIATE` reclama el escritor, que WAL admite lectores concurrentes y que `synchronous=FULL` sincroniza cada commit WAL. Fuentes primarias: https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/wal.html y https://www.sqlite.org/pragma.html.
- **B REFERENCIA (NO incorporar dependencia):** AndroidX Room, `useWriterConnection/immediateTransaction`, serializa escritura y desalienta transacciones largas con operaciones no SQLite. Referencias: https://developer.android.com/reference/androidx/room/RoomDatabase y https://developer.android.com/topic/performance/sqlite-performance-best-practices. FARO conserva su driver y coordinador existentes; no sumar Room ni reescribir owners.
- **C REFERENCIA (NO dependencia ni red):** Square documenta idempotency keys para efectos repetidos; aplicar el principio de `intent_key` y recibo existente en runtime local, no integrarse con Square ni hacer pagos. https://developer.squareup.com/docs/build-basics/common-api-patterns/idempotency.
- **D DESCARTAR:** UPDATE/DELETE del receipt histórico, compensación por saldo global sin receipt padre, transacción SQLite aislada de Apply, desbloqueo por existencia de archivo SQL13, abrir `immutable=1` en ACTIVE, reutilizar `stock.receive` o ejecutar Undo al recibir texto de chat. Todas violan recuperación, atribución o separación de autoridad.
- Licencias/coste: SQLite dominio público y AndroidX Room Apache-2.0 usado aquí solo como patrón documental; ninguna dependencia ni servicio externo se agrega. Hipótesis falsable: ante ABA, revocación o cambio de revisión, la DB queda idéntica y no aparece receipt de compensación.

## B4 — Rutas funcionales candidatas **exactas solo para G0** (no autorizadas aún)

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationApplyPort.kt` — nuevo port interno; verifica receipt, identidad, propiedad, cantidad total, doble Undo y neto en el ledger; no emite grants.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` — modo SQL13 writable separado del read-only (fail closed por defecto), disponible solo con misma transacción activa, token/epoch y fence; preserva los modos anteriores.
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` — factory aislada con root lease, ticket SQL13 COMPLETED, pointer completo, cinco stores, gate compartido y cierre atómico; ningún opener productivo.
4. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationApplyPortTest.kt` — nuevas pruebas SQLite reales/negative admissions, sin suplantar prueba Android.
5. `.github/workflows/faro-v5-s4-w05-a1c2g0-sql13-undo-write-admission.yml` — preflight exacto, JUnit XML originales, conteos cero failure/error/skip, artefacto y SHA.

No modificar `RealityHousehold13CompensationRowsPort.kt` ni codec FBS2v10 salvo dependencia nueva demostrada: en tal caso **STOP** y ampliar auditoría/permiso; nunca colar una ruta extra. G1 definirá por separado sus rutas para typed Review, host y pruebas.

## B5 — Auditoría adversarial **propuesta; ninguna prueba ejecutada**

- T01: abrir SQL13 ACTIVE solo tras ticket Evidence2 cutover COMPLETED legítimo, cinco stores coherentes y exacto full pointer; SQL12/SQL11/Evidence1/v9/ticket incompleto -> STOP antes de writable SQLite.
- T02: reader SQL13 anterior continúa read-only; constructor sin opt-in falla; etapa STAGED no puede invocar port ACTIVE.
- T03: compra10, receipts7+3, compensación7 → neto3 con historia original intacta; luego recepción7 → neto10 bajo futuro G1 (esta última parte queda **no ejecutable** en G0).
- T04: misma `receipt_id` compensada dos veces, `intent_key` duplicada, principal/compra/unidad/cantidad mismatch -> STOP sin mutación.
- T05: recepción consumida, saldo neto insuficiente o revisión obsoleta -> STOP; sin cantidades físicas negativas.
- T06: crash/precondition antes del COMMIT del Apply => rollback de `revision` y fila; confirmación COMMITTED sin confirmación de UI se resolverá por receipt original sin repetir efecto en G1.
- T07: no aceptar transacción falsa, reutilizada u originada en otro RealityStore, ni llamada fuera del WriteCoordinator.
- T08: pointer ABA, root lease ocupado, cambio de hostEpoch, pérdida de foreground/grant -> STOP; comprobar de nuevo en frontera del commit (G1 completará el grant).
- T09: sidecars WAL, reapertura y `synchronous=FULL`; histograma de receipts+compensaciones persiste y port read-only coincide con el nuevo estado.
- T10: regresión dirigida de A1C2F cutover, A1C2C row readback y A1C1 recepción, sin alterar los B7 históricos.

**Criterio STOP para READY:** verificar rutas reales y viabilidad con test runner; validar el SHA/blob de este B1–B5, guardia documental SUCCESS, permiso nuevo G0 exact5 y preflight en cada ruta. Ningún B6 mientras `FARO_PROTOCOL_WORK_PERMIT.json` siga BLOCKED.

## Siguiente y límites

1. Ejecutar guardia documental de este checkpoint; si SUCCESS y código revalidado, publicar READY nuevo para G0 en otro commit **solo documental**. 2. G0 B6/CI/XML/B7 scoped. 3. G1 N-016 Review/Apply con receipt histórico y compensación, recovery; 4. A1C3 Android; A1C4 global. Work credits no son un requisito técnico para esta preauditoría.

Estado físico Moto G15: NOT_RUN. Sin stock real, pagos, APIs pagas, Auto/Noche, main/V4.4 o activación de V2. El propietario del writer debe liberarlo tras este checkpoint.
