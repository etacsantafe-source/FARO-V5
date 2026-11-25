# FARO V5 · W05-A1C2G1B · B7 PASS — SQL13 stock undo Review/Confirm/Apply DEV
Fecha: 2026-11-25. Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
**Alcance:** G1B DEV aislado, N-016 `undo.execute`. NO producción, no activación de stock real. A1C3 Android y A1C4 global todavía pendientes.

## B7 — Evidencia CI y JUnit originales

- Código funcional final: `bb6daaf118f280ff8a01e964299a561e961d7dc0` (padre READY con B1–B5 verificados y RED_FIX4 formalizado). Trailer `FARO-UNIT:S4_W05_A1C2G1B_TRUSTED_STOCK_UNDO_APPLY`.
- Guardia del código: `45667290974` — COMPLETED **SUCCESS** sobre SHA exacto `bb6daaf118f280ff8a01e964299a561e961d7dc0`; tres rutas exactas de G1B dentro del permiso. No tocar G0/G1A.
- CI G1B: `45667290966` — COMPLETED **SUCCESS** sobre SHA exacto `bb6daaf118f280ff8a01e964299a561e961d7dc0`; job `139392773051` SUCCESS; Gradle `:app:testDebugUnitTest` **BUILD SUCCESSFUL in 4m 42s**; preflight `Verify exact3 B6 paths and READY parent` SUCCESS.
- Paso `Verify two original JUnit XML files` SUCCESS: `S4W05StockUndoDevApplyHostTest` **9/9**, `S4W05StockReceiveDevApplyHostTest` **8/8**. En ambos XML originales **0 failures, 0 errors, 0 skipped**. Total **17/17 PASS** y 17 testcase. Artefacto ZIP `w05-a1c2g1b-stock-undo-xml` ID `15048417322`, 1.657 bytes, subida SUCCESS, no expirado; XML descargados y comprobados independientemente.
- Anteriores RED_FIX (no repetir): `84ceeda8` CI `45647661813` 12/17; `bf9f2d07` CI `45663321658` 12/17; `879b9fb2` CI `45664677892` 12/17; `8f2a1700` CI `45665927863` 12/17. Sus diagnósticos e invariantes figuran en docs de RED_FIX 1–4. El último mostró máscara `11110111`: comparación de contexto entero incorrecta tras BeginApply.
- Causa corregida: `ConversationPlanReviewPolicy.evaluate()` incrementa `revision` ante transición aceptada. `afterBeginApply` verifica identidad estable (conversationId, reviewId, authorityToken, planFingerprint), nueva revisión **exactamente +1** bajo CAS, review/envelope canónicos, transactionId y estado APPLYING, admisión G1A revalidada, foreground/owner N-016 policy. Se eliminó toda instrumentación temporal (no diagnostics en código/tests finales).

## B6 — Invariantes certificados dentro del alcance

- G1B usa el opener **ya certificado G0** `openActiveHousehold13DevForUndoHost` con lock de root y SQL13 Evidence2 completed journal. N-016 `undo.execute` no hereda N-035 `stock.receive`; requires explicit fresh Review→Approve→Confirm→Apply.
- Origen exige Workspace Review N-035 **COMMITTED** y envelope sealed validado, Reality receipt exacto y Apply attempt COMMITTED; no equipara `ExecutionReceipt.receiptId` con receipt físico `receive.intentKey`. G1A valida compra, item, principal, cantidad, unidad, timestamp y balance/revisión.
- Admisión G1A se valida **antes** de transacción, se promociona solamente para identity+node de Apply; SQL13 G0 `compensate` valida saldo y revisión CAS bajo `RealityTransactionScope`, inserta evento inverso append-only y nueva transacción/receipt; historia original preservada. Final gate consume el grant con recibo válido, no abre Workspace dentro de SQL13. Idempotencia sin doble compensación.
- Pruebas de G1B incluyen 7→3, receipt independiente, origen falso no COMMITTED, falta de aprobación/grant, revisión stale, doble Undo y reinicio, principal/epoch/puntero ABA, UNKNOWN con receipt y reconciliación, rollback sin efecto parcial. Regresión N-035 8/8 intacta.
- Permiso de esta unidad a BLOCKED por consumo, stages B6 COMPLETE_SCOPED/B7 PASS_SCOPED, `last_code_review` PASS en código final. No implica cierre total W05-A1.

## Continuidad y límites

**Siguiente:** W05-A1C3 Android DEV aislado; luego W05-A1C4 aceptación integral. Preparar permiso propio de cada unidad, sin repetir G1B. Moto G15 físico NOT_RUN.
V2 productiva OFF, stock real OFF, pagos OFF, Auto/Noche OFF, main y V4.4 intactos. El writer debe quedar RELEASED en la época 2033 y la guardia final documental PASS sobre el checkpoint de cierre antes de dar por terminado este turno.
