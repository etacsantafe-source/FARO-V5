# S3.A3 — RED_FIX CAS/receipts Kotlin + FK: B1–B5
Fecha: 2026-11-09. Unidad de corrección `S3_A3_BOOKMARKS_AUXILIARY_CAS_RECEIPTS` (misma unidad fallida). SHA observado `32382b9c54f4b73795bd727c4e69c580a4ef2d5b`.

## B1 — Evidencia exacta
B7 RED_FIX `docs/s3-night/S3_A3_BOOKMARKS_AUX_CAS_RECEIPTS_B7_RED_FIX_20261109.md`: 42728500403 Kotlin compile SMARTCAST_IMPOSSIBLE, `request.cursor` cross-module. Un segundo defecto confirmado por lectura: `bookmark_scope` se crea luego de INSERT de bookmark_folder/bookmark_item, incumpliendo las FK de schema v3. No afirmar 8/8.
Mantener certificados previos: contratos 8/8 en 42720699353, esquema v3 5/5+16/16 en 42726963284. Writer global CAS único; V2 productiva OFF.

## B2 — Reutilización V4.4 / actual
V4.4 no ofrecía owner de mensaje guardado: comparación ya asentada en `S3_A3_BOOKMARKS_B1_B5_PREAUDIT_20261105.md`. Sin duplicar módulos ni tocar V4.4. Reusar `AuxiliaryBookmarkStore.kt` y `AuxiliaryDeliveryStore.kt` con misma base, transacción y gate; no cambiar contratos ni esquema ni catálogo.

## B3 — Investigación de alternativas
Kotlin Smart Casts https://kotlinlang.org/docs/typecasts.html: propiedades públicas de otro módulo no admiten smartcast estable, guardar `val cursor=request.cursor` local inmutable o `?.let`; preferir `?.let` para bind seguro. SQLite Foreign Keys https://www.sqlite.org/foreignkeys.html: una fila hija no puede persistir sin padre, aunque la transacción sea `BEGIN IMMEDIATE`; crear padre antes de INSERT hijo en la misma transacción, revertir padre al abortar. SQLite https://www.sqlite.org/lang_transaction.html: `ROLLBACK` protege atomicidad frente a fallos de INSERT, no reemplaza el orden de constraints. Alternativa descartada: deshabilitar FKs, ignorar error o `ON DELETE CASCADE`, por pérdida de integridad. Android WAL mantiene FULL.

## B4 — Corrección B6 y pruebas
Scope limitado a `AuxiliaryBookmarkStore.kt`, `AuxiliaryBookmarkStoreTest.kt` (pruebas nuevas/afirmaciones en suite) y opcional workflow dirigido existente si fuera necesario. Sustituir `request.cursor` cross-module smart cast por copia local. Insertar `bookmark_scope` inicial después de validar expectedRevision/fingerprint pero ANTES de `applyMutation`, de forma que `ROLLBACK` revierta tanto scope como cambios y receipt; revalidar authority pre/post. Agregar caso que garantice que inserción inicial con FK habilitadas da `Saved` y `foreign_key_check` limpio (ya hay pruebas alta/folder). Una prueba de fallo en receipt debe dejar scope/item en cero y permitir reintento. Mantener ocho pruebas dirigidas; revisar error terminal siguiente sin fabricar PASS.

## B5 — Adversarial/STOP
Si un `Add` o `FolderCreate` no puede crear scope por FK, STOP; nunca `PRAGMA foreign_keys=OFF`. Repetición de idempotency key misma solicitud -> receipt; distinta solicitud -> IdentityConflict; CAS stale no muta. Fallos de autoridad -> Unavailable; no datos del mensaje ni token crudo en DB.
B6 nueva compilación y XML 8/8 sin skipped, más guardia y regresiones existentes. Si falla, B7 RED_FIX misma unidad. No registrar catálogo/activar UI/real production antes de respaldo y revalidación Workspace.
