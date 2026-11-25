# FARO V5 · W05-A1C2G1A · Admisión de origen y revisión Undo stock N-016

Fecha 2026-11-25. Branch `work/s3-night-20261102`, baseline exacta `ba8df772cff9c8e9602bd900b73b341b0f0512dd`.
**B1–B5 preparación documental, B6 NO IMPLEMENTADO, B7 NO PROBADO; aún NO_READY hasta guardia y permiso separado.**

## B1 — Evidencia exacta y dependencia

- A1C2G0 B7 **PASS scoped 18/18** con `docs/v5/FARO_S4_W05_A1C2G0_B7_PASS_SQL13_COMPENSATION_DEV_20261125.md`; código `1f21c1770618c8cb725f7c6d6a9956ddd832d689`, CI `45639876681`, guard final `45641157003` sobre cierre `ba8df772cff9c8e9602bd900b73b341b0f0512dd`, writer RELEASED 2021. NO REPETIR G0 salvo regresión dirigida.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationApplyPort.kt`: `position(receiptId)` presenta compra, principal, stockItemRef, unidad, cantidad, revisión, neto y tiempo, bajo fence de generación. `compensate` necesita nuevo Apply scope; no valida receipt de Apply ni COMMITTED Workspace.
- `RealityHousehold12StockReceiveRows.receive`: **el ID del recibo físico de stock es `request.intentKey` del `HouseholdStockReceiveReview` original**. No confundir con `ExecutionReceipt.receiptId` del Apply, que identifica la transacción original y puede ser distinto. G1 debe probar los dos vínculos.
- `HouseholdStockReceiveReview` es payload canónico `faro.household.stock.receive.v1`, contiene contexto, principal, hostEpoch, purchaseRef, stockItem, revision, intentKey, quantity y ledgerRevision original. `UndoExecuteIntent/UndoExecutePayload` en kernel:contracts ya serializa receipt original de Apply, source Review, sourceOperationId, originalPayload, targetObjectId y timestamp; **REUTILIZAR**, no generar otro codec.
- Catálogo180 `undo.execute` es N-016 con executor `undo-coordinator` y REALITY_MUTATION BINDABLE; `stock.receive` es N-035 con executor `household`. `RealityPlanningUndoExecuteBinding` NO admite stock, solo Reminders/Monthly; no heredar permisos.
- **Migración relevante:** el source `HouseholdStockReceiveReview.context.activePointer` corresponde a generación SQL12 anterior; el SQL13 ACTIVE actual puede tener otro ID/secuencia debido al cutover. Rechazar mismatch en la lineage **solo por ser generaciones diferentes es incorrecto**. G1B debe comprobar la cadena de custodia mediante completed journal/ticket y receipt original.
- G1A es la admisión tipada **READ_ONLY**, no autentica por sí misma un origen ni abre una generación. G1B verificará el original COMMITTED Workspace + Apply attempt/receipt y reinvocará admisión durante Review y final commit, con grant revocable.

## B2 — Comparación histórica y reutilización

- No copiar persistencia V4.4; conservar sus invariantes AtomicApply, PREPARED/COMMITTED, Undo, receipts, idempotencia y restart/rollback. Referencias `FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`, `FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`.
- Reusar `UndoExecuteIntent`/`UndoExecutePayload` kernel:contracts (8 pruebas históricas, no recrearlas), `HouseholdStockReceiveReview.decode`, `HouseholdStockReceiveDevContext`, `OperationContractLookupPort`, `HouseholdReviewGeneration` y `Household13CompensationCandidate` en G1B.
- G1A será un *validador tipado* con puertos de suministro confiable: fuente COMMITTED verificada, posición del recibo físico y permiso fresco. No otorgar `RealityTransactionScope` ni crear un `PreparedOperation` ejecutable todavía.
- G1B futuro montará `ReviewedConversationApplyCoordinator`, `ApplyRetryReceiptGate`, `ApplyTransactionFinalCommitGate`, recibos COMMITTED y carga de envelope previo. El host G1B deberá usar el grafo SQL13 DEV de G0, sin modificar su contrato.

## B3 — Alternativas externas verificadas y rechazo explícito

- SQLite oficial https://www.sqlite.org/lang_transaction.html: `BEGIN IMMEDIATE` puede obtener la exclusión de escritor, `COMMIT` y `ROLLBACK` son delimitaciones de la transacción única. https://www.sqlite.org/wal.html y https://www.sqlite.org/pragma.html: WAL y `synchronous=FULL` para durabilidad. **ADAPTAR principio, no escribir SQLite en G1A.**
- Android Room oficial https://developer.android.com/reference/androidx/room/Transaction.html: transacciones Room serializadas, patrón comparable; **no importar Room ni agregar dependencia**, ya existe coordinator de Reality.
- `UndoExecutePayload` propio, con UTF-8 estricto y exact bytes, es más pertinente que protocolo remoto de retry; reusar su fingerprint e idempotencia. No cloud, no pagos, sin nuevo formato de backup. 
- Rechazadas: reset/DELETE receipt original, inferir COMMITTED de la mera presencia de row SQL, equiparar receipt físico con Apply receipt, reuse automático de grant `stock.receive`, apuntar ciegamente al generation SQL12 anterior, ejecutar Undo en Review o mediante texto del modelo sin nueva aprobación.

## B4 — Unificación y tres rutas FUNCIONALES EXCLUSIVAS de G1A

1. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockUndoDevAdmission.kt` — nuevo: operación N-016 exacta, fuente tipada COMMITTED desde puerto *trusted* de G1B, posición tipada del receipt físico, validación del original `stock.receive`, stock/lote/principal, tiempo, revisión y permiso fresco. Produce `ReviewOnly` sin transacciones ni grants; revalidación refetch de origen+posición.
2. `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockUndoDevAdmissionTest.kt` — tests JUnit5 con positivos y negativos (al menos 8) sin simular que la persistencia es una prueba física.
3. `.github/workflows/faro-v5-s4-w05-a1c2g1a-stock-undo-origin-admission.yml` — workflow exact3 con preflight desde parent READY, ejecuta suite owner:household nueva, comprueba XML de forma estricta, artefacto original.

Estas rutas **no incluyen** nuevo binding host (G1B), aplicación Android, stores, generación, codec, catálogo ni migración. Si se requiere cuarta ruta, STOP y cambiar auditoría previa, no ampliar silenciosamente.

## B5 — Matriz adversarial y criterio READY

- T01 válido: origen COMMITTED fiable de `stock.receive`, receipt Apply diferente del físico, `sourceReview/Receipt` coinciden, stock revision y neto suficientes, grant fresco → `ReviewOnly`, sin SQL mutation.
- T02 sourceOperation distinta (Needs/Planner), catálogo incorrecto, ExecutorId `household`, contrato null o INTERNAL_ONLY → DENY.
- T03 payload/receipt fingerpint mal asociado, `UndoExecuteIntent.originalCreatePayload` distinto del stock review canónico, fuente sin COMMITTED confiable → DENY.
- T04 receipt físico de destino distinto del `sourceStockReceive.request.intentKey` (no confundir con `sourceReceipt.receiptId`) → DENY.
- T05 compra, item, principal, cantidad, unidad, timestamp antes del recibo, revision de stock o saldo insuficiente → DENY.
- T06 grant revocado en segunda lectura, context foreground falso, hostEpoch revocado, puntero actual ABA → DENY; no inventar validación de journal en G1A.
- T07 intento repetido y fuente compensada: posición port null o cambió → DENY / STALE, sin reescribir compensación.
- T08 bytes canónicos del `UndoExecutePayload` alterados → rechazo por decoder del kernel; no duplicar su codec.
- T09 fuente original de SQL12 y candidato de SQL13 difieren de generación por **cutover verificado en G1B**: G1A no rechaza unilateralmente por origen de generación. G1B deberá validar ticket y lineage exacto.
- T10 cero dependencia de red, Cloud, identidad/receipt expuestos por logs, pagos, port Apply o modificaciones de código existente.

**READY condicionado:** guardia documental SUCCESS sobre SHA preciso, evidence blob SHA, unidad nueva `S4_W05_A1C2G1A_STOCK_UNDO_ORIGIN_ADMISSION`, rutas exact3 en permiso, B1–B5 VERIFIED; B6 Ready y B7 pendiente. Cero autorización G1B heredada. Después B6 CI XML final y B7 antes de cualquier host G1B.

Mantener V2 productiva, Apply productivo, stock real, pagos y Auto/Noche OFF; Moto G15 NOT_RUN. No tocar main/V4.4.
