# M03 B6 — implementación y CI pendiente

Unidad `S3_A3_BOOKMARKS_FOLDERS_LIFECYCLE_DEV`; base `178847015179643e9b396954e96635dc91758028`.
B1–B5 originales y delta certificados reutilizados. Preflight exacto 13/13 PASS. Writer CAS epoch706.

Implementación: lookup exacto REMOVED y páginas opt-in 50+1 en conexión/ownerLock Auxiliary; revision scope canónica; gate Workspace por cada fila incluida lookahead, fences de scope antes/después. Proyecciones metadata-only sin token/body/receipt. Lectura ACTIVE/contratos/schema/portability sin cambio. Binding de lectura con doble grant, lease por identidad, pin completo y epoch; restore relee ID exacto. FolderActionBinding prepara revisión literal con CAS, grants M01 solo en Confirm, one-shot, pending unknown y Stale sin retry. Controles nativos DEV para carpetas/favorito/raíz/quitar/recuperar; cierre/pause limpia filas y diálogos.

CI dirigida on:push: 11 suites / 53 tests esperados incluyendo regresiones pertinentes de los owners/bindings modificados, parser XML exacto sin fail/error/skip, APK aislada con SHA256. Pruebas aún NOT_RUN; B7 PENDING. Gradle local no pudo descargar distribución (Network is unreachable); usar Actions sin modificar main. Campaña física G15 NOT_RUN, solo al final. M04/M05 sin permiso hasta B7 M03.
