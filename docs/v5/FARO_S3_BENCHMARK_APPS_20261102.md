# FARO V5 S3 — Benchmark externo: recuperación de patrones y huecos (2026-11-02)

ESTADO: INVESTIGACIÓN Y PROPUESTA DE DISEÑO / NO IMPLEMENTADO / NO AUTORIZA ACTIVAR NOCHE.
Protocolo: comparar documentación oficial, contrastar V4.4 y V5, detectar huecos, revisión adversarial y convergencia. No importar código propietario. S2 CLOSED 8/8; app V2 OFF; S3/S4 aún no abiertos; mantener main y repo V4.4 intactos.

## Comparación de referentes

| Producto | Comportamiento comprobado en sus docs | Recomendación específica S3 |
|---|---|---|
| Obsidian Bookmarks | Marcadores a archivos, encabezados, bloques y búsquedas; agrupaciones | Recuperar navegación exacta a MessageRef(conversationId,messageId), foco/resaltado; no duplicar contenido |
| Obsidian Search | Operadores, búsqueda con términos recientes y contexto | Resultados con tipo, owner, fragmento autorizado y motivo de coincidencia; no DSL complejo ni guardar consultas privadas automáticamente |
| Notion | Search por sidebar, recientes, filtros de área/fecha/título, relevancia/recencia | Filtros simples y ranking determinista con cobertura visible; resultado local primero |
| Google Keep | Fijar, etiquetar, archivar/desarchivar; eliminar es distinto | Separar pin de chat, marcar favorito, guardar atajo, archivar y borrar entidad; no falsa papelera de Reality |
| Linear Inbox | Priority vs Other, leído, snooze y canales | Vistas Necesitan atención/Otros con filtros sin alterar fuente; Snooze requeriría bounded reopen N-032, no disfrazarlo de dismiss |
| GitHub Notifications | Save, Done, Read y filtros, sin alterar issue/PR original | Avisos=requieren atención; Guardados=acceso futuro; Actividad=eventos/receipts; un aviso cerrado no cancela un recordatorio |
| Todoist | Quick Add interpreta fechas y recurrencias | En Chat, convertir lenguaje natural a Candidate y ofrecer corrección/confirmación; no crear parser o scheduling paralelo en S3 |
| n8n HITL | Approval por herramienta seleccionada antes de ejecutar, parámetros visibles | Preview exacto antes/después y receipt correcto por owner; Auxiliary separado de Reality Apply (H04) |
| Anytype local-first | Lecturas/escrituras locales independientes de sincronización remota | Distinguir guardado local, respuesta IA y efectos Android; no falso Applied/Delivered |
| Android/SQLite | Offline-first, WorkManager constraints, FTS Unicode, permisos Android 13+ | Búsqueda local offline, índices reconstruibles, prueba con español y fallos, avisos OS verificados sin asumir lectura |

## Huecos y refinamientos detectados

**R0/P0 — Actividad omitida del plan visible S3.** El alcance conversacional incluía Actividad, pero el diseño S3 consolidado no la ponía en drawer; el enum V5 actual no tiene destino ACTIVIDAD. N-015 YA tiene `history.query` y `history.receipt.read`. Diseñar una vista Actividad READ_ONLY desde History, separada de Avisos y Guardados, con filtros por tipo/fecha/owner, recibo y efecto conocido. No crear store/OperationContract nuevo para su lectura.

**R1/P0 — Búsqueda explicable.** Usar `search.query` con hit tipado: owner, kind, ID estable, matchField, título, fragmento redacted, asOf, estado de cobertura y autorización al abrir; ordenar relevancia/recencia, filtrar por fuente/tipo/fecha/título. Evitar UI que muestre «sin resultados» cuando un owner falló. La ampliación de payload requiere compatibilidad/versionado.

**R2/P0 — Guardados de mensajes.** Referencia `MessageRef(conversationId,messageId)` inequívoca; scroll al mensaje exacto y resaltado, protección si la entidad fue borrada/ocultada/restaurada. Reutilizar 8 contratos Guardados aún CANDIDATOS, NO declararlos congelados.

**R3/P0 — Aprobación visible correcta.** El modelo propone, la persona revisa parámetros literales, FARO ejecuta por owner; mutation de Auxiliary recibe receipt auxiliar y no falsifica `ConversationPlanReviewSnapshot(COMMITTED)` de Reality. Se conserva Apply/Undo certificado en S2.

**R4/P1 — Bandeja de Avisos clara.** Priority/Otros por severidad + atención, no por estado de entrega OS. Mostrar motivo y fuente, no auto-mark-read en una lectura. Snooze merece propuesta separada y bounded reopen de N-032, no incluir en S3 por defecto.

**R5/P1 — Español y FTS.** Probar unicode61/diacríticos/tildes/ñ contra SQLite real antes de elegir FTS4/FTS5. Índice derivado, local, invalidado al restore y sin reexponer snippets revocados.

**R6/P1 — Offline/recovery.** S3 sigue usable sin red; index y resúmenes IA son opcionales. WorkManager para trabajos postergables con constraints/backoff, jamás auto-retry de Reality Apply UNKNOWN.

**R7/P1 — Accesibilidad.** Targets táctiles mínimo 48dp, lector de pantalla, estados no sólo por color, foco/restauración y drawer único sin segunda navegación.

## Doce escenarios adversariales adicionales (pendientes de implementación)
X01 Actividad sólo declara operaciones completadas con receipts canónicos.
X02 No duplicar un hecho entre Avisos y Actividad.
X03 Dismiss Notice no cambia la fuente Reality.
X04 MessageRef idéntico en chats distintos lleva al mensaje y chat correctos.
X05 Target de Guardados revocado/borrado/restaurado no filtra contenido previo.
X06 Hit explica campo, origen, fecha y orden de empate estable.
X07 Owner/FTS caído => resultados parciales + cobertura, nunca falso «sin resultados».
X08 ContextPack no egresa SECRET ni UNCLASSIFIED sin garantías.
X09 Review auxiliar obsoleto da STALE, sin falsas filas Reality.
X10 Retry idempotente de saved.add no duplica efectos/receipts.
X11 Búsquedas español/acento, consultas canceladas y restore nunca mezclan generaciones.
X12 Drawer/targets/avisos accesibles, foco estable en navegación.

## Rechazos deliberados
No copiar bases ni código de otras apps, ni crear cloud obligatorio, sincronización P2P/CRDT, árboles de carpetas, multi-etiquetas, DSL de búsqueda avanzado, snooze sin operación, un nuevo motor de recurrencias, auto-Apply o una navegación duplicada. Guardados sigue referencia auxiliar, Reality sigue autoridad, Evidence sigue separado.

## Semántica de estado
Los 172 OperationContracts oficiales siguen intactos. La ampliación N-018 de 8 operaciones Guardados (172→180) sigue CANDIDATA local hasta revisión bounded y registro; no hay nuevos OperationIds por este benchmark. Conservar correcciones H01–H04 del informe adversarial previo. S3 no se abre por documentar esto. El ticket obligatorio para Noche al abrir S3 es cerrar R0–R3 junto con H01–H04, luego R4–R7 según precedencia.

## Evidencia y fuentes primarias
- https://obsidian.md/help/Plugins/Bookmarks
- https://obsidian.md/help/Plugins/Search
- https://www.notion.com/help/search
- https://support.google.com/keep/answer/6191044?co=GENIE.Platform%3DAndroid&hl=es
- https://support.google.com/keep/answer/6262765?co=GENIE.Platform%3DAndroid&hl=es
- https://linear.app/docs/inbox
- https://docs.github.com/en/subscriptions-and-notifications/concepts/about-notifications
- https://www.todoist.com/es/help/todoist/features/use-task-quick-add-in-todoist-va4Lhpzz
- https://github.com/n8n-io/n8n-docs/blob/main/docs/build/integrate-ai/ai-examples/human-in-the-loop-for-tools.md
- https://docs.any.org/understanding/local-first.html
- https://developer.android.com/topic/architecture/data-layer/offline-first
- https://developer.android.com/training/data-storage/room/defining-data
- https://www.sqlite.org/fts5.html
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/develop/ui/compose/notifications/notification-permission
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults

## Nota de método
Tres rondas: I exploración oficial de productos, II comparación con V5/source+especificación S3, III adversarial de autoridad/privacidad/costo/alcance. No se instalaron/probaron manualmente las apps; no se compiló S3, ni se ejecutaron X01–X12. El informe sólo demuestra hallazgos de diseño sustentados.
