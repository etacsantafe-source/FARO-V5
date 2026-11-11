# FARO V5 — X03-U01 delta B1–B5: propagación explícita Activity→SurfaceHost→AdaptiveShell→ShellNavigation→Chat

Fecha: 2026-11-11 AR. Unidad original e INMUTABLE: `S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV`.
Base de este delta: `237a6fda683f363846e2f4dc647ca2b6bc697a03`. Causa: Work `NEEDS_PREP_SCOPE_PROPAGATION`; B6 no iniciado, B7 no ejecutado. Documento original B1–B5 `docs/s3-night/S3_A5_7_X03_U01_EXPLICIT_CONSULT_B1_B5_20261111.md` blob `f726eacaa302778405320d8707fa94476a0c0acb`; **conservar íntegra esa evidencia y agregar solo este delta material**.

## B1 — Hallazgo observable, causa y alcance de corrección

- Dictamen independiente de Work: `docs/s3-night/S3_A5_7_X03_U01_WORK_NEEDS_PREP_20261111.md` blob `8328980edfce723baeac041a92cbd3adc394a7a0`; preflight original 4/4 PASS, pero las rutas no alcanzan la ruta real de pantalla. El checkpoint `237a6fda683f363846e2f4dc647ca2b6bc697a03` bloqueó el permiso antes del código. No hubo compilación/APK/XML ni B7 nuevos.
- `FaroV5V2CoreDevActivity.kt` blob `2694090ba564baad0cfa31e3f73dd7171b459fb2` llama `FaroV5SurfaceHost.install` en dos sitios: apertura inicial (líneas 215–227) y regreso desde Guardados (370–380). **Ambos** requieren modo explícito; no alcanza con uno.
- `FaroV5SurfaceHost.kt` blob `626266d393249078df31e8123afd247aff221992` recibe hoy `assistantTurnPort` pero no un modo; delega a `FaroV5AdaptiveShell`.
- `FaroV5AdaptiveShell.kt` blob `fde0c1bee1d0bb04b01322f1075a8abcc94c0f00` delega a `FaroV5ShellScaffold` y tiene un `key(... assistantTurnPort ...)` que debe incluir modo si varía la sesión.
- `FaroV5ShellNavigation.kt` blob `911f7e5ae8510ae351eb68da35e835acfb065a63` define `FaroV5ShellScaffold` línea 197, y monta `FaroV5ActiveChatScreen` en 491–502. Sin propagación, la UI usa su default y activa el dispatch automático de `sendDraft` cuando existe puerto: falla objetivo X03 de gesto separado.
- `FaroV5ActiveChatScreen.kt` blob `d85e6e04e009f856ca65a0eefb2096a23648dfa5` recibe `assistantTurnPort`, y despacha automáticamente a partir de un `ConversationTimelineWriteResult.Saved` si no replay. Debe preservar default para S2 y permitir opt-in DEV sin nuevo owner.
- X03-U01 solo es gesto local comprobable con puerto inyectado en **instrumentación test-only**. V2 DEV sigue sin puerto de IA y con manifiesto sin Internet; si no existe puerto tipado autorizado, no mostrar un botón que prometa Groq. X03 U02 remota es otra unidad y no recibe permiso por este delta.

## B2 — Reutilización, callers y compatibilidad

- **USAR:** un tipo explícito `FaroV5AssistantRequestMode` con `AUTOMATIC_ON_SEND` y `EXPLICIT_CONSULT`, definido en `FaroV5ActiveChatScreen.kt` existente (sin 8.º archivo de contrato). Los defaults en `install`, `AdaptiveShell`, `ShellScaffold` y `ActiveChatScreen` conservan el antiguo `AUTOMATIC_ON_SEND` para **todos los callers previos**. En Activity DEV agregar `assistantRequestMode = EXPLICIT_CONSULT` en AMBOS montajes.
- **Compatibilidad binaria/fuente:** `FaroV5SurfaceHost.install` es `@JvmStatic @JvmOverloads`. Agregar el parámetro nuevo **AL FINAL de la firma** con default, sin alterar tipos ni orden de argumentos anteriores. Comprobar callers Java/Kotlin/overloads existentes compilando la variante elegida. No renombrar ni reordenar parámetros viejos.
- `FaroV5AdaptiveShell` y `FaroV5ActiveChatScreen` incluyen el modo en claves `key(...)` cuando se aplique, para que el contexto de callback y estado efímero no sobreviva a un cambio de modo o conversación. El modo no es grant, no se almacena en SQLite ni Bundle.
- `ShellScaffold` usa `assistantTurnPort` y puertos tipados existentes, no introduce global mutable ni copia de texto. No sustituir la ShellNavigation de S3 ni romper reentrada Guardados→mensaje.
- No introducir proveedores falsos en el APK DEV, tocar X04/X05, catálogos, esquema, permisos ni V4.4. El modo EXPLICIT no habilita egress sin consent/owner/puerto por separado.

## B3 — Solución seleccionada y alternativas adversariales

La investigación externa original queda vigente: Android UI/data layer https://developer.android.com/topic/architecture/ui-layer ; Compose Testing https://developer.android.com/develop/ui/compose/testing ; lifecycle https://developer.android.com/guide/components/activities/activity-lifecycle . **USAR** parámetro tipado unidireccional y event-to-owner con estado canónico; **ADAPTAR** Compose `key` para invalidación de control efímero al cambiar modo; la lectura source en Workspace gobierna si el turno todavía es aceptado y vigente. Android `@JvmOverloads` requiere preservar el orden de parámetros existentes al extender la firma.

Opciones evaluadas:
- A. **ELEGIDA:** propagar por 3 rutas de shell con defaults (7 rutas exactas totales), dos callsites de Activity, UI local con modo comprobable, sin red. Cambio acotado y auditable.
- B. **DESCARTADA:** inferir modo por `packageName` o `BuildConfig` en Chat compartido; el origen del permiso sería implícito y se contaminaría S2.
- C. **DESCARTADA:** instalar directamente ActiveChatScreen desde Activity saltando shell; perdería navegación/foco/Guardados y crearía dos arquitecturas de UI.
- D. **DESCARTADA:** singleton de modo, flags persistentes o segundo owner. Riesgo ABA/lifecycle y acoplamiento a producción.
- E. **DIFERIDA:** habilitar Internet/Groq en manifest V2 DEV, reusar staged F1 sobre active generation o activar V2 shipping; es X03 U02, necesita nueva auditoría y permiso. No hacerlo aquí.

## B4 — Plan preciso, siete rutas exactas y pruebas

`allowed_functional_paths` para U01 tras nuevo READY (ninguna otra):
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ActiveChatScreen.kt` — enum/modo + diferencia entre envío USER y click EXPLICIT, con canonidad/anti-duplicado.
2. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt` — pasar modo EXPLICIT en **dos** llamadas `FaroV5SurfaceHost.install`, ambas sin `assistantTurnPort` hasta U02.
3. `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X03ExplicitConsultUiTest.kt` — nuevo fixture con Workspace y puerto fake **test-only** + caso real V2 DEV offline.
4. `.github/workflows/faro-v5-s3-a5-7-x03-consult-android-directed.yml` — nueva CI acotada Android API35/2 XML, APK/manifest.
5. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt` — agregar modo al FINAL, default AUTOMATIC, pasarlo a AdaptiveShell.
6. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AdaptiveShell.kt` — pasarlo a ShellScaffold e incluir modo en key.
7. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` — pasarlo a ActiveChatScreen conservando foco, backstack y chats anteriores.

Ejecución B6 (solo después de permiso READY en commit documental SEPARADO y preflight exacto de 7 rutas):
1. Cambio pequeño de firma propagado `Activity → SurfaceHost → AdaptiveShell → ShellScaffold → Chat`, sin cambiar navegación o conectar modelo en DEV.
2. En Chat modo AUTOMATIC por default conserva comportamiento previo. En modo EXPLICIT, `sendDraft` guarda USER pero **no** inicia `assistantTurnPort`. Botón Consultar FARO solo si puerto autorizado presente, turno USER ACCEPTED canónico, sesión/conversación/epoch vigente y no PROCESSING/replayed; el click único despacha ID tipado del turno, nunca texto arbitrario.
3. Aserciones negativas: sin puerto IA no botón falsa autoridad, sin red ni permiso `INTERNET`, sin `Aplicar cambios` de forma implícita, no auto-dispatch en Send, sin segundo dispatch por doble click/recreación/restauración, no publicar resultado de callback caducado. Reentrada desde Guardados mantiene EXPLICIT.
4. CI dirigida: `createAndroidComposeRule` API35 y ruta de fixture test; Java21 SDK37, APK V2 DEV + APK AndroidTest, exactamente 2 pruebas Android reales, `OK (2 tests)` y `INSTRUMENTATION_CODE: -1`; XML `tests=2 failures=0 errors=0 skipped=0`, hash APK/artifact y manifest sin INTERNET. Guardia B1–B7 del SHA exacto SUCCESS. No reclamar PASS si solo compila.
5. B7 independiente: `X03_U01_PASS_SCOPED_EXPLICIT_UI_NO_REMOTE`, no `X03 global PASS`. Una nueva unidad X03 U02 tiene su propia preauditoría y permiso; no encadenar código bajo U01.

**Si el fixture exige una 8.ª ruta o la compilación revela otra dependencia real, STOP `NEEDS_PREP` con error preciso antes de ampliar scope.** Evitar probar B6 sin ruta autorizada.

## B5 — Auditoría adversarial y guardias de cierre

- Riesgos nuevos: `@JvmOverloads` incompatible por insertar parámetros a mitad de firma; modo no propagado en regreso de Guardados; `key` incompleto conservando callbacks; click duplicado sobre USER antiguo; Activity recreada con un pending turn sin fuente; default S2 alterado; botón falso sin puerto; aprobación/Apply sin Review.
- Mitigación verificable: añadir al FINAL, defaults conservadores, prueba compilación de callers existentes y UI real con fixtures de doble click/recreación; ningún token en Bundle; `ConversationAcceptedUserTurn` exacto; read source fresco ante click; ningún fake de asistente en APK productivo.
- La falta de Internet en `v2CoreDev/AndroidManifest.xml` y ausencia de `FaroV5AiSettingsActivity` permanecen INTACTAS. No cambiar `main`, V4.4, S2 congelada, productiva V2, S4, Auto/Noche, pagos, secrets, teléfono G15 ni stores; staging F1 separado.
- Uso de suites previas: los PASS S2 y A3/X04–X05/X07–X11 siguen certificados por sus respectivos SHAs y alcances; no rehacerlos sin delta. X03-U01 agrega una prueba nueva, no cierra X03-U02.
- B7 Work del turno previo fue `NOT_RUN/NEEDS_PREP`, no es CI roja ni motivo para inventar RED_FIX. Antes de B6: nuevo permiso acotado con B1–B5 original + este delta, `reviewed_head` actual, guard y preflight de TODAS las siete rutas.
- **Dictamen B5: DELTA_PREAUDITED_SAFE_FOR_INDIVIDUAL_READY_PERMIT (DOC ONLY).** Si guard del checkpoint documental falla, NO activar READY ni código.
