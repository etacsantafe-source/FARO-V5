# FARO V5 — S3.A3 Guardados: contratos tipados, B1–B5 posterior a autorización

Fecha: 2026-11-09 AR. **Unidad técnica** `S3_A3_BOOKMARKS_CONTRACTS_CORE`. Rama única `work/s3-night-20261102`. Catálogo oficial **172** (no declarar 180). A3 y sus ocho OperationIds fueron **autorizados expresamente por el usuario**; código no iniciado antes de este permiso. **S3.A5.7 X08/X12 B7 PASS**, evidencia `docs/s3-night/S3_A5_7_X08_X12_UI_COUNTERS_B7_PASS_20261109.md`. A5 global OPEN, V2 productiva/S4/Auto/Noche OFF.

## B1 — Reusar el trabajo ya aprobado, fijar el alcance mínimo

Referencia sin repetir auditoría: `docs/s3-night/S3_GUARDADOS_BOUNDED_REVIEW_20261102.md` y `docs/s3-night/S3_A3_BOOKMARKS_B1_B5_PREAUDIT_20261105.md`. Operaciones humanas autorizadas (candidatas en inventario hasta integrar almacenamiento real): `search.bookmark.read` READ_ONLY y `search.bookmark.add`, `search.bookmark.lifecycle.set`, `search.bookmark.favorite.set`, `search.bookmark.folder.create`, `search.bookmark.folder.rename`, `search.bookmark.folder.delete`, `search.bookmark.folder.assign` AUXILIARY_MUTATION. NO son `search.saved_view.*` (consultas) ni `conversation.pin.set` (chat). **Esta subunidad implementa únicamente modelos, lectura/escritura tipadas y pruebas JVM, NO DB ni UI ni catálogo**.

## B2 — Autoridades reales y generaciones, leídas del HEAD actual

`ConversationTimeline.kt` define `ConversationMessageId` y `ConversationTimelineSnapshot(conversationId, authorityToken, revision, messages)`; `ConversationWorkspaceStore.kt` es owner N-010 del texto y ya tiene receipt + mutex + transacción. `AuxiliaryDeliveryStore.kt` usa WAL/FULL y esquema v2, con Avisos y sus intents, NO bookmarks; `AuxiliaryBackupSnapshot` y `AuxiliaryPortabilitySnapshot` sólo respaldan Avisos. Diseño de persistencia futuro: metadata + receipt en **el mismo owner Auxiliar N-018**, nuevas tablas aisladas y migración v2→v3, backups/restore/portability completas. No sexto Truth store, no contenido copiado, sin forjar receipts Reality. La lectura final deberá consultar la fuente N-010 bajo generación y ACL vigentes, negar chats archivados/ocultos/deleted/revoked sin snippets.

## B3 — Política de datos, límites y alternativas

Un `BookmarkTarget` contiene `conversationId + messageId + generationId + authorityToken` exactos; no `message.text`. Metadata de bookmark: ID opaco, target, folder opcional, favorite distinto del pin de chat, estado ACTIVE/REMOVED, revision. Carpeta plana sin cascada. Para implementación inicial se proponen **32 carpetas, 1024 bookmarks por owner, nombre de carpeta 1–80 caracteres y página de lectura hasta 50**. No purga automática de tombstones ni receipts antes de política de reconciliación explícita. Esos límites son propuestas técnicas acotadas: necesitan enforcement y prueba cuando exista DB, no son cuotas ya operativas. Alternativas rechazadas: reutilizar saved_view, copiar mensajes a otra conversación, nuevo store independiente, persistir strings de texto o generar false Reality COMMITTED.

## B4 — Implementación B6 de alcance único y prueba dirigida

Permiso sólo para: 
1. `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/BookmarkContracts.kt` — ocho IDs propuestos, tipos, solicitudes, proyecciones SIN mensajes, puertos neutrales y validaciones puras;
2. `v5/kernel/contracts/src/test/kotlin/ar/com/faro/v5/kernel/contracts/BookmarkContractsTest.kt` — **8 pruebas JVM dirigidas**: IDs exclusivos, referencias exactas, generación requerida, carpeta validada, lifecycle/favorite separados, resultados safe-unavailable, límites de página y mutación idempotente/revisión;
3. `.github/workflows/faro-v5-s3-a3-bookmark-contracts-directed.yml` — Java21, `:kernel:contracts:test --tests ...BookmarkContractsTest`, XML exacto **8/8** y marcador B6.

El contrato typed no convierte metadata en autoridad real. **No montar Guardados ni modificar indicadores**, catálogo D-229 172, backup, restore ni generated baseline hasta que los siguientes submódulos tengan persistencia y safety gates. GitHub ref explícita S3, main intocado.

## B5 — Adversarial y criterio de STOP

- Una colisión de `messageId` entre dos chats no confunde `conversationId`; restaurar generación diferente rechaza aunque IDs coincidan. Ningún modelo transporta texto sensible ni sabe leer contenido sin Workspace.
- `read` retorna `Unavailable` cuando no puede probar autoridad, NO lista vacía falsamente; `Stale` y `IdentityConflict` son resultados explícitos de mutación futura. `ACTIVE/REMOVED` no afecta el mensaje, `folder.delete` sólo para vacía bajo owner.
- Las escrituras futuras serán CAS expectedRevision, idempotencyKey y recibos auxiliares atómicos; Chat→Review→auxiliary outcome sin Reality. Esta subunidad sólo verifica la forma del contrato, NO declara que ya existe almacenamiento/Undo.
- Guard B1–B7 obligatorio, commit funcional posterior al permiso con `FARO-UNIT:S3_A3_BOOKMARKS_CONTRACTS_CORE`; si CI/8 tests falla, B7 RED_FIX misma unidad. No reinterpretar B5 como certificación de todos los ocho workflows; G15 físico una campaña final.
