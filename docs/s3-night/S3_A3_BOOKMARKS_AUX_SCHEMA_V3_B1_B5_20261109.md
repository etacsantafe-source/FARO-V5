# FARO V5 — S3.A3 Guardados: esquema auxiliar v3, B1–B5
Fecha: 2026-11-09 AR. Unidad: `S3_A3_BOOKMARKS_AUXILIARY_SCHEMA_V3`. Fuente auditada: `1b9df754917a55c47bd775910ef6637ccd3fa785`. **Esquema/migración solamente**, NO API operacional, UI ni alta de operaciones al catálogo. El siguiente corte cubrirá CAS+receipts+backup/restore+ACL con permiso nuevo; Guardados sigue no disponible hasta entonces.

## B1 — Evidencia y no duplicación
- Reusar `S3_A3_BOOKMARKS_CONTRACTS_B7_PASS_20261109.md`: 8/8 pruebas certificadas en 42720699353, no repetir ni editar contratos congelados. Usar `BookmarkContracts.kt` como único contrato tipado.
- `AuxiliaryDeliveryStore.kt` en HEAD 1b9df754917a55c47bd775910ef6637ccd3fa785 es dueño N-018 con SQLite bundled, WAL y FULL, `user_version=2`, tabla `delivery_notice` y `delivery_intent`; B6 crea exclusivamente las tablas de metadata Guardados y actualiza `user_version=3`. `AuxiliaryBackupSnapshot` y la portabilidad aún sólo cubren avisos; **no activar bookmarks ni describir backup v3 como completo** hasta integración posterior.
- Ocho operaciones humanas aprobadas continúan CANDIDATAS, catálogo de 172 intacto; S3.A5 OPEN, V2 productiva/S4/Auto/Noche OFF; pruebas G15 sólo al cierre.

## B2 — V4.4 y reutilización
- Auditoría histórica ya realizada en `S3_A3_BOOKMARKS_B1_B5_PREAUDIT_20261105.md`: pin de conversación y saved views V4.4 no son identidad de mensaje. Sin nuevo trial de V4.4 ni duplicación de texto. N-010 es dueño de mensaje y N-018 es dueño de metadata/recibos.
- Reutilizar `initializeSchema` y `schemaTransaction` existentes, `PRAGMA user_version`, `validateRequiredSchema`, `PRAGMA foreign_key_check` de maintenance; extender v2 sin alterar columnas/semántica de avisos. Mantener OWNER único, SQLite no sexto store.

## B3 — Alternativas / investigación verificable
- SQLite `BEGIN IMMEDIATE`, `COMMIT`, `ROLLBACK`: https://www.sqlite.org/lang_transaction.html. Aceptar transacción de esquema inmediata; rechazo a migraciones parcialmente confirmadas. `SQLITE_BUSY` -> abortar, nunca continuar un estado a medias.
- SQLite `ALTER TABLE`: https://www.sqlite.org/lang_altertable.html. Preferir tablas nuevas a columnas de aviso, evitar mezclar dominios. `v1 -> v2 -> v3` en UNA transacción para preservar atomicidad.
- Android SQLite WAL/durabilidad: https://developer.android.com/topic/performance/sqlite-performance-best-practices. FARO conserva `synchronous=FULL` aunque Android recomiende `NORMAL` para throughput, por prioridad de durabilidad explícita del proyecto.
- Slack Save for Later (https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later) y Obsidian Bookmarks (https://obsidian.md/help/Plugins/Bookmarks) son ejemplos de UX, NO autoridad ni código reusable; descartar copiar texto, Saved Views, otro DB o sincronización cloud.

## B4 — Unificación / alcance exacto de B6
- Esquema v3: `bookmark_scope` una fila (generation_id + digest de token/authority + revision), `bookmark_folder` plana con ID, nombre y revision, `bookmark_item` con ID, conversación/mensaje/generación, folder FK RESTRICT, favorite, lifecycle ACTIVE/REMOVED y revision, `bookmark_receipt` con idempotency_key, request_hash, result_revision; **ninguna columna de texto del mensaje**. Constraints CHECK para estados, revisión, scope, boolean y claves; FK de folder fail-closed. Los tokens crudos nunca se guardan.
- `v0`: crear avisos v2 históricos + tablas v3 en una transacción; `v1`: agregar resolution_reason v2 + tablas v3 en una transacción; `v2`: crear tablas v3 con migración atómica; `v3`: validar tablas y checks. Versiones superiores/desconocidas STOP. Prohibido DROP de datos anteriores o reset por ser V5 limpia.
- Prueba JVM dirigida: migración desde base v2 real, arranque limpio v3, integridad/foreign keys, esquema esperado, datos de Avisos intactos, rechazo de futuro user_version. Cobertura de compatibilidad de tests preexistentes se ajustará sólo donde asumen versión 2.
- El hecho de crear tablas no habilita escritura: todas las operaciones BookmarkReadPort/BookmarkMutationPort siguen sin adapter; ninguna UI habilitada. CAS, reconciliación por recibos y autoridad por generación son gate obligatorio de la siguiente subunidad, no simulados como PASS ahora.

## B5 — Auditoría adversarial previa y STOP
- Migrar v2 con aviso existente y luego reabrir: ningún aviso ni intent perdido. Si cualquier DDL falla a mitad, v2 permanece verificable; no permitir `user_version=3` sin tablas completas.
- `folder.delete` futuro debe chocar con FK RESTRICT para carpeta con items; `bookmark_receipt` requerirá clave única y atomicidad con mutación. Evitar `ON DELETE CASCADE` para Guardados.
- Bloquear incluso después de migración cuando falten validación live de generación/ACL, garantías de idempotency/revisión, backup portable v3 y UI segura. Ningún resultado `Available` inventado; cero texto copiado.
- B6 sólo los archivos de la lista en `FARO_PROTOCOL_WORK_PERMIT.json`; nuevo commit funcional etiquetado `FARO-UNIT:S3_A3_BOOKMARKS_AUXILIARY_SCHEMA_V3`, CI JVM dirigida y guardia obligatoria; si RED, B7 RED_FIX, no abrir otra unidad. Tras CI, B7 documentará PASS_SCOPED o STOP y se liberará writer. Nunca tocar main.
