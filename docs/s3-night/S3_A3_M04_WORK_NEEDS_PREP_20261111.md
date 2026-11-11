# M04 NEEDS_PREP — callback de fuente en controles nativos M03

## Evidencia READ_ONLY / preflight delta

Base de inspección `a12b0ee182ad815f56c569c02aa9b6199e7cce86`; M03 B7 PASS en S3_A3_M03_WORK_B7_PASS_20261111.md. M04 no tiene permiso READY. M01/M02/M03 no reabrir.

La preauditoría M04 S3_A3_M04_EXACT_SOURCE_NAV_B1_B5_20261111.md B4.4 exige cablear callback desde M03 hacia Session/Activity/SurfaceHost. La cola propone8 rutas, pero no incluye el nuevo `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevBookmarkFolderControls.kt`. Este archivo posee las filas y paginación ACTIVE/REMOVED en `rows` privado, constructor recibe Activity, reader, executor, foreground y fábrica de acciones de mutación. No expone callback de abrir fuente ni identidad+lease de página para navegación. La Activity solo monta controls.view; no posee la selección exacta de sus filas. Su implementación nueva es un delta frente al blob de lectura antiguo auditado por M04.

**STOP STALE_NEEDS_DELTA_REAUDIT**, razón `M04_SOURCE_CALLBACK_CONTROLS_PATH_NOT_PREAUDITED`. Ningún código M04/M05 escrito, ningún permiso ampliado en este turno. No inferir acceso a source desde UI, ni inspeccionar Views por posición, ni usar IDs de REMOVED como navegación o searchConfirmedTarget como bootstrap.

## Preparación mínima requerida

1. Auditar B1–B5 DELTA sobre código M03 final para callback de fuente SOLO ACTIVE, identidad del item y página vigente. Incorporar controles M03 como novena ruta del permiso M04 (documental separado, guard+preflight) si se mantiene el plan de callback desde esa UI.
2. Precisar control del callback por foreground/ticket y invalidación pause/close/generation/epoch, misma Session canónica al pasar de native setContentView a Compose SurfaceHost. No enviar texto/token ni copiar caché.
3. Reusar metadata ACTIVE y reviewSnapshot por target para releer activeId exacto y scope revision bajo ACL; documentar si ese seam satisface lookup por ID del B5 M04. Si requiere nueva API/ruta, preauditarla ANTES del permiso.
4. Mantener selección Workspace confirmada + relectura canónica timeline + conversationId/messageId exactos y foco efímero, sin fallback. Auditar callbacks tardíos, REMOVED tras click, misma messageId en otro chat, pointer ABA, fuente archivada/revocada, selección Saved con relectura fallida. M04 B6/CI/B7 después de permiso READY; luego M05 individual.

No investigación certificada rehecha. No físico G15. Esta nota señala el delta pendiente; no pretende sustituir B1–B5 ni autoaprobarlo.
