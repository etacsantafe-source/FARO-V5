# FARO V5 — X09 Review explícito, Workspace canónico, cero Apply implícito · B1–B5
Fecha 2026-11-11. Unidad `S3_A5_7_X09_EXPLICIT_CANONICAL_REVIEW_NO_APPLY`. HEAD de preauditoría `c5e5e5bad2c50c2c6c0797442a1f09c89444391f`. U01/U02 X03 **B7 PASS scoped**, NO repetir. `X03` global sigue OPEN para prueba de proveedor Free y futura integración, sin abrir Internet en V2 CORE DEV. Este documento **no es B6 ni un permiso por sí mismo**.

## B1 — Hallazgos verificables, brecha exacta
- U02 final `fcfbe151f55561a70acc9fb552760d96cea30235`, Android API35 `43116880002` XML 2/0/0/0, guard `43116879920`, B7 `docs/s3-night/S3_A5_7_X03_U02_WORK_B7_PASS_20261111.md` blob `1330195c252cbc83e8406deb8bf13a91fe08abf8`. Último HEAD `c5e5e5bad2c50c2c6c0797442a1f09c89444391f`, writer RELEASED epoch751, permiso U02 BLOCKED/consumido.
- X09 READ_ONLY anterior `docs/s3-night/S3_A5_7_X09_READ_ONLY_PREPARATION_20261111.md` blob `b1c21f85caee1a37957d49bd410284593327d130`. Sus cinco preguntas de diseño se resuelven aquí, sin repetir suites S2.
- `FaroV5QuarantinedV2ChatHostBinding.kt` blob `6ce0ffcc29fd331cd437d35bb1a3ce4c12729d83` YA expone `reviewPort`; `FaroV5ModelReviewBridge` blob `c73bbb23a789e668fa0730abc7390e934030f232` genera propuesta durable solo desde USER ACCEPTED+FARO COMPLETED y allowlist `profile.update_declared`; no interpreta todo el catálogo como acciones permitidas.
- `FaroV5X03U02IsolatedAiActivity.kt` blob `4222878f0ee7e5fd5030a14095a16f7ae61b0b4e` (líneas 117–130) pasa `reviewPort=null`, `applyRequestPort=null`, modelo real staged y `EXPLICIT_CONSULT`, consentimiento y Free confirmado por sesión; `onPause` invalida síncronamente la sesión; no modificar su root, manifest, aislamiento ni Free.
- `FaroV5ActiveChatScreen.kt` blob `6435b762e8e37d99abc7eabce4f629cb4b272dc9`: si hay reviewPort, cada review se dibuja con `reviewExpanded=true` (líneas 239–244), por lo que Confirmar/Descartar pueden aparecer SIN acción «Revisar cambios». La `FaroV5PlanReview.kt` blob `3c528b23aae7c1806f1144adbcee41a5e371e6f7` YA ofrece `onOpenReview` y solo muestra Confirmar/Descartar tras `reviewExpanded=true` cuando se configure así. **No hace falta modificar ese componente**.
- Cadena UI completa a través de `FaroV5SurfaceHost.kt` blob `33dab30f764958fd38b0c04a083ba042609ce9bb` → `FaroV5AdaptiveShell.kt` blob `7224a57309b38bacc0554cb04228a4510d0f1f63` → `FaroV5ShellNavigation.kt` blob `8417141d5200e8733f671b4508b6f5c392c51c1b` → `FaroV5ActiveChatScreen.kt`. Los cuatro lugares requieren el modo; no volver a caer en NEEDS_PREP_SCOPE_PROPAGATION.
- `FaroV5ActiveChatReviewSession.kt` blob `ba8ce6cb11688cf51e2e5cc714ff5bfaeacbe1e2` valida generation/pointer/ready y bloquea Apply; `ConversationPlanReview.kt` blob `f68bcc0dee9610ff73dd49079319becf199c229d` exige IDs, authorityToken, revision y planFingerprint. Confirmar/Descartar solo transiciona Workspace, NO modifica Reality ni emite un receipt.
- Aceptación falsable: en host U02 debug, candidato válido aparece contraído con «Revisar cambios»; click explícito revela detalles/Confirmar; sin click no se puede confirmar; doble click/conversación cambiada/revisión obsoleta se niega por canonical owner; ninguna ruta expone Apply ni certifica Groq live.

## B2 — Referencia V4.4 y reutilización V5
- V4.4 solo referencia UX de revisiones y navegación; no importar objetos, datos ni ejecutores históricos. S2 8/8 congelada, S3 A3 M01–M05 B7 PASS y X04/X05 Android 2/2, X07/X11 JVM 4/4, no repetir.
- USAR `FaroV5PlanReview`, `ConversationPlanReviewSnapshot/context`, `FaroV5ActiveChatReviewSession.port`, `host.reviewPort`, `FaroV5ModelReviewBridge` y el owner Workspace canónico. **No añadir otro journal Review, un esquema, contratos, catálogo ni fake AI en APK debug**.
- Propagar opt-in `FaroV5ReviewDisclosureMode.EXPLICIT_OPEN` como parámetro default `LEGACY_EXPANDED` por superficie, host y Chat, conservando absolutamente TODOS los callers S2 y V2 DEV previos. Crear enum en `FaroV5ActiveChatScreen.kt` existente (sin nueva ruta). `FaroV5SurfaceHost.install` usa `@JvmOverloads`: agregar campo al FINAL, con default, para conservar los overloads Java binarios/fuente. `AdaptiveShell` incluye modo en `key`. `ShellNavigation` reenvía hasta Chat. Activity U02 opt-in + `reviewPort=ready.reviewPort`, `applyRequestPort=null`.
- Modo explícito sólo controla visibilidad: NO es concesión de autoridad. El owner de generación y `reviewPort.decide` retienen CAS, fingerprint y todos los controles de aprobación.
- Sin nueva Activity/launcher/manifest: X09 adopta el mismo root staged U02 YA CERTIFICADO; todas las escrituras (Review) quedan en Workspace de U02. No tocar S2 Activity/Settings/Factory ni V2 Core DEV.

## B3 — Investigación externa y decisión
- Android Compose State Hoisting, https://developer.android.com/develop/ui/compose/state-hoisting: USAR single source of truth y eventos UI hacia owner; booleano de expansión efímero no equivale a decisión. Estado no persistido en Bundle/rememberSaveable.
- Android Compose Side-effects, https://developer.android.com/develop/ui/compose/side-effects: USAR event-driven review; recomposición no debe ejecutar confirmación ni restaurar un intent.
- Android Compose testing, https://developer.android.com/develop/ui/compose/testing: USAR aserciones semánticas de «Revisar cambios» y ausencia de confirmación previa, no inferir de screenshot/código.
- Android lifecycle, https://developer.android.com/guide/components/activities/activity-lifecycle: USAR nueva confirmación tras reentrada; `onPause` invalida pending.
- Opción A ELEGIDA: campo explícito con default compatible y propagación completa de 7 archivos, Workspace dueño de decisiones.
- B DESCARTADA: cambiar globalmente `reviewExpanded=false`; rompería UX y tests S2 congelada.
- C DESCARTADA: esconder Confirmar únicamente en View sin exigir context tipado al owner o guardar permiso booleano en Bundle; falsa autoridad después de recreation.
- D DESCARTADA: Activity paralela para Review, nuevo Workspace/Reality o IPC; duplica modelo, pierde continuidad canónica y aumenta scope.
- E DESCARTADA: ligar «Confirmar» a `applyRequestPort` o hacer Apply al abrir Review; X10 tiene su propia auditoría/permiso.
- F DIFERIDA: más operaciones de perfil, finanzas o catálogo; `FaroV5ModelReviewBridge` actual solo admite perfil allowlisted, no ampliar sin aprobación contractual.

## B4 — Siete rutas autorizables y B6/CI dirigidas
**Rutas exactas para permiso individual X09, ninguna más:**
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ActiveChatScreen.kt`: enum modo opt-in, expanded identity ligada a conversationId+reviewId+authorityToken+revision+planFingerprint; se pierde al cambiar conversación, generación, pending o recomposición/Activity recreate. `onOpenReview` evento visual; `onConfirm`/Discard solo al abrir, invocación de `reviewPort.decide` con context real, revalidación de stale por owner. No pasar Apply.
2. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt`: propagar modo nuevo al FINAL `@JvmOverloads install`, default legacy, junto al modo X03 ya existente.
3. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AdaptiveShell.kt`: propagar modo a `ShellScaffold`, incluir modo en `key` sin romper backstack/Guardados/Chat.
4. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt`: propagar modo de `ShellScaffold` al `ActiveChatScreen`, default legacy.
5. `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5X03U02IsolatedAiActivity.kt`: host U02 staged reutilizado, set `reviewPort=ready.reviewPort`, `applyRequestPort=null`, `reviewDisclosureMode=EXPLICIT_OPEN`; conservar Free/session, `EXPLICIT_CONSULT`, no egress automático ni nueva credencial.
6. `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X09ReviewUiTest.kt`: TEST ANDROID NUEVO: fixture `ConversationWorkspaceStore` real + `FaroV5ActiveChatReviewSession` y candidate canónico bounded, y montaje U02 con credencial sintética sin consultarlo. Casos exactos:
   - `canonicalReviewRequiresExplicitOpenBeforeApproveAndLeavesRealityUnchanged`: propuesta en Workspace, UI colapsada, no Confirmar, click Revisar cambios -> Confirmar explícito -> estado APPROVED durable, Reality intacta, no receipt.
   - `reviewRevokesOnLifecycleOrStaleContextWithoutApply`: context stale, cancel/doble click/recreate produce rechazo o revalidación; U02 sin botón Apply ni Internet en V2 DEV; ninguna llamada HTTP.
7. `.github/workflows/faro-v5-s3-a5-7-x09-review-android-directed.yml`: WORKFLOW nuevo dirigido debug build + Android API35, Java21, SDK37, `-PfaroP6PhysicalApplicationId=ar.com.faro.alpha.s2isolated`, `:app:assembleDebug` y `:app:assembleDebugAndroidTest`, clase nueva exacta; XML real 2/0/0/0 y `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`; hashes APK y artifact, regression `FaroV5QuarantinedV2ChatHostBindingT2Test` JVM si compilación lo permite. Comprobar U02 debug no toca S2 y V2 DEV permanece sin `INTERNET`. Guardia 7 bloques SHA exacto.

Se permite importar/ajustar tests SOLAMENTE en ruta test autorizada. Antes del código Work debe verificar desde HEAD actual y ejecutar preflight en los 7 paths. Si descubre necesidad **comprobada** de octava ruta, STOP `NEEDS_PREP` delta, no violar permiso.

## B5 — Auditoría adversarial, PASS/FAIL
- Mostrar propuesta sin gesto o habilitar Confirmar antes de «Revisar cambios» en modo EXPLICIT -> FAIL.
- Recomposición, pausa, recreación, cambio de sesión/generación/candidato no conserva identidad visual que permita confirmar; relectura canónica+CAS. Evitar guardar la apertura en `rememberSaveable`; no alimentar desde JSON remoto.
- Doble Confirmar se serializa `reviewBusy`; Workspace owner verifica ID+authorityToken+revision+fingerprint, negar stale y no segundo efecto.
- Mantener **aprobación ≠ Apply**. `applyRequestPort=null` y prohibido crear `FaroV5ActiveReviewedApplySession`/delivery/Reality mutaciones/receipts desde X09. `reviewPort` no implica `ApplyPort`. Verificar Reality sin cambios.
- `S2` usa default LEGACY_EXPANDED; no modificar S2 y no convertir el host U02 en V2 productiva. X03 U01/U02 no repetir CI certificada por rutina. Cualquier regresión requiere delta dirigida.
- Ningún test llama a Groq real, almacena claves o presupone plan Free; credencial sintética sólo de `androidTest`.
- `V2 Core DEV` mantiene Internet removido, main/V4.4 intactos, S4/Auto/Noche OFF, G15 físico solo campaña final.
- B7 PASS solo si Android API35 XML 2/0/0/0 + hashes/APK + guard funcional SUCCESS + auditoría B7 independiente y writer RELEASED. **`X09_PASS_SCOPED_DEBUG_CANONICAL_REVIEW_NO_APPLY`**, no X09 global sin evaluar matriz total ni X03 remote live.
- RED_FIX documental ANTES de segundo commit de código si CI falla, causal y mínimo. No alterar aserciones de seguridad para pasar verde.
- **Dictamen B5: PREAUDITED_FOR_INDIVIDUAL_READY_X09_SCOPED_DEBUG**; permiso READY requiere commit documental posterior al PASS de guard B1–B5. Work luego B6/CI/B7; siguiente X10 tiene **permiso independiente**.
