# S3.A3 — Auxiliary Guardados CAS y recibos: B1–B5
Fecha AR: 2026-11-09. Unidad `S3_A3_BOOKMARKS_AUXILIARY_CAS_RECEIPTS`. SHA base auditado `1b9fa3e8f51d8705678716cee19ce213b3bca29b`. Permiso condicionado al alcance de pruebas y API no montada; esta subunidad NO declara disponibilidad real de Guardados.

## B1 — Evidencia previa sin repetición
- Contratos tipados 8/8 B7 `S3_A3_BOOKMARKS_CONTRACTS_B7_PASS_20261109.md`; esquema v3 y migraciones 5/5 +16 regresión certificados `S3_A3_BOOKMARKS_AUX_SCHEMA_V3_B7_PASS_20261109.md`. No editarlos ni repetir sus tests como objetivo nuevo. Fuente exacta `BookmarkContracts.kt`: `BookmarkReadPort`, `BookmarkMutationPort`, `BookmarkTarget`, `BookmarkReadResult.Unavailable`, `BookmarkMutationRequest.expectedRevision`, `IdempotencyKey`.
- La DB Auxiliar N-018 incluye `bookmark_scope`, `bookmark_folder`, `bookmark_item`, `bookmark_receipt`, pero ninguna llamada las usa; UI sigue no disponible, catálogo 172, backups actuales sólo Avisos. Prioridad real: implementar transacciones durables y negación por defecto.

## B2 — Reutilización V4.4 y V5
- Ya contrastados V4.4 pin-chat y saved views en `S3_A3_BOOKMARKS_B1_B5_PREAUDIT_20261105.md`: ninguna representa bookmark a mensaje individual. No recrear almacenamiento de texto ni nuevo Truth store.
- Reutilizar conexión SQLite v3, `lock`, `MaterialWriteGate`, WAL/FULL, `BEGIN IMMEDIATE` y reversión existentes en `AuxiliaryDeliveryStore.kt`. No usar `delivery_notice` ni `delivery_intent` para Guardados; no inventar receipt Reality. Usar un adapter interno para requests de contratos y sin side effects de SO.

## B3 — Alternativas externas y decisión
- SQLite transaction https://www.sqlite.org/lang_transaction.html: `BEGIN IMMEDIATE` + mutación + `bookmark_receipt` + `bookmark_scope.revision` en la MISMA transacción, `ROLLBACK` completo ante excepción. Rechazo a receipt previo o a segunda DB no atómica.
- SQLite UPSERT https://www.sqlite.org/lang_upsert.html y UNIQUE: idempotency key única en `bookmark_receipt`, pero no sobrescribir ante fingerprint distinto; replay exacto retorna revisión original. Mejor que `INSERT OR REPLACE` destructivo.
- SQLite FK https://www.sqlite.org/foreignkeys.html: `ON DELETE RESTRICT` para carpeta no vacía; rechazo a cascada accidental. Android WAL https://developer.android.com/topic/performance/sqlite-performance-best-practices: conservar FULL según FARO aunque default Android pueda ser NORMAL.
- Slack Saved for Later/Obsidian Bookmarks son referencias UX ya examinadas (fuentes en preaud anterior), no prueba de ACL ni autorización para cloud, Saved Views o copias de mensaje.

## B4 — Plan acotado B6 y puertos
- Nuevo `AuxiliaryBookmarkStore.kt`: adapter a `BookmarkReadPort` y `BookmarkMutationPort` sobre la MISMA SQLiteConnection y lock del owner; exposición aislada `AuxiliaryDeliveryStore.bookmarks`. Constructor permite inyectar un `BookmarkAuthorityGate` con `isScopeActive(generationId, authorityToken)` y `isTargetAvailable(target)`; **por defecto null -> Unavailable en lecturas y mutaciones**. Test fakes no sustituyen ACL real.
- Scope singleton generation+hash de authority token, nunca token crudo en DB. Check scope antes y después de cada mutación; check target original en Add y cada elemento de Read; si cambió durante transacción, abortar; otro generation/token = IdentityConflict o Unavailable, nunca exponer metadata ajena.
- CAS global `expectedRevision` -> `Stale(currentRevision)`; replay idéntico por idempotency key tras commit -> `Saved(revision, replayed=true)`; colisión fingerprint -> `IdentityConflict`. Mutación + scope revision + receipt commit atómico. Limitar 32 carpetas y 1024 metadata (incluye tombstones), lectura <=50 y folder delete no cascada. Separar favorito de pin y tombstone de borrado de mensaje. Serializar fingerprint con campos acotados y hash SHA-256; proteger token.
- Pruebas JVM nuevas: default unavailable, create/reopen, replay idempotente, conflict key, CAS stale, collision scope, folder RESTRICT, rollback, target revocation. CI dirigida por SHA con guardia y XML; no Android físico en este corte. Backup/restore v3, integración real N-010, catálogo y UI son unidades SIGUIENTES y quedan expresamente bloqueados.

## B5 — Auditoría adversarial y condiciones de STOP
- Si `BookmarkAuthorityGate` es null, falla, cambia de generación, revoca ACL o no verifica target, retornar `Unavailable` y abortar antes de commit. Nunca permitir test fake como autoridad de shipping. No se garantiza atomicidad distribuida N-010/N-018; futura integración debe revalidar antes de mostrar mensaje con owner/generation fence.
- Timeout, fallo SQLite entre metadata y recibo: transacción se revierte íntegra. Si se confirmó y el caller perdió respuesta, reintento con misma key/fingerprint retorna receipt. Key repetida con otro payload siempre falla.
- DB schema v3 tiene `UNIQUE(scope, generation, conversation, message)`: prohibir duplicados y colisión de mensajes entre chats; no borrar message; carpeta con items -> rechazo, tombstone conservado; índice para paginación.
- STOP para activar operaciones/UI mientras no haya backup/restore/portabilidad y autoridad real; un `Available` vacío sin ACL es error. No modificar catálogo 172 ni V2 productiva, S4, Auto, Noche. Cualquier rojo CI => B7 RED_FIX y mismo unit id, no abrir otra unidad.
