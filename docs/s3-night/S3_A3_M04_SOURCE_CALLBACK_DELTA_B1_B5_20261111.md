# FARO V5 — S3.A3 M04 · Navegación a fuente exacta desde Guardados DEV · B1–B5 DELTA

Fecha: 2026-11-11 AR. Unidad `S3_A3_BOOKMARKS_EXACT_SOURCE_NAV_DEV`; evidencia HEAD exacto `77faa90a00a39812cb71ec308ee5969a0e6a288c`. Complementa, sin modificar ni reabrir, `docs/s3-night/S3_A3_M04_EXACT_SOURCE_NAV_B1_B5_20261111.md` (blob `97be894856ea585d46869881c4c46557eb2631a6`) y resuelve STOP `docs/s3-night/S3_A3_M04_WORK_NEEDS_PREP_20261111.md` (blob `96099312c9a3c8755fe7a6f42b1d8a4dc6d14aac`). M01, M02, M03 B7 PASS; M04 B6/B7 NOT_RUN. No alterar main, V4.4, S4 ni V2 productiva; física G15 solo al final.

## B1 — Evidencia SHA y brecha de alcance

- M03 B7 `docs/s3-night/S3_A3_M03_WORK_B7_PASS_20261111.md` blob `87de1ead6c47dedbbbaf5f477bfb269fc63a0271`, código `a12b0ee182ad815f56c569c02aa9b6199e7cce86`, 53/53 XML+APK run `43036909801`, guard `43036909777`; el checkpoint final de M03 es `77faa90a...` y writer RELEASED epoch711.

- Controles nativos `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevBookmarkFolderControls.kt` blob `08d35139ed8c1ffc7d751785b3e0bb0ca6e543c1`: `loadActive(cursor)` entrega filas solo ACTIVE, `rows` privado, botones Favorite/Remove/Assign y `reader.isPageCurrent(page)`, ticket `sequence`, `closed`, `foreground`; `loadRemoved` separado. No hay callback `Abrir fuente`. Al click de item en una fila, solo la propia clase posee el item Y su página/lease; la Activity no debe recuperar el índice visual.

- Activity DEV `FaroV5V2CoreDevActivity.kt` blob `acc05ba5e7cf73b590657f06a9c816511143699f`: `openBookmarksView(active)` usa `setContentView(ScrollView)` con `bookmarkFolderControls`; el Chat se instala por `FaroV5SurfaceHost.install(...)` Compose en una ruta separada. `onPause` revoca grants y limpiar controles. Cambiar View→Compose con `setContent` NO implica automáticamente `onPause`; revocar explícitamente controles/read authority al abandonar página.

- Session `FaroV5V2CoreDevAuthority.kt` blob `8d1b0af40ce3e0fddc05ea117966adbef851b964` tiene `selectionPort`, `timelineReadPort`, `workspaceReadPort`, `graph.workspace`, `graph.auxiliary`, pointer pinned, `openBookmarksAfterTrustedForegroundRequest`, chat actions, revokes; **no** callback host para source navigation. No abrir segundo owner ni Session temporal.

- Lectura M03 `FaroV5BookmarkReadHostBinding.kt` blob `b90209394de60126d3d0bf46390afea13ed6d147`: `readAuthorized` devuelve `FaroV5BookmarkHostPage` con bookmarkId/conversationId/messageId; `isPageCurrent` exige mismo objeto leased y doble read grant; `readRemoved...` es camino privado separado, JAMÁS navegable. Fuente actual se puede releer por owner M02: `AuxiliaryDeliveryStore.bookmarkReviewSnapshotForTrustedHost(gate,target)` retorna `scopeRevision,activeId,favorite` para ACTIVE exacto, `activeId` debe ser igual a clicked id; está bajo `ownerLock` y revalida Workspace target. No agregar API nueva SQL solo para M04.

- Surface `FaroV5SurfaceHost.kt` blob `b5bc74b7a1ef5d57d4b189313f3bb3bf4164e6f9`, `FaroV5AdaptiveShell.kt` blob `83d4a625ce5aa4d2964a9de7c6d3f5a2cebccc39`, `FaroV5ShellNavigation.kt` blob `c59783b7f7766c99a765b50cffabf8ccd74d8f12`: no reciben solicitud inicial tipada desde native. Shell usa `searchConfirmedTarget` para Buscar, no se debe reutilizar para Guardados. `FaroV5ActiveChatScreen` ya acepta `focusMessageId`; timeline ya verifica ID presente. No modificar ChatTimeline sin evidencia delta.

- Contratos existentes: `ConversationWorkspaceSelectionPort.select(id, key)` devuelve `Saved(snapshot,replayed)|Stale|Unavailable/...`; `ConversationTimelineReadPort.readTimeline(conversationId)` devuelve snapshot de mensajes con conversationId y authorityToken. Deben verificarse contra el target exacto después de selección; selección `Saved` por sí sola no demuestra que el mensaje siga visible.

- Original M04 propuso 8 paths; Work detectó callback faltante en nuevo `FaroV5V2CoreDevBookmarkFolderControls.kt` y STOP. Este delta añade **3 paths exactos**: controles, `FaroV5AdaptiveShell.kt` (transferencia tipada de foco explícita sin CompositionLocal global) y `FaroV5BookmarkFoldersDevTest.kt` (regresión DEV callback), TOTAL **11 rutas**. No modificar Aux/Workspace/kernel/AndroidManifest fuera de scope.

## B2 — Reutilización y comparación V4.4

- Referencias V4.4 de `S3_P00_CONTRACT_REVIEW_20261102.md` y preaudit M04: pin/archivo/SavedView se orientaban a conversación y no garantizaban mensaje exacto ni permisos actuales. **RECHAZAR** copy/paste de navegación V4.4.

- Reutilizar M03 ACTIVE page+lease, M02 reviewSnapshot exacta, M01/M02 Workspace ACL/source+generation fence, Search→Chat A2 únicamente como patrón de refresco canónico, y foco ya presente en ActiveChat. No reimplementar M01–M03, storage, CAS/receipts, catálogo180, schema, parser ni timeline.

## B3 — Investigación externa y elección

- Android arquitectura oficial `https://developer.android.com/topic/architecture/recommendations`: single source of truth, UI events hacia owner y no lectura SQL en View/Compose. **ADAPTAR** Session host gate + mínima entrega a Shell.

- Android Navigation data `https://developer.android.com/guide/navigation/use-graph/pass-data`: pasar sólo IDs mínimos, recuperar desde source of truth; **ADAPTAR principio**, NO usar una API Navigation2 en Navigation3 ni publicar deeplink/token. `https://developer.android.com/develop/ui/compose/state`: estado efímero hoisted desde host hacia Compose. **ADAPTAR** parámetro tipado, no guardarlo en `rememberSaveable`.

- GitHub workflows `https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax`: nuevo workflow dirigido debe activar con `on:push` en `work/s3-night-20261102`, incluyendo controles/host/surface/tests; `workflow_dispatch` no funciona si workflow falta en default; **USAR push**, main intacta.

- Alternativas: (A) inspeccionar botón/View índice para extraer target — **DESCARTAR**; (B) abrir cached Search target — **DESCARTAR**; (C) mover a Compose todo Guardados — **DESCARTAR** por alcance M04, (D) abrir `REMOVED` directo — **DESCARTAR**; (E) solicitud tipada + revalidación host + selección Workspace + relectura timeline + foco effímero — **ELEGIDA**; no nueva biblioteca ni APIs pagas.

## B4 — Implementación exacta propuesta

1. Controles DEV `FaroV5V2CoreDevBookmarkFolderControls.kt`: añadir parámetro callback `onOpenSource(page:FaroV5BookmarkHostPage,item:FaroV5BookmarkHostItem)` (o interfaz tipada narrow). Botón **`Abrir fuente` sólo en `loadActive`**, nunca `loadRemoved`; al click comprobar foreground, `sequence` ticket igual, objeto de página leased con `reader.isPageCurrent(page)`, pertenencia item∈page, dialog/acción no pendiente; bloquear doble toque e invalidar filas. No pasar `BookmarkTarget` con authorityToken ni texto de mensaje.

2. Host-only `FaroV5BookmarkSourceNavigationBinding.kt` (nuevo): una invocación por evento explícito, pinned pointer completo, host epoch, sandbox DEV/foreground, grants metadata+workspace, `reader.isPageCurrent(page)`; reconstruir Workspace `BookmarkTarget` desde item conversacion+messageId y authorityToken obtenido de Workspace, NO desde UI. Revalidar AUX ACTIVE exacta con `bookmarkReviewSnapshotForTrustedHost(gate,target)`, exigir `activeId==item.id` y scope admitido, ACL/source available; comparar antes/después con Workspace y pointer, no confiar en snapshot/UI vieja. Recibir sólo `FaroV5BookmarkHostItem` de la página leased. No nueva puerta SQL.

3. Selección: usar la **misma** `Session.selectionPort` de Workspace con UUID/idempotency key exclusiva; si ya está activa, igualmente releer. Aceptar solo selección canónica `Saved` o estado equivalente sin error; frente a `Stale`, `Unavailable`, IdentityConflict o excepción: fail-closed (`No disponible` / `Resultado pendiente de verificar`), SIN retry. Releer `workspaceReadPort.readSnapshot()` después de la selección y `timelineReadPort.readTimeline(conversationId)`; verificar activeConversationId exacto, messageId presente en timeline de **esa** conversación, authorityToken/revisión actuales y Workspace ACL+generation+epoch sin modificación conflictiva. La selección puede incrementar la revisión Workspace: usar nuevas fences post-selección, no exigir igualdad con el snapshot previo a mutación de selección.

4. Activity DEV: ejecutar callback y owner IO fuera de UI thread, ticket propio de navegación + comparar `session === active`, foreground/pointer y controles aún válidos; sólo si host devuelve `VerifiedOpenTarget(conversationId,messageId)` continuar. Antes de `FaroV5SurfaceHost.install` hacer close/redact de `bookmarkFolderControls` y revocar los read grants nativos; no cerrar Session/graph ni recrear Activity. Si la transición falla, no abrir Chat previo silenciosamente; UI `No disponible`, nueva consulta explícita. `setContent`/Native→Compose no implica onPause.

5. SurfaceHost/AdaptiveShell/ShellNavigation: pasar **argumento tipado EFÍMERO** de IDs mínimos (`conversationId, messageId`) por las tres capas permitidas, sin token/texto/BookmarkId/receipt/public Intent ni saver. La Shell refresca Workspace canónico y verifica activeConversationId y timeline de esa conversación antes de publicar foco. `searchConfirmedTarget` de Buscar permanece independiente. Enfocar `focusMessageId` existente de ActiveChat sólo si snapshot fresco contiene target; al salir/cambiar conversación/read port/source host revocado, limpiar el foco (nunca fallback). No duplicar selector Workspace ni UI en M04.

6. Workflow/test: `FaroV5BookmarkSourceNavigationBindingTest.kt` prueba owner real + ACL/read/mutation independiente y exact ID; `FaroV5BookmarkSourceNavTest.kt` prueba foco efímero, error fail-closed y orden de selección; ampliar `FaroV5BookmarkFoldersDevTest.kt` para que sólo ACTIVE emita callback y no al construir/`onPause`/REMOVED. Compilar Android DEV, verificar JUnit XML real 0 skipped/fail/error, APK y guardía exact SHA. No prometer prueba física.

**STOP explícito:** si app requiere cambios en `FaroV5BookmarkReadHostBinding.kt`, `FaroV5ActiveChatScreen.kt` o kernel/Workspace/auxiliary, NO tomarlos implícitamente: hacer delta B1–B5 y otro permiso, o rediseñar con los puertos actuales. Sin cambiar API pública, Activity productiva ni main.

## B5 — Auditoría adversarial previa al código

- M04-N01 click ACTIVE en página leased: verifica bookmarkId+conversationId+messageId exactos; mismo `messageId` de otra conversación no acepta. Click REMOVED, page vencida, item fuera de página o `rows` reemplazados ⇒ ninguna navegación, ninguna lectura de texto.

- N02 source archivado/revocado/oculto o bookmark quitado justo después de mostrar fila ⇒ no navegación, `No disponible`, sin caché ni leaked ID. Si consentimiento original ya se usó, no renace al cambiar Activity.

- N03 `select()` Saved pero relectura Workspace cambió/no active target o `timeline` null/falta mensaje/authority token distinto ⇒ fail-closed sin enfocar chat viejo; nunca asumir selección por saved reply.

- N04 owner ACL cambia por generación ABA, pointer full sequence cambia, read grants revocados, hostEpoch/sandbox o pause entre consulta/callback ⇒ no callback tardío UI, no publicación de foco ni de texto cacheado.

- N05 back/rotation/recompose/click doble o segundo destino inmediato ⇒ ticket invalida primera acción, estado de foco no se persiste (no `rememberSaveable`), no second select por retry y pantalla vuelve a estado seguro.

- N06 si native View→Compose ejecuta `setContent` sin onPause, demostrar que controles/lectores se cierran **explícitamente** y Session se conserva; un nuevo menú/Chat no hereda autorización de recovery ni mutación.

- N07 `searchConfirmedTarget` no se reutiliza, y foco sólo sobre `ConversationTimelineSnapshot` con `conversationId` + mensaje exacto y revisión canónica; no copiar cuerpo ni insertar deep link exportado.

- N08 control de scope 11 rutas, `FARO-UNIT:<unit>` y preflight; tests JVM dirigidos, XML preciso, APK `assembleV2CoreDev`, protocolo seven-block-evidence `SUCCESS` exact SHA y B7 independiente. G15 reservado final. M01–M03 B7 no se repiten como work nuevo.

**Dictamen**: el bloqueo `M04_SOURCE_CALLBACK_CONTROLS_PATH_NOT_PREAUDITED` queda resuelto en B1–B5 mediante tres rutas precisas más las ocho originales. M04 puede recibir un nuevo permiso READY documental en commit separado tras publicar este delta; B6/B7 NOT_RUN. M05 espera B7 de M04.
