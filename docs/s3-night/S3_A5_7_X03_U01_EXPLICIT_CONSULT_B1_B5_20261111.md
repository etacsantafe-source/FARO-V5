# FARO V5 — S3.A5.7 X03 U01: gesto explícito Consultar FARO, DEV aislado — B1–B5

Fecha: 2026-11-11 AR. Unidad exacta: `S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV`. Base fuente auditada: `f2b98dcb0bd6b1e3e6a873974235522e05717e13`. **Documento de preparación; NO afirma código B6, CI, emulador, IA real ni X03 global PASS.** X03 completo requiere una subunidad separada para admisión del modelo real. A3 M01–M05 PASS automatizado; X04/X05, X07/X11 PASS en alcance propio; reutilizar y NO repetir.

## B1 — Evidencia real, hueco y objetivo falsable
- `FaroV5V2CoreDevActivity.kt` blob `2694090ba564baad0cfa31e3f73dd7171b459fb2`: monta `FaroV5ActiveChatScreen` con workspace/timeline/selection/composer y sin `assistantTurnPort`; botón inicial `Abrir Chat local de desarrollo`; leyenda «Sin Groq/IA». Estado real DEV LOCAL_ONLY.
- `v5/app/src/v2CoreDev/AndroidManifest.xml` blob `14f0babf1ea17fc94fcbadc68661c535745b216d`: elimina `android.permission.INTERNET` y `FaroV5AiSettingsActivity`. **No se puede declarar IA remota integrada en este APK; la negativa de red es intencional.** Prohibido aflojar el manifiesto en U01.
- `FaroV5ActiveChatScreen.kt` blob `d85e6e04e009f856ca65a0eefb2096a23648dfa5`: al guardar un USER en Workspace, si hay `assistantTurnPort`, líneas 323–376 despacha IA AUTOMÁTICAMENTE tras `sendDraft`. La semántica nueva de X03 es gesto **separado** «Consultar FARO», sin cambiar el comportamiento preexistente del Chat S2 aislado.
- `FaroV5AdaptiveShell.kt` ya acepta un `assistantTurnPort` opcional y el componente Chat recibe `ConversationAssistantTurnDispatchPort`; `FaroV5TextComposer.kt` controla el envío. `FaroV5ActiveChatReasoningSession.kt` requiere USER durable ACCEPTED, validates egress/revocación y no reenvía un turno PROCESSING. No reimplementar ese motor.
- `FaroV5F1ConsentedQuarantineHost.kt` + `FaroV5QuarantinedV2ChatHostBinding.kt` + `FaroV5TrustedModelGatewayFactory.kt` prueban que S2 ya tiene motor real de IA **solo en generación staged/no activa**, con credencial Keystore/consentimiento y endpoint de catálogo fijo. La apertura de ese host sobre el pointer V2 DEV activo fallaría por su aislamiento: **no copiarlo a ciegas**.
- Contrato falsable de U01: luego de enviar un USER real, no se puede invocar ningún `ConversationAssistantTurnDispatchPort` por el mero envío en modo EXPLICIT; un click separado permite exactamente un intento sobre ese USER tipado y persistido cuando existe un puerto autorizado; cuando no existe, UI local no ofrece falsa conexión remota. Cambiar de conversación, revocar/recrear o click duplicado no autoriza otra solicitud.
- Pruebas anteriores X04/X05 emulador API35 2/2 run `43084379020`, A3 M05 11/11 run `43055545460`, S2 F1 JVM 4/4 run `41421309967`: **no prueban este gesto X03** ni deben repetirse rutinariamente.

## B2 — Reuso, compatibilidad V4.4 y alternativas locales
- V4.4 se conserva como referencia **solo de experiencia Chat/UX** según `docs/s3-night/S3_A5_X01_X12_B1_B5_20261109.md`; no importar código, datos, identidad o permisos V4.4 ni reconstruir contratos existentes.
- Reusar `ConversationAcceptedUserTurn`, `ConversationAssistantTurnDispatchPort`, `ConversationAssistantTurnOutcomePort`, `ConversationTimelineReadPort`, la revisión/receipts y `FaroV5ActiveChatReasoningSession`. El test puede inyectar un puerto falso **solo desde el APK test**, nunca un proveedor sintético en el producto.
- Preservar comportamiento AUTOMATIC_ON_SEND existente **como default para todos los callers no modificados** (incluido Chat S2). Introducir opción opt-in `EXPLICIT_CONSULT` únicamente en el caso DEV aislado de U01. No disparar red en local; no habilitar V2 productiva ni Apply.
- Reusar `g5AndroidTest`, emulador API35 y checks XML/manifest de workflows dirigidos X04/X05 y X06; no reejecutar sus métodos certificados.

## B3 — Investigación externa, decisión técnica y licencias
- Android arquitectura/UDF: https://developer.android.com/topic/architecture — **USAR** eventos explícitos de UI hacia un estado derivado del Workspace canónico; la Activity no debe almacenar autoridad durable ni contenido como fuente de verdad.
- UI lifecycle/estado: https://developer.android.com/topic/architecture/ui-layer — **USAR** relectura del estado autorizado tras recomposición/recreación, sin recuperar un token de egreso desde un Bundle.
- Compose testing: https://developer.android.com/develop/ui/compose/testing — **USAR** aserciones sobre botón accesible, click efectivo y eventos; no inferir pruebas Android de JVM.
- Gradle rendimiento CI: https://docs.gradle.org/current/userguide/performance.html — **ADAPTAR** tarea dirigida y cache existente; sin ampliar plugins ni regenerar todo el repositorio.
- A) **USAR** opt-in explícito en Chat compartido + puerto tipado inyectable + test aislado. B) **DESCARTAR** habilitar `INTERNET` o montar directamente S2 staged en V2 activo: transgrede aislamiento y introduce credenciales/permisos. C) **DESCARTAR** fake de IA que muestre respuesta sintética como éxito real. D) **DIFERIR** real Groq: requiere una **nueva unidad X03 U02**, B1–B5 separados sobre host/manifest/consentimiento y revisión de aislamiento. Sin APIs pagas.

## B4 — Ejecución exacta para Work (U01, no X03 global)
**Las únicas CUATRO rutas funcionales candidatas del permiso U01**, sin comodines ni ampliación:
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ActiveChatScreen.kt`
2. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`
3. `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X03ExplicitConsultUiTest.kt` (archivo nuevo de instrumentación)
4. `.github/workflows/faro-v5-s3-a5-7-x03-consult-android-directed.yml` (workflow dirigido nuevo)

Plan B6:
1. Separar envío USER de solicitud al asistente mediante parámetro de modo opcional en `FaroV5ActiveChatScreen`, default compatible. Los cambios de editor/guardados/review de S2 no se alteran.
2. En modo explícito, obtener el USER ACCEPTED por `conversationId+messageId+turnId` desde Workspace/timeline canónico, nunca texto borrador ni mensaje del modelo. Si el turno ya inició, cambió, quedó PROCESSING/UNKNOWN o carece de permiso, ocultar/deshabilitar nueva ejecución y responder de forma segura.
3. Exponer acción accesible «Consultar FARO» **solo cuando hay puerto tipado y solicitud fresca autorizable**. El APK V2 DEV permanece sin red y sin puerto IA; ningún botón puede fingir un proveedor conectado. Con `assistantTurnPort=null`, envío es solo local.
4. Pruebas API35: (i) fixture aislado con Workspace real/fake dispatch **test-only** comprueba envío sin auto-dispatch, click explícito único y cero repetición ante doble click; (ii) Activity V2 DEV real con usuario Workspace comprueba ausencia de red/IA ficticia, coherencia tras recreación y ausencia de Apply o consentimiento implícito. Si no es viable montar fixture tipado sin tocar una quinta ruta, STOP `NEEDS_PREP` y auditar delta exacto.
5. Workflow nuevo dirigido con Java21, SDK37, APK V2 DEV + androidTest en `-PfaroG5AndroidTestV2CoreDev=true`, emulador API35, clase nueva exacta, XML 2 tests/failures=0/errors=0/skipped=0 y `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`; verificar manifiesto SIN `INTERNET`, artefactos APK/XML y guard B1–B7 por SHA. El test no invoca Groq remoto.
6. Auditoría B7 independiente al SHA funcional; solo `X03_U01_PASS_SCOPED_EXPLICIT_UI_NO_REMOTE`, **NO `X03_DONE`**. Siguiente U02 requiere investigación de host/consent/network separada y nuevo permiso.

## B5 — Riesgos adversariales y decisión
- **Auto-egress:** un USER enviado no equivale a consentimiento de ejecutar IA. Si el modo explícito dispara `dispatchAcceptedTurn` en `sendDraft` o dispara ante restore/onResume, FAIL.
- **Falsa autoridad:** el botón no concede `INTERNET`, credenciales o Apply. No usar `FaroV5ModelReviewBridge` como ejecución, no publicar respuesta sintética, no activar V2 productiva.
- **Idempotencia/ABA:** revalidar ID+turn+conversación al click, prohibir doble ejecución del mismo turno, no reintentar `PROCESSING`/UNKNOWN; callback tras dispose o revocación no produce éxito visible; revisar source canónica al volver.
- **Regresión S2:** default actual de `FaroV5ActiveChatScreen` no cambia; ejecutar al menos suite focal que pruebe modo previo además del caso nuevo si hay tests/infra disponibles, sin repetir toda S2.
- **Fail closed:** si la UI no puede distinguir respuesta confirmada de cancelada, negada o interrumpida, mostrar estado incierto y exigir reabrir, nunca inventar respuesta.
- **No scope creep:** no tocar `AndroidManifest.xml`, `build.gradle.kts`, fábricas Groq, claves, catálogos, SQLite, shipping, main, V4.4, S4, Auto/Noche ni Moto G15. Una brecha real exige RED_FIX B1–B5 y nuevo permiso; no ampliar por impulso.
- **Stop del protocolo:** esta auditoría no es permiso. El preparador debe publicar documento/paquete en commit DOCUMENTAL, validar guard, emitir permiso READY con esta evidencia y rutas exactas en otro commit, comprobar preflight; Work solo entonces empieza B6. Si no se cumple, STOP `NEEDS_PREP`.

**Dictamen B5:** `PREAUDITED_FOR_SCOPED_UI_UNIT`. Está prohibido tratar U01 como integración Groq completa. La unidad U02 y X09/X10 quedan planificados pero NO autorizados para código.
