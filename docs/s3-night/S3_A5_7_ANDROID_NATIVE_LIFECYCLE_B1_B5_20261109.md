# FARO V5 — S3.A5.7 X12 Android DEV native lifecycle/a11y — B1–B5
Fecha: 2026-11-09 AR. Unit S3_A5_7_ANDROID_NATIVE_LIFECYCLE. Baseline a76fc76a21bb350202afe10aba48690f159b01ab. Writer epoch607. Ejecutar exclusivamente en emulador de GitHub Actions, NO Moto G15.

## B1 — Evidencia exacta y hueco
- Preflight: writer RELEASED epoch606, rama work/s3-night-20261102, HEAD a76fc76a. Permisos push true. V2 productiva/S4/Auto/Noche OFF. S2 y catálogo oficial 172 congelados.
- A5.1–A5.6 certificadas scoped 33/33 y A5.7 X07/X11 4/4: CI #42558390722 SHA 87b1c2ed, B7 docs/s3-night/S3_A5_7_OWNER_REVISION_B7_PASS_SCOPED_20261109.md. No repetir ninguna suite.
- Actividad DEV nativa FaroV5V2CoreDevActivity tiene cuatro botones explícitos y status "Sin abrir", exige applicationId aislado y debug; click "Crear Reality8 vacía (DEV)" bloquea si Lifecycle no RESUMED. onPause revoca pendientes y Actividad; onDestroy cierra sesión. No hay test NUEVO de ActivityScenario con botón en estado STARTED / recreación/semántica de botones físicos sobre Android OS.
- Diferenciar: esto cubre **solo shell nativo aislado**, NO Chat Compose, a11y integrada, egress, AI/Apply, G15 ni X12 global.
- Resultado binario acotado: instrumentación REAL Android emulada API35 de NUEVA clase con exactamente 2 métodos, salida `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`; guard B1–B7 éxito mismo SHA. Nunca inferir PASS desde compilar APKs.

## B2 — V4.4, diseño actual y reutilización
- V4.4 se toma SOLO como referencia UX de botón explícito/drawer y reanudación, no tiene paquete DEV aislado ni la promoción Reality8, por lo que no copiar su estado ni abrir datos.
- Reutilizar Gradle `-PfaroG5AndroidTestV2CoreDev=true`, sourceSet `v5/app/src/g5AndroidTest`, test runner JUnit4, instrumentación y emulator-runner validados por unidad G5 antigua (#42071443306). NO ejecutar G5 anterior.
- Aplicación DEV actual exhibe cuatro botones Native View al inicio; Compose/FaroV5AdaptiveShell se monta después de petición explícita. Por ello usar View introspection únicamente para esta puerta nativa, no confundirlo con validación del árbol semántico Compose.

## B3 — Investigación externa primaria y alternativas
- Android Activity lifecycle https://developer.android.com/guide/components/activities/activity-lifecycle : se revoca o limita acceso al abandonar RESUMED, y onResume puede llamar otra vez. USAR como criterio de foreground.
- AndroidX Test ActivityScenario https://developer.android.com/reference/androidx/test/core/app/ActivityScenario : `moveToState(STARTED)`, `onActivity`, `recreate`, `close` permiten prueba real de ciclo de vida en Android. USAR dependencia ya presente `androidx.test:core`.
- Android Accessibility native View https://developer.android.com/guide/topics/ui/accessibility/apps : control táctil >=48dp, label y click accesible. USAR solo para botones del shell nativo.
- Compose semantics testing https://developer.android.com/develop/ui/compose/testing/semantics : probar árbol semántico Compose en suite separada después de abrir Chat, **no fingir que View native equivale a Compose**. REFERENCIA para X12 restante.
- Opción A elegida: nueva instrumentación ActivityScenario de 2 casos + nuevo workflow de emulador basado en infraestructura G5, APK DEV aislada, sin tocar app y sin pruebas físicas. Opción B descartada: inspección JVM simulada de Activity/semantics; no prueba Android OS. Opción C descartada: reinstalar/usar Moto G15 ahora o ejecutar G5 1/1 repetido.

## B4 — Delta estricto y aceptación
- NUEVO `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57AndroidNativeLifecycleTest.kt`, EXACTAMENTE 2 @Test:
  1) arranque de FaroV5V2CoreDevActivity aislado con cuatro acciones explícitas etiquetadas; botones enabled/clickable, visible con tamaño al menos 48dp, sin etiqueta de Apply ni falsa autorización, status V2 productiva apagada.
  2) ActivityScenario STARTED (onPause), click programático en botón Reality8 DENEGADO cuando no RESUMED; puntero/raíz local no muta. RESUMED+recreate reestablece shell sin click, ni promoción automática.
- NUEVO workflow `.github/workflows/faro-v5-s3-a5-7-android-native-lifecycle-directed.yml`: compila V2 DEV y APK instrumentada, ejecuta SOLO clase nueva en emulador API35, verifica `OK (2 tests)` y `INSTRUMENTATION_CODE: -1`. No ejecutar G5 anterior, ni junit 33/33. Guard existente.
- No toques fuentes productivas, manifest, build.gradle.kts, S2, S4, catálogo, paquetes instalados ni datos de usuario.

## B5 — Auditoría previa adversarial y criterios STOP
- Este test nace sólo en APK de instrumentación aislada en emulador Actions; sin acceso Moto G15, sin credenciales, red, datos heredados ni permisos nuevos; no meter ningún componente adicional shipping.
- Conservar exclusividad writer CAS sobre GitHub; scripts/quality/faro-protocol-gate.py exige permiso READY en padre del nuevo commit de test/workflow y B7 audit previo PASS del commit 87b1c2ed (cumplido).
- Callbacks de UI no fuerzan permiso de Reality8 ni acceden a base del teléfono. En el test STARTED, verificar que no se crea nuevo root si inexistente, y status se mantiene. Si root preexistía, comparar estado de fichero/puntero y no limpiar datos ajenos.
- Caso adversarial: una acción directa en UI sin Lifecycle RESUMED no inicia promoción, incluso si botón click handler es invocado. Tras recreación, no se auto-consume el permiso anterior. Si hay race/renderer flakiness, registrar log y revisar hipótesis; nunca relajar umbral a solo build green.
- Un PASS nuevo 2/2 implica **X12 native DEV shell scoped**, no completa X12 Compose ni campaña física; X03/X04/X05/X09/X10 siguen no certificados y A3 NO APROBADO. Más adelante una única campaña G15. Deshabilitar V2 productiva/S4/Auto/Noche.
- B7 independiente revisará exact SHA, diff, instrument output y guard. Si FAILURE, B7 RED_FIX, corregir solo test/workflow con otro permiso y red causal.
