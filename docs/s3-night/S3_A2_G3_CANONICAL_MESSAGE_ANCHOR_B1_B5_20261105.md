# FARO V5 — S3.A2 G3 — B1–B5: búsqueda canónica y salto a mensaje exacto

Fecha: 05/11/2026 AR. Unidad: S3_A2_G3_CANONICAL_MESSAGE_ANCHOR. HEAD investigado: 962c04085c9bbf7b95374f52c7b2da71451bfa52. Alcance: Workspace local V2/dev, Android single-process; no V2 productiva, G4, G5, S4, Auto, Noche, permisos OS, cambios de esquema, datos físicos ni catálogos.

## B1 EVIDENCIA
- G1 CLOSED y G2 CLOSED B7: docs/s3-night/S3_A2_G2_FORMAL_CLOSED_B7_20261105.md; código G2 6ea5f223; CI 52/52, V2 CORE y APK aislada PASS. No reabrir.
- FaroV5WorkspaceSearchReadSource produce un documento por conversación, contenido USER ACCEPTED / FARO COMPLETED canónico, máximo 250 mensajes elegibles, 4.096 caracteres/conversación, 64.000 globales y 500 conversaciones; cobertura PARTIAL ante límites; NO indexa mensajes privados incompletos.
- FaroV5WorkspaceSearchSurfaceAdapter.openAuthorized revalida publicación, generación, ACL, revision y selección real, pero retorna solo CONFIRMED de conversación. FaroV5SearchScreen onOpenConversation no transporta MessageId; Chat tiene FaroV5MessageTimeline.focusMessageId exacto y búsqueda in-chat sobre timeline canónico.
- FaroV5ActiveChatTimeline hace scroll por id solo si el mensaje consta en el snapshot actual. Falta transportar el ancla post-selección sin asumir coincidencia desde el título del resultado.
- El permiso heredado está BLOCKED; writer RELEASED epoch 218 al recuperar; nuevo código sólo con permiso de esta unidad y writer CAS.

## B2 COMPARACIÓN
- V4.4, según inventario read-only previo docs/s3-night/S3_BACKLOG_20261102.md y G2 B1-B5: búsqueda por dominios, degradación y cabeceras de cobertura textuales; UI podía omitir advertencias. V5 conserva cobertura tipada, owner auténtico y selección idempotente; reutilizar, NO portar la autoridad V4.4.
- Chat actual ya permite buscar en mensajes y navegar con focusMessageId. Lo único faltante para salto global es ancla efímera a MessageId canónico durante openAuthorized y su revalidación al pintar Chat.
- Búsqueda global limitada puede reconocer un cuerpo sin devolver cuál mensaje produjo la coincidencia. Un ancla se obtiene releyendo el timeline autorizado; si no se encuentra, se abre solo la conversación SIN fingir salto exacto.

## B3 INVESTIGACIÓN
- Element Android (issue original de búsqueda en sala) https://github.com/element-hq/element-android/issues/2110 exige que tocar un resultado abra el timeline en el mensaje y permita volver a la búsqueda; separar selección de sala de ubicación de mensaje. REFERENCIA UX, no copiar código ni incluir dependencias.
- Element X Android registró en octubre 2026 incidencias de resultados de Search que no navegan a la ubicación especificada: https://github.com/element-hq/element-x-android/issues/7988 . REFERENCIA adversarial: probar target inexistente, rotado, revocado y desactualizado; el salto puede fallar aunque haya resultado.
- Android Room documenta FTS y tokenizador Unicode61 https://developer.android.com/training/data-storage/room/defining-data . Alternativa A: nuevo índice FTS con rowid, triggers y migraciones; DESCARTAR para G3: más copies de contenido y riesgo de restauración, sin resolver ACL/navegación. No afirmar soporte FTS en este código.
- Alternativa B ADAPTAR: relectura canónica acotada y ancla efímera post-confirmación, con validación al recibir timeline; reusa SQLite/Chat/G1/G2; sin almacenamiento auxiliar. Alternativa C DESCARTAR: pasar ID persistente en deep link o confiar en hit sin relectura; puede navegar con permiso revocado o Chat viejo.
- Los hallazgos externos se usan como diseño y pruebas negativas; licencia de código ajeno no afectada porque no se importa código.

## B4 UNIFICACIÓN
- Mantener SearchHit opaco y SearchPage existentes: publicación guarda la consulta canónica en memoria, sin credenciales ni nuevos campos persistentes.
- Resolver el MessageId con readTimeline del Workspace verdadero tras revalidar generation/ACL/revision. Sólo mensajes USER ACCEPTED / FARO COMPLETED y mismo conversationId; búsqueda de texto normalizada español existente. Elegir último mensaje coincidente dentro del presupuesto 250/64k; nunca declararlo exhaustivo.
- Después de selección Saved verificar el dueño otra vez; entregar ancla efímera por consumeConfirmedMessageTarget una sola vez. La UI comunica target solo tras CONFIRMED. Shell espera lectura canónica nueva; si falla no navega y descarta ancla. Chat vuelve a comprobar pertenencia exacta al timeline; si falta, no centra un mensaje ajeno.
- Rutas específicas: v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SearchSurface.kt; FaroV5InChatSearchPolicy.kt; FaroV5ShellNavigation.kt; FaroV5ActiveChatScreen.kt; FaroV5ActiveChatTimeline.kt; v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5WorkspaceSearchReadSource.kt; FaroV5WorkspaceSearchSurfaceAdapter.kt; v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5WorkspaceSearchSurfaceAdapterTest.kt; .github/workflows/faro-v5-s3-home-notice-directed.yml.

## B5 AUDITORÍA
- NEGATIVAS: hit no publicado/tampered, ACL revocado o regrant distinto, generation/pointer ABA, readiness OFF, owner cerrado, timeline fallido, mensaje borrado o no elegible, stale selection y búsqueda concurrente. Nunca revivir un ancla después de invalidación o cambio de consulta.
- La sola selección de conversación NO autoriza el salto a un MessageId. El Chat exige ConversationId activo y snapshot fresco donde exista exactamente el MessageId. No conservar identidad fuera de memoria y nunca usar el índice como autoridad.
- Si búsqueda de cuerpo sólo cubrió un prefijo, sin mensaje exacto, abrir conversación como hoy y mantener PARTIAL; no mostrar 'sin resultados' definitivo ni inventar snippet/posición. No agregar datos a SEARCH ni alterar receipts/Undo.
- Aceptación dirigida: test G3 de mensaje canónico español y exclusión de FARO PARTIAL; target consumible una vez, bloqueado tras revocación; prueba de cambio de conversación/invalidación; compilar Compose y preservar suites G1/G2 52/52 con nuevos XML exactos y protocolo PASS. G5 se reserva la campaña física de rotación/reinicio.
- Dictamen B5: READY_FOR_BOUNDED_G3_DELTA; requiere PERMIT READY separado en padre, preflight por rutas y writer CAS antes de B6. Si CI no da PASS: G3 PARTIAL, nunca CLOSED.
