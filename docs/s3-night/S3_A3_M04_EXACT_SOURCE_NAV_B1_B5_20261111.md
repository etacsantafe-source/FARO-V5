# S3.A3 M04 — Guardado → mensaje fuente exacto — B1–B5

Unidad S3_A3_BOOKMARKS_EXACT_SOURCE_NAV_DEV. Depende M01–M03 B7 PASS.

Fecha 2026-11-11 AR. Base auditada 00bd1f5dd1833bcea136cc4a50219ede08000fd0.

Estado PREAUDITED_DEPENDENCY; NO READY ni permiso emitido. Solo M01 cuenta con permiso READY. Esta preparación no ejecuta B6 ni B7.

Fuentes externas verificadas: Android Architecture https://developer.android.com/topic/architecture/recommendations (ADAPTAR single-source-of-truth y UDF), Slack Saved https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later (REFERENCIA experiencia, no cloud), SQLite BEGIN IMMEDIATE https://www.sqlite.org/lang_transaction.html (USAR transacción existente). Sin dependencias pagas.

Política transversal: un writer CAS, HEAD y permit frescos, staged ref solo rama work/s3-night-20261102; V2 shipping/S4/Auto/Noche OFF; main/V4.4 no tocar; tests físicos G15 reservados a campaña única final.

## B1 Evidencia de código

Read binding FaroV5BookmarkReadHostBinding.kt blob c105075727d5a7ad4cd333e4e5b4f1e9687d6e66 expone solo metadatos bookmarkId+conversationId+messageId, nunca mensaje. ShellNavigation.kt blob a947175019799079bc0ef2161e8b6bbb83a9c276 ya implementa Search→Chat con lectura fresca. FaroV5ActiveChatTimeline.kt blob a4475a61b9ea6ab8d46373961645a0cfa699781b y FaroV5MessageTimeline.kt blob d0000c7b9aff469c5db022bd68cb3d204deec3cd tienen focusMessageId, y LazyColumn desplaza a ID existente, pero eso no prueba target/ACL. Guardados solo es entrada SECONDARY sin pantalla funcional en Shell.

## B2 Paridad y reutilización

V4.4 pin de conversación no posee este contrato exacto (P00). Reusar comportamiento certificado A2 Search→Chat navegación canónica commit abca920c / CI 41853477492, añadiendo revalidación de bookmark identity, generation y source. No cachear texto, índices ni tokens.

## B3 Alternativas

Android architecture/navigation recomienda pasar identificadores mínimos y consultar SSOT (https://developer.android.com/topic/architecture/recommendations), ADAPTAR; Slack Saved permite abrir mensaje en contexto (https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later), REFERENCIA; SQLite: metadata read previo no concede autoridad posterior, NO inferir permiso. DESCARTAR deep link público con token, snapshot texto en ruta, abrir chat anterior por defecto.

## B4 Plan

1. Crear host-only FaroV5BookmarkSourceNavigationBinding.kt en app: revalidar click contra page actual/isPageCurrent, generación completa/hostEpoch, Workspace bookmark targetAvailable y ACL, relectura del bookmark para impedir race. 2. Selección con ConversationWorkspaceSelectionPort y key idempotente; esperar Saved+snapshot canónico y releer ConversationTimelineReadPort. 3. Entregar focusMessageId EFÍMERO solo si actual conversationId+messageId idénticos a fuente; UI resalta y desplaza el mensaje exacto. 4. Cablear destino Guardados de Shell, callback desde M03, Activity/Authority DEV y SurfaceHost; conservar UI shipping OFF. 5. Tests de host+shell+focus y APK DEV por SHA.

## B5 Casos de ataque

Mismo messageId en dos conversaciones; generación ABA y pointer switch; bookmark REMOVED o revocado; mensaje archived/deleted; seleccionar Saved pero relectura falla; rotación o callback tardío; LazyColumn sin target; click doble; fallback a chat anterior prohibido. Debe mostrar No disponible sin mostrar texto en estado incierto. No retener cached target tras cambiar sesión. STOP si no existe selección Workspace canónica o lookup autorizado por bookmark ID.
