# FARO V5 — S3.A3 M02: revisión canónica previa a confirmación — auditoría delta B1–B5

Fecha 2026-11-11 AR. Unidad `S3_A3_BOOKMARKS_CHAT_REVIEW_DEV`. Baseline GitHub exacto `4a87ac39fd3483b320549a0b6a6dca676f2cd216`. Este documento COMPLEMENTA `S3_A3_M02_CHAT_REVIEW_B1_B5_20261111.md` (blob `955155399d7c32c5e7caeade33d2e4c0ae7840da`) y resuelve `S3_A3_M02_WORK_NEEDS_PREP_20261111.md` (blob `0cd69f94e4da798cc1a0747158eb00ff2a2c81ba`). Implementación y pruebas NUEVAS NOT_RUN. M01 B7 PASS conservado en SHA `649d3f08ec7d6a14dea4869ad84603408dbee953`; CI 43016377617 64/64, guard 43016377548.

## B1 — Evidencia exacta y hueco

- `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/BookmarkContracts.kt` (contratos congelados) `BookmarkReadPage(bookmarks,folders,hasMore)` no contiene `bookmark_scope.revision`; revisiones `BookmarkMetadata.revision` y `BookmarkFolder.revision` son por elemento. No modificar los contratos para remediar esto.

- `v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryBookmarkStore.kt` blob `ddd9b8fab971deeefb0cb62b8f89ecce3398f05a`: `scope()` es privado, `scope()?.revision ?: 0L` se usa en `mutateLocked` para verificar `BookmarkMutationRequest.expectedRevision`. No equivale al contador de un elemento. `read()` devuelve solo ACTIVE y omite la revisión global; solo `bookmark_scope` es fuente de verdad para CAS.

- `v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryDeliveryStore.kt`: `bookmarkReaderForTrustedHost(gate)` comparte conexión SQLite y ownerLock, preserva default mutation-denied. No existe lector de revisión para Review.

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5BookmarkMutationHostBinding.kt` blob `8b65636d594c1e48cbc36d98da6a78ffaf42db9d` acepta request completo y grant solo tras confirmación; no construye `expectedRevision`. `FaroV5LocalInstallBookmarkMutationAuthority.kt` blob `ca996f1344326b64aff1b959f64515ace420ed87` admite grant de request+target por gesto, distinto de lectura. Reutilizar, no reescribir M01.

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5BookmarkReadHostBinding.kt` blob `c105075727d5a7ad4cd333e4e5b4f1e9687d6e66` exige los dos dominios de lectura + generation + hostEpoch, pero solo devuelve page de metadata. `FaroV5V2CoreDevAuthority.kt` (source set DEV) puede admitir un click foreground explícito; Chat no recibe autorización automática de lectura por estar abierto.

- Evidencia de STOP: `S3_A3_M02_WORK_NEEDS_PREP_20261111.md`: usar `Stale` como sondeo puede aplicar `Add` con `expectedRevision=0` cuando la revisión canónica es realmente 0; un sondeo MUTANTE antes de confirmar viola consentimiento humano. `backup.snapshot` y `bookmarkPortability` no son fuente de verdad admisible para un Review visible.

## B2 — Reutilización y comparación

- V4.4: comparación READ_ONLY ya aprobada por `S3_P00_CONTRACT_REVIEW_20261102.md` y `S3_A3_BOOKMARKS_B1_B5_PREAUDIT_20261105.md`; Saved Views, pin y archivo de conversación no implementan bookmark por mensaje. No copiar almacenamiento legado.

- V5: reutilizar EXACTAMENTE SQLite/lock del Auxiliary N-018, `bookmark_scope.revision` global, `FaroV5WorkspaceBookmarkAuthorityGate` para lectura, `FaroV5BookmarkMutationHostBinding` y action-scoped grant de M01 para mutación. No sexta base, no otro owner, no migración, no cambio de OperationIds, no Reality Apply/COMMITTED.

## B3 — Investigación externa y decisión

- Android UI/Data/UDF oficial `https://developer.android.com/topic/architecture/ui-layer` y `https://developer.android.com/topic/architecture/data-layer`: **ADAPTAR** estado de revisión solo derivado del owner; UI no calcula `revision` desde lista ni almacena authorityToken. Sin bibliotecas nuevas.

- SQLite `https://www.sqlite.org/lang_transaction.html` y `https://www.sqlite.org/isolation.html`: **USAR** read bajo el mutex existente y comparación CAS en BEGIN IMMEDIATE al mutar; dos owners SQLite no son una transacción conjunta. Evitar prometer atomicidad cross-owner.

- GitHub Actions `https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax`: **USAR** `on: push` branch exacta y rutas; `workflow_dispatch` nuevo fuera de default no basta. Sin tocar main.

- Alternativa A `Stale` por mutación ficticia antes de confirmar: **RECHAZADA** (cuando 0 coincide puede escribir). B inferir scope max(item.revision): **RECHAZADA**, no son el mismo contador. C backup/portabilidad: **RECHAZADA**, carece de gates/fences de Review y filtra más datos. D añadir `scopeRevision` a contrato público: **RECHAZADA**, rompería artefactos certificados y supera alcance. Elegida E **READ-ONLY snapshot exacto de scope revision + estado mínimo de target ACTIVE**, en capa Auxiliary con gate real y revalidación Workspace; sin exponer token ni receipt y sin efectos.

## B4 — Diseño de la solución elegida

**Paso 1 — Auxiliary:** añadir SOLO un lector interno host-only (por ejemplo `bookmarkReviewSnapshotForTrustedHost`) sobre la MISMA conexión SQLite y `ownerLock`; autoridad suministrada por host read gate de Workspace. Su resultado será tipado `Available(scopeRevision:Long, existingActiveBookmarkId:BookmarkId?, favorite:Boolean?) | Unavailable` (o equivalente de scope/target mínimo, no `BookmarkReadPage` ni backup). Debe distinguir scope no inicializado pero autorizado (`revision=0`, sin escribir) de scope existente con generación/token/digest ajeno (`Unavailable`, NO `revision=0`), y validar `isScopeActive` antes y después de leer. Si examina una referencia, resolver solo target exacto `(generationId,conversationId,messageId)` y validar `isTargetAvailable`; nunca devolver texto ni REMOVED a UI. No exponer la función a Compose sin el host y sus grants.

**Paso 2 — host read/preflight:** nueva clase `FaroV5BookmarkReviewPreparationBinding.kt` (o seam interno equivalente) con pointer completo, hostEpoch, principal técnico y readAuthority separado; `prepareAfterTrustedUserAction(conversationId,messageId)` toma los hechos canónicos Workspace antes/después, usa DOBLE grant de metadata y mensaje y llama a lector Auxiliary, conservando solo revision y bookmarkId/estado mínimo necesario. Fallas/ACL revocada = `Unavailable`, no `0`. `isPreparedReviewCurrent` exige misma identidad de objeto/revision, read epoch, Workspace ACL/revision, pointer+session y foreground; nunca cachea tokens ni presenta contenido fuente eliminado.

**Paso 3 — Review humano:** Chat click Guardar/Favorito obtiene preparación read-only después del gesto, presenta acción literal, target, y estado, pero no guarda aún. Para Add generar `BookmarkId` opaco y key idempotente única por intento; Favorite/Remove usar ID revalidado por owner. Confirmar genera **grant nuevo de mutación M01 para request exacto con expectedRevision desde preflight**; aceptar sin duplicación y sin mutation en `onResume`, render, preflight o preview. La UI no decide CAS.

**Paso 4 — concurrencia:** si `bookmark_scope.revision` cambia entre preparación y confirmación, owner responde `Stale`: descartar intención autorizada previa y pedir una NUEVA revisión visible; no actualizar `expectedRevision` ni relanzar automáticamente. Si cae/revoca host después de COMMIT y no se puede verificar receipt con autoridad, UI = `Resultado pendiente de verificar`, sin reintento ciego. Un `Saved` visible exige fence final M01.

**Paso 5 — montaje:** modificar SOLO source set v2CoreDev/Activity + Authority y superficie ya propuesta M02; read grant explícito tras acción de Chat, no al abrir pantalla. Refusar shipping V2 / default grant / Internet / nuevas operaciones. `M03` queda separado para REMOVED y carpetas complejas.

**Rutas:** once originales M02 más cinco adiciones exactas: `AuxiliaryBookmarkStore.kt`, `AuxiliaryDeliveryStore.kt`, `AuxiliaryBookmarkReviewRevisionTest.kt`, `FaroV5BookmarkReviewPreparationBinding.kt`, `FaroV5BookmarkReviewPreparationT2Test.kt` (=16). Es el scope M02, no una unidad M01 retroactiva. Cualquier cambio no incluido obliga delta B1–B5 y nuevo permiso, nunca ampliar por comodidad.

## B5 — Auditoría adversarial binaria y STOP

- R01 scope ausente+ACL real: `Available(0)` sin escritura, nuevo grant separado posterior a confirmación; snapshot inicial/backup permanecen idénticos después de la preparación.

- R02 scope existente con 2 revisiones (FolderCreate + Add), item.revision=1: devuelve `scopeRevision=2`, jamás `1` ni max de items. Add con 0 solo después de confirmación humana cuando scope real=0.

- R03 generación/token equivocado, digest ajeno, puntero cambiado/ABA, sandbox ajeno, owner cerrado o revocado: `Unavailable`, nunca `Available(0)` ni metadata.

- R04 mensaje source archivado/revocado/oculto: aborta sin mostrar bookmarkId ni texto; target con mismo messageId en otro chat NO se cruza.

- R05 double-tap, recompose, background/onPause después de preflight, callback lento: grant revocado; no doble mutación ni mensaje de éxito.

- R06 otro actor modifica scope entre read y confirmación: `Stale`, nueva revisión y nueva confirmación; no reintento automático ni mutación exploratoria.

- R07 `Saved` post-COMMIT y revoke/exception: `UnknownReconcile`, nunca Reality COMMITTED ni rollback ficticio; replay exige nueva autorización exacta por M01.

- R08 tests dirigidos JVM real Auxiliary+Workspace, lectura no mutante, revisión inexistente vs scope incompatible, 2→1 inconsistencia, `Stale`, carreras, UI sin grant, APK DEV real; guard B1–B7 y XML sin skips por SHA; CI de M01 ya 64/64 NO repetir sin delta concreto; G15 físico solo al final.

**Dictamen:** brecha M02 previa RECONCILIADA POR DISEÑO B1–B5; M01 se mantiene DONE_VERIFIED; M02 puede emitirse READY con 16 rutas exactas, en commit documental posterior y preflight SUCCESS. Este documento no certifica implementación, CI ni B7 de M02.
