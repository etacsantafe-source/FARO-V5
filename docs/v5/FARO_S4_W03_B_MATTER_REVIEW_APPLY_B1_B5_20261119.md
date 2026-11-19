# FARO V5 · W03-B · Asuntos Review/Apply, recibos, Undo · B1–B5

Fecha: 2026-11-19. Unidad exacta: `S4_W03_B_MATTER_REVIEW_APPLY`. HEAD auditado: `17191e67419d03a44e73f9da5bb36d2cd41effae`. **PREAUDITORÍA DOC_ONLY; permiso BLOCKED; B6/B7 NOT_RUN.** W03-A B7 PASS DEV 33/33 y W01/W02 PASS DEV: NO repetir.

## B1 — Estado y fuentes verificadas
- `AGENTS.md` (blob `ae30e443518633dbc21a4d448e73455f0422cf59`), puntero de continuidad y permiso W03-A BLOCKED (blob `260954c74e9d4c7c0d6f467af99d5d2913deea6e`). HEAD remoto exactamente `17191e67419d03a44e73f9da5bb36d2cd41effae`; writer remoto RELEASED epoch 1292 antes de esta publicación documental. Guardia de W03-A `44536914665` SUCCESS; CI W03-A `44536914638` SUCCESS con 7 XML, 33/33.
- W03-A `MatterModel.kt` blob `5412676822aab3ec16d7a66b01ce1f0127f103a4`: `MatterRecord` tipado; `MatterDetails(priority, reviewDate, history)`, `MatterHistoryEntry` append-only, `MatterStatus` OPEN/IN_PROGRESS/WAITING/RESOLVED/ARCHIVED, `updatedAtEpochMillis`. `MatterPersistencePort.kt` blob `3edd39208b7e6b7328294526f77e3e336d39ade3` expone `read/upsert` bajo `RealityTransactionScope`, sin grants propios.
- `RealityStore.kt` aplica `upsertMatter()` con scope transaccional real, timestamp estrictamente creciente, `RealityMatterDetails.write()` y revision `matter_state` en la misma transacción. No hay editor humano enlazado a ese port.
- Catálogo de 180 descriptores ya incluye exactamente `matter.create`, `matter.update`, `matter.lifecycle.set`, `matter.snooze`, todos N-031/canonical executor planning/REALITY_MUTATION/BINDABLE. NO agregar IDs ni alterar catálogo.
- Host W02 de referencia, no para modificar: `S4W02ObjectiveReviewHost.kt` blob `e74fde1da0166a792919c6b3607807c8b55bba51`, `ObjectiveReviewedEditBinding.kt` blob `a4e97cf09efcc76089edc2b7711cd3055c9c67b6`, `S4W02ObjectiveReviewHostTest.kt` blob `6c0225cfbf99266706f16c8a82a06a4abc1e39b1`. Patrón: Workspace PENDING, explicit APPROVED, `PreparedPlan` canónico, CAS revision, `Planning8ObjectScope`/grant de política independiente, `ReviewedConversationApplyCoordinator`, recibo durable y reconciliación UNKNOWN. Host grant `FaroV5Planning8ObjectGrantOwner.kt` blob `5301094c14dabdca7a024dc5bd6b505fe146f746` es aislamiento DEV local; no certificar política interproceso productiva.
- Handoff W03 `FARO_S4_W03_WORK_TURBO_HANDOFF_PREP_20261119.md`, preauditoría general W03 `FARO_S4_W03_MATTERS_B1_B5_PREAUDIT_20261119.md`, P05 PF15–17, P07 P7T17, P03 R08. W03-C/D/E/G quedan fuera.

## B2 — V4.4 y reutilización
- V4.4 `etacsantafe-source/FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939`, `FaroV4FollowUpSurface.kt` (L08) modeló fechas/prioridad/estados/acciones y `FaroV4FollowUpReversalContractTest.kt` validó reversión. **ADAPTAR** la semántica humana de Esperar, Resolver, Cerrar, Reabrir y deshacer con evento compensatorio. NO copiar Activity, base, reglas de correo ni runtime V4.4.
- Reusar el patrón probado W02-B Review/Apply y W02-C historial tipado; **no editar** su código ni duplicar mecanismos comunes, ni alterar Finance9, Workspace, Reality9, codec G4, W03-A.
- `ARCHIVED` no es un borrado físico, `RESOLVED` no equivale a ACK de Recordatorio; el aviso/dismiss no cambia Matter. Una Undo no elimina recibos ni historial.

## B3 — Alternativas y criterio técnico
- Documentación oficial `https://www.sqlite.org/isolation.html` y `https://www.sqlite.org/lang_transaction.html`: **USAR** revisión de owner + transacción existente para rechazo de segunda edición concurrente. **DESCARTAR** guardar directamente desde UI o implementar otro SQLite.
- Documentación de Android offline-first `https://developer.android.com/topic/architecture/data-layer/offline-first`: **ADAPTAR** owner local como única verdad, sin sync ni APIs pagas.
- Patrón Event Sourcing, `https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing`: **ADAPTAR** historial append-only y Undo como operación compensatoria explícita. **DESCARTAR** dependencia Azure/cola/reconstrucción total de owner.
- V4.4 y W02-B ya ofrecen alternativas implementables bajo la licencia del propio repositorio. Tasks.org GPL-3.0 y Vikunja AGPL-3.0 solo son referencias UX de la preauditoría general; no copiar código ni importar sus dependencias.
- Opciones: A) `PreparedPlan` canónico → Review humana → grant independiente → Apply único → receipt durable, **ELEGIDA**; B) formulario → upsert directo, **RECHAZADA**; C) Undo por borrar history o reescribir receipts, **RECHAZADA**. Para create, Undo = archivar compensando, no borrar Matter ni el receipt; para edit/status, nueva mutación revisada que preserve el prefijo.

## B4 — Contrato exacto a implementar
- `MatterReviewOperations`: cuatro OperationIds N-031 existentes; validador fail-closed contra descriptor actual. `MatterMutationIntent`: id, revision esperada, instante monotónico y acción tipada `Create/Edit/Transition/Snooze/Compensate`; payload binario canónico acotado con decode+reencode igual a bytes de entrada y tipado semántico sin strings arbitrarios del cliente. `Compensate` usa `matter.update` o `matter.lifecycle.set` según mutación compensada, sin OperationId inventado.
- `Create` produce OPEN con evento CREATED; `Edit` conserva status y preserva historial; `Transition` valida transición explícita y razón, con evento STATUS_CHANGED; `Snooze` mantiene status, ajusta dueAt y adjunta evento EDITED; `Compensate` solo sobre revision/evento corriente y crea evento nuevo, NO borra el evento deshecho ni permite saltar la autorización. El historial legacy permanece vacío hasta el primer cambio real.
- La validación de `expectedRevision`, CAS de intención y sello de tiempo se repite **dentro** de la transacción Reality después de BEGIN IMMEDIATE. El registro resultante se entrega exclusivamente al port existente `MatterPersistencePort.upsert`. Sin grants desde Search/Preview/IA; política independiente por scope Matter, generación/pointer y epoch del foreground. Revalidación al aprobar, antes de Apply, durante transacción y antes de COMMIT. Si falla, denegar y preservar Reality.
- `Workspace`: PENDING no muta, DISCARD no muta, APPROVED no ejecuta hasta confirmación explícita; `ApplyRetryReceiptGate` reconcilia UNKNOWN por identity+receipt previo sin reaplicar. Doble tap misma review no duplica receipts; cambiar chat/host invalida grant.
- **Cinco rutas exclusivas a READY posterior, todas nuevas**:
  1. `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterOperations.kt`
  2. `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterReviewBinding.kt`
  3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W03MatterReviewHost.kt`
  4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W03MatterReviewHostTest.kt`
  5. `.github/workflows/faro-v5-s4-w03-b-matter-review.yml`
- Scope DEV JVM/SQLite, NO Android shipping; si Kotlin necesita dependencia/otra ruta, **STOP**, ampliar B1–B5 y READY primero. Mantener V2 productiva, Auto y Noche OFF.

## B5 — Matriz adversarial cerrada para el diseño (tests TODAVÍA NOT_RUN)
- T-B01: create y edit en PENDING/DISCARD/APPROVED sin ejecución no alteran Matter ni receipt.
- T-B02: doble submit con idempotency key, doble apply y recibo persistente → una escritura/receipt; reconcile-only no crea recibo.
- T-B03: dos edits o dos cierres sobre la misma revision → el segundo falla sin alterar primero.
- T-B04: grant ausente, revocado, foreground falso, otra selección de Chat, cambio de generación/epoch/política → 0 mutaciones.
- T-B05: receipt UNKNOWN solo reconcile antes de retry; restart/ABA no autoriza duplicado.
- T-B06: Undo de estado/edición por nuevo Review/Apply: restaura solo campos explícitamente autorizados, mantiene TODOS los eventos anteriores y añade uno compensatorio; Undo create = ARCHIVED (no delete).
- T-B07: RESOLVED→OPEN y ARCHIVED→OPEN, motivo obligatorio, eventos ordenados; transición inválida, timestamp stale y payload falsificado DENY.
- Regresiones acotadas: legacy Matter W03-A G4/FBS2 no se altera por leer, W01/W02 inalterados; pruebas nuevas JVM+JUnit XML, guard por SHA y delta solo estas 5 rutas. Fuera de alcance: recordatorios W03-C/D y UI/Android W03-E, G15 NOT_RUN.

## Puerta a B6
Publicar este documento solo, release writer, confirmar guard del SHA documental SUCCESS. Luego permiso READY **para estas cinco rutas exactas** con evidencia de blob SHA de este B1–B5, en commit documental separado, release/recheck guard. Preflight de cada ruta sobre HEAD padre, adquirir writer para B6, test dirigido/XML; B7 independiente tras corrección real, consumir READY BLOCKED y writer RELEASED. Ningún PASS inferido de este documento.
