# FARO V5 · S3.A3 Guardados — B1–B5, checkpoint 05/11/2026

**Resultado:** B1–B4 con investigación y diseño; B5 = STOP antes de código por ausencia de autorización para nuevos contratos. B6/B7 NOT_RUN. Este checkpoint es solo documentación: catálogo oficial 172, S2 8/8 congelado, S4 OFF, V2 productiva OFF, Noche/Auto OFF, G15 reservado para A5.

## B1 · Evidencia / alcance

HEAD previo `69e5050fc3b311cadfe8ac115271e32ff97b3870`, rama `work/s3-night-20261102`. A1 CLOSED_AUTOMATED_SCOPE_UI_SHELL_ONLY, A2 CLOSED_AUTOMATED_SCOPE 5/5; A3 PENDING antes de este documento. `docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md` congeló R2/H01–H04 solo como especificación; `docs/s3-night/S3_GUARDADOS_BOUNDED_REVIEW_20261102.md` estudió 8 candidatos y **NO los aprobó**.

Código real leído en esta rama:
- `v5/kernel/contracts/.../ConversationTimeline.kt` blob `a3f51cdd5551f108d198d097b5195bede0b95512`: `ConversationMessageId`, `ConversationTimelineSnapshot.authorityToken/revision` y read port.
- `v5/kernel/contracts/.../ConversationWorkspace.kt` blob `bd3834a2a5dab94b9240a3d556934c3d46c2b1ff`: `ConversationId`, archivo/pin a nivel conversación.
- `v5/workspace/.../ConversationWorkspaceStore.kt` blob `cfaa639e1d84dcc91e672d86792bd30ba3a0dbb5`: owner canónico y receipts transaccionales de Workspace, NO almacenamiento bookmark.
- `v5/store/auxiliary/.../AuxiliaryDeliveryStore.kt` blob `9c5de8aef1343f2e2f1dcd61467ac51ee0d35e1a`: persistencia de avisos/entrega, NO owner Guardados.
- `v5/surface/.../FaroV5HomeIndicators.kt` blob `907e957d629e971dd3f12cd2ac08c3270616adc4`: indicador Guardados debe seguir «No disponible» si falta owner real.
- `docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md` blob `a161f7f57eaf6285edb1f250ce691d07725b32dc`: 172 aprobadas; `search.saved_view.*` representa consultas guardadas, no bookmarks.

Criterios A3: referencia exacta a mensaje, privacidad/ACL, archivo separado, borrado/restore, favorito, carpetas, idempotencia y receipt auxiliar. Pruebas dirigidas B01–B08 y físicas X01–X12 **NO EJECUTADAS** para A3.

## B2 · Comparación V4.4

Reutilizar comparación READ_ONLY ya asentada en P00: archivado reversible de conversación, pin de chat y Saved Views D-226. Ninguna propiedad equivale a guardar mensaje individual. N-010 conserva autoridad del texto/timeline, N-018 la semántica de búsqueda; un bookmark debe apuntar a la fuente, no copiarla ni crear un sexto Truth store. No afirmar en este turno un nuevo ensayo de V4.4.

## B3 · Investigación externa (documentación primaria consultada)

- Slack Save for Later: https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later — guardado privado y apertura en conversación original. **ADAPTAR UX** de referencia/contexto; no adoptar cloud ni inferir internals.
- Obsidian Bookmarks: https://obsidian.md/help/Plugins/Bookmarks — accesos directos a bloques/archivos, grupos y rutas. **REFERENCIA** para carpetas/atajos; no acredita seguridad de FARO.
- Telegram Saved Messages: https://core.telegram.org/api/saved-messages — mensajes reenviados a chat propio. **DESCARTAR** duplicación del contenido y servicio cloud para FARO.
- Google Keep: https://support.google.com/keep/answer/6191044 — etiquetas/pins de nota. **REFERENCIA UX**; no equivalente a identidad de mensaje.
- SQLite UPSERT: https://www.sqlite.org/lang_upsert.html — UNIQUE permite DO NOTHING/UPDATE. **ADAPTAR** para idempotencia con receipt + transacción real, no basta UPSERT solo.
- Android Room: https://developer.android.com/reference/androidx/room/RoomDatabase — transacción/invalidation; no asumir invalidación entre procesos. **REFERENCIA** para reads revalidados.

Arquitecturas: (A) reusar `search.saved_view.*` DESCARTADA: es query; (B) `conversation.pin.set` DESCARTADA: es chat; (C) chat de mensajes reenviados DESCARTADA: copia sensible; (D) metadata de `MessageRef` bajo owner N-018 respaldada por dominio Auxiliar existente: **OPCIÓN DE DISEÑO**, no aprobación de código.

## B4 · Diseño unificado, todavía no aprobado

Referencias `conversationId + messageId + identidad de generación` (y revisión/ACL verificada al usar). Al abrir, N-010 resuelve contenido original y verifica autoridad antes y después: si archived/hidden/deleted/revoked/restore inválido, responder «No disponible», nunca snippet viejo. N-018 conserva metadata mínima, `folderId` opcional, `favorite`, ciclo `ACTIVE/REMOVED`, revisión e idempotency key; la persistencia auxiliar exige nuevo contrato/tabla aislado de Avisos, migración y backup/restore coherentes con cinco stores; sin inventar owner nuevo.

Ocho OperationIds **solo candidatos**, no catálogo: `search.bookmark.read`, `search.bookmark.add`, `search.bookmark.lifecycle.set`, `search.bookmark.favorite.set`, `search.bookmark.folder.create`, `search.bookmark.folder.rename`, `search.bookmark.folder.delete`, `search.bookmark.folder.assign`. Propuesta: carpeta plana; no cascada al eliminar carpeta; borrar bookmark no borra mensaje; listado/navegación READ_ONLY sin materialización; writes por Chat→Review→receipt auxiliar, no Reality COMMITTED/Undo. Oficial sigue 172, NO 180.

## B5 · Adversarial y criterio de STOP

Negativas antes de permiso READY: B01 colisión de `messageId` entre conversaciones; B02 restore de otra generación/revisión; B03 mensaje revocado/oculto/eliminado y ACL retirada (sin filtración); B04 repetición/crash después de commit (un receipt, UNKNOWN reconciliado); B05 favorito/undo con revisión stale; B06 carpeta no vacía (REJECT sin cascada) y reubicación explícita; B07 owner indisponible → parcial/no disponible, no 0; B08 Chat→Review→auxiliary outcome, nunca Reality COMMITTED falso. Revisar backup/restore/migración del owner auxiliar y límites/cuotas/retención de tombstones, sin afirmar PASS por lectura documental.

**Bloqueo vinculante:** el usuario solo autorizó revisión acotada de ocho contratos, NO incorporación ni ejecución. Falta decisión humana específica de catálogo, ownership, retención y cuota. `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` queda BLOCKED; no emitir READY ni realizar B6. S3.A3 = IN_PROGRESS_REVIEW_ONLY; A4/A5 sin abrir, tests/CI A3 NOT_RUN. Próxima acción: aprobación contractual separada, entonces completar B5 de rutas concretas, emitir permiso, implementar con writer CAS, CI exacto XML, B7 y checkpoint final.
