# FARO V5 — S3.A5.7 X01/X02 real Android Activity + Avisos B1–B5
Fecha: 2026-11-09 AR. Unidad S3_A5_7_X01_X02_ANDROID_READ_ONLY_UI. Baseline f3180f9c83f0d91540c00db751e2ac4f705aad64. SOLO PREAUDITORÍA DOCUMENTAL mientras la CI Compose #42594626211 está en curso. No autorización de código nuevo hasta el B7 del último cambio funcional.

## B1 — Evidencia e hipótesis
- A4 está CLOSED_AUTOMATED_SCOPE_READ_ONLY_DEV según docs/s3-night/S3_A4_STAGE_CLOSURE_AUTOMATED_SCOPE_20261108.md. Activity tiene 28/28 SQLite reales y 68/68 Surface en B7 scoped. No se repiten.
- A5 inicial seis subunidades 33/33, integración adicional X07/X11 4/4, Android native X12 2/2 PASS scoped. X12 Compose sigue WAIT_CI con SHA 9ddd5851, guard #42594626180 SUCCESS, CI #42594626211 RUNNING al planificar. Estos datos NO certifican X01/X02.
- La Activity DEV nativa ofrece un botón explícito "Abrir Chat y Actividad local". Solo el click foreground permite Session.openActivityAfterTrustedForegroundRequest, que enlaza ActivityReadPort real al Compose drawer. El Chat común NO monta ese read port. El host DEV no monta read port de Avisos; por diseño, Avisos debe mostrar "Avisos no disponibles. No se consultaron datos.", jamás "No hay avisos abiertos." ni un cero global.
- Brecha nueva: aceptación con navegación y consentimiento Android reales, desde botón nativo a drawer Compose, mostrando al menos un mensaje Workspace USER confirmado como evento Activity con proveniencia y resumen local de dos fuentes SOLO ante acción; más negación de Activity no solicitada y Avisos sin owner.

## B2 — V4.4 y composición actual
- V4.4 solo referencia UX de Activity, cobertura parcial y lectura no mutante, no copiar datos ni código; Realty8 y grants actuales pertenecen V5.
- FaroV5ActivitySurfacePolicy marca mensajes USER como "Mensaje de usuario" y reloj Workspace como "Hora declarada por emisor (UTC)". Solo "Ver resumen local" produce "Resumen local solicitado": la cifra se limita a los eventos de la página y dos fuentes autorizadas, sin declarar total FARO.
- FaroV5AvisosSurfacePolicy no confunde unavailable/null con un owner empty: no crea unread counts ni marca leído. Reusar host DEV, Workspace canónico, shell y puertos de esas certificaciones; no reabrir A4.
- Android emulator API35 G5 sourceSet e instrumentacion ya fueron validados. A5.7 native test X12 2/2 es distinto: solo verificó launcher y lifecycle, no lectura de Activity bajo un owner real.

## B3 — Investigación externa y alternativas
- Android Compose Testing https://developer.android.com/develop/ui/compose/testing: createAndroidComposeRule, semántica con acciones y sincronización; USAR para medir UI real, no screenshot/mock.
- Android Compose and Views interoperability https://developer.android.com/develop/ui/compose/testing/interoperability: botón View nativo + navegación Compose en una Activity; ADAPTAR a host V2 DEV.
- AndroidX ActivityScenario https://developer.android.com/reference/androidx/test/core/app/ActivityScenario: onActivity/moveToState/recreate, en lugar de simular lifecycle; USAR.
- Offline-first data layer https://developer.android.com/topic/architecture/data-layer/offline-first: Workspace SQLite real como verdad, cero inferido cuando owner no responde; REFERENCIA.
- Opción A ELEGIDA: 3 casos nuevos Android instrumentados solo DEV, fixture USER auténtico con puertos canónicos y pantallas reales; no habilita modelo, Apply, OS delivery ni red. Opción B DESCARTADA: copiar tests de A4 28/28+68/68 o mockear pantallas (no validan gesto Android). Opción C DESCARTADA: Moto G15 ahora, contradice campaña única final.

## B4 — Delta y criterios binarios
- Nuevo archivo test-only v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ActivityAvisosComposeUiTest.kt con EXACTAMENTE 3 @Test.
  1. Crear un USER accepted por Composer/Timeline reales en directorio noBackupFilesDir del paquete DEV del EMULADOR; cerrar la sesión. Click nativo "Abrir Chat y Actividad local" en actividad RESUMED; abrir menú Compose "Actividad"; verificar "Mensaje de usuario", origen de hora correcto, cero datos confidenciales; "Ver resumen local" solo después de click, indicando la cobertura de dos fuentes.
  2. Abrir Chat normal sin solicitud Activity; navegar a Actividad y verificar "Actividad no disponible. No se consultaron datos.", sin resumen ni falsa página vacía.
  3. Abrir Chat normal y navegar a Avisos sin owner montado; ver "Avisos no disponibles. No se consultaron datos." y NO "No hay avisos abiertos.".
- Nuevo workflow .github/workflows/faro-v5-s3-a5-7-activity-avisos-android-directed.yml en Actions API35, exact Android test class nueva y SOLO estos tres casos. Aceptación: stdout "OK (3 tests)", "INSTRUMENTATION_CODE: -1", guard B1–B7 mismo SHA, nunca solo compilación.
- NO cambios productivos, manifest, build.gradle, catálogo, main, legacy, V2 productiva, S4 ni G15.

## B5 — Adversarial, regresión, STOP
- Producir fixture local con transacciones de Workspace host real e idempotency keys únicas, sin inserción SQL manual; usuario no aporta datos, APK corre solo UID aislado del emulador efímero. Se permite un USER accepted pero jamás fake FARO, Apply o receipt Reality sintético.
- UI de Actividad con permiso no debe revelar texto de mensaje ni opaque ID; solo etiqueta y provenance. Resumen explícito debe indicar alcance "página autorizada" y "dos fuentes", nunca total global. Fuera del permiso o Avisos sin fuente, mostrar NO DISPONIBLE; no aceptar cualquiera de las dos rutas como "vacía" para pasar el test.
- Revalidar grant tras pause/resume y puntero actual; el UI no tiene capacidad de mutar fuentes por click de Activity o Avisos. Sin nuevas APIs/egress ni Android permisos. Nunca activar V2 productiva.
- PRECONDICIÓN CRÍTICA: no crear permiso READY ni commit funcional hasta verificar B7 del code SHA 9ddd5851. El guard exige last_code_review exacto del anterior; esta fase es B1–B5 SOLO, sin encadenar código prematuramente.
- Si CI no produce 3 casos, clasificar RED con logs, volver a B2/B3/B5 y corregir solo una causa, sin rebajar condiciones. B7 futuro PASS_SCOPED si Android + guard exactos. Global X01–X12 OPEN; A3 X04/X05 NO AUTORIZADO, 172 operaciones sin cambios, G15 solo única campaña final.
