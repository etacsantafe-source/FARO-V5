# FARO V5 · W02-B · Alta y edición con Review/Apply

Unidad `S4_W02_B_OBJECTIVE_REVIEW_APPLY`. Base funcional inspeccionada `6c1d1edea9b64015dd0ba2dbc5c6919f186c1ceb`. Esta evidencia prepara el diseño y no abre un permiso. Antes de READY se requiere disposición B7 de W02-A, guardia de esta auditoría y revalidación contra HEAD.

## B1
- El catálogo oficial de 180 ya contiene N-030: `objective.create`, `objective.update` y `objective.lifecycle.set` con executor `planning`, `REALITY_MUTATION` y `BINDABLE`. Se usan create/update existentes. El lifecycle queda para W02-C.
- Workspace ya guarda una Review con envelope, fingerprint, revisión, selección, token de timeline y decisión humana. ReviewedConversationApplyCoordinator ya resuelve reclamo, bind, transacción y conciliación por recibo. En Objetivos falta el payload canónico, el binding de mutación y el host de permiso del objeto.
- Aceptación. Alta/edición manual desde el contexto seleccionado, Review persistida y descartable, una mutación de Reality con su receipt por confirmación, replay sin cambio duplicado y rechazo ante cambios concurrentes de owner, generación o grant.

## B2
- Leído V4.4 en snapshot `bfa0ee71f5b9cd104baaadd71f1e77a61f203939`. FaroV4ObjectivesActions propone CREATE/UPDATE para Apply explícito y UPDATE permite distinguir campos omitidos de eliminar fecha/meta. Mantener separación entre proponer y aplicar. El formulario V5 entrega el estado completo visible y confirmado de título, detalle, prioridad, objetivo y fecha, con valores opcionales nulos para limpiar. El historial y el estado se obtienen de Reality en el binding, sin aceptar sustitución desde el payload de edición.
- Reusar ReviewedConversationApplyCoordinator, ApplyPreflightGate, ApplyTransactionExecutor, ApplyRetryReceiptGate, Workspace y el grant revocable en memoria de FaroV5Planning8ObjectGrantOwner. El lease serializa revocación local bajo un host DEV. Usar las comprobaciones de sandbox, generación completa y foreground del host de la sesión. Reutilizar el patrón de validación de la revisión de MonthlyPlanItemAddBinding, sin modificar el host W01 ni el Plan del Mes.

## B3
- [EF Core Optimistic Concurrency](https://learn.microsoft.com/en-us/ef/core/saving/concurrency). ADAPTAR comparación de versión original al escribir. La comparación solo en preflight deja un intervalo de carrera; repetirla con scope activo antes del upsert. DESCARTAR EF, .NET y dependencias. Probar dos ediciones confirmadas con el mismo snapshot y rechazar la segunda cuando la primera ya modificó el owner.
- [SQLite Isolation](https://www.sqlite.org/isolation.html) y [SQLite Transaction](https://www.sqlite.org/lang_transaction.html). USAR BEGIN IMMEDIATE existente en Reality y una lectura/revalidación dentro de la misma conexión. DESCARTAR transacciones adicionales y edición directa por el formulario. Probar carrera entre aprobación y transacción.
- Reusar ApplyRetryReceiptGate para recuperar una confirmación en estado incierto tras commit. DESCARTAR reintentar el upsert a partir de la UI o tratar un identificador de solicitud del caller como autoridad. El flujo sigue offline; costo y dependencias se mantienen.

## B4
- Payload canónico acotado, con propósito de la solicitud (operación catalogada), objetivo exacto, campos de alta/edición completos y revisión esperada. Preparar plan de un nodo con identidad de intención estable, revalidar bytes y contrato al cargar y al ejecutar. Crear en PLANNED. Edición mantiene lifecycle e historial; si el timestamp no puede avanzar, la mutación se rechaza.
- Alcance del grant: operationId + objectId + decisión humana canonizada + generación/sequence + epoch de host. No emitir grant desde Search ni antes de una Review aprobada y foreground. Reusar el lease por objeto y revocar al cerrar la sesión. Revalidar tras transición a APPLYING, en la autorización en SQL y antes del COMMIT. No leer Workspace dentro de SQL; mantener el orden de locks documentado por Planning8.
- Rutas candidatas para el preflight exacto, a confirmar con el HEAD posterior a B7 de A:
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveReviewedEdit.kt`
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveReviewedEditBinding.kt`
  - `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W02ObjectiveReviewHost.kt`
  - `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W02ObjectiveReviewHostTest.kt`
  - `.github/workflows/faro-v5-s4-w02-b-objective-review.yml`

## B5
- Pending/discard no deja escritura ni receipt. Doble confirmación y conciliación recuperan como máximo el recibo ya durable. Validar payload/fingerprint falsificado, owner revision viejo, título vacío, moneda sin unidad y cambio de objetivo. Una edición válida conserva la secuencia histórica.
- Probar revocación, background/host epoch viejo, pointer ABA con la misma generación y otro sequence, operación inesperada, plan multinodo y selección de Chat cambiada. Capturar la revocación local antes de COMMIT o rechazar; autoridad interproceso no se certifica con un lease local. Montaje Android de usuario en E.
- Mantener W01, el catálogo, Finance/Matter y los mecanismos de escritura/recibos comunes. Local only, W03 detenido y V2 productiva OFF.
