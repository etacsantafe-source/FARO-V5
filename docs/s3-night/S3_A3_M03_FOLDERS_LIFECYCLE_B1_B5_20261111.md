# S3.A3 M03 — Carpetas y ciclo de vida de referencias — B1–B5

Unidad S3_A3_BOOKMARKS_FOLDERS_LIFECYCLE_DEV. Depende de M01 y M02 B7 PASS.

Fecha 2026-11-11 AR. Base auditada 00bd1f5dd1833bcea136cc4a50219ede08000fd0.

Estado PREAUDITED_DEPENDENCY; NO READY ni permiso emitido. Solo M01 cuenta con permiso READY. Esta preparación no ejecuta B6 ni B7.

Fuentes externas verificadas: Android Architecture https://developer.android.com/topic/architecture/recommendations (ADAPTAR single-source-of-truth y UDF), Slack Saved https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later (REFERENCIA experiencia, no cloud), SQLite BEGIN IMMEDIATE https://www.sqlite.org/lang_transaction.html (USAR transacción existente). Sin dependencias pagas.

Política transversal: un writer CAS, HEAD y permit frescos, staged ref solo rama work/s3-night-20261102; V2 shipping/S4/Auto/Noche OFF; main/V4.4 no tocar; tests físicos G15 reservados a campaña única final.

## B1 Evidencia y gap

BookmarkContracts.kt conserva FolderCreate/Rename/Delete/Assign y LifecycleSet/FavoriteSet; cuotas 32 carpetas, 1024 items y 50 por página. AuxiliaryBookmarkStore.kt blob 188051ff885b9a84db051a97dbe1745a13b97f7c implementa CAS, receipts y no cascada de FolderDelete cuando carpeta no vacía. FaroV5V2CoreDevBookmarksPresentation.kt blob 77a97deedacd5dfee702e5323ec7f91090703b96 es metadata-only de lectura, no tiene controles. FaroV5BookmarkReadHostBinding.kt blob c105075727d5a7ad4cd333e4e5b4f1e9687d6e66 es read-only y protege page+epoch.

## B2 Históricos y reutilización

Paridad V4.4 ya auditada en P00: pin/archivo Saved Views no sustituyen colección bookmark. Reutilizar Auxiliary CAS, backup/restore ya B7, M01 adapter, M02 review/gesture, no crear un sexto owner, ni tabla nueva, ni Undo Reality.

## B3 Opciones

Android UDF: ADAPTAR estado UI desde snapshot autorizado; Slack Saved: REFERENCIA listas/organización, no sincronización; SQLite: USAR receipts transaccionales. DESCARTAR cascada de carpeta, copiar texto fuente, crear pseudo Undo Reality, UI optimista como prueba durabilidad.

## B4 Propuesta

1. Nueva pantalla DEV FaroV5V2CoreDevBookmarkFolderControls.kt con contenido paginado y auth metadata; dueño de controles solo UI. 2. Crear/renombrar/eliminar carpeta vacía; asignar a carpeta o raíz; quitar/restaurar/favorito con confirmación. 3. Integración v2CoreDev/FaroV5V2CoreDevActivity.kt y v2CoreDev/FaroV5V2CoreDevAuthority.kt; tocar FaroV5V2CoreDevBookmarksPresentation.kt solo si necesario, sin modificar el reader ya certificado. 4. M01 mutator grant separado del lector; releer tras receipt; cuotas/validación en owner, no Compose. 5. B7/JVM+Android APK dirigido.

## B5 Negativos y STOP

Carpeta no vacía rechaza delete; nombre inválido, cuota excedida, asignar carpeta inexistente, CAS obsoleto, clave repetida con payload distinto, revoke onPause, target eliminado, puntero ABA, dos ventanas. CRÍTICO: lector ACTIVE hoy puede ocultar REMOVED; no exponer tombstones ni inventar restauración. Si host no ofrece lookup autorizado por ID de bookmark REMOVED, STOP para delta preauditoría de lectura específica; no ampliar permiso de M03 sin aprobación. Sin CI ejecutada ni móvil físico.
