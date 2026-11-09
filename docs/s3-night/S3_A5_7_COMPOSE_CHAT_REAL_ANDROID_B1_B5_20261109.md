# FARO V5 — S3.A5.7 X12 Compose Chat real Android emulado — B1–B5
Fecha: 2026-11-09 AR. Unidad S3_A5_7_COMPOSE_CHAT_REAL_ANDROID. Baseline HEAD 72996da3c8926225e238a2a697161b5c5947697e. Writer CAS 611. Objetivo automatizable independiente: UI Compose **real instalada** en Android API35 de GitHub Actions, no el G15 físico.

## B1 — Evidencia y brecha exacta
- Conservados seis subcomponentes A5 PASS scoped (33/33), X07/X11 owner-revision 4/4 (CI #42558390722, B7 a76fc76a), X12 native Activity 2/2 (CI #42559425316, B7 72996da3). No repetir ninguna prueba, ni certificarlas fuera de su alcance.
- V2 productiva OFF, S4/Auto/Noche OFF, repo etacsantafe-source/FARO-V5 rama work/s3-night-20261102. Writer RELEASED epoch610 en preflight. GitHub push permission confirmed, cola STAGED no autorizada. Catálogo oficial 172 congelado. A3 Guardados X04/X05 bloqueado sin autorización.
- FaroV5V2CoreDevActivity tiene botón nativo "Abrir Chat local de desarrollo"; al click foreground crea/abre grafo local real y monta FaroV5SurfaceHost.install, FaroV5AdaptiveShell y FaroV5ActiveChatScreen con Workspace/canonical Composer/Timeline/Search, pero **no** pasa assistantTurnPort, reviewPort ni applyRequestPort. Este hueco no queda probado por X12 native launcher anterior.
- Criterio exacto: dos NUEVAS pruebas de instrumentación en Android API35 que atraviesan botón nativo→Compose real; verifican campo "Preguntale a FARO", acción Enviar deshabilitada sin texto, y composición de un mensaje local USER durable luego de recreación real sin generar respuesta FARO sintética. Ninguna requiere IA, red, APIs, G15 o Apply.

## B2 — V4.4, V5 y reutilización
- V4.4 solo referencia de UX Chat y retorno por navegación; no copiar código/datos históricos ni inferir soporte AI.
- V5 canónico usa Compose FaroV5TextComposer (label Preguntale a FARO, envío solo si texto no vacío) y FaroV5MessageTimeline (solo mensajes persistidos, no respuesta sintética). FaroV5V2CoreDevActivity monta puertos reales tras acción del usuario y reabre desde noBackupFilesDir.
- Patrón certificado reusado: workflow .github/workflows/faro-v5-s3-a5-7-android-native-lifecycle-directed.yml (SDK37/AGP9, Android emulator API35, exact APK DEV aislada) sin ejecutar la clase anterior. Reutilizar sourceSet v5/app/src/g5AndroidTest y dependencia ya presente ui-test-junit4; sin tocar Gradle ni producción.

## B3 — Investigación externa, alternativas y decisión
- Guía primaria Compose Testing https://developer.android.com/develop/ui/compose/testing : `createAndroidComposeRule<Activity>` interactúa con semántica y sincronización reales; usar onNodeWithText/hasSetTextAction/performTextInput; **USAR**.
- Compose Testing APIs https://developer.android.com/develop/ui/compose/testing/apis : acciones de accesibilidad y aserciones UI sobre árbol semántico, no parsear screenshots; **USAR**.
- Interoperabilidad Compose + Views https://developer.android.com/develop/ui/compose/testing/interoperability : test real puede pulsar botón View inicial y verificar Compose resultante; **ADAPTAR** a FARO sin dependencias extra.
- ActivityScenario https://developer.android.com/reference/androidx/test/core/app/ActivityScenario : recreación real y persistencia no dependiente de Bundle; **USAR** el ActivityScenarioRule del AndroidComposeTestRule.
- A elegida: instrumentación nueva con `createAndroidComposeRule<FaroV5V2CoreDevActivity>()`, clic nativo explícito y aserciones Compose, dos métodos, segundo envía USER real y reabre; costo sin API paga, sin red. B descartada: tests JVM fake composables o snapshots, no prueban Android. C descartada: repetir 33/33 o usar Moto G15 ahora; afecta certificaciones y única campaña final. No se introducen librerías.

## B4 — Delta mínimo y aceptación
- NUEVO `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ComposeChatUiTest.kt`, dos tests separados con AndroidComposeTestRule sobre host real aislado:
  1. Al abrir Chat por View click foreground, Compose muestra timeline in-chat, editor accesible y Enviar inicialmente disabled. No presenta Apply, Revisiones o contenido FARO fabricado; NO asegura IA.
  2. Text input en Compose, Enviar, se ve el USER turn; activity.recreate() y reabrir Chat por click explícito conserva el texto UNA vez, no inicia modelo ni duplica mensaje.
- NUEVO `.github/workflows/faro-v5-s3-a5-7-compose-chat-android-directed.yml`, únicamente instala APK DEV y APK instrumentación en emulador API35; ejecuta SOLO FaroV5S3A57ComposeChatUiTest, acepta exactamente `OK (2 tests)` e `INSTRUMENTATION_CODE: -1`, requiere guard B1–B7. CI RUNNING/FAILURE no es PASS.
- No cambiar app real, UI productiva, manifest, catálogos, owners, layouts, hardening, S2/S4, main, V4.4 ni apps instaladas.

## B5 — Adversarial, fail-closed, STOP
- La entrada nativa solo acepta click con Activity RESUMED. La aserción Compose espera a que exista semántica real antes de tocar campos, usa timeouts explícitos y nunca declara ausencia de AI por un instante de render. El host NO cablea assistantTurnPort; el criterio positivo es mensaje USER persistido exactamente una vez tras recreación y regreso; no generar Fake FARO.
- Si ActivityScenario recrea, no hay reanudación privilegiada automática: el botón se pulsa de nuevo por prueba; autenticidad UI. No leer/escribir app G15, solo directorio aislado de paquete v2coredev en emulador efímero. No poner datos sensibles reales; mensaje ficticio de test.
- Text composer y search in-chat contienen dos campos: elegir el composer mediante semántica de su placeholder/en orden verificado; evitar manipular timeline/search equivocado. No parchear la producción para facilitar tests.
- El guard debe tener permiso READY en padre exacto, rutas limitadas test + workflow. XML Android log `OK (2 tests)` y guard del MISMO SHA son necesarios para B7. ROJO: volver a causa real con B7 RED_FIX y corrección dirigida; máximo dos hipótesis ciegas.
- B7 clasifica solo X12 **Compose Chat/emulador scoped**; no incluye lector TalkBack real, recursos/memoria Moto G15, orientación, Consent FARO, revisión/Apply, A3 Guardados, ni X01–X12 global. X04/X05 bloqueadas por autorización externa; A5 global OPEN. Reservar una única campaña física final.

