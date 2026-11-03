# FARO V5 — S3 · revisión acotada de ocho operaciones candidatas de Guardados
Fecha local: 2026-11-02. Autorización: revisión acotada, **NO incorporación al catálogo**. Estado: **REVIEWED_PROVISIONAL / NO_APPROVED / SPEC_ONLY / IMPLEMENTATION_NOT_RUN**.

## Línea de autoridad y alcance
- Branch exclusiva S3: `work/s3-night-20261102`. S2 permanece CLOSED 8/8, app V2 OFF, S4 OFF, main/V4.4 intocados.
- Catálogo oficial: **172 OperationContracts** / 54 capacidades (D-229); `search.saved_view.read/create/update/delete` ya son cuatro operaciones de *consultas guardadas*, **no** bookmarks. El número 180 es sólo suma hipotética 172+8, NO catálogo vigente.
- El diseño S3 había reservado ocho operaciones candidatas sin nombres individuales congelados; **los identificadores siguientes son nombres de trabajo propuestos durante esta revisión**, no identidades anteriores ni aprobación implícita.
- N-018 puede poseer el índice/metadata auxiliar de Guardados; N-010 Workspace conserva la autoridad de conversación y mensajes. El bookmark nunca copia texto del mensaje ni altera Reality, Avisos, History ni Workspace chat archive. No inventar nuevos stores/owners de dominio por una superficie.
- Carpeta plana (sin árbol), favoritos sobre bookmark (distinto del pin de chat), acciones materiales por Chat con Review explícito. Navegación/lecturas no deben materializar efectos.

## Ocho candidatos — propuesta revisable, NO registros oficiales
| # | OperationId PROVISIONAL | Perfil | Contrato/criterio bounded | Estado |
|---|---|---|---|---|
| 1 | `search.bookmark.read` | READ_ONLY | Lista paginada/filtrada de metadata autorizada (incluye carpetas y favoritos); abrir target obliga relectura N-010, ACL y generación, sin texto cacheado | CANDIDATE |
| 2 | `search.bookmark.add` | AUXILIARY_MUTATION | Crea atajo a `MessageRef(conversationId,messageId)`, nunca duplica mensaje; valida owner y generación; idempotencia estable/receipt auxiliar | CANDIDATE |
| 3 | `search.bookmark.lifecycle.set` | AUXILIARY_MUTATION | `ACTIVE/REMOVED` explícitos: quitar/recuperar bookmark, sin borrar/restaurar mensaje ni simular papelera Reality; rechazar target inválido | CANDIDATE |
| 4 | `search.bookmark.favorite.set` | AUXILIARY_MUTATION | Favorito booleano de bookmark, separado de `conversation.pin.set`; idempotente y reversible según receipt auxiliar | CANDIDATE |
| 5 | `search.bookmark.folder.create` | AUXILIARY_MUTATION | Crea carpeta plana con id estable, validación de nombre, unicidad por owner y límite bounded; no crea contenido | CANDIDATE |
| 6 | `search.bookmark.folder.rename` | AUXILIARY_MUTATION | Renombra carpeta por id/expected revision, sin cambiar referencias de sus elementos | CANDIDATE |
| 7 | `search.bookmark.folder.delete` | AUXILIARY_MUTATION | Por defecto REJECT si contiene bookmarks; no cascada silenciosa ni borrado de mensajes. Reasignación previa explícita | CANDIDATE |
| 8 | `search.bookmark.folder.assign` | AUXILIARY_MUTATION | Mueve/quita asignación de un bookmark a carpeta existente o raíz por id, bajo mismo owner/ACL y CAS | CANDIDATE |

## Revisión de riesgos e invariantes por contrato
1. **Identidad**: `conversationId + messageId` no basta si la autoridad se restauró; persistir identificador de generación/provenance y revalidar snapshot del owner actual. Nunca usar índice de UI o preview como ID.
2. **Lectura / privacidad**: devolver metadata mínima; al abrir: resolver conversación vigente, mensaje, estado archive/deleted/hidden/revoked y ACL; si no se prueba, `UNAVAILABLE` y sin snippet; no copiar secreto a receipt.
3. **Escrituras**: sólo auxiliar con writer/expected revision, journal+receipt del **owner auxiliar** en la misma transacción que mutación; `UNKNOWN` se reconcilia y no se reejecuta ciegamente.
4. **Undo**: un inverso auxiliar puede originar nueva operación con expected state y receipt propio; N-016 `undo.execute` se reserva a Reality. No forjar `ExecutionReceipt` ni `ConversationPlanReviewSnapshot(COMMITTED)` de Reality.
5. **Ciclo de vida**: bookmark `REMOVED` ≠ borrar mensaje; carpeta ≠ archived chat; `folder.delete` exige vacía; restaurar bookmark nunca resucita contenido oculto/revocado. Definir retención y garbage collection de tombstones ANTES de freeze.
6. **Idempotencia**: clave lógica con operación+objeto+intención/versión (sin contenido sensible); replay devuelve mismo resultado/receipt; conflicto de parámetros => `IDENTITY_CONFLICT`; concurrent writes/restore => `STALE` seguro.
7. **Contabilidad**: no añadir operaciones para "abrir", "scroll", "foco", "filtro", "contador", "consultar con FARO" ni acciones ya cubiertas por `search.query` / Saved Views. No mezclar fotos, URLs o tareas si no existe referencia owner exacta y contrato específico.
8. **Persistencia/backup**: definir tablas auxiliares, transaction boundary, claves foráneas lógicas e invalidación por restore, migración y eliminación; control de cuota, carpetas vacías y seguridad de generación. La conexión real con N-018 y N-010 está pendiente.

## Evidencia revisada y huecos que NO deben maquillarse
- P00: `docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md`; H01–H04/R0–R3 congelados solo como especificación, X01–X12 no ejecutados.
- Código consultado: `v5/kernel/contracts/.../ConversationTimeline.kt` (`ConversationMessageId`, `ConversationTimelineSnapshot.authorityToken/revision`); `ConversationWorkspace.kt` (`ConversationId`, archived/pinned); `ConversationWorkspaceStore.kt` (mutex/SQLite y receipts de Workspace, no bookmark registry). Coincide con separación N-010/N-018; **no acredita motor de bookmarks implementado**.
- V4.4 (comparación READ_ONLY documentada en P00): búsqueda deduplicada/advertencia de owner, archivado reversible y Saved Views D-226. Reutilizar semántica sin copiar almacén ni imponer equivalencia entre consulta guardada y bookmark.
- Benchmark documental: `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md` (Obsidian, Notion, Keep, GitHub, Android). Es investigación, no prueba de app instalada.
- Pendientes antes de promover catálogo: (a) dueño de storage auxiliar N-018/Workspace y recovery de generación; (b) definiciones exactas de `REMOVED`/restore y retención; (c) forma del receipt auxiliar y dispatch Chat/Review; (d) cuotas y seguridad de carpetas; (e) tests reales de SQLite/Android/restore/ACL; (f) revisión de compatibilidad con D-229 y decisión humana INDEPENDIENTE.

## Gate de aceptación, aún NOT_RUN
- B01–B02: colisión de `messageId` entre chats y target válido apunta sólo a conversación/mensaje correctos.
- B03: mensaje eliminado/oculto/revocado, chat archivado o restore de otra generación => no filtra contenido previo.
- B04: `add` repetido y crash entre journal/commit no duplican bookmark ni receipt.
- B05: `favorite.set` repetido, revisión obsoleta y undo auxiliar seguro.
- B06: carpeta vacía se puede borrar; no vacía falla con mensaje seguro; movimiento/root explícito.
- B07: `read` bajo ACL/owner unavailable retorna parcial/no disponible, no falsos ceros.
- B08: Chat Candidate→Review→auxiliary outcome, nunca falso COMMITTED Reality; UNKNOWN exige reconciliación.
- Complementar X04, X05, X09 y X10 de la especificación S3 y test físico único al final de S3.

## Resultado y decisión humana pendiente
**Se acepta únicamente la apertura y realización de esta revisión.** Resultado: ocho perfiles/owners preliminares y riesgos explicitados; ninguno entra todavía a inventario, registry, cola de implementación A3 ni plan de producto certificado. Mantener **172**, no 180. Resolver huecos y someter una propuesta final separada para aprobación expresa. No reabrir S2, no tocar S4, V2 OFF.

## Preparación Noche V2.1, sin activar
- Protocolo durable `docs/v5/FARO_NOCHE_PRODUCTIVITY_V21_20261102.md`, con bucle productivo 0–20, cierre 20–25, checkpoint/release 25–27, READ_ONLY 27–30.
- Cola CLOUD permanece `STAGED`, `activation_authorized=false`; 7 tareas S3 conservadas; writer debe terminar `RELEASED`. A/B/informe deben permanecer OFF hasta aprobación explícita separada con **nuevo corte** (no reutilizar el 02/11 22:00).
- A1 sigue parcial (única fuente real de Inicio: Avisos N-032; faltan Pendientes N-031, Actividad N-015, Guardados); A2–A4 no empezar fuera de dependencias.
- N21-01..08 siguen NOT_RUN: antes de certificar scheduler V2.1 hay que verificar gates de cancelación, autorización/deadline, contienda/CAS y métricas. El gate N00 previo NO demuestra carrera simultánea.
- ACTIVACIÓN NO AUTORIZADA EN ESTE CHECKPOINT. Preparado para preflight y autorización futura; ni horarios ni cola se habilitan ahora.
