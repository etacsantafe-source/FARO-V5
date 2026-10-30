# FARO V5 — Inventario canónico de OperationContract
> **CURRENT AUTHORITY — D-229 (2026-10-20): OPERATION CATALOG REFROZEN / PASS.**
> Current exact set = **172 unique OperationContracts** with canonical executor/profile; profiles **49/70/35/7/5/6**; remaining OperationContract REOPEN = **NONE**. Historical 152-pass sections below are provenance.

> **POST-RG.06C.04 NOTICE — D-226 (2026-10-20): N-018 SAVED VIEWS CONTRACT DESIGN CLOSED.**
> D-226 adds `search.saved_view.read/create/update/delete`; execution reuses `search.query`. Current bounded subtotal: **172**. Remaining OperationContract reopen: **NONE**.

> **POST-RG.06C.03 NOTICE — D-225 (2026-10-20): N-041 PORTABILITY DESIGN CLOSED.**
> D-225 refines N-041 descriptor and adds `portability.preview` + `portability.export`. Current bounded subtotal: **168**. Remaining OperationContract reopen: N-018 only.

> **POST-RG.06C.02 NOTICE — D-224 (2026-10-20): N-032 DELIVERY/ATTENTION CONTRACT DESIGN CLOSED.**
> D-224 adds 8 canonical contracts and refines `notification.delivery.dispatch` without changing its ID/profile. Current bounded subtotal: **166**. Remaining contract reopens: N-041 + N-018.

> **POST-RG.06C.01 NOTICE — D-223 (2026-10-20): N-054 ADDS 6 CANONICAL CONTRACTS.**
> Current bounded subtotal: **158** = historical 152 + 6 N-054. The global catalog remains REOPENED for N-032/N-041/N-018; 158 is not the final RG.06C count.

> **POST-RG.06B OWNERSHIP NOTICE — D-217 (2026-10-20): N-041 PORTABILITY + N-018 SAVED VIEWS ALSO REOPENED BOUNDED.**
> M-E01 and M-E07 were approved earlier but lacked explicit executable homes. No new capabilities are created. Exact OperationIds/counts remain intentionally deferred to post-research design. The 152/152 catalog is historical pre-D-212/D-216/D-217 evidence, not the final catalog.

> **POST-FREEZE NOTICE — D-212 (2026-10-20): REOPENED BOUNDED FOR C-M03 ONLY.**
> **POST-RG.06A NOTICE — D-216 (2026-10-20): N-032 DELIVERY/ATTENTION ALSO REOPENED BOUNDED.**
> D-213 requires a durable generic Proactive Notice lifecycle and reliable delivery from non-Reality sources (e.g. Radar/C-M03). No new capability is created; exact new/refined OperationIds are intentionally deferred to post-research design. The frozen 152/152 remains the historical pre-D-212/D-216 baseline, not the final count.
>

> El catálogo 152/152 permanece como baseline histórico pre-D-212. RG.05 demostró que Watches/Routines necesita lifecycle/semántica propia; no se agregan OperationIds ni se altera el conteo en esta mini-auditoría. RG.06 deberá diseñar el mínimo catálogo C-M03 y ejecutar el downstream impact/ripple audit antes de reanudar V5-0D.


Actualizado: 2026-10-20
Original freeze: V5-C015 / WRITER_EPOCH 15 · POST-AUDIT.08
Metadata completion: V5-C018 / WRITER_EPOCH 18 · V5-0C.09

## Estado

**FROZEN / AUTHORITY FOR V5 OPERATION CONTRACTS — D-229 · current 172/172.**

Este documento baja la taxonomía corriente 54/54 a semánticas ejecutables sin derivar el catálogo desde las tools visibles de V4.

Reglas rectoras:
- CapabilityDescriptor y responsabilidad auditada no equivalen a OperationContract;
- cada OperationContract tiene un único capabilityId canónico y un owner/executor canónico;
- ProviderBinding, Quick, Shell, Voz y request_capability no crean variantes de operación;
- D-087/D-088: Chat es la interfaz universal de intención manual; simulación/planificación no son modos y las secciones no crean contextos semánticos persistentes;
- helpers, loops, validators, parser/extractor, staging/checkpoint y detalles del executor no se vuelven contratos;
- READ_ONLY es durablemente puro; derived/observational writes se declaran como tales;
- todo write material usa una sola espina Prepare/Policy/Apply/Outcome;
- el freeze POST-AUDIT.08 (D-095) fijó 152 IDs históricos; D-223 agregó 6 N-054, D-224 agregó 8 N-032, D-225 agregó 2 N-041 y D-226 agrega 4 N-018: subtotal bounded corriente **172**.

## Cobertura corriente 54/54

- OWNS_OPERATIONS: **35**
- USES_OPERATIONS: **4**
- INFRASTRUCTURE/SURFACE: **12**
- RETIRED: **3**
- Total: **54/54**
- OperationContract baseline histórico congelado: **152**
- Subtotal bounded corriente post-D-226: **172**

Cambios materiales acumulados hasta revisión 3:
- N-012 y N-039 pasan a OWNS_OPERATIONS;
- se eliminan falsos contratos de implementation phase;
- se recuperan AI settings/usage/credential operations y preferencias UX;
- se explicita onboarding defer/resume;
- N-021 deja de proponer seis contratos `simulation.*`: la exploración hipotética queda absorbida por Chat/workspace + cálculos puros de dominio + Apply genérico;
- D-089 elimina `advice.generate` y `coaching.evaluate`: Advice/Coaching reutilizan reads/evidence/Radar/History/Delivery sin motores paralelos;
- el conteo sigue siendo una red de cobertura, no un objetivo arquitectónico.

| Capability | Responsibility | Classification | OperationContracts / disposition | Notes |
|---|---|---|---|---|
| N-001 | Truth / autoridad / ownership | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-002 | Composición, runtime y health técnico | USES_OPERATIONS | reusa contratos de otros owners | Consume health/runtime diagnostics; no duplicate health contract. |
| N-003 | Persistencia durable y stores owner-local | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-004 | Interpretación semántica e intención canónica | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-005 | Tools/read models y operación tipada | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-006 | Propuesta, revisión y Apply | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-007 | Identidad transaccional, idempotencia, stale y recovery | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-008 | Auditabilidad determinística de mutaciones | INFRASTRUCTURE/SURFACE | sin OperationContract propio |  |
| N-009 | Contexto conversacional y orquestación | INFRASTRUCTURE/SURFACE | sin OperationContract propio | Chat global; contexto selectivo bajo demanda; puede coordinar reads/validaciones cross-owner sin asumir ownership ajeno (D-088). |
| N-010 | Workspace conversacional y lifecycle de chats | OWNS_OPERATIONS | `conversation.workspace.read`, `conversation.search`, `conversation.create`, `conversation.activate`, `conversation.rename`, `conversation.pin.set`, `conversation.archive.set`, `conversation.delete` | rename/pin/archive stay explicit; no generic metadata.update mega-operation. |
| N-011 | Memoria / aprendizaje semántico validado | OWNS_OPERATIONS | `memory.recall`, `memory.learn_validated`, `memory.correct`, `memory.forget` | memory.correct/forget are V5 lifecycle gaps; learn_validated is INTERNAL_ONLY/no model binding. |
| N-012 | IA desacoplada, routing, telemetría y costo | OWNS_OPERATIONS | `ai.settings.read`, `ai.usage.read`, `ai.provider.select`, `ai.models.set`, `ai.budget.set`, `ai.routing.reset`, `ai.credential.replace`, `ai.credential.clear`, `ai.credentials.clear_all` | Recovered from Settings: routing, usage/cost and credentials are N-012 semantics; secret values never enter receipts. |
| N-013 | Conocimiento local, corpus y evaluación offline | OWNS_OPERATIONS | `knowledge.search` | Offline corpus/eval harness is support tooling; only knowledge.search enters runtime OperationContract catalog. |
| N-014 | Summary / revisión estructurada | OWNS_OPERATIONS | `summary.read` | update_summary is not a Summary mutation contract; it produces Candidates for canonical domain operations. |
| N-015 | Historial y receipts operacionales | OWNS_OPERATIONS | `history.query`, `history.receipt.read` |  |
| N-016 | Undo / reversal / corrección segura | OWNS_OPERATIONS | `undo.inspect`, `undo.execute` |  |
| N-017 | Relaciones tipadas entre entidades | OWNS_OPERATIONS | `relation.read`, `relation.create`, `relation.delete` |  |
| N-018 | Búsqueda global/transversal | OWNS_OPERATIONS | `search.query`, `search.saved_view.read`, `search.saved_view.create`, `search.saved_view.update`, `search.saved_view.delete` | D-226: SavedViewDefinition is durable auxiliary user-defined state; results remain derived/ephemeral. |
| N-019 | Ingesta/extracción documental a evidencia | OWNS_OPERATIONS | `evidence.ingest`, `evidence.read`, `evidence.assess` | ContentExtractor is a component inside ingest/web pipelines; evidence.extract is not a standalone contract. |
| N-020 | Comprobantes / archivos owned de usuario | RETIRED | — |  |
| N-021 | Escenarios / simulación hipotética | INFRASTRUCTURE/SURFACE | sin OperationContract propio | D-087: what-if es una propiedad del workspace conversacional. Usa deltas/hipótesis efímeros + cálculos/read models puros de los owners; promoción/revalidación reutiliza Candidate/PreparedAction/ApplyPlan. No owner/store/lifecycle Simulation independiente. |
| N-022 | Inicio / materialidad / resumen | OWNS_OPERATIONS | `home.read` |  |
| N-023 | Advice / recomendación puntual | USES_OPERATIONS | reusa Evidence/read models/operaciones de owners | D-089: Advice es razonamiento conversacional on-demand; no owner/store ni `advice.generate`. Si propone cambios, emite Candidate hacia la operación canónica correspondiente. |
| N-024 | Radar / analítica transversal / salud | OWNS_OPERATIONS | `radar.read`, `radar.refresh`, `radar.history.read`, `radar.feedback.record` | radar.refresh is DERIVED/OBSERVATIONAL_WRITE; source-owner reads remain pure. |
| N-025 | Finanzas causal y operativa | OWNS_OPERATIONS | `finance.snapshot.read`, `finance.account.create`, `finance.entry.record`, `finance.transfer.record`, `finance.reservation.create`, `finance.reservation.release`, `finance.reservation.apply`, `finance.debt.create`, `finance.debt.payment.record`, `finance.credit_line.create`, `finance.financed_purchase.record` | Product-level operations absorb lower-level primitives where needed. |
| N-026 | Money, moneda y FX | OWNS_OPERATIONS | `finance.fx_transfer.record` |  |
| N-027 | Planificación financiera, recurrentes y horizonte | OWNS_OPERATIONS | `finance.schedule.read`, `finance.schedule.create`, `finance.schedule.update`, `finance.schedule.set_active`, `finance.schedule.delete`, `finance.plan_item.create`, `finance.plan_item.execute`, `finance.plan_item.cancel`, `finance.horizon.read` | D-077: schedule/template and concrete plan_item are separate; legacy recurring maps to schedule. |
| N-028 | Plan del mes / compromisos coordinados | OWNS_OPERATIONS | `monthly_plan.read`, `monthly_plan.item.add`, `monthly_plan.item.cancel`, `monthly_plan.objective.allocate` | D-085: monthly coordination layer links FinancePlanItem/realityCauseIds; reconcilePayment is internal identity-based reconciliation. |
| N-029 | Presupuestos, margen/ahorro seguro y conciliación | OWNS_OPERATIONS | `finance.budget.read`, `finance.budget.set`, `finance.budget.clear`, `finance.savings_floor.read`, `finance.savings_floor.set`, `finance.savings_floor.clear`, `finance.safe_capacity.read`, `finance.reconciliation.read` |  |
| N-030 | Objetivos | OWNS_OPERATIONS | `objectives.read`, `objective.create`, `objective.update`, `objective.lifecycle.set` | Economic progress is reconciled from canonical Finance links/reservations; no free-standing objective.progress.record. |
| N-031 | Asuntos / follow-up / atención persistente | OWNS_OPERATIONS | `attention.read`, `matter.create`, `matter.update`, `matter.lifecycle.set`, `matter.snooze` |  |
| N-032 | Recordatorios, temporalidad y notificaciones | OWNS_OPERATIONS | `reminder.read`, `reminder.create`, `reminder.update`, `reminder.cancel`, `reminder.attend`, `notice.read`, `notice.route`, `notice.seen.set`, `notice.dismiss`, `notice.resolve`, `delivery.preference.read`, `delivery.preference.set`, `notification.delivery.dispatch`, `notification.delivery.reconcile` | D-224: one durable Notice identity, multi-sink delivery, preferences owned here; no new capability. |
| N-033 | SmartCompra necesidad/decisión doméstica | OWNS_OPERATIONS | `shopping.snapshot.read`, `shopping_need.add`, `shopping_need.update`, `shopping_need.complete`, `shopping_need.remove`, `shopping_need.schedule.create`, `shopping_need.schedule.update`, `shopping_need.schedule.set_active`, `shopping.preference.site.set`, `shopping.need.quality.set`, `shopping.savings_objective.set` | Domestic-only boundary. READ_ONLY never materializes recurrences. |
| N-034 | Evidencia de precios y catálogo comercial | OWNS_OPERATIONS | `commercial.price_history.read`, `commercial.evidence.record`, `commercial.evidence.ingest`, `commercial.quote` | Evidence writes are observational; observed price is never paid price/Finance Reality. |
| N-035 | Stock / inventario doméstico | OWNS_OPERATIONS | `stock.snapshot.read`, `stock.observe`, `stock.reserve`, `stock.release`, `stock.receive`, `stock.consume`, `stock.meta.set` | D-080: receive/consume replace fulfillment-event stock authority; reserve/release support kitchen. |
| N-036 | Plan de compra, fulfillment y recepción | OWNS_OPERATIONS | `purchase_plan.preview`, `purchase_plan.commit`, `shopping.purchase.read`, `shopping.purchase.record`, `shopping.payment.record`, `shopping.receipt.record` | D-080: CommercialOperation is canonical purchase chain; FulfillmentEvent is transitional/derived. |
| N-037 | Cocina, recetas, planificación y preparados | OWNS_OPERATIONS | `kitchen.period.preview`, `meal.plan`, `meal.cancel`, `prepared_food.mark_prepared`, `prepared_food.use`, `kitchen.storage_capacity.set`, `recipe.save`, `recipe.delete` | recipe.save/delete only for explicit durable user choice; generation remains AI-first/ephemeral. |
| N-038 | Perfil / identidad y preferencias personales | OWNS_OPERATIONS | `profile.read`, `profile.update_declared` |  |
| N-039 | Ajustes y control operativo | OWNS_OPERATIONS | `settings.presentation.read`, `settings.theme.set`, `settings.amount_visibility.set` | D-086: Settings owns only presentation preferences; other settings delegate their real owners. |
| N-040 | Seguridad local y privacidad | OWNS_OPERATIONS | `security.status.read`, `security.pin.set`, `security.pin.clear`, `security.biometric.set`, `security.screen_privacy.set` |  |
| N-041 | Backup, restore, migración, compatibilidad y portabilidad de datos | OWNS_OPERATIONS | `backup.export`, `backup.restore`, `backup.recover`, `storage.migrate`, `portability.preview`, `portability.export` | D-225: portability is open/human-readable and explicitly NOT a FARO backup/restore format. |
| N-042 | Actualización y distribución | OWNS_OPERATIONS | `update.check`, `update.stage`, `update.install`, `update.reconcile` | verify is an invariant inside stage/install; install outcome is reconciled explicitly. |
| N-043 | FARO Quick / entrada rápida | INFRASTRUCTURE/SURFACE | sin OperationContract propio | Quick reuses chat/domain operations; no quick_* operations. |
| N-044 | Shell, navegación y superficies | INFRASTRUCTURE/SURFACE | sin OperationContract propio | Shell/navigation is surface/control-plane; no domain operations. |
| N-045 | Diagnóstico, self-test, auditoría técnica y performance | OWNS_OPERATIONS | `diagnostics.health.read`, `diagnostics.self_test.run`, `diagnostics.performance.run`, `diagnostics.export` |  |
| N-046 | Reset / limpieza controlada | OWNS_OPERATIONS | `reset.plan`, `reset.execute` |  |
| N-047 | Onboarding / entrada inicial | OWNS_OPERATIONS | `onboarding.state.read`, `onboarding.defer`, `onboarding.complete` |  |
| N-048 | Web externo bajo demanda | OWNS_OPERATIONS | `web.search`, `web.fetch` | search/fetch produce Evidence only; open-world and no Reality authority. |
| N-049 | State Packet / transporte compacto | RETIRED | — |  |
| N-050 | Documents / biblioteca durable | RETIRED | — |  |
| N-051 | Adjuntos como canal efímero | USES_OPERATIONS | reusa contratos de otros owners | Attachments are an ephemeral ingress channel to evidence.ingest. |
| N-052 | Coaching / detección proactiva | USES_OPERATIONS | reusa Radar/History/feedback/notification delivery | D-089: Coaching conserva la responsabilidad longitudinal, pero no crea detector/store/OperationContract paralelo; no auto-escribe Reality. |
| N-053 | Voz, dictado y lectura TTS | INFRASTRUCTURE/SURFACE | sin OperationContract propio | Voice/TTS reuses composer/domain pipeline; no voice_* operations. |
| N-054 | Vigilancias y rutinas de reevaluación | OWNS_OPERATIONS | `watch.read`, `watch.create`, `watch.update`, `watch.lifecycle.set`, `watch.evaluate`, `watch.history.read` | D-223: user-defined durable reevaluation; scheduler/AI/delivery are not owner; no auto-Apply. |


## Reconciliaciones obligatorias ya decididas

### Finance
- CREATE_ACCOUNT_DETAILED → finance.account.create; CreateAccount/RecordEntry de apertura son implementación interna de esa intención.
- RECORD_ENTRY_DETAILED → finance.entry.record.
- PAY_DEBT_DETAILED → finance.debt.payment.record.
- CREATE_FLEXIBLE_PLAN + CREATE_RECURRING legacy → finance.schedule.create.
- UPDATE_FLEXIBLE_PLAN + UPDATE_RECURRING legacy → finance.schedule.update.
- SET_RECURRING_ACTIVE → finance.schedule.set_active.
- DELETE_RECURRING → finance.schedule.delete.
- FinancePlanCreate/Execute/Cancel → finance.plan_item.create/execute/cancel.
- materialización schedule → plan_item es determinista/interna, no otra autoridad.
- transfer/fx_transfer registran/reconcilian hechos dentro de FARO; no mueven dinero bancario.
- storage initialization/migration no puede ocurrir como side effect de una operación READ_ONLY.

### MonthlyPlan / Objectives
- FinancePlanItem y MonthlyPlanItem permanecen distintos bajo D-085 y se enlazan por identity.
- MonthlyPlan reconciliation usa financePlanItemId/realityCauseId, no texto.
- objective progress económico no se escribe libremente: se deriva/reconcilia desde operaciones Finance canónicas.
- reconcilePayment es lógica interna de owner, no un OperationContract independiente.

### SmartCompra
Cadena canónica:
purchase_plan → shopping.purchase.record → shopping.payment.record / shopping.receipt.record.

ShoppingFulfillmentEvent queda TRANSITIONAL/DERIVED:
- PURCHASED → shopping.purchase.record;
- PAID → shopping.payment.record;
- RECEIVED → shopping.receipt.record;
- COOKING_PLANNED → stock.reserve vía meal.plan;
- CONSUMED → stock.consume.

Pago no agrega stock; recepción sí. PaymentLink, loops y detalles de persistencia no son contratos separados.
SmartCompra READ_ONLY no materializa recurrencias.

### Conversation / Summary / Memory
- update_summary no es writer de dominio: genera/revisa Candidates de operaciones canónicas.
- runStructuredSummary y productores paralelos de identidad quedan superseded.
- memory.learn_validated es INTERNAL_ONLY, idempotente y post-commit, asociado al receipt; no ProviderBinding para el modelo.
- conversation rename/pin/archive son semánticas distintas; no metadata.update genérico.

### Settings / AI / Security
- Settings es surface de composición bajo D-086.
- N-012 posee routing/model/budget/usage/credential semantics.
- N-039 posee sólo preferencias de presentación.
- N-038/N-040/N-041/N-042/N-046/N-047 conservan su propia autoridad.
- secretos nunca se copian a receipts/history.

### Evidence / Web
- capture_price_evidence → commercial.evidence.record, OBSERVATIONAL_WRITE.
- web.search / web.fetch → Evidence Store, nunca Reality.
- ContentExtractor no es OperationContract separado.
- evidence assessment es dinámico; no confidence fija como Truth.

### Backup / Update
- backup.export y backup.restore son operaciones de producto; prepare/stage/checkpoint/commit internos no crean contratos.
- update.stage incluye download/validation/verification; verify no es contrato separado.
- update.reconcile resuelve outcome durable post-install/restart.

## Rutas que NO son OperationContract
- request_capability / lazy discovery;
- ProviderBinding/provider tool names;
- ActionKind/FaroV4StoredAction como identidad canónica;
- checkpoint/staging/rollback internos;
- materialization loops;
- reversal helpers RESTORE_*;
- parser de instruction:String;
- ContentExtractor por sí solo;
- payment-link helper;
- Quick/Shell/Voice;
- UI buttons por el solo hecho de existir.

## Ejecución y auditabilidad
- D-082: todo write material comparte Prepare → Policy/Preconditions → Apply/Execute → Outcome.
- Candidate es obligatorio sólo para productores interpretativos/no autoritativos.
- D-083: writes/efectos materiales dejan ExecutionOutcome/Receipt; History es proyección.
- D-084: READ_ONLY es durablemente puro.
- D-079: nodos Reality locales cross-owner pueden compartir una transacción SQLite real.
- external effects salen por Outbox post-commit cuando nacen de un commit local.

## Execution profile canónico — completion 0C.09

0C.09 detectó que D-042/D-065 exigían owner/executor y efecto operativo explícitos por contrato, mientras la revisión 5 todavía dejaba esa metadata para el primer registry implementado.

Se realizó un **REOPEN controlado de POST-AUDIT.08 sólo para metadata**:
- OperationContract IDs: **sin cambios (152/152)**;
- capabilityId: **sin cambios**;
- semántica observable: **sin cambios**;
- disposiciones KEEP/ADAPT/MERGE/RECOVER/NEW_V5/INTERNAL_ONLY: **sin cambios**;
- taxonomía: **no reabierta**.

Las 152 filas de Pass A–D son ahora la autoridad explícita también para:
- `Canonical executor`;
- `Execution profile`.

Perfiles canónicos:

| Profile | Semántica |
|---|---|
| `READ_ONLY` | Puede leer estado/local o remoto bounded, pero produce **0 durable writes**; D-094/D-190 aplican transitivamente. |
| `REALITY_MUTATION` | Cambia DOMAIN_TRUTH por owner canónico mediante Prepare/Policy/Apply, dependency stamps y ExecutionReceipt; D-069/D-079/D-082 aplican. |
| `AUXILIARY_MUTATION` | Cambia una autoridad durable no-Reality (Workspace, Memory, Radar feedback, Control/Vault); usa Prepare/Policy/Execute/typed durable outcome y nunca se disfraza de Reality Apply. |
| `OBSERVATIONAL_WRITE` | Persiste Evidence o estado DERIVED observado; conserva provenance/identity/outcome y **nunca escribe Reality**. |
| `EXTERNAL_EFFECT` | Produce efecto fuera del estado local autoritativo (OS/delivery/export/install); requiere effect identity, journal/reconciliation y `EFFECT_UNKNOWN` cuando no puede demostrarse. |
| `MAINTENANCE_EFFECT` | Cambia lifecycle/control multi-store (restore/migrate/stage/reconcile/reset); usa Maintenance Runtime + control journal/recovery, sin transacción distribuida ficticia. |

Aclaraciones materiales:
- `evidence.assess` = **READ_ONLY**: la evaluación es dinámica; no crea confidence Truth durable por defecto.
- `diagnostics.self_test.run` y `diagnostics.performance.run` = **READ_ONLY**: ejecutar pruebas/mediciones no muta estado autoritativo; exportar es otra operación.
- `reset.plan` = **READ_ONLY**; `reset.execute` = **MAINTENANCE_EFFECT**.
- `update.check` = **READ_ONLY**; `update.stage` = **MAINTENANCE_EFFECT**; `update.install` = **EXTERNAL_EFFECT**; `update.reconcile` = **MAINTENANCE_EFFECT**.
- `notification.delivery.dispatch` = **EXTERNAL_EFFECT** con executor `delivery`.
- `undo.execute` = **REALITY_MUTATION**: Undo es una nueva operación sobre Reality actual, no rollback histórico.

Conteo exacto:
- READ_ONLY: **43**;
- REALITY_MUTATION: **67**;
- AUXILIARY_MUTATION: **26**;
- OBSERVATIONAL_WRITE: **6**;
- EXTERNAL_EFFECT: **4**;
- MAINTENANCE_EFFECT: **6**;
- **TOTAL: 152**.

Post-D-226 — subtotal corriente por profile:
- READ_ONLY: **49**;
- REALITY_MUTATION: **70**;
- AUXILIARY_MUTATION: **35**;
- OBSERVATIONAL_WRITE: **7**;
- EXTERNAL_EFFECT: **5**;
- MAINTENANCE_EFFECT: **6**;
- **TOTAL bounded corriente: 172**.

Los Pass A–D inferiores verifican el freeze histórico 152/152; D-223 es overlay post-freeze.

Regla:
un cambio posterior de executor o execution profile es cambio material de owner/effect y activa REOPEN bajo D-095.

## Clasificación por origen/destino — Pass A

Criterio:
- **KEEP**: semántica madura actual ya coincide sustancialmente;
- **ADAPT**: semántica existe, pero V5 cambia contrato/owner/pureza/cobertura;
- **MERGE**: varias rutas históricas convergen en una sola operación;
- **RECOVER**: semántica valiosa existía pero hoy no está productivamente conectada;
- **NEW_V5**: lifecycle/semántica necesaria no existía como operación equivalente;
- **INTERNAL_ONLY**: operación canónica real sin ProviderBinding de modelo/superficie común.

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-010 | conversation.workspace.read | workspace | READ_ONLY | KEEP | workspace/chat persistente maduro |
| N-010 | conversation.search | workspace | READ_ONLY | KEEP | búsqueda de chats ya es semántica estable |
| N-010 | conversation.create | workspace | AUXILIARY_MUTATION | KEEP | lifecycle multi-chat maduro |
| N-010 | conversation.activate | workspace | AUXILIARY_MUTATION | KEEP | lifecycle multi-chat maduro |
| N-010 | conversation.rename | workspace | AUXILIARY_MUTATION | KEEP | lifecycle explícito, no metadata genérica |
| N-010 | conversation.pin.set | workspace | AUXILIARY_MUTATION | KEEP | lifecycle explícito |
| N-010 | conversation.archive.set | workspace | AUXILIARY_MUTATION | KEEP | lifecycle explícito |
| N-010 | conversation.delete | workspace | AUXILIARY_MUTATION | KEEP | lifecycle explícito |
| N-011 | memory.recall | semantic-memory | AUXILIARY_MUTATION | ADAPT | memoria existente; V5 endurece provenance/separación de Reality |
| N-011 | memory.learn_validated | semantic-memory | AUXILIARY_MUTATION | INTERNAL_ONLY | aprendizaje post-commit idempotente ligado a receipt; sin binding del modelo |
| N-011 | memory.correct | semantic-memory | AUXILIARY_MUTATION | NEW_V5 | gap explícito de lifecycle/corrección |
| N-011 | memory.forget | semantic-memory | AUXILIARY_MUTATION | NEW_V5 | gap explícito de lifecycle/olvido |
| N-012 | ai.settings.read | ai-control | READ_ONLY | ADAPT | semántica recuperada desde Settings hacia owner IA |
| N-012 | ai.usage.read | ai-control | READ_ONLY | ADAPT | uso/costo existe pero necesita provenance/budget real |
| N-012 | ai.provider.select | ai-control | AUXILIARY_MUTATION | ADAPT | routing/provider existente, owner semántico N-012 |
| N-012 | ai.models.set | ai-control | AUXILIARY_MUTATION | ADAPT | configuración existente, desacoplada de surface Settings |
| N-012 | ai.budget.set | ai-control | AUXILIARY_MUTATION | ADAPT | budget existente/parcial; contrato V5 explícito |
| N-012 | ai.routing.reset | ai-control | AUXILIARY_MUTATION | ADAPT | lifecycle de routing existente pero disperso |
| N-012 | ai.credential.replace | ai-control | AUXILIARY_MUTATION | ADAPT | credenciales existentes; secreto fuera de receipts |
| N-012 | ai.credential.clear | ai-control | AUXILIARY_MUTATION | ADAPT | lifecycle seguro de credencial |
| N-012 | ai.credentials.clear_all | ai-control | AUXILIARY_MUTATION | ADAPT | lifecycle seguro bulk del mismo owner |
| N-013 | knowledge.search | knowledge | READ_ONLY | RECOVER | core:knowledge/corpus útil está desconectado del runtime actual |
| N-014 | summary.read | summary-readmodel | READ_ONLY | ADAPT | Summary existe; V5 elimina producer/identity paralelo |
| N-015 | history.query | history-readmodel | READ_ONLY | ADAPT | historial existe; V5 amplía cobertura sin volverlo Truth |
| N-015 | history.receipt.read | history-readmodel | READ_ONLY | KEEP | lectura de receipt operacional es semántica madura |
| N-016 | undo.inspect | undo-coordinator | READ_ONLY | KEEP | guardas de Undo maduras y Reality-aware |
| N-016 | undo.execute | undo-coordinator | REALITY_MUTATION | KEEP | ejecución/recovery de Undo madura |
| N-017 | relation.read | relations | READ_ONLY | KEEP | relaciones tipadas cross-domain ya materializadas |
| N-017 | relation.create | relations | REALITY_MUTATION | KEEP | semántica ownerizada y validada |
| N-017 | relation.delete | relations | REALITY_MUTATION | KEEP | semántica ownerizada y validada |
| N-018 | search.query | federated-search | READ_ONLY | ADAPT | búsqueda global existe pero requiere ampliar cobertura/canonical refs |
| N-019 | evidence.ingest | evidence | OBSERVATIONAL_WRITE | RECOVER | recuperar capa estructurada/provenance de Evidence sin volverla Reality |
| N-019 | evidence.read | evidence | READ_ONLY | RECOVER | lectura de Evidence Store vuelve como capability explícita |
| N-019 | evidence.assess | evidence | READ_ONLY | NEW_V5 | evaluación dinámica separada de confidence fija histórica |
| N-022 | home.read | home-readmodel | READ_ONLY | ADAPT | Home actual se conserva como read model puro/dedupe por identidad |
| N-024 | radar.read | radar | READ_ONLY | ADAPT | Radar maduro; V5 endurece sufficiency/provenance/pureza |
| N-024 | radar.refresh | radar | OBSERVATIONAL_WRITE | ADAPT | derived/observational write explícito, no read con side effects |
| N-024 | radar.history.read | radar | READ_ONLY | ADAPT | historia/score se conserva con versión/provenance |
| N-024 | radar.feedback.record | radar | AUXILIARY_MUTATION | ADAPT | feedback conserva lifecycle y alimenta proactividad sin ceder Truth |

Resultado Pass A:
- **39 operation IDs clasificadas**;
- N-021/N-023/N-052 no generan OperationContract por D-087/D-089;
- no apareció MERGE adicional en este sector;
- no se reabrió taxonomía.

## Clasificación por origen/destino — Pass B

Sector: Finance + MonthlyPlan + Objectives + Matters + Reminders.

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-025 | finance.snapshot.read | finance | READ_ONLY | ADAPT | read models maduros; V5 exige pureza/contexto selectivo uniforme |
| N-025 | finance.account.create | finance | REALITY_MUTATION | MERGE | absorbe CreateAccount y CREATE_ACCOUNT_DETAILED en una semántica |
| N-025 | finance.entry.record | finance | REALITY_MUTATION | MERGE | absorbe RecordEntry y RECORD_ENTRY_DETAILED |
| N-025 | finance.transfer.record | finance | REALITY_MUTATION | RECOVER | capacidad Finance madura no expuesta uniformemente por tools V4 |
| N-025 | finance.reservation.create | finance | REALITY_MUTATION | RECOVER | owner Finance ya posee reserva; se recupera al catálogo canónico |
| N-025 | finance.reservation.release | finance | REALITY_MUTATION | RECOVER | lifecycle de reserva existente/oculto |
| N-025 | finance.reservation.apply | finance | REALITY_MUTATION | RECOVER | aplicación causal de reserva existente/oculta |
| N-025 | finance.debt.create | finance | REALITY_MUTATION | RECOVER | capacidad madura detectada fuera de tools visibles |
| N-025 | finance.debt.payment.record | finance | REALITY_MUTATION | MERGE | unifica PayDebt/DETAILED y registra hecho sin mover dinero real |
| N-025 | finance.credit_line.create | finance | REALITY_MUTATION | RECOVER | capacidad madura oculta detectada en owner |
| N-025 | finance.financed_purchase.record | finance | REALITY_MUTATION | RECOVER | capacidad madura oculta detectada en owner |
| N-026 | finance.fx_transfer.record | finance | REALITY_MUTATION | RECOVER | semántica multimoneda madura no expuesta como operación canónica |
| N-027 | finance.schedule.read | finance | READ_ONLY | ADAPT | lectura recurrente/horizonte madura bajo modelo schedule único |
| N-027 | finance.schedule.create | finance | REALITY_MUTATION | MERGE | CREATE_FLEXIBLE_PLAN + CREATE_RECURRING → schedule |
| N-027 | finance.schedule.update | finance | REALITY_MUTATION | MERGE | UPDATE_FLEXIBLE_PLAN + UPDATE_RECURRING → schedule |
| N-027 | finance.schedule.set_active | finance | REALITY_MUTATION | MERGE | lifecycle recurrente/flexible unificado |
| N-027 | finance.schedule.delete | finance | REALITY_MUTATION | MERGE | retiro de regla/template único |
| N-027 | finance.plan_item.create | finance | REALITY_MUTATION | RECOVER | FinancePlan lifecycle maduro detectado fuera de tools actuales |
| N-027 | finance.plan_item.execute | finance | REALITY_MUTATION | RECOVER | FinancePlan lifecycle maduro detectado fuera de tools actuales |
| N-027 | finance.plan_item.cancel | finance | REALITY_MUTATION | RECOVER | FinancePlan lifecycle maduro detectado fuera de tools actuales |
| N-027 | finance.horizon.read | finance | READ_ONLY | ADAPT | proyección madura; V5 refuerza confirmado/estimado y no-write |
| N-028 | monthly_plan.read | planning | READ_ONLY | ADAPT | MonthlyPlan fuerte; read model debe usar identidad canónica |
| N-028 | monthly_plan.item.add | planning | REALITY_MUTATION | ADAPT | semántica existente; V5 enlaza FinancePlanItem por identity |
| N-028 | monthly_plan.item.cancel | planning | REALITY_MUTATION | ADAPT | lifecycle existente con reconciliación endurecida |
| N-028 | monthly_plan.objective.allocate | planning | REALITY_MUTATION | ADAPT | coordinación objetivo/mes existente; sin duplicar progreso financiero |
| N-029 | finance.budget.read | finance | READ_ONLY | ADAPT | presupuesto conectado; V5 endurece currency/policy/provenance |
| N-029 | finance.budget.set | finance | REALITY_MUTATION | ADAPT | mutación existente bajo contrato canónico |
| N-029 | finance.budget.clear | finance | REALITY_MUTATION | ADAPT | lifecycle explícito |
| N-029 | finance.savings_floor.read | finance | READ_ONLY | ADAPT | margen/ahorro existente con semántica a clarificar |
| N-029 | finance.savings_floor.set | finance | REALITY_MUTATION | ADAPT | policy existente bajo contrato tipado |
| N-029 | finance.savings_floor.clear | finance | REALITY_MUTATION | ADAPT | lifecycle explícito |
| N-029 | finance.safe_capacity.read | finance | READ_ONLY | ADAPT | cálculo existente; no se convierte en Truth propia |
| N-029 | finance.reconciliation.read | finance | READ_ONLY | ADAPT | reconciliación confirmado/estimado por identidad |
| N-030 | objectives.read | planning | READ_ONLY | ADAPT | owner maduro; progreso no financiero se generaliza |
| N-030 | objective.create | planning | REALITY_MUTATION | ADAPT | creación existente bajo payload canónico |
| N-030 | objective.update | planning | REALITY_MUTATION | ADAPT | actualización existente preservando DAG/invariantes |
| N-030 | objective.lifecycle.set | planning | REALITY_MUTATION | MERGE | unifica cambios de estado/complete/cancel en lifecycle tipado |
| N-031 | attention.read | planning | READ_ONLY | ADAPT | vista de atención/follow-up source-aware |
| N-031 | matter.create | planning | REALITY_MUTATION | KEEP | owner/follow-up sólido y conectado |
| N-031 | matter.update | planning | REALITY_MUTATION | KEEP | semántica estable |
| N-031 | matter.lifecycle.set | planning | REALITY_MUTATION | ADAPT | normaliza estados sin mutar source |
| N-031 | matter.snooze | planning | REALITY_MUTATION | ADAPT | existe; V5 elimina policy hardcoded de 1 día |
| N-032 | reminder.read | planning | READ_ONLY | ADAPT | recurrence/catch-up maduros bajo modelo temporal explícito |
| N-032 | reminder.create | planning | REALITY_MUTATION | ADAPT | regla temporal existente con timezone/recovery endurecidos |
| N-032 | reminder.update | planning | REALITY_MUTATION | ADAPT | lifecycle temporal existente |
| N-032 | reminder.cancel | planning | REALITY_MUTATION | ADAPT | lifecycle temporal existente |
| N-032 | reminder.attend | planning | REALITY_MUTATION | ADAPT | ack/attend separado de delivery |
| N-032 | notification.delivery.dispatch | delivery | EXTERNAL_EFFECT | NEW_V5 | cierre explícito del gap histórico: scheduled != delivered |

Resultado Pass B:
- **48 operation IDs clasificadas**;
- acumulado Pass A+B: **87/152**;
- planificación de dominio se conserva; desaparece sólo el “modo Planning” de interacción;
- variantes legacy/detailed/recurrentes se fusionan sin perder semántica;
- delivery durable queda explícitamente nuevo en V5.

## Clasificación por origen/destino — Pass C

Sector: SmartCompra + Commercial Evidence + Stock + Purchase + Kitchen.

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-033 | shopping.snapshot.read | household | READ_ONLY | ADAPT | owner conectado; V5 elimina materialización accidental en reads |
| N-033 | shopping_need.add | household | REALITY_MUTATION | KEEP | necesidad doméstica estable y ownerizada |
| N-033 | shopping_need.update | household | REALITY_MUTATION | KEEP | lifecycle estable |
| N-033 | shopping_need.complete | household | REALITY_MUTATION | KEEP | lifecycle estable |
| N-033 | shopping_need.remove | household | REALITY_MUTATION | KEEP | lifecycle estable |
| N-033 | shopping_need.schedule.create | household | REALITY_MUTATION | ADAPT | recurrencia existe; V5 separa regla de materialización |
| N-033 | shopping_need.schedule.update | household | REALITY_MUTATION | ADAPT | recurrencia existente, idempotencia/pureza endurecidas |
| N-033 | shopping_need.schedule.set_active | household | REALITY_MUTATION | ADAPT | lifecycle explícito de regla |
| N-033 | shopping.preference.site.set | household | REALITY_MUTATION | ADAPT | policy existente dentro de ShoppingPreferences |
| N-033 | shopping.need.quality.set | household | REALITY_MUTATION | ADAPT | policy existente; identidad de item se endurece |
| N-033 | shopping.savings_objective.set | household | REALITY_MUTATION | ADAPT | policy existente; no reemplaza Objetivos globales |
| N-034 | commercial.price_history.read | commercial-evidence | READ_ONLY | ADAPT | evidencia/precios existen; V5 refuerza stale/provenance/product identity |
| N-034 | commercial.evidence.record | commercial-evidence | OBSERVATIONAL_WRITE | MERGE | converge captura manual/tool y CommercialPriceEvidence en un write observacional |
| N-034 | commercial.evidence.ingest | commercial-evidence | OBSERVATIONAL_WRITE | ADAPT | CommercialImportService existe; se tipa replay/fingerprint/provenance |
| N-034 | commercial.quote | commercial-evidence | READ_ONLY | KEEP | CommercialDecisionQuote.quote ya expresa consulta de dominio estable |
| N-035 | stock.snapshot.read | household | READ_ONLY | ADAPT | StockLedger snapshot existente; agrega granularidad progresiva sin ERP |
| N-035 | stock.observe | household | REALITY_MUTATION | KEEP | observación física causal madura |
| N-035 | stock.reserve | household | REALITY_MUTATION | KEEP | reserva de stock madura |
| N-035 | stock.release | household | REALITY_MUTATION | KEEP | liberación de reserva madura |
| N-035 | stock.receive | household | REALITY_MUTATION | MERGE | reemplaza autoridad RECEIVED de FulfillmentEvent y soporta entrada física causal |
| N-035 | stock.consume | household | REALITY_MUTATION | MERGE | reemplaza autoridad CONSUMED de FulfillmentEvent y soporta salida física causal |
| N-035 | stock.meta.set | household | REALITY_MUTATION | ADAPT | KitchenStockMeta existente; V5 permite lote/ubicación/vencimiento opcionales |
| N-036 | purchase_plan.preview | household | READ_ONLY | ADAPT | ShoppingCartOptimizer/plan existe; preview queda puro y conversable |
| N-036 | purchase_plan.commit | household | REALITY_MUTATION | ADAPT | persiste revisión real sólo cuando el usuario decide materializarla |
| N-036 | shopping.purchase.read | household | READ_ONLY | ADAPT | lectura de CommercialOperation/progress sobre cadena canónica |
| N-036 | shopping.purchase.record | household | REALITY_MUTATION | MERGE | CommercialOperation + Fulfillment PURCHASED convergen en una autoridad |
| N-036 | shopping.payment.record | household | REALITY_MUTATION | MERGE | PaymentLink + Fulfillment PAID convergen; registra hecho, no mueve dinero |
| N-036 | shopping.receipt.record | household | REALITY_MUTATION | MERGE | CommercialReceipt + Fulfillment RECEIVED convergen; recepción puede ser parcial |
| N-037 | kitchen.period.preview | household | READ_ONLY | ADAPT | KitchenPeriodPlanner existe; preview es cálculo puro, no “modo planificación” |
| N-037 | meal.plan | household | REALITY_MUTATION | ADAPT | KitchenPlan durable + reservas canónicas |
| N-037 | meal.cancel | household | REALITY_MUTATION | ADAPT | lifecycle de plan durable |
| N-037 | prepared_food.mark_prepared | household | REALITY_MUTATION | ADAPT | preparación real persiste y reconcilia stock |
| N-037 | prepared_food.use | household | REALITY_MUTATION | ADAPT | consumo de preparado con identidad/idempotencia |
| N-037 | kitchen.storage_capacity.set | household | REALITY_MUTATION | KEEP | capacidad de almacenamiento ya modelada |
| N-037 | recipe.save | household | REALITY_MUTATION | ADAPT | store existe; sólo elección explícita del usuario vuelve receta durable |
| N-037 | recipe.delete | household | REALITY_MUTATION | ADAPT | lifecycle explícito de receta durable opcional |

Resultado Pass C:
- **36 operation IDs clasificadas**;
- acumulado Pass A+B+C: **123/152**;
- Purchase/Payment/Receipt sustituyen la autoridad duplicada de FulfillmentEvent;
- planificación/cocina siguen siendo semántica de dominio cuando se materializan; no crean modos de Chat.

## Clasificación por origen/destino — Pass D

Sector: Profile/Settings/Security + Backup/Update/Diagnostics/Reset/Onboarding + Web.

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-038 | profile.read | profile | READ_ONLY | ADAPT | capability sobrevive sin root Profile; datos declarados mínimos |
| N-038 | profile.update_declared | profile | REALITY_MUTATION | ADAPT | unifica edición desde Chat/Settings/Onboarding bajo owner Profile |
| N-039 | settings.presentation.read | presentation-control | READ_ONLY | ADAPT | Settings deja de ser super-owner; conserva sólo preferencias UX |
| N-039 | settings.theme.set | presentation-control | AUXILIARY_MUTATION | ADAPT | preferencia durable de presentación |
| N-039 | settings.amount_visibility.set | presentation-control | AUXILIARY_MUTATION | ADAPT | preferencia visual durable, separada de Security |
| N-040 | security.status.read | security-control | READ_ONLY | ADAPT | estado/security policy se vuelve lectura canónica y mínima |
| N-040 | security.pin.set | security-control | AUXILIARY_MUTATION | KEEP | capacidad local madura |
| N-040 | security.pin.clear | security-control | AUXILIARY_MUTATION | KEEP | lifecycle local maduro |
| N-040 | security.biometric.set | security-control | AUXILIARY_MUTATION | ADAPT | conserva baja fricción y permiso/platform state explícitos |
| N-040 | security.screen_privacy.set | security-control | AUXILIARY_MUTATION | ADAPT | control visual/plataforma existente con owner Security |
| N-041 | backup.export | maintenance.backup | EXTERNAL_EFFECT | ADAPT | backup semántico maduro; V5 amplía inventario/policies |
| N-041 | backup.restore | maintenance.backup | MAINTENANCE_EFFECT | ADAPT | staging/validate/rollback maduros bajo contrato V5 |
| N-041 | backup.recover | maintenance.backup | MAINTENANCE_EFFECT | ADAPT | recovery existente se formaliza como outcome/lifecycle de producto |
| N-041 | storage.migrate | maintenance.backup | MAINTENANCE_EFFECT | INTERNAL_ONLY | migración durable necesaria pero no intención normal del modelo/usuario |
| N-042 | update.check | maintenance.update | READ_ONLY | ADAPT | updater existente; policy/source/signing más explícitos |
| N-042 | update.stage | maintenance.update | MAINTENANCE_EFFECT | ADAPT | agrupa download+validation+verification sin helper-contracts |
| N-042 | update.install | maintenance.update | EXTERNAL_EFFECT | ADAPT | instalación existente con OS gate/outcome |
| N-042 | update.reconcile | maintenance.update | MAINTENANCE_EFFECT | NEW_V5 | cierra gap post-install/restart: éxito del intent != install confirmada |
| N-045 | diagnostics.health.read | diagnostics | READ_ONLY | ADAPT | health actual se conserva sin crear segunda Truth |
| N-045 | diagnostics.self_test.run | diagnostics | READ_ONLY | RECOVER | V4 redujo cobertura; recuperar self-test seguro/redacted |
| N-045 | diagnostics.performance.run | diagnostics | READ_ONLY | RECOVER | recuperar benchmarking/performance reproducible sin mutar Reality |
| N-045 | diagnostics.export | diagnostics | EXTERNAL_EFFECT | RECOVER | recuperar export/support acotado y sin secretos/raw indiscriminado |
| N-046 | reset.plan | maintenance.reset | READ_ONLY | ADAPT | plan explícito existente; inventario DELETE/PRESERVE completo |
| N-046 | reset.execute | maintenance.reset | MAINTENANCE_EFFECT | ADAPT | reset real conserva human gate + recovery/idempotencia |
| N-047 | onboarding.state.read | onboarding-control | READ_ONLY | ADAPT | boolean histórico evoluciona a lifecycle versionado/resumible |
| N-047 | onboarding.defer | onboarding-control | AUXILIARY_MUTATION | NEW_V5 | permite skip/defer sin bloquear uso ni pedir datos prematuros |
| N-047 | onboarding.complete | onboarding-control | AUXILIARY_MUTATION | ADAPT | completion existente, sin privilegio especial para owner writes |
| N-048 | web.search | web-evidence | OBSERVATIONAL_WRITE | ADAPT | web lazy actual se hace provider-neutral, evidence-only y trazable |
| N-048 | web.fetch | web-evidence | OBSERVATIONAL_WRITE | ADAPT | URL/fetch queda SafeHttpFetcher + Evidence, nunca Reality |

Resultado Pass D:
- **29 operation IDs clasificadas**;
- acumulado Pass A+B+C+D: **152/152**;
- clasificación individual del inventario: **CLOSED / PASS**;
- N-043 Quick, N-044 Shell, N-051 Attachments y N-053 Voice siguen como surfaces/channels sin OperationContracts propios.

## Post-freeze bounded addition — D-223 / N-054

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-054 | watch.read | watch | READ_ONLY | NEW_V5 | lectura pura de definition/estado operacional |
| N-054 | watch.create | watch | REALITY_MUTATION | NEW_V5 | intención durable user-defined tras definition tipada + Review/Apply |
| N-054 | watch.update | watch | REALITY_MUTATION | NEW_V5 | versiona definitionRevision y reconcilia scheduling |
| N-054 | watch.lifecycle.set | watch | REALITY_MUTATION | NEW_V5 | ACTIVE/PAUSED/RETIRED |
| N-054 | watch.evaluate | watch | OBSERVATIONAL_WRITE | NEW_V5 | outcome/emission intent derivados; nunca Reality ajena |
| N-054 | watch.history.read | watch | READ_ONLY | NEW_V5 | observabilidad M-E09 sin side effects |

Verificación D-223:
- new operation rows: **6**;
- duplicate OperationIds: **0**;
- missing executor/profile: **0**;
- subtotal corriente: **158**;
- catálogo global: **REOPENED BOUNDED** por N-032/N-041/N-018.

## Owner/executor y store boundaries — D-091
## Post-freeze bounded addition — D-226 / N-018 Saved Views

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-018 | search.saved_view.read | federated-search | READ_ONLY | NEW_V5 | read/list durable typed definitions with zero implicit usage writes |
| N-018 | search.saved_view.create | federated-search | AUXILIARY_MUTATION | NEW_V5 | persist validated versioned SavedViewDefinition |
| N-018 | search.saved_view.update | federated-search | AUXILIARY_MUTATION | NEW_V5 | explicit revision-checked definition/title update |
| N-018 | search.saved_view.delete | federated-search | AUXILIARY_MUTATION | NEW_V5 | delete definition only; source/domain data untouched |

Execution rule:
- `search.query` remains the only canonical query executor and may resolve a `savedViewId`;
- SavedViewDefinition is authority; query results/search indexes are not;
- reads never write last-used/cache state;
- stale semantic refs fail closed against result widening;
- no `search.saved_view.execute`, schedule or notify contract.

Verification D-226:
- new operation rows: **4**;
- duplicate OperationIds introduced: **0**;
- missing executor/profile: **0**;
- N-018 contract reopen: **CLOSED / PASS**;
- bounded subtotal current: **172**;
- remaining contract reopen: **NONE**.


## Post-freeze bounded addition — D-225 / N-041 Portability

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-041 | portability.preview | maintenance.portability | READ_ONLY | NEW_V5 | scope/format/exclusions/warnings preview without artifact or write |
| N-041 | portability.export | maintenance.portability | EXTERNAL_EFFECT | NEW_V5 | explicit human data egress to user-selected destination in open/versioned format |

Contract invariants:
- portability artifact is **NOT RESTORABLE** by `backup.restore`;
- no Vault/credential export;
- owner reads/export projections only; no DB bypass;
- user-selected destination + explicit human gate;
- no scheduled/background export;
- external failure/unknown never mutates Truth;
- no blind retry after uncertain external write.

Verification D-225:
- new operation rows: **2**;
- duplicate OperationIds introduced: **0**;
- missing executor/profile: **0**;
- N-041 descriptor/contract reopen: **CLOSED / PASS**;
- bounded subtotal current: **168**.


## Post-freeze bounded addition — D-224 / N-032 Delivery-Attention

| Capability | Operation | Canonical executor | Execution profile | Disposition | Motivo |
|---|---|---|---|---|---|
| N-032 | notice.read | delivery | READ_ONLY | NEW_V5 | durable feed/history read; no implicit seen side effect |
| N-032 | notice.route | delivery | AUXILIARY_MUTATION | INTERNAL_ONLY | typed NoticeCandidate → silent/materialize/coalesce + desired sink intents |
| N-032 | notice.seen.set | delivery | AUXILIARY_MUTATION | NEW_V5 | explicit human attention state; independent of source relevance |
| N-032 | notice.dismiss | delivery | AUXILIARY_MUTATION | NEW_V5 | dismiss attention without resolving source Truth |
| N-032 | notice.resolve | delivery | AUXILIARY_MUTATION | INTERNAL_ONLY | source-driven relevance resolution/expiry/replacement |
| N-032 | delivery.preference.read | delivery | READ_ONLY | NEW_V5 | FARO preference + effective observed platform availability |
| N-032 | delivery.preference.set | delivery | AUXILIARY_MUTATION | NEW_V5 | owner contract for bounded sink/materiality preferences |
| N-032 | notification.delivery.reconcile | delivery | AUXILIARY_MUTATION | INTERNAL_ONLY | delivery journal/effect reconcile after unknown/crash/platform observation |

Refinement without new OperationId:
- `notification.delivery.dispatch` remains EXTERNAL_EFFECT / executor `delivery`;
- consumes durable DeliveryIntent;
- desired platform action is bounded UPSERT/CANCEL;
- successful OS post never means human SEEN.

Verification D-224:
- new operation rows: **8**;
- duplicate OperationIds introduced: **0**;
- missing executor/profile: **0**;
- N-032 contract reopen: **CLOSED / PASS**;
- bounded subtotal current: **166**.



- ContextOrchestrator / FederatedSearch / HomeReadModel / HistoryProjection: **sin Truth store propio**.
- Summary usa **Workspace Store**, no crea base Summary paralela.
- Undo coordina receipts + Reality actual y delega al owner; no store Undo propio.
- Evidence → **Evidence Store**.
- Semantic Memory → **Semantic Memory Store**.
- Radar → **Radar Derived Store**.
- MonthlyPlan → **Reality Store** bajo ownership propio.
- Delivery → **Outbox + Delivery Journal**.
- WebEvidence → SafeHttpFetcher + **Evidence Store**.
- Watch definition/lifecycle → authority de dominio N-054; physical store placement se difiere a 0C.04.
- Watch evaluation/outcome/emission intent → estado DERIVED/operacional owner-local; no segunda Truth ni generic event bus.
- Advice/Coaching → sin executor/store de dominio propio.

Regla: cross-owner orchestration no habilita escritura transversal; owners conservan APIs/Truth y D-079 gobierna atomicidad Reality local.

## Gates de cierre de POST-AUDIT.08
1. **CLOSED / PASS** — 152/152 OperationContract IDs clasificados como KEEP / ADAPT / MERGE / RECOVER / NEW_V5 / INTERNAL_ONLY;
2. **CLOSED / PASS** — nomenclatura auditada y convención congelada por D-090; `search.faro`→`search.query`, `smartcompra.read`→`shopping.snapshot.read`; sin otras colisiones materiales;
3. **CLOSED / PASS** — D-091 fija executors y store boundaries para Context/Workspace/Memory/Summary/History/Undo/Search/Evidence/Home/Radar/MonthlyPlan/Delivery/Web; coordinación no adquiere Truth;
4. **CLOSED / PASS** — D-092 fija la espina por effect class; Evidence queda como rama paralela no autoritativa, Receipt acompaña commits materiales y Undo parte de receipt + Reality actual;
5. **CLOSED / PASS** — D-093 elimina bypasses semánticos: surfaces/schedulers/recovery/providers no escriben materialmente fuera del OperationContract + owner/outcome; housekeeping interno no material permanece interno y owner-bounded;
6. **CLOSED / PASS** — D-094 certifica READ_ONLY durable y transitivamente puro; bootstrap/migration/materialization/derived writes quedan fuera de read paths.

### Evidencia as-built del gate 5 — bypasses

V4 conserva rutas directas que explican la regla V5:
- Settings → ProductPreferences / AI routing-model-budget / credentials;
- Onboarding/Security → preferencias directas;
- FollowUpSurface → mutations del gateway desde UI;
- MainActivity → receipt post-hoc + startup recovery;
- Home → `radar.refresh()` durante carga de la vista;
- UpdateManager → `checkAndPrepare()` combina check con staging/download.

Clasificación:
**ADAPT/REPLACE ROUTE**. La semántica útil se conserva; la ruta directa no.

No se crea un “OperationContract por setter”: cada caso converge en las operaciones canónicas ya inventariadas y en la espina D-082/D-092.

### Evidencia as-built del gate 6 — READ_ONLY

Violaciones históricas detectadas:
- Finance `read()` llama `ensureCanonicalStorage()` y puede `materializeThrough(horizonEnd)`;
- Finance `readForSearch()` evita recurrencias pero todavía fuerza canonical-storage bootstrap;
- SmartCompra `read()` llama `materializeRecurring()`; `readForSearch()` ya demuestra que la lectura sin materialización es viable;
- Home usa Finance/SmartCompra reads y además ejecuta Radar refresh, por lo que su impureza es transitiva;
- V4 Update `checkAndPrepare()` escribe staged APK durante una operación de apariencia “check”.

Destino V5:
- bootstrap/migrations son lifecycle/control;
- recurrence projection es cálculo puro;
- durable recurrence materialization ocurre sólo desde ruta mutante;
- Home consume `radar.read`, nunca refresh implícito;
- `update.check` y `update.stage` quedan separados;
- Search/Summary/context/read models sólo pueden depender de reads ya certificados.

### Metadata completion 0C.09

Resultado: **CLOSED / PASS**.

Mechanical verification:
- operation rows: **152**;
- unique OperationIds: **152**;
- missing executor: **0**;
- missing execution profile: **0**;
- profile total: **152**;
- OperationId/capability/disposition del freeze previo: **sin delta**.

### Freeze

**POST-AUDIT.08: CLOSED / PASS · 6/6 gates.**

Inventario congelado por D-095:
- **152 OperationContracts canónicos**;
- cobertura **53/53**;
- missing **0**;
- duplicates **0**;
- taxonomía no reabierta;
- programación V5 todavía no iniciada.

### Post-D-226 current state
- historical freeze: **152 / 53 identities**;
- current bounded subtotal: **172 / 54 identities**;
- N-054: **CLOSED/PASS design**;
- N-032 Delivery/Attention: **CLOSED/PASS design**;
- N-041 Portability: **CLOSED/PASS design**;
- N-018 Saved Views: **CLOSED/PASS design**;
- remaining contract reopen: **NONE**;
- global refreeze **CLOSED / PASS — D-229**.
