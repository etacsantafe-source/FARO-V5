# FARO V5 S3.A4 — N033C3 undo.execute: owner binding interno · B1–B5
Fecha AR 2026-11-08; unidad `S3_A4_N033C3_UNDO_EXECUTE_OWNER_BINDING`.
Baseline `be9476d43fd6b9af900f2890ed835f9a311242d2`. NO repetir los 3 B7 anteriores: `UNDO_ORIGIN_INSPECT` host12/12+kernel38/38; `UNDO_OWNER_CANCEL_SQL` SQL6/6+14/14 CI; `UNDO_EXECUTE_INTENT_CODEC` codec8/8 y guard. Código de codec `6b5453c4737d1185af25a63a89422a7bc44c6e8e` certificado.

## B1 — Evidencia y aceptación precisa
- Catálogo D-229 ya contiene `undo.execute` N-016, REALITY_MUTATION, executor `undo-coordinator`; **no crear nuevo ID**. Runtime todavía no registra su binding. El nuevo `UndoExecutePayload` transporta source Review, receipt, original CREATE payload, destino y cancelAt de manera inerte.
- `RealityStore` esquema8 tiene `planningApplyCancel` bajo active Apply lease y adapters `reminderCancellation`/`monthlyPlanCancellation` con CAS exacto. No se han enlazado a un `OperationBindingPort`/revalidator para `undo.execute`.
- Aceptación de ESTA unidad: nuevo binding/revalidator **interno** admitido en un test explícito bajo Reality8 y catálogo exacto, con dependencyStamp del owner y source `COMMITTED` attempt+receipt; usa el owner SQL de cancelación y luego nuevo `db.commit` de una NEW Apply identity. No permitimos que Chat/Surface/Android lo ejecuten todavía. Verificar 7 tests dirigidos no skip y regresiones disparadas.

## B2 — Comparación y reutilización (sin duplicación)
- V4.4 `SqliteComplexReversalPort` REFERENCIA: nuevo efecto auditado, niega doble-void, verifica relación antes de compensar. El SQL de cancelación V5 YA lo hace y no se modifica.
- V5 `ReminderCreateBinding`/`MonthlyPlanItemAddBinding`: patrón de revalidation+binding y catálogo exacto, pero no copiar owners ni abrir un módulo nuevo. El nuevo binding vive en `:store:reality` porque necesita los dos SPI internos; `RealityStore` proporciona factory de composición separada, no registrada en la V2 productiva. Se reutiliza `ApplyPreflightGate` y `ApplyTransactionExecutor` existentes.
- No duplicar el source receipt ni crear historial paralelo. Fuente COMMITTED se acredita en el journal/receipt Reality8 y la atribución a una conversación real queda pendiente de comprobación mediante `TrustedCommittedConversationPlanReadPort` del host.

## B3 — Investigación y decisiones
- Git/CI existentes para `N033B3` muestran revalidación redundante: preflight fuera de SQL y binding dentro de SQL. **ADAPTAR** para `undo.execute` utilizando la misma `UndoCandidate` fail-closed.
- SQLite `UPDATE` condicional y transacciones https://www.sqlite.org/lang_update.html y https://www.sqlite.org/lang_transaction.html: owner SQL e intento/receipt en una sola transacción; **REUSAR** mecanismo ya implementado, no agregar SQL.
- OWASP Authorization Cheat Sheet https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: autenticar el objeto y el contexto antes de mutar; **ADAPTAR** a la futura unidad de host, no convertir receipt en permiso.
- DESCARTAR: pasar source Review como identidad suficiente, inventar otro Apply pipeline, cancelar desde un read-only inspector, permitir source `undo.execute` o de otros owners, consumir un stamp de owner que no coincida con actual.

## B4 — Delta y pruebas acotadas
1. NUEVO `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityPlanningUndoExecuteBinding.kt`: `OperationBindingPort` y `OperationRevalidationPort` para `undo.execute` con descriptor original/catálogo exacto. Parsear `UndoExecutePayload`; fuente `reminder.create` o `monthly_plan.item.add` solamente; source receipt y journal `COMMITTED` exactos; owner payload original se decodifica y debe corresponder a la fila aún activa; no dependencias de Matter/ocurrencias/pago; dependencia canonical única del owner. Revalida antes de Apply y dentro de la transacción; owner SQL hace CAS final; deniega fuente alterada, doble cancel y schema7.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: dos factories `planningUndoBindingsForTrustedHost(catalog)` y `planningUndoRevalidatorsForTrustedHost(catalog)`, esquema8 y explícitas, NO se montan ni en shipping ni en v2CoreDev ni en chat.
3. NUEVO `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityPlanningUndoExecuteBindingT2Test.kt`: 7 tests: reminder (source&new receipt y reopen), monthly planned (sin pago), forged source receipt/journal, altered target/payload/owner revision, stale/double/cancelled, contrato/catálogo/error, schema7/foreign intent. Pruebas reales SQLite, NO afirmar Review humano.
4. NUEVO `.github/workflows/faro-v5-s3-a4-n033c3-undo-execute-owner-binding-directed.yml`: `:store:reality:test --tests RealityPlanningUndoExecuteBindingT2Test`, XML **7/7** sin failures/errors/skips.
5. Commit funcional `FARO-UNIT:S3_A4_N033C3_UNDO_EXECUTE_OWNER_BINDING` solo luego de READY+writer CAS; B7 exact SHA + regresión de workflows triggered, writer release durante CI.

## B5 — Auditoría adversarial / STOP
- En `Reality.run`, nunca leer Workspace: evitar deadlock. Source Review incluido en payload NO es prueba de identidad, y el nuevo binding NO podrá ejecutarse desde la UI mientras falta host. El host próximo debe comparar plan source `COMMITTED` completo (incluido originalCreatePayload) con receipt exacto y nueva human Review, pointer + object grant, antes de PREPARED y dentro SQL.
- SourceReceipt de otra tx, attempt PREPARED, source `undo.execute`, descriptor/capability/executor/fingerprint cambiados, stamps extra o ausentes, ID objetivo distinto, estado owner cancelado o pagado, esquema7, scope inválido => DENY, sin nuevas cancelaciones. Si SQL arroja, `Reality.run` rollback, nunca cambiar recibo original.
- Operación nueva debe tener identidad Apply nueva, receipt nuevo; nunca sobrescribir source COMMITTED. Reintentos y reconciliación recibo-only se harán en el host en unidad siguiente; estos tests cubren el nivel owner.
- **READY_SCOPED_OWNER_BINDING_ONLY** cuatro paths B4, S3.A4 OPEN, N033C3 Undo final aún pendiente. V2 productiva, S4, FARO Auto/Noche OFF; G15 físico al final.
