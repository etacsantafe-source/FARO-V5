# FARO V5 — S3.A3 M03 · recuperación REMOVED segura y carpetas · B1–B5 DELTA

Fecha: 2026-11-11 AR. Unidad `S3_A3_BOOKMARKS_FOLDERS_LIFECYCLE_DEV`, rama `work/s3-night-20261102`. Evidencia inspeccionada en HEAD `3aa1ad299112015a07b544fb15bb2d926f300af8`. Complementa sin alterar el documento B1–B5 original `docs/s3-night/S3_A3_M03_FOLDERS_LIFECYCLE_B1_B5_20261111.md` (blob `729e56f1a56c42c2801c5dcd3bbaba86e154754d`) y responde al STOP `docs/s3-night/S3_A3_M03_WORK_NEEDS_PREP_20261111.md` (blob `0234c34549cd556b3f070bd59e3181d2f69d3149`). **Preparación y diseño documental; M03 B6/B7 NOT_RUN.** M01 y M02 B7 PASS no se repiten.

## B1 — Inspección real y defecto exacto

- M01: `FaroV5BookmarkMutationHostBinding.kt`, `FaroV5LocalInstallBookmarkMutationAuthority.kt` y `AuxiliaryBookmarkStore.requestAllowed` protegen `BookmarkMutation.LifecycleSet(id, ACTIVE)` con consent grant, source Workspace, generación, ownerLock, recibo/CAS e idempotencia. **No hace falta reimplementar M01 ni cambiar los tipos BookmarkContracts**. Código M01 `649d3f08ec7d6a14dea4869ad84603408dbee953` / B7 `S3_A3_M01_WORK_B7_PASS_20261111.md`, 64/64 run43016377617.

- M02: `AuxiliaryBookmarkStore.kt` blob `62768e87e4f8d44eb6bb9200f19c54b515bfe105`: `read(BookmarkReadRequest)` filtra `lifecycle='ACTIVE'`; `reviewSnapshot(BookmarkTarget)` filtra `ACTIVE` por target y devuelve `BookmarkReviewSnapshot(scopeRevision,activeId,favorite)`, no visibiliza `REMOVED`. `persistedTarget(id,token)` privado solo para mutación; no ofrece lectura host-only por ID. `AuxiliaryDeliveryStore.kt` blob `04a309679e1cdbaa4ce75c8a40cb43b3182273ff` exporta `bookmarkReaderForTrustedHost` y `bookmarkReviewSnapshotForTrustedHost`, compartiendo la misma conexión/lock. M02 código `c04a19971f8d610f221c3739961f4e8b3a0c2b22` / B7 12/12 run43029315832.

- `FaroV5BookmarkReadHostBinding.kt` blob `c105075727d5a7ad4cd333e4e5b4f1e9687d6e66` emite metadata `FaroV5BookmarkHostPage` de ACTIVE con doble grant `BOOKMARK_METADATA+WORKSPACE_MESSAGE`, pointer+epoch, `isPageCurrent` y `close`. Es read-only, no write. `FaroV5V2CoreDevAuthority.kt` blob `dc8066746697a421b4ad57cf8847c4c7e6f8a5c0` expone `openBookmarksAfterTrustedForegroundRequest`, `revokeBookmarkReadingOnPause`, `bookmarkChatActions`, sin UI de recuperación. `FaroV5V2CoreDevActivity.kt` tiene pantalla Guardados nativa separada de Chat Compose.

- **Segundo hueco descubierto**: un mero lookup por bookmarkId restaura solo si el usuario conserva ese ID. Tras cerrar/reabrir la app, la lista ACTIVE no muestra REMOVED. Se requieren DOS operaciones técnicas acotadas: lookup por ID exacto y **vista de recuperación opt-in** paginada para descubrir IDs restaurables. La pantalla de Guardados NORMAL jamás debe mostrar REMOVED. Ni backup ni portabilidad son accesos autorizados para UI.

- `BookmarkPolicy.MAX_READ_PAGE=50`, `MAX_BOOKMARKS=1024`, `MAX_FOLDERS=32`; `bookmark_scope.revision` global no equivale a revision de item. La recuperación requiere scope revision canónica para CAS. `BookmarkMutationResult.Stale` es resultado posterior, no bootstrap. Reusar la estrategia read-only de M02 bajo ownerLock.

## B2 — Reutilización V4.4 y separación de propietarios

- Reutilizar comparación READ_ONLY de V4.4 en `S3_P00_CONTRACT_REVIEW_20261102.md` y B1–B5 originales M03: pin de chat/archivo/Saved Views no sustituyen el histórico local de referencia individual ni su restore.

- Mantener N-018 Auxiliary como única fuente de metadata/tombstone/scopeRevision, N-010 Workspace como única fuente de disponibilidad real del mensaje y ACL, y host DEV como autoridad técnica efímera. NO crear SQLite separada, índice de texto, nuevo owner, cambio de schema, nuevo OperationId, Reality COMMITTED/Undo, auto-sync ni actualización automática de datos en background.

- Reutilizar M01 mutation-only trusted host para `LifecycleSet(ACTIVE|REMOVED)` y FolderCreate/Rename/Delete/Assign/FavoriteSet; reutilizar M02 preview/consent/`Stale` sin invocación de mutación antes del gesto de confirmar. No volver a correr M01 64/64 o M02 12/12 salvo regresión material de archivos que efectivamente se toquen.

## B3 — Alternativas externas, costos y decisión

- Android Data Layer y fuente única de verdad: https://developer.android.com/topic/architecture/data-layer y https://developer.android.com/topic/architecture/data-layer/offline-first — **ADAPTAR** estado de recuperación desde Auxiliary con UI que envía intención al owner. Ni nueva biblioteca ni API paga.

- SQLite `https://www.sqlite.org/lang_transaction.html` y `https://www.sqlite.org/isolation.html` — **USAR** la conexión/ownerLock actual y CAS global; un snapshot local no certifica atomicidad entre Auxiliary y Workspace. `READ_ONLY` lookup no inicia transaction de escritura ni crea scope/receipt.

- Slack Saved (referencia UX, NO dependencia): `https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later` — **REFERENCIA** descubrir elementos recuperables a pedido del usuario; descartar cloud/servicios y cualquier permiso derivado de listar.

- Opciones: A) lookup por ID solamente: **DESCARTAR como única UX**, porque el ID se pierde tras reabrir; B) mostrar siempre REMOVED en Guardados: **DESCARTAR** por defecto, privacidad; C) usar backup/portability: **DESCARTAR** por falta de ACL de UI y riesgo de receipts/metadata; D) mutation-probe: **DESCARTAR** porque puede escribir; E) **SELECCIONADA:** lookup exacto y listado REMOVED solo tras gesto explícito 'Ver quitados', limitado/paginado, metadata mínima y fences antes/después, y confirmar Restore con M01.

## B4 — Contrato técnico delimitado para Work

1. **Auxiliary store**: crear tipos internos mínimos (por ejemplo `BookmarkRemovedCandidate(id, scopeRevision, targetIdentity, folderId?, favorite?)` y `BookmarkRemovedPage(items<=50,hasMore,scopeRevision)`) SIN texto fuente, tokens ni receipt público. Función lookup `removedByIdForTrustedHost(id, generation, token, authority)` consulta `WHERE bookmark_id=? AND lifecycle='REMOVED'` y `scope()` bajo mismo ownerLock/SQLite; verifica scope compatible antes/después, `isTargetAvailable` Workspace para la referencia persistida, owner open y misma revision global. Scope ausente válido => `NotFound`/empty autorizado; token/ACL/host inválido o scope incompatible => `Unavailable`, nunca falsa ausencia. No exponer `persistedTarget` como API general; no filtrar por solo ID sin generación.

2. **Recuperación opt-in paginada**: `removedPageForTrustedHost(cursor,idLim=50)` solo a través de host tras click explícito 'Ver quitados'. SQL parametrizado `lifecycle='REMOVED' AND bookmark_id>cursor ORDER BY bookmark_id LIMIT 51` bajo ownerLock; no conteo global ni scanning ilimitado. Revalidar source Workspace de CADA elemento antes de publicar; si una fuente revocada aparece, **denegar la página completa** y mostrar 'No disponible' (cero leak por conteos/hasMore), sin exportar texto ni 'tombstone' de otra generación. Futuro refinamiento de saltar sources revocadas exigiría auditoría separada. No cambiar `BookmarkReadPort.read` ni `BookmarkReviewSnapshot` ACTIVE ni portabilidad; guardados normales siguen ACTIVE.

3. **Trusted read host**: extender `FaroV5BookmarkReadHostBinding.kt` con métodos de recovery separados de `readAuthorized`, que requieren dos read grants, host epoch, pointer completo, Workspace `bookmarkReadAdmission` y post-fence como M02; cache/lease de página o lookup por IDENTIDAD exacta, invalidación onPause/close/cambio de generación. Lookup y recovery page son **solo de lectura**, nunca conceden mutación. Para acceder a eliminado, exigir intención explícita foreground; listado normal no puede llamar este método.

4. **Acción Restore y carpetas**: introducir `FaroV5BookmarkFolderActionBinding.kt` (DEV narrow) que toma candidato devuelto por host con identidad exacta y revision, prepara un Review literal, genera nuevo `BookmarkMutationRequest(expectedRevision=scopeRevision, LifecycleSet(id,ACTIVE))` y pide permiso de mutación M01 SOLO después de Confirmar. M01 vuelve a resolver `persistedTarget` bajo lock. Si el target ya está ACTIVE, cambió chat, fue archivado/revocado o cambió scope, rechazo/Stale y nueva confirmación; nunca auto-retry. Para carpetas, usar operaciones existentes tipadas, grant exacto por folder/target y revisión scope actual. `FolderDelete` no puede cascada si no vacía. Renombre/nombres/cuotas validadas en owner.

5. **UI DEV**: nueva `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevBookmarkFolderControls.kt`; integrar en Session/Activity existentes, `FaroV5V2CoreDevBookmarksPresentation.kt` solo para metadata; botón separado 'Ver quitados', mostrar hasta50 y 'Siguiente' con cursor, Restore por cada item, Cancel, estados `Cargando/No disponible/Vacío/Conflicto/Stale/Resultado pendiente`. OnPause y al salir redacción de página; nuevo gesto tras reanudar. Si la UI solo posee IDs viejos, al pulsar restore debe releer ID exacto (no confiar en item cacheado). No admitir fuente texto/cache como prueba ACL.

6. **CI/B7**: tests reales Aux+Workspace y host DEV, unit/mutation gates y APK `:app:assembleV2CoreDev` en workflow dirigido `on:push` branch actual con todos los archivos permitidos, XML parser sin omitted; protocolo `seven-block-evidence` con SHA; auditoría independiente B7 y release writer. No Moto G15 físico. Paths exactos en execution package/permiso (total 13). No modificar contratos, schema, READ API normal, M01/M02 por fuera de estas rutas, ni habilitar shipping.

## B5 — Matriz adversarial y STOP binarios

- T01 ACTIVE read y `bookmarkReviewSnapshot` siguen ocultando REMOVED; export/backupPortability no revela ni recibe grant nuevo. `Ver quitados` no se llama por construir pantalla/render/recreate/onResume. Gesto read y gesto Confirm Restore son separados.

- T02 `REMOVED` tras app reopen/cierre de Session: nuevo foreground click y grants releen página, encuentra ID real y permite abrir Review, sin mapa de IDs persistido en UI. `lookup(id)` idéntico sin UI previa. La restauração confirma `LifecycleSet(id,ACTIVE)` y receipt real con revision CAS correcta; no nuevo Add ni duplicado.

- T03 otra generación, token errado, scope digest distinto, bookmarkId inexistente, wrong chat, Workspace source archived/hidden/deleted/revoked: `Unavailable`/NotFound según contrato interno sin mostrar IDs ajenos, texto, cantidad total ni recibos. Revalidar antes y DESPUÉS de SQL.

- T04 paginación 50+1, cursor estable, 51 elementos, 1024 máx, `hasMore` no cuenta elementos revocados, página incompleta bajo revocación => `Unavailable` toda la página; sin fugas por logs/error ni estructura pública.

- T05 ABA puntero, hostEpoch cambia, pause/cierre durante trabajo, UI callback tardío: invalidar lease/metadata, ni revelar candidate ni indicar éxito. Read grant jamás sirve para mutate; grant M01 solo tras confirmar.

- T06 CAS stale tras preview, conflict idempotency, replay autorizado, doble tap y mutation pre/post COMMIT incierta: `Stale` pide nueva revisión y nuevo consentimiento, `UnknownReconcile` no reenvía a ciegas, 0 Reality COMMITTED. Para `FolderDelete` no vacía rechazo FOLDER_NOT_EMPTY sin cascada.

- T07 nombres vacíos/spaces/control/>80 y cuota32/1024, asignación carpeta inexistente y root null: respetar owner. Sin schema/backup change; no operaciones nuevas ni red/APIs pagas. Mantener seguridad de `RequestScopedBookmarkAuthorityGate` de M01.

- T08 Work debe limitar diff a 13 rutas, CI XML sin fail/error/skip y APK aislado; guard protocolo success EXACT SHA, B7 independiente; demostrar hash APK. Si necesita otros archivos, STOP `STALE_NEEDS_DELTA_REAUDIT`, no ampliar permiso sobre la marcha.

**Dictamen**: M03 gap `AUTHORIZED_REMOVED_REFERENCE_LOOKUP_ABSENT` resuelto en diseño con lookup exacto + recuperación explícita paginada; B1–B5 delta listos. Preparar permiso independiente después del commit documental de esta prueba y comprobar guard SUCCESS. B6/B7 de M03 **NOT_RUN**.
