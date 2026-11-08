# FARO V5 S3.A4 — N033C3 Undo owner cancellation: Reality8 transaction B1–B5
Fecha AR: 2026-11-08. Unidad `S3_A4_N033C3_UNDO_OWNER_CANCEL_SQL`. Baseline previo `00c6d09385f0f7eef324a45454b9eae4e8401867`. Previo N033C3 origin inspection `52455e3d43dc8e892452847c8341e1f0d8d2a0d0` B7 PASS_SCOPED host12/12 kernel38/38 seis CI SUCCESS, NO REHACER.

## B1 — Necesidad y hechos observados
- `RealityStore` schema8 ya conserva `reminder_rule.enabled` y `monthly_plan_item.status`, cada owner tiene revisión CAS; `planningApplyInsert` comparte SAME SQLite transaction + `execution_receipt`. Las SPI de Reminders/MonthlyPlan sólo permiten creación. No hay cancelaciones automáticas, no hay Undo ejecutado.
- Contratos D-229 172 frozen: `undo.inspect`, `undo.execute`, `reminder.cancel` y `monthly_plan.item.cancel` están enumerados pero los últimos no tienen binding runtime y NO significa que este scope los habilite.
- Objetivo acotado: añadir **sólo motor interno** para cancelar estado de UNA regla vigente sin vínculo ni ocurrencias y UN ítem PLANNED sin pago; vía lease `RealityTransactionScope`, CAS exacta, misma transacción de un futuro Apply+receipt; conservar filas (no DELETE), cambio de revision propietario, ni mutación cruzada.
- No se incluye constructor de nuevo `ApplyPlan`, ACL host, interfaz Surface ni `undo.execute` ejecutable en esta unidad.

## B2 — Investigación y reuso
- V4.4 `SqliteComplexReversalPort` exige revisar estado actual, impide double-void y compensa dentro de una nueva transacción. REFERENCIA, no portar legacy.
- Reusar `RealityStore.withMaterialAdmission`, `synchronized(lock)`, `requireActiveScope`, `activeApplyScope === scope`, `readPlanningSnapshot`, `readForeignKeysHealthy`, SQLite `changes()`, CAS de revision, preservación PLN1. Rechazar mutation staging o través del host, por lo que no se modifica `planningStageInsert` ni `planningCreateBindingsForTrustedHost`.
- Mantener SPA/B7 certificados anteriores, solo nueva suite dirigida y regresiones automáticamente disparadas por cambios necesarios.

## B3 — Alternativas contrastadas
- SQLite UPDATE https://www.sqlite.org/lang_update.html — UPDATE condicional con WHERE de ID/estado/versión y `changes()==1`: ADOPTAR.
- SQLite transacciones https://www.sqlite.org/transactional.html — Atomicidad por una base/transacción: ADOPTAR para estado propietario + futuro recibo. No confundir con rollback de receipt ya COMMITTED.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html — autorización específica de objeto en cada request: la capa SQL NO es la ACL futura, dejar desconectada por defecto, ADOPTAR en el scope host posterior.
- DESCARTAR: `DELETE` físico de regla, volver a poner `PREPARED` el old tx, cambiar el old receipt, editar Workspace COMMITTED, cancelaciones sin revisión actual, borrar ocurrencias programadas sin owner de delivery, contabilizar expectedAmount como pago, inventar operación nueva.

## B4 — Implementación acotada, paths y verificación
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityPlanningStageMutationPort.kt`: nuevo `RealityPlanningApplyCancel` (Rule, MonthlyItem) con `original`, `cancelledAtEpochMillis`, revision esperada; nuevo `RealityPlanningApplyCancelPort`. Nunca expuesto a Surface/Chat.
2. `v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/spi/ReminderPersistencePort.kt`: nueva SPI separada `ReminderCancellationPersistencePort` para cancelar exact original en Reality transaction sin alterar create SPI.
3. `v5/owner/monthlyplan/src/main/kotlin/ar/com/faro/v5/owner/monthlyplan/spi/MonthlyPlanPersistencePort.kt`: nueva SPI separada `MonthlyPlanCancellationPersistencePort`; no declara pago.
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: adapters internos, cancelación bajo `withMaterialAdmission`+Reality lock+lease, SQL UPDATE condicional, CAS owner y `changes()==1`, pleno snapshot read/FK/PLN1 before SQLite COMMIT; reject no source original active exact, no linked Matter, no occurrences, no payment, cancelledAt > original.updatedAt, no stale owner, no cross-domain change.
5. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityPlanningUndoCancelT2Test.kt`: 6 pruebas: rule valid no delete, monthly valid no paid, stale CAS, double cancel+modified data, scope expired/schema7, rollback before commit including receipt for NEW identity. Comprobar real SQLite on reopen, original receipt remains intact if created in test.
6. `.github/workflows/faro-v5-s3-a4-n033c3-undo-owner-cancel-directed.yml`: JVM :store:reality:test --tests exact new suite, strict XML 6/6 no skip. No rerun manual de otros 66/22/18 tests certificados; git push might launch cambios regression workflows existentes como medida de compatibilidad.
- Código requiere nuevo commit `FARO-UNIT:S3_A4_N033C3_UNDO_OWNER_CANCEL_SQL`, permiso READY 6 paths exactos, guard 7 bloques.
## B5 — Adversarial / permisos / STOP
- No existe nueva habilitación de `undo.execute`: la nueva SPI únicamente sirve al owner protegido dentro de un Reality Apply transaction lease, NO a la interfaz usuaria. Si recibe receipt histórico equivocado, future host debe rechazarlo antes de llegar aquí; esta capa no debe prometer verificar el Workspace original.
- Regla con ocurrencias PENDING/ACKNOWLEDGED, linkedMatterId, deshabilitada, ID ausente u origen divergente: DENY. Monthly PAID_CONFIRMED, CANCELLED, fecha preexistente cambiada, pago confirmado, id o revision distinta: DENY. SQL afectó 0/2 filas => error, rollback. timestamp debe avanzar. Ambos owner snapshots deben mantenerse íntegros.
- Deny schema7, scope expirado o scope de otra RealityStore, transacción nesting, writer no adquirido, ACL ausente desde Chat, V2 productiva y S4. Receipt previo inmutable; no interpretar cancelación materializada como Undo user complete.
- B5 **READY_ONLY_INTERNAL_SQL** para seis paths B4. Si hay RED, dejar B7 RED_FIX mismo unit con nueva preauditoría antes de más código. No marcar A4 CLOSED, ni Undo N033C3 final hasta Review + binding + nuevo receipt + reintento.
