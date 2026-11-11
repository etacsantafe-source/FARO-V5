# S3.A3 M02 — Chat, revisión humana y acción Guardar/Favorito — B1–B5

Unidad S3_A3_BOOKMARKS_CHAT_REVIEW_DEV. Dependencia: M01 mutation host B7 PASS.

Fecha 2026-11-11 AR. Base auditada 00bd1f5dd1833bcea136cc4a50219ede08000fd0.

Estado PREAUDITED_DEPENDENCY; NO READY ni permiso emitido. Solo M01 cuenta con permiso READY. Esta preparación no ejecuta B6 ni B7.

Fuentes externas verificadas: Android Architecture https://developer.android.com/topic/architecture/recommendations (ADAPTAR single-source-of-truth y UDF), Slack Saved https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later (REFERENCIA experiencia, no cloud), SQLite BEGIN IMMEDIATE https://www.sqlite.org/lang_transaction.html (USAR transacción existente). Sin dependencias pagas.

Política transversal: un writer CAS, HEAD y permit frescos, staged ref solo rama work/s3-night-20261102; V2 shipping/S4/Auto/Noche OFF; main/V4.4 no tocar; tests físicos G15 reservados a campaña única final.

## B1 Evidencia y gap

FaroV5ActiveChatScreen.kt blob c920a9147a0fd2e3d1da24880599b9154dbc5d3c; FaroV5ActiveChatTimeline.kt blob a4475a61b9ea6ab8d46373961645a0cfa699781b; FaroV5MessageTimeline.kt blob d0000c7b9aff469c5db022bd68cb3d204deec3cd. Chat ya consume Workspace Timeline pero no ofrece Guardar por mensaje. FaroV5PlanReview.kt blob 3c528b23aae7c1806f1144adbcee41a5e371e6f7 es SOLO Review Reality: no reutilizar su COMMITTED. FaroV5SurfaceHost.kt blob 8d6f6403584c37940a351ddbf1610ad0729f08b1 carece de mutator de Guardados. Contratos BookmarkContracts tipados ya certificados, sin reescritura.

## B2 Comparación V4.4 y reutilización

Reutilizar evidencia histórica S3_P00_CONTRACT_REVIEW_20261102.md y S3_A3_BOOKMARKS_B1_B5_PREAUDIT_20261105.md: archivo de conversaciones, pin y Saved Views V4.4 no son bookmarks de mensajes. Conservar UI de confirmación humana como patrón, no reutilizar Apply Reality, Undo, storage ni tokens.

## B3 Opciones externas y selección

Android UDF/data layer: ADAPTAR; Slack Saved: REFERENCIA; SQLite: USAR receipts existentes. DESCARTAR botones que llaman SQLite o raw BookmarkMutationPort desde Compose, aceptación derivada de respuesta IA, auto-grant onResume, COMMITTED Reality por bookmark.

## B4 Implementación propuesta

1. Contrato de intención UI tipada por conversationId+messageId y operación; en superficie nueva FaroV5BookmarkActionSurface.kt; acción per-item en FaroV5MessageTimeline.kt y propagación ActiveChatTimeline/ActiveChatScreen. 2. Previsualizar literalmente Guardar/Quitar/Restaurar/Favorito y pedir confirmación humana separada de Apply; no autoejecutar por recomposición. 3. Inyectar adaptador narrow por FaroV5SurfaceHost.kt y FaroV5ShellNavigation.kt, montado solo en v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt y FaroV5V2CoreDevAuthority.kt. 4. Host revalida source Workspace, generation full-pointer, epoch, scope revision, transient grant M01 y receipt Auxiliary; usa CAS, clave idempotente por intento y no expone authorityToken a UI. 5. Estados Saved/Stale/Unavailable/Conflict/Rejected/UNKNOWN: éxito solo con receipt confirmado y post-fence; relectura en caso de incertidumbre. M02 no cambia contrato/store de M01.

## B5 Ataques y pruebas obligatorias

Negativos: read grant sin mutate; doble tap/recompose; click desde chat anterior; target colisionado de otro chat; background/onPause/revocación/ABA; owner revision stale; resultado UNKNOWN post-commit no debe reenviarse; no return COMMITTED Reality; puerto ausente deja Chat read-only; UI sin texto guardado persistido. Positivos: Add, LifecycleSet y FavoriteSet bajo gesto confirmado y receipt; tests JVM de owner/UI, XML cero skipped y :app:assembleV2CoreDev en workflow dirigido, guard protocol por SHA.

STOP si M01 no tiene B7 o no existe una API autorizada para revision/receipt: retornar DELTA_B5 y pedir nuevo permiso. B6/B7 NOT_RUN.
