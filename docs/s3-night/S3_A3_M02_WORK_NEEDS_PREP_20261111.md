# S3.A3 M02 — NEEDS_PREP: revisión global antes del Review
Fecha 2026-11-11 AR. Unidad S3_A3_BOOKMARKS_CHAT_REVIEW_DEV. Dependencia M01 B7 PASS en 649d3f08ec7d6a14dea4869ad84603408dbee953.

## Estado
NEEDS_PREP, sin código M02 y sin permiso READY. Se aplicó revisión delta de interfaces reales; no se repitió investigación certificada. M03–M05 permanecen dependientes.

## B1 evidencia exacta
BookmarkReadPage (kernel/contracts/BookmarkContracts.kt) contiene bookmarks/folders/hasMore; no contiene revisión global de bookmark_scope. FaroV5BookmarkReadHostBinding proyecta metadata sin revisión global. AuxiliaryBookmarkStore.scope() es privado, mutateLocked requiere expectedRevision de bookmark_scope; revisiones individuales no equivalen al scope. Ejemplo certificado de fixture: crear carpeta+Add deja scope2 pero item.revision1. bookmarkPortability devuelve watermark/folders/records, no revisión global. M01 mutator exige solicitud completa e inmutable, por diseño.

El resultado autorizado BookmarkMutationResult.Stale(currentRevision) existe DESPUÉS de intentar una mutación; no es una consulta de preparación. Usarlo para bootstrap exige definir qué ocurre si expectedRevision0 ya coincide y se aplica el efecto, cómo se presenta nueva revisión y cuándo se renueva consentimiento. El B4/B5 M02 preparado no selecciona ni prueba este flujo. No se lo incorpora improvisadamente. backup.snapshot expone estado de mantenimiento sin las fences del host y tampoco es un sustituto autorizado para Review.

## Disparador STOP aplicable
La ficha S3_A3_M02_CHAT_REVIEW_B1_B5_20261111.md exige en B4 revalidar scope revision y en B5: STOP si no existe una API autorizada para revision/receipt; retornar DELTA_B5 y pedir nuevo permiso. Las 11 rutas M02 propuestas no incluyen AuxiliaryBookmarkStore.kt, AuxiliaryDeliveryStore.kt ni M01 read/mutation binding. Un accessor de revisión autorizado requiere ampliar esas rutas mediante preauditoría específica; no se hace como parte de M02 sin permiso. No hay permiso global implícito por M01.

## Preparación mínima para reanudar
Elegir y preauditar un snapshot de revisión de Auxiliary para Review, de solo lectura, acotado a scope/target+grant, generación, token y mutex; distinguir scope ausente (revision0) de unavailable; revalidación pre/post Workspace y host epoch. Nunca inferir revisión desde item/folder/max ni exponer backup/receipts completos. Añadir negativas de token/generación ajena, revoke/ABA/cierre y concurrencia; luego permiso documental exacto para rutas necesarias en commit separado y preflight. Alternativa mutation->Stale->nuevo Review solo si se selecciona y prueba expresamente, sin retry automático ni efecto previo a confirmación.

No se agregaron contratos oficiales, owners, tablas, operaciones ni permisos. Primero preparar este delta y emitir permiso individual; luego M02 B6/CI/B7 y retomar cola. Writer se libera en checkpoint M01+NEEDS_PREP. No pruebas G15.
