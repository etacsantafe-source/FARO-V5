# FARO V5 — W05-A2C1C: host Review→Confirm→Apply de stock.consume DEV — B1–B5

**Unidad futura:** S4_W05_A2C1C_STOCK_CONSUMPTION_REVIEW_APPLY_HOST.
**Fuente base real:** HEAD 0014b30205ee0ce577b136366723b894e898cd72, rama work/s3-night-20261102, 27/11/2026 (Argentina).
**Estado:** B1–B5 DOCUMENTADOS PARA CONTROL; B6 NOT_RUN, B7 NOT_RUN, NO_READY hasta guardia del documento y permiso nuevo separado, de cuatro rutas exactas.
**Dependencia:** A2C1A B7 PASS DEV 8/8; A2C1B B7 PASS DEV 8/8, código 09a7f72445b3a8a9c691d1a79096e6d468afb929, guardia 45928809081 SUCCESS, CI 45928809060 SUCCESS; checkpoint 0014b302 guardia 45929290638 SUCCESS, writer RELEASED 2154. No rehacer las dos unidades.
**Frontera:** SOLO DEV aislado, sin stock real, pagos, V2 productiva, FARO Auto/Noche, uso desatendido ni Moto G15 físico.

## B1 — Evidencia / brecha específica

1. Source: v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockConsumptionReview.kt (blob da763b477078d01645416eef5045b542bc5930cf). Review stock.consume codificado canónicamente con principal, generation(id,sequence), hostEpoch, item, unit, quantity, sourceRef, intentKey, revision; reservationRef debe ser null. NO autoriza ejecución.
2. Source: HouseholdStockConsumptionDevAdmission.kt (blob d878c7b2430f340478f66be25e47656b3eadcb0f): descriptor N-035 (household / REALITY_MUTATION / BINDABLE), consentimiento por acto, fuente auténtica, cantidad <= physical-reserved y <= restante de source; revalidación de posición/contexto.
3. Source: RealityHousehold15ConsumptionApplyPort.kt (blob 969b7ad48f144a4f92074d07edaeb748eba80ad3): Apply SQL15 transactional-scope, sourceRef de compra manual recibida, rechazo de reserva, revisión e intentKey; sin BEGIN/COMMIT independiente. RealityStore ofrece household15ConsumptionForTrustedDev solo tras opt-in material gate DEV; default lectura.
4. Source: v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt (blob 537fa41ece4218e0be9ec2aea992bb5f54e4a1fb): existe sesión RW SQL14 con raíz lease y port reserva, pero solo inspectActiveHousehold15DevReadOnly para SQL15, SIN sesión SQL15 Apply ni montura owner host; su openValidatedV2Internal, openStores, preflight strict y Auxiliary tienen ramificaciones explícitas hasta SQL14. Falta abrir cinco stores SQL15 ACTIVE en modo RW DEV con ticket auténtico.
5. Source: FaroV5Household15Evidence2DevJournaledCutover.kt (blob d54add2ecba7ecee7103dbccbfbc439113ce42fa): hasCompletedSelection(root, selected) exige ticket SQL15, journal COMPLETED, materialWritesAccepted, pointer completo, replay fence, root y estado de cutover; NO basta un boolean inventado.
6. Patrón certificado: S4W05StockReservationDevApplyHost.kt (blob e5563ba9522f9d8c11d5931b2e760b89650912cf): Review Workspace → explicit Confirm → Approved → Applying → AtomicApply + receipt durable/retry. Test de host A2B1B1 y B7 ya PASS DEV; NO transferir sus grants.
7. **Brecha concreta:** falta la composición graph SQL15 ACTIVE/host DEV para conectar Review original con el writer B, mantener root-lease, póliza humana y PREPARED/COMMITTED/UNKNOWN; no falta el SQL writer, por lo que NO repetir A2C1B B6. No existe permiso A2C1C READY en este HEAD.

## B2 — Comparación V4.4 y reuse mínimo

Matriz V4.4: docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md R12/R13 y docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md. Conservar recuperación durable PREPARED/COMMITTED, idempotencia por intent y receipt, Undo compensatorio; V4.4 se inspecciona, NO se modifica ni copia.
Reusar exclusivamente ReviewedConversationApplyCoordinator, ApplyRetryReceiptGate, PreparedPlanEnvelopeCodec, ApplyTransactionExecutor, gates de autorización/final-commit, session graph y root lease del host SQL14. Sustituir solo operation=stock.consume, request=HouseholdStockConsumptionReview y port=household15ConsumptionForTrustedDev. No duplicar coordinator o owner, ni hacer segundo commit, ni usar efectos de compra, reserva, Cocina o pagos como consentimiento.
SQL15 consumo disminuye físico NO reservado; RESERVED permanece separado. A2C1D podría cubrir Undo compensatorio, pero no debe inventarse stock.consume.undo (no existe descriptor).

## B3 — Investigación externa y decisiones (27/11/2026)

Fuentes primarias comprobadas al preparar este documento:
- SQLite BEGIN IMMEDIATE / SQLITE_BUSY: https://www.sqlite.org/lang_transaction.html. ADAPTAR: serializar en un solo BEGIN IMMEDIATE de RealityStore, rollback de toda la operación si falla cualquier gate/commit.
- Android offline-first / local source of truth: https://developer.android.com/topic/architecture/data-layer/offline-first. ADAPTAR: los datos locales SQLite y Workspace COMMITTED son autoridad; DESCARTAR servidor / red / cola remota y jobs background.
- Android Room @Transaction: https://developer.android.com/reference/androidx/room/Transaction.html. REFERENCIA para límite de transacción, NO migrar el stack a Room ni anidar otra transacción.
- Grocy OpenAPI https://github.com/grocy/grocy/blob/master/grocy.openapi.json, bookings undo y recipes consume. REFERENCIA semántica: consumo, reserva y deshacer como eventos distintos; DESCARTAR el consumo automático de recetas y su dependencia externa.
Alternativa A: host local reutilizando coordinator certificado y transacción SQL15 (ADAPTAR: mínima delta). B: escribir saldos desde una Activity o evento de chat directo (DESCARTAR: sin Review/receipt, falla ante replay). C: saga entre cinco stores o servicio remoto por cada acto (DESCARTAR: multiplica fallos/egress, rompe atomicidad). Riesgos negativos que se agregan: SQLITE_BUSY, duplicación de recibos tras crash, upgrade indebido de ACTIVE read-only, sourceRef por compra no recibida, operación stock.reserve inyectada como stock.consume.

## B4 — Diseño y cuatro rutas exactas candidatas

1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt
2. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockConsumptionDevApplyHost.kt (NUEVA)
3. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockConsumptionDevApplyHostTest.kt (NUEVA)
4. .github/workflows/faro-v5-s4-w05-a2c1c-consume-apply-host.yml (NUEVA)

**Grafo (ruta 1):** crear Household15ConsumptionDevSession DEV scoped con Workspace + identities/attempts/receipts/transactions/commits/dependencies, consumption port y snapshot. Abrir exclusivamente vía openActiveHousehold15DevForConsumptionHost(root,pinned,gate,verifier,ownerAdmission): FileChannel raíz finance9-dev-root.lock, tryLock, nlink=1, no symlink, comprobar selección COMPLETED real con FaroV5Household15Evidence2DevJournaledCutover.hasCompletedSelection y pointer == pinned, fenced owner epoch. Requerir strict schema15 en cinco stores y manifest/workspace canónicos (no tocar archivos productivos). Propagar householdActive15Dev=true y householdStockConsumptionWritableDev=true por openValidatedV2Internal/openStores y RealityStore, con exclusividad frente a todos los escritores 12/13/14. ACTIVE14/RW y ACTIVE15/RW nunca coexisten. Auxiliar ReceiptV4 activo bajo el mismo fence; NO tratar SQL15 ACTIVE como SEALED STAGED ni como immutable=1. Reusar funciones strict preflight ya presentes (householdActive15Dev) y los flags DEV existentes. Si una condición de root/ticket no se puede probar, STOP sin fallback a stage o writer alterno.

**Host (ruta 2):** adaptar S4W05StockReservationDevApplyHost sin heredar grants. Mismo root lease, workspace fuente auténtica, generación id+secuencia, principal y hostEpoch. submit(chat,localRequestId,item,quantity,sourceRef) crea SOLO Review PENDING canónica con fingerprint/IdempotencyKey y descriptor oficial stock.consume N-035; no aplica. executeAfterExplicitConfirmation requiere Workspace APPROVED + foreground + consentimiento NUEVO y vigente para esta operación/item/sourceRef, descriptor y epoch, actualización de estado APPLYING, transacción real del session y recepción durable COMMITTED. En B6 distinguir consentimiento de submit/confirm y rechazar cambio de fuente antes de COMMIT. Retry de identidad idéntica con receipt COMMITTED devuelve ese receipt, nunca duplica CONSUME. UNKNOWN se reconcilia únicamente contra recibo COMMITTED real. Revalidate posición/ledgerRevision dentro de la misma transacción. Close revoca futuras operaciones. No exponer UI shipping ni llamador de Chat/receta automática.

**Test y CI (rutas 3–4):** fixture de cinco stores FBS2v12/SQL15 con cutover journaled C2 COMMITTED REAL (no fake completedDevSelection), compra manual recibida con sourceRef igual a compra, physical7 reserve2 then consume4 -> physical3/reserved2, receipt durable. Android JVM directed :app:testV2CoreDevUnitTest (verificar source-set disponible antes de B6; si la clase de test debe ir en otro source-set, reauditar ruta y permiso). XML JUnit original test>0, failures/errors/skipped=0, SHA y guard SUCCESS. El test no modifica móvil ni dinero.

**No autoriza B6:** las rutas son CANDIDATAS hasta publicar esta preauditoría, ver guardia SUCCESS del SHA documental, emitir en otro commit permiso A2C1C READY (exact4 con blobs de B1–B5 y last_code_review A2C1B PASS), confirmar guardia del permiso y preflight antes del primer archivo de código. Un cambio extra requiere reauditoría.

## B5 — Adversarial binario antes de B7

- C01: Review PENDING/CANCELLED y preview/Chat no escriben stock, Receipt ni WAL; debe haber confirmación humana APPROVED para CONSUME.
- C02: cutover SQL15 sin ticket COMPLETED genuino, pointer ABA, generation/sequence obsoleta, hardlink/symlink/root lease ocupado y modo immutable activo -> STOP antes del writer; root lease liberado al cerrar.
- C03: exact sourceRef MANUAL_EXPLICIT + recibos misma unidad/principal; compra pagada pero no recibida, sourceRef ajena, unidad errada o source agotada -> STOP.
- C04: físico7 / reservado2 / consumo4 => físico3 / reservado2 / disponible1, CONSUME único + receipt COMMITTED; cantidad6, negativa, overflow y reserved consumption -> rechazo sin Apply.
- C05: Workspace no COMMITTED/APPROVED, catálogo N-035 sustituido, payload/envelope/fingerprint alterado, operación reserve inyectada, principal o epoch cambiado -> STOP sin receipt.
- C06: doble tap/retry idéntico retorna mismo COMMITTED receipt; distinto intent con stale revision se rechaza; UNKNOWN sin recibo NO repite SQL15.
- C07: rollback y crash antes de commit (incluyendo SQLITE_BUSY) no dejan consumo ni receipt parcial; crash posterior solo reconciliar desde COMMITTED; idempotencia tras reopen.
- C08: revocación de política justo antes de commit, foreground false, cierre de host, pérdida de root/fence -> fail closed; no cancelar transacción de otro owner.
- C09: finance/commercial/web/auxiliary y ledger SQL12/13/14 intactos; pagos y shipping OFF; no stock.consume.undo inferido, ni G15 físico.
- C10: original JUnit XML>0, failures=errors=skipped=0, guard mismo SHA, B7 independiente. No dar PASS global por ocho tests del port B.

## Entrega documental

Estado de salida: B1–B5 documentados y listos para guardia; permit actual A2C1B BLOCKED y A2C1C NO_READY. B6/B7 NO EJECUTADOS. Las pruebas C01–C10 son criterios futuros, no hechos observados. Si falta CI/permiso/fence, STOP, preservar certificaciones previas. A2C1D–F siguen dependientes y NO_READY. Writer liberar CAS antes de CI.